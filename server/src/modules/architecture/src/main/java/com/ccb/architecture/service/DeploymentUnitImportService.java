package com.ccb.architecture.service;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnit;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportBatch;
import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitImportItem;
import com.ccb.architecture.model.DeploymentUnitModels.ImportBatchStatus;
import com.ccb.architecture.model.DeploymentUnitModels.ImportItemStatus;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.architecture.persistence.DeploymentUnitStore.PhysicalSubsystemRef;
import com.ccb.architecture.web.ArchitectureNotFoundException;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemOperationAuditCommand;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 部署单元初始化导入：Excel 上传解析 → 预览校验 → 确认写入 → 批次台账与错误报告。
 * 确认写入运行在单事务内：预期行级失败记录明细并继续；意外异常整批回滚。
 */
@Service
public class DeploymentUnitImportService {
    public static final String RESOURCE_PATH = "/api/architecture/deployment-unit-imports";
    private static final String IMPORT_OPERATION = "ARCHITECTURE_DEPLOYMENT_UNIT_IMPORT";

    private static final Logger log = LoggerFactory.getLogger(DeploymentUnitImportService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] EXPECTED_HEADERS = {
            "物理子系统编号", "部署单元简称", "部署单元名称", "部署单元类型", "描述", "备注"
    };
    private static final Map<String, String> KIND_LABELS = Map.of(
            "应用", "APPLICATION",
            "数据库", "DATABASE",
            "消息队列", "MQ");
    private static final String[] KIND_VALUES = {"APPLICATION", "DATABASE", "MQ"};

