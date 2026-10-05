package org.kanger;

import java.util.*;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.*;
import org.kanger.units.TVariable;

/** Scoped main-thread attribution; all original calls are still performed. */
public final class DeletionCallerCounters {
    private static final UnitType[] TYPES = UnitType.values();
    private static final String[] ROLES = {"OTHER", "BINDING", "STAMP"};
    private static final String[] PHASES = {"OUTSIDE", "FIRST", "SECOND"};
    private static Thread target;
    private static Thread tracingThread;
    private static long exposedMinds;
    private static long exposureGetters;
    private static int role, phase;
    private static long[][][] totals, depths;
    private static long[][] edges;
    private static long[] collectors;
    private static long[][] unexposedCalls, unexposedLevels;
    private static int index(UnitType type) { return type == null ? TYPES.length : type.ordinal(); }
    public static void startExposureTracking() {
        tracingThread = Thread.currentThread();
        exposedMinds = 0;
        exposureGetters = 0;
    }
    public static boolean expose(boolean previouslyExposed) {
        if (tracingThread != Thread.currentThread()) return previouslyExposed;
        if (!previouslyExposed) exposedMinds++;
        exposureGetters++;
        return true;
    }
    public static void begin() {
        totals = new long[9][TYPES.length + 1][8];
        depths = new long[9][TYPES.length + 1][64];
        edges = new long[9][4];
        collectors = new long[3];
        unexposedCalls = new long[9][TYPES.length + 1];
        unexposedLevels = new long[9][TYPES.length + 1];
        role = phase = 0;
        target = Thread.currentThread();
    }
    public static List<TVariable> collect(ArgumentsList arguments, IMind mind, int kind) throws Exception {
        if (target != Thread.currentThread()) return arguments.getTVariables(mind);
        int previous = role;
        role = kind;
        collectors[kind]++;
        try { return arguments.getTVariables(mind); }
        finally { role = previous; }
    }
    public static boolean argumentDeleted(IArgument argument, IMind mind, int kind) {
        if (target != Thread.currentThread()) return argument.isDeleted(mind);
        int previous = phase;
        phase = kind;
        try { return argument.isDeleted(mind); }
        finally { phase = previous; }
    }
    public static void argumentBoundary(Argument argument, IUnit held, IMind mind) {
        if (target != Thread.currentThread()) return;
        long[] row = edges[role * 3 + phase];
        row[0]++;
        boolean builtinArgument = argument.getClass() == Argument.class;
        boolean builtinVariable = held != null && held.getClass() == TVariable.class;
        if (builtinArgument) row[1]++;
        if (builtinArgument && builtinVariable) row[2]++;
        if (builtinArgument && builtinVariable && mind != null && mind.getClass() == Mind.class) row[3]++;
    }
    public static boolean restoredProbe(UnitType type, boolean present, boolean wasExposed) {
        if (target != Thread.currentThread()) return false;
        int group = role * 3 + phase, i = index(type);
        long[] row = totals[group][i];
        row[1]++;
        if (present) row[2]++;
        if (!wasExposed) unexposedLevels[group][i]++;
        return wasExposed;
    }
    public static void deletedProbe(UnitType type, boolean present) {
        if (target != Thread.currentThread()) return;
        long[] row = totals[role * 3 + phase][index(type)];
        row[3]++;
        if (present) row[4]++;
    }
    public static void complete(UnitType type, int depth, int outcome, boolean neverExposed) {
        if (target != Thread.currentThread()) return;
        int group = role * 3 + phase, i = index(type);
        totals[group][i][0]++;
        totals[group][i][5 + outcome]++;
        depths[group][i][Math.min(depth, 63)]++;
        if (neverExposed) unexposedCalls[group][i]++;
    }
    public static void finish(int sample) {
        if (role != 0 || phase != 0) throw new AssertionError("unbalanced attribution scope");
        target = null;
        System.out.println("DELETION_COLLECTORS sample=" + sample + " values=" + Arrays.toString(collectors));
        System.out.println("DELETION_EXPOSURE sample=" + sample + " minds=" + exposedMinds + " getters=" + exposureGetters);
        for (int group = 0; group < 9; group++) {
            String scope = "sample=" + sample + " role=" + ROLES[group / 3] + " phase=" + PHASES[group % 3];
            if (edges[group][0] != 0) System.out.println("DELETION_EDGES " + scope + " values=" + Arrays.toString(edges[group]));
            for (int i = 0; i < TYPES.length + 1; i++) {
                if (totals[group][i][0] == 0 && totals[group][i][1] == 0) continue;
                System.out.println("DELETION_CALLER " + scope + " type=" + (i < TYPES.length ? TYPES[i].name() : "NULL")
                    + " totals=" + Arrays.toString(totals[group][i]) + " depths=" + Arrays.toString(depths[group][i])
                    + " never_exposed=" + Arrays.toString(new long[]{unexposedCalls[group][i], unexposedLevels[group][i]}));
            }
        }
        totals = depths = null;
        edges = null;
        collectors = null;
    }
}
