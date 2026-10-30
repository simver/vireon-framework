package com.simxe.vireon.security.principal;
import com.simxe.vireon.core.error.VireonErrorCode;
import com.simxe.vireon.core.error.VireonException;
import com.simxe.vireon.security.identity.AuthenticatedIdentity;
import com.simxe.vireon.security.identity.CurrentIdentity;
import java.util.Objects;
import java.util.Optional;
import java.time.Clock;
public final class VireonAuthenticationContext implements CurrentIdentity {
    private final VireonCurrentPrincipalProvider principalProvider;
    private final Clock clock;
    public VireonAuthenticationContext(VireonCurrentPrincipalProvider principalProvider) {
        this(principalProvider, Clock.systemUTC());
    }
    public VireonAuthenticationContext(VireonCurrentPrincipalProvider principalProvider, Clock clock) {
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.clock = Objects.requireNonNull(clock);
    }
    public Optional<VireonPrincipal> currentPrincipal() {
        return Objects.requireNonNull(principalProvider.currentPrincipal(), "Provider returned null")
                .filter(principal -> principal.identity().expiresAt().isAfter(clock.instant()));
    }
    @Override public Optional<AuthenticatedIdentity> current() { return currentPrincipal().map(VireonPrincipal::identity); }
    public VireonPrincipal requireAuthenticated(VireonErrorCode error) { return currentPrincipal().orElseThrow(() -> new VireonException(error)); }
}
