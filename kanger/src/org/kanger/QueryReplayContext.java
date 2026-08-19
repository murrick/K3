/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
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
 * <p>Compilation occurs in nested technical Mind transactions below the Mind
 * on which the user issued the query. Therefore every observation is published
 * to the active Mind lineage. A FALSE-pass compile clears stale replay data;
 * the following TRUE-pass installs the original {@code ?} query on every
 * ancestor that can later own the resulting hypothesis store.</p>
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
            publish(mind, null);
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
        publish(mind, new Snapshot(source, values));
    }

    private static void publish(Mind mind, Snapshot snapshot) {
        for (IMind level = mind; level != null; level = level.getNext()) {
            Mind current = (Mind) level;
            if (snapshot == null) {
                SNAPSHOTS.remove(current);
            } else {
                SNAPSHOTS.put(current, snapshot.copy());
            }
        }
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
