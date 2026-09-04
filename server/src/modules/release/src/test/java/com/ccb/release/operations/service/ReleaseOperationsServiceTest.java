package com.ccb.release.operations.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlanRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleaseDrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleaseDrillRoundRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssuePriority;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItemRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineType;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleasePlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleasePlanRequest;
import com.ccb.release.operations.persistence.ReleaseOperationsStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseOperationsServiceTest {
    private final AuthUser actor = new AuthUser(7L, 1L, "operator", "", "操作员", 1L, true);
    private ReleaseOperationsStore store;
    private ProjectMemberReferenceQuery members;
    private ReleaseOperationsService service;

    @BeforeEach
    void setUp() {
        store = mock(ReleaseOperationsStore.class);
        members = mock(ProjectMemberReferenceQuery.class);
        service = new ReleaseOperationsService(store, members);
    }

    @Test
    void rejectsEveryOperationForUserOutsideProject() {
        when(members.findActiveMembers(actor, 9001L)).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveDrillPlan(9001L, new DrillPlanRequest("方案", "环境", 0), actor));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        verify(store, never()).insertDrillPlan(any(), anyLong());
    }

    @Test
    void rejectsTimelineItemWithInvertedDatesBeforeWriting() {
        allowActor();
        when(store.findTimeline(1L, 9001L, TimelineType.NORMAL))
                .thenReturn(Optional.of(new Timeline(100L, 9001L, TimelineType.NORMAL, "上线时序", null, 0, null, List.of())));

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveTimelineItem(9001L, TimelineType.NORMAL, null,
                new TimelineItemRequest(null, "切换流量", LocalDateTime.of(2026, 9, 2, 12, 0), LocalDateTime.of(2026, 9, 2, 11, 0), null, "PENDING", null, 0), actor));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        verify(store, never()).insertTimelineItem(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsGroupMemberThatIsNotAnActiveProjectMember() {
        allowActor();
        when(store.findGroup(8001L, 1L, 9001L)).thenReturn(Optional.of(new Group(8001L, 9001L, "投产组", null, 0, null, List.of())));
        when(members.findActiveMember(actor, 9001L, 7002L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.addGroupMember(9001L, 8001L, 7002L, actor));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        verify(store, never()).insertGroupMember(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void rejectsStaleGroupUpdate() {
        allowActor();
        Group current = new Group(8001L, 9001L, "投产组", null, 3L, null, List.of());
        when(store.findGroup(8001L, 1L, 9001L)).thenReturn(Optional.of(current));
        when(store.updateGroup(any(), eq(1L), eq(2L), eq(7L))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveGroup(9001L, 8001L, new GroupRequest("新名称", null, 2L), actor));

        assertEquals(ErrorCode.CONFLICT, error.code());
    }

    @Test
    void rejectsDrillRoundWithoutCurrentProjectPlanOrEnvironment() {
        allowActor();
        when(store.findReleasePlan(1001L, 1L, 9001L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveReleaseDrill(9001L, null,
                new ReleaseDrillRoundRequest(1001L, 2001L, "第一轮", null, DrillStatus.PLANNED.name(), null, 0), actor));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        verify(store, never()).insertReleaseDrillRound(any(), anyLong(), anyLong());
    }

    @Test
    void rejectsIssueRoundFromAnotherProject() {
        allowActor();
        when(store.findReleaseDrillRound(3001L, 1L, 9001L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.saveIssue(9001L, null,
                new IssueRequest("REL-001", "问题", IssuePriority.MEDIUM.name(), IssueStatus.OPEN.name(), null,
                        null, null, null, null, null, null, 3001L, 0), actor));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        verify(store, never()).insertIssue(any(), anyLong(), anyLong());
    }

    @Test
    void rejectsDeletingPlanReferencedByDrillRound() {
        allowActor();
        when(store.findReleaseDrillRounds(1L, 9001L)).thenReturn(List.of(new ReleaseDrillRound(3001L, 9001L, 1,
                "第一轮", null, DrillStatus.PLANNED, null, 1001L, "方案", 2001L, "环境", 0, null, List.of())));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteReleasePlan(9001L, 1001L, 0, actor));

        assertEquals(ErrorCode.CONFLICT, error.code());
        verify(store, never()).deleteReleasePlan(anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void createsPlanWithDefaultTimelineNames() {
        allowActor();
        when(store.findReleasePlan(anyLong(), eq(1L), eq(9001L)))
                .thenReturn(Optional.of(new ReleasePlan(1L, 1L, 9001L, "方案", "CODE", null, null,
                        "DRAFT", "正向投产时序", "回退时序", 0, null, List.of())));

        service.saveReleasePlan(9001L, null,
                new ReleasePlanRequest("方案", "CODE", null, null, null, null, null, 0), actor);

        org.mockito.ArgumentCaptor<ReleasePlan> captured = org.mockito.ArgumentCaptor.forClass(ReleasePlan.class);
        verify(store).insertReleasePlan(captured.capture(), eq(7L));
        assertEquals("正向投产时序", captured.getValue().normalTimelineName());
        assertEquals("回退时序", captured.getValue().rollbackTimelineName());
    }

    private void allowActor() {
        when(members.findActiveMembers(actor, 9001L)).thenReturn(List.of(new ProjectMemberReference(7001L, 7L, "操作员", "operator")));
    }
}
