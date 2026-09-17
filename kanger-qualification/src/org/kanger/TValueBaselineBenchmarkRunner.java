/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Fresh fixtures, warmup excluded; compile/query timed separately, no rendering. */
public final class TValueBaselineBenchmarkRunner {
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
        emit("fixture,sample,compile_ns,query_ns,rows,domain_pairs,unifications,semantic_sha256");
        if (Boolean.getBoolean("kanger.experiment.benchScaled")) {
            int partitions = Integer.getInteger("kanger.experiment.benchPartitions", 1);
            if (partitions <= 0) throw new IllegalArgumentException("Partitions must be positive");
            for (String sizeText : System.getProperty("kanger.experiment.benchEdgeSizes", "10,30").split(",")) {
                int size = Integer.parseInt(sizeText.trim());
                if (size <= 0) throw new IllegalArgumentException("Edge fixture size must be positive");
                StringBuilder source = new StringBuilder();
                for (int i = 0; i < size; i++) {
                    String predicate = partitions == 1 ? "edge" : "edge" + (i % partitions);
                    source.append('!').append(predicate).append('(').append(i).append(',').append(i + 1).append(");\n");
                }
                for (int p = 0; p < partitions; p++) {
                    String predicate = partitions == 1 ? "edge" : "edge" + p;
                    source.append("!@x @y ").append(predicate).append("(x,y) -> ")
                            .append(predicate).append("(y,x); !@x @y ").append(predicate).append("(x,y) -> path(x,y);");
                }
                bench("symmetric-edges-" + size + (partitions == 1 ? "" : "-partitions-" + partitions),
                        source.toString(), "?$x $y path(x,y);");
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
            StackSampler sampler = Boolean.getBoolean("kanger.experiment.sampleQuery")
                    ? new StackSampler(Thread.currentThread()) : null;
            if (sampler != null) sampler.start();
            long[] scansBefore = Boolean.getBoolean("kanger.experiment.profileSolveScans")
                    ? Linker.experimentalSolveScanProfile() : null;
            long queryStarted = System.nanoTime();
            try {
                if (!Boolean.TRUE.equals(mind.query(query, null, false))) throw new AssertionError("query " + label);
            } finally {
                if (sampler != null) sampler.running = false;
            }
            long finished = System.nanoTime();
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.profileSolveScans")) {
                long[] scans = Linker.experimentalSolveScanProfile();
                for (int k = 0; k < scans.length; k++) scans[k] -= scansBefore[k];
                System.err.println("SOLVE_SCANS " + label + " " + i + " " + java.util.Arrays.toString(scans));
            }
            if (sampler != null) {
                sampler.join();
                if (i >= 0) sampler.report(label, i);
            }
            if (trace) System.err.println("BENCH_END " + label + " " + i);
            if (i >= 0) {
                LinkerStatistics s = mind.getLinkerStatistics();
                String stages = "," + fingerprint(mind);
                emit(label + "," + i + "," + (compiled - start) + "," + (finished - queryStarted)
                        + "," + mind.getValues().size() + "," + s.getDomainPairs() + "," + s.getUnificationAttempts() + stages);
            }
        }
        System.out.flush();
        if (System.out.checkError()) throw new IllegalStateException("Benchmark stdout failed: " + label);
        if (Boolean.getBoolean("kanger.experiment.benchFingerprint"))
            System.err.println("BENCH_COMPLETE " + label);
    }

    /** Semantic projection outside the measured query interval; same categories as state runner. */
    /** Approximate wall-time stack samples; never interpreted as CPU accounting. */
    private static final class StackSampler extends Thread {
        final Thread target;
        volatile boolean running = true;
        final java.util.Map<String, Integer> leaves = new java.util.TreeMap<>();
        final java.util.Map<String, Integer> inclusive = new java.util.TreeMap<>();
        int samples;
        StackSampler(Thread target) { super("query-stack-sampler"); this.target = target; setDaemon(true); }
        @Override public void run() {
            while (running) {
                StackTraceElement[] stack = target.getStackTrace();
                boolean inQuery = false;
                for (StackTraceElement frame : stack)
                    if (frame.getClassName().equals("org.kanger.Mind") && frame.getMethodName().equals("query"))
                        inQuery = true;
                if (inQuery) {
                    samples++;
                    String leaf = stack[0].getClassName() + "." + stack[0].getMethodName();
                    leaves.put(leaf, leaves.getOrDefault(leaf, 0) + 1);
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (StackTraceElement frame : stack) {
                        if (!frame.getClassName().startsWith("org.kanger.")) continue;
                        seen.add(frame.getClassName() + "." + frame.getMethodName());
                    }
                    for (String name : seen) inclusive.put(name, inclusive.getOrDefault(name, 0) + 1);
                }
                try { Thread.sleep(10); } catch (InterruptedException e) { return; }
            }
        }
        void report(String label, int sample) {
            System.err.println("STACK_TOTAL " + label + " " + sample + " " + samples);
            for (java.util.Map.Entry<String, Integer> e : inclusive.entrySet())
                System.err.println("STACK_INCLUSIVE " + label + " " + sample + " " + e.getKey() + " " + e.getValue());
            for (java.util.Map.Entry<String, Integer> e : leaves.entrySet())
                System.err.println("STACK_LEAF " + label + " " + sample + " " + e.getKey() + " " + e.getValue());
        }
    }

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
