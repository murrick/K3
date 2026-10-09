/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Runtime-only semantic qualification result; never persisted or replayed. */
public final class ContextQualification {

    public static final class CollisionWitness {
        private final String left;
        private final String right;

        CollisionWitness(String left, String right) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
        }

        public String getLeft() {
            return left;
        }

        public String getRight() {
            return right;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CollisionWitness)) {
                return false;
            }
            CollisionWitness witness = (CollisionWitness) other;
            return left.equals(witness.left) && right.equals(witness.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }
    }

    private final boolean valid;
    private final List<CollisionWitness> collisions;

    ContextQualification(boolean valid, List<CollisionWitness> collisions) {
        this.valid = valid;
        this.collisions = Collections.unmodifiableList(
                new ArrayList<CollisionWitness>(collisions));
    }

    /**
     * Internal detached qualification view for storage/federation gates.
     * The returned witnesses contain diagnostic statements only; no runtime
     * rule or local object identity is exposed.
     */
    public static ContextQualification inspect(
            Mind mind, boolean logging) throws Exception {
        if (mind == null) {
            throw new NullPointerException("mind");
        }
        return mind.qualifyCurrentContext(logging);
    }

    public boolean isValid() {
        return valid;
    }

    public List<CollisionWitness> getCollisions() {
        return collisions;
    }
}
