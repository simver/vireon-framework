package com.simxe.vireon.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 真实Servlet注册顺序的独立消费者反例；测试身份仅存在于此夹具。 */
@SpringBootTest(classes = OpenApiServerTests.Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"vireon.openapi.enabled=true", "vireon.openapi.path=/api/example/docs", "springdoc.api-docs.enabled=true", "springdoc.api-docs.path=/api/example/v3/api-docs"})
class OpenApiServerTests {
    @Autowired TestRestTemplate http;
    @Autowired OpenApiRoutes routes;
    @Test void servletDefenseProtectsSchemaAndStaticAssetsBehindAnOverlyPermissiveConsumerChain() {
        for (String path : routes.paths()) for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.HEAD)) {
            assertThat(http.exchange(path, method, HttpEntity.EMPTY, byte[].class).getStatusCode().value()).as(path).isEqualTo(401);
            var headers = new HttpHeaders(); headers.set("X-Fixture-Authority", "other:docs:read");
            assertThat(http.exchange(path, method, new HttpEntity<>(headers), byte[].class).getStatusCode().value()).as(path).isEqualTo(403);
            headers.set("X-Fixture-Authority", "example:docs:read");
            var allowed = http.exchange(path, method, new HttpEntity<>(headers), byte[].class);
            assertThat(allowed.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(allowed.getHeaders().getFirst("Content-Security-Policy")).contains("script-src 'self'").doesNotContain("unsafe-eval");
        }
    }
    @Test void dependencyWebjarAndDefaultSchemaAliasesRemainClosed() {
        for (String path : List.of("/webjars/scalar/scalar.js", "/scalar", "/v3/api-docs", "/api/example/v3/api-docs/group", "/api/example/docs/")) {
            assertThat(http.getForEntity(path, String.class).getStatusCode().value()).as(path).isEqualTo(404);
        }
    }
    @Test void publicServletCannotForwardOrIncludeDocumentation() {
        for (String target : List.of("/api/example/docs", "/api/example/docs/scalar.js", "/api/example/v3/api-docs")) {
            for (String mode : List.of("forward", "include")) {
                var result = http.getForEntity("/fixture-dispatch?mode=" + mode + "&target=" + target, String.class);
                assertThat(result.getStatusCode().value()).as(mode + " " + target).isGreaterThanOrEqualTo(400);
                assertThat(java.util.Objects.toString(result.getBody(), "")).doesNotContain("createApiReference", "<!doctype html>", "\"openapi\"");
            }
        }
    }
    @Test void encodedAliasesCannotReachDecodedMvcMappings() {
        for (String path : List.of("/api/example/%64ocs", "/api/example/%76%33/api-docs", "/api/example/docs;x=1", "/api/example//docs")) {
            var response = http.getForEntity(java.net.URI.create(http.getRootUri() + path), String.class);
            assertThat(response.getStatusCode().value()).as(path).isBetween(400, 499);
        }
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
        @Bean org.springframework.boot.web.servlet.ServletRegistrationBean<jakarta.servlet.http.HttpServlet> dispatchFixture() {
            return new org.springframework.boot.web.servlet.ServletRegistrationBean<>(new jakarta.servlet.http.HttpServlet() {
                @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                    var dispatcher = request.getRequestDispatcher(request.getParameter("target"));
                    if ("include".equals(request.getParameter("mode"))) dispatcher.include(request, response);
                    else dispatcher.forward(request, response);
                }
            }, "/fixture-dispatch");
        }
        @Bean OpenApiSecurityAdapter documentationSecurity() {
            return new OpenApiSecurityAdapter("example:docs:read",request->SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("example:docs:read"))?com.simxe.vireon.security.permission.AccessDecision.ALLOW:com.simxe.vireon.security.permission.AccessDecision.DENY, (r,s,e) -> s.setStatus(401), (r,s,e) -> s.setStatus(403));
        }
        @Bean SecurityFilterChain security(HttpSecurity security) throws Exception {
            security.authorizeHttpRequests(a -> a.anyRequest().permitAll()).addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                    String authority = request.getHeader("X-Fixture-Authority");
                    if (authority != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", null, List.of(new SimpleGrantedAuthority(authority))));
                    chain.doFilter(request, response);
                }
            }, AnonymousAuthenticationFilter.class);
            return security.build();
        }
    }
}
