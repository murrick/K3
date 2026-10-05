package org.kanger;

import java.util.Arrays;
import org.kanger.enums.UnitType;

/** Main-thread counts only; no extra semantic getters or collection callbacks. */
public final class DeletionChainCounters {
    private static final UnitType[] TYPES = UnitType.values();
    private static Thread target;
    private static long[][] totals, depths;
    private static int maxDepth;
    private static int index(UnitType type) { return type == null ? TYPES.length : type.ordinal(); }
    public static void begin() {
        totals = new long[TYPES.length + 1][8];
        depths = new long[TYPES.length + 1][64];
        maxDepth = 0;
        target = Thread.currentThread();
    }
    public static void restoredProbe(UnitType type, boolean present) {
        if (target != Thread.currentThread()) return;
        long[] row = totals[index(type)];
        row[1]++;
        if (present) row[2]++;
    }
    public static void deletedProbe(UnitType type, boolean present) {
        if (target != Thread.currentThread()) return;
        long[] row = totals[index(type)];
        row[3]++;
        if (present) row[4]++;
    }
    public static void complete(UnitType type, int depth, int outcome) {
        if (target != Thread.currentThread()) return;
        int i = index(type);
        totals[i][0]++;
        totals[i][5 + outcome]++;
        depths[i][Math.min(depth, 63)]++;
        maxDepth = Math.max(maxDepth, depth);
    }
    public static void finish(int sample) {
        target = null;
        System.out.println("DELETION_MAX_DEPTH sample=" + sample + " value=" + maxDepth);
        for (int i = 0; i < totals.length; i++) {
            if (totals[i][0] == 0 && totals[i][1] == 0) continue;
            System.out.println("DELETION_CHAIN sample=" + sample + " type="
                + (i < TYPES.length ? TYPES[i].name() : "NULL")
                + " totals=" + Arrays.toString(totals[i]) + " depths=" + Arrays.toString(depths[i]));
        }
        totals = depths = null;
    }
}
