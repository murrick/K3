/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.CausalFrontierCapture;
import org.kanger.interfaces.IRule;
import org.kanger.stores.ValuesStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.primitives.ArgumentsList;
import java.util.Iterator;
import org.kanger.Mind;
import org.kanger.SemanticTermSnapshot;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.enums.Enums;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * M3.6/M3.7 continuation over ephemeral foreign evidence.
 *
 * <p>The query Rule is compiled exactly once in one operation-local child Mind.
 * Each federation wave may add ordinary assertion Rules to that same child,
 * after which the same compiled query Rule is linked/analyzed again. No
 * evidence is committed to the source Context and no query is recursively
 * federated by target Contexts.</p>
 *
 * <p>M3.7 aggregates every foreign frontier independently. Ground TRUE and
 * FALSE aggregates become operation-local factual donors of the corresponding
 * polarity. UNKNOWN and CONFLICT remain observations only. Provisional foreign
 * hypotheses are retained with exact source/revision provenance and never
 * become factual donors.</p>
 */
final class FrontierContinuationEngine {

    private static final int MAX_WAVES = 128;

    private FrontierContinuationEngine() {
    }

    static Result execute(
            Path sourceLocation,
            String querySource) throws Exception {
        return execute(sourceLocation, null, querySource);
    }

    static Result execute(
            Path sourceLocation,
            ConnectionVector connections,
            String querySource) throws Exception {
        return execute(sourceLocation,connections,-1L,querySource);
    }

    static Result execute(Path sourceLocation, ConnectionVector connections, long revision,
            String querySource) throws Exception {
        validateQuerySource(sourceLocation, querySource);
        OperationSnapshot operation = revision>=0L ? OperationSnapshot.open(sourceLocation,revision,connections)
                : connections==null ? OperationSnapshot.open(sourceLocation) : OperationSnapshot.open(sourceLocation,connections);
        SnapshotMindRuntime runtime = null;
        Mind root = null;
        Mind work = null;
        try {
            runtime = SnapshotMindRuntime.open(
                    operation.getSourceLocation(),
                    operation.getSourceRef(),
                    "frontier-operation-"
                            + operation.getSourceRef()
                                    .getContextId().toString());
            root = runtime.getMind();
            work = Mind.ephemeralChild(root);

            Result result = run(
                    work,
                    root,
                    operation,
                    querySource,
                    new LinkedList<ITerm>(),
                    false);

            Mind settled = work;
            work = null;
            root.discardEphemeral(settled);
            return result;
        } finally {
            if (root != null && work != null) {
                Mind unsettled = work;
                work = null;
                root.discardEphemeral(unsettled);
            }
            if (runtime != null) {
                runtime.close();
            }
            operation.close();
        }
    }

    /**
     * Continues one unresolved polarity against the live initiating Mind.
     *
     * <p>The source Mind may include a user transaction overlay. Only foreign
     * targets are read from exact immutable pins captured by OperationSnapshot.
     * A resolved pass publishes query presentation state back to the source;
     * an unresolved pass is discarded without replacing the caller's previous
     * local Values/Solutions/logs.</p>
     */
    static Result execute(
            Mind sourceMind,
            Path sourceLocation,
            String querySource,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        return execute(
                sourceMind,
                sourceLocation,
                null,
                querySource,
                externals,
                logging);
    }

    static Result execute(
            Mind sourceMind,
            Path sourceLocation,
            ConnectionVector connections,
            String querySource,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        return execute(sourceMind,sourceLocation,connections,-1L,querySource,externals,logging);
    }

