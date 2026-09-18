package org.kanger.test;

import org.kanger.*;
import org.kanger.udf.UDF;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Measures the unmodified historical method, including result formatting/output. */
public final class Set0802ProfileRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("set0802-").toString());
        List<String> results = new ArrayList<>();
        results.add("sample,elapsed_ns,solutions,values,main_thread_reference_scans,last_passes,last_rule_visits,last_terminal_rotations,last_database_evaluations,last_domain_pairs,last_unifications");
        int warmups = Integer.getInteger("bench.warmups", 0);
        int samples = Integer.getInteger("bench.samples", 1);
        for (int i = -warmups; i < samples; ++i) {
            User user = new User();
            new UDF().init(user);
            KangerTest test = new KangerTest(new Mind(user));
            test.setUp();
            Sampler sampler = Boolean.getBoolean("bench.sample") ? new Sampler() : null;
            long before = Linker.experimentalSolveScanProfile()[0];
            long[] rotationsBefore = Linker.experimentalRotationProfile();
            long[] frontierBefore = Linker.experimentalFrontierProfile();
            long[] costBefore = Linker.experimentalFrontierCost();
            long[] causesBefore = org.kanger.units.CachedDomain.experimentalCauseProfile();
            long[] weightsBefore = org.kanger.units.CachedDomain.experimentalCauseWeightProfile();
            if (sampler != null) sampler.start();
            long start = System.nanoTime();
            try { test.set_08_02(); }
            finally { if (sampler != null) sampler.running = false; }
            long elapsed = System.nanoTime() - start;
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.shadowCauseWeights")) {
                long[] weights = org.kanger.units.CachedDomain.experimentalCauseWeightProfile();
                for (int j = 0; j < weights.length; ++j) weights[j] -= weightsBefore[j];
                System.err.println("MAIN_CAUSE_WEIGHTS " + Arrays.toString(weights));
            }
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.profileCauseMemo")) {
                long[] causes = org.kanger.units.CachedDomain.experimentalCauseProfile();
                for (int j = 0; j < causes.length; ++j) causes[j] -= causesBefore[j];
                System.err.println("MAIN_CAUSE_MEMO " + Arrays.toString(causes));
            }
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.profileFrontierCost")) {
                long[] cost = Linker.experimentalFrontierCost();
                for (int j = 0; j < cost.length; ++j) cost[j] -= costBefore[j];
                System.err.println("MAIN_FRONTIER_COST " + Arrays.toString(cost));
            }
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.shadowRotationFrontier")) {
                long[] frontier = Linker.experimentalFrontierProfile();
                for (int j = 0; j < frontier.length; ++j) frontier[j] -= frontierBefore[j];
                System.err.println("MAIN_FRONTIER " + Arrays.toString(frontier));
            }
            if (i >= 0 && Boolean.getBoolean("kanger.experiment.profileRotations")) {
                long[] rotations = Linker.experimentalRotationProfile();
                for (int j = 0; j < rotations.length; ++j) rotations[j] -= rotationsBefore[j];
                System.err.println("MAIN_ROTATIONS " + Arrays.toString(rotations));
            }
            if (sampler != null) { sampler.join(); if (i >= 0) sampler.report(); }
            LinkerStatistics stats = ((Mind) test.mind).getLinkerStatistics();
            if (i >= 0) results.add(i + "," + elapsed + "," + test.mind.getSolutions().size()
                    + "," + test.mind.getValues().size() + ","
                    + (Linker.experimentalSolveScanProfile()[0] - before)
                    + "," + stats.getPasses() + "," + stats.getRuleVisits()
                    + "," + stats.getTerminalRotations() + "," + stats.getDatabaseEvaluations()
                    + "," + stats.getDomainPairs() + "," + stats.getUnificationAttempts());
        }
        Files.write(Paths.get(args[0]), results, StandardCharsets.UTF_8);
    }

    /** Wall-stack observations across all scenario threads, not CPU attribution. */
    private static final class Sampler extends Thread {
        volatile boolean running = true;
        final Map<String, Integer> counts = new TreeMap<>();
        int observations;
        Sampler() { super("set0802-sampler"); setDaemon(true); }
        public void run() {
            while (running) {
                for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                    boolean scenario = false;
                    for (StackTraceElement f : e.getValue())
                        if (f.getClassName().equals(KangerTest.class.getName()) && f.getMethodName().equals("set_08_02")
                                || f.getClassName().startsWith(KangerTest.class.getName() + "$")) scenario = true;
                    if (!scenario) continue;
                    observations++;
                    String state = e.getKey().getState().name();
                    if (e.getValue().length > 0) {
                        StackTraceElement leaf = e.getValue()[0];
                        String key = "LEAF " + state + " " + leaf.getClassName() + "." + leaf.getMethodName();
                        counts.put(key, counts.getOrDefault(key, 0) + 1);
                    }
                    Set<String> frames = new HashSet<>();
                    for (StackTraceElement f : e.getValue()) frames.add(state + " " + f.getClassName() + "." + f.getMethodName());
                    for (String f : frames) counts.put(f, counts.getOrDefault(f, 0) + 1);
                }
                try { Thread.sleep(5); } catch (InterruptedException ex) { return; }
            }
        }
        void report() {
            System.err.println("THREAD_STACK_OBSERVATIONS " + observations);
            counts.entrySet().stream().sorted((a,b) -> Integer.compare(b.getValue(),a.getValue()))
                    .forEach(e -> System.err.println(e.getValue() + " " + e.getKey()));
        }
    }
}
