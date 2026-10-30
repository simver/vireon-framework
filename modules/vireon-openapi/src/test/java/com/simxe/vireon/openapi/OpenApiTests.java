package com.simxe.vireon.openapi;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class OpenApiTests {
    @Test void reviewIndexPredicateIsBoundedAndReferencesKnownColumn() throws Exception {
        var json=new ObjectMapper();
        var index=json.createObjectNode().put("name","only_special").put("unique",true).put("predicate","name = 'Special'");
        index.putArray("columns").add("name");
        var root=json.valueToTree(Map.of("schemaVersion",1,"service","test","revision","unavailable","inputDigest","a".repeat(64),
                "internalEndpoints",List.of(),"relationships",List.of(),"permissions",List.of(),"runtimeNotes",List.of(),
                "tables",List.of(Map.of("name","app.fact","comment","测试表","columns",List.of(Map.of("name","name","type","text","nullable",false,"comment","名称")),"indexes",List.of(index)))));
        assertThat(OpenApiMetadata.parse(json.writeValueAsBytes(root),false)).isNotNull();
        var mutableIndex=(com.fasterxml.jackson.databind.node.ObjectNode)root.path("tables").get(0).path("indexes").get(0);
        mutableIndex.put("predicate","name = '"+"a''".repeat(5000)+"'");
        assertThat(OpenApiMetadata.parse(json.writeValueAsBytes(root),false)).isNotNull();
        mutableIndex.put("predicate","name = '"+"a''".repeat(5000));
        assertThatThrownBy(()->OpenApiMetadata.parse(json.writeValueAsBytes(root),false)).isInstanceOf(IllegalStateException.class);
        for(String predicate:List.of("missing = 'x'","name = lower('x')","name = 'x' OR name = 'y'","name = 'unterminated")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("tables").get(0).path("indexes").get(0)).put("predicate",predicate);
            assertThatThrownBy(()->OpenApiMetadata.parse(json.writeValueAsBytes(root),false)).isInstanceOf(IllegalStateException.class);
        }
    }
    private final OpenApiRoutes routes = new OpenApiRoutes("/api/test/docs", "/api/test/v3/api-docs", true);
    private final java.util.concurrent.atomic.AtomicReference<com.simxe.vireon.security.permission.AccessDecision> decision = new java.util.concurrent.atomic.AtomicReference<>(com.simxe.vireon.security.permission.AccessDecision.DENY);
    private final OpenApiSecurityAdapter adapter = new OpenApiSecurityAdapter("test:docs:read", request -> {assertThat(request.permissionCodes()).containsExactly("test:docs:read");return decision.get();},
            (request, response, failure) -> response.setStatus(401),
            (request, response, failure) -> response.setStatus(403));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void everyAssetRequiresExactAuthorityEvenIfConsumerChainIsMissing() throws Exception {
        for (String path : routes.paths()) for (String method : List.of("GET", "HEAD")) {
            var filter = new OpenApiAuthorizationFilter(routes, adapter);
            var request = new MockHttpServletRequest(method, path);
            for (String authority : List.of("anonymous", "other:docs:read", "test:docs:read")) {
                decision.set(authority.equals("test:docs:read") ? com.simxe.vireon.security.permission.AccessDecision.ALLOW : com.simxe.vireon.security.permission.AccessDecision.DENY);
                SecurityContextHolder.clearContext();
                if (!authority.equals("anonymous")) SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("reader", "unused", List.of(new SimpleGrantedAuthority(authority))));
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (r, s) -> ((jakarta.servlet.http.HttpServletResponse)s).setStatus(204));
                assertThat(response.getStatus()).as(method + " " + path + " " + authority)
                        .isEqualTo(authority.equals("anonymous") ? 401 : authority.equals("test:docs:read") ? 204 : 403);
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            }
        }
    }
    @Test void aliasesAndUnsupportedMethodsNeverRenderAssets() throws Exception {
        var filter = new OpenApiAuthorizationFilter(routes, adapter);
        for (String path : List.of("/api/test/docs/", "/api/test/docs/review.json/", "/api/test/v3/api-docs/group", "/v3/api-docs", "/webjars/scalar/scalar.js", "/scalar")) {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", path), response, (r,s) -> fail("Alias reached resource"));
            assertThat(response.getStatus()).isEqualTo(404);
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "unused", List.of(new SimpleGrantedAuthority("test:docs:read"))));
        decision.set(com.simxe.vireon.security.permission.AccessDecision.ALLOW);
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", routes.documentPath()), response, (r,s) -> fail("Write reached resource"));
        assertThat(response.getStatus()).isEqualTo(405);
    }
    @Test void contextPathMatchesWithoutForwardedHeaders() {
        var request = new MockHttpServletRequest("GET", "/context/api/test/v3/api-docs.yaml");
        request.setContextPath("/context"); request.addHeader("X-Forwarded-Prefix", "/evil");
        assertThat(routes.matches(request)).isTrue();
    }
    @Test void permissionPortRatherThanAuthoritySnapshotControlsDocumentation() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null, List.of()));
        var filter=new OpenApiAuthorizationFilter(routes,adapter);
        decision.set(com.simxe.vireon.security.permission.AccessDecision.ALLOW);
        var allowed=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET",routes.documentPath()),allowed,(r,s)->((jakarta.servlet.http.HttpServletResponse)s).setStatus(204));assertThat(allowed.getStatus()).isEqualTo(204);
        decision.set(com.simxe.vireon.security.permission.AccessDecision.DENY);
        var denied=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET",routes.documentPath()),denied,(r,s)->fail("Revoked permission rendered documentation"));assertThat(denied.getStatus()).isEqualTo(403);
    }
    @Test void permissionFailuresPropagateToHostAndNeverRenderDocumentation() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null, List.of()));
        var failure=new IllegalStateException("fixture unavailable");
        var unavailable=new OpenApiSecurityAdapter("test:docs:read",request->{throw failure;},adapter.authenticationEntryPoint(),adapter.accessDeniedHandler());
        assertThatThrownBy(()->new OpenApiAuthorizationFilter(routes,unavailable).doFilter(new MockHttpServletRequest("GET",routes.documentPath()),new MockHttpServletResponse(),(r,s)->fail("Unavailable permission rendered documentation"))).isSameAs(failure);
        var invalid=new OpenApiSecurityAdapter("test:docs:read",request->null,adapter.authenticationEntryPoint(),adapter.accessDeniedHandler());
        assertThatThrownBy(()->new OpenApiAuthorizationFilter(routes,invalid).doFilter(new MockHttpServletRequest("GET",routes.documentPath()),new MockHttpServletResponse(),(r,s)->fail("Invalid permission result rendered documentation"))).isInstanceOf(NullPointerException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"//docs", "/docs/", "/docs%2f", "/docs?q", "/../docs", "https://remote/docs"})
    void invalidPathsFail(String path) { assertThatIllegalArgumentException().isThrownBy(() -> new OpenApiRoutes(path, "/v3/api-docs", false)); }
    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class))
                .withPropertyValues("springdoc.api-docs.enabled=false");
    }
    @Test void defaultsAreClosedAndDoNotRequireOptionalResources() {
        runner().run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(OpenApiRoutes.class); });
    }
    @Test void enabledWithoutAdapterFails() {
        runner().withPropertyValues("vireon.openapi.enabled=true", "springdoc.api-docs.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void explicitAdapterEnablesBasicDocsWithoutMetadata() {
        runner().withPropertyValues("vireon.openapi.enabled=true", "springdoc.api-docs.enabled=true")
                .withBean(OpenApiSecurityAdapter.class, () -> adapter).run(context -> assertThat(context).hasNotFailed().hasSingleBean(OpenApiController.class));
    }
    @ParameterizedTest @ValueSource(strings = {"springdoc.api-docs.enabled=true", "vireon.openapi.review.enabled=true", "vireon.openapi.try-it.enabled=true", "vireon.openapi.layout=unknown", "scalar.enabled=true", "springdoc.swagger-ui.enabled=true", "springdoc.use-management-port=true"})
    void contradictionsFail(String property) { runner().withPropertyValues(property).run(context -> assertThat(context).hasFailed()); }
    @Test void reviewMissingResourceFailsStartup() {
        runner().withPropertyValues("vireon.openapi.enabled=true", "springdoc.api-docs.enabled=true", "vireon.openapi.review.enabled=true")
                .withBean(OpenApiSecurityAdapter.class, () -> adapter).run(context -> assertThat(context).hasFailed());
    }
    @Test void metadataRejectsDuplicateKeysUnknownVersionTrailingContentAndLimits() throws Exception {
        var root = Map.of("schemaVersion", 1, "service", "test", "revision", "unavailable", "inputDigest", "a".repeat(64), "operations", List.of());
        byte[] valid = new ObjectMapper().writeValueAsBytes(root);
        assertThat(OpenApiMetadata.parse(valid, true).get("service").asText()).isEqualTo("test");
        String json = new String(valid, StandardCharsets.UTF_8);
        for (String invalid : List.of(json + "{}", json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), json.replace("\"service\":\"test\"", "\"service\":\"test\",\"service\":\"other\""))) {
            assertThatThrownBy(() -> OpenApiMetadata.parse(invalid.getBytes(StandardCharsets.UTF_8), true)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> OpenApiMetadata.parse(new byte[OpenApiMetadata.MAX_BYTES + 1], true)).isInstanceOf(IllegalStateException.class);
    }
    @Test void htmlAndBootstrapContainOnlyExplicitSameOriginConfiguration() throws Exception {
        var props = new OpenApiProperties(); props.setTitle("<script>bad</script>");
        var controller = new OpenApiController(props, routes, new OpenApiMetadata(props));
        var request = new MockHttpServletRequest("GET", routes.documentPath()); request.addHeader("Forwarded", "host=evil.example");
        assertThat(controller.html(request).getBody()).contains("&lt;script&gt;").doesNotContain("<script>bad", "evil.example", "<script>");
        assertThat(controller.bootstrap(request)).contains("\"telemetry\":false", "\"persistAuth\":false", "\"hideTestRequestButton\":true").doesNotContain("evil.example");
        props.getTryIt().setEnabled(true);
        assertThat(controller.bootstrap(request)).contains("\"hideTestRequestButton\":false", "\"hideClientButton\":true", "\"persistAuth\":false", "\"url\":\"/\"").doesNotContain("evil.example");
        assertThat(new String(controller.scalar(), StandardCharsets.UTF_8)).contains("vireon-scalar-source-sha256");
        assertThat(new String(controller.styles(), StandardCharsets.UTF_8)).contains("vireon-scalar-source-sha256");
    }
    @Test void runtimeRejectsHandlersMissingFromBuildCatalogIncludingComposedAnnotationEndpoints() throws Exception {
        var history = new ObjectMapper().readTree("{\"operations\":[]}");
        var handler = new org.springframework.web.method.HandlerMethod(new ComposedEndpoint(), ComposedEndpoint.class.getDeclaredMethod("read"));
        assertThatThrownBy(() -> OpenApiAutoConfiguration.validateHandlerHistory(history, List.of(handler)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Every consumer MVC handler");
        var stale = new ObjectMapper().readTree("{\"operations\":[{\"handler\":\"deleted.Controller#read\",\"operationId\":\"read\"}]}");
        assertThatThrownBy(() -> OpenApiAutoConfiguration.validateHandlerHistory(stale, List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("differ");
    }
    @org.springframework.web.bind.annotation.RestController
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
    @interface ExternalComposedController { }
    @ExternalComposedController
    static class ComposedEndpoint {
        @org.springframework.web.bind.annotation.GetMapping("/internal/composed")
        public String read() { return "internal"; }
    }
}
