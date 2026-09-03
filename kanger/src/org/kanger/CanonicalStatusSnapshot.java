/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;

/**
 * Cheap read-only projection of already existing KANGER runtime state.
 *
 * <p>The snapshot deliberately does not qualify, hydrate, enumerate storages,
 * traverse object factories, materialize transaction compatibility or touch
 * storage lifecycle. Missing information remains unavailable rather than
 * triggering computation.</p>
 */
public final class CanonicalStatusSnapshot {
    private final long userId;
    private final long mindId;
    private final int transactionLevel;
    private final String transactionCompatibility;
    private final String storage;
    private final String javaVersion;
    private final String jvmName;
    private final String osName;
    private final String osArch;

    private CanonicalStatusSnapshot(long userId,
                                    long mindId,
                                    int transactionLevel,
                                    String transactionCompatibility,
                                    String storage,
                                    String javaVersion,
                                    String jvmName,
                                    String osName,
                                    String osArch) {
        this.userId = userId;
        this.mindId = mindId;
        this.transactionLevel = transactionLevel;
        this.transactionCompatibility = transactionCompatibility;
        this.storage = storage;
        this.javaVersion = javaVersion;
        this.jvmName = jvmName;
        this.osName = osName;
        this.osArch = osArch;
    }

    /** Capture only state that is already present and cheap to observe. */
    public static CanonicalStatusSnapshot capture(IUser user, IMind mind)
            throws Exception {
        if (user == null) {
            throw new IllegalArgumentException("Status user is required");
        }
        if (!(mind instanceof Mind)) {
            throw new IllegalArgumentException("Status Mind is required");
        }

        Mind current = (Mind) mind;
        TransactionCompatibilityRegistry.Record compatibility =
                TransactionCompatibilityRegistry.peek(current);

        return new CanonicalStatusSnapshot(
                user.getId(),
                current.getId(),
                current.getTransactionLevel(),
                compatibility.getCompatibility().name(),
                current.isStorageUsed() ? current.getStorageName() : null,
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"));
    }

    public long getUserId() {
        return userId;
    }

    public long getMindId() {
        return mindId;
    }

    public int getTransactionLevel() {
        return transactionLevel;
    }

    public String getTransactionCompatibility() {
        return transactionCompatibility;
    }

    public String getStorage() {
        return storage;
    }

    public boolean isStorageUsed() {
        return storage != null;
    }

    /** Object counts are intentionally unavailable in v1: factory size may materialize indexes. */
    public boolean isObjectCountAvailable() {
        return false;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJvmName() {
        return jvmName;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsArch() {
        return osArch;
    }
}
