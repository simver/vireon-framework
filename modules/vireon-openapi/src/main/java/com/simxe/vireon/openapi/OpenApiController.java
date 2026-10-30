package com.simxe.vireon.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.HtmlUtils;

/** 官方standalone资源加外置初始化脚本，避免内联脚本及转发头推导。 */
@Hidden
@RestController
public final class OpenApiController {
    private final OpenApiProperties properties;
    private final OpenApiRoutes routes;
    private final OpenApiMetadata metadata;
    OpenApiController(OpenApiProperties properties, OpenApiRoutes routes, OpenApiMetadata metadata) {
        this.properties = properties; this.routes = routes; this.metadata = metadata;
    }
    @GetMapping(value = "${vireon.openapi.path:/docs}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> html(HttpServletRequest request) {
        String base = HtmlUtils.htmlEscape(request.getContextPath() + routes.documentPath());
        String review = properties.getReview().isEnabled() ? base + "/review.json" : "";
        return ResponseEntity.ok().header("Cache-Control", "no-store").body("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
                + HtmlUtils.htmlEscape(properties.getTitle()) + "</title><link rel=\"icon\" href=\"data:,\"><link rel=\"stylesheet\" href=\"" + base + "/styles.css\"></head><body data-layout=\""
                + properties.getLayout() + "\"><div id=\"app\"></div><script src=\"" + base + "/scalar.js\"></script><script src=\""
                + base + "/bootstrap.js\" data-review-url=\"" + review + "\"></script></body></html>");
    }
    @GetMapping(value = "${vireon.openapi.path:/docs}/scalar.js", produces = "application/javascript")
    public byte[] scalar() throws IOException { return resource("scalar.js"); }
    @GetMapping(value = "${vireon.openapi.path:/docs}/bootstrap.js", produces = "application/javascript")
    public String bootstrap(HttpServletRequest request) throws IOException {
        String context = request.getContextPath();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", context + routes.schemaPath());
        config.put("telemetry", false); config.put("withDefaultFonts", false); config.put("persistAuth", false);
        config.put("hideTestRequestButton", !properties.getTryIt().isEnabled()); config.put("hideClientButton", true); config.put("showDeveloperTools", "never");
        config.put("agent", Map.of("disabled", true)); config.put("mcp", Map.of("disabled", true));
        config.put("servers", java.util.List.of(Map.of("url", context.isEmpty() ? "/" : context)));
        if (properties.getReview().isEnabled()) config.put("pluginUrls", java.util.List.of(context + routes.documentPath() + "/review-plugin.js"));
        return "'use strict';window.Scalar.createApiReference('#app'," + new ObjectMapper().writeValueAsString(config) + ");";
    }
    @GetMapping(value = "${vireon.openapi.path:/docs}/styles.css", produces = "text/css")
    public byte[] styles() throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        output.write(resource("scalar.css")); output.write('\n'); output.write(resource("styles.css"));
        return output.toByteArray();
    }
    static byte[] resource(String file) throws IOException {
        try (var input = new ClassPathResource("META-INF/vireon/openapi/" + file).getInputStream()) { return input.readAllBytes(); }
    }
}