    private final DeploymentUnitStore store;
    private final DeploymentUnitService unitService;
    private final SystemReferenceQuery referenceQuery;
    private final SystemOperationAudit operationAudit;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    public DeploymentUnitImportService(DeploymentUnitStore store,
                                       DeploymentUnitService unitService,
                                       SystemReferenceQuery referenceQuery,
                                       SystemOperationAudit operationAudit,
                                       TransactionTemplate transactions,
                                       ObjectMapper objectMapper) {
        this.store = store;
        this.unitService = unitService;
        this.referenceQuery = referenceQuery;
        this.operationAudit = operationAudit;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    // ---------- 上传与预览 ----------

    public ImportBatchView upload(AuthUser actor, MultipartFile file, String traceId) {
        requireActor(actor);
        if (file == null || file.isEmpty()) {
            throw badRequest("导入文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("导入文件不能超过 10MB");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw badRequest("仅支持 .xlsx 格式的 Excel 文件");
        }
        List<RawRow> rows;
        try (InputStream input = file.getInputStream()) {
            rows = parseWorkbook(input);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件读取失败：" + safeIoMessage(exception));
        }

        Map<String, PhysicalSubsystemRef> physicalCache = new HashMap<>();
        List<PreparedRow> prepared = new ArrayList<>();
        Map<String, Integer> fileKeys = new HashMap<>();
        for (RawRow row : rows) {
            prepared.add(prepareRow(actor, row, physicalCache, fileKeys));
        }
        int totalRows = prepared.size();
        int validRows = (int) prepared.stream().filter(row -> row.status() == ImportItemStatus.VALID).count();

        long batchId = nextId();
        try {
            transactions.executeWithoutResult(status -> {
                store.insertBatch(batchId, actor.tenantId(), fileName, file.getSize(), totalRows, validRows,
                        actor.id());
                for (PreparedRow row : prepared) {
                    store.insertItem(nextId(), actor.tenantId(), batchId, row.lineNo(), row.rawJson(),
                            row.status().name(), row.errorMessage(), row.note(), null);
                }
            });
        } catch (RuntimeException exception) {
            throw recordFailure(actor, exception, traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, "POST", RESOURCE_PATH, null, traceId));
        return batchView(actor, batchId);
    }

    // ---------- 确认写入 ----------

    public ImportBatchView confirm(AuthUser actor, long batchId, String traceId) {
        requireActor(actor);
        DeploymentUnitImportBatch batch = store.findBatch(actor.tenantId(), batchId)
                .orElseThrow(() -> new ArchitectureNotFoundException("导入批次不存在：" + batchId));
        if (!batch.status().equals(ImportBatchStatus.PREVIEW.name())) {
            throw conflict("该导入批次已确认或已结束，不能重复确认");
        }
        try {
            transactions.executeWithoutResult(status -> {
                List<DeploymentUnitImportItem> items = store.findItems(actor.tenantId(), batchId,
                        DeploymentUnitStore.MAX_IMPORT_ROWS + 1);
                Map<String, PhysicalSubsystemRef> physicalCache = new HashMap<>();
                int success = 0;
                int skipped = 0;
                int failed = 0;
                for (DeploymentUnitImportItem item : items) {
                    if (item.rowStatus().equals(ImportItemStatus.INVALID.name())) {
                        failed++;
                        continue;
                    }
                    if (!item.rowStatus().equals(ImportItemStatus.VALID.name())) {
                        continue;
                    }
                    ImportItemStatus result = processRow(actor, item, physicalCache);
                    if (result == ImportItemStatus.SUCCESS) {
                        success++;
                    }
                    if (result == ImportItemStatus.SKIPPED) {
                        success++;
                        skipped++;
                    }
                    if (result == ImportItemStatus.FAILED) {
                        failed++;
                    }
                }
                String batchStatus = failed > 0 ? ImportBatchStatus.PARTIAL.name() : ImportBatchStatus.SUCCESS.name();
                store.updateBatchResult(actor.tenantId(), batchId, batchStatus, success, failed, skipped, null);
            });
        } catch (RuntimeException exception) {
            markBatchFailed(actor, batchId, exception);
            throw recordFailure(actor, new BusinessException(ErrorCode.CONFLICT,
                    "导入确认失败，已整批回滚，请修正数据后重新导入"), traceId);
        }
        operationAudit.recordSuccess(auditCommand(actor, "POST", RESOURCE_PATH + "/" + batchId + "/confirm",
                null, traceId));
        return batchView(actor, batchId);
    }

    /**
     * 行级处理：预期校验失败记录 FAILED 明细并继续；意外异常向上抛出让整批回滚。
     * 返回该行最终状态，供批次计数使用。
     */
    private ImportItemStatus processRow(AuthUser actor, DeploymentUnitImportItem item,
                                        Map<String, PhysicalSubsystemRef> physicalCache) {
        RawRow row = parseRawRow(item.rawJson());
        try {
            PhysicalSubsystemRef physical = resolvePhysical(actor, row.physicalCode(), physicalCache);
            if (physical == null) {
                throw badRequest("物理子系统编号不存在或不属于当前租户：" + row.physicalCode());
            }
            if (physical.deleted()) {
                throw badRequest("物理子系统已删除，不能在其下创建部署单元");
            }
            if (!"ACTIVE".equals(physical.status())) {
                throw badRequest("物理子系统状态不允许创建部署单元（状态 " + physical.status() + "）");
            }
            Optional<DeploymentUnit> existing = store.findUnitByPhysicalAndName(actor.tenantId(),
                    physical.id(), row.name());
            if (existing.isPresent()) {
                if (existing.get().status().equals("ACTIVE")) {
                    store.updateItemResult(actor.tenantId(), item.id(), ImportItemStatus.SKIPPED.name(), null,
                            "已存在同名 ACTIVE 部署单元，跳过不重复创建", existing.get().id());
                    return ImportItemStatus.SKIPPED;
                }
                throw badRequest("名称已被停用或作废的部署单元占用，不可复用");
            }
            String kind = normalizeKind(row.kindLabel());
            if (kind == null) {
                throw badRequest("部署单元类型仅支持 应用、数据库、消息队列");
            }
            long unitId = unitService.publishInitial(actor, physical.id(), row.shortName(), row.name(),
                    kind, row.description(), null, defaultDeploymentUnitType(kind), row.remark());
            store.updateItemResult(actor.tenantId(), item.id(), ImportItemStatus.SUCCESS.name(), null, null, unitId);
            return ImportItemStatus.SUCCESS;
        } catch (BusinessException exception) {
            store.updateItemResult(actor.tenantId(), item.id(), ImportItemStatus.FAILED.name(),
                    exception.getMessage(), null, null);
            return ImportItemStatus.FAILED;
        }
    }

    private void markBatchFailed(AuthUser actor, long batchId, RuntimeException original) {
        try {
            transactions.executeWithoutResult(status -> store.updateBatchResult(actor.tenantId(), batchId,
                    ImportBatchStatus.FAILED.name(), 0, 0, 0, safeErrorMessage(original)));
        } catch (RuntimeException markFailure) {
            log.error("导入批次失败标记写入失败，batchId={}", batchId, markFailure);
        }
    }

    // ---------- 查询与导出 ----------

    public PageResult<ImportBatchSummary> listBatches(AuthUser actor, PageQuery page) {
        requireActor(actor);
        PageResult<DeploymentUnitImportBatch> result = store.pageBatches(actor.tenantId(), page);
        Map<Long, Optional<SystemUserReference>> users = new HashMap<>();
        List<ImportBatchSummary> records = result.records().stream()
                .map(batch -> new ImportBatchSummary(batch.id(), batch.fileName(), batch.fileSize(),
                        batch.totalRows(), batch.validRows(), batch.successRows(), batch.failedRows(),
                        batch.skippedRows(), batch.status(), batch.errorMessage(), batch.createdBy(),
                        displayName(actor, batch.createdBy(), users), batch.createdAt(), batch.completedAt()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public ImportBatchView batchDetail(AuthUser actor, long batchId) {
        requireActor(actor);
        ImportBatchSummary batch = findBatchSummary(actor, batchId);
        return new ImportBatchView(batch, itemViews(actor, batchId));
    }

    /** 失败明细 CSV 错误报告（UTF-8 BOM，Excel 可直接打开）。 */
    public byte[] errorReport(AuthUser actor, long batchId) {
        requireActor(actor);
        ImportBatchSummary batch = findBatchSummary(actor, batchId);
        StringBuilder csv = new StringBuilder();
        csv.append("行号,物理子系统编号,部署单元简称,部署单元名称,部署单元类型,描述,备注,状态,说明\n");
        for (DeploymentUnitImportItem item : store.findItems(actor.tenantId(), batchId,
                DeploymentUnitStore.MAX_IMPORT_ROWS + 1)) {
            if (!item.rowStatus().equals(ImportItemStatus.INVALID.name())
                    && !item.rowStatus().equals(ImportItemStatus.FAILED.name())) {
                continue;
            }
            RawRow row = parseRawRow(item.rawJson());
            csv.append(item.lineNo()).append(',')
                    .append(csvCell(row.physicalCode())).append(',')
                    .append(csvCell(row.shortName())).append(',')
                    .append(csvCell(row.name())).append(',')
                    .append(csvCell(row.kindLabel())).append(',')
                    .append(csvCell(row.description())).append(',')
                    .append(csvCell(row.remark())).append(',')
                    .append(csvCell(item.rowStatus())).append(',')
                    .append(csvCell(item.errorMessage() == null ? item.note() : item.errorMessage()))
                    .append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    /** 导入模板（xlsx）：表头 + 一行示例数据。 */
    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("部署单元导入模板");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                header.createCell(i).setCellValue(EXPECTED_HEADERS[i]);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("W0001A");
            sample.createCell(1).setCellValue("ECIP-AP");
            sample.createCell(2).setCellValue("电子渠道接入应用");
            sample.createCell(3).setCellValue("应用");
            sample.createCell(4).setCellValue("渠道接入服务（示例数据）");
            sample.createCell(5).setCellValue("演示用，可删除");
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                sheet.setColumnWidth(i, 24 * 256);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板生成失败，请稍后重试");
        }
    }

    // ---------- 解析与校验 ----------

    private List<RawRow> parseWorkbook(InputStream input) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw badRequest("导入文件缺少工作表");
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw badRequest("导入文件缺少表头");
            }
            for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
                String actual = normalizeHeader(formatter.formatCellValue(headerRow.getCell(i)));
                if (!EXPECTED_HEADERS[i].equals(actual)) {
                    throw badRequest("表头第 " + (i + 1) + " 列应为「" + EXPECTED_HEADERS[i] + "」，实际为「"
                            + (actual == null ? "空" : actual) + "」");
                }
            }
            List<RawRow> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            if (lastRow > DeploymentUnitStore.MAX_IMPORT_ROWS) {
                throw badRequest("导入数据行不能超过 " + DeploymentUnitStore.MAX_IMPORT_ROWS + " 行");
            }
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String[] cells = new String[6];
                boolean anyValue = false;
                for (int c = 0; c < 6; c++) {
                    cells[c] = normalizeCell(formatter.formatCellValue(row.getCell(c)));
                    if (cells[c] != null) {
                        anyValue = true;
                    }
                }
                if (!anyValue) {
                    continue;
                }
                rows.add(new RawRow(i + 1, cells[0], cells[1], cells[2], cells[3], cells[4], cells[5]));
            }
            return rows;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入文件解析失败，请确认是有效的 .xlsx 文件");
        }
    }

