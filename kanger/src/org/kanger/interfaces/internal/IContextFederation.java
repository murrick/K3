/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.interfaces.internal;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

    final class RevisionVersion {

        private final long revision;
        private final String description;

        public RevisionVersion(
                long revision,
                String description) {
            if (revision < 0L) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            if (description == null) {
                throw new NullPointerException("description");
            }
            this.revision = revision;
            this.description = description;
        }

        public long getRevision() {
            return revision;
        }

        public String getDescription() {
            return description;
        }
    }

    final class VersionHistory {

        private final String locator;
        private final UUID contextId;
        private final long currentRevision;
        private final long pinnedRevision;
        private final List<RevisionVersion> revisions;

        public VersionHistory(
                String locator,
                UUID contextId,
                long currentRevision,
                long pinnedRevision,
                List<RevisionVersion> revisions) {
            if (locator == null
                    || locator.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "locator must not be blank");
            }
            if (contextId == null
                    || revisions == null) {
                throw new NullPointerException();
            }
            if (currentRevision < 0L
                    || pinnedRevision < -1L) {
                throw new IllegalArgumentException(
                        "invalid revision marker");
            }
            this.locator = locator.trim();
            this.contextId = contextId;
            this.currentRevision = currentRevision;
            this.pinnedRevision = pinnedRevision;
            this.revisions =
                    Collections.unmodifiableList(
                            new ArrayList<RevisionVersion>(
                                    revisions));
        }

        public String getLocator() {
            return locator;
        }

        public UUID getContextId() {
            return contextId;
        }

        public long getCurrentRevision() {
            return currentRevision;
        }

        public boolean hasPinnedRevision() {
            return pinnedRevision >= 0L;
        }

        public long getPinnedRevision() {
            return pinnedRevision;
        }

        public List<RevisionVersion> getRevisions() {
            return revisions;
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

    final class ValueRow {

        private final Map<String, String> bindings;

        public ValueRow(Map<String, String> bindings) {
            if (bindings == null) {
                throw new NullPointerException("bindings");
            }
            this.bindings = Collections.unmodifiableMap(
                    new LinkedHashMap<String, String>(bindings));
        }

        public Map<String, String> getBindings() {
            return bindings;
        }
    }

    final class QueryResult {

        private final boolean resolved;
        private final FrontierTruth resultTruth;
        private final int waves;
        private final int evidenceCount;
        private final List<FrontierObservation> observations;
        private final List<ValueRow> values;
        private final List<ProvisionalHypothesis> provisionalHypotheses;

        public QueryResult(
                boolean resolved,
                int waves,
                int evidenceCount,
                List<FrontierObservation> observations,
                List<ProvisionalHypothesis> provisionalHypotheses) {
            this(
                    resolved,
                    resolved
                            ? FrontierTruth.TRUE
                            : FrontierTruth.UNKNOWN,
                    waves,
                    evidenceCount,
                    observations,
                    Collections.<ValueRow>emptyList(),
                    provisionalHypotheses);
        }

        public QueryResult(
                boolean resolved,
                FrontierTruth resultTruth,
                int waves,
                int evidenceCount,
                List<FrontierObservation> observations,
                List<ValueRow> values,
                List<ProvisionalHypothesis> provisionalHypotheses) {
            if (resultTruth == null) {
                throw new NullPointerException("resultTruth");
            }
            if (waves < 0 || evidenceCount < 0) {
                throw new IllegalArgumentException(
                        "query counters must be non-negative");
            }
            if (observations == null
                    || values == null
                    || provisionalHypotheses == null) {
                throw new NullPointerException();
            }
            this.resolved = resolved;
            this.resultTruth = resultTruth;
            this.waves = waves;
            this.evidenceCount = evidenceCount;
            this.observations = Collections.unmodifiableList(
                    new ArrayList<FrontierObservation>(
                            observations));
            this.values = Collections.unmodifiableList(
                    new ArrayList<ValueRow>(values));
            this.provisionalHypotheses =
                    Collections.unmodifiableList(
                            new ArrayList<ProvisionalHypothesis>(
                                    provisionalHypotheses));
        }

        public boolean isResolved() {
            return resolved;
        }

        public FrontierTruth getResultTruth() {
            return resultTruth;
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

        public List<ValueRow> getValues() {
            return values;
        }

        public List<ProvisionalHypothesis> getProvisionalHypotheses() {
            return provisionalHypotheses;
        }
    }

    Snapshot federationSnapshot() throws Exception;

    VersionHistory versionHistory(
            String targetLocator) throws Exception;



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
     * Returns whether the currently open Context has at least one direct
     * connection eligible for federation.
     */
    boolean hasConnectedContexts() throws Exception;

    /**
     * Continues one unresolved query polarity in the live initiating Mind.
     *
     * <p>The source Mind remains the owner of variables, substitution
     * convergence and final Values/Solutions. Foreign Contexts execute only
     * local exact-revision frontier queries. A leading {@code ?} represents the
     * normal TRUE pass; a leading {@code !} is the historical inverted FALSE
     * pass compiled as a query, not an assertion.</p>
     *
     * <p>When the pass resolves, query presentation state is published back to
     * {@code sourceMind}. Foreign semantic objects themselves remain confined to
     * an ephemeral child and are never committed into the source factories.</p>
     */
    QueryResult continueFederatedQuery(
            IMind sourceMind,
            String querySource,
            Queue<ITerm> externals,
            boolean logging) throws Exception;

    /**
     * Executes one local-only diagnostic query against X or one explicitly
     * connected exact-pinned Context.
     *
     * <p>The source locator selects the live initiating Mind. A foreign locator
     * resolves only through the source Context's direct ConnectionVector and
     * executes against that immutable pinned revision. Target connections are
     * never traversed.</p>
     */
    QueryResult executeIsolatedQuery(
            IMind sourceMind,
            String targetLocator,
            String querySource) throws Exception;

    /**
     * Executes one operation-local federated query for operator diagnostics.
     * Foreign evidence remains ephemeral and is not committed to the source
     * Context.
     */
    QueryResult executeFederatedQuery(
            String querySource) throws Exception;
}
