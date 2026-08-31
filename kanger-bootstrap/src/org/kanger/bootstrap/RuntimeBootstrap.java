package org.kanger.bootstrap;

import org.kanger.User;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers and attaches optional runtime capabilities around a KANGER user.
 *
 * <p>Classpath absence is a supported state. A single provider for a missing
 * capability is attached automatically. If multiple providers are present,
 * selection must be explicit in the user configuration.</p>
 */
public final class RuntimeBootstrap {

    public static final String STORAGE_MODULE_PROPERTY = "runtime.storage.module";
    public static final String UDF_MODULE_PROPERTY = "runtime.udf.module";

    private RuntimeBootstrap() {
    }

    public static RuntimeBootstrapResult ensure(IUser user) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = RuntimeBootstrap.class.getClassLoader();
        }
        return ensure(user, loader);
    }

    public static RuntimeBootstrapResult ensure(IUser user,
                                                ClassLoader loader) throws Exception {
        if (!(user instanceof User)) {
            throw new IllegalArgumentException(
                    "Runtime bootstrap requires org.kanger.User");
        }
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }

        Map<RuntimeCapability, List<RuntimeModule>> discovered =
                new EnumMap<RuntimeCapability, List<RuntimeModule>>(RuntimeCapability.class);
        for (RuntimeCapability capability : RuntimeCapability.values()) {
            discovered.put(capability, new ArrayList<RuntimeModule>());
        }

        for (RuntimeModule module : ServiceLoader.load(RuntimeModule.class, loader)) {
            if (module == null || module.getCapability() == null) {
                throw new RuntimeErrorException(
                        "Runtime module without capability was discovered");
            }
            discovered.get(module.getCapability()).add(module);
        }

        User concreteUser = (User) user;
        Map<RuntimeCapability, String> loaded =
                new EnumMap<RuntimeCapability, String>(RuntimeCapability.class);

        for (RuntimeCapability capability : RuntimeCapability.values()) {
            if (isAttached(concreteUser, capability)) {
                continue;
            }
            RuntimeModule selected = select(user, capability, discovered.get(capability));
            if (selected != null) {
                selected.init(user);
                loaded.put(capability, selected.getDescription());
            }
        }
        return new RuntimeBootstrapResult(loaded);
    }

    private static boolean isAttached(User user,
                                      RuntimeCapability capability) throws Exception {
        try {
            if (capability == RuntimeCapability.STORAGE) {
                user.getData();
            } else {
                user.getUdf();
            }
            return true;
        } catch (RuntimeErrorException missing) {
            return false;
        }
    }

    private static RuntimeModule select(IUser user,
                                        RuntimeCapability capability,
                                        List<RuntimeModule> candidates) throws Exception {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String requested = configuredModule(user, capability);
        if (!requested.isEmpty()) {
            for (RuntimeModule candidate : candidates) {
                if (requested.equalsIgnoreCase(candidate.getId())) {
                    return candidate;
                }
            }
            throw new RuntimeErrorException(
                    "Configured " + capability + " runtime module '" + requested
                            + "' was not found; discovered: " + ids(candidates));
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        throw new RuntimeErrorException(
                "Multiple " + capability + " runtime modules discovered: "
                        + ids(candidates) + "; configure " + property(capability));
    }

    private static String configuredModule(IUser user,
                                           RuntimeCapability capability) throws Exception {
        String key = property(capability);
        if (!user.containsProperty(key)) {
            return "";
        }
        String value = user.getProperty(key, "");
        return value == null ? "" : value.trim();
    }

    private static String property(RuntimeCapability capability) {
        return capability == RuntimeCapability.STORAGE
                ? STORAGE_MODULE_PROPERTY
                : UDF_MODULE_PROPERTY;
    }

    private static String ids(List<RuntimeModule> candidates) {
        StringBuilder value = new StringBuilder();
        for (RuntimeModule candidate : candidates) {
            if (value.length() > 0) {
                value.append(", ");
            }
            value.append(candidate.getId());
        }
        return value.toString();
    }
}