    static Result execute(Mind sourceMind, Path sourceLocation, ConnectionVector connections, long revision,
            String querySource, Queue<ITerm> externals, boolean logging) throws Exception {
        if (sourceMind == null) {
            throw new NullPointerException("sourceMind");
        }
        validateQuerySource(sourceLocation, querySource);

        OperationSnapshot operation = revision>=0L ? OperationSnapshot.open(sourceLocation,revision,connections)
                : connections==null ? OperationSnapshot.open(sourceLocation) : OperationSnapshot.open(sourceLocation,connections);
        Mind work = Mind.ephemeralChild(sourceMind);
        try {
            Result result = run(
                    work,
                    sourceMind,
                    operation,
                    querySource,
                    externals == null
                            ? new LinkedList<ITerm>()
                            : new LinkedList<ITerm>(externals),
                    logging);

            Mind settled = work;
            work = null;
            if (result.isResolved() || result.enumerated) {
                sourceMind.release(settled);
            } else {
                sourceMind.discardEphemeral(settled);
            }
            return result;
        } finally {
            if (work != null) {
                Mind unsettled = work;
                work = null;
                sourceMind.discardEphemeral(unsettled);
            }
            operation.close();
        }
    }

    private static void validateQuerySource(
            Path sourceLocation,
            String querySource) {
        if (sourceLocation == null) {
            throw new NullPointerException("sourceLocation");
        }
        if (querySource == null || querySource.isEmpty()) {
            throw new IllegalArgumentException(
                    "Federated continuation requires a query source");
        }
        char operator = querySource.charAt(0);
        if (operator != Enums.SUC
                && operator != Enums.ANT) {
            throw new IllegalArgumentException(
                    "Federated continuation requires a TRUE/FALSE query pass");
        }
    }

