/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable, detached context results for SDK consumers. */
public interface IContextResults {

    final class RuleRow {
        public final long id;
        public final String statement;
        public final boolean generated;
        public final String comment;
        public final List<List<String>> tree;
        public final List<ProofCause> causes;
        public RuleRow(long id, String statement, boolean generated,
                       String comment, List<List<String>> tree) {
            this(id, statement, generated, comment, tree, Collections.<ProofCause>emptyList());
        }
        public RuleRow(long id, String statement, boolean generated, String comment,
                       List<List<String>> tree, List<ProofCause> causes) {
            this.causes = Collections.unmodifiableList(new ArrayList<ProofCause>(causes));
            this.id=id; this.statement=statement;
            this.generated=generated; this.comment=comment;
            List<List<String>> copy = new ArrayList<List<String>>();
            for (List<String> row : tree) copy.add(Collections.unmodifiableList(new ArrayList<String>(row)));
            this.tree=Collections.unmodifiableList(copy);
        }
    }

    /** Native provenance edge, detached before the owning runtime is closed. */
    final class ProofCause {
        public final Revision contextSource;
        public final boolean configuredByX;
        public final boolean hypothesis;
        public final Revision hypothesisSource;
        public final String requiredFor;
        public final long ruleId;
        public final String ruleStatement, donorStatement;
        public final Long donorId;
        public final boolean cycle;
        public final List<ProofCause> causes;
        public ProofCause(long ruleId, String ruleStatement, Long donorId, String donorStatement,
                          boolean cycle, List<ProofCause> causes) {
            this(ruleId, ruleStatement, donorId, donorStatement, cycle, causes, false, null, null);
        }
        public ProofCause(long ruleId, String ruleStatement, Long donorId, String donorStatement,
                          boolean cycle, List<ProofCause> causes, boolean hypothesis, Revision source, String requiredFor) {
            this(ruleId, ruleStatement, donorId, donorStatement, cycle, causes, hypothesis, source, requiredFor, null);
        }
        public ProofCause(long ruleId, String ruleStatement, Long donorId, String donorStatement,
                          boolean cycle, List<ProofCause> causes, boolean hypothesis, Revision source,
                          String requiredFor, Revision contextSource) {
            this(ruleId, ruleStatement, donorId, donorStatement, cycle, causes, hypothesis, source,
                    requiredFor, contextSource, false);
        }
        public ProofCause(long ruleId, String ruleStatement, Long donorId, String donorStatement,
                          boolean cycle, List<ProofCause> causes, boolean hypothesis, Revision source,
                          String requiredFor, Revision contextSource, boolean configuredByX) {
            this.contextSource = contextSource;
            this.configuredByX = configuredByX;
            this.ruleId = ruleId; this.ruleStatement = ruleStatement; this.donorId = donorId;
            this.donorStatement = donorStatement; this.cycle = cycle;
            this.causes = Collections.unmodifiableList(new ArrayList<ProofCause>(causes));
            this.hypothesis = hypothesis; this.hypothesisSource = source; this.requiredFor = requiredFor;
        }
    }

    final class Opinion {
        private final String locator;
        private final Revision source;
        private final boolean working;
        private final QueryResult result;
        private final List<RuleRow> solutions;
        private final boolean configured;
        public Opinion(String locator, Revision source, boolean working, QueryResult result, List<RuleRow> solutions) {
            this(locator, source, working, result, solutions, false);
        }
        public Opinion(String locator, Revision source, boolean working, QueryResult result, List<RuleRow> solutions,
                       boolean configured) {
            this.locator = locator; this.source = source; this.working = working; this.result = result;
            this.solutions = Collections.unmodifiableList(new ArrayList<RuleRow>(solutions));
            this.configured = configured;
        }
        public String getLocator() { return locator; }
        public Revision getSource() { return source; }
        public boolean isWorking() { return working; }
        public boolean isConfigured() { return configured; }
        public QueryResult getResult() { return result; }
        public List<RuleRow> getSolutions() { return solutions; }
        public boolean isMeaningful() { return result.getResultTruth() != FrontierTruth.UNKNOWN
                || !result.getProvisionalHypotheses().isEmpty(); }
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

}
