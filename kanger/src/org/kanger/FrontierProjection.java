/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detached positional relation from a native matched pair. Group numbers are
 * descriptor-local equality classes, never runtime variable or Term IDs.
 * Applying the relation yields parent candidates only, never proven Values.
 */
public final class FrontierProjection {
    static final class Slot {
        final int group;
        final SemanticTermSnapshot fixed;

        Slot(int group, SemanticTermSnapshot fixed) {
            this.group = group;
            this.fixed = fixed;
        }
    }

    private final List<Slot> parent;
    private final List<Slot> child;

    FrontierProjection(List<Slot> parent, List<Slot> child) {
        this.parent = Collections.unmodifiableList(new ArrayList<Slot>(parent));
        this.child = Collections.unmodifiableList(new ArrayList<Slot>(child));
    }

    public int getChildPosition(int parentPosition) {
        Slot slot = parent.get(parentPosition);
        if (slot.fixed == null) {
            for (int i = 0; i < child.size(); ++i) {
                if (child.get(i).fixed == null && slot.group == child.get(i).group) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Accepts the full child argument vector, including fixed positions.
     * Returns null for incompatible evidence. An unbound parent position
     * absent from the child remains null and must be resolved by native proof.
     */
    public List<SemanticTermSnapshot> project(List<SemanticTermSnapshot> values) {
        if (values == null || values.size() != child.size()) {
            throw new IllegalArgumentException("Child argument arity mismatch");
        }
        Map<Integer, SemanticTermSnapshot> groups = new HashMap<Integer, SemanticTermSnapshot>();
        for (int i = 0; i < child.size(); ++i) {
            Slot slot = child.get(i);
            SemanticTermSnapshot value = values.get(i);
            if (value == null) {
                throw new IllegalArgumentException("Child evidence must be concrete");
            }
            if (slot.fixed != null) {
                if (!slot.fixed.semanticallyEquals(value)) {
                    return null;
                }
            } else {
                SemanticTermSnapshot previous = groups.put(slot.group, value);
                if (previous != null && !previous.semanticallyEquals(value)) {
                    return null;
                }
            }
        }
        List<SemanticTermSnapshot> result = new ArrayList<SemanticTermSnapshot>();
        for (Slot slot : parent) {
            result.add(slot.fixed != null ? slot.fixed : groups.get(slot.group));
        }
        return Collections.unmodifiableList(result);
    }

    boolean semanticallyEquivalent(FrontierProjection other) {
        return equivalent(parent, other.parent) && equivalent(child, other.child);
    }

    private static boolean equivalent(List<Slot> left, List<Slot> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); ++i) {
            Slot a = left.get(i);
            Slot b = right.get(i);
            if (a.fixed == null ? b.fixed != null || a.group != b.group
                    : b.fixed == null || !a.fixed.semanticallyEquals(b.fixed)) {
                return false;
            }
        }
        return true;
    }
}