    private static Result run(
            Mind work,
            Mind localSource,
            OperationSnapshot operation,
            String querySource,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        QueryPass queryPass =
                querySource.charAt(0) == Enums.ANT
                        ? QueryPass.CHECKFALSE
                        : QueryPass.CHECKTRUE;
        work.setQueryPass(queryPass);

        Queue<ITerm> localExternals = new LinkedList<ITerm>();
        for (ITerm value : externals) {
            localExternals.add(work.getTerms().projectSemantic(SemanticTermSnapshot.capture(value).materialize()));
        }
        Rule query = (Rule) work.compileLine(
                querySource,
                true,
                new LinkedList<ITerm>(localExternals));
        if (query == null || query.isSecond()) {
            throw new IllegalStateException(
                    "Unable to establish operation-local query Rule: "
                            + querySource);
        }

        FrontierDomain enumeration = queryPass == QueryPass.CHECKTRUE
                ? CausalFrontierCapture.describeAtomic(work, query) : null;
        if (enumeration != null && !enumeration.isGround()) {
            return enumerate(work, localSource, operation, query, enumeration, queryPass, logging);
        }

        long queryRuleId = query.getId();
        List<FrontierObservation> observations =
                new ArrayList<FrontierObservation>();
        Set<FrontierAggregate.ProvisionalHypothesis>
                provisionalHypotheses =
                new LinkedHashSet<
                        FrontierAggregate.ProvisionalHypothesis>();
        List<IContextFederation.CausalStep> causalSteps = new ArrayList<IContextFederation.CausalStep>();
        List<EvidenceInjection> injections =
                new ArrayList<EvidenceInjection>();

        if (prove(work, query, logging)) {
            return new Result(
                    true,
                    0,
                    0,
                    queryRuleId,
                    Collections.<List<String>>emptyList(),
                    observations,
                    injections,
                    provisionalHypotheses, causalSteps);
        }

        // A complete commune-native FALSE for an existential atomic query is a
        // proof of the whole opposite pass, not a tuple. Do not invent a
        // witness or reconstruct it from an empty Values collection.
        if (queryPass == QueryPass.CHECKFALSE && operation.getExecutionConnections().stream()
                .anyMatch(connection -> connection.commune() != null)) {
            Mind descriptor = Mind.ephemeralChild(work);
            FrontierDomain existential;
            try {
                descriptor.setQueryPass(QueryPass.CHECKTRUE);
                Rule positive = (Rule) descriptor.compileLine("?" + querySource.substring(1),
                        true, new LinkedList<ITerm>(localExternals));
                existential = positive == null ? null : CausalFrontierCapture.describeAtomic(descriptor, positive);
            } finally { work.discardEphemeral(descriptor); }
            if (existential != null && !existential.isGround()) {
                CausalFrontierScheduler.Result scheduled = CausalFrontierScheduler.enumerate(
                        operation, existential, localSource, localSource.isExplainQueryActive());
                FrontierAggregate aggregate = scheduled.getRootAggregate();
                observations.add(new FrontierObservation(1, existential, aggregate));
                for (CausalFrontierScheduler.Conflict one : scheduled.getConflicts())
                    observations.add(new FrontierObservation(1, one.frontier, one.aggregate));
                causalSteps.addAll(scheduled.getSteps());
                if (!scheduled.hasConflict() && aggregate.getTruth() == FrontierAggregate.Truth.FALSE) {
                    work.getSolutions().clear();
                    work.getValues().clear();
                    return new Result(true, 1, 0, queryRuleId,
                            Collections.singletonList(Collections.singletonList(existential.getPredicateName())),
                            observations, injections, provisionalHypotheses, causalSteps);
                }
            }
        }

        Set<EvidenceKey> evidence =
                new LinkedHashSet<EvidenceKey>();
        List<List<String>> frontierTrace =
                new ArrayList<List<String>>();
        int waves = 0;
        int evidenceCount = 0;

        while (waves < MAX_WAVES) {
            List<FrontierDomain> frontiers =
                    new ArrayList<FrontierDomain>(
                            work.getFrontierDomains());
            if (frontiers.isEmpty()) {
                return new Result(
                        false,
                        waves,
                        evidenceCount,
                        queryRuleId,
                        frontierTrace,
                        observations,
                        injections,
                        provisionalHypotheses, causalSteps);
            }

            List<String> predicates =
                    new ArrayList<String>();
            for (FrontierDomain frontier : frontiers) {
                predicates.add(
                        frontier.getPredicateName());
            }
            frontierTrace.add(
                    Collections.unmodifiableList(predicates));
            ++waves;

            boolean changed = false;
            for (FrontierDomain frontier : frontiers) {
                CausalFrontierScheduler.Result scheduled =
                        CausalFrontierScheduler.execute(operation, frontier, localSource, localSource.isExplainQueryActive());
                causalSteps.addAll(scheduled.getSteps());
                List<FrontierAnswer> answers = scheduled.getAnswers();
                FrontierAggregate aggregate =
                        FrontierAggregate.of(answers);
                observations.add(
                        new FrontierObservation(
                                waves,
                                frontier,
                                aggregate));
                provisionalHypotheses.addAll(
                        aggregate.getHypotheses());
                for (CausalFrontierScheduler.Conflict conflict : scheduled.getConflicts()) {
                    if (!conflict.frontier.sameSemanticQuery(frontier)) {
                        observations.add(new FrontierObservation(waves, conflict.frontier, conflict.aggregate));
                    }
                }
                if (scheduled.hasRootConflict()
                        || scheduled.hasConflict() && aggregate.getTruth() == FrontierAggregate.Truth.UNKNOWN) {
                    // A causal child contradiction cannot become a donor or a hypothesis.
                    // Preserve the root witness too when its latest reproof was withdrawn.
                    if (aggregate.getTruth() != FrontierAggregate.Truth.CONFLICT) {
                        for (CausalFrontierScheduler.Conflict conflict : scheduled.getConflicts()) {
                            if (conflict.frontier.sameSemanticQuery(frontier)) {
                                observations.add(new FrontierObservation(waves, frontier, conflict.aggregate));
                            }
                        }
                    }
                    continue;
                }

                /*
                 * Ground TRUE is a factual donor for this exact frontier
                 * polarity. FALSE, UNKNOWN and CONFLICT remain observations;
                 * the opposite proposition is handled by the separate
                 * historical FALSE query pass.
                 */
                if (frontier.isGround()
                        && aggregate.getTruth()
                                == FrontierAggregate.Truth.TRUE
                        && inject(
                                work,
                                frontier,
                                Collections.<String>emptyList(),
                                Collections.<ITerm>emptyList(),
                                evidence,
                                true,
                                queryPass, answers)) {
                    ++evidenceCount;
                    changed = true;
                    injections.add(
                            EvidenceInjection.of(
                                    frontier,
                                    Collections.<String>emptyList(),
                                    Collections.<ITerm>emptyList(),
                                    aggregate.getTrueSources()));
                }

                FrontierLiftSession.LiftResult lifted =
                        FrontierLiftSession.liftInto(
                                work,
                                operation,
                                answers);
                for (FrontierLiftSession.LiftedTuple tuple
                        : lifted.getTuples()) {
                    if (inject(
                            work,
                            frontier,
                            lifted.getVariableOrder(),
                            tuple.getValues(),
                            evidence,
                            true,
                            queryPass, answers)) {
                        ++evidenceCount;
                        changed = true;
                        injections.add(
                                EvidenceInjection.of(
                                        frontier,
                                        lifted.getVariableOrder(),
                                        tuple.getValues(),
                                        tuple.getSupports()));
                    }
                }
            }

            if (!changed) {
                return new Result(
                        false,
                        waves,
                        evidenceCount,
                        queryRuleId,
                        frontierTrace,
                        observations,
                        injections,
                        provisionalHypotheses, causalSteps);
            }

            work.setQueryPass(queryPass);
            /*
             * New foreign evidence can satisfy a premise one or more local
             * rules away from the original query. Saturate the same operation
             * Mind, then re-analyze the same compiled query Rule.
             */
            work.link(null, logging);
            if (work.analyze(query, logging)) {
                return new Result(
                        true,
                        waves,
                        evidenceCount,
                        queryRuleId,
                        frontierTrace,
                        observations,
                        injections,
                        provisionalHypotheses, causalSteps);
            }
            if (query.getId() != queryRuleId) {
                throw new AssertionError(
                        "Federated continuation replaced the compiled query Rule");
            }
        }

        throw new IllegalStateException(
                "Federated continuation exceeded "
                        + MAX_WAVES + " waves");
    }

