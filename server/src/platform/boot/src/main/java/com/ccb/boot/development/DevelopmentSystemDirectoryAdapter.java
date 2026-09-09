package com.ccb.boot.development;

import com.ccb.architecture.integration.PhysicalSystemDirectoryQuery;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.development.integration.DevelopmentSystemDirectory;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DevelopmentSystemDirectoryAdapter implements DevelopmentSystemDirectory {
    private final PhysicalSystemDirectoryQuery systems;

    public DevelopmentSystemDirectoryAdapter(PhysicalSystemDirectoryQuery systems) { this.systems = systems; }

    @Override
    public PageResult<SystemRef> searchManageable(AuthUser actor, long projectId, PageQuery page, String keyword) {
        var result = systems.searchManageable(actor, projectId, page, keyword);
        return new PageResult<>(result.records().stream().map(DevelopmentSystemDirectoryAdapter::convert).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public Optional<SystemRef> find(AuthUser actor, long projectId, long systemId) {
        return systems.find(actor, projectId, systemId).map(DevelopmentSystemDirectoryAdapter::convert);
    }

    private static SystemRef convert(PhysicalSystemDirectoryQuery.SystemRef system) {
        return new SystemRef(system.id(), system.code(), system.name(), system.ownerId(), system.status(), system.rowVersion());
    }
}
