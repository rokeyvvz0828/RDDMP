package com.ccb.system.org;

import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {
    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;

    public OrganizationService(JdbcTemplate jdbc, MinioStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public List<OrgTreeNode> tree(AuthUser user) {
        List<OrgTreeNode> nodes = jdbc.query("""
                SELECT id, parent_id, org_code, org_name, sort_no, status
                FROM sys_org WHERE tenant_id = ? AND deleted = 0 ORDER BY sort_no, id
                """, (rs, rowNum) -> new OrgTreeNode(rs.getLong("id"), rs.getLong("parent_id"),
                        rs.getString("org_code"), rs.getString("org_name"), rs.getInt("sort_no"),
                        rs.getInt("status"), new ArrayList<>(), new ArrayList<>()), user.tenantId());
        Map<Long, OrgTreeNode> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        jdbc.query("""
                SELECT u.id, u.username, u.display_name, u.org_id, o.org_name, u.avatar_object_key, u.status
                FROM sys_user u LEFT JOIN sys_org o ON o.id = u.org_id AND o.tenant_id = u.tenant_id AND o.deleted = 0
                WHERE u.tenant_id = ? AND u.deleted = 0 ORDER BY u.display_name, u.id
                """, rs -> {
                    OrgTreeNode node = byId.get(rs.getLong("org_id"));
                    if (node != null) node.users().add(new OrgUserSummary(rs.getLong("id"), rs.getString("username"),
                            rs.getString("display_name"), rs.getLong("org_id"), rs.getString("org_name"),
                            storage.presignedUrl(rs.getString("avatar_object_key")), rs.getInt("status")));
                }, user.tenantId());
        List<OrgTreeNode> roots = new ArrayList<>();
        nodes.forEach(node -> {
            OrgTreeNode parent = byId.get(node.parentId());
            boolean cycle = false;
            long cursor = node.parentId();
            List<Long> visited = new ArrayList<>();
            while (!cycle && cursor != 0) {
                if (cursor == node.id() || visited.contains(cursor)) { cycle = true; break; }
                visited.add(cursor);
                OrgTreeNode ancestor = byId.get(cursor);
                if (ancestor == null) break;
                cursor = ancestor.parentId();
            }
            if (parent == null || node.parentId() == 0 || cycle) roots.add(node);
            else parent.children().add(node);
        });
        return roots;
    }
}