    private static Result enumerate(Mind work, Mind localSource, OperationSnapshot operation,
            Rule query, FrontierDomain frontier, QueryPass pass, boolean logging) throws Exception {
        CausalFrontierScheduler.Result scheduled = CausalFrontierScheduler.enumerate(
                operation, frontier, localSource, localSource.isExplainQueryActive());
        List<FrontierObservation> observations = new ArrayList<FrontierObservation>();
        observations.add(new FrontierObservation(1, frontier, FrontierAggregate.of(scheduled.getAnswers())));
        for (CausalFrontierScheduler.Conflict conflict : scheduled.getConflicts())
            observations.add(new FrontierObservation(1, conflict.frontier, conflict.aggregate));
        FrontierLiftSession.LiftResult lifted = FrontierLiftSession.liftEnumerationInto(work, operation, scheduled.getAnswers());
        Set<EvidenceKey> evidence = new LinkedHashSet<EvidenceKey>();
        List<EvidenceInjection> injections = new ArrayList<EvidenceInjection>();
        List<FrontierDomain> allowed = new ArrayList<FrontierDomain>();
        for (FrontierLiftSession.LiftedTuple tuple : lifted.getTuples()) {
            List<SemanticTermSnapshot> values = new ArrayList<SemanticTermSnapshot>();
            for (ITerm value : tuple.getValues()) values.add(SemanticTermSnapshot.capture(value));
            allowed.add(frontier.specialize(lifted.getVariableOrder(), values));
            if (inject(work, frontier, lifted.getVariableOrder(), tuple.getValues(), evidence, true, pass, scheduled.getAnswers()))
                injections.add(EvidenceInjection.of(frontier, lifted.getVariableOrder(), tuple.getValues(), tuple.getSupports()));
        }
        // All donors are now known. The existing native query proves the complete set once.
        work.setQueryPass(pass);
        work.link(null, logging);
        boolean proven = prove(work, query, logging);
        filterEnumeration(work, frontier, allowed);
        boolean resolved = proven && !work.getValues().isEmpty();
        Result result = new Result(resolved, 1, injections.size(), query.getId(),
                Collections.singletonList(Collections.singletonList(frontier.getPredicateName())),
                observations, injections, new LinkedHashSet<FrontierAggregate.ProvisionalHypothesis>(), scheduled.getSteps(), true);
        return result;
    }

