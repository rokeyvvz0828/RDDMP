package com.ccb.architecture.web;

import com.ccb.architecture.service.SubsystemParticipationService;
import com.ccb.architecture.service.SubsystemParticipationService.ParticipationView;
import com.ccb.architecture.service.SubsystemParticipationService.ReplaceCommand;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubsystemParticipationControllerTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean SubsystemParticipationService service() { return mock(SubsystemParticipationService.class); }
        @Bean ProjectAccessService projects() { return mock(ProjectAccessService.class); }
        @Bean SubsystemParticipationController controller(SubsystemParticipationService s, ProjectAccessService p) {
            return new SubsystemParticipationController(s, p);
        }
    }
    private AnnotationConfigApplicationContext context;
    private SubsystemParticipationController controller;
    private SubsystemParticipationService service;
    private ProjectAccessService projects;
    private final AuthUser actor = new AuthUser(9, 7, "tester", "", "测试用户", 1, true);
    private final ProjectAccess project = new ProjectAccess(70, "P70", "项目");
    private final ReplaceCommand command = new ReplaceCommand(List.of(20L), 0L, "分工调整");

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(SubsystemParticipationController.class);
        service = context.getBean(SubsystemParticipationService.class);
        projects = context.getBean(ProjectAccessService.class);
        when(projects.requireAccessible("P70", actor)).thenReturn(project);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); context.close(); com.ccb.common.trace.TraceId.clear(); }
    private UsernamePasswordAuthenticationToken login(String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(actor, "", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test void 查看权限不能调用写入口() {
        var auth = login("architecture:view");
        assertThatThrownBy(() -> controller.replace(10, "P70", command, actor, auth)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service, projects);
    }
    @Test void 不可访问项目在业务调用前拒绝() {
        var auth = login("architecture:manage");
        when(projects.requireAccessible("P71", actor)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问"));
        assertThatThrownBy(() -> controller.replace(10, "P71", command, actor, auth)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(service);
    }
    @Test void 管理能力仅从认证权限解析() {
        var auth = login("architecture:physical:update");
        controller.replace(10, "P70", command, actor, auth);
        verify(service).replace(eq(actor), eq(project), eq(10L), eq(command), eq(false), anyString());
    }
    @Test void 只读负责人响应不得宣称可维护() {
        var auth = login("architecture:view");
        when(service.detail(actor, project, 10, false)).thenReturn(new ParticipationView(9L,List.of(),List.of(9L),0,true,List.of()));
        var response = controller.detail(10, "P70", actor, auth);
        assertThat(response.data().canManage()).isFalse();
    }
}
