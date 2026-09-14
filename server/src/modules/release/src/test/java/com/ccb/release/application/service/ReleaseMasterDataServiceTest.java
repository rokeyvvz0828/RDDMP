package com.ccb.release.application.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.release.application.model.ReleaseApplicationModels.ArtifactType;
import com.ccb.release.application.model.ReleaseApplicationModels.DeliveryInput;
import com.ccb.release.integration.ReleaseArchitectureDirectory;
import com.ccb.release.integration.ReleaseRequirementDirectory;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseMasterDataServiceTest {
    private static final AuthUser ACTOR = new AuthUser(7, 1, "developer", "", "研发人员", 11, true);
    private static final ProjectAccess PROJECT = new ProjectAccess(31, "P-001", "项目");
    private ProjectAccessService projects;
    private ReleaseArchitectureDirectory directory;
    private ReleaseRequirementDirectory requirementDirectory;
    private ReleaseMasterDataService service;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectAccessService.class);
        directory = mock(ReleaseArchitectureDirectory.class);
        requirementDirectory = mock(ReleaseRequirementDirectory.class);
        service = new ReleaseMasterDataService(projects, directory, requirementDirectory);
        when(projects.requireAccessible("P-001", ACTOR)).thenReturn(PROJECT);
    }

    @Test
    void listsOptionsOnlyAfterResolvingAccessibleProject() {
        when(directory.searchPhysicalSubsystems(ACTOR, 31, new com.ccb.common.api.PageQuery(1, 100), null))
                .thenReturn(new PageResult<>(List.of(
                        new ReleaseArchitectureDirectory.PhysicalSubsystem(42, "AUTH", "统一认证子系统")),
                        1, 1, 100));
        when(directory.searchDeliveryUnits(ACTOR, 31, 42, new com.ccb.common.api.PageQuery(1, 100), null))
                .thenReturn(new PageResult<>(List.of(
                        new ReleaseArchitectureDirectory.DeliveryUnit(101, 42, "AUTH-SVC", "认证服务", "IMAGE"),
                        new ReleaseArchitectureDirectory.DeliveryUnit(102, 42, "AUTH-SDK", "认证组件", null)),
                        2, 1, 100));

        var physical = service.physicalSubsystems("P-001", 1, 100, null, ACTOR);
        var units = service.deliveryUnits("P-001", "42", 1, 100, null, ACTOR);

        assertThat(physical.records().get(0).id()).isEqualTo("42");
        assertThat(units.records().get(0).selectable()).isTrue();
        assertThat(units.records().get(1).selectable()).isFalse();
        assertThat(units.records().get(1).unavailableReason()).contains("配置制品类型");
    }

    @Test
    void returnsTrustedSelectionInClientOrder() {
        var physical = new ReleaseArchitectureDirectory.PhysicalSubsystem(42, "AUTH", "统一认证子系统");
        var first = new ReleaseArchitectureDirectory.DeliveryUnit(101, 42, "AUTH-SVC", "认证服务", "IMAGE");
        var second = new ReleaseArchitectureDirectory.DeliveryUnit(102, 42, "AUTH-SDK", "认证组件", "BINARY");
        when(directory.resolveActiveSelection(ACTOR, 31, 42, new LinkedHashSet<>(List.of(102L, 101L))))
                .thenReturn(Optional.of(new ReleaseArchitectureDirectory.Selection(physical, List.of(first, second))));

        var selection = service.requireActiveSelection(PROJECT, "42", List.of(
                new DeliveryInput("102", "伪造", "伪造", "IMAGE", "v2"),
                new DeliveryInput("101", "伪造", "伪造", "BINARY", "v1")), ACTOR);

        assertThat(selection.deliveryUnits()).extracting("id").containsExactly("102", "101");
        assertThat(selection.deliveryUnits()).extracting("artifactType")
                .containsExactly(ArtifactType.BINARY, ArtifactType.IMAGE);
    }

    @Test
    void listsAndResolvesOnlyProjectRequirementsInClientOrder() {
        var first = new ReleaseRequirementDirectory.Requirement(201, "REQ-001", "统一登录改造", "软需编制");
        var second = new ReleaseRequirementDirectory.Requirement(202, "REQ-002", "权限中心改造", "需求分析");
        when(requirementDirectory.searchActive(ACTOR, "P-001", new com.ccb.common.api.PageQuery(1, 100), null))
                .thenReturn(new PageResult<>(List.of(first, second), 2, 1, 100));
        when(requirementDirectory.resolveActive(ACTOR, "P-001",
                new LinkedHashSet<>(List.of("REQ-002", "REQ-001"))))
                .thenReturn(Optional.of(List.of(first, second)));

        assertThat(service.requirements("P-001", 1, 100, null, ACTOR).records())
                .extracting("number").containsExactly("REQ-001", "REQ-002");
        assertThat(service.requireActiveRequirements(PROJECT, List.of(" REQ-002 ", "REQ-001"), ACTOR))
                .containsExactly("REQ-002", "REQ-001");
    }

    @Test
    void rejectsRequirementsThatCannotBeResolvedInCurrentProject() {
        when(requirementDirectory.resolveActive(ACTOR, "P-001", new LinkedHashSet<>(List.of("REQ-OTHER"))))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveRequirements(PROJECT, List.of("REQ-OTHER"), ACTOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前项目");
    }

    @Test
    void rejectsDuplicateOrLegacyIdentifiersBeforeDirectoryLookup() {
        assertThatThrownBy(() -> service.requireActiveSelection(PROJECT, "legacy-subsystem", List.of(), ACTOR))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireActiveSelection(PROJECT, "42", List.of(
                new DeliveryInput("101", "A", "A", "IMAGE", "v1"),
                new DeliveryInput("101", "B", "B", "IMAGE", "v2")), ACTOR))
                .isInstanceOf(BusinessException.class);
        verify(directory, never()).resolveActiveSelection(any(), anyLong(), anyLong(), any());
    }
}
