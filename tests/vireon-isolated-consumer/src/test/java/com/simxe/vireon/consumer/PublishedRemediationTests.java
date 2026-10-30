package com.simxe.vireon.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.web.bind.annotation.*;

class PublishedRemediationTests {
    @org.junit.jupiter.api.Test
    void publishedMatchersAcceptUntracedWebAndKeepExplicitTraceCheck() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new EnvelopeController()).build();
        var ok = mvc.perform(get("/envelope-ok"))
                .andExpect(com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.success()).andReturn();
        var fail = mvc.perform(get("/envelope-fail"))
                .andExpect(com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.failure(409, "CONFLICT")).andReturn();
        for (var result : java.util.List.of(ok, fail)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    com.simxe.vireon.testsupport.VireonMockMvcResultMatchers.traceIdPresent().match(result))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @org.junit.jupiter.api.Test
    void publishedSecurityRetainsNameSelectionAndAmbiguityFailure() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        com.simxe.vireon.security.autoconfigure.VireonSecurityAutoConfiguration.class));
        runner.withBean("principalProvider", com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider.class,
                        () -> () -> java.util.Optional.of(new com.simxe.vireon.security.principal.VireonPrincipal(
                                new com.simxe.vireon.security.identity.AuthenticatedIdentity("1", "合成身份", "session", "app", java.time.Instant.parse("2099-01-01T00:00:00Z")))))
                .withBean("beta", com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider.class,
                        () -> () -> { throw new AssertionError("must not try another provider"); })
                .run(context -> {
                    assertThat(context).hasSingleBean(com.simxe.vireon.security.principal.VireonAuthenticationContext.class);
                    assertThat(context.getBean(com.simxe.vireon.security.principal.VireonAuthenticationContext.class)
                            .currentPrincipal().orElseThrow().identity().subjectId()).isEqualTo("1");
                });
        runner.withBean("alpha", com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider.class,
                        () -> java.util.Optional::empty)
                .withBean("beta", com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider.class,
                        () -> java.util.Optional::empty)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(org.springframework.beans.factory.NoUniqueBeanDefinitionException.class));
    }

    @RestController
    static class EnvelopeController {
        @GetMapping("/envelope-ok")
        VireonApiResponse<String> ok() { return VireonResponses.ok("ready"); }
        @GetMapping("/envelope-fail")
        org.springframework.http.ResponseEntity<VireonApiResponse<Void>> fail() {
            return org.springframework.http.ResponseEntity.status(409)
                    .body(new VireonApiResponse<>("CONFLICT", "冲突", null, null));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sameSignatureOnDifferentInstanceIsAnInternalError(boolean adapted, boolean jdk) throws Exception {
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var internal = proxy(new Controller(null), adapted, jdk, validators);
            var entry = proxy(new Controller(internal), adapted, jdk, validators);
            // 实际经过嵌套代理及 MVC 入口，不用手工构造异常代替分派。
            var outer = new ProxyFactory(entry);
            outer.setProxyTargetClass(!jdk);
            outer.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
            var mvc = MockMvcBuilders.standaloneSetup(outer.getProxy())
                    .setControllerAdvice(new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler()).build();
            mvc.perform(get("/origin").param("external-count", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].path").value("external-count"));
            var response = mvc.perform(get("/origin").param("external-count", "1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("VIREON.COMMON.INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue())).andReturn();
            assertThat(response.getResponse().getContentAsString()).doesNotContain("private minimum");
            mvc.perform(get("/origin").param("external-count", "1").accept("text/plain"))
                    .andExpect(status().isNotAcceptable()).andExpect(content().string(""));
            mvc.perform(head("/origin").param("external-count", "1"))
                    .andExpect(status().isInternalServerError()).andExpect(content().string(""));
        }
    }

    private static Api proxy(Controller target, boolean adapted, boolean jdk,
            jakarta.validation.ValidatorFactory validators) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(!jdk);
        factory.addAdvice(new MethodValidationInterceptor(validators::getValidator, adapted));
        return (Api) factory.getProxy();
    }

    @RestController
    @Validated
    public interface Api {
        @GetMapping("/origin")
        VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(value = 1, message = "private minimum") int count);
    }

    @RestController
    @Validated
    public static class Controller implements Api {
        private final Api delegate;
        Controller(Api delegate) { this.delegate = delegate; }
        public VireonApiResponse<Integer> count(int count) {
            return delegate == null ? VireonResponses.ok(count) : delegate.count(0);
        }
    }
}
