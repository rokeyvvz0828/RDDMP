package com.ccb.security.web;

import com.ccb.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtAuthenticationFilterProjectPathTest {
    @Test
    void rejectsMissingOrDifferentProjectContextForProjectEntityPath() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(null, null, new ObjectMapper());
        Method validator = JwtAuthenticationFilter.class
                .getDeclaredMethod("validateProjectPath", HttpServletRequest.class, Long.class);
        validator.setAccessible(true);
        HttpServletRequest request = request("/api/project/9001/roles");

        BusinessException missing = invokeFailure(validator, filter, request, null);
        BusinessException mismatch = invokeFailure(validator, filter, request, 9002L);

        assertEquals(40000, missing.code());
        assertEquals(40300, mismatch.code());
        validator.invoke(filter, request, 9001L);
    }

    private BusinessException invokeFailure(Method validator, JwtAuthenticationFilter filter,
                                            HttpServletRequest request, Long projectId) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> validator.invoke(filter, request, projectId));
        return (BusinessException) exception.getCause();
    }

    private HttpServletRequest request(String servletPath) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getServletPath".equals(method.getName()) ? servletPath : null);
    }
}
