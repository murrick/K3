/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.primitives.TVariableSet;
import org.kanger.units.*;

/** Isolated sync cost: not an end-to-end inference benchmark. */
public final class LatentSolveSyncShapeRunner {
    private static Method sync, candidates;
    private static final List<String> output = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("solve-shapes-").toString());
        sync = Linker.class.getDeclaredMethod("synchronizeSolveIndex");
        sync.setAccessible(true);
        candidates = Linker.class.getDeclaredMethod("getSolveCandidates", TVariableSet.class, TVariable.class, TValue.class);
        candidates.setAccessible(true);
        output.add("groups,tuples_per_group,mode,sample,sync_ns,calls,scans,skips,group_visits,new_slots,checked_tuples");
        int warmups = Integer.getInteger("kanger.experiment.benchWarmups", 3);
        int samples = Integer.getInteger("kanger.experiment.benchSamples", 7);
        boolean reverse = Boolean.getBoolean("kanger.experiment.benchReverseShapes");
        int[] groupSizes = reverse ? new int[]{128, 32, 4} : new int[]{4, 32, 128};
        int[] tupleSizes = reverse ? new int[]{16, 1} : new int[]{1, 16};
        for (int groups : groupSizes) for (int tuples : tupleSizes) {
            for (int sample = -warmups; sample < samples; sample++) {
                boolean first = (sample & 1) != 0;
                run(groups, tuples, first, sample);
                run(groups, tuples, !first, sample);
            }
        }
        Files.write(Paths.get(args[0]), output, StandardCharsets.UTF_8);
        System.out.println("LATENT_SOLVE_SHAPES_PASS rows=" + (output.size() - 1));
    }

    private static void run(int groups, int tuples, boolean enabled, int sample) throws Exception {
        System.setProperty("kanger.experiment.versionedSolveSync", Boolean.toString(enabled));
        Mind mind = new Mind(new User());
        Linker linker = new Linker(mind);
        List<TVariable[]> variables = new ArrayList<>();
        List<TSolve> expected = new ArrayList<>();
        for (int group = 0; group < groups; group++) {
            Rule rule = new Rule(mind);
            TVariable[] pair = {
                mind.getTVars().createTVar(rule, mind.getTerms().add("x" + group)),
                mind.getTVars().createTVar(rule, mind.getTerms().add("y" + group))
            };
            variables.add(pair);
            for (int i = 0; i < tuples; i++) expected.add(append(mind, pair, i));
        }
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) sync.invoke(linker);
        long elapsed = System.nanoTime() - start;
        check(mind, linker, expected);
        // Publish a new relation in EVERY existing group between stable periods.
        for (TVariable[] pair : variables) expected.add(append(mind, pair, tuples));
        start = System.nanoTime();
        for (int i = 0; i < 500; i++) sync.invoke(linker);
        elapsed += System.nanoTime() - start;
        check(mind, linker, expected);
        long[] counts = linker.snapshotStatistics().getSolveSync();
        long[] work = linker.snapshotStatistics().getSolveSyncWork();
        if (counts[0] != 1000 || counts[1] != (enabled ? 2 : 1000)
                || counts[2] != (enabled ? 998 : 0) || counts[3] != 0)
            throw new AssertionError("Unexpected sync scheduling " + Arrays.toString(counts));
        if (Boolean.getBoolean("kanger.experiment.profileSolveSync")) {
            if (work[0] != (long) groups * (enabled ? 2 : 1000)
                    || work[1] != (long) groups * (tuples + 1))
                throw new AssertionError("Unexpected group/tuple work " + Arrays.toString(work));
        }
        if (sample >= 0) output.add(groups + "," + tuples + "," + enabled + "," + sample + "," + elapsed
                + "," + counts[0] + "," + counts[1] + "," + counts[2] + "," + work[0] + "," + work[1] + "," + expected.size());
    }

    private static TSolve append(Mind mind, TVariable[] pair, int value) throws Exception {
        List<TValue> tuple = new ArrayList<>();
        for (TVariable variable : pair) tuple.add(mind.getTValues().add(variable, mind.getTerms().add(value)));
        return mind.addTSolve(tuple);
    }

    private static void check(Mind mind, Linker linker, List<TSolve> expected) throws Exception {
        for (TSolve solve : expected) {
            TVariableSet key = new TVariableSet(solve, mind);
            for (TValue value : solve.getSolve()) {
                List<?> found = (List<?>) candidates.invoke(linker, key, value.getTVar(mind), value);
                if (found.size() != 1 || found.get(0) != solve)
                    throw new AssertionError("Tuple candidate identity/order mismatch");
            }
        }
    }
}
