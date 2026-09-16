/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Fresh fixtures, warmup excluded; compile/query timed separately, no rendering. */
public final class LatentSubstitutionBenchmarkRunner {
    private static final java.util.List<String> csv = new java.util.ArrayList<>();
    public static void main(String[] args) throws Exception {
        csv.clear();
        run();
        String output = System.getProperty("kanger.experiment.benchOutput");
        if (output != null) Files.write(Paths.get(output), csv, StandardCharsets.UTF_8);
    }

    private static void emit(String line) {
        csv.add(line);
        System.out.println(line);
    }

    private static void run() throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-bench-").toString());
        String natives = new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8);
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < 100; i++) facts.append("!value(").append(i).append(");\n");
        emit("fixture,sample,compile_ns,query_ns,rows,domain_pairs,unifications"
                + (Boolean.getBoolean("kanger.experiment.timeStages")
                ? ",selection_ns,linking_ns,functions_ns,database_ns,update_ns,resolved_ns,invocation_ns,pass_prepare_ns,rotator_setup_ns,rule_setup_ns,rotation_ns,callback_ns,validity_ns,solve_sync_ns" : "")
                + (Boolean.getBoolean("kanger.experiment.profileResolved")
                ? ",lookup_calls,layers,empty_signatures,resolution_ns,filter_ns,batch_ns,materialize_ns,selected_ids,batch_hits,batch_misses,ensure_ns,returned_ids" : "")
                + (Boolean.getBoolean("kanger.experiment.profileSolveSync")
                ? ",sync_calls,sync_scans,sync_skips,sync_fallbacks,group_visits,new_slots,sum_list_lengths,unchanged_groups,verify_groups,verify_slots,peak_groups,peak_tuples" : "")
                + (Boolean.getBoolean("kanger.experiment.benchFingerprint") ? ",semantic_sha256" : ""));
        if (Boolean.getBoolean("kanger.experiment.benchScaled")) {
            for (String sizeText : System.getProperty("kanger.experiment.benchEdgeSizes", "10,30").split(",")) {
                int size = Integer.parseInt(sizeText.trim());
                if (size <= 0) throw new IllegalArgumentException("Edge fixture size must be positive");
                StringBuilder source = new StringBuilder();
                for (int i = 0; i < size; i++) source.append("!edge(").append(i).append(',').append(i + 1).append(");\n");
                source.append("!@x @y edge(x,y) -> edge(y,x); !@x @y edge(x,y) -> path(x,y);");
                bench("symmetric-edges-" + size, source.toString(), "?$x $y path(x,y);");
            }
            return;
        }
        bench("natives-values", natives, "?$x male(x);");
        bench("singleton-values", facts.toString(), "?$x value(x);");
    }

    private static void bench(String label, String source, String query) throws Exception {
        for (int i = -Integer.getInteger("kanger.experiment.benchWarmups", 3);
                i < Integer.getInteger("kanger.experiment.benchSamples", 7); i++) {
            User user = new User();
            new UDF().init(user);
            Mind mind = new Mind(user);
            boolean trace = Boolean.getBoolean("kanger.experiment.traceInvocations");
            if (trace) System.err.println("BENCH_COMPILE " + label + " " + i);
            long start = System.nanoTime();
            if (!mind.compile(source)) throw new AssertionError("compile " + label);
            long compiled = System.nanoTime();
            if (trace) System.err.println("BENCH_QUERY " + label + " " + i);
            if (!Boolean.TRUE.equals(mind.query(query, null, false))) throw new AssertionError("query " + label);
            long finished = System.nanoTime();
            if (trace) System.err.println("BENCH_END " + label + " " + i);
            if (i >= 0) {
                LinkerStatistics s = mind.getLinkerStatistics();
                String stages = "";
                if (Boolean.getBoolean("kanger.experiment.timeStages"))
                    for (long nanos : s.getStageNanos()) stages += "," + nanos;
                if (Boolean.getBoolean("kanger.experiment.profileResolved"))
                    for (long value : s.getResolvedProfile()) stages += "," + value;
                if (Boolean.getBoolean("kanger.experiment.profileSolveSync")) {
                    for (long value : s.getSolveSync()) stages += "," + value;
                    for (long value : s.getSolveSyncWork()) stages += "," + value;
                }
                if (Boolean.getBoolean("kanger.experiment.benchFingerprint")) stages += "," + fingerprint(mind);
                emit(label + "," + i + "," + (compiled - start) + "," + (finished - compiled)
                        + "," + mind.getValues().size() + "," + s.getDomainPairs() + "," + s.getUnificationAttempts() + stages);
            }
        }
        System.out.flush();
        if (System.out.checkError()) throw new IllegalStateException("Benchmark stdout failed: " + label);
        if (Boolean.getBoolean("kanger.experiment.benchFingerprint"))
            System.err.println("BENCH_COMPLETE " + label);
    }

    /** Semantic projection outside the measured query interval; same categories as state runner. */
    private static String fingerprint(Mind mind) throws Exception {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (java.util.Map<String, org.kanger.interfaces.ITerm> row : mind.getValues()) {
            java.util.Map<String, String> sorted = new java.util.TreeMap<>();
            for (java.util.Map.Entry<String, org.kanger.interfaces.ITerm> e : row.entrySet())
                sorted.put(e.getKey(), e.getValue().toString());
            lines.add("value:" + sorted);
        }
        for (org.kanger.interfaces.IRule r : mind.getSolutions())
            lines.add("solution:" + ((org.kanger.units.Rule) r).toString(mind));
        for (org.kanger.interfaces.IHypothesis h : mind.getHypothesis())
            lines.add("hypothesis:" + ((org.kanger.primitives.Hypothesis) h).toString(mind));
        for (org.kanger.interfaces.IRule r : mind.getRules()) if (!r.isDeleted(mind))
            lines.add("rule:" + r.isGenerated() + ":" + ((org.kanger.units.Rule) r).toString(mind));
        java.util.Collections.sort(lines);
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (String line : lines) digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }
}