    private PreparedRow prepareRow(AuthUser actor, RawRow row, Map<String, PhysicalSubsystemRef> physicalCache,
                                   Map<String, Integer> fileKeys) {
        List<String> errors = new ArrayList<>();
        if (row.physicalCode() == null) {
            errors.add("物理子系统编号不能为空");
        } else {
            PhysicalSubsystemRef physical = resolvePhysical(actor, row.physicalCode(), physicalCache);
            if (physical == null) {
                errors.add("物理子系统编号不存在或不属于当前租户");
            } else if (physical.deleted()) {
                errors.add("物理子系统已删除，不能在其下创建部署单元");
            } else if (!"ACTIVE".equals(physical.status())) {
                errors.add("物理子系统状态不允许创建部署单元（" + physical.status() + "）");
            }
        }
        if (row.shortName() == null || row.shortName().length() < 2 || row.shortName().length() > 100) {
            errors.add("部署单元简称长度必须为 2—100 个字符");
        }
        if (row.name() == null || row.name().length() < 2 || row.name().length() > 200) {
            errors.add("部署单元名称长度必须为 2—200 个字符");
        }
        String kind = normalizeKind(row.kindLabel());
        if (kind == null) {
            errors.add("部署单元类型仅支持 应用、数据库、消息队列");
        }
        if (row.description() != null && row.description().length() > 2000) {
            errors.add("描述最长 2000 个字符");
        }
        if (row.remark() != null && row.remark().length() > 1000) {
            errors.add("备注最长 1000 个字符");
        }
        String duplicateKey = row.physicalCode() + "|" + (row.name() == null ? "" : row.name());
        if (errors.isEmpty()) {
            Integer previousLine = fileKeys.putIfAbsent(duplicateKey, row.lineNo());
            if (previousLine != null) {
                errors.add("与文件内第 " + previousLine + " 行重复");
            }
        }
        String existingNote = null;
        if (errors.isEmpty() && row.physicalCode() != null && row.name() != null) {
            PhysicalSubsystemRef physical = physicalCache.get(row.physicalCode());
            Optional<DeploymentUnit> existing = physical == null ? Optional.empty()
                    : store.findUnitByPhysicalAndName(actor.tenantId(), physical.id(), row.name());
            if (existing.isPresent()) {
                if (existing.get().status().equals("ACTIVE")) {
                    existingNote = "已存在同名 ACTIVE 部署单元，确认时将跳过";
                } else {
                    errors.add("名称已被停用或作废的部署单元占用，不可复用");
                }
            }
        }
        ImportItemStatus status = errors.isEmpty() ? ImportItemStatus.VALID : ImportItemStatus.INVALID;
        return new PreparedRow(row, status,
                errors.isEmpty() ? null : String.join("；", errors), existingNote,
                toJson(new String[]{row.physicalCode(), row.shortName(), row.name(), row.kindLabel(),
                        row.description(), row.remark()}));
    }

