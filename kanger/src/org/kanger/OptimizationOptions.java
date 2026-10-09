package org.kanger;

import org.kanger.interfaces.IMind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Live session overrides for qualified acceleration paths; JVM defaults remain supported. */
public final class OptimizationOptions {
    static final class Session {
        // One volatile publication keeps the all-options switch atomic.
        volatile int overrides;
        synchronized void set(int bit, boolean enabled) {
            overrides = ((overrides | bit) & ~(bit << 8)) | (enabled ? bit << 8 : 0);
        }
        synchronized void setAll(boolean enabled) {
            overrides = 0xFF | (enabled ? 0xFF00 : 0);
        }
    }

    private static int bit(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return 1 << i;
        throw new IllegalArgumentException("Unknown optimization " + name);
    }

    private static Session session(IMind mind) {
        return mind != null && mind.getUser() instanceof User
                ? ((User) mind.getUser()).optimizationOptions() : null;
    }
    private static final String[] NAMES = {
        "versionedSolveSync", "residentBaseComparison", "singleTValueLookup",
        "resolvedCauseWeights", "compactCauseWeights", "candidateMembershipFilter",
        "compactFindSnapshots", "preserveTValueIndex"
    };
    private OptimizationOptions() { }

    public static String[] names() { return NAMES.clone(); }

    public static boolean enabled(IMind mind, String name) {
        Session session = session(mind);
        int overrides = session == null ? 0 : session.overrides;
        int bit = bit(name);
        if ((overrides & bit) != 0) return (overrides & (bit << 8)) != 0;
        return Boolean.parseBoolean(System.getProperty("kanger.experiment." + name, "true"));
    }

    public static void set(IMind mind, String name, boolean enabled) {
        Session session = session(mind);
        if (session == null) throw new IllegalArgumentException("Session options require a User");
        session.set(bit(name), enabled);
    }

    public static void setAll(IMind mind, boolean enabled) {
        Session session = session(mind);
        if (session == null) throw new IllegalArgumentException("Session options require a User");
        session.setAll(enabled);
    }

    public static Map<String, Boolean> snapshot(IMind mind) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String name : NAMES) result.put(name, enabled(mind, name));
        return Collections.unmodifiableMap(result);
    }
}
