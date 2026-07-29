/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.CommentFactory;
import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.DomainFactory;
import org.kanger.factory.FValueFactory;
import org.kanger.factory.FunctionFactory;
import org.kanger.factory.LibraryFactory;
import org.kanger.factory.PredicateFactory;
import org.kanger.factory.RuleFactory;
import org.kanger.factory.TValueFactory;
import org.kanger.factory.TVariableFactory;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Black-box performance characterization for large homogeneous predicate
 * workloads. Timing remains observational; cache bounds and result counts are
 * deterministic contracts.
 */
public final class KangerPerformanceRunner {

    private static final int[] DEFAULT_SIZES = new int[]{100, 500, 1000};
    private static final String[] STORAGE_SCHEMAS = new String[]{
            DictionaryFactory.SCHEMA,
            DomainFactory.SCHEMA,
            FunctionFactory.SCHEMA,
            PredicateFactory.SCHEMA,
            RuleFactory.SCHEMA,
            TVariableFactory.SCHEMA,
            LibraryFactory.SCHEMA,
            TValueFactory.SCHEMA,
            FValueFactory.SCHEMA,
            CommentFactory.SCHEMA
    };

    private KangerPerformanceRunner() {
    }

    public static void main(String[] args) {
        try {
            Path testHome = createTestHome();
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            boolean storage = Boolean.parseBoolean(
                    System.getProperty("kanger.perf.storage", "false"));
            int[] sizes = parseSizes(args);

            System.out.println("KANGER performance home: " + testHome.toAbsolutePath());
            System.out.println("KANGER performance storage: " + storage);
            System.out.println("mode,size,operation,millis,result,rows,solutions,rules,domains,tvalues,"
                    + "cache_hits,cache_misses,cache_evictions,cache_entries,cache_bytes,cache_max_bytes");

            for (int size : sizes) {
                runCase(size, storage);
            }
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCase(int size, boolean storage) throws Exception {
        String suffix = size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser("perf-" + suffix, "perf-" + suffix);

        String configuredCacheSize = System.getProperty("kanger.perf.cache.size");
        if (configuredCacheSize != null && !configuredCacheSize.trim().isEmpty()) {
            user.setProperty("cache.size", configuredCacheSize.trim());
        }
        String configuredDataCacheSize = System.getProperty("kanger.perf.data.cache.size");
        if (configuredDataCacheSize != null && !configuredDataCacheSize.trim().isEmpty()) {
            user.setProperty("cache.data.size", configuredDataCacheSize.trim());
        }
        user.setProperty("cache.enable", System.getProperty("kanger.perf.cache.enable", "true"));

        new UDF().init(user);
        new DB().init(user);

        Mind mind = new Mind(user);
        String storageName = null;
        try {
            if (storage) {
                if (mind.isStorageUsed()) {
                    mind = (Mind) mind.closeStorage();
                }
                storageName = "data/perf-indexed-linker-" + suffix;
                mind = (Mind) mind.useStorage(storageName);
            }
            mind = (Mind) mind.clearWorkspace();

            CacheSnapshot beforeInsert = cacheSnapshot(user);
            long started = System.nanoTime();
            for (int i = 1; i <= size; ++i) {
                Boolean result = mind.query(
                        "!value(" + i + "," + (1000 + i) + ",7);",
                        null,
                        false);
                if (!Boolean.TRUE.equals(result)) {
                    throw new IllegalStateException("Insert failed at row " + i);
                }
            }
            report(mind, user, storage, size, "insert-sequential", started,
                    Boolean.TRUE, beforeInsert);

            if (storage) {
                long reopenStarted = System.nanoTime();
                mind = (Mind) mind.closeStorage();
                mind = (Mind) mind.useStorage(storageName);
                CacheSnapshot reopened = cacheSnapshot(user);
                report(mind, user, true, size, "reopen-storage", reopenStarted,
                        Boolean.TRUE, reopened);
            }

            int key = Math.max(1, size / 2);
            measureQuery(mind, user, storage, size, "query-exact",
                    "?value(" + key + "," + (1000 + key) + ",7);");
            measureQuery(mind, user, storage, size, "query-two-constants",
                    "?$z value(" + key + "," + (1000 + key) + ",z);");
            measureQuery(mind, user, storage, size, "query-one-constant",
                    "?$y $z value(" + key + ",y,z);");
            measureQuery(mind, user, storage, size, "query-all-variables",
                    "?$x $y $z value(x,y,z);");
        } finally {
            if (storage && mind.isStorageUsed()) {
                mind.removeStorage(null);
            }
        }
    }

    private static void measureQuery(Mind mind,
                                     User user,
                                     boolean storage,
                                     int size,
                                     String operation,
                                     String query) throws Exception {
        CacheSnapshot before = cacheSnapshot(user);
        long started = System.nanoTime();
        Boolean result = mind.query(query, null, false);
        report(mind, user, storage, size, operation, started, result, before);
    }

    private static void report(Mind mind,
                               User user,
                               boolean storage,
                               int size,
                               String operation,
                               long started,
                               Boolean result,
                               CacheSnapshot before) throws Exception {
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        CacheSnapshot after = cacheSnapshot(user);
        assertCacheBound(after);

        System.out.printf(
                "%s,%d,%s,%.3f,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                storage ? "storage" : "memory",
                size,
                operation,
                millis,
                String.valueOf(result),
                mind.getValues().size(),
                mind.getSolutions().size(),
                mind.getRules().size(),
                mind.getDomains().size(),
                mind.getTValues().size(),
                delta(after.hits, before.hits),
                delta(after.misses, before.misses),
                delta(after.evictions, before.evictions),
                after.entries,
                after.usedBytes,
                after.maxBytes);
    }

    private static long delta(long after, long before) {
        return after < 0L || before < 0L ? -1L : after - before;
    }

    private static CacheSnapshot cacheSnapshot(User user) {
        long hits = 0L;
        long misses = 0L;
        long evictions = 0L;
        long entries = 0L;
        long usedBytes = 0L;
        long maxBytes = 0L;
        boolean telemetry = false;

        for (String schema : STORAGE_SCHEMAS) {
            IBase base = user.getStorage(schema);
            if (base == null) {
                continue;
            }
            long baseHits = base.getCacheHits();
            long baseMisses = base.getCacheMisses();
            long baseEvictions = base.getCacheEvictions();
            long baseEntries = base.getCachedEntryCount();
            if (baseHits >= 0L && baseMisses >= 0L && baseEvictions >= 0L && baseEntries >= 0L) {
                telemetry = true;
                hits += baseHits;
                misses += baseMisses;
                evictions += baseEvictions;
                entries += baseEntries;
            }
            usedBytes += Math.max(0L, base.getUsedCacheSize());
            maxBytes += Math.max(0L, base.getMaxCacheSize());
        }

        return new CacheSnapshot(
                telemetry ? hits : -1L,
                telemetry ? misses : -1L,
                telemetry ? evictions : -1L,
                telemetry ? entries : -1L,
                usedBytes,
                maxBytes);
    }

    private static void assertCacheBound(CacheSnapshot snapshot) {
        if (snapshot.maxBytes > 0L && snapshot.usedBytes > snapshot.maxBytes) {
            throw new IllegalStateException("Cache bound exceeded: "
                    + snapshot.usedBytes + " > " + snapshot.maxBytes);
        }
    }

    private static int[] parseSizes(String[] args) {
        List<Integer> parsed = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                addSizes(parsed, arg);
            }
        }
        if (parsed.isEmpty()) {
            addSizes(parsed, System.getProperty("kanger.perf.sizes", ""));
        }
        if (parsed.isEmpty()) {
            return DEFAULT_SIZES;
        }

        int[] result = new int[parsed.size()];
        for (int i = 0; i < parsed.size(); ++i) {
            result[i] = parsed.get(i);
        }
        return result;
    }

    private static void addSizes(List<Integer> parsed, String source) {
        if (source == null || source.trim().isEmpty()) {
            return;
        }
        for (String token : source.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            int size = Integer.parseInt(value);
            if (size <= 0) {
                throw new IllegalArgumentException("Performance size must be positive: " + value);
            }
            parsed.add(size);
        }
    }

    private static Path createTestHome() throws Exception {
        String configuredHome = System.getProperty("kanger.perf.home");
        if (configuredHome == null || configuredHome.trim().isEmpty()) {
            return Files.createTempDirectory("kanger-perf-home-");
        }
        Path path = Paths.get(configuredHome);
        Files.createDirectories(path);
        return path;
    }

    private static final class CacheSnapshot {
        private final long hits;
        private final long misses;
        private final long evictions;
        private final long entries;
        private final long usedBytes;
        private final long maxBytes;

        private CacheSnapshot(long hits,
                              long misses,
                              long evictions,
                              long entries,
                              long usedBytes,
                              long maxBytes) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.entries = entries;
            this.usedBytes = usedBytes;
            this.maxBytes = maxBytes;
        }
    }
}
