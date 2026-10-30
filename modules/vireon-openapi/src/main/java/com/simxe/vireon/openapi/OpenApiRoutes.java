package com.simxe.vireon.openapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** 同一精确资源集合供服务认证链、授权和框架防线使用。 */
public final class OpenApiRoutes implements RequestMatcher {
    private final Set<String> paths;
    private final String documentPath;
    private final String schemaPath;
    public OpenApiRoutes(String documentPath, String schemaPath, boolean review) {
        validate(documentPath); validate(schemaPath);
        if (documentPath.equals(schemaPath) || schemaPath.startsWith(documentPath + "/")
                || documentPath.startsWith(schemaPath + "/")) {
            throw new IllegalArgumentException("Documentation routes overlap");
        }
        this.documentPath = documentPath; this.schemaPath = schemaPath;
        var result = new LinkedHashSet<String>();
        result.add(documentPath);
        for (String file : Set.of("scalar.js", "bootstrap.js", "styles.css")) result.add(documentPath + "/" + file);
        if (review) { result.add(documentPath + "/review-plugin.js"); result.add(documentPath + "/review.json"); }
        result.add(schemaPath); result.add(schemaPath + ".yaml");
        paths = Set.copyOf(result);
    }
    private static void validate(String path) {
        if (path == null || !path.matches("(?:/[A-Za-z0-9_-]+)+") || path.length() > 200
                || path.startsWith("/webjars") || path.startsWith("/actuator")) {
            throw new IllegalArgumentException("Documentation path must be a canonical absolute path");
        }
    }
    public Set<String> paths() { return paths; }
    public String documentPath() { return documentPath; }
    public String schemaPath() { return schemaPath; }
    public String applicationPath(HttpServletRequest request) {
        String uri = request.getDispatcherType() == jakarta.servlet.DispatcherType.INCLUDE
                ? (String) request.getAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI) : request.getRequestURI();
        if (uri == null) uri = request.getRequestURI();
        String context = request.getContextPath();
        return uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
    @Override public boolean matches(HttpServletRequest request) { return paths.contains(applicationPath(request)); }
    public boolean readMethod(HttpServletRequest request) { return Set.of("GET", "HEAD").contains(request.getMethod()); }
    boolean reserved(HttpServletRequest request) {
        String path = applicationPath(request);
        if (reservedPath(path)) return true;
        try {
            String normalized = org.springframework.web.util.UriUtils.decode(path, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
            return reservedPath(normalized);
        }
        catch (IllegalArgumentException failure) { return false; }
    }
    private boolean reservedPath(String path) {
        return paths.contains(path) || path.startsWith(documentPath + "/") || path.startsWith(schemaPath)
                || path.startsWith("/v3/api-docs") || path.equals("/scalar") || path.startsWith("/scalar/")
                || path.equals("/webjars/scalar") || path.startsWith("/webjars/scalar/") || path.startsWith("/swagger-ui");
    }
}
