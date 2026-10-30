package com.simxe.vireon.observability.autoconfigure;

import com.simxe.vireon.observability.tracing.VireonTraceIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class VireonObservabilityAutoConfiguration {

    @Bean
    FilterRegistrationBean<VireonTraceIdFilter> vireonTraceIdFilterRegistration() {
        FilterRegistrationBean<VireonTraceIdFilter> registration =
                new FilterRegistrationBean<>(new VireonTraceIdFilter());
        registration.setName("vireonTraceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
