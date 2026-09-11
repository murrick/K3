/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.Locale;

/** Plain-text projection of one {@link CanonicalStatusSnapshot}. */
public final class CanonicalStatusRenderer {
    private CanonicalStatusRenderer() {
    }

    public static String render(CanonicalStatusSnapshot snapshot,
                                String section,
                                String subsection) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Status snapshot is required");
        }

        String normalizedSection = normalize(section);
        String normalizedSubsection = normalize(subsection);
        if (normalizedSection == null) {
            return renderRoot(snapshot);
        }
        if ("core".equals(normalizedSection)) {
            return renderCore(snapshot, normalizedSubsection);
        }
        if ("storage".equals(normalizedSection)) {
            return renderStorage(snapshot);
        }
        if ("session".equals(normalizedSection)) {
            return renderSession(snapshot);
        }
        if ("runtime".equals(normalizedSection)) {
            return renderRuntime(snapshot);
        }
        throw new IllegalArgumentException("Unsupported status section " + section);
    }

    private static String renderRoot(CanonicalStatusSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        append(out, "core.transaction.level", snapshot.getTransactionLevel());
        append(out, "core.transaction.compatibility",
                snapshot.getTransactionCompatibility());
        append(out, "storage.current",
                snapshot.isStorageUsed() ? snapshot.getStorage() : "none");
        append(out, "session.user", snapshot.getUserId());
        append(out, "runtime.version", value(snapshot.getKangerVersion()));
        append(out, "runtime.java", value(snapshot.getJavaVersion()));
        return out.toString();
    }

    private static String renderCore(CanonicalStatusSnapshot snapshot,
                                     String subsection) {
        if (subsection == null) {
            StringBuilder out = new StringBuilder();
            append(out, "transaction.level", snapshot.getTransactionLevel());
            append(out, "transaction.compatibility",
                    snapshot.getTransactionCompatibility());
            append(out, "transaction.quiescent", snapshot.isTransactionQuiescent());
            append(out, "transaction.current.pending.children",
                    snapshot.getTransactionCurrentPendingChildCount());
            append(out, "transaction.root.pending.children",
                    snapshot.getTransactionRootPendingChildCount());
            append(out, "objects", "unavailable");
            return out.toString();
        }
        if ("transaction".equals(subsection)) {
            StringBuilder out = new StringBuilder();
            append(out, "level", snapshot.getTransactionLevel());
            append(out, "compatibility", snapshot.getTransactionCompatibility());
            append(out, "quiescent", snapshot.isTransactionQuiescent());
            append(out, "current.pending.children",
                    snapshot.getTransactionCurrentPendingChildCount());
            append(out, "root.pending.children",
                    snapshot.getTransactionRootPendingChildCount());
            return out.toString();
        }
        if ("levels".equals(subsection)) {
            StringBuilder out = new StringBuilder();
            append(out, "current", snapshot.getTransactionLevel());
            append(out, "mind", snapshot.getMindId());
            append(out, "root.mind", snapshot.getRootMindId());
            return out.toString();
        }
        if ("objects".equals(subsection)) {
            return "count=unavailable";
        }
        throw new IllegalArgumentException("Unsupported core status subsection "
                + subsection);
    }

    private static String renderStorage(CanonicalStatusSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        append(out, "current", snapshot.isStorageUsed()
                ? snapshot.getStorage() : "none");
        append(out, "state", snapshot.isStorageUsed() ? "open" : "closed");
        append(out, "backend", value(snapshot.getStorageBackend()));
        append(out, "bases", metric(snapshot.getStorageBaseCount()));
        append(out, "records", metric(snapshot.getStorageRecordCount()));
        append(out, "physical.bytes", metric(snapshot.getStoragePhysicalSizeBytes()));
        append(out, "wal.pending.bases",
                metric(snapshot.getStoragePendingRecoveryBaseCount()));
        append(out, "cache.used.bytes", metric(snapshot.getStorageCacheUsedBytes()));
        append(out, "cache.max.bytes", metric(snapshot.getStorageCacheMaxBytes()));
        append(out, "cache.entries", metric(snapshot.getStorageCachedEntryCount()));
        append(out, "cache.hits", metric(snapshot.getStorageCacheHits()));
        append(out, "cache.misses", metric(snapshot.getStorageCacheMisses()));
        append(out, "cache.evictions", metric(snapshot.getStorageCacheEvictions()));
        return out.toString();
    }

    private static String renderSession(CanonicalStatusSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        append(out, "user", snapshot.getUserId());
        append(out, "mind", snapshot.getMindId());
        append(out, "user.dir", value(snapshot.getUserDir()));
        append(out, "database.dir", value(snapshot.getDatabaseDir()));
        append(out, "sources.dir", value(snapshot.getSourceDir()));
        return out.toString();
    }

    private static String renderRuntime(CanonicalStatusSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        append(out, "version", value(snapshot.getKangerVersion()));
        append(out, "source.branch", value(snapshot.getSourceBranch()));
        append(out, "build.date", value(snapshot.getBuildDate()));
        append(out, "java", value(snapshot.getJavaVersion()));
        append(out, "jvm", value(snapshot.getJvmName()));
        append(out, "uptime.ms", metric(snapshot.getUptimeMillis()));
        append(out, "heap.used.bytes", metric(snapshot.getHeapUsedBytes()));
        append(out, "heap.committed.bytes", metric(snapshot.getHeapCommittedBytes()));
        append(out, "heap.max.bytes", metric(snapshot.getHeapMaxBytes()));
        append(out, "os", value(snapshot.getOsName()));
        append(out, "arch", value(snapshot.getOsArch()));
        return out.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String value(String value) {
        return value == null || value.isEmpty() ? "unavailable" : value;
    }

    private static String metric(long value) {
        return value < 0 ? "unavailable" : Long.toString(value);
    }

    private static void append(StringBuilder out, String name, Object value) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(name).append('=').append(value);
    }
}
