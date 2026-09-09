package com.ccb.boot.development;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.integration.DevelopmentSourceDirectory;
import com.ccb.requirement.integration.RequirementDevelopmentQuery;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class DevelopmentSourceDirectoryAdapter implements DevelopmentSourceDirectory {
    private final RequirementDevelopmentQuery sources;
    private final ProjectAccessService projects;

    public DevelopmentSourceDirectoryAdapter(RequirementDevelopmentQuery sources, ProjectAccessService projects) {
        this.sources = sources;
        this.projects = projects;
    }

    @Override
    public PageResult<SourceRequirement> search(AuthUser actor, SourceQuery query) {
        var project = projects.requireAccessible(query.projectRef(), actor);
        var result = sources.search(actor, new RequirementDevelopmentQuery.Query(project.projectRef(),
                query.systemCodes(), query.keyword(), query.page()));
        return new PageResult<>(result.records().stream().map(source -> convert(source, project.id())).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public SourceRequirement requireCurrent(AuthUser actor, String projectRef, long requirementId) {
        var project = projects.requireAccessible(projectRef, actor);
        var source = sources.find(actor, project.projectRef(), requirementId)
                .filter(RequirementDevelopmentQuery.SourceRequirement::active)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "需求来源已失效或不属于当前项目，请刷新后重试"));
        return convert(source, project.id());
    }

    private static SourceRequirement convert(RequirementDevelopmentQuery.SourceRequirement source, long platformProjectId) {
        return new SourceRequirement(source.id(), source.number(), source.name(), source.summary(), platformProjectId,
                source.revision(), source.active(), source.systems().stream().map(system -> new SourceSystem(system.systemCode(),
                system.roles().stream().map(role -> Role.valueOf(role.name())).collect(Collectors.toSet()),
                system.suggestedOwnerId())).toList());
    }
}
