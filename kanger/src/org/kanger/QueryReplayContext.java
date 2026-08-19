/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.interfaces.ITerm;

import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;

/**
 * Query-local replay data required by semantic hypothesis optimization.
 *
 * <p>The public {@link org.kanger.interfaces.IMind#optimizeHypothesis()} API is
 * intentionally argument-free, while relevance is defined against the exact
 * query that produced the current hypothesis list. The compiler is the first
 * boundary at which external parameters are already canonical KANGER terms, so
 * it records the TRUE-pass query here before those values are consumed.</p>
 *
 * <p>Entries are weakly keyed by Mind and replaced for every query compile.
 * A FALSE-pass/insertion compile clears an older entry; the following TRUE-pass
 * then installs the original {@code ?} query. This prevents a stale query from
 * being reused when no TRUE-pass follows.</p>
 */
public final class QueryReplayContext {

    private static final Map<Mind, Snapshot> SNAPSHOTS =
            new WeakHashMap<Mind, Snapshot>();

    private QueryReplayContext() {
    }

    public static synchronized void observeCompilation(Mind mind,
                                                       String source,
                                                       boolean query,
                                                       Queue<ITerm> externals)
            throws Exception {
        if (!query) {
            return;
        }
        if (source == null || source.isEmpty()
                || source.charAt(0) != Enums.SUC) {
            SNAPSHOTS.remove(mind);
            return;
        }

        Object[] values;
        if (externals == null || externals.isEmpty()) {
            values = null;
        } else {
            values = new Object[externals.size()];
            int index = 0;
            for (ITerm term : externals) {
                values[index++] = term.getValue();
            }
        }
        SNAPSHOTS.put(mind, new Snapshot(source, values));
    }

    public static synchronized Snapshot snapshot(Mind mind) {
        Snapshot snapshot = SNAPSHOTS.get(mind);
        return snapshot == null ? null : snapshot.copy();
    }

    public static final class Snapshot {
        private final String source;
        private final Object[] externals;

        private Snapshot(String source, Object[] externals) {
            this.source = source;
            this.externals = externals == null ? null : externals.clone();
        }

        private Snapshot copy() {
            return new Snapshot(source, externals);
        }

        public String getSource() {
            return source;
        }

        public Object[] getExternals() {
            return externals == null ? null : externals.clone();
        }
    }
}
