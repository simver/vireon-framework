package com.simxe.vireon.openapi;

import java.util.Objects;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 消费方声明文档权限及既有错误输出；身份仍由服务安全链解析。 */
public record OpenApiSecurityAdapter(String requiredAuthority, com.simxe.vireon.security.permission.PermissionEvaluator permissions,
        AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler) {
    public OpenApiSecurityAdapter {
        if (requiredAuthority == null || !requiredAuthority.matches("[A-Za-z0-9:._-]{1,128}")) {
            throw new IllegalArgumentException("A concrete documentation authority is required");
        }
        Objects.requireNonNull(authenticationEntryPoint);
        Objects.requireNonNull(permissions);
        Objects.requireNonNull(accessDeniedHandler);
    }
}
