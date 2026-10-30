package com.simxe.vireon.security.identity;
import java.util.Optional;
@FunctionalInterface
public interface CurrentIdentity { Optional<AuthenticatedIdentity> current(); }
