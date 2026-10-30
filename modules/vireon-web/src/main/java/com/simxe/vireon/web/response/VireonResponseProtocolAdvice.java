package com.simxe.vireon.web.response;

import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.internal.VireonResponsePolicy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 只维护协议边界，不包装、序列化或替换业务响应。 */
@ControllerAdvice
public class VireonResponseProtocolAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        int status = response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus() : 200;
        if (VireonResponsePolicy.bodyForbidden(request.getMethod().name(), status)) {
            return null;
        }
        if (body instanceof VireonApiResponse<?> && VireonResponsePolicy.isJson(selectedContentType)
                && !VireonResponsePolicy.accepts(request.getHeaders().get(HttpHeaders.ACCEPT), selectedContentType)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
        }
        return body;
    }
}