    private static boolean permitted(FrontierDomain specialization, List<FrontierDomain> allowed) {
        if (specialization == null) return false;
        for (FrontierDomain row : allowed) if (row.sameSemanticQuery(specialization)) return true;
        return false;
    }

    private static boolean permittedSolution(FrontierDomain solution, List<FrontierDomain> allowed) {
        if (!solution.isGround()) return false;
        // Native solution Domains retain the proof's opposite polarity.
        List<SemanticTermSnapshot> actual = solution.semanticArguments(
                Collections.<String>emptyList(), Collections.<SemanticTermSnapshot>emptyList());
        for (FrontierDomain row : allowed) {
            List<SemanticTermSnapshot> expected = row.semanticArguments(
                    Collections.<String>emptyList(), Collections.<SemanticTermSnapshot>emptyList());
            if (actual.size() != expected.size()) continue;
            boolean equal = true;
            for (int i = 0; i < actual.size(); i++)
                if (!actual.get(i).semanticallyEquals(expected.get(i))) { equal = false; break; }
            if (equal) return true;
        }
        return false;
    }

    private static void filterEnumeration(Mind work, FrontierDomain frontier,
            List<FrontierDomain> allowed) throws Exception {
        List<Boolean> keep = new ArrayList<Boolean>();
        for (Map<String, ITerm> row : work.getValues()) {
            List<String> names = new ArrayList<String>();
            for (FrontierDomain.VariableState variable : frontier.getVariables())
                if (!variable.isBound()) names.add(variable.getName());
            List<SemanticTermSnapshot> values = new ArrayList<SemanticTermSnapshot>();
            for (String name : names) values.add(SemanticTermSnapshot.capture(row.get(name)));
            keep.add(permitted(frontier.specialize(names, values), allowed));
        }
        Iterator<ArgumentsList> rows = ((ValuesStore) work.getValues()).getRoot().iterator();
        for (Boolean accepted : keep) { rows.next(); if (!accepted) rows.remove(); }
        SolutionsStore solutions = (SolutionsStore) work.getSolutions();
        if (!solutions.isEmpty()) {
            Iterator<IRule> iterator = solutions.getRoot().iterator();
            while (iterator.hasNext()) {
                FrontierDomain solution = CausalFrontierCapture.describeAtomic(work, (Rule) iterator.next());
                if (solution != null && solution.getPredicateName().equals(frontier.getPredicateName())
                        && !permittedSolution(solution, allowed)) iterator.remove();
            }
        }
    }

    private static boolean prove(
            Mind work,
            Rule query,
            boolean logging) throws Exception {
        boolean result = work.analyze(query, logging);
        if (!result) {
            work.link(query, logging);
            result = work.analyze(query, logging);
        }
        return result;
    }

