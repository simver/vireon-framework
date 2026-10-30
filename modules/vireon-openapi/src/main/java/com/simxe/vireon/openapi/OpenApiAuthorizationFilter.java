package com.simxe.vireon.openapi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

/** 在既有认证过滤链内完成后的Servlet防线，不建立第二套身份。 */
final class OpenApiAuthorizationFilter extends GenericFilterBean {
    private final OpenApiRoutes routes;
    private final OpenApiSecurityAdapter adapter;
    private final boolean enabled;
    OpenApiAuthorizationFilter(OpenApiRoutes routes, OpenApiSecurityAdapter adapter) {
        this(routes, adapter, true);
    }
    OpenApiAuthorizationFilter(OpenApiRoutes routes, OpenApiSecurityAdapter adapter, boolean enabled) {
        this.routes = routes; this.adapter = adapter; this.enabled = enabled;
    }
    @Override public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse,
            FilterChain chain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (!routes.reserved(request)) { chain.doFilter(request, response); return; }
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.INCLUDE) throw new ServletException("Documentation includes are prohibited");
        if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) { response.setStatus(404); return; }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!enabled) { response.setStatus(404); return; }
        if (!routes.matches(request)) { response.setStatus(404); return; }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            adapter.authenticationEntryPoint().commence(request, response, new InsufficientAuthenticationException("Documentation authentication required"));
            return;
        }
        var decision = java.util.Objects.requireNonNull(
                adapter.permissions().evaluate(new com.simxe.vireon.security.permission.PermissionRequest(
                        java.util.Set.of(adapter.requiredAuthority()), com.simxe.vireon.security.permission.PermissionRequest.Match.ALL)),
                "Permission evaluator returned null");
        if (decision == com.simxe.vireon.security.permission.AccessDecision.DENY) {
            adapter.accessDeniedHandler().handle(request, response, new AccessDeniedException("Documentation authority required"));
            return;
        }
        if (!routes.readMethod(request)) { response.setStatus(405); response.setHeader("Allow", "GET, HEAD"); return; }
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; font-src 'self'; base-uri 'none'; frame-ancestors 'none'; object-src 'none'; form-action 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        chain.doFilter(request, response);
    }
}
