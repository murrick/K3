package org.kanger;

import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * P5a observational runner. It classifies existing Linker counters by pass and
 * rule origin without changing inference semantics or execution order.
 */
public final class KangerSemanticProfileRunner {

    private KangerSemanticProfileRunner() {
    }

    public static void main(String[] args) {
        try {
            Path home = Files.createTempDirectory("kanger-semantic-profile-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            int[] sizes = parseSizes(args);

            System.out.println(SemanticOperationReport.csvHeader());
            for (int size : sizes) {
                runCase(size);
            }
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runCase(int size) throws Exception {
        String suffix = size + "-" + System.nanoTime();
        User user = (User) UserFactory.createUser(
                "semantic-" + suffix, "semantic-" + suffix);
        new UDF().init(user);
        new DB().init(user);

        Mind mind = (Mind) new Mind(user).clearWorkspace();
        for (int i = 1; i <= size; ++i) {
            Boolean result = mind.query(
                    "!value(" + i + "," + (1000 + i) + ",7);",
                    null,
                    false);
            if (!Boolean.TRUE.equals(result)) {
                throw new IllegalStateException("Insert failed at row " + i);
            }
        }

        int key = Math.max(1, size / 2);
        runQuery(mind, size, "query-exact",
                "?value(" + key + "," + (1000 + key) + ",7);");
        runQuery(mind, size, "query-two-constants",
                "?$z value(" + key + "," + (1000 + key) + ",z);");
        runQuery(mind, size, "query-one-constant",
                "?$y $z value(" + key + ",y,z);");
        runQuery(mind, size, "query-all-variables",
                "?$x $y $z value(x,y,z);");
    }

    private static void runQuery(Mind mind,
                                 int size,
                                 String operation,
                                 String query) throws Exception {
        Boolean result = mind.query(query, null, false);
        if (!Boolean.TRUE.equals(result)) {
            throw new IllegalStateException("Query failed: " + query);
        }
        SemanticOperationReport report =
                SemanticOperationReport.from(mind.getLinkerStatistics());
        System.out.println(report.toCsvRow(
                size, operation, mind.getValues().size()));
    }

    private static int[] parseSizes(String[] args) {
        List<Integer> values = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                addSizes(values, arg);
            }
        }
        if (values.isEmpty()) {
            addSizes(values, System.getProperty(
                    "kanger.semantic.profile.sizes", "100,500,1000"));
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static void addSizes(List<Integer> values, String source) {
        if (source == null || source.trim().isEmpty()) {
            return;
        }
        for (String token : source.split(",")) {
            int value = Integer.parseInt(token.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "Profile size must be positive: " + value);
            }
            values.add(value);
        }
    }
}
