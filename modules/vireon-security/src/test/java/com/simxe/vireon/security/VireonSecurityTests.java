package com.simxe.vireon.security;
import static org.assertj.core.api.Assertions.*;
import com.simxe.vireon.security.identity.*;
import com.simxe.vireon.security.permission.*;
import com.simxe.vireon.security.principal.*;
import com.simxe.vireon.core.error.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class VireonSecurityTests {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private AuthenticatedIdentity identity(Instant expires) { return new AuthenticatedIdentity("1", "User", "session", "app", expires); }
    @Test void rejectsInvalidIdentityReferencesAndEmptyPermissionChecks() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthenticatedIdentity("", "User", "s", "app", now));
        assertThatIllegalArgumentException().isThrownBy(() -> new PermissionRequest(Set.of(), PermissionRequest.Match.ALL));
        assertThatIllegalArgumentException().isThrownBy(() -> new PermissionRequest(Set.of(" "), PermissionRequest.Match.ANY));
        var codes = new HashSet<>(Set.of("business:read"));
        var request = new PermissionRequest(codes, PermissionRequest.Match.ANY); codes.clear();
        assertThat(request.permissionCodes()).containsExactly("business:read");
        assertThatThrownBy(() -> request.permissionCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void anonymousAndExpiredIdentityNeverInvokePermissionBackend() {
        PermissionEvaluator forbidden = request -> { throw new AssertionError("must not call backend"); };
        var request = new PermissionRequest(Set.of("business:read"), PermissionRequest.Match.ALL);
        assertThat(new VireonPermissionChecker(Optional::empty, forbidden, clock).evaluate(request)).isEqualTo(AccessDecision.DENY);
        assertThat(new VireonPermissionChecker(() -> Optional.of(identity(now)), forbidden, clock).evaluate(request)).isEqualTo(AccessDecision.DENY);
    }
    @Test void preservesAllAnyRequestsAndDoesNotTurnDependencyFailureIntoAllow() {
        var granted = Set.of("business:read");
        PermissionEvaluator evaluator = request -> (request.match() == PermissionRequest.Match.ALL
            ? granted.containsAll(request.permissionCodes()) : request.permissionCodes().stream().anyMatch(granted::contains)) ? AccessDecision.ALLOW : AccessDecision.DENY;
        var checker = new VireonPermissionChecker(() -> Optional.of(identity(now.plusSeconds(30))), evaluator, clock);
        assertThat(checker.evaluate(new PermissionRequest(Set.of("business:read", "business:write"), PermissionRequest.Match.ALL))).isEqualTo(AccessDecision.DENY);
        assertThat(checker.evaluate(new PermissionRequest(Set.of("business:read", "business:write"), PermissionRequest.Match.ANY))).isEqualTo(AccessDecision.ALLOW);
        var failure = new IllegalStateException("dependency unavailable");
        var broken = new VireonPermissionChecker(() -> Optional.of(identity(now.plusSeconds(30))), request -> { throw failure; }, clock);
        assertThatThrownBy(() -> broken.evaluate(new PermissionRequest(granted, PermissionRequest.Match.ANY))).isSameAs(failure);
        assertThat(checker.evaluate(new PermissionRequest(Set.of("BUSINESS:READ"), PermissionRequest.Match.ANY))).isEqualTo(AccessDecision.DENY);
    }
    @Test void contextDoesNotInventIdentityOrAcceptNullProviderResult() {
        var context = new VireonAuthenticationContext(Optional::empty);
        assertThatThrownBy(() -> context.requireAuthenticated(VireonCommonErrorCode.UNAUTHORIZED)).isInstanceOf(VireonException.class);
        assertThatThrownBy(() -> new VireonAuthenticationContext(() -> null).current()).isInstanceOf(NullPointerException.class);
    }
    @Test void contextDoesNotExposeExpiredIdentityToBusinessCallers() {
        var context = new VireonAuthenticationContext(() -> Optional.of(new VireonPrincipal(identity(Instant.EPOCH))));
        assertThat(context.current()).isEmpty();
        assertThat(context.currentPrincipal()).isEmpty();
        assertThatThrownBy(() -> context.requireAuthenticated(VireonCommonErrorCode.UNAUTHORIZED)).isInstanceOf(VireonException.class);
    }
}
