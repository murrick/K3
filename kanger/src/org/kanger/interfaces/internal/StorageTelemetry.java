package org.kanger.interfaces.internal;

/** Cheap read-only telemetry for an already attached storage generation. */
public final class StorageTelemetry {
    private final long baseCount;
    private final long recordCount;
    private final long physicalSizeBytes;
    private final long pendingRecoveryBaseCount;
    private final long cacheUsedBytes;
    private final long cacheMaxBytes;
    private final long cachedEntryCount;
    private final long cacheHits;
    private final long cacheMisses;
    private final long cacheEvictions;

    private StorageTelemetry(long baseCount, long recordCount,
                             long physicalSizeBytes,
                             long pendingRecoveryBaseCount,
                             long cacheUsedBytes, long cacheMaxBytes,
                             long cachedEntryCount, long cacheHits,
                             long cacheMisses, long cacheEvictions) {
        this.baseCount = baseCount;
        this.recordCount = recordCount;
        this.physicalSizeBytes = physicalSizeBytes;
        this.pendingRecoveryBaseCount = pendingRecoveryBaseCount;
        this.cacheUsedBytes = cacheUsedBytes;
        this.cacheMaxBytes = cacheMaxBytes;
        this.cachedEntryCount = cachedEntryCount;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
        this.cacheEvictions = cacheEvictions;
    }

    public static StorageTelemetry of(long baseCount, long recordCount,
                                      long physicalSizeBytes,
                                      long pendingRecoveryBaseCount,
                                      long cacheUsedBytes, long cacheMaxBytes,
                                      long cachedEntryCount, long cacheHits,
                                      long cacheMisses, long cacheEvictions) {
        return new StorageTelemetry(baseCount, recordCount, physicalSizeBytes,
                pendingRecoveryBaseCount, cacheUsedBytes, cacheMaxBytes,
                cachedEntryCount, cacheHits, cacheMisses, cacheEvictions);
    }

    public static StorageTelemetry unavailable() {
        return new StorageTelemetry(-1L, -1L, -1L, -1L, -1L,
                -1L, -1L, -1L, -1L, -1L);
    }

    public long getBaseCount() { return baseCount; }
    public long getRecordCount() { return recordCount; }
    public long getPhysicalSizeBytes() { return physicalSizeBytes; }
    public long getPendingRecoveryBaseCount() { return pendingRecoveryBaseCount; }
    public long getCacheUsedBytes() { return cacheUsedBytes; }
    public long getCacheMaxBytes() { return cacheMaxBytes; }
    public long getCachedEntryCount() { return cachedEntryCount; }
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public long getCacheEvictions() { return cacheEvictions; }
}
