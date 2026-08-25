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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelService {
    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final Set<String> TYPES = Set.of("RULE", "PARAMETER", "TABLE_STRUCTURE", "INTERMEDIATE_TABLE");
    private final JdbcTemplate jdbc;
    public ExcelService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public byte[] export(String type, AuthUser user) {
        return export(type, null, null, null, user);
    }
    public byte[] export(String type, Long projectId, Long componentId, String keyword, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("data-migration");
            Row header = sheet.createRow(0); String[] columns = {"asset_code","asset_name","project_id","component_id","asset_type","structured_data"};
            for (int i=0;i<columns.length;i++) header.createCell(i).setCellValue(columns[i]);
            StringBuilder sql = new StringBuilder("SELECT asset_code, asset_name, project_id, component_id, asset_type, CAST(structured_data AS CHAR) AS structured_data FROM dm_asset WHERE tenant_id = ? AND asset_type = ? AND deleted = 0");
            List<Object> args = new ArrayList<>(List.of(user.tenantId(), type));
            if (projectId != null) { sql.append(" AND project_id = ?"); args.add(projectId); }
            if (componentId != null) { sql.append(" AND component_id = ?"); args.add(componentId); }
            if (keyword != null && !keyword.isBlank()) { sql.append(" AND (asset_code LIKE ? OR asset_name LIKE ?)"); args.add("%" + keyword.trim() + "%"); args.add("%" + keyword.trim() + "%"); }
            sql.append(" ORDER BY id");
            List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            int index = 1; for (Map<String,Object> data : rows) { Row row = sheet.createRow(index++); for (int i=0;i<columns.length;i++) row.createCell(i).setCellValue(String.valueOf(data.getOrDefault(columns[i], ""))); }
            workbook.write(out); return out.toByteArray();
        } catch (IOException ex) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to export XLSX"); }
    }
    public Map<String,Object> importAssets(String type, MultipartFile file, AuthUser user) {
        if (!TYPES.contains(type)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported structured asset type");
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new BusinessException(ErrorCode.BAD_REQUEST, "XLSX is empty or exceeds 50 MB");
        int rows = 0; int accepted = 0; List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException(ErrorCode.BAD_REQUEST, "XLSX exceeds 5000 rows");
            DataFormatter formatter = new DataFormatter();
            for (int i=1;i<=sheet.getLastRowNum();i++) {
                rows++; Row row = sheet.getRow(i);
                try {
                    String code = text(row, 0, formatter); String name = text(row, 1, formatter);
                    long projectId = number(row, 2, formatter); String component = text(row, 3, formatter);
                    if (code.isBlank() || name.isBlank()) throw new IllegalArgumentException("asset_code and asset_name are required");
                    if (!exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", projectId, user.tenantId())) throw new IllegalArgumentException("project_id not found");
                    Long componentId = component.isBlank() ? null : Long.valueOf(component);
                    if (componentId != null && !exists("SELECT COUNT(*) FROM dm_component WHERE id = ? AND project_id = ? AND tenant_id = ? AND deleted = 0", componentId, projectId, user.tenantId())) throw new IllegalArgumentException("component_id not found");
                    String structuredData = text(row, 5, formatter); String assetType = text(row, 4, formatter);
                    if (assetType.isBlank()) assetType = type;
                    if (!type.equals(assetType)) throw new IllegalArgumentException("asset_type does not match import type");
                    jdbc.update("INSERT INTO dm_asset (id, tenant_id, project_id, component_id, asset_type, asset_code, asset_name, structured_data, owner_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", nextId(), user.tenantId(), projectId, componentId, type, code, name, structuredData.isBlank() ? "{}" : structuredData, user.id());
                    accepted++; audit(user, code);
                } catch (Exception ex) { errors.add("row " + (i + 1) + ": " + ex.getMessage()); }
            }
        } catch (IOException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid XLSX file"); }
        return Map.of("rows", rows, "accepted", accepted, "failed", errors.size(), "errors", errors);
    }

    private static String text(Row row, int index, DataFormatter formatter) { return row == null || row.getCell(index) == null ? "" : formatter.formatCellValue(row.getCell(index)).trim(); }
    private static long number(Row row, int index, DataFormatter formatter) { String value = text(row, index, formatter); if (value.isBlank()) throw new IllegalArgumentException("project_id is required"); return Long.parseLong(value); }
    private boolean exists(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count != null && count > 0; }
    private void audit(AuthUser user, String code) { jdbc.update("INSERT INTO dm_operation_log (tenant_id, actor_id, operation_code, entity_type, detail_json) VALUES (?, ?, 'STRUCTURED_IMPORT', 'ASSET', ?)", user.tenantId(), user.id(), "{\"assetCode\":\"" + code.replace("\"", "") + "\"}"); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
