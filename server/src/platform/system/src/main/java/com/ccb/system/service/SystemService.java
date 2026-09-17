package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.common.audit.OperationAuditContext;
import com.ccb.system.model.SystemPage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class SystemService {
    private static final Pattern PERMISSION_CODE = Pattern.compile("[a-z0-9][a-z0-9:-]{2,159}");
    private static final Pattern ACTION_CODE = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private final SystemRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService storage;

    public SystemService(SystemRepository repository, PasswordEncoder passwordEncoder, MinioStorageService storage) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

    public SystemPage<Map<String, Object>> list(String resource, PageQuery pageQuery, String keyword, AuthUser user) {
        return list(resource, pageQuery, keyword, null, null, user);
    }

    public SystemPage<Map<String, Object>> list(String resource, PageQuery pageQuery, String keyword, Long orgId, AuthUser user) {
        return list(resource, pageQuery, keyword, orgId, null, user);
    }

    public SystemPage<Map<String, Object>> list(String resource, PageQuery pageQuery, String keyword, Long orgId, Long categoryId, AuthUser user) {
        requireAction(resource, "read", user);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("tenantId", user.tenantId()); query.put("keyword", keyword == null ? null : keyword.trim());
        query.put("orgId", resource.equals("users") ? orgId : null); query.put("categoryId", resource.equals("params") ? categoryId : null);
        query.put("offset", (pageQuery.page() - 1) * pageQuery.size()); query.put("limit", pageQuery.size());
        List<Map<String, Object>> rows = repository.list(resource, query);
        rows.forEach(row -> decorate(resource, row, user.tenantId()));
        long total = repository.count(resource, query);
        return new SystemPage<>(rows, total, pageQuery.page(), pageQuery.size());
    }

    @Transactional
    public Map<String, Object> create(String resource, Map<String, Object> input, AuthUser user) {
        requireAction(resource, "create", user);
        Map<String, Object> fields = allowedFields(resource, input, true);
        validateOrganizationParent(resource, null, fields, user.tenantId());
        encodeUserPassword(resource, fields, false);
        fields.put("tenant_id", user.tenantId());
        fields.put("id", nextId());
        repository.insert(resource, fields);
        audit(user, "system:" + resource + ":create");
        return findById(resource, ((Number) fields.get("id")).longValue(), user.tenantId());
    }

    @Transactional
    public Map<String, Object> update(String resource, long id, Map<String, Object> input, AuthUser user) {
        requireAction(resource, "update", user);
        Map<String, Object> fields = allowedFields(resource, input, false);
        encodeUserPassword(resource, fields, true);
        validateOrganizationParent(resource, id, fields, user.tenantId());
        if (fields.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "No editable fields");
        fields.put("id", id); fields.put("tenantId", user.tenantId());
        int changed = repository.update(resource, fields);
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Resource not found");
        audit(user, "system:" + resource + ":update");
        return findById(resource, id, user.tenantId());
    }

    private void encodeUserPassword(String resource, Map<String, Object> fields, boolean allowBlank) {
        if (!resource.equals("users") || !fields.containsKey("password")) return;
        String password = String.valueOf(fields.remove("password"));
        if (allowBlank && password.isBlank()) return;
        fields.put("password_hash", passwordEncoder.encode(password));
    }

    @Transactional
    public void updateStatus(String resource, long id, int status, AuthUser user) {
        requireAction(resource, "update", user);
        int changed = repository.updateStatus(resource, id, user.tenantId(), status);
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Resource not found");
        audit(user, "system:" + resource + ":status");
    }

    @Transactional
    public Map<String, Object> uploadAvatar(long id, MultipartFile file, AuthUser user) {
        requireAction("users", "update", user);
        validateImage(file);
        String oldKey = findAvatarObjectKey(id, user.tenantId());
        String objectKey = "avatars/" + user.tenantId() + "/" + id + "/" + UUID.randomUUID() + extension(file.getContentType());
        try {
            storage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        }
        repository.mapper().updateAvatar(id, user.tenantId(), objectKey);
        deleteAvatarObject(oldKey);
        audit(user, "system:users:avatar");
        return findUser(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> clearAvatar(long id, AuthUser user) {
        requireAction("users", "update", user);
        String oldKey = findAvatarObjectKey(id, user.tenantId());
        repository.mapper().updateAvatar(id, user.tenantId(), null);
        deleteAvatarObject(oldKey);
        audit(user, "system:users:avatar-delete");
        return findUser(id, user.tenantId());
    }

    private String findAvatarObjectKey(long id, long tenantId) {
        return repository.mapper().selectAvatarObjectKey(id, tenantId);
    }

    private void deleteAvatarObject(String objectKey) {
        if (objectKey != null && !objectKey.isBlank()) storage.delete(objectKey);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择头像文件");
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException(ErrorCode.BAD_REQUEST, "头像不能超过 2MB");
        String type = file.getContentType();
        if (type == null || !Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(type.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像仅支持 JPG、PNG、GIF 或 WebP");
        }
    }

    private String extension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private Map<String, Object> findUser(long id, long tenantId) {
        return findById("users", id, tenantId);
    }

    private void validateOrganizationParent(String resource, Long id, Map<String, Object> fields, long tenantId) {
        if (!resource.equals("orgs") || !fields.containsKey("parent_id")) return;
        Long parentId = toLong(fields.get("parent_id"));
        if (parentId == null || parentId == 0) return;
        if (id != null && id.equals(parentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上级组织不能选择当前组织");
        }
        if (repository.mapper().countOrganization(parentId, tenantId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上级组织不存在或不属于当前租户");
        }
        Set<Long> visited = new HashSet<>();
        long cursor = parentId;
        while (cursor != 0) {
            if (id != null && id == cursor) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上级组织不能选择当前组织的下级组织");
            }
            if (!visited.add(cursor)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "组织层级存在循环关系，请先修复组织数据");
            }
            Long next = repository.mapper().selectOrganizationParent(cursor, tenantId);
            cursor = next == null ? 0 : next;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw new BusinessException(ErrorCode.BAD_REQUEST, "上级组织编号无效"); }
    }

    @Transactional
    public void delete(String resource, long id, AuthUser user) {
        requireAction(resource, "delete", user);
        int changed = repository.delete(resource, id, user.tenantId());
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Resource not found");
        if (resource.equals("roles")) {
            repository.mapper().deleteRoleLinks(id, user.tenantId()); repository.mapper().deleteRoleMenus(id, user.tenantId()); repository.mapper().deleteRolePermissions(id, user.tenantId());
        }
        audit(user, "system:" + resource + ":delete");
    }

    public void requireAction(String resource, String action, AuthUser user) {
        String permission = basePermission(resource);
        String actionPermission = "read".equals(action) ? permission : permission + ":" + action;
        if (repository.mapper().countPermittedAction(user.id(), user.tenantId(), actionPermission, action) == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + permissionLabel(resource) + "的" + actionLabel(action) + "权限");
    }

    private String permissionLabel(String resource) {
        return switch (resource) {
            case "users" -> "用户管理";
            case "roles" -> "角色管理";
            case "role-permissions" -> "权限维护";
            case "orgs" -> "组织架构";
            case "menus" -> "菜单管理";
            case "params", "param-categories" -> "参数管理";
            case "dicts" -> "字典管理";
            case "configs" -> "系统配置";
            case "form-metadata" -> "输入项配置";
            default -> "该操作";
        };
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "read" -> "查看";
            case "create" -> "新增";
            case "delete" -> "删除";
            default -> "编辑";
        };
    }

    private String basePermission(String resource) {
        return switch (resource) {
            case "users" -> "system:user:list";
            case "roles", "role-permissions" -> "system:role:list";
            case "orgs" -> "system:org:list";
            case "menus" -> "system:menu:list";
            case "params", "param-categories" -> "system:param:list";
            case "dicts" -> "system:dict:list";
            case "configs" -> "system:config:list";
            case "form-metadata" -> "system:form-config:list";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported system resource");
        };
    }

    public List<Map<String, Object>> roleOptions(AuthUser user) {
        requireAction("roles", "read", user);
        return repository.mapper().selectEnabledRoles(user.tenantId());
    }

    public List<Long> userRoleIds(long userId, AuthUser user) {
        requireAction("users", "read", user);
        return repository.mapper().selectUserRoleIds(userId, user.tenantId());
    }

    @Transactional
    public void saveUserRoles(long userId, List<?> roleIds, AuthUser user) {
        requireAction("users", "update", user);
        if (repository.mapper().countActiveUser(userId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        repository.mapper().deleteUserRoles(userId, user.tenantId());
        for (Long roleId : longSet(roleIds)) repository.mapper().insertUserRoleIfEnabled(userId, roleId, user.tenantId());
        audit(user, "system:users:roles");
    }

    public Map<String, Object> permissionCatalog(AuthUser user) {
        requireAction("roles", "read", user);
        List<Map<String, Object>> menus = repository.mapper().selectPermissionMenus(user.tenantId());
        for (Map<String, Object> menu : menus) menu.put("actions", repository.mapper().selectMenuActions(((Number) menu.get("id")).longValue(), user.tenantId()));
        return Map.of("menus", menus);
    }

    public SystemPage<Map<String, Object>> permissions(PageQuery pageQuery, String keyword, Integer status, AuthUser user) {
        requireAction("role-permissions", "read", user);
        Map<String, Object> query = new LinkedHashMap<>(); query.put("tenantId", user.tenantId()); query.put("keyword", keyword == null ? null : keyword.trim());
        if (status != null) {
            if (status != 0 && status != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限状态无效");
            query.put("status", status);
        }
        query.put("offset", (pageQuery.page() - 1) * pageQuery.size()); query.put("limit", pageQuery.size());
        return new SystemPage<>(repository.mapper().selectPermissions(query), repository.mapper().countPermissions(query), pageQuery.page(), pageQuery.size());
    }

    @Transactional
    public Map<String, Object> createPermission(Map<String, Object> input, AuthUser user) {
        requireAction("role-permissions", "create", user);
        long menuId = requiredPositiveLong(input.get("menu_id"), "所属菜单");
        String actionCode = requiredCode(input.get("action_code"), "动作编码", ACTION_CODE);
        String permissionCode = requiredCode(input.get("permission_code"), "权限编码", PERMISSION_CODE);
        String permissionName = requiredText(input.get("permission_name"), "权限名称", 64);
        validatePermissionMenu(menuId, user.tenantId());
        if (repository.mapper().countPermissionDuplicate(user.tenantId(), permissionCode, menuId, actionCode) > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限编码或菜单动作已存在");
        long id = nextId();
        Map<String, Object> values = new LinkedHashMap<>(); values.put("id", id); values.put("tenantId", user.tenantId()); values.put("menu_id", menuId); values.put("action_code", actionCode); values.put("permission_code", permissionCode); values.put("permission_name", permissionName); values.put("status", optionalStatus(input.get("status"), 1));
        repository.mapper().insertPermission(values);
        audit(user, "system:permissions:create");
        return permission(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePermission(long permissionId, Map<String, Object> input, AuthUser user) {
        requireAction("role-permissions", "update", user);
        ensurePermission(permissionId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("id", permissionId); values.put("tenantId", user.tenantId());
        if (input.containsKey("menu_id")) {
            long menuId = requiredPositiveLong(input.get("menu_id"), "所属菜单");
            validatePermissionMenu(menuId, user.tenantId());
            values.put("menu_id", menuId);
        }
        if (input.containsKey("permission_name")) {
            values.put("permission_name", requiredText(input.get("permission_name"), "权限名称", 64));
        }
        if (input.containsKey("status")) {
            values.put("status", optionalStatus(input.get("status"), 1));
        }
        if (values.size() == 2) throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可修改的权限字段，权限编码和动作编码创建后不可修改");
        repository.mapper().updatePermission(values);
        audit(user, "system:permissions:update");
        return permission(permissionId, user.tenantId());
    }

    @Transactional
    public void updatePermissionStatus(long permissionId, int status, AuthUser user) {
        requireAction("role-permissions", "update", user);
        if (status != 0 && status != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限状态无效");
        int changed = repository.mapper().updatePermissionStatus(permissionId, user.tenantId(), status);
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
        audit(user, "system:permissions:status");
    }

    @Transactional
    public void deletePermission(long permissionId, AuthUser user) {
        requireAction("role-permissions", "delete", user);
        ensurePermission(permissionId, user.tenantId());
        if (repository.mapper().countPermissionReferences(permissionId, user.tenantId()) > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限已被角色引用，不能删除");
        repository.mapper().deletePermission(permissionId, user.tenantId());
        audit(user, "system:permissions:delete");
    }

    private Map<String, Object> permission(long permissionId, long tenantId) {
        return repository.mapper().selectPermission(permissionId, tenantId);
    }

    private void ensurePermission(long permissionId, long tenantId) {
        if (repository.mapper().countPermission(permissionId, tenantId) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
    }

    private void validatePermissionMenu(long menuId, long tenantId) {
        if (repository.mapper().countMenu(menuId, tenantId) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "所属菜单不存在");
    }

    private long requiredPositiveLong(Object value, String label) {
        Long result = toLong(value);
        if (result == null || result <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能为空");
        return result;
    }

    private String requiredCode(Object value, String label, Pattern pattern) {
        String result = requiredText(value, label, 160).toLowerCase();
        if (!pattern.matcher(result).matches()) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "格式无效");
        return result;
    }

    private String requiredText(Object value, String label, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.isEmpty() || result.length() > maxLength) throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能为空且不能超过" + maxLength + "个字符");
        return result;
    }

    private int optionalStatus(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try {
            int status = Integer.parseInt(String.valueOf(value));
            if (status != 0 && status != 1) throw new NumberFormatException();
            return status;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "权限状态无效");
        }
    }

    public Map<String, Object> rolePermissions(long roleId, AuthUser user) {
        requireAction("roles", "read", user);
        if (repository.mapper().countRole(roleId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在");
        return Map.of("permissionIds", repository.mapper().selectRolePermissionIds(roleId, user.tenantId()));
    }

    @Transactional
    public void saveRolePermissions(long roleId, List<?> permissionIds, AuthUser user) {
        requireAction("roles", "update", user);
        if (repository.mapper().countRole(roleId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在");
        Set<Long> permissions = longSet(permissionIds);
        repository.mapper().deleteRolePermissionLinks(roleId, user.tenantId());
        Set<Long> menus = new HashSet<>();
        for (Long permissionId : permissions) {
            if (repository.mapper().countEnabledPermission(permissionId, user.tenantId()) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
            repository.mapper().insertRolePermission(roleId, permissionId, user.tenantId());
            Long menuId = repository.mapper().selectPermissionMenuId(permissionId, user.tenantId());
            if (menuId != null) menus.add(menuId);
        }
        Set<Long> allMenus = new HashSet<>(menus);
        for (Long menuId : menus) {
            Long cursor = menuId;
            while (cursor != null && cursor != 0) {
                cursor = repository.mapper().selectMenuParent(cursor, user.tenantId());
                if (cursor != null && cursor != 0) allMenus.add(cursor);
            }
        }
        repository.mapper().deleteRoleMenuLinks(roleId, user.tenantId());
        for (Long menuId : allMenus) repository.mapper().insertRoleMenu(roleId, menuId, user.tenantId());
        audit(user, "system:roles:permissions");
    }

    private Set<Long> longSet(List<?> values) {
        Set<Long> ids = new HashSet<>();
        if (values != null) for (Object value : values) { Long id = toLong(value); if (id != null) ids.add(id); }
        return ids;
    }

    private Map<String, Object> allowedFields(String resource, Map<String, Object> input, boolean create) {
        Set<String> allowed = switch (resource) {
            case "users" -> create ? Set.of("username", "password", "display_name", "mobile_phone", "org_id", "status") : Set.of("password", "display_name", "mobile_phone", "org_id", "status");
            case "roles" -> create ? Set.of("role_code", "role_name", "status") : Set.of("role_name", "status");
            case "orgs" -> create ? Set.of("parent_id", "org_code", "org_name", "sort_no", "status") : Set.of("parent_id", "org_name", "sort_no", "status");
            case "menus" -> create ? Set.of("parent_id", "menu_type", "menu_name", "route_name", "route_path", "component_path", "permission_code", "icon", "sort_no", "visible", "status") : Set.of("parent_id", "menu_name", "route_name", "route_path", "component_path", "permission_code", "icon", "sort_no", "visible", "status");
            case "param-categories", "dicts" -> create ? Set.of("dict_code", "dict_name", "status") : Set.of("dict_name", "status");
            case "params" -> create ? Set.of("category_id", "config_key", "config_value", "config_type", "status", "remark") : Set.of("category_id", "config_value", "config_type", "status", "remark");
            case "configs" -> create ? Set.of("config_key", "config_value", "config_type", "remark") : Set.of("config_value", "config_type", "remark");
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported system resource");
        };
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> { if (allowed.contains(key) && value != null) result.put(key, value); });
        return result;
    }

    private Map<String, Object> findById(String resource, long id, long tenantId) {
        Map<String, Object> row = repository.find(resource, id, tenantId);
        decorate(resource, row, tenantId);
        return row;
    }

    private void decorate(String resource, Map<String, Object> row, long tenantId) {
        maskSensitive(resource, row);
        if (resource.equals("params")) { row.put("category_name", repository.mapper().selectDictionaryName(((Number) row.get("category_id")).longValue(), tenantId)); }
        if (!resource.equals("users")) return;
        Object orgId = row.get("org_id");
        row.put("org_name", orgId == null ? null : repository.mapper().selectOrganizationName(((Number) orgId).longValue(), tenantId));
        row.put("avatar_url", storage.presignedUrl((String) row.get("avatar_object_key")));
        row.remove("avatar_object_key");
    }

    private void maskSensitive(String resource, Map<String, Object> row) {
        if (!resource.equals("configs") && !resource.equals("params")) return;
        String key = String.valueOf(row.getOrDefault("config_key", "")).toLowerCase();
        if (key.contains("secret") || key.contains("password") || key.contains("token") || key.contains("key")) row.put("config_value", "******");
    }

    private void audit(AuthUser user, String operation) {
        if (OperationAuditContext.capture(operation, targetType(operation), null, null)) return;
        repository.mapper().insertAudit(nextId(), user.tenantId(), user.id(), operation);
    }

    private String targetType(String operation) {
        String[] segments = operation.split(":", 4);
        return segments.length > 1 ? segments[1] : null;
    }

    public void auditOperation(AuthUser user, String operation) {
        audit(user, operation);
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
