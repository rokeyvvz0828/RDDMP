package com.ccb.architecture.web;

import com.ccb.architecture.model.LogicalSubsystem;
import com.ccb.architecture.model.LogicalSubsystemQuery;
import com.ccb.architecture.model.OrganizationOption;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.architecture.service.ArchitectureOptionsService;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArchitectureOptionsControllerTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);

    private OrganizationService organizationService;
    private SystemReferenceQuery referenceQuery;
    private ArchitectureSubsystemRepository repository;
    private ArchitectureOptionsService service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        organizationService = mock(OrganizationService.class);
        referenceQuery = mock(SystemReferenceQuery.class);
        repository = mock(ArchitectureSubsystemRepository.class);
        service = new ArchitectureOptionsService(organizationService, referenceQuery, repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new ArchitectureOptionsController(service))
                .setControllerAdvice(new ArchitectureExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void clearTrace() {
        com.ccb.common.trace.TraceId.clear();
    }

    @Test
    void organizationsAreActiveTenantTreeOptionsWithExactKeysAndPath() throws Exception {
        when(organizationService.tree(ACTOR)).thenReturn(organizationTree());

        PageResult<OrganizationOption> filtered = service.organizations(ACTOR, new PageQuery(1, 1), " 研发 ");
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.records()).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(12);
            assertThat(option.parentId()).isEqualTo(11);
            assertThat(option.pathLabel()).isEqualTo("数字事业群 / 平台研发团队");
        });

        MvcResult result = mockMvc.perform(get("/api/architecture/options/logical-subsystem/organizations")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andReturn();
        assertExactKeys(result, "/data/records/0", "id", "name", "parentId", "pathLabel");
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/records/0/parentId").isNull())
                .isTrue();
    }

    @Test
    void usersReturnOnlySafeExactKeysAndExplicitNullPhone() throws Exception {
        when(referenceQuery.searchActiveUsers(eq(ACTOR), any(PageQuery.class), eq("张")))
                .thenReturn(new PageResult<>(List.of(new SystemUserReference(21, "张三", "zhangsan", null, true)),
                        1, 1, 20));

        MvcResult result = mockMvc.perform(get("/api/architecture/options/physical-subsystem/users")
                        .param("keyword", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].displayName").value("张三"))
                .andReturn();

        assertExactKeys(result, "/data/records/0", "id", "displayName", "username", "phone");
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/records/0").get("phone").isNull())
                .isTrue();
    }

    @Test
    void parametersAreResourceWhitelistedAndReturnExactKeys() throws Exception {
        when(referenceQuery.activeParameters(ACTOR, "ARCH_SYSTEM_TYPE"))
                .thenReturn(List.of(new SystemParameterReference("APPLICATION", "应用平台类")));

        MvcResult result = mockMvc.perform(get(
                        "/api/architecture/options/logical-subsystem/parameters/ARCH_SYSTEM_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("应用平台类"))
                .andReturn();
        assertExactKeys(result, "/data/0", "code", "label");

        assertThatThrownBy(() -> service.parameters(ACTOR, ArchitectureOptionsService.LOGICAL_RESOURCE, "ARCH_RUNTIME"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void physicalContextReturnsLogicalSubsystemExactOptions() throws Exception {
        when(repository.pageLogical(eq(7L), any(PageQuery.class), any(LogicalSubsystemQuery.class)))
                .thenReturn(new PageResult<>(List.of(logical()), 1, 1, 20));

        MvcResult result = mockMvc.perform(get(
                        "/api/architecture/options/physical-subsystem/logical-subsystems")
                        .param("code", "AP").param("name", "员工"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("AP_201"))
                .andReturn();

        assertExactKeys(result, "/data/records/0", "id", "code", "name");
        ArgumentCaptor<LogicalSubsystemQuery> query = ArgumentCaptor.forClass(LogicalSubsystemQuery.class);
        verify(repository).pageLogical(eq(7L), any(PageQuery.class), query.capture());
        assertThat(query.getValue().code()).isEqualTo("AP");
        assertThat(query.getValue().name()).isEqualTo("员工");
    }

    @Test
    void unknownOrUnsupportedResourceContextReturns40400() throws Exception {
        mockMvc.perform(get("/api/architecture/options/unknown/users"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE));

        mockMvc.perform(get("/api/architecture/options/logical-subsystem/logical-subsystems"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ArchitectureExceptionAdvice.NOT_FOUND_CODE));
    }

    @Test
    void eachKnownContextUsesOnlyItsOwnListPermission() throws Exception {
        assertPermission("logicalOrganizations", "architecture:logical:list",
                long.class, long.class, String.class, AuthUser.class);
        assertPermission("logicalUsers", "architecture:logical:list",
                long.class, long.class, String.class, AuthUser.class);
        assertPermission("logicalParameters", "architecture:logical:list", String.class, AuthUser.class);
        assertPermission("physicalOrganizations", "architecture:physical:list",
                long.class, long.class, String.class, AuthUser.class);
        assertPermission("physicalUsers", "architecture:physical:list",
                long.class, long.class, String.class, AuthUser.class);
        assertPermission("physicalParameters", "architecture:physical:list", String.class, AuthUser.class);
        assertPermission("physicalLogicalSubsystems", "architecture:physical:list",
                long.class, long.class, String.class, String.class, AuthUser.class);
    }

    private void assertPermission(String methodName, String authority, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = ArchitectureOptionsController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('" + authority + "')");
        assertThat(annotation.value()).doesNotContain("hasAnyAuthority").doesNotContain(" or ");
    }

    private void assertExactKeys(MvcResult result, String pointer, String... expected) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer);
        Set<String> actual = new LinkedHashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    private List<OrgTreeNode> organizationTree() {
        OrgTreeNode root = new OrgTreeNode(11, 0, "BU", "数字事业群", 1, 1,
                new ArrayList<>(), new ArrayList<>());
        root.children().add(new OrgTreeNode(12, 11, "TEAM", "平台研发团队", 1, 1,
                new ArrayList<>(), new ArrayList<>()));
        root.children().add(new OrgTreeNode(13, 11, "OLD", "停用团队", 2, 0,
                new ArrayList<>(), new ArrayList<>()));
        return List.of(root);
    }

    private LogicalSubsystem logical() {
        return new LogicalSubsystem(101, "AP_201", "员工渠道", "员工渠道整合平台", 11,
                null, null, null, 21, null, null, 9, 9,
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0));
    }

    private record AuthenticationPrincipalResolver(AuthUser actor) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return actor;
        }
    }
}
