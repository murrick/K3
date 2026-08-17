/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Storage-independent snapshot of the explicit user transaction stack.
 *
 * <p>Each explicit U-level is captured from its own authorial state rather than
 * inferred from the effective visible difference to its parent. Storage-local
 * ids and derived runtime structures are intentionally excluded; unresolved
 * relative state is retained as portable residue on the rebuilt level.</p>
 */
final class UserTransactionStackSnapshot {

    private final PortableMindLayer rootLevel;
    private final List<PortableMindLayer> levels;

    private UserTransactionStackSnapshot(PortableMindLayer rootLevel,
                                         List<PortableMindLayer> levels) {
        this.rootLevel = rootLevel;
        this.levels = Collections.unmodifiableList(levels);
    }

    static UserTransactionStackSnapshot capture(Mind top) throws Exception {
        if (top == null) {
            throw new IllegalArgumentException("Transaction stack requires a current Mind");
        }
        List<Mind> lineage = lineage(top);
        List<PortableMindLayer> states = new ArrayList<>();
        for (int i = 1; i < lineage.size(); ++i) {
            states.add(PortableMindLayer.capture(lineage.get(i)));
        }
        return new UserTransactionStackSnapshot(null, states);
    }

    static UserTransactionStackSnapshot captureOffline(Mind top) throws Exception {
        if (top == null) {
            throw new IllegalArgumentException("Transaction stack requires a current Mind");
        }
        List<Mind> lineage = lineage(top);
        PortableMindLayer root = PortableMindLayer.captureRoot(lineage.get(0));
        List<PortableMindLayer> states = new ArrayList<>();
        for (int i = 1; i < lineage.size(); ++i) {
            states.add(PortableMindLayer.capture(lineage.get(i)));
        }
        return new UserTransactionStackSnapshot(root, states);
    }

    private static List<Mind> lineage(Mind top) {
        List<Mind> lineage = new ArrayList<>();
        for (Mind current = top; current != null; current = (Mind) current.getNext()) {
            lineage.add(current);
        }
        Collections.reverse(lineage);
        return lineage;
    }

    int depth() {
        return levels.size();
    }

    Mind replay(Mind root) throws Exception {
        return replayLevels(root);
    }

    Mind replayOverBaseline(Mind root) throws Exception {
        if (rootLevel != null && !rootLevel.isEmpty()) {
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(root)) {
                Mind work = tx.mind();
                rootLevel.apply(root, work);
                if (!tx.commit()) {
                    throw new IllegalStateException(
                            "Offline U0 cannot be assimilated into the target storage baseline");
                }
            }
        }
        return replayLevels(root);
    }

    Mind restoreOffline(Mind root) throws Exception {
        if (rootLevel != null && !rootLevel.isEmpty()) {
            rootLevel.apply(root, root);
            root.setPortableRebaseResidue(PortableMindLayer.empty());
        }
        return replayLevels(root);
    }

    private Mind replayLevels(Mind root) throws Exception {
        Mind current = root;
        try {
            for (PortableMindLayer state : levels) {
                Mind child = new Mind(current);
                boolean applied = false;
                try {
                    state.apply(current, child);
                    applied = true;
                } finally {
                    if (!applied) {
                        current.release(child);
                    }
                }
                current = child;
            }
            return current;
        } catch (Throwable failure) {
            Throwable propagated = failure;
            try {
                rollbackToRoot(current);
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    propagated.addSuppressed(cleanupFailure);
                }
            }
            rethrow(propagated);
            throw new AssertionError("unreachable");
        }
    }

    static Mind rollbackToRoot(Mind top) throws Exception {
        Mind current = top;
        while (current.getNext() != null) {
            Mind parent = (Mind) current.getNext();
            parent.release(current);
            current = parent;
        }
        return current;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }
}
