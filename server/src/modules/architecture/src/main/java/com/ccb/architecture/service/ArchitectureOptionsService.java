package com.ccb.architecture.service;

import com.ccb.architecture.model.LogicalSubsystemOption;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.OrganizationOption;
import com.ccb.architecture.model.ParameterOption;
import com.ccb.architecture.model.UserOption;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ArchitectureOptionsService {
    public static final String LOGICAL_RESOURCE = "logical-subsystem";
    public static final String PHYSICAL_RESOURCE = "physical-subsystem";

    private static final Set<String> LOGICAL_PARAMETER_CATEGORIES = Set.of(
            LogicalSubsystemService.DEPLOYMENT_PLATFORM_CATEGORY,
            LogicalSubsystemService.SYSTEM_TYPE_CATEGORY,
            LogicalSubsystemService.SYSTEM_OWNERSHIP_CATEGORY);
    private static final Set<String> PHYSICAL_PARAMETER_CATEGORIES = Set.of(
            PhysicalSubsystemService.RUNTIME_CATEGORY,
            PhysicalSubsystemService.SYSTEM_LEVEL_CATEGORY,
            PhysicalSubsystemService.DEVELOPMENT_FRAMEWORK_CATEGORY);

    private final OrganizationService organizationService;
    private final SystemReferenceQuery referenceQuery;
    private final ArchitectureSubsystemRepository repository;

    public ArchitectureOptionsService(OrganizationService organizationService,
                                      SystemReferenceQuery referenceQuery,
                                      ArchitectureSubsystemRepository repository) {
        this.organizationService = organizationService;
        this.referenceQuery = referenceQuery;
        this.repository = repository;
    }

    public PageResult<OrganizationOption> organizations(AuthUser actor, PageQuery page, String keyword) {
        requireActor(actor);
        PageQuery normalizedPage = page == null ? new PageQuery(1, 20) : page;
        String normalizedKeyword = normalizeOptional(keyword);
        List<OrganizationOption> active = new ArrayList<>();
        for (OrgTreeNode root : organizationService.tree(actor)) {
            collectOrganizations(root, null, normalizedKeyword, active);
        }
        long from = Math.min((normalizedPage.page() - 1) * normalizedPage.size(), active.size());
        long to = Math.min(from + normalizedPage.size(), active.size());
        return new PageResult<>(List.copyOf(active.subList((int) from, (int) to)), active.size(),
                normalizedPage.page(), normalizedPage.size());
    }

    public PageResult<UserOption> users(AuthUser actor, PageQuery page, String keyword) {
        requireActor(actor);
        PageResult<com.ccb.system.capability.SystemUserReference> result =
                referenceQuery.searchActiveUsers(actor, page, normalizeOptional(keyword));
        List<UserOption> records = result.records().stream()
                .map(item -> new UserOption(item.id(), item.displayName(), item.username(), item.phone()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    public List<ParameterOption> parameters(AuthUser actor, String resource, String categoryCode) {
        requireActor(actor);
        String normalizedCategory = normalizeOptional(categoryCode);
        if (normalizedCategory == null) {
            throw badRequest("参数分类不能为空");
        }
        normalizedCategory = normalizedCategory.toUpperCase(Locale.ROOT);
        Set<String> allowed = switch (resource) {
            case LOGICAL_RESOURCE -> LOGICAL_PARAMETER_CATEGORIES;
            case PHYSICAL_RESOURCE -> PHYSICAL_PARAMETER_CATEGORIES;
            default -> throw badRequest("选项资源上下文无效");
        };
        if (!allowed.contains(normalizedCategory)) {
            throw badRequest("参数分类不属于当前资源上下文");
        }
        return referenceQuery.activeParameters(actor, normalizedCategory).stream()
                .map(item -> new ParameterOption(item.code(), item.label()))
                .toList();
    }

    public PageResult<LogicalSubsystemOption> logicalSubsystems(AuthUser actor, PageQuery page,
                                                                 String code, String name) {
        requireActor(actor);
        PageResult<com.ccb.architecture.model.LogicalSubsystem> result = repository.pageLogical(
                actor.tenantId(), page, new LogicalSubsystemQuery(normalizeOptional(code), null,
                        normalizeOptional(name), null));
        List<LogicalSubsystemOption> records = result.records().stream()
                .map(item -> new LogicalSubsystemOption(item.id(), item.code(), item.name()))
                .toList();
        return new PageResult<>(records, result.total(), result.page(), result.size());
    }

    private void collectOrganizations(OrgTreeNode node, String parentPath, String keyword,
                                      List<OrganizationOption> target) {
        String path = parentPath == null ? node.orgName() : parentPath + " / " + node.orgName();
        if (node.status() == 1 && matchesOrganization(node, path, keyword)) {
            target.add(new OrganizationOption(node.id(), node.orgName(), node.parentId() == 0 ? null : node.parentId(), path));
        }
        if (node.children() != null) {
            for (OrgTreeNode child : node.children()) {
                collectOrganizations(child, path, keyword, target);
            }
        }
    }

    private boolean matchesOrganization(OrgTreeNode node, String path, String keyword) {
        if (keyword == null) {
            return true;
        }
        String expected = keyword.toLowerCase(Locale.ROOT);
        return node.orgName().toLowerCase(Locale.ROOT).contains(expected)
                || node.orgCode().toLowerCase(Locale.ROOT).contains(expected)
                || path.toLowerCase(Locale.ROOT).contains(expected);
    }

    private void requireActor(AuthUser actor) {
        if (actor == null || actor.id() <= 0 || actor.tenantId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要有效的认证用户和租户");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
