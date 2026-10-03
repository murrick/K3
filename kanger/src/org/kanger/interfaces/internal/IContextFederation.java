/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.interfaces.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Optional operator-facing projection of multi-Context federation state.
 *
 * <p>The contract deliberately exposes only external Context identity
 * ({@code ContextId + RevisionId}), exact pin policy and qualification state.
 * Storage-local unit identifiers, inference machinery and physical snapshot
 * objects are not part of this boundary.</p>
 */
public interface IContextFederation {

    enum PinPolicy {
        EXACT_REVISION
    }

    enum CompatibilityStatus {
        QUALIFIED,
        STALE
    }

    final class Snapshot {

        private final UUID sourceContextId;
        private final long sourceRevision;
        private final List<Connection> connections;

        public Snapshot(UUID sourceContextId,
                        long sourceRevision,
                        List<Connection> connections) {
            if (sourceContextId == null) {
                throw new NullPointerException("sourceContextId");
            }
            if (sourceRevision < 0L) {
                throw new IllegalArgumentException(
                        "sourceRevision must be non-negative");
            }
            if (connections == null) {
                throw new NullPointerException("connections");
            }
            this.sourceContextId = sourceContextId;
            this.sourceRevision = sourceRevision;
            this.connections = Collections.unmodifiableList(
                    new ArrayList<Connection>(connections));
        }

        public UUID getSourceContextId() {
            return sourceContextId;
        }

        public long getSourceRevision() {
            return sourceRevision;
        }

        public List<Connection> getConnections() {
            return connections;
        }
    }

    final class Connection {

        private final String locator;
        private final UUID targetContextId;
        private final long pinnedRevision;
        private final long currentRevision;
        private final PinPolicy pinPolicy;
        private final CompatibilityStatus compatibilityStatus;
        private final String semanticVersion;

        public Connection(String locator,
                          UUID targetContextId,
                          long pinnedRevision,
                          long currentRevision,
                          PinPolicy pinPolicy,
                          CompatibilityStatus compatibilityStatus,
                          String semanticVersion) {
            if (locator == null || locator.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "locator must not be blank");
            }
            if (targetContextId == null) {
                throw new NullPointerException("targetContextId");
            }
            if (pinnedRevision < 0L || currentRevision < 0L) {
                throw new IllegalArgumentException(
                        "revisions must be non-negative");
            }
            if (pinPolicy == null) {
                throw new NullPointerException("pinPolicy");
            }
            if (compatibilityStatus == null) {
                throw new NullPointerException(
                        "compatibilityStatus");
            }
            if (semanticVersion == null
                    || semanticVersion.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "semanticVersion must not be blank");
            }
            this.locator = locator;
            this.targetContextId = targetContextId;
            this.pinnedRevision = pinnedRevision;
            this.currentRevision = currentRevision;
            this.pinPolicy = pinPolicy;
            this.compatibilityStatus =
                    compatibilityStatus;
            this.semanticVersion =
                    semanticVersion.trim();
        }

        public String getLocator() {
            return locator;
        }

        public UUID getTargetContextId() {
            return targetContextId;
        }

        public long getPinnedRevision() {
            return pinnedRevision;
        }

        /**
         * CURRENT observed at inspection time. It is informational only and
         * never changes the exact pinned revision.
         */
        public long getCurrentRevision() {
            return currentRevision;
        }

        public boolean hasNewerRevision() {
            return currentRevision > pinnedRevision;
        }

        public PinPolicy getPinPolicy() {
            return pinPolicy;
        }

        public CompatibilityStatus getCompatibilityStatus() {
            return compatibilityStatus;
        }

        public String getSemanticVersion() {
            return semanticVersion;
        }
    }

    Snapshot federationSnapshot() throws Exception;

    Connection connectContext(String targetLocator)
            throws Exception;

    void disconnectContext(UUID targetContextId)
            throws Exception;

    Connection switchContextRevision(
            UUID targetContextId,
            long targetRevision) throws Exception;
}
