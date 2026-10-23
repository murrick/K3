package org.kanger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Property names may be precomputed; values and User overrides must stay live. */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class OptimizationOptionsTest {
    private static final String PREFIX = "kanger.experiment.";

    private static final class PropertiesScope implements AutoCloseable {
        private final Map<String, String> saved = new LinkedHashMap<>();

        PropertiesScope() {
            for (String name : OptimizationOptions.names()) {
                String key = PREFIX + name;
                saved.put(key, System.getProperty(key));
                System.clearProperty(key);
            }
        }

        @Override public void close() {
            saved.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    @Test
    void propertyChangesAfterFirstReadRemainVisible() throws Exception {
        try (PropertiesScope ignored = new PropertiesScope()) {
            Mind mind = new Mind(new User());
            for (String name : OptimizationOptions.names()) {
                String key = PREFIX + name;
                assertTrue(OptimizationOptions.enabled(mind, new String(name)), name);
                System.setProperty(key, "false");
                assertFalse(OptimizationOptions.enabled(mind, name), name);
                assertFalse(OptimizationOptions.enabled(null, name), name);
                System.setProperty(key, "TrUe");
                assertTrue(OptimizationOptions.enabled(mind, name), name);
                System.setProperty(key, "invalid");
                assertFalse(OptimizationOptions.enabled(mind, name), name);
                System.clearProperty(key);
                assertTrue(OptimizationOptions.enabled(mind, name), name);
            }
        }
    }

    @Test
    void individualOverridesAreLiveSharedByUserAndIsolatedFromOtherUsers() throws Exception {
        try (PropertiesScope ignored = new PropertiesScope()) {
            User user = new User();
            Mind first = new Mind(user), sameUser = new Mind(user);
            Mind otherUser = new Mind(new User());
            for (String name : OptimizationOptions.names()) {
                System.setProperty(PREFIX + name, "false");
                OptimizationOptions.set(first, name, true);
                assertTrue(OptimizationOptions.enabled(sameUser, name), name);
                assertFalse(OptimizationOptions.enabled(otherUser, name), name);
                System.setProperty(PREFIX + name, "true");
                OptimizationOptions.set(sameUser, name, false);
                assertFalse(OptimizationOptions.enabled(first, name), name);
                assertTrue(OptimizationOptions.enabled(otherUser, name), name);
            }
        }
    }

    @Test
    void setAllReplacesIndividualOverridesAndAppliesToChildTransactions() throws Exception {
        try (PropertiesScope ignored = new PropertiesScope()) {
            Mind root = new Mind(new User()), otherUser = new Mind(new User());
            Mind child = new Mind(root);
            try {
                for (boolean value : new boolean[]{false, true, false}) {
                    OptimizationOptions.set(root, OptimizationOptions.names()[0], !value);
                    OptimizationOptions.setAll(child, value);
                    for (String name : OptimizationOptions.names()) {
                        System.setProperty(PREFIX + name, Boolean.toString(!value));
                        assertEquals(value, OptimizationOptions.enabled(root, name), name);
                        assertEquals(value, OptimizationOptions.enabled(child, name), name);
                        assertEquals(!value, OptimizationOptions.enabled(otherUser, name), name);
                    }
                }
            } finally {
                root.release(child);
            }
        }
    }

    @Test
    void unknownNamesAreRejectedEvenWhenAllOverridesAreSet() throws Exception {
        try (PropertiesScope ignored = new PropertiesScope()) {
            Mind mind = new Mind(new User());
            OptimizationOptions.setAll(mind, true);
            for (String name : new String[]{"unknown", "", null}) {
                assertThrows(IllegalArgumentException.class, () -> OptimizationOptions.enabled(mind, name));
                assertThrows(IllegalArgumentException.class, () -> OptimizationOptions.enabled(null, name));
                assertThrows(IllegalArgumentException.class, () -> OptimizationOptions.set(mind, name, false));
            }
            assertThrows(IllegalArgumentException.class, () -> OptimizationOptions.setAll(null, false));
            assertThrows(IllegalArgumentException.class,
                    () -> OptimizationOptions.set(null, OptimizationOptions.names()[0], false));
            String[] names = OptimizationOptions.names();
            names[0] = "changed";
            assertNotEquals("changed", OptimizationOptions.names()[0]);
            assertThrows(UnsupportedOperationException.class,
                    () -> OptimizationOptions.snapshot(mind).clear());
        }
    }
}
