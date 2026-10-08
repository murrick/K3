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
public interface IContextFederation extends org.kanger.interfaces.IContextResults {

    default Revision forkContext(IMind source, String locator) throws Exception {
        throw new UnsupportedOperationException("Context fork is unavailable");
    }


    enum PinPolicy {
        EXACT_REVISION
    }

    enum CompatibilityStatus {
        QUALIFIED,
        STALE
    }

    enum RuleSelection { PRIMARY, ALL, PRODUCED, SHOW, TREE, COMMENT }

    final class RuleBlock {
        public final String locator;
        public final Revision revision;
        public final boolean working;
        public final boolean configured;
        public final List<RuleRow> rules;
        public RuleBlock(String locator, Revision revision, boolean working,
                         List<RuleRow> rules) {
            this(locator, revision, working, rules, false);
        }
        public RuleBlock(String locator, Revision revision, boolean working, List<RuleRow> rules, boolean configured) {
            this.locator=locator; this.revision=revision; this.working=working;
            this.rules=Collections.unmodifiableList(new ArrayList<RuleRow>(rules));
            this.configured = configured;
        }
    }

    /** One full-query, local-only opinion. All payloads are detached from its runtime. */
    final class DependencyNotice {
        public final SourceDependency owner, dependency;
        public final Long actualRevision;
        public DependencyNotice(SourceDependency owner, SourceDependency dependency, Long actualRevision) {
            this.owner=owner; this.dependency=dependency; this.actualRevision=actualRevision;
        }
        public String getStatus() {
            return actualRevision==null ? "NOT_CONNECTED"
                    : actualRevision.longValue()==dependency.getRevision() ? "CONNECTED" : "DIFFERENT_REVISION";
        }
    }

    final class Snapshot {

        private final String sourceLocator;
        private final UUID sourceContextId;
        private final long sourceRevision;
        private final List<Connection> connections;
        private final List<Connection> publishedConnections;
        private final List<DependencyNotice> dependencyNotices;

        public Snapshot(String sourceLocator,
                        UUID sourceContextId,
                        long sourceRevision,
                        List<Connection> connections) {
            this(sourceLocator, sourceContextId, sourceRevision, connections, connections,
                    Collections.<DependencyNotice>emptyList());
        }

