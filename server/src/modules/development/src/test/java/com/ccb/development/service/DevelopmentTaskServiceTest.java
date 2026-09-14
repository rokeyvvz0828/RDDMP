package com.ccb.development.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.development.repository.DevelopmentChangeRepository;
import com.ccb.development.repository.DevelopmentTaskRepository;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static com.ccb.development.repository.DevelopmentTaskMySqlTest.standalone;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevelopmentTaskServiceTest {
    @Test
    void administratorVisibilityDoesNotReadUnusedOwnerDirectory(){
        AuthUser actor=new AuthUser(9,7,"fixture","","虚构管理员",1,true);
        var systems=mock(com.ccb.development.integration.DevelopmentSystemDirectory.class);
        var policy=new DevelopmentAccessPolicy(mock(DevelopmentTaskRepository.class),systems,
                mock(com.ccb.system.capability.SystemReferenceQuery.class),mock(com.ccb.system.capability.ProjectAccessService.class));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("development:admin"))));
        try{
            var visibility=policy.visibility(actor,31);
            assertTrue(visibility.admin());assertTrue(visibility.systemIds().isEmpty());
            verifyNoInteractions(systems);
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

    @Test
    void createDenialPrecedesSourceReadsNumberAllocationAndPersistence(){
        AuthUser actor=new AuthUser(10,7,"fixture","","虚构用户",1,true);
        var repository=mock(DevelopmentTaskRepository.class);var access=mock(DevelopmentAccessPolicy.class);
        var sources=mock(DevelopmentSourceResolver.class);var numbers=mock(DevelopmentNumberService.class);
        var changes=mock(DevelopmentChangeRepository.class);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无操作权限")).when(access).permission(actor,"development:task:create");
        var service=new DevelopmentTaskService(repository,access,sources,numbers,changes,new ObjectMapper());
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(BusinessException.class,()->service.create(actor,standalone(null))).code());
        verifyNoInteractions(repository,sources,numbers,changes);
    }
}