    private PhysicalSubsystemRef resolvePhysical(AuthUser actor, String code,
                                                 Map<String, PhysicalSubsystemRef> cache) {
        return cache.computeIfAbsent(code, key -> store.findPhysicalByCode(actor.tenantId(), key).orElse(null));
    }

    private String normalizeKind(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        String mapped = KIND_LABELS.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        for (String value : KIND_VALUES) {
            if (value.equalsIgnoreCase(trimmed)) {
                return value;
            }
        }
        return null;
    }

    private String defaultDeploymentUnitType(String kind) {
        return "DATABASE".equalsIgnoreCase(kind) ? "DB" : "AP";
    }

    private RawRow parseRawRow(String rawJson) {
        String[] values;
        try {
            values = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("导入行快照解析失败", exception);
        }
        return new RawRow(0, values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private String toJson(String[] values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (IOException exception) {
            throw new IllegalStateException("导入行快照序列化失败", exception);
        }
    }

    // ---------- 投影 ----------

    private ImportBatchView batchView(AuthUser actor, long batchId) {
        ImportBatchSummary batch = findBatchSummary(actor, batchId);
        return new ImportBatchView(batch, itemViews(actor, batchId));
    }

    private List<ImportItemView> itemViews(AuthUser actor, long batchId) {
        return store.findItems(actor.tenantId(), batchId, DeploymentUnitStore.MAX_IMPORT_ROWS + 1).stream()
                .map(item -> new ImportItemView(item.id(), item.lineNo(), toRowView(parseRawRow(item.rawJson())),
                        item.rowStatus(), item.errorMessage(), item.note(), item.unitId()))
                .toList();
    }

    private ImportRowView toRowView(RawRow row) {
        return new ImportRowView(row.physicalCode(), row.shortName(), row.name(), row.kindLabel(),
                row.description(), row.remark());
    }

    private ImportBatchSummary findBatchSummary(AuthUser actor, long batchId) {
        DeploymentUnitImportBatch batch = store.findBatch(actor.tenantId(), batchId)
                .orElseThrow(() -> new ArchitectureNotFoundException("导入批次不存在：" + batchId));
        return new ImportBatchSummary(batch.id(), batch.fileName(), batch.fileSize(), batch.totalRows(),
                batch.validRows(), batch.successRows(), batch.failedRows(), batch.skippedRows(), batch.status(),
                batch.errorMessage(), batch.createdBy(),
                displayName(actor, batch.createdBy(), new HashMap<>()), batch.createdAt(), batch.completedAt());
    }

    private String displayName(AuthUser actor, long userId, Map<Long, Optional<SystemUserReference>> cache) {
        Optional<SystemUserReference> reference = cache.computeIfAbsent(userId,
                key -> referenceQuery.findUser(actor, key, false));
        return reference == null || reference.isEmpty() ? "用户 #" + userId : reference.get().displayName();
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String sanitizeFileName(String original) {
        if (original == null) {
            return null;
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String normalizeHeader(String value) {
        String normalized = normalizeCell(value);
        if (normalized != null && normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizeCell(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // ---------- 审计与通用 ----------

    private SystemOperationAuditCommand auditCommand(AuthUser actor, String method, String path,
                                                     String error, String traceId) {
        return new SystemOperationAuditCommand(actor, IMPORT_OPERATION, method, path, error, traceId);
    }

    private RuntimeException recordFailure(AuthUser actor, RuntimeException original, String traceId) {
        try {
            operationAudit.recordFailure(auditCommand(actor, "POST", RESOURCE_PATH,
                    safeErrorMessage(original), traceId));
        } catch (RuntimeException auditFailure) {
            log.error("部署单元导入失败审计写入失败", auditFailure);
        }
        return original;
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof ArchitectureNotFoundException) {
            return exception.getMessage();
        }
        return "部署单元导入操作失败";
    }

    private String safeIoMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null ? "无法读取文件" : message.replace('\n', ' ').trim();
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }

    private record RawRow(int lineNo, String physicalCode, String shortName, String name, String kindLabel,
                          String description, String remark) {
    }
    private record PreparedRow(RawRow row, ImportItemStatus status, String errorMessage, String note,
                               String rawJson) {
        int lineNo() {
            return row.lineNo();
        }
    }

    public record ImportBatchSummary(
            long id,
            String fileName,
            long fileSize,
            int totalRows,
            int validRows,
            int successRows,
            int failedRows,
            int skippedRows,
            String status,
            String errorMessage,
            long createdBy,
            String createdByDisplayName,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
    }

    public record ImportRowView(
            String physicalCode,
            String shortName,
            String name,
            String kindLabel,
            String description,
            String remark) {
    }

    public record ImportItemView(
            long itemId,
            int lineNo,
            ImportRowView row,
            String rowStatus,
            String errorMessage,
            String note,
            Long unitId) {
    }

    public record ImportBatchView(ImportBatchSummary batch, List<ImportItemView> items) {
    }
}
