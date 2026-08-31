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
        return ensureCapabilities(user, defaultClassLoader(), RuntimeCapability.values());
    }

    public static RuntimeBootstrapResult ensure(IUser user,
                                                ClassLoader loader) throws Exception {
        return ensureCapabilities(user, loader, RuntimeCapability.values());
    }

    /**
     * Discovers and attaches only the requested capabilities, in the requested
     * order. This keeps narrow probe paths from acquiring unrelated runtime
     * modules while preserving the full bootstrap contract for normal users.
     */
    public static RuntimeBootstrapResult ensureCapabilities(
            IUser user,
            RuntimeCapability... capabilities) throws Exception {
        return ensureCapabilities(user, defaultClassLoader(), capabilities);
    }

    public static RuntimeBootstrapResult ensureCapabilities(
            IUser user,
            ClassLoader loader,
            RuntimeCapability... capabilities) throws Exception {
        if (!(user instanceof User)) {
            throw new IllegalArgumentException(
                    "Runtime bootstrap requires org.kanger.User");
        }
        if (loader == null) {
            throw new IllegalArgumentException("loader must not be null");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }

        List<RuntimeCapability> requested = new ArrayList<RuntimeCapability>();
        for (RuntimeCapability capability : capabilities) {
            if (capability == null) {
                throw new IllegalArgumentException("capability must not be null");
            }
            if (!requested.contains(capability)) {
                requested.add(capability);
            }
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

        for (RuntimeCapability capability : requested) {
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

    private static ClassLoader defaultClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? RuntimeBootstrap.class.getClassLoader() : loader;
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
