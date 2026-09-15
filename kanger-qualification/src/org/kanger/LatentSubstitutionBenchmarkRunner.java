/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Fresh fixtures, warmup excluded; compile/query timed separately, no rendering. */
public final class LatentSubstitutionBenchmarkRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-bench-").toString());
        String natives = new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8);
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < 100; i++) facts.append("!value(").append(i).append(");\n");
        System.out.println("fixture,sample,compile_ns,query_ns,rows,domain_pairs,unifications");
        bench("natives-values", natives, "?$x male(x);");
        bench("singleton-values", facts.toString(), "?$x value(x);");
    }

    private static void bench(String label, String source, String query) throws Exception {
        for (int i = -3; i < 7; i++) {
            User user = new User();
            new UDF().init(user);
            Mind mind = new Mind(user);
            long start = System.nanoTime();
            if (!mind.compile(source)) throw new AssertionError("compile " + label);
            long compiled = System.nanoTime();
            if (!Boolean.TRUE.equals(mind.query(query, null, false))) throw new AssertionError("query " + label);
            long finished = System.nanoTime();
            if (i >= 0) {
                LinkerStatistics s = mind.getLinkerStatistics();
                System.out.println(label + "," + i + "," + (compiled - start) + "," + (finished - compiled)
                        + "," + mind.getValues().size() + "," + s.getDomainPairs() + "," + s.getUnificationAttempts());
            }
        }
    }
}
