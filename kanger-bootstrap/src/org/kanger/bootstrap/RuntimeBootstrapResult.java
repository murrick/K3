package org.kanger.bootstrap;

import java.util.EnumMap;
import java.util.Map;

/**
 * Capabilities attached by one bootstrap pass.
 */
public final class RuntimeBootstrapResult {

    private final Map<RuntimeCapability, String> loaded;

    RuntimeBootstrapResult(Map<RuntimeCapability, String> loaded) {
        this.loaded = new EnumMap<RuntimeCapability, String>(loaded);
    }

    public boolean loaded(RuntimeCapability capability) {
        return loaded.containsKey(capability);
    }

    public String getDescription(RuntimeCapability capability) {
        return loaded.get(capability);
    }
}
