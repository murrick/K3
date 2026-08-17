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
                if (!Boolean.TRUE.equals(work.queryCheck(false))) {
                    throw new IllegalStateException(
                            "Offline U0 conflicts with target storage baseline");
                }
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
            if (!Boolean.TRUE.equals(current.queryCheck(false))) {
                throw new IllegalStateException(
                        "Replayed current context conflicts with storage baseline");
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

    /**
     * Collapses all explicit user transaction levels above U0 into exactly
     * one live U1 while leaving the root baseline untouched. The original
     * stack remains published until a fully replayed and qualified sibling
     * candidate has been reduced to one level.
     */
    static Mind squash(Mind top) throws Exception {
        if (top == null) {
            throw new IllegalArgumentException("Transaction squash requires a current Mind");
        }
        if (top.getTransactionLevel() <= 1) {
            return top;
        }

        requireExplicitSquashTopology(top);
        Mind root = (Mind) top.getTop();
        UserTransactionStackSnapshot snapshot = capture(top);
        Mind candidate = null;

        try {
            candidate = snapshot.replay(root);
            while (candidate.getNext() != root) {
                Mind parent = (Mind) candidate.getNext();
                if (!parent.commitUserTransaction(candidate)) {
                    throw new IllegalStateException(
                            "Transaction squash candidate commit was rejected");
                }
                candidate = parent;
            }

            if (candidate.getTransactionLevel() != 1
                    || !Boolean.TRUE.equals(candidate.queryCheck(false))) {
                throw new IllegalStateException(
                        "Transaction squash candidate is not a valid U1 context");
            }

            /*
             * The sibling candidate keeps one root reservation alive while
             * the old chain is discarded, so no root pack/update/flush can be
             * triggered by the intermediate releases.
             */
            rollbackToRoot(top);
            return candidate;
        } catch (Throwable failure) {
            Throwable propagated = failure;
            if (candidate != null) {
                try {
                    rollbackToRoot(candidate);
                } catch (Throwable cleanupFailure) {
                    if (cleanupFailure != failure) {
                        propagated.addSuppressed(cleanupFailure);
                    }
                }
            }
            rethrow(propagated);
            throw new AssertionError("unreachable");
        }
    }

    private static void requireExplicitSquashTopology(Mind top) {
        Mind current = top;
        if (current.pendingTransactionCount() != 0) {
            throw new IllegalStateException(
                    "Cannot squash transaction stack with hidden children at published U"
                            + top.getTransactionLevel());
        }

        int level = top.getTransactionLevel();
        while (current.getNext() != null) {
            Mind parent = (Mind) current.getNext();
            if (parent.pendingTransactionCount() != 1) {
                throw new IllegalStateException(
                        "Cannot squash transaction stack: U" + (level - 1)
                                + " does not exclusively own U" + level);
            }
            current = parent;
            --level;
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
