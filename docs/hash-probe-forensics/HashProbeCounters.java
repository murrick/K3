package org.kanger.storage;

import java.util.*;

/** Diagnostic only. Observe completed lookups; never change their result. */
public final class HashProbeCounters {
    private static final int[] SIZES = {1, 4, 16, 64, 256};
    private static final class Owner {
        final int[][] keys = new int[SIZES.length][];
        final boolean[][] present = new boolean[SIZES.length][];
        Owner() {
            for (int i = 0; i < SIZES.length; i++) {
                keys[i] = new int[SIZES[i]];
                present[i] = new boolean[SIZES[i]];
            }
        }
    }
    private static IdentityHashMap<Object, Owner> owners;
    private static Map<Integer, Long> frequencies;
    private static Thread target;
    private static long calls, small, empty, singleton, multiple;
    private static long[] hits;
    public static void begin() {
        owners = new IdentityHashMap<>();
        frequencies = new HashMap<>();
        calls = small = empty = singleton = multiple = 0;
        hits = new long[SIZES.length];
        target = Thread.currentThread();
    }
    public static void observe(Object owner, int hash, Set<Long> ids) {
        if (Thread.currentThread() != target) return;
        calls++;
        if (hash >= -128 && hash <= 127) small++;
        if (ids == null || ids.isEmpty()) empty++;
        else if (ids.size() == 1) singleton++;
        else multiple++;
        Long count = frequencies.get(hash);
        frequencies.put(hash, count == null ? 1L : count + 1);
        Owner state = owners.get(owner);
        if (state == null) { state = new Owner(); owners.put(owner, state); }
        // Keys alone are simulated, never borrowed values from the live index.
        int spread = hash ^ (hash >>> 16);
        for (int i = 0; i < SIZES.length; i++) {
            int slot = spread & (SIZES[i] - 1);
            if (state.present[i][slot] && state.keys[i][slot] == hash) {
                if (hash < -128 || hash > 127) hits[i]++;
            }
            state.present[i][slot] = true;
            state.keys[i][slot] = hash;
        }
    }
    public static void finish(int sample) {
        target = null;
        System.out.println("HASH_PROBES sample=" + sample + " calls=" + calls
            + " small=" + small + " empty=" + empty + " singleton=" + singleton
            + " multiple=" + multiple + " owners=" + owners.size()
            + " distinct=" + frequencies.size() + " hits=" + Arrays.toString(hits));
        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(frequencies.entrySet());
        Collections.sort(sorted, (a,b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Integer.compare(a.getKey(), b.getKey());
        });
        System.out.println("HASH_FREQUENCIES sample=" + sample + " values=" + sorted);
        owners = null;
        frequencies = null;
    }
}
