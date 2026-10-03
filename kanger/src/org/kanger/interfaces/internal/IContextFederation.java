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

        private final String sourceLocator;
        private final UUID sourceContextId;
        private final long sourceRevision;
        private final List<Connection> connections;

        public Snapshot(String sourceLocator,
                        UUID sourceContextId,
                        long sourceRevision,
                        List<Connection> connections) {
            if (sourceLocator == null || sourceLocator.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "sourceLocator must not be blank");
            }
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
            this.sourceLocator = sourceLocator.trim();
            this.sourceContextId = sourceContextId;
            this.sourceRevision = sourceRevision;
            this.connections = Collections.unmodifiableList(
                    new ArrayList<Connection>(connections));
        }

        public String getSourceLocator() {
            return sourceLocator;
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

    enum FrontierTruth {
        TRUE,
        FALSE,
        UNKNOWN,
        CONFLICT
    }

    final class Revision {

        private final UUID contextId;
        private final long revision;

        public Revision(UUID contextId, long revision) {
            if (contextId == null) {
                throw new NullPointerException("contextId");
            }
            if (revision < 0L) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            this.contextId = contextId;
            this.revision = revision;
        }

        public UUID getContextId() {
            return contextId;
        }

        public long getRevision() {
            return revision;
        }
    }

    final class FrontierObservation {

        private final int wave;
        private final String querySource;
        private final FrontierTruth truth;
        private final List<Revision> trueSources;
        private final List<Revision> falseSources;
        private final List<Revision> unknownSources;

        public FrontierObservation(
                int wave,
                String querySource,
                FrontierTruth truth,
                List<Revision> trueSources,
                List<Revision> falseSources,
                List<Revision> unknownSources) {
            if (wave < 1) {
                throw new IllegalArgumentException(
                        "wave must be positive");
            }
            if (querySource == null || querySource.isEmpty()) {
                throw new IllegalArgumentException(
                        "querySource must not be empty");
            }
            if (truth == null) {
                throw new NullPointerException("truth");
            }
            this.wave = wave;
            this.querySource = querySource;
            this.truth = truth;
            this.trueSources = immutableRevisions(
                    trueSources, "trueSources");
            this.falseSources = immutableRevisions(
                    falseSources, "falseSources");
            this.unknownSources = immutableRevisions(
                    unknownSources, "unknownSources");
        }

        public int getWave() {
            return wave;
        }

        public String getQuerySource() {
            return querySource;
        }

        public FrontierTruth getTruth() {
            return truth;
        }

        public List<Revision> getTrueSources() {
            return trueSources;
        }

        public List<Revision> getFalseSources() {
            return falseSources;
        }

        public List<Revision> getUnknownSources() {
            return unknownSources;
        }

        private static List<Revision> immutableRevisions(
                List<Revision> source,
                String name) {
            if (source == null) {
                throw new NullPointerException(name);
            }
            return Collections.unmodifiableList(
                    new ArrayList<Revision>(source));
        }
    }

    final class ProvisionalHypothesis {

        private final Revision source;
        private final String statement;

        public ProvisionalHypothesis(
                Revision source,
                String statement) {
            if (source == null) {
                throw new NullPointerException("source");
            }
            if (statement == null || statement.isEmpty()) {
                throw new IllegalArgumentException(
                        "statement must not be empty");
            }
            this.source = source;
            this.statement = statement;
        }

        public Revision getSource() {
            return source;
        }

        public String getStatement() {
            return statement;
        }
    }

    final class QueryResult {

        private final boolean resolved;
        private final int waves;
        private final int evidenceCount;
        private final List<FrontierObservation> observations;
        private final List<ProvisionalHypothesis> provisionalHypotheses;

        public QueryResult(
                boolean resolved,
                int waves,
                int evidenceCount,
                List<FrontierObservation> observations,
                List<ProvisionalHypothesis> provisionalHypotheses) {
            if (waves < 0 || evidenceCount < 0) {
                throw new IllegalArgumentException(
                        "query counters must be non-negative");
            }
            if (observations == null
                    || provisionalHypotheses == null) {
                throw new NullPointerException();
            }
            this.resolved = resolved;
            this.waves = waves;
            this.evidenceCount = evidenceCount;
            this.observations = Collections.unmodifiableList(
                    new ArrayList<FrontierObservation>(
                            observations));
            this.provisionalHypotheses =
                    Collections.unmodifiableList(
                            new ArrayList<ProvisionalHypothesis>(
                                    provisionalHypotheses));
        }

        public boolean isResolved() {
            return resolved;
        }

        public int getWaves() {
            return waves;
        }

        public int getEvidenceCount() {
            return evidenceCount;
        }

        public List<FrontierObservation> getObservations() {
            return observations;
        }

        public List<ProvisionalHypothesis> getProvisionalHypotheses() {
            return provisionalHypotheses;
        }
    }

    Snapshot federationSnapshot() throws Exception;

    Connection connectContext(String targetLocator)
            throws Exception;

    void disconnectContext(String targetLocator)
            throws Exception;

    void disconnectContext(UUID targetContextId)
            throws Exception;

    Connection switchContextRevision(
            String targetLocator,
            long targetRevision) throws Exception;

    Connection switchContextRevision(
            UUID targetContextId,
            long targetRevision) throws Exception;

    /**
     * Executes one operation-local federated query for operator diagnostics.
     * Foreign evidence remains ephemeral and is not committed to the source
     * Context.
     */
    QueryResult executeFederatedQuery(
            String querySource) throws Exception;
}
