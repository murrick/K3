/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.ContextQualification;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Full-state compatibility qualification for two exact DUMB2 revisions.
 *
 * <p>Both replay directions are executed. The left side may be either an
 * already-published revision or one immutable unpublished ContextCandidate.
 * Qualification never publishes either side.</p>
 */
final class PairQualification {

    private PairQualification() {
    }

    static Result qualify(Path leftLocation,
                          long leftRevision,
                          Path rightLocation,
                          long rightRevision) throws Exception {
        AttachedMind left =
                AttachedMind.open(
                        leftLocation, leftRevision, "pair-left");
        AttachedMind right =
                AttachedMind.open(
                        rightLocation, rightRevision, "pair-right");
        try {
            return qualify(left, right);
        } finally {
            right.close();
            left.close();
        }
    }

    static Result qualify(ContextCandidate leftCandidate,
                          Path rightLocation,
                          long rightRevision) throws Exception {
        AttachedMind left =
                AttachedMind.open(
                        leftCandidate, "pair-candidate-left");
        AttachedMind right =
                AttachedMind.open(
                        rightLocation, rightRevision, "pair-right");
        try {
            return qualify(left, right);
        } finally {
            right.close();
            left.close();
        }
    }

    static boolean qualifyLocal(
            ContextCandidate candidate) throws Exception {
        AttachedMind attached =
                AttachedMind.open(
                        candidate, "write-candidate-local");
        try {
            return Boolean.TRUE.equals(
                    attached.mind.queryCheck(false));
        } finally {
            attached.close();
        }
    }

    /**
     * Validates the transient union candidate + every direct target at once.
     * No target may recursively consult its own connections.
     */
    static boolean qualifyComposition(
            ContextCandidate candidate,
            ConnectionVector connections) throws Exception {
        AttachedMind attached =
                AttachedMind.open(
                        candidate, "write-candidate-composition");
        try {
            return qualifyComposition(
                    attached, connections);
        } finally {
            attached.close();
        }
    }

    static boolean qualifyComposition(
            Path sourceLocation,
            long sourceRevision,
            ConnectionVector connections) throws Exception {
        return qualifyCompositionState(
                sourceLocation,
                sourceRevision,
                connections).isValid();
    }

    static CompositionQualification qualifyCompositionState(
            Path sourceLocation,
            long sourceRevision,
            ConnectionVector connections) throws Exception {
        AttachedMind attached =
                AttachedMind.open(
                        sourceLocation,
                        sourceRevision,
                        "connection-switch-composition");
        try {
            return qualifyCompositionState(
                    attached, connections);
        } finally {
            attached.close();
        }
    }

    private static boolean qualifyComposition(
            AttachedMind attached,
            ConnectionVector connections) throws Exception {
        return qualifyCompositionState(
                attached, connections).isValid();
    }

    private static CompositionQualification qualifyCompositionState(
            AttachedMind attached,
            ConnectionVector connections) throws Exception {
        Mind overlay = null;
        try {
            overlay =
                    Mind.ephemeralChild(attached.mind);
            for (ContextConnection connection
                    : connections.getConnections()) {
                AttachedMind target =
                        AttachedMind.open(
                                connection.getTargetLocation(),
                                connection.getTarget().getRevision(),
                                "composition-target");
                try {
                    if (!connection.getTarget().equals(
                            target.ref())) {
                        throw new IllegalStateException(
                                "Pinned target identity changed during composition qualification: expected "
                                        + connection.getTarget()
                                        + " found " + target.ref());
                    }
                    PortableSource.capture(
                            target.mind).replay(overlay);
                } finally {
                    target.close();
                }
            }
            ContextQualification qualification =
                    ContextQualification.inspect(
                            overlay, false);
            return new CompositionQualification(
                    qualification.isValid(),
                    collisionKeys(qualification));
        } finally {
            if (overlay != null) {
                overlay.getSolutions().clear();
                overlay.getValues().clear();
                attached.mind.release(overlay);
            }
        }
    }

    private static Set<String> collisionKeys(
            ContextQualification qualification) {
        Set<String> result =
                new LinkedHashSet<String>();
        for (ContextQualification.CollisionWitness witness
                : qualification.getCollisions()) {
            String left = witness.getLeft();
            String right = witness.getRight();
            if (left.compareTo(right) <= 0) {
                result.add(left + "\u0000" + right);
            } else {
                result.add(right + "\u0000" + left);
            }
        }
        return result;
    }

    static final class CompositionQualification {

        private final boolean valid;
        private final Set<String> collisions;

        private CompositionQualification(
                boolean valid,
                Set<String> collisions) {
            this.valid = valid;
            this.collisions =
                    new LinkedHashSet<String>(collisions);
        }

        boolean isValid() {
            return valid;
        }

        boolean introducesNewCollisionComparedTo(
                CompositionQualification baseline) {
            if (baseline == null) {
                throw new NullPointerException("baseline");
            }
            if (!valid && collisions.isEmpty()) {
                return true;
            }
            return !baseline.collisions.containsAll(
                    collisions);
        }
    }

