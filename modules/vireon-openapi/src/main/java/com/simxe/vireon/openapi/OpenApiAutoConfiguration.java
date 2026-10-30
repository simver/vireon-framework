package com.simxe.vireon.openapi;

import jakarta.servlet.DispatcherType;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Servlet文档入口；启用组合和安全适配不成立时拒绝启动。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {
    @Bean
    SmartInitializingSingleton openApiConfigurationValidation(OpenApiProperties properties, Environment environment,
            ObjectProvider<GroupedOpenApi> groups) {
        return () -> {
            boolean schema = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true);
            if (schema != properties.isEnabled()) throw new IllegalStateException("vireon.openapi.enabled and springdoc.api-docs.enabled must agree explicitly");
            if (!java.util.Set.of("balanced", "request-focused", "request-expanded").contains(properties.getLayout())) throw new IllegalStateException("Unsupported documentation layout");
            if (properties.getTitle() == null || properties.getTitle().isBlank() || properties.getTitle().length() > 160) throw new IllegalStateException("Invalid documentation title");
            if (properties.getReview() == null || properties.getGitHistory() == null || properties.getTryIt() == null) throw new IllegalStateException("Invalid feature configuration");
            if (!properties.isEnabled() && (properties.getReview().isEnabled() || properties.getGitHistory().isEnabled() || properties.getTryIt().isEnabled())) throw new IllegalStateException("Documentation extensions require enabled documentation");
            for (String key : java.util.List.of("springdoc.use-management-port", "springdoc.swagger-ui.enabled", "scalar.enabled")) {
                    if (environment.getProperty(key, Boolean.class, false)) throw new IllegalStateException("Unsupported extra documentation entry point: " + key);
            }
            if (properties.isEnabled()) {
                if (groups.stream().findAny().isPresent() || environment.containsProperty("springdoc.group-configs[0].group")) throw new IllegalStateException("Grouped documentation is not supported");
            }
        };
    }
    @Bean
    @ConditionalOnProperty(name = "vireon.openapi.enabled", havingValue = "true")
    OpenApiRoutes openApiRoutes(OpenApiProperties properties, Environment environment) {
        return new OpenApiRoutes(properties.getPath(), environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"), properties.getReview().isEnabled());
    }
    @Bean
    FilterRegistrationBean<OpenApiAuthorizationFilter> openApiAuthorizationRegistration(OpenApiProperties properties,
            ObjectProvider<OpenApiSecurityAdapter> adapters, ObjectProvider<SecurityProperties> security, Environment environment) {
        var routes = new OpenApiRoutes(properties.getPath(), environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"), properties.getReview().isEnabled());
        var adapter = properties.isEnabled() ? adapters.getObject() : null;
        var registration = new FilterRegistrationBean<>(new OpenApiAuthorizationFilter(routes, adapter, properties.isEnabled()));
        int order = security.getIfAvailable(SecurityProperties::new).getFilter().getOrder();
        order = environment.getProperty("spring.security.filter.order", Integer.class, order);
        if (order == Integer.MAX_VALUE) throw new IllegalStateException("Security filter order leaves no documentation defense slot");
        registration.setOrder(order + 1);
        registration.setDispatcherTypes(java.util.EnumSet.allOf(DispatcherType.class));
        registration.addUrlPatterns("/*");
        return registration;
    }
    @Bean
    @ConditionalOnProperty(name = "vireon.openapi.enabled", havingValue = "true")
    OpenApiMetadata openApiMetadata(OpenApiProperties properties) { return new OpenApiMetadata(properties); }
    @Bean
    @ConditionalOnProperty(name = "vireon.openapi.enabled", havingValue = "true")
    OpenApiGitCustomizer openApiGitCustomizer(OpenApiMetadata metadata, OpenApiProperties properties) {
        return new OpenApiGitCustomizer(properties.getGitHistory().isEnabled() ? metadata.history() : null);
    }
    @Bean
    @ConditionalOnProperty(name = "vireon.openapi.enabled", havingValue = "true")
    OpenApiController openApiController(OpenApiProperties properties, OpenApiRoutes routes, OpenApiMetadata metadata) {
        return new OpenApiController(properties, routes, metadata);
    }
    @Bean
    @ConditionalOnProperty(name = {"vireon.openapi.enabled", "vireon.openapi.review.enabled"}, havingValue = "true")
    OpenApiReviewController openApiReviewController(OpenApiMetadata metadata) { return new OpenApiReviewController(metadata); }
    @Bean
    @ConditionalOnProperty(name = "vireon.openapi.enabled", havingValue = "true")
    SmartInitializingSingleton openApiHandlerHistoryValidation(OpenApiMetadata metadata,
            ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings) {
        return () -> {
            if (metadata.history() == null) return;
            validateHandlerHistory(metadata.history(), mappings.stream().flatMap(mapping -> mapping.getHandlerMethods().values().stream()).toList());
        };
    }
    static void validateHandlerHistory(com.fasterxml.jackson.databind.JsonNode history,
            java.util.Collection<org.springframework.web.method.HandlerMethod> handlers) {
            var actual = new java.util.HashMap<String, String>();
            handlers.forEach(handler -> {
                Class<?> type = handler.getBeanType();
                if (type == OpenApiController.class || type == OpenApiReviewController.class
                        || type == org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController.class
                        || type == org.springdoc.webmvc.api.OpenApiWebMvcResource.class) return;
                var operation = handler.getMethodAnnotation(io.swagger.v3.oas.annotations.Operation.class);
                if (operation == null) throw new IllegalStateException("Every consumer MVC handler requires a catalog operationId");
                {
                    String key = handler.getBeanType().getName() + "#" + handler.getMethod().getName();
                    if (operation.operationId().isBlank()) throw new IllegalStateException("Explicit operationId required for history");
                    actual.put(key, operation.operationId());
                }
            });
            var expected = new java.util.HashMap<String, String>();
            for (var entry : history.get("operations")) expected.put(entry.get("handler").asText(), entry.get("operationId").asText());
            if (!actual.equals(expected)) throw new IllegalStateException("Runtime handlers and history catalog differ");
    }
}
