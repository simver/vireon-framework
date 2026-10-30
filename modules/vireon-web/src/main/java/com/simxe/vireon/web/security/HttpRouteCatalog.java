package com.simxe.vireon.web.security;
import java.util.Set;
/** Host-owned actual routes; null means unknown. */
@FunctionalInterface
public interface HttpRouteCatalog { Set<String> methods(String path); }
