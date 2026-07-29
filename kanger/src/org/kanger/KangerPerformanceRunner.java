/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Black-box performance characterization for the large homogeneous predicate
 * workload that exposed the current all-pairs Linker behaviour.
 *
 * <p>The runner deliberately records observations instead of enforcing timing
 * thresholds. It can exercise either the in-memory workspace or the currently
 * configured persistent storage backend. Persistent data remains behind the
 * normal KANGER storage/cache API; this class never preloads semantic objects
 * or assumes a particular database engine.</p>
 *
 * <p>Storage mode closes and reopens the database after loading the facts, so
 * all query measurements exercise index reconstruction and normal on-demand
 * hydration rather than the insertion-time object graph.</p>
 *
 * <p>Examples:</p>
 * <pre>
 *   java -cp target/classes:lib/*:kanger-server/lib/* \
 *        org.kanger.KangerPerformanceRunner
 *
 *   java -Dkanger.perf.storage=true \
 *        -Dkanger.perf.sizes=100,500,1000 \
 *        -cp target/classes:lib/*:kanger-server/lib/* \
 *        org.kanger.KangerPerformanceRunner
 * </pre>
 */
public final class KangerPerformanceRunner {

    private static final int[] DEFAULT_SIZES = new int[]{100, 500, 1000};

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
            System.out.println("mode,size,operation,millis,result,rows,solutions,rules,domains,tvalues");

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
        IUser user = UserFactory.createUser("perf-" + suffix, "perf-" + suffix);
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
            report(mind, storage, size, "insert-sequential", started, Boolean.TRUE);

            if (storage) {
                long reopenStarted = System.nanoTime();
                mind = (Mind) mind.closeStorage();
                mind = (Mind) mind.useStorage(storageName);
                report(mind, true, size, "reopen-storage", reopenStarted, Boolean.TRUE);
            }

            int key = Math.max(1, size / 2);
            measureQuery(mind, storage, size, "query-exact",
                    "?value(" + key + "," + (1000 + key) + ",7);");
            measureQuery(mind, storage, size, "query-two-constants",
                    "?$z value(" + key + "," + (1000 + key) + ",z);");
            measureQuery(mind, storage, size, "query-one-constant",
                    "?$y $z value(" + key + ",y,z);");
            measureQuery(mind, storage, size, "query-all-variables",
                    "?$x $y $z value(x,y,z);");
        } finally {
            if (storage && mind.isStorageUsed()) {
                mind.removeStorage(null);
            }
        }
    }

    private static void measureQuery(Mind mind,
                                     boolean storage,
                                     int size,
                                     String operation,
                                     String query) throws Exception {
        long started = System.nanoTime();
        Boolean result = mind.query(query, null, false);
        report(mind, storage, size, operation, started, result);
    }

    private static void report(Mind mind,
                               boolean storage,
                               int size,
                               String operation,
                               long started,
                               Boolean result) throws Exception {
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf(
                "%s,%d,%s,%.3f,%s,%d,%d,%d,%d,%d%n",
                storage ? "storage" : "memory",
                size,
                operation,
                millis,
                String.valueOf(result),
                mind.getValues().size(),
                mind.getSolutions().size(),
                mind.getRules().size(),
                mind.getDomains().size(),
                mind.getTValues().size());
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
}
