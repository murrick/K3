package org.kanger.test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.kanger.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

/** Uninstrumented complete query/optimization and original four-thread test body. */
public final class FreshNameWorkloadRunner {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean PROCESS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static final class Meter {
        long process, cpu, allocated, start;
        Meter() {
            process = PROCESS.getProcessCpuTime();
            cpu = THREADS.getCurrentThreadCpuTime();
            allocated = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId());
            start = System.nanoTime();
        }
        String stop(boolean includeMainAllocation) {
            long wall = System.nanoTime() - start;
            long main = THREADS.getCurrentThreadCpuTime() - cpu;
            long processNs = PROCESS.getProcessCpuTime() - process;
            long bytes = THREADS.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocated;
            check(wall > 0 && main >= 0 && processNs >= 0 && bytes >= 0);
            return "wall_ns=" + wall + " main_cpu_ns=" + main + " process_cpu_ns=" + processNs
                    + (includeMainAllocation ? " main_allocated_bytes=" + bytes : "");
        }
    }
    private static void son(int samples) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        String source = new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8);
        check(mind.compile(source)); // Setup is outside the measured interval.
        for (int sample = 0; sample < samples; sample++) {
            Meter meter = new Meter();
            Boolean result = mind.query("?$x son(John,x);");
            int raw = mind.getHypothesis().size();
            mind.optimizeHypothesis();
            String timing = meter.stop(true);
            check(result == null && raw == 18 && mind.getHypothesis().size() == 6);
            check(mind.getSolutions().size() == 0 && mind.getValues().size() == 0);
            List<String> finalRows = new ArrayList<>();
            for (IHypothesis h : mind.getHypothesis()) finalRows.add(((Hypothesis) h).toString(mind));
            Collections.sort(finalRows);
            LinkerStatistics stats = mind.getLinkerStatistics();
            System.out.println("SON_SAMPLE " + sample + " " + timing + " raw=18 optimized=6 solutions=0 values=0");
            System.out.println("FINAL " + finalRows);
            System.out.println("STATS passes=" + stats.getPasses() + " rules=" + stats.getRuleVisits()
                    + " rotations=" + stats.getTerminalRotations() + " pairs=" + stats.getDomainPairs()
                    + " unifications=" + stats.getUnificationAttempts());
        }
    }
    private static int integer(ITerm term) {
        double value = ((Number) term.getValue()).doubleValue();
        check(value == (int) value); return (int) value;
    }
    private static void set(int samples) throws Exception {
        Set<String> expected = new TreeSet<>();
        for (int x = 0; x < 164; x++) for (int group = 2; group <= 4; group++)
            expected.add(x + ":" + (group * 1000 + x));
        expected.add("3:1003");
        check(expected.size() == 493);
        for (int sample = 0; sample < samples; sample++) {
            IUser user = UserFactory.createUser("workload-" + sample, "workload-" + sample);
            new UDF().init(user); new DB().init(user);
            IMind input = new Mind(user).useStorage("data/workload-" + sample);
            KangerTest test = new KangerTest(input); test.setUp();
            Meter meter = new Meter();
            test.set_08_02(); // Original test body and stdout, no copied worker logic.
            String timing = meter.stop(false);
            IMind result = test.mind;
            check(Boolean.TRUE.equals(result.getQueryResult()));
            check(result.getSolutions().size() == 493 && result.getValues().size() == 493);
            check(result.getHypothesis().size() == 0);
            Set<String> actual = new TreeSet<>();
            for (Map<String, ITerm> row : result.getValues()) {
                check(row.size() == 2 && row.containsKey("x") && row.containsKey("y"));
                check(actual.add(integer(row.get("x")) + ":" + integer(row.get("y"))));
            }
            check(actual.equals(expected));
            System.out.println("SET_SAMPLE " + sample + " " + timing + " solutions=493 values=493 hypotheses=0");
            System.out.println("SET_ROWS " + actual);
            result.closeStorage(); // Cleanup is outside the measured interval.
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("fresh-name-workload-").toString());
        check(THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadAllocatedMemorySupported());
        THREADS.setThreadCpuTimeEnabled(true); THREADS.setThreadAllocatedMemoryEnabled(true);
        int samples = Integer.getInteger("bench.samples", 6); check(samples > 0);
        String workload = System.getProperty("bench.workload", "son");
        if ("son".equals(workload)) son(samples);
        else if ("set".equals(workload)) set(samples);
        else throw new IllegalArgumentException(workload);
    }
}
