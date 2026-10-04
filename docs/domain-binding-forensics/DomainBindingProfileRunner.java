package org.kanger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.IHypothesis;
import org.kanger.primitives.Hypothesis;
import org.kanger.udf.UDF;

/** Diagnostic runner: normal logged query, separate hypothesis optimization. */
public final class DomainBindingProfileRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("son-profile-").toString());
        User user = new User();
        new UDF().init(user);
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        String source = new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8);
        long start = System.nanoTime();
        if (!mind.compile(source)) throw new AssertionError("Compilation rejected");
        System.out.println("COMPILE_NS " + (System.nanoTime() - start));
        com.sun.management.ThreadMXBean allocation = null;
        if (Boolean.getBoolean("bench.allocation")) {
            java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean) {
                allocation = (com.sun.management.ThreadMXBean) bean;
                if (!allocation.isThreadAllocatedMemorySupported()) allocation = null;
                else allocation.setThreadAllocatedMemoryEnabled(true);
            }
        }
        int samples = Integer.getInteger("bench.samples", 4);
        for (int i = 0; i < samples; i++) {
            Sampler sampler = Boolean.getBoolean("bench.sample") ? new Sampler(Thread.currentThread()) : null;
            if (sampler != null) sampler.start();
            start = System.nanoTime();
            Boolean result;
            try { result = mind.query("?$x son(John,x);"); }
            finally { if (sampler != null) sampler.running = false; }
            long queryNs = System.nanoTime() - start;
            if (sampler != null) { sampler.join(); sampler.report(i, "query"); }
            List<String> raw = texts(mind);
            sampler = Boolean.getBoolean("bench.sample") ? new Sampler(Thread.currentThread()) : null;
            if (sampler != null) sampler.start();
            start = System.nanoTime();
            java.lang.management.ThreadMXBean cpu = java.lang.management.ManagementFactory.getThreadMXBean();
            long cpuBefore = cpu.isCurrentThreadCpuTimeSupported() ? cpu.getCurrentThreadCpuTime() : -1;
            long allocatedBefore = allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
            DomainBindingProfile.reset();
            try { mind.optimizeHypothesis(); }
            finally { if (sampler != null) sampler.running = false; }
            long optimizeNs = System.nanoTime() - start;
            long cpuNs = cpuBefore < 0 ? -1 : cpu.getCurrentThreadCpuTime() - cpuBefore;
            if (sampler != null) { sampler.join(); sampler.report(i, "optimize"); }
            long allocatedBytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedBefore;
            DomainBindingProfile.report(i);
            List<String> optimized = texts(mind);
            if (result != null || optimized.size() != 6) throw new AssertionError("Unexpected result");
            System.out.println("SAMPLE " + i + " query_ns=" + queryNs + " optimize_ns=" + optimizeNs
                    + " optimize_cpu_ns=" + cpuNs + " optimize_allocated_bytes=" + allocatedBytes + " raw=" + raw.size() + " optimized=" + optimized.size()
                    + " solutions=" + mind.getSolutions().size() + " values=" + mind.getValues().size());
            System.out.println("RAW " + raw);
            System.out.println("OPTIMIZED " + optimized);
            LinkerStatistics s = mind.getLinkerStatistics();
            System.out.println("POST_OPTIMIZE_LAST_LINKER_STATS passes=" + s.getPasses() + " rules=" + s.getRuleVisits()
                    + " rotations=" + s.getTerminalRotations() + " pairs=" + s.getDomainPairs()
                    + " unifications=" + s.getUnificationAttempts());
        }
    }

    private static List<String> texts(Mind mind) throws Exception {
        List<String> rows = new ArrayList<>();
        for (IHypothesis h : mind.getHypothesis()) rows.add(((Hypothesis) h).toString(mind));
        Collections.sort(rows);
        return rows;
    }

    /** Main-thread wall-stack observations, not CPU percentages. */
    private static final class Sampler extends Thread {
        final Thread target;
        volatile boolean running = true;
        final Map<String, Integer> counts = new HashMap<>();
        int observations;
        Sampler(Thread target) { this.target = target; setDaemon(true); }
        public void run() {
            while (running) {
                StackTraceElement[] stack = target.getStackTrace();
                observations++;
                Set<String> frames = new HashSet<>();
                for (StackTraceElement f : stack) {
                    frames.add(f.getClassName() + "." + f.getMethodName());
                    if (f.getClassName().equals("org.kanger.stores.HypothesisStore"))
                        frames.add("CALLSITE " + f.toString());
                }
                for (String f : frames) counts.put(f, counts.getOrDefault(f, 0) + 1);
                if (stack.length > 0) {
                    String leaf = "LEAF " + stack[0].getClassName() + "." + stack[0].getMethodName();
                    counts.put(leaf, counts.getOrDefault(leaf, 0) + 1);
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { return; }
            }
        }
        void report(int sample, String phase) {
            System.err.println("SAMPLE " + sample + " PHASE " + phase + " OBSERVATIONS " + observations);
            counts.entrySet().stream().sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> System.err.println(e.getValue() + " " + e.getKey()));
        }
    }
}
