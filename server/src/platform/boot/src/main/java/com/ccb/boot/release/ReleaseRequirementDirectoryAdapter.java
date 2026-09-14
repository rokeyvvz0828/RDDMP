package com.ccb.boot.release;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.integration.ReleaseRequirementDirectory;
import com.ccb.requirement.integration.ReleaseRequirementQuery;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class ReleaseRequirementDirectoryAdapter implements ReleaseRequirementDirectory {
    private final ReleaseRequirementQuery query;

    public ReleaseRequirementDirectoryAdapter(ReleaseRequirementQuery query) {
        this.query = query;
    }

    @Override
    public PageResult<Requirement> searchActive(AuthUser actor, String projectRef, PageQuery page, String keyword) {
        var result = query.searchActive(actor, projectRef, page, keyword);
        return new PageResult<>(result.records().stream().map(ReleaseRequirementDirectoryAdapter::requirement).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public Optional<List<Requirement>> resolveActive(AuthUser actor, String projectRef, Collection<String> numbers) {
        return query.resolveActive(actor, projectRef, numbers)
                .map(items -> items.stream().map(ReleaseRequirementDirectoryAdapter::requirement).toList());
    }

    private static Requirement requirement(ReleaseRequirementQuery.RequirementRef value) {
        return new Requirement(value.id(), value.number(), value.name(), value.status());
    }
}
