package com.simxe.vireon.web.error;

import com.simxe.vireon.core.error.VireonErrorCode;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.context.VireonRequestTraceId;
import com.simxe.vireon.web.internal.VireonResponsePolicy;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** 所有框架错误信封的内部出口。 */
final class VireonErrorResponses {

    private static final List<String> REPRESENTATION_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_ENCODING, HttpHeaders.TRANSFER_ENCODING);

    private VireonErrorResponses() {}

    static <T> ResponseEntity<VireonApiResponse<T>> failure(
            VireonErrorCode code, String message, T data, HttpHeaders sourceHeaders) {
        ServletRequestAttributes attributes = currentRequest();
        if (committed(attributes)) {
            return null;
        }
        if (code.httpStatus() == 406) {
            return notAcceptable();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(sourceHeaders);
        REPRESENTATION_HEADERS.forEach(headers::remove);
        if (VireonResponsePolicy.bodyForbidden(
                attributes == null ? "GET" : attributes.getRequest().getMethod(), code.httpStatus())) {
            clearPreviousBody(attributes);
            return ResponseEntity.status(code.httpStatus()).headers(headers).build();
        }
        List<String> accept = attributes == null ? List.of()
                : Collections.list(attributes.getRequest().getHeaders(HttpHeaders.ACCEPT));
        if (!VireonResponsePolicy.accepts(accept, MediaType.APPLICATION_JSON)) {
            return notAcceptable();
        }
        clearPreviousBody(attributes);
        return ResponseEntity.status(code.httpStatus()).headers(headers).contentType(MediaType.APPLICATION_JSON)
                .body(VireonApiResponse.failure(code, message, data, VireonRequestTraceId.currentTraceId()));
    }

    static <T> ResponseEntity<T> notAcceptable() {
        ServletRequestAttributes attributes = currentRequest();
        if (committed(attributes)) {
            return null;
        }
        clearPreviousBody(attributes);
        return ResponseEntity.status(406).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    static boolean cannotWriteFailure(Exception exception) {
        ServletRequestAttributes attributes = currentRequest();
        if (committed(attributes)) {
            return true;
        }
        // 普通应用 I/O 失败不代表响应不可用；不再从控制器返回类型推断写出阶段。
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
        }
        return false;
    }

    private static ServletRequestAttributes currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes ? attributes : null;
    }

    private static boolean committed(ServletRequestAttributes attributes) {
        return attributes != null && attributes.getResponse() != null && attributes.getResponse().isCommitted();
    }

    private static void clearPreviousBody(ServletRequestAttributes attributes) {
        HttpServletResponse response = attributes == null ? null : attributes.getResponse();
        if (response != null) {
            response.resetBuffer();
            // 容器可能单独保存类型/长度；setHeader(name, null) 不保证清掉这些 Servlet 状态。
            response.setContentType(null);
            response.setContentLengthLong(-1);
            REPRESENTATION_HEADERS.forEach(name -> response.setHeader(name, null));
        }
    }
}
