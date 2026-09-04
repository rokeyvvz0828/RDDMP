package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final Set<String> TYPES = Set.of("RULE", "PARAMETER");
    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService permissions;
    private final ContentDocCodeGenerator docCodes;
    @Autowired
    public ExcelService(JdbcTemplate jdbc, DataMigrationPermissionService permissions, ContentDocCodeGenerator docCodes) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.docCodes = docCodes;
    }
    public ExcelService(JdbcTemplate jdbc, DataMigrationPermissionService permissions) {
        this(jdbc, permissions, new ContentDocCodeGenerator());
    }

    /** 导出（T32）：{@code projectId} 必填，且必须是调用者可访问的项目；Excel 不再提供跨项目全量出口。 */
    public byte[] export(String type, Long projectId, Long componentId, String keyword, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        long scope = permissions.requireProject(projectId, user);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("data-migration");
            Row header = sheet.createRow(0); String[] columns = {"asset_code","asset_name","project_id","component_id","asset_type","structured_data"};
            for (int i=0;i<columns.length;i++) header.createCell(i).setCellValue(columns[i]);
            StringBuilder sql = new StringBuilder("SELECT doc_code AS asset_code, doc_name AS asset_name, project_id, component_id, '" + type + "' AS asset_type, CAST(structured_data AS CHAR) AS structured_data FROM " + table(type) + " WHERE tenant_id = ? AND project_id = ? AND deleted = 0");
            List<Object> args = new ArrayList<>(List.of(user.tenantId(), scope));
            if (componentId != null) { sql.append(" AND component_id = ?"); args.add(componentId); }
            if (keyword != null && !keyword.isBlank()) { sql.append(" AND (doc_code LIKE ? OR doc_name LIKE ?)"); args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%"); }
            sql.append(" ORDER BY id");
            List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            int index = 1; for (Map<String,Object> data : rows) { Row row = sheet.createRow(index++); for (int i=0;i<columns.length;i++) row.createCell(i).setCellValue(String.valueOf(data.getOrDefault(columns[i], ""))); }
            workbook.write(out); return out.toByteArray();
        } catch (IOException ex) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to export XLSX"); }
    }
    /**
     * 导入（T32 决策 D4）：请求必须携带可访问的 {@code projectId}，入库归属一律以请求项目为准；
     * 行内 {@code project_id} 与请求项目不一致时逐行失败，不默默改写归属，避免跨项目脏数据。
     */
    public Map<String,Object> importAssets(String type, Long projectId, MultipartFile file, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        long scope = permissions.requireProject(projectId, user);
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "XLSX is empty or exceeds 50 MB");
        int rows = 0; int accepted = 0; List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "XLSX exceeds 5000 rows");
            DataFormatter formatter = new DataFormatter();
            for (int i=1;i<=sheet.getLastRowNum();i++) {
                rows++; Row row = sheet.getRow(i);
                try {
                    String name = text(row, 0, formatter);
                    long rowProjectId = number(row, 1, formatter); String component = text(row, 2, formatter);
                    if (name.isBlank()) throw new IllegalArgumentException("asset_name is required");
                    if (rowProjectId != scope) throw new IllegalArgumentException("row project_id " + rowProjectId + " does not match the selected project " + scope);
                    Long componentId = component.isBlank() ? null : Long.valueOf(component);
                    if (componentId != null && !exists("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", componentId, scope, user.tenantId())) throw new IllegalArgumentException("component_id not found");
                    String structuredData = text(row, 4, formatter); String assetType = text(row, 3, formatter);
                    if (assetType.isBlank()) assetType = type;
                    if (!type.equals(assetType)) throw new IllegalArgumentException("asset_type does not match import type");
                    String code = docCodes.generate(type);
                    jdbc.update("INSERT INTO " + table(type) + " (id, tenant_id, project_id, component_id, doc_code, doc_name, structured_data, owner_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), scope, componentId, code, name, structuredData.isBlank() ? "{}" : structuredData, user.id(), user.id(), user.id());
                    accepted++; audit(user, scope, code);
                } catch (Exception ex) { errors.add("row " + (i + 1) + ": " + ex.getMessage()); }
            }
        } catch (IOException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid XLSX file"); }
        return Map.of("rows", rows, "accepted", accepted, "failed", errors.size(), "errors", errors);
    }

    private static String table(String type) { return ContentAssetTables.tableFor(type); }
    private static String text(Row row, int index, DataFormatter formatter) { return row == null || row.getCell(index) == null ? "" : formatter.formatCellValue(row.getCell(index)).trim(); }
    private static long number(Row row, int index, DataFormatter formatter) { String value = text(row, index, formatter); if (value.isBlank()) throw new IllegalArgumentException("project_id is required"); return Long.parseLong(value); }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }
    private void audit(AuthUser user, long projectId, String code) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, project_id, operation_code, entity_type, detail_json) VALUES (?, ?, ?, 'STRUCTURED_IMPORT', 'ASSET', ?)", user.tenantId(), user.id(), projectId, "{\"assetCode\":\"" + code.replace("\"", "") + "\"}"); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