    private static boolean inject(
            Mind work,
            FrontierDomain frontier,
            List<String> variableOrder,
            List<ITerm> values,
            Set<EvidenceKey> evidence,
            boolean truth,
            QueryPass queryPass, List<FrontierAnswer> answers) throws Exception {
        Queue<ITerm> arguments =
                frontier.evidenceArguments(
                        work,
                        variableOrder,
                        values);
        String evidenceSource =
                frontier.getEvidenceSource(truth);
        EvidenceKey key =
                EvidenceKey.of(
                        evidenceSource,
                        arguments);
        if (!evidence.add(key)) {
            return false;
        }

        work.setQueryPass(QueryPass.ACCEPT);
        Rule assertion = (Rule) work.compileLine(
                evidenceSource,
                false,
                new LinkedList<ITerm>(arguments));
        work.setQueryPass(queryPass);

        if (assertion != null) {
            String fact = org.kanger.ContextProofProjection.factKey(assertion, work);
            for (FrontierAnswer answer : answers) if (answer.getTruth() == FrontierAnswer.Truth.TRUE)
                work.addContextProofs(fact, answer.getProofs().get(fact));
        }
        if (assertion == null || assertion.isSecond()) {
            return false;
        }

        /*
         * Do not analyze the donor assertion as an independent ACCEPT. The
         * operation Mind already contains the opposite-polarity generated
         * demand, so ordinary ACCEPT collision semantics would correctly see
         * that pair as a collision. In federation that pair means "the answer
         * arrived". Retain the donor Rule and let the original compiled query
         * Rule consume it on the next link/analyze continuation.
         */
        return true;
    }

    static final class Result {

        private final List<IContextFederation.CausalStep> causalSteps;
        private final boolean resolved;
        private final boolean enumerated;
        private final int waves;
        private final int evidenceCount;
        private final long queryRuleId;
        private final List<List<String>> frontierTrace;
        private final List<FrontierObservation> observations;
        private final List<EvidenceInjection> evidenceInjections;
        private final List<FrontierAggregate.ProvisionalHypothesis>
                provisionalHypotheses;

        private Result(
                boolean resolved,
                int waves,
                int evidenceCount,
                long queryRuleId,
                List<List<String>> frontierTrace,
                List<FrontierObservation> observations,
                List<EvidenceInjection> evidenceInjections,
                Set<FrontierAggregate.ProvisionalHypothesis>
                        provisionalHypotheses, List<IContextFederation.CausalStep> causalSteps) {
            this(resolved, waves, evidenceCount, queryRuleId, frontierTrace, observations,
                    evidenceInjections, provisionalHypotheses, causalSteps, false);
        }

        private Result(boolean resolved, int waves, int evidenceCount, long queryRuleId,
                List<List<String>> frontierTrace, List<FrontierObservation> observations,
                List<EvidenceInjection> evidenceInjections,
                Set<FrontierAggregate.ProvisionalHypothesis> provisionalHypotheses,
                List<IContextFederation.CausalStep> causalSteps, boolean enumerated) {
            this.enumerated = enumerated;
            this.causalSteps = Collections.unmodifiableList(new ArrayList<IContextFederation.CausalStep>(causalSteps));
            this.resolved = resolved;
            this.waves = waves;
            this.evidenceCount = evidenceCount;
            this.queryRuleId = queryRuleId;

            List<List<String>> copied =
                    new ArrayList<List<String>>();
            for (List<String> wave : frontierTrace) {
                copied.add(Collections.unmodifiableList(
                        new ArrayList<String>(wave)));
            }
            this.frontierTrace =
                    Collections.unmodifiableList(copied);
            this.observations =
                    Collections.unmodifiableList(
                            new ArrayList<FrontierObservation>(
                                    observations));
            this.evidenceInjections =
                    Collections.unmodifiableList(
                            new ArrayList<EvidenceInjection>(
                                    evidenceInjections));
            boolean conflict = false;
            for (FrontierObservation observation : observations) {
                if (observation.getAggregate().getTruth() == FrontierAggregate.Truth.CONFLICT) {
                    conflict = true;
                    break;
                }
            }
            this.provisionalHypotheses = resolved || conflict
                    ? Collections.<FrontierAggregate.ProvisionalHypothesis>emptyList()
                    : Collections.unmodifiableList(new ArrayList<FrontierAggregate.ProvisionalHypothesis>(provisionalHypotheses));
        }

