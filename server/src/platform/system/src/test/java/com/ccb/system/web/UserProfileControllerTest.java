/*
 * 文件：server/src/platform/system/src/test/java/com/ccb/system/web/UserProfileControllerTest.java
 * 说明：人员档案批量查询接口的权限声明、上限、空请求与响应白名单测试。
 * 用途：确保接口只需登录即可访问、租户标识缺失进入 missingIds、响应不含敏感字段。
 * 作者：Codex
 */
package com.ccb.system.web;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.common.trace.TraceId;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {
    private static final String BASE = "/api/platform/user-profiles";
    private static final AuthUser ACTOR = new AuthUser(9L, 7L, "tester", "hash", "测试员", 11L, true);
    private static final List<String> ALLOWED_PROFILE_FIELDS =
            List.of("id", "username", "displayName", "mobilePhone", "orgName", "avatarUrl", "roles", "status");
    private static final List<String> FORBIDDEN_PROFILE_FIELDS =
            List.of("passwordHash", "tenantId", "deleted", "lastLoginAt", "avatarObjectKey");

    private UserProfileService service;
    private MinioStorageService storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UserProfileService.class);
        storage = mock(MinioStorageService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserProfileController(service, storage))
                .setCustomArgumentResolvers(new AuthenticationPrincipalResolver(ACTOR))
                .build();
    }

    @AfterEach
    void clearTrace() {
        TraceId.clear();
    }

    @Test
    void 路径映射与登录即可访问的权限声明保持明确() {
        RequestMapping root = UserProfileController.class.getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly(BASE);

        PreAuthorize permission = UserProfileController.class.getAnnotation(PreAuthorize.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo("isAuthenticated()");
        assertThat(permission.value()).doesNotContain("system:access");

        PostMapping mapping = null;
        for (var method : UserProfileController.class.getDeclaredMethods()) {
            if ("query".equals(method.getName())) {
                mapping = method.getAnnotation(PostMapping.class);
            }
        }
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/query");
    }

    @Test
    void 超过五十个标识时拒绝且不查询数据库() {
        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            tooMany.add(id);
        }

        assertThatThrownBy(() -> new UserProfileController(service, storage)
                .query(ACTOR, new UserProfileController.QueryRequest(tooMany)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void 空标识返回成功且两个列表都为空() throws Exception {
        mockMvc.perform(post(BASE + "/query").contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.profiles").isEmpty())
                .andExpect(jsonPath("$.data.missingIds").isEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void 未命中标识进入missingIds而响应只含白名单字段() throws Exception {
        when(service.query(any(AuthUser.class), any())).thenReturn(List.of(
                new UserProfileService.Row(1L, "zhangwei", "张伟", "13800138000", "平台研发一组",
                        "avatar/1.png", 1, List.of(new UserProfileService.RoleItem(1L, "ARCH_ADMIN", "架构管理员")))));
        when(storage.presignedUrl("avatar/1.png")).thenReturn("http://minio.local/avatar/1.png");

        String body = mockMvc.perform(post(BASE + "/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1,7]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingIds[0]").value(7))
                .andExpect(jsonPath("$.data.profiles[0].id").value(1))
                .andExpect(jsonPath("$.data.profiles[0].displayName").value("张伟"))
                .andExpect(jsonPath("$.data.profiles[0].roles[0].name").value("架构管理员"))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> profile = firstProfile(body);
        assertThat(profile.keySet()).containsExactlyInAnyOrderElementsOf(ALLOWED_PROFILE_FIELDS);
        assertThat(profile.keySet()).doesNotContainAnyElementsOf(FORBIDDEN_PROFILE_FIELDS);
    }

    @Test
    void 停用用户仍返回并带零状态() throws Exception {
        when(service.query(any(AuthUser.class), any())).thenReturn(List.of(
                new UserProfileService.Row(2L, "disabled", "停用人员", "", "", null, 0, List.of())));

        mockMvc.perform(post(BASE + "/query").contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profiles[0].status").value(0))
                .andExpect(jsonPath("$.data.profiles[0].avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.missingIds").isEmpty());

        verify(storage, never()).presignedUrl(anyString());
    }

    @Test
    void 重复标识在进入服务前已去重() throws Exception {
        when(service.query(any(AuthUser.class), any())).thenReturn(List.of());

        mockMvc.perform(post(BASE + "/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[3,3,3,null]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingIds.length()").value(1));

        verify(service).query(any(AuthUser.class), org.mockito.ArgumentMatchers.eq(List.of(3L)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstProfile(String body) throws Exception {
        Map<String, Object> root = new ObjectMapper().readValue(body, Map.class);
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        List<Map<String, Object>> profiles = (List<Map<String, Object>>) data.get("profiles");
        return profiles.get(0);
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
