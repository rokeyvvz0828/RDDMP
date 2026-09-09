package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.common.audit.OperationAuditContext;
import com.ccb.system.model.SystemPage;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private record Spec(String table, String select, String orderBy, Set<String> createFields, Set<String> updateFields) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MinioStorageService storage;

    private final Map<String, Spec> specs = Map.of(
            "users", new Spec("sys_user", "id, username, display_name, mobile_phone, org_id, avatar_object_key, status, last_login_at, created_at", "id DESC", Set.of("username", "password", "display_name", "mobile_phone", "org_id", "status"), Set.of("password", "display_name", "mobile_phone", "org_id", "status")),
            "roles", new Spec("sys_role", "id, role_code, role_name, status, created_at", "id DESC", Set.of("role_code", "role_name", "status"), Set.of("role_name", "status")),
            "orgs", new Spec("sys_org", "id, parent_id, org_code, org_name, sort_no, status, created_at", "sort_no, id", Set.of("parent_id", "org_code", "org_name", "sort_no", "status"), Set.of("parent_id", "org_name", "sort_no", "status")),
            "menus", new Spec("sys_menu", "id, parent_id, menu_type, menu_name, route_name, route_path, component_path, permission_code, icon, sort_no, visible, status", "parent_id, sort_no, id", Set.of("parent_id", "menu_type", "menu_name", "route_name", "route_path", "component_path", "permission_code", "icon", "sort_no", "visible", "status"), Set.of("parent_id", "menu_name", "route_name", "route_path", "component_path", "permission_code", "icon", "sort_no", "visible", "status")),
            "param-categories", new Spec("sys_dict_type", "id, dict_code, dict_name, status, created_at", "id DESC", Set.of("dict_code", "dict_name", "status"), Set.of("dict_name", "status")),
            "params", new Spec("sys_config", "id, category_id, config_key, config_value, config_type, status, remark, created_at, updated_at", "id DESC", Set.of("category_id", "config_key", "config_value", "config_type", "status", "remark"), Set.of("category_id", "config_value", "config_type", "status", "remark")),
            "dicts", new Spec("sys_dict_type", "id, dict_code, dict_name, status, created_at", "id DESC", Set.of("dict_code", "dict_name", "status"), Set.of("dict_name", "status")),
            "configs", new Spec("sys_config", "id, config_key, config_value, config_type, remark, created_at, updated_at", "id DESC", Set.of("config_key", "config_value", "config_type", "remark"), Set.of("config_value", "config_type", "remark"))
    );

    public SystemService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, MinioStorageService storage) {
        this.jdbc = jdbc;
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
        Spec spec = spec(resource);
        String filter = keyword == null || keyword.isBlank() ? "" : " AND (CAST(id AS CHAR) LIKE ? OR " + keywordColumn(resource) + " LIKE ?)";
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        if (!filter.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (resource.equals("users") && orgId != null) { filter += " AND org_id = ?"; args.add(orgId); }
        if (resource.equals("params") && categoryId != null) { filter += " AND category_id = ?"; args.add(categoryId); }
        args.add((pageQuery.page() - 1) * pageQuery.size());
        args.add(pageQuery.size());
        String sql = "SELECT " + spec.select() + " FROM " + spec.table() + " WHERE tenant_id = ? AND deleted = 0" + filter + " ORDER BY " + spec.orderBy() + " LIMIT ?, ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        rows.forEach(row -> decorate(resource, row, user.tenantId()));
        List<Object> countArgs = new ArrayList<>(args.subList(0, args.size() - 2));
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + spec.table() + " WHERE tenant_id = ? AND deleted = 0" + filter, Long.class, countArgs.toArray());
        return new SystemPage<>(rows, total, pageQuery.page(), pageQuery.size());
    }

    @Transactional
    public Map<String, Object> create(String resource, Map<String, Object> input, AuthUser user) {
        requireAction(resource, "create", user);
        Spec spec = spec(resource);
        Map<String, Object> fields = allowedFields(spec.createFields(), input);
        validateOrganizationParent(resource, null, fields, user.tenantId());
        encodeUserPassword(resource, fields, false);
        fields.put("tenant_id", user.tenantId());
        fields.put("id", nextId());
        insert(spec.table(), fields);
        audit(user, "system:" + resource + ":create");
        return findById(spec, fields.get("id"), user.tenantId(), resource);
    }

    @Transactional
    public Map<String, Object> update(String resource, long id, Map<String, Object> input, AuthUser user) {
        requireAction(resource, "update", user);
        Spec spec = spec(resource);
        Map<String, Object> fields = allowedFields(spec.updateFields(), input);
        encodeUserPassword(resource, fields, true);
        validateOrganizationParent(resource, id, fields, user.tenantId());
        if (fields.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "No editable fields");
        List<Object> args = new ArrayList<>();
        String assignments = fields.keySet().stream().map(key -> key + " = ?").reduce((a, b) -> a + ", " + b).orElseThrow();
        fields.values().forEach(args::add);
        args.add(id);
        args.add(user.tenantId());
        int changed = jdbc.update("UPDATE " + spec.table() + " SET " + assignments + " WHERE id = ? AND tenant_id = ? AND deleted = 0", args.toArray());
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Resource not found");
        audit(user, "system:" + resource + ":update");
        return findById(spec, id, user.tenantId(), resource);
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
        Spec spec = spec(resource);
        int changed = jdbc.update("UPDATE " + spec.table() + " SET status = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", status, id, user.tenantId());
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
        jdbc.update("UPDATE sys_user SET avatar_object_key = ? WHERE id = ? AND tenant_id = ? AND deleted = 0", objectKey, id, user.tenantId());
        deleteAvatarObject(oldKey);
        audit(user, "system:users:avatar");
        return findUser(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> clearAvatar(long id, AuthUser user) {
        requireAction("users", "update", user);
        String oldKey = findAvatarObjectKey(id, user.tenantId());
        jdbc.update("UPDATE sys_user SET avatar_object_key = NULL WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        deleteAvatarObject(oldKey);
        audit(user, "system:users:avatar-delete");
        return findUser(id, user.tenantId());
    }

    private String findAvatarObjectKey(long id, long tenantId) {
        return jdbc.queryForObject(
                "SELECT avatar_object_key FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0",
                String.class,
                id,
                tenantId);
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
        return findById(spec("users"), id, tenantId, "users");
    }

    private Spec spec(String resource) {
        Spec spec = specs.get(resource);
        if (spec == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported system resource");
        return spec;
    }

    private void validateOrganizationParent(String resource, Long id, Map<String, Object> fields, long tenantId) {
        if (!resource.equals("orgs") || !fields.containsKey("parent_id")) return;
        Long parentId = toLong(fields.get("parent_id"));
        if (parentId == null || parentId == 0) return;
        if (id != null && id.equals(parentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上级组织不能选择当前组织");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_org WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, parentId, tenantId);
        if (count == null || count == 0) {
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
            Long next = jdbc.query("SELECT parent_id FROM sys_org WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getLong("parent_id") : null, cursor, tenantId);
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
        Spec spec = spec(resource);
        int changed = jdbc.update("UPDATE " + spec.table() + " SET deleted = 1 WHERE id = ? AND tenant_id = ? AND deleted = 0", id, user.tenantId());
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "Resource not found");
        if (resource.equals("roles")) {
            jdbc.update("DELETE FROM sys_user_role WHERE role_id = ? AND tenant_id = ?", id, user.tenantId());
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ? AND tenant_id = ?", id, user.tenantId());
            jdbc.update("DELETE FROM sys_role_permission WHERE role_id = ? AND tenant_id = ?", id, user.tenantId());
        }
        audit(user, "system:" + resource + ":delete");
    }

    public void requireAction(String resource, String action, AuthUser user) {
        String permission = basePermission(resource);
        String actionPermission = "read".equals(action) ? permission : permission + ":" + action;
        Integer allowed = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.action_code = ? AND p.status = 1 AND r.status = 1", Integer.class, user.id(), user.tenantId(), actionPermission, action);
        if (allowed == null || allowed == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + permissionLabel(resource) + "的" + actionLabel(action) + "权限");
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
        return jdbc.queryForList("SELECT id, role_code, role_name FROM sys_role WHERE tenant_id = ? AND deleted = 0 AND status = 1 ORDER BY id", user.tenantId());
    }

    public List<Long> userRoleIds(long userId, AuthUser user) {
        requireAction("users", "read", user);
        return jdbc.queryForList("SELECT role_id FROM sys_user_role WHERE user_id = ? AND tenant_id = ? ORDER BY role_id", Long.class, userId, user.tenantId());
    }

    @Transactional
    public void saveUserRoles(long userId, List<?> roleIds, AuthUser user) {
        requireAction("users", "update", user);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, userId, user.tenantId());
        if (exists == null || exists == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ? AND tenant_id = ?", userId, user.tenantId());
        for (Long roleId : longSet(roleIds)) jdbc.update("INSERT INTO sys_user_role (user_id, role_id, tenant_id) SELECT ?, id, ? FROM sys_role WHERE id = ? AND tenant_id = ? AND deleted = 0 AND status = 1", userId, user.tenantId(), roleId, user.tenantId());
        audit(user, "system:users:roles");
    }

    public Map<String, Object> permissionCatalog(AuthUser user) {
        requireAction("roles", "read", user);
        List<Map<String, Object>> menus = jdbc.queryForList("SELECT id, parent_id, menu_name, menu_type, route_path, permission_code, icon, sort_no FROM sys_menu WHERE tenant_id = ? AND deleted = 0 ORDER BY parent_id, sort_no, id", user.tenantId());
        for (Map<String, Object> menu : menus) menu.put("actions", jdbc.queryForList("SELECT id, action_code, permission_code, permission_name FROM sys_menu_permission WHERE tenant_id = ? AND menu_id = ? AND status = 1 ORDER BY id", user.tenantId(), menu.get("id")));
        return Map.of("menus", menus);
    }

    public SystemPage<Map<String, Object>> permissions(PageQuery pageQuery, String keyword, Integer status, AuthUser user) {
        requireAction("role-permissions", "read", user);
        String filter = keyword == null || keyword.isBlank()
                ? ""
                : " AND (p.permission_code LIKE ? OR p.permission_name LIKE ? OR m.menu_name LIKE ?)";
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        if (!filter.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            if (status != 0 && status != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限状态无效");
            filter += " AND p.status = ?";
            args.add(status);
        }
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add((pageQuery.page() - 1) * pageQuery.size());
        listArgs.add(pageQuery.size());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.menu_id, m.menu_name, p.action_code, p.permission_code, p.permission_name, p.status, p.created_at, p.updated_at " +
                        "FROM sys_menu_permission p JOIN sys_menu m ON m.id = p.menu_id AND m.tenant_id = p.tenant_id AND m.deleted = 0 " +
                        "WHERE p.tenant_id = ?" + filter + " ORDER BY m.sort_no, p.menu_id, p.id LIMIT ?, ?",
                listArgs.toArray());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu_permission p JOIN sys_menu m ON m.id = p.menu_id AND m.tenant_id = p.tenant_id AND m.deleted = 0 " +
                        "WHERE p.tenant_id = ?" + filter,
                Long.class, args.toArray());
        return new SystemPage<>(rows, total == null ? 0 : total, pageQuery.page(), pageQuery.size());
    }

    @Transactional
    public Map<String, Object> createPermission(Map<String, Object> input, AuthUser user) {
        requireAction("role-permissions", "create", user);
        long menuId = requiredPositiveLong(input.get("menu_id"), "所属菜单");
        String actionCode = requiredCode(input.get("action_code"), "动作编码", ACTION_CODE);
        String permissionCode = requiredCode(input.get("permission_code"), "权限编码", PERMISSION_CODE);
        String permissionName = requiredText(input.get("permission_name"), "权限名称", 64);
        validatePermissionMenu(menuId, user.tenantId());
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = ? AND (permission_code = ? OR (menu_id = ? AND action_code = ?))",
                Integer.class, user.tenantId(), permissionCode, menuId, actionCode);
        if (duplicate != null && duplicate > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限编码或菜单动作已存在");
        long id = nextId();
        jdbc.update("INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, user.tenantId(), menuId, actionCode, permissionCode, permissionName, optionalStatus(input.get("status"), 1));
        audit(user, "system:permissions:create");
        return permission(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updatePermission(long permissionId, Map<String, Object> input, AuthUser user) {
        requireAction("role-permissions", "update", user);
        ensurePermission(permissionId, user.tenantId());
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (input.containsKey("menu_id")) {
            long menuId = requiredPositiveLong(input.get("menu_id"), "所属菜单");
            validatePermissionMenu(menuId, user.tenantId());
            assignments.add("menu_id = ?");
            args.add(menuId);
        }
        if (input.containsKey("permission_name")) {
            assignments.add("permission_name = ?");
            args.add(requiredText(input.get("permission_name"), "权限名称", 64));
        }
        if (input.containsKey("status")) {
            assignments.add("status = ?");
            args.add(optionalStatus(input.get("status"), 1));
        }
        if (assignments.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可修改的权限字段，权限编码和动作编码创建后不可修改");
        args.add(permissionId);
        args.add(user.tenantId());
        jdbc.update("UPDATE sys_menu_permission SET " + String.join(", ", assignments) + " WHERE id = ? AND tenant_id = ?", args.toArray());
        audit(user, "system:permissions:update");
        return permission(permissionId, user.tenantId());
    }

    @Transactional
    public void updatePermissionStatus(long permissionId, int status, AuthUser user) {
        requireAction("role-permissions", "update", user);
        if (status != 0 && status != 1) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限状态无效");
        int changed = jdbc.update("UPDATE sys_menu_permission SET status = ? WHERE id = ? AND tenant_id = ?", status, permissionId, user.tenantId());
        if (changed == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
        audit(user, "system:permissions:status");
    }

    @Transactional
    public void deletePermission(long permissionId, AuthUser user) {
        requireAction("role-permissions", "delete", user);
        ensurePermission(permissionId, user.tenantId());
        Integer references = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = ? AND tenant_id = ?) + " +
                        "(SELECT COUNT(*) FROM pm_project_role_permission WHERE permission_id = ? AND tenant_id = ?)",
                Integer.class, permissionId, user.tenantId(), permissionId, user.tenantId());
        if (references != null && references > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限已被角色引用，不能删除");
        jdbc.update("DELETE FROM sys_menu_permission WHERE id = ? AND tenant_id = ?", permissionId, user.tenantId());
        audit(user, "system:permissions:delete");
    }

    private Map<String, Object> permission(long permissionId, long tenantId) {
        return jdbc.queryForMap(
                "SELECT p.id, p.menu_id, m.menu_name, p.action_code, p.permission_code, p.permission_name, p.status, p.created_at, p.updated_at " +
                        "FROM sys_menu_permission p JOIN sys_menu m ON m.id = p.menu_id AND m.tenant_id = p.tenant_id AND m.deleted = 0 " +
                        "WHERE p.id = ? AND p.tenant_id = ?",
                permissionId, tenantId);
    }

    private void ensurePermission(long permissionId, long tenantId) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission WHERE id = ? AND tenant_id = ?", Integer.class, permissionId, tenantId);
        if (exists == null || exists == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
    }

    private void validatePermissionMenu(long menuId, long tenantId) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, menuId, tenantId);
        if (exists == null || exists == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "所属菜单不存在");
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
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, roleId, user.tenantId());
        if (exists == null || exists == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在");
        return Map.of("permissionIds", jdbc.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id = ? AND tenant_id = ? ORDER BY permission_id", Long.class, roleId, user.tenantId()));
    }

    @Transactional
    public void saveRolePermissions(long roleId, List<?> permissionIds, AuthUser user) {
        requireAction("roles", "update", user);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, roleId, user.tenantId());
        if (exists == null || exists == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在");
        Set<Long> permissions = longSet(permissionIds);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = ? AND tenant_id = ?", roleId, user.tenantId());
        Set<Long> menus = new HashSet<>();
        for (Long permissionId : permissions) {
            Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu_permission WHERE id = ? AND tenant_id = ? AND status = 1", Integer.class, permissionId, user.tenantId());
            if (valid == null || valid == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "权限不存在");
            jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id, tenant_id) VALUES (?, ?, ?)", roleId, permissionId, user.tenantId());
            Long menuId = jdbc.queryForObject("SELECT menu_id FROM sys_menu_permission WHERE id = ? AND tenant_id = ?", Long.class, permissionId, user.tenantId());
            if (menuId != null) menus.add(menuId);
        }
        Set<Long> allMenus = new HashSet<>(menus);
        for (Long menuId : menus) {
            Long cursor = menuId;
            while (cursor != null && cursor != 0) {
                cursor = jdbc.query("SELECT parent_id FROM sys_menu WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getLong("parent_id") : 0L, cursor, user.tenantId());
                if (cursor != null && cursor != 0) allMenus.add(cursor);
            }
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ? AND tenant_id = ?", roleId, user.tenantId());
        for (Long menuId : allMenus) jdbc.update("INSERT INTO sys_role_menu (role_id, menu_id, tenant_id) VALUES (?, ?, ?)", roleId, menuId, user.tenantId());
        audit(user, "system:roles:permissions");
    }

    private Set<Long> longSet(List<?> values) {
        Set<Long> ids = new HashSet<>();
        if (values != null) for (Object value : values) { Long id = toLong(value); if (id != null) ids.add(id); }
        return ids;
    }

    private String keywordColumn(String resource) {
        return switch (resource) {
            case "users" -> "username";
            case "roles" -> "role_name";
            case "orgs" -> "org_name";
            case "menus" -> "menu_name";
            case "dicts", "param-categories" -> "dict_name";
            case "configs", "params" -> "config_key";
            default -> "id";
        };
    }

    private Map<String, Object> allowedFields(Set<String> allowed, Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> { if (allowed.contains(key) && value != null) result.put(key, value); });
        return result;
    }

    private void insert(String table, Map<String, Object> fields) {
        String columns = String.join(", ", fields.keySet());
        String placeholders = fields.keySet().stream().map(key -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
        jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", fields.values().toArray());
    }

    private Map<String, Object> findById(Spec spec, Object id, long tenantId, String resource) {
        Map<String, Object> row = jdbc.queryForMap("SELECT " + spec.select() + " FROM " + spec.table() + " WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId);
        decorate(resource, row, tenantId);
        return row;
    }

    private void decorate(String resource, Map<String, Object> row, long tenantId) {
        maskSensitive(resource, row);
        if (resource.equals("params")) { row.put("category_name", jdbc.query("SELECT dict_name FROM sys_dict_type WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getString("dict_name") : null, row.get("category_id"), tenantId)); }
        if (!resource.equals("users")) return;
        Object orgId = row.get("org_id");
        row.put("org_name", orgId == null ? null : jdbc.query("SELECT org_name FROM sys_org WHERE id = ? AND tenant_id = ? AND deleted = 0", rs -> rs.next() ? rs.getString("org_name") : null, orgId, tenantId));
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
        jdbc.update("INSERT INTO sys_operation_log (id, tenant_id, operator_id, operation_code, request_method, success) VALUES (?, ?, ?, ?, 'SYSTEM', 1)", nextId(), user.tenantId(), user.id(), operation);
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