        public Snapshot(String sourceLocator, UUID sourceContextId, long sourceRevision,
                        List<Connection> connections, List<Connection> publishedConnections,
                        List<DependencyNotice> dependencyNotices) {
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
            this.publishedConnections=Collections.unmodifiableList(new ArrayList<Connection>(publishedConnections));
            this.dependencyNotices=Collections.unmodifiableList(new ArrayList<DependencyNotice>(dependencyNotices));
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

        public List<Connection> getPublishedConnections() { return publishedConnections; }
        public List<DependencyNotice> getDependencyNotices() { return dependencyNotices; }
        public boolean hasWorkingChanges() {
            Map<UUID,String> published=new LinkedHashMap<UUID,String>();
            for (Connection c:publishedConnections) published.put(c.getTargetContextId(),c.getPinnedRevision() + ":" + c.getInitialization() + ":" + c.getTrustGroup());
            Map<UUID,String> working=new LinkedHashMap<UUID,String>();
            for (Connection c:connections) working.put(c.getTargetContextId(),c.getPinnedRevision() + ":" + c.getInitialization() + ":" + c.getTrustGroup());
            return !published.equals(working);
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
        private final List<String> initialization;
        private final String trustGroup;

        public Connection(String locator,
                          UUID targetContextId,
                          long pinnedRevision,
                          long currentRevision,
                          PinPolicy pinPolicy,
                          CompatibilityStatus compatibilityStatus,
                          String semanticVersion) {
            this(locator, targetContextId, pinnedRevision, currentRevision, pinPolicy,
                    compatibilityStatus, semanticVersion, Collections.<String>emptyList());
        }
        public Connection(String locator, UUID targetContextId, long pinnedRevision, long currentRevision,
                          PinPolicy pinPolicy, CompatibilityStatus compatibilityStatus, String semanticVersion,
                          List<String> initialization) {
            this(locator, targetContextId, pinnedRevision, currentRevision, pinPolicy, compatibilityStatus, semanticVersion, initialization, null);
        }
        public Connection(String locator, UUID targetContextId, long pinnedRevision, long currentRevision,
                          PinPolicy pinPolicy, CompatibilityStatus compatibilityStatus, String semanticVersion,
                          List<String> initialization, String trustGroup) {
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
            this.trustGroup = org.kanger.TrustGroups.validate(trustGroup);
            this.initialization = Collections.unmodifiableList(new ArrayList<String>(initialization));
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

        public List<String> getInitialization() { return initialization; }
        public String getTrustGroup() { return trustGroup; }

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

    final class SourceDependencyRequest {

        private final String locator;
        private final Long exactRevision;
        private final List<String> initialization;
        private final String trustGroup;

        public SourceDependencyRequest(
                String locator,
                Long exactRevision) {
            this(locator, exactRevision, Collections.<String>emptyList());
        }
        public SourceDependencyRequest(String locator, Long exactRevision, List<String> initialization) {
            this(locator, exactRevision, initialization, null);
        }
        public SourceDependencyRequest(String locator, Long exactRevision, List<String> initialization, String trustGroup) {
            if (locator == null
                    || locator.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "locator must not be blank");
            }
            if (exactRevision != null
                    && exactRevision.longValue() < 0L) {
                throw new IllegalArgumentException(
                        "exactRevision must be non-negative");
            }
            this.locator = locator.trim();
            this.exactRevision = exactRevision;
            this.trustGroup = org.kanger.TrustGroups.validate(trustGroup);
            this.initialization = Collections.unmodifiableList(new ArrayList<String>(initialization));
        }

        public String getLocator() {
            return locator;
        }

        public List<String> getInitialization() { return initialization; }
        public String getTrustGroup() { return trustGroup; }

        public boolean isExact() {
            return exactRevision != null;
        }

        public long getExactRevision() {
            if (exactRevision == null) {
                throw new IllegalStateException(
                        "dependency request resolves CURRENT");
            }
            return exactRevision.longValue();
        }
    }

    final class SourceDependency {

        private final String locator;
        private final UUID contextId;
        private final long revision;
        private final List<String> initialization;
        private final String trustGroup;

        public SourceDependency(
                String locator,
                UUID contextId,
                long revision) {
            this(locator, contextId, revision, Collections.<String>emptyList());
        }
        public SourceDependency(String locator, UUID contextId, long revision, List<String> initialization) {
            this(locator, contextId, revision, initialization, null);
        }
        public SourceDependency(String locator, UUID contextId, long revision, List<String> initialization, String trustGroup) {
            if (locator == null
                    || locator.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "locator must not be blank");
            }
            if (contextId == null) {
                throw new NullPointerException("contextId");
            }
            if (revision < 0L) {
                throw new IllegalArgumentException(
                        "revision must be non-negative");
            }
            this.locator = locator.trim();
            this.contextId = contextId;
            this.revision = revision;
            this.trustGroup = org.kanger.TrustGroups.validate(trustGroup);
            this.initialization = Collections.unmodifiableList(new ArrayList<String>(initialization));
        }

        public String getLocator() {
            return locator;
        }

        public UUID getContextId() {
            return contextId;
        }

        public List<String> getInitialization() { return initialization; }
        public String getTrustGroup() { return trustGroup; }

        public long getRevision() {
            return revision;
        }
    }

    interface SourceDependencyPlan {

        List<SourceDependency> getDependencies();
    }

    enum ExplainPolarity {
        FALSE_PASS,
        TRUE_PASS
    }

    final class ExplainPass {

        private final ExplainPolarity polarity;
        private final QueryResult continuation;

        public ExplainPass(
                ExplainPolarity polarity,
                QueryResult continuation) {
            if (polarity == null
                    || continuation == null) {
                throw new NullPointerException();
            }
            this.polarity = polarity;
            this.continuation = continuation;
        }

        public ExplainPolarity getPolarity() {
            return polarity;
        }

        public QueryResult getContinuation() {
            return continuation;
        }
    }

    final class ExplainResult {

        private final Snapshot context;
        private final FrontierTruth localTruth;
        private final FrontierTruth finalTruth;
        private final List<ExplainPass> passes;
        private final List<ValueRow> values;
        private final List<String> solutions;

        public ExplainResult(
                Snapshot context,
                FrontierTruth localTruth,
                FrontierTruth finalTruth,
                List<ExplainPass> passes,
                List<ValueRow> values,
                List<String> solutions) {
            if (context == null
                    || localTruth == null
                    || finalTruth == null
                    || passes == null
                    || values == null
                    || solutions == null) {
                throw new NullPointerException();
            }
            this.context = context;
            this.localTruth = localTruth;
            this.finalTruth = finalTruth;
            this.passes =
                    Collections.unmodifiableList(
                            new ArrayList<ExplainPass>(
                                    passes));
            this.values =
                    Collections.unmodifiableList(
                            new ArrayList<ValueRow>(
                                    values));
            this.solutions =
                    Collections.unmodifiableList(
                            new ArrayList<String>(
                                    solutions));
        }

        public Snapshot getContext() {
            return context;
        }

        public FrontierTruth getLocalTruth() {
            return localTruth;
        }

        public FrontierTruth getFinalTruth() {
            return finalTruth;
        }

        public List<ExplainPass> getPasses() {
            return passes;
        }

        public List<ValueRow> getValues() {
            return values;
        }

        public List<String> getSolutions() {
            return solutions;
        }
    }

    SourceDependencyPlan prepareSourceDependencies(
            List<SourceDependencyRequest> requests)
            throws Exception;

    void installSourceDependencies(
            SourceDependencyPlan plan) throws Exception;

    List<SourceDependency> sourceDependencies()
            throws Exception;

    Snapshot federationSnapshot() throws Exception;

    default List<RuleBlock> inspectRules(IMind source, String locator, RuleSelection selection, Long number) throws Exception {
        throw new UnsupportedOperationException("Context rules inspection is unavailable");
    }

    /** Opaque session topology checkpoint for explicit user transaction rollback. */
    default Object checkpointConnections() throws Exception { return null; }

    default void restoreConnections(Object checkpoint) throws Exception { }

    default long saveConnections(IMind source) throws Exception {
        throw new UnsupportedOperationException("Context topology publication is unavailable");
    }

    /** DUMB2 named history is read-only and requires neither an open storage nor a direct connection. */
    VersionHistory versionHistory(
            String targetLocator) throws Exception;



    Connection connectContext(String targetLocator)
            throws Exception;

    default Connection connectContext(String targetLocator, String trustGroup) throws Exception {
        if (trustGroup == null) return connectContext(targetLocator);
        throw new UnsupportedOperationException("Trust communes are unavailable");
    }

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

    /** Explicit full-query opinions from live X and exact direct pins; never traverses dependencies. */
    /** Local whole-query proofs from epistemic participants, without exporting abstract witnesses. */
    default Boolean continueWholeQuery(IMind sourceMind, String querySource,
            Queue<ITerm> externals, boolean logging) throws Exception { return null; }

    default Map<String, Opinion> executeOpinions(IMind sourceMind, String locator, String querySource,
            List<org.kanger.SemanticTermSnapshot> parameters) throws Exception {
        throw new UnsupportedOperationException("Context opinions are not supported by this provider");
    }

    /**
     * Executes one local-only diagnostic query against X or one explicitly
     * connected exact-pinned Context.
     *
     * <p>The source locator selects the live initiating Mind. A foreign locator
     * resolves only through the source Context's direct ConnectionVector and
     * executes against that immutable pinned revision plus X's private initialization layer. Target connections are
     * never traversed.</p>
     */
    QueryResult executeIsolatedQuery(
            IMind sourceMind,
            String targetLocator,
            String querySource) throws Exception;

    default void applyConnectionCommand(IMind sourceMind, String locator, String command) throws Exception {
        throw new UnsupportedOperationException("Context connection layers are unavailable");
    }

    /**
     * Executes one operation-local federated query for operator diagnostics.
     * Foreign evidence remains ephemeral and is not committed to the source
     * Context.
     */
    QueryResult executeFederatedQuery(
            String querySource) throws Exception;
}
