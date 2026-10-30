package com.simxe.vireon.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.security.permission.VireonPermissionChecker;
import com.simxe.vireon.security.principal.VireonAuthenticationContext;
import com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider;
import com.simxe.vireon.security.principal.VireonPrincipal;
import jakarta.annotation.Priority;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.annotation.Order;

class VireonSecurityAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VireonSecurityAutoConfiguration.class));

    @Test
    void doesNotInventAuthenticationWhenProviderIsMissing() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(VireonPermissionChecker.class)
                .doesNotHaveBean(VireonAuthenticationContext.class)
                .doesNotHaveBean("securityFilterChain"));
    }

    @Test
    void createsAuthenticationContextForConsumerProvider() {
        VireonCurrentPrincipalProvider provider = Optional::empty;
        contextRunner.withBean(VireonCurrentPrincipalProvider.class, () -> provider).run(context -> {
            assertThat(context).hasSingleBean(VireonAuthenticationContext.class);
            assertThat(context.getBean(VireonAuthenticationContext.class).currentPrincipal()).isEmpty();
        });
    }

    @Test
    void resolvesProviderByNameAndAlias() {
        assertSelected(contextRunner.withBean("principalProvider", VireonCurrentPrincipalProvider.class, () -> provider(1))
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2)), 1);
        assertSelected(twoProviders().withInitializer(context ->
                context.getBeanFactory().registerAlias("beta", "principalProvider")), 2);
    }

    @Test
    void primaryOverridesNameAndFallbackYieldsToRegularCandidate() {
        assertSelected(contextRunner.withBean("principalProvider", VireonCurrentPrincipalProvider.class, () -> provider(1))
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2), bean -> bean.setPrimary(true)), 2);
        assertSelected(contextRunner.withBean("alpha", VireonCurrentPrincipalProvider.class, () -> provider(1),
                        bean -> bean.setFallback(true))
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2)), 2);
    }

    @Test
    void prioritySelectsOneCandidateButOrderDoesNotResolveAmbiguity() {
        assertSelected(contextRunner.withBean("alpha", PriorityProvider.class)
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2)), 1);
        contextRunner.withBean("alpha", OrderedProvider.class)
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void failsForAmbiguousOrMultiplePrimaryCandidates() {
        twoProviders().run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
        contextRunner.withBean("alpha", VireonCurrentPrincipalProvider.class, () -> provider(1), bean -> bean.setPrimary(true))
                .withBean("principalProvider", VireonCurrentPrincipalProvider.class, () -> provider(2), bean -> bean.setPrimary(true))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Test
    void userContextBacksOffEvenWhenProvidersAreMissingOrAmbiguous() {
        var custom = new VireonAuthenticationContext(provider(3));
        for (var runner : new ApplicationContextRunner[] {contextRunner, twoProviders()}) {
            runner.withBean(VireonAuthenticationContext.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(VireonAuthenticationContext.class);
                assertThat(context.getBean(VireonAuthenticationContext.class)).isSameAs(custom);
            });
        }
    }

    @Test
    void emptySelectedIdentityNeverTriesAnotherProvider() {
        var calls = new AtomicInteger();
        contextRunner.withBean("principalProvider", VireonCurrentPrincipalProvider.class, () -> Optional::empty)
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> () -> {
                    calls.incrementAndGet();
                    return provider(2).currentPrincipal();
                }).run(context -> {
                    assertThat(context).hasSingleBean(VireonAuthenticationContext.class);
                    assertThat(context.getBean(VireonAuthenticationContext.class).currentPrincipal()).isEmpty();
                    assertThat(calls).hasValue(0);
                });
    }

    private ApplicationContextRunner twoProviders() {
        return contextRunner.withBean("alpha", VireonCurrentPrincipalProvider.class, () -> provider(1))
                .withBean("beta", VireonCurrentPrincipalProvider.class, () -> provider(2));
    }

    @Test
    void validIdentityDoesNotGrantPermissionsWithoutBackend() {
        contextRunner.withBean(VireonCurrentPrincipalProvider.class, () -> provider(1)).run(context -> {
            var request = new com.simxe.vireon.security.permission.PermissionRequest(
                    Set.of("business:read"), com.simxe.vireon.security.permission.PermissionRequest.Match.ALL);
            assertThat(context.getBean(VireonPermissionChecker.class).evaluate(request))
                    .isEqualTo(com.simxe.vireon.security.permission.AccessDecision.DENY);
        });
    }

    private static VireonCurrentPrincipalProvider provider(long id) {
        return () -> Optional.of(new VireonPrincipal(new com.simxe.vireon.security.identity.AuthenticatedIdentity(Long.toString(id), "合成测试身份", "session", "app", java.time.Instant.parse("2099-01-01T00:00:00Z"))));
    }

    private static void assertSelected(ApplicationContextRunner runner, long id) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(VireonAuthenticationContext.class);
            assertThat(context.getBean(VireonAuthenticationContext.class).currentPrincipal())
                    .contains(new VireonPrincipal(new com.simxe.vireon.security.identity.AuthenticatedIdentity(Long.toString(id), "合成测试身份", "session", "app", java.time.Instant.parse("2099-01-01T00:00:00Z"))));
        });
    }

    @Priority(1)
    static class PriorityProvider implements VireonCurrentPrincipalProvider {
        public Optional<VireonPrincipal> currentPrincipal() { return provider(1).currentPrincipal(); }
    }

    @Order(1)
    static class OrderedProvider implements VireonCurrentPrincipalProvider {
        public Optional<VireonPrincipal> currentPrincipal() { return provider(1).currentPrincipal(); }
    }
}
