package com.ccb.boot.audit;

import com.ccb.common.audit.OperationAuditContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

@ControllerAdvice
public class OperationAuditRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof Map<?, ?> map) {
            OperationAuditContext.addChangedFields(map.keySet().stream().map(String::valueOf).toList());
        } else if (body != null && body.getClass().isRecord()) {
            OperationAuditContext.addChangedFields(Arrays.stream(body.getClass().getRecordComponents())
                    .map(RecordComponent::getName).toList());
        }
        return body;
    }
}
