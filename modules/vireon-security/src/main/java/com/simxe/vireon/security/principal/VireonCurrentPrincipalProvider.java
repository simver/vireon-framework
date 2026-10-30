package com.simxe.vireon.security.principal;

import java.util.Optional;

@FunctionalInterface
public interface VireonCurrentPrincipalProvider {

    Optional<VireonPrincipal> currentPrincipal();
}
