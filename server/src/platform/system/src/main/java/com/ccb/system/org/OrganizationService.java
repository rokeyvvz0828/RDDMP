package com.ccb.system.org;

import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {
    private final OrganizationRepository repository;
    private final MinioStorageService storage;

    public OrganizationService(OrganizationRepository repository, MinioStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public List<OrgTreeNode> tree(AuthUser user) {
        List<OrgTreeNode> nodes = repository.organizations(user.tenantId()).stream().map(row -> new OrgTreeNode(
                ((Number) row.get("id")).longValue(), ((Number) row.get("parent_id")).longValue(),
                (String) row.get("org_code"), (String) row.get("org_name"), ((Number) row.get("sort_no")).intValue(),
                ((Number) row.get("status")).intValue(), new ArrayList<>(), new ArrayList<>())).toList();
        Map<Long, OrgTreeNode> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        repository.users(user.tenantId()).forEach(row -> {
            OrgTreeNode node = byId.get(((Number) row.get("org_id")).longValue());
            if (node != null) node.users().add(new OrgUserSummary(((Number) row.get("id")).longValue(), (String) row.get("username"),
                    (String) row.get("display_name"), ((Number) row.get("org_id")).longValue(), (String) row.get("org_name"),
                    storage.presignedUrl((String) row.get("avatar_object_key")), ((Number) row.get("status")).intValue()));
        });
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