    private static Result qualify(
            AttachedMind left,
            AttachedMind right) throws Exception {
        RevisionRef leftRef = left.ref();
        RevisionRef rightRef = right.ref();

        PortableSource leftSource =
                PortableSource.capture(left.mind);
        PortableSource rightSource =
                PortableSource.capture(right.mind);

        boolean leftOverRight =
                qualifyDirection(
                        leftSource, right.mind);
        boolean rightOverLeft =
                qualifyDirection(
                        rightSource, left.mind);

        if (leftOverRight != rightOverLeft) {
            throw new IllegalStateException(
                    "Pair qualification is direction-dependent for "
                            + leftRef + " and " + rightRef
                            + ": left-over-right=" + leftOverRight
                            + ", right-over-left=" + rightOverLeft);
        }

        CompatibilityCertificate certificate =
                leftOverRight
                        ? new CompatibilityCertificate(
                                leftRef,
                                rightRef,
                                Version.CORE_VERSION_S)
                        : null;
        return new Result(
                leftRef,
                rightRef,
                leftOverRight,
                rightOverLeft,
                certificate);
    }

    private static boolean qualifyDirection(
            PortableSource source,
            Mind target) throws Exception {
        Mind overlay =
                Mind.ephemeralChild(target);
        try {
            source.replay(overlay);
            return Boolean.TRUE.equals(
                    overlay.queryCheck(false));
        } finally {
            overlay.getSolutions().clear();
            overlay.getValues().clear();
            target.release(overlay);
        }
    }

    static final class Result {

        private final RevisionRef left;
        private final RevisionRef right;
        private final boolean leftOverRight;
        private final boolean rightOverLeft;
        private final CompatibilityCertificate certificate;

        private Result(RevisionRef left,
                       RevisionRef right,
                       boolean leftOverRight,
                       boolean rightOverLeft,
                       CompatibilityCertificate certificate) {
            this.left = left;
            this.right = right;
            this.leftOverRight = leftOverRight;
            this.rightOverLeft = rightOverLeft;
            this.certificate = certificate;
        }

        RevisionRef getLeft() {
            return left;
        }

        RevisionRef getRight() {
            return right;
        }

        boolean isCompatible() {
            return leftOverRight && rightOverLeft;
        }

        boolean isLeftOverRightValid() {
            return leftOverRight;
        }

        boolean isRightOverLeftValid() {
            return rightOverLeft;
        }

        CompatibilityCertificate getCertificate() {
            return certificate;
        }
    }

    /**
     * Same portable authoritative surface already used by Core rebase:
     * visible non-generated Rule origins plus visible UDF source.
     */
    static final class PortableSource {

        private final List<String> rules =
                new ArrayList<String>();
        private final List<String> operations =
                new ArrayList<String>();

        static PortableSource capture(Mind source)
                throws Exception {
            PortableSource result =
                    new PortableSource();
            for (IRule candidate : source.getRules()) {
                Rule rule = (Rule) candidate;
                if (!rule.isGenerated()
                        && !rule.isDeleted(source)) {
                    result.rules.add(
                            rule.getOrigin());
                }
            }
            for (IOperation operation
                    : source.getLibrary()) {
                if (!operation.isDeleted(source)) {
                    result.operations.add(
                            operation.asString());
                }
            }
            return result;
        }

        void replay(Mind target) throws Exception {
            for (String operation : operations) {
                target.query(
                        operation, null, false);
            }
            for (String rule : rules) {
                target.compileLine(
                        rule,
                        false,
                        new LinkedList<ITerm>());
            }
        }
    }

    private static final class AttachedMind
            implements AutoCloseable {

        private final User user;
        private final ContextSnapshotData data;
        private Mind mind;

        private AttachedMind(
                User user,
                ContextSnapshotData data,
                Mind mind) {
            this.user = user;
            this.data = data;
            this.mind = mind;
        }

        static AttachedMind open(
                Path location,
                long revision,
                String logicalName) throws Exception {
            return open(
                    new ContextSnapshotData(
                            location,
                            logicalName,
                            Long.valueOf(revision)),
                    logicalName);
        }

        static AttachedMind open(
                ContextCandidate candidate,
                String logicalName) throws Exception {
            return open(
                    new ContextSnapshotData(
                            candidate,
                            logicalName),
                    logicalName);
        }

        private static AttachedMind open(
                ContextSnapshotData data,
                String logicalName) throws Exception {
            User user = new User();
            data.init(user);
            Mind mind = new Mind(user);
            user.setCurrentMind(mind);
            mind = (Mind) mind.useStorage(
                    logicalName);
            user.setCurrentMind(mind);
            return new AttachedMind(
                    user, data, mind);
        }

        RevisionRef ref() {
            return new RevisionRef(
                    data.getContextId(),
                    data.getRevision());
        }

        @Override
        public void close() throws Exception {
            if (mind != null) {
                mind = (Mind) mind.closeStorage();
                user.setCurrentMind(mind);
            }
        }
    }
}
