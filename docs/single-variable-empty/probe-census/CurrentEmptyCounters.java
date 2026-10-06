package org.kanger;

import java.util.Arrays;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TVariable;

/** Temporary main-thread counters; no retained semantic objects or memoized results. */
public final class CurrentEmptyCounters {
    private static Thread owner;
    private static int variableDepth, getDepth;
    private static boolean nativeVariableContext;
    // Rows: outside/inside TVariable.isEmpty x outside/inside factory.get.
    // Columns: containsKey calls, absent, present, exact factory/key,
    //          exact variable/Mind/factory/key, HashMap.get calls, null get result.
    private static final long[][] rows = new long[4][7];
    private static final long[] variables = new long[4]; // calls, null context, true, false

    private static boolean enabled() { return Thread.currentThread() == owner; }
    public static void begin() {
        for (long[] row : rows) Arrays.fill(row, 0);
        Arrays.fill(variables, 0);
        variableDepth = getDepth = 0;
        owner = Thread.currentThread();
    }
    public static int enterGet() {
        if (!enabled()) return -1;
        return getDepth++;
    }
    public static void leaveGet(int previous) { if (previous >= 0) getDepth = previous; }
    public static int enterVariable(TVariable variable, Mind active) {
        if (!enabled()) return -1;
        int previous = variableDepth;
        ++variableDepth;
        ++variables[0];
        if (active == null) ++variables[1];
        nativeVariableContext = variable.getClass() == TVariable.class
                && active != null && active.getClass() == Mind.class;
        return previous;
    }
    public static void leaveVariable(int previous, boolean previousNative) {
        if (previous >= 0) { variableDepth = previous; nativeVariableContext = previousNative; }
    }
    public static boolean nativeContext() { return enabled() && nativeVariableContext; }
    private static int row() { return (variableDepth > 0 ? 2 : 0) + (getDepth > 0 ? 1 : 0); }
    public static void contains(TValueFactory factory, TVariable key, boolean present) {
        if (!enabled()) return;
        long[] counters = rows[row()];
        ++counters[0]; ++counters[present ? 2 : 1];
        if (factory.getClass() == TValueFactory.class && (key == null || key.getClass() == TVariable.class)) {
            ++counters[3];
            if (variableDepth > 0 && nativeVariableContext) ++counters[4];
        }
    }
    public static void mapGet(boolean isNull) {
        if (!enabled()) return;
        long[] counters = rows[row()]; ++counters[5];
        if (isNull) ++counters[6];
    }
    public static void variableResult(boolean result) {
        if (enabled()) ++variables[result ? 2 : 3];
    }
    public static void finish(int sample) {
        if (!enabled() || variableDepth != 0 || getDepth != 0) throw new AssertionError("Unbalanced scopes");
        owner = null;
        System.out.println("EMPTY_VARIABLE " + sample + " " + Arrays.toString(variables));
        for (int i = 0; i < rows.length; ++i)
            System.out.println("EMPTY_ROW " + sample + " " + i + " " + Arrays.toString(rows[i]));
    }
}
