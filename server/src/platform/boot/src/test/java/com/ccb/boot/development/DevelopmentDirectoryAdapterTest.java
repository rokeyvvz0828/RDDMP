package com.ccb.boot.development;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.integration.DevelopmentSourceDirectory;
import com.ccb.requirement.integration.RequirementDevelopmentQuery;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentDirectoryAdapterTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "虚构负责人", 1, true);

    @Test
    void searchUsesProjectCodeAcrossDistinctIdDomainsAndPreservesPagingAndRoles() {
        Source producer = new Source();
        ProjectAccessService projects = (ref, actor) -> {
            assertEquals("PROJECT-A", ref);
            assertEquals(ACTOR, actor);
            return new ProjectAccess(31, ref, "虚构项目");
        };
        var result = new DevelopmentSourceDirectoryAdapter(producer, projects).search(ACTOR,
                new DevelopmentSourceDirectory.SourceQuery("PROJECT-A", Set.of("SYS-A"), "需求", new PageQuery(2, 5)));
        assertEquals("PROJECT-A", producer.query.projectRef());
        assertEquals(Set.of("SYS-A"), producer.query.systemCodes());
        assertEquals(2, result.page());
        assertEquals(5, result.size());
        assertEquals("digest", result.records().get(0).revision());
        assertEquals(31, result.records().get(0).projectId());
        assertEquals(Set.of(DevelopmentSourceDirectory.Role.LEAD, DevelopmentSourceDirectory.Role.TEST),
                result.records().get(0).systems().get(0).roles());
    }

    @Test
    void deniedProjectDoesNotReadSourceOrExposeItsDetails() {
        Source producer = new Source();
        ProjectAccessService projects = (ref, actor) -> { throw new BusinessException(ErrorCode.FORBIDDEN, "项目不可访问"); };
        var adapter = new DevelopmentSourceDirectoryAdapter(producer, projects);
        assertThrows(BusinessException.class, () -> adapter.search(ACTOR,
                new DevelopmentSourceDirectory.SourceQuery("PROJECT-B", Set.of("SYS-A"), null, new PageQuery(1, 20))));
        assertThrows(BusinessException.class, () -> adapter.requireCurrent(ACTOR, "PROJECT-B", 42));
        assertEquals(0, producer.calls);
    }

    @Test
    void currentSourceIsScopedBeforeLookupAndUnavailableIsAConflict() {
        Source producer = new Source();
        var adapter = new DevelopmentSourceDirectoryAdapter(producer,
                (ref, actor) -> new ProjectAccess(31, ref, "虚构项目"));
        assertEquals(42, adapter.requireCurrent(ACTOR, "PROJECT-A", 42).id());
        producer.absent = true;
        BusinessException error = assertThrows(BusinessException.class,
                () -> adapter.requireCurrent(ACTOR, "PROJECT-A", 42));
        assertEquals(ErrorCode.CONFLICT, error.code());
    }

    private static class Source implements RequirementDevelopmentQuery {
        Query query;
        int calls;
        boolean absent;
        private SourceRequirement source() {
            return new SourceRequirement(42, "REQ-FIXTURE", "虚构需求", "内容", 3100, "digest", true,
                    List.of(new SourceSystem("SYS-A", Set.of(Role.LEAD, Role.TEST), 9L)));
        }
        @Override
        public PageResult<SourceRequirement> search(AuthUser actor, Query query) {
            this.query = query;
            calls++;
            return new PageResult<>(List.of(source()), 1, query.page().page(), query.page().size());
        }
        @Override
        public Optional<SourceRequirement> find(AuthUser actor, String projectRef, long requirementId) {
            assertEquals("PROJECT-A", projectRef);
            assertEquals(42, requirementId);
            calls++;
            return absent ? Optional.empty() : Optional.of(source());
        }
    }
}
