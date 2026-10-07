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

    enum RuleSelection { PRIMARY, ALL, PRODUCED, SHOW, TREE, COMMENT }

    final class RuleRow {
        public final long id;
        public final String statement;
        public final boolean generated;
        public final String comment;
        public final List<List<String>> tree;
        public RuleRow(long id, String statement, boolean generated,
                       String comment, List<List<String>> tree) {
            this.id=id; this.statement=statement;
            this.generated=generated; this.comment=comment;
            List<List<String>> copy = new ArrayList<List<String>>();
            for (List<String> row : tree) copy.add(Collections.unmodifiableList(new ArrayList<String>(row)));
            this.tree=Collections.unmodifiableList(copy);
        }
    }

    final class RuleBlock {
        public final String locator;
        public final Revision revision;
        public final boolean working;
        public final List<RuleRow> rules;
        public RuleBlock(String locator, Revision revision, boolean working,
                         List<RuleRow> rules) {
            this.locator=locator; this.revision=revision; this.working=working;
            this.rules=Collections.unmodifiableList(new ArrayList<RuleRow>(rules));
        }
    }

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
            Map<UUID,Long> published=new LinkedHashMap<UUID,Long>();
            for (Connection c:publishedConnections) published.put(c.getTargetContextId(),c.getPinnedRevision());
            Map<UUID,Long> working=new LinkedHashMap<UUID,Long>();
            for (Connection c:connections) working.put(c.getTargetContextId(),c.getPinnedRevision());
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

    final class SourceDependencyRequest {

        private final String locator;
        private final Long exactRevision;

        public SourceDependencyRequest(
                String locator,
                Long exactRevision) {
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
        }

        public String getLocator() {
            return locator;
        }

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

        public SourceDependency(
                String locator,
                UUID contextId,
                long revision) {
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
        }

        public String getLocator() {
            return locator;
        }

        public UUID getContextId() {
            return contextId;
        }

        public long getRevision() {
            return revision;
        }
    }

    interface SourceDependencyPlan {

        List<SourceDependency> getDependencies();
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

    final class EvidenceInjection {

        private final String statement;
        private final Map<String, String> substitutions;
        private final List<Revision> supports;

        public EvidenceInjection(
                String statement,
                Map<String, String> substitutions,
                List<Revision> supports) {
            if (statement == null
                    || statement.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "statement must not be blank");
            }
            if (substitutions == null
                    || supports == null) {
                throw new NullPointerException();
            }
            this.statement = statement;
            this.substitutions =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<String, String>(
                                    substitutions));
            this.supports =
                    Collections.unmodifiableList(
                            new ArrayList<Revision>(
                                    supports));
        }

        public String getStatement() {
            return statement;
        }

        public Map<String, String> getSubstitutions() {
            return substitutions;
        }

        public List<Revision> getSupports() {
            return supports;
        }
    }

    /** Semantic parent/child relation; positions are zero-based argument positions. */
    final class CausalDemand {
        private final String parentQuery;
        private final String childQuery;
        private final List<Integer> parentToChild;
        public CausalDemand(String parentQuery, String childQuery, List<Integer> parentToChild) {
            this.parentQuery = parentQuery;
            this.childQuery = childQuery;
            this.parentToChild = Collections.unmodifiableList(new ArrayList<Integer>(parentToChild));
        }
        public String getParentQuery() { return parentQuery; }
        public String getChildQuery() { return childQuery; }
        public List<Integer> getParentToChild() { return parentToChild; }
    }

    /** One local Context response during a causal wave, never a final root Value by itself. */
    final class CausalStep {
        private final int wave;
        private final String query;
        private final String rootQuery;
        private final Revision target;
        private final FrontierTruth truth;
        private final List<ValueRow> values;
        private final List<EvidenceInjection> suppliedEvidence;
        private final List<CausalDemand> demands;
        public CausalStep(int wave, String rootQuery, String query, Revision target, FrontierTruth truth,
                          List<ValueRow> values, List<EvidenceInjection> suppliedEvidence, List<CausalDemand> demands) {
            this.wave = wave; this.rootQuery = rootQuery; this.query = query; this.target = target; this.truth = truth;
            this.values = Collections.unmodifiableList(new ArrayList<ValueRow>(values));
            this.suppliedEvidence = Collections.unmodifiableList(new ArrayList<EvidenceInjection>(suppliedEvidence));
            this.demands = Collections.unmodifiableList(new ArrayList<CausalDemand>(demands));
        }
        public int getWave() { return wave; }
        public String getRootQuery() { return rootQuery; }
        public String getQuery() { return query; }
        public Revision getTarget() { return target; }
        public FrontierTruth getTruth() { return truth; }
        public List<ValueRow> getValues() { return values; }
        public List<EvidenceInjection> getSuppliedEvidence() { return suppliedEvidence; }
        public List<CausalDemand> getDemands() { return demands; }
    }

    final class QueryResult {

        private final boolean resolved;
        private final FrontierTruth resultTruth;
        private final int waves;
        private final int evidenceCount;
        private final List<FrontierObservation> observations;
        private final List<ValueRow> values;
        private final List<EvidenceInjection> evidenceInjections;
        private final List<ProvisionalHypothesis> provisionalHypotheses;
        private final List<CausalStep> causalSteps;

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
                    Collections.<EvidenceInjection>emptyList(),
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
            this(
                    resolved,
                    resultTruth,
                    waves,
                    evidenceCount,
                    observations,
                    values,
                    Collections.<EvidenceInjection>emptyList(),
                    provisionalHypotheses);
        }

        public QueryResult(
                boolean resolved,
                FrontierTruth resultTruth,
                int waves,
                int evidenceCount,
                List<FrontierObservation> observations,
                List<ValueRow> values,
                List<EvidenceInjection> evidenceInjections,
                List<ProvisionalHypothesis> provisionalHypotheses) {
            this(resolved, resultTruth, waves, evidenceCount, observations, values,
                    evidenceInjections, provisionalHypotheses, Collections.<CausalStep>emptyList());
        }

        public QueryResult(boolean resolved, FrontierTruth resultTruth, int waves, int evidenceCount,
                           List<FrontierObservation> observations, List<ValueRow> values,
                           List<EvidenceInjection> evidenceInjections, List<ProvisionalHypothesis> provisionalHypotheses,
                           List<CausalStep> causalSteps) {
            if (resultTruth == null) {
                throw new NullPointerException("resultTruth");
            }
            if (waves < 0 || evidenceCount < 0) {
                throw new IllegalArgumentException(
                        "query counters must be non-negative");
            }
            if (observations == null
                    || values == null
                    || evidenceInjections == null
                    || provisionalHypotheses == null) {
                throw new NullPointerException();
            }
            this.causalSteps = Collections.unmodifiableList(new ArrayList<CausalStep>(causalSteps));
            this.resolved = resolved;
            this.resultTruth = resultTruth;
            this.waves = waves;
            this.evidenceCount = evidenceCount;
            this.observations = Collections.unmodifiableList(
                    new ArrayList<FrontierObservation>(
                            observations));
            this.values = Collections.unmodifiableList(
                    new ArrayList<ValueRow>(values));
            this.evidenceInjections =
                    Collections.unmodifiableList(
                            new ArrayList<EvidenceInjection>(
                                    evidenceInjections));
            this.provisionalHypotheses =
                    Collections.unmodifiableList(
                            new ArrayList<ProvisionalHypothesis>(
                                    provisionalHypotheses));
        }

        public List<CausalStep> getCausalSteps() { return causalSteps; }

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

        public List<EvidenceInjection> getEvidenceInjections() {
            return evidenceInjections;
        }

        public List<ProvisionalHypothesis> getProvisionalHypotheses() {
            return provisionalHypotheses;
        }
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
