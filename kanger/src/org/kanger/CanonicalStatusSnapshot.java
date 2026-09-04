/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.StorageTelemetry;

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
    private final long rootMindId;
    private final String userDir;
    private final String databaseDir;
    private final String sourceDir;
    private final int transactionLevel;
    private final int transactionCurrentPendingChildCount;
    private final int transactionRootPendingChildCount;
    private final boolean transactionQuiescent;
    private final String transactionCompatibility;
    private final String storage;
    private final boolean storageOpen;
    private final String storageBackend;
    private final StorageTelemetry storageTelemetry;
    private final String kangerVersion;
    private final String sourceBranch;
    private final String buildDate;
    private final String javaVersion;
    private final String jvmName;
    private final long uptimeMillis;
    private final long heapUsedBytes;
    private final long heapCommittedBytes;
    private final long heapMaxBytes;
    private final String osName;
    private final String osArch;

    private CanonicalStatusSnapshot(long userId,
                                    long mindId,
                                    long rootMindId,
                                    String userDir,
                                    String databaseDir,
                                    String sourceDir,
                                    int transactionLevel,
                                    int transactionCurrentPendingChildCount,
                                    int transactionRootPendingChildCount,
                                    boolean transactionQuiescent,
                                    String transactionCompatibility,
                                    String storage,
                                    boolean storageOpen,
                                    String storageBackend,
                                    StorageTelemetry storageTelemetry,
                                    String kangerVersion,
                                    String sourceBranch,
                                    String buildDate,
                                    String javaVersion,
                                    String jvmName,
                                    long uptimeMillis,
                                    long heapUsedBytes,
                                    long heapCommittedBytes,
                                    long heapMaxBytes,
                                    String osName,
                                    String osArch) {
        this.userId = userId;
        this.mindId = mindId;
        this.rootMindId = rootMindId;
        this.userDir = userDir;
        this.databaseDir = databaseDir;
        this.sourceDir = sourceDir;
        this.transactionLevel = transactionLevel;
        this.transactionCurrentPendingChildCount = transactionCurrentPendingChildCount;
        this.transactionRootPendingChildCount = transactionRootPendingChildCount;
        this.transactionQuiescent = transactionQuiescent;
        this.transactionCompatibility = transactionCompatibility;
        this.storage = storage;
        this.storageOpen = storageOpen;
        this.storageBackend = storageBackend;
        this.storageTelemetry = storageTelemetry == null
                ? StorageTelemetry.unavailable() : storageTelemetry;
        this.kangerVersion = kangerVersion;
        this.sourceBranch = sourceBranch;
        this.buildDate = buildDate;
        this.javaVersion = javaVersion;
        this.jvmName = jvmName;
        this.uptimeMillis = uptimeMillis;
        this.heapUsedBytes = heapUsedBytes;
        this.heapCommittedBytes = heapCommittedBytes;
        this.heapMaxBytes = heapMaxBytes;
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
        int transactionLevel = current.getTransactionLevel();
        Mind root = (Mind) current.getTop();
        int currentPendingChildren = current.pendingTransactionCount();
        int rootPendingChildren = root.pendingTransactionCount();
        boolean transactionQuiescent = transactionLevel == 0
                && rootPendingChildren == 0;
        TransactionCompatibilityRegistry.Record compatibility =
                TransactionCompatibilityRegistry.peek(current);
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        boolean storageOpen = current.isStorageUsed();
        IData data = attachedStorage(user);
        StorageTelemetry telemetry = data == null
                ? StorageTelemetry.unavailable() : data.telemetry();

        return new CanonicalStatusSnapshot(
                user.getId(),
                current.getId(),
                root.getId(),
                user.getUserDir(),
                user.getDatabaseDir(),
                user.getSourceDir(),
                transactionLevel,
                currentPendingChildren,
                rootPendingChildren,
                transactionQuiescent,
                compatibility.getCompatibility().name(),
                storageOpen ? current.getStorageName() : null,
                storageOpen,
                data == null ? null : data.getDescription(),
                telemetry,
                Version.PRODUCT_VERSION_S,
                Version.SOURCE_BRANCH,
                Version.DATE,
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                ManagementFactory.getRuntimeMXBean().getUptime(),
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                System.getProperty("os.name"),
                System.getProperty("os.arch"));
    }

    private static IData attachedStorage(IUser user) {
        if (!(user instanceof User)) {
            return null;
        }
        try {
            return ((User) user).getData();
        } catch (RuntimeErrorException missingStorageModule) {
            return null;
        }
    }

    public long getUserId() {
        return userId;
    }

    public long getMindId() {
        return mindId;
    }

    public long getRootMindId() {
        return rootMindId;
    }

    public String getUserDir() {
        return userDir;
    }

    public String getDatabaseDir() {
        return databaseDir;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public int getTransactionLevel() {
        return transactionLevel;
    }

    public int getTransactionCurrentPendingChildCount() {
        return transactionCurrentPendingChildCount;
    }

    public int getTransactionRootPendingChildCount() {
        return transactionRootPendingChildCount;
    }

    public boolean isTransactionQuiescent() {
        return transactionQuiescent;
    }

    public String getTransactionCompatibility() {
        return transactionCompatibility;
    }

    public String getStorage() {
        return storage;
    }

    public boolean isStorageUsed() {
        return storageOpen;
    }

    public String getStorageBackend() {
        return storageBackend;
    }

    public long getStorageBaseCount() {
        return storageTelemetry.getBaseCount();
    }

    public long getStorageRecordCount() {
        return storageTelemetry.getRecordCount();
    }

    public long getStoragePhysicalSizeBytes() {
        return storageTelemetry.getPhysicalSizeBytes();
    }

    public long getStoragePendingRecoveryBaseCount() {
        return storageTelemetry.getPendingRecoveryBaseCount();
    }

    public long getStorageCacheUsedBytes() {
        return storageTelemetry.getCacheUsedBytes();
    }

    public long getStorageCacheMaxBytes() {
        return storageTelemetry.getCacheMaxBytes();
    }

    public long getStorageCachedEntryCount() {
        return storageTelemetry.getCachedEntryCount();
    }

    public long getStorageCacheHits() {
        return storageTelemetry.getCacheHits();
    }

    public long getStorageCacheMisses() {
        return storageTelemetry.getCacheMisses();
    }

    public long getStorageCacheEvictions() {
        return storageTelemetry.getCacheEvictions();
    }

    /** Object counts are intentionally unavailable in v1: factory size may materialize indexes. */
    public boolean isObjectCountAvailable() {
        return false;
    }

    public String getKangerVersion() {
        return kangerVersion;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getBuildDate() {
        return buildDate;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJvmName() {
        return jvmName;
    }

    public long getUptimeMillis() {
        return uptimeMillis;
    }

    public long getHeapUsedBytes() {
        return heapUsedBytes;
    }

    public long getHeapCommittedBytes() {
        return heapCommittedBytes;
    }

    public long getHeapMaxBytes() {
        return heapMaxBytes;
    }

    public String getOsName() {
        return osName;
    }

    public String getOsArch() {
        return osArch;
    }
}
