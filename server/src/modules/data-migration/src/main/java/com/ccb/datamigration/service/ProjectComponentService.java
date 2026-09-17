package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectComponentService {
    private final ProjectComponentRepository repository;
    private final DataMigrationPermissionService permissions;

    public ProjectComponentService(ProjectComponentRepository repository, DataMigrationPermissionService permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    /** 分页查询组件清单：维护页可见全部启用/停用记录。 */
    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, PageQuery page) {
        return components(user, projectId, null, null, null, null, null, null, page);
    }

    public PageResult<Map<String, Object>> components(AuthUser user, Long projectId, String businessGroupName,
                                                      String systemCode, String responsibleTeam, String systemKeyword,
                                                      Integer totalCheck, String keyword, PageQuery page) {
        PageQuery normalized = page == null ? new PageQuery(1, 20) : page;
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        long total = repository.count(user.tenantId(), filter.projectId(), filter.businessGroupName(), filter.systemCode(),
                filter.responsibleTeam(), filter.systemKeyword(), filter.totalCheck(), filter.keyword());
        List<Map<String, Object>> records = repository.page(user.tenantId(), filter.projectId(), filter.businessGroupName(),
                filter.systemCode(), filter.responsibleTeam(), filter.systemKeyword(), filter.totalCheck(), filter.keyword(),
                normalized.size(), (normalized.page() - 1) * normalized.size());
        return new PageResult<>(records, total, normalized.page(), normalized.size());
    }

    /**
     * 业务系统下拉：仅返回当前项目启用中的系统，前端本地随输随筛（编号/名称均可、不区分大小写）。
     */
    public List<Map<String, Object>> getSystemOptions(Long projectId, AuthUser user) {
        long scope = permissions.requireProject(projectId, user);
        return repository.systemOptions(user.tenantId(), scope);
    }

    /** 按筛选条件导出全量元数据字段（维护页可用，含停用记录）。 */
    public byte[] exportComponents(AuthUser user, Long projectId, String businessGroupName, String systemCode,
                                   String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) {
        long scope = permissions.requireProject(projectId, user);
        ComponentFilter filter = buildFilter(scope, businessGroupName, systemCode, responsibleTeam,
                systemKeyword, totalCheck, keyword);
        List<Map<String, Object>> rows = repository.exportRows(user.tenantId(), filter.projectId(), filter.businessGroupName(),
                filter.systemCode(), filter.responsibleTeam(), filter.systemKeyword(), filter.totalCheck(), filter.keyword());
        String[] columns = {"project_code", "project_name", "system_code", "enabled",
                "business_group_name", "system_short_name", "system_name", "system_description",
                "responsible_team_name", "total_check", "created_at", "created_by_name",
                "updated_at", "updated_by_name"};
        String[] headers = {"项目编号", "项目名称", "系统编号", "启用状态", "所属事业群",
                "系统简称", "系统名称", "系统描述", "负责团队", "是否涉及总分核对", "创建时间", "创建人", "更新时间", "更新人"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("system-components");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 1;
            for (Map<String, Object> data : rows) {
                Row row = sheet.createRow(index++);
                for (int i = 0; i < columns.length; i++) {
                    Object value = data.get(columns[i]);
                    row.createCell(i).setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to export XLSX");
        }
    }

    private record ComponentFilter(long projectId, String businessGroupName, String systemCode,
                                   String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) {
    }

    private static ComponentFilter buildFilter(long projectId, String businessGroupName, String systemCode,
                                               String responsibleTeam, String systemKeyword, Integer totalCheck,
                                               String keyword) {
        return new ComponentFilter(projectId, blankToNull(businessGroupName), blankToNull(systemCode),
                blankToNull(responsibleTeam), blankToNull(systemKeyword), totalCheck, blankToNull(keyword));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public Map<String, Object> createComponent(Map<String, Object> body, AuthUser user) {
        long projectId = number(body, "projectId");
        String systemCode = requireText(body, "systemCode");
        permissions.requireAccessible(projectId, user);
        if (repository.exists(user.tenantId(), projectId, systemCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 " + systemCode + " 已在该项目的组件清单中，请勿重复新增");
        }
        int totalCheck = integer(body, "totalCheck", 0);
        repository.insert(user.tenantId(), projectId, systemCode, totalCheck, user.id(), user.id());
        audit(user, "COMPONENT_CREATE", projectId, systemCode);
        return componentView(projectId, systemCode, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateComponent(Map<String, Object> body, AuthUser user) {
        permissions.requireAdmin(user);
        long projectId = number(body, "projectId");
        String systemCode = requireText(body, "systemCode");
        Map<String, Object> stored = find(projectId, systemCode, user.tenantId());
        permissions.requireStoredProject(stored.get("project_id"), user);
        int totalCheck = integer(body, "totalCheck", 0);
        repository.updateTotalCheck(totalCheck, user.id(), user.tenantId(), projectId, systemCode);
        audit(user, "COMPONENT_UPDATE", projectId, systemCode);
        return componentView(projectId, systemCode, user.tenantId());
    }

    @Transactional
    public void deleteComponent(long projectId, String systemCode, AuthUser user) {
        Map<String, Object> row = find(projectId, systemCode.trim(), user.tenantId());
        long storedProjectId = permissions.requireStoredProject(row.get("project_id"), user);
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        if (hasContentAssets(storedProjectId, systemCode.trim(), user)) throw new BusinessException(ErrorCode.CONFLICT, "该组件存在相关业务数据，拒绝物理删除，请先清理或停用");
        repository.delete(user.tenantId(), storedProjectId, systemCode.trim());
        audit(user, "COMPONENT_DELETE", storedProjectId, systemCode.trim());
    }

    @Transactional
    public Map<String, Object> setEnabled(long projectId, String systemCode, boolean enabled, AuthUser user) {
        Map<String, Object> row = find(projectId, systemCode.trim(), user.tenantId());
        long storedProjectId = permissions.requireStoredProject(row.get("project_id"), user);
        permissions.requireWrite(user, ((Number) row.get("owner_id")).longValue());
        repository.updateEnabled(enabled, user.id(), user.tenantId(), storedProjectId, systemCode.trim());
        audit(user, enabled ? "COMPONENT_ENABLE" : "COMPONENT_DISABLE", storedProjectId, systemCode.trim());
        return componentView(storedProjectId, systemCode.trim(), user.tenantId());
    }

    private Map<String, Object> componentView(long projectId, String systemCode, long tenantId) {
        return repository.view(tenantId, projectId, systemCode);
    }

    private Map<String, Object> find(long projectId, String systemCode, long tenantId) {
        return repository.require(tenantId, projectId, systemCode);
    }

    /** 跨全部内容表统计组件占用（含映射/依赖/方案/专题等业务数据），任一活动行即拒绝物理删除。 */
    private boolean hasContentAssets(long projectId, String systemCode, AuthUser user) {
        return repository.hasActiveContent(user.tenantId(), projectId, systemCode);
    }

    private void audit(AuthUser user, String op, long projectId, String systemCode) {
        String detailJson = "{\"projectId\":" + projectId + ",\"systemCode\":\"" + systemCode.replace("\"", "") + "\"}";
        repository.insertAudit(user.tenantId(), user.id(), projectId, op, detailJson);
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required");
        return String.valueOf(value).trim();
    }

    private static long number(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required");
        try { return Long.parseLong(String.valueOf(raw).trim()); }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); }
    }

    private static int integer(Map<String, Object> body, String key, int defaultValue) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) return defaultValue;
        try { int value = Integer.parseInt(String.valueOf(raw).trim()); return value == 1 ? 1 : 0; }
        catch (NumberFormatException ex) { throw new BusinessException(ErrorCode.BAD_REQUEST, key + " must be numeric"); }
    }
}
