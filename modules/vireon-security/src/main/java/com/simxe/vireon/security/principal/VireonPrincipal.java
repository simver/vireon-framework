package com.simxe.vireon.security.principal;
import com.simxe.vireon.security.identity.AuthenticatedIdentity;
import java.util.Objects;
public record VireonPrincipal(AuthenticatedIdentity identity) {
    public VireonPrincipal { Objects.requireNonNull(identity, "identity"); }
}
