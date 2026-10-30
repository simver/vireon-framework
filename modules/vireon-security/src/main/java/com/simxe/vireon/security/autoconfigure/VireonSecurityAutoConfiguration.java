package com.simxe.vireon.security.autoconfigure;
import com.simxe.vireon.security.permission.VireonPermissionChecker;
import com.simxe.vireon.security.permission.PermissionEvaluator;
import com.simxe.vireon.security.permission.AccessDecision;
import com.simxe.vireon.security.identity.CurrentIdentity;
import com.simxe.vireon.security.principal.VireonAuthenticationContext;
import com.simxe.vireon.security.principal.VireonCurrentPrincipalProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
@AutoConfiguration
public class VireonSecurityAutoConfiguration {
    @Bean @ConditionalOnMissingBean(PermissionEvaluator.class)
    PermissionEvaluator defaultPermissionEvaluator() { return request -> AccessDecision.DENY; }
    @Bean @ConditionalOnMissingBean
    VireonPermissionChecker vireonPermissionChecker(org.springframework.beans.factory.ObjectProvider<CurrentIdentity> identities, PermissionEvaluator evaluator) {
        return new VireonPermissionChecker(identities.getIfAvailable(() -> Optional::empty), evaluator);
    }
    @Bean @ConditionalOnBean(VireonCurrentPrincipalProvider.class) @ConditionalOnMissingBean(CurrentIdentity.class)
    VireonAuthenticationContext vireonAuthenticationContext(VireonCurrentPrincipalProvider principalProvider) { return new VireonAuthenticationContext(principalProvider); }
}