        List<IContextFederation.CausalStep> getCausalSteps() { return causalSteps; }

        boolean isResolved() {
            return resolved;
        }

        int getWaves() {
            return waves;
        }

        int getEvidenceCount() {
            return evidenceCount;
        }

        long getQueryRuleId() {
            return queryRuleId;
        }

        List<List<String>> getFrontierTrace() {
            return frontierTrace;
        }

        List<FrontierObservation> getObservations() {
            return observations;
        }

        List<EvidenceInjection> getEvidenceInjections() {
            return evidenceInjections;
        }

        List<FrontierAggregate.ProvisionalHypothesis>
                getProvisionalHypotheses() {
            return provisionalHypotheses;
        }

        boolean hasConflict() {
            if (resolved) return false;
            for (FrontierObservation observation : observations) {
                if (observation.getAggregate().getTruth()
                        == FrontierAggregate.Truth.CONFLICT) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class FrontierObservation {

        private final int wave;
        private final String predicateName;
        private final String querySource;
        private final FrontierAggregate aggregate;

        private FrontierObservation(
                int wave,
                FrontierDomain frontier,
                FrontierAggregate aggregate) {
            this.wave = wave;
            this.predicateName = frontier.getPredicateName();
            this.querySource = frontier.getDiagnosticSource();
            this.aggregate = aggregate;
        }

        int getWave() {
            return wave;
        }

        String getPredicateName() {
            return predicateName;
        }

        String getQuerySource() {
            return querySource;
        }

        FrontierAggregate getAggregate() {
            return aggregate;
        }
    }

    static final class EvidenceInjection {

        private final String statement;
        private final Map<String, String> substitutions;
        private final Set<RevisionRef> supports;

        private EvidenceInjection(
                String statement,
                Map<String, String> substitutions,
                Set<RevisionRef> supports) {
            this.statement = statement;
            this.substitutions =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<String, String>(
                                    substitutions));
            this.supports =
                    Collections.unmodifiableSet(
                            new LinkedHashSet<RevisionRef>(
                                    supports));
        }

        static EvidenceInjection of(
                FrontierDomain frontier,
                List<String> variableOrder,
                List<ITerm> values,
                Set<RevisionRef> supports) {
            LinkedHashMap<String, String> substitutions =
                    new LinkedHashMap<String, String>();
            for (int i = 0; i < variableOrder.size(); ++i) {
                substitutions.put(
                        variableOrder.get(i),
                        values.get(i).toString());
            }
            return new EvidenceInjection(
                    frontier.getDiagnosticSource(),
                    substitutions,
                    supports);
        }

        String getStatement() {
            return statement;
        }

        Map<String, String> getSubstitutions() {
            return substitutions;
        }

        Set<RevisionRef> getSupports() {
            return supports;
        }
    }

    private static final class EvidenceKey {

        private final String source;
        private final List<Long> argumentIds;

        private EvidenceKey(String source,
                            List<Long> argumentIds) {
            this.source = source;
            this.argumentIds = argumentIds;
        }

        static EvidenceKey of(
                String source,
                Queue<ITerm> arguments) {
            List<Long> ids =
                    new ArrayList<Long>();
            for (ITerm argument : arguments) {
                ids.add(Long.valueOf(argument.getId()));
            }
            return new EvidenceKey(
                    source,
                    Collections.unmodifiableList(ids));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EvidenceKey)) {
                return false;
            }
            EvidenceKey key = (EvidenceKey) other;
            return source.equals(key.source)
                    && argumentIds.equals(key.argumentIds);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode()
                    + argumentIds.hashCode();
        }
    }
}
