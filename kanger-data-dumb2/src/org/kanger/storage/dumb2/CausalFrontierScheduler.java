/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDemand;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.interfaces.internal.IContextFederation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** X-owned causal graph. Every target call is an independent local-only proof. */
final class CausalFrontierScheduler {
    private static final int MAX_WAVES = 128;

    private CausalFrontierScheduler() { }

    static Result execute(OperationSnapshot operation, FrontierDomain frontier) throws Exception {
        return execute(operation, frontier, null);
    }

    static Result execute(OperationSnapshot operation, FrontierDomain frontier, Mind source) throws Exception {
        return execute(operation, frontier, source, false);
    }

    static Result execute(OperationSnapshot operation, FrontierDomain frontier, Mind source, boolean trace) throws Exception {
        return execute(operation, frontier, source, trace, false);
    }

    static Result enumerate(OperationSnapshot operation, FrontierDomain frontier, Mind source, boolean trace) throws Exception {
        return execute(operation, frontier, source, trace, true);
    }

    private static Result execute(OperationSnapshot operation, FrontierDomain frontier,
            Mind source, boolean trace, final boolean includeSourceAtRoot) throws Exception {
        List<IContextFederation.CausalStep> steps = new ArrayList<IContextFederation.CausalStep>();
        List<ContextConnection> connections = operation.getExecutionConnections();
        List<Node> nodes = new ArrayList<Node>();
        Node root = node(nodes, frontier, connections, includeSourceAtRoot ? source : null, operation.getSourceRef());
        int calls = 0;
        int waves = 0;
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1,
                Math.min(connections.size(), Runtime.getRuntime().availableProcessors())));
        try {
            while (true) {
                // A wave observes only complete latest batches from the previous wave.
                Map<Node, List<SuppliedEvidence>> facts = new LinkedHashMap<Node, List<SuppliedEvidence>>();
                for (Node one : nodes) {
                    facts.put(one, one.complete() && one.conflict == null ? SuppliedEvidence.fromAnswers(one.answers())
                            : Collections.<SuppliedEvidence>emptyList());
                }
                List<Call> pending = new ArrayList<Call>();
                for (Node one : nodes) {
                    for (Target target : one.targets) {
                        List<SuppliedEvidence> relevant = new ArrayList<SuppliedEvidence>();
                        for (Edge edge : target.edges) {
                            for (SuppliedEvidence fact : facts.get(edge.child)) {
                                SuppliedEvidence.add(relevant, fact);
                            }
                        }
                        List<FrontierDemand> demands = new ArrayList<FrontierDemand>();
                        for (Edge edge : target.edges) {
                            demands.add(edge.demand);
                        }
                        FrontierRequest request = FrontierRequest.forDemands(one.invocation,
                                target.ref, demands, relevant);
                        FrontierExecutionState state = request.executionState(target.ref);
                        if (target.answer != null && target.answer.getExecutionState().equals(state)) {
                            continue;
                        }
                        FrontierAnswer cached = target.cache.get(state);
                        if (cached != null) {
                            pending.add(new Call(target, cached));
                        } else if (target.source != null) {
                            // X is live operation state: never access its shared Mind from workers.
                            FrontierAnswer answer = LocalFrontierExecutor.execute(target.source, target.ref, request, includeSourceAtRoot);
                            pending.add(new Call(target, answer));
                            ++calls;
                        } else {
                            final ContextConnection connection = target.connection;
                            final FrontierRequest execution = request;
                            Future<FrontierAnswer> future = executor.submit(new Callable<FrontierAnswer>() {
                                @Override public FrontierAnswer call() throws Exception {
                                    return LocalFrontierExecutor.execute(connection, execution, includeSourceAtRoot);
                                }
                            });
                            pending.add(new Call(target, future));
                            ++calls;
                        }
                    }
                }
                if (pending.isEmpty()) {
                    return new Result(root.answers(), nodes, waves, calls, steps);
                }
                if (++waves > MAX_WAVES) {
                    throw new IllegalStateException("Causal frontier did not reach a fixed point in " + MAX_WAVES + " waves");
                }
                for (Call call : pending) {
                    FrontierAnswer answer = call.answer();
                    if (trace) steps.add(step(waves, root.invocation.getFrontier().getDiagnosticSource(), answer));
                    call.target.answer = answer;
                    call.target.cache.put(answer.getExecutionState(), answer);
                    for (FrontierDemand demand : answer.getUnresolvedFrontiers()) {
                        Node child = node(nodes, demand.getQuery(), connections, source, operation.getSourceRef());
                        boolean present = false;
                        for (Edge edge : call.target.edges) {
                            if (edge.child == child && edge.demand.semanticallyEquivalent(demand)) {
                                present = true;
                                break;
                            }
                        }
                        if (!present) {
                            call.target.edges.add(new Edge(child, demand));
                        }
                    }
                }
                // Ground contradictions remain known. A free frontier's mixed supports
                // must instead be checked for each concrete substitution by native proof.
                for (Node one : new ArrayList<Node>(nodes)) {
                    if (!one.complete()) continue;
                    FrontierAggregate aggregate=FrontierAggregate.of(one.rawAnswers());
                    if (one.invocation.getFrontier().isGround()) {
                        if (one.conflict==null && aggregate.getTruth()==FrontierAggregate.Truth.CONFLICT)
                            one.conflict=aggregate;
                        continue;
                    }
                    if (aggregate.getTruth()==FrontierAggregate.Truth.CONFLICT) one.validateRows=true;
                    if (!one.validateRows) continue;
                    for (FrontierAnswer answer : one.rawAnswers()) {
                        if (answer.getTruth()!=FrontierAnswer.Truth.TRUE) continue;
                        for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
                            List<org.kanger.SemanticTermSnapshot> values=new ArrayList<org.kanger.SemanticTermSnapshot>();
                            for (FrontierAnswer.ValueRef value : row) values.add(value.getSemantic());
                            FrontierDomain specialized=one.invocation.getFrontier().specialize(answer.getVariableOrder(),values);
                            Node validation=node(nodes,specialized,connections,source,operation.getSourceRef());
                            if (!one.rowValidations.contains(validation)) one.rowValidations.add(validation);
                        }
                    }
                }

            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static IContextFederation.CausalStep step(int wave, String rootQuery, FrontierAnswer answer) {
        List<IContextFederation.ValueRow> values = new ArrayList<IContextFederation.ValueRow>();
        if (answer.getTruth() == FrontierAnswer.Truth.TRUE) {
            for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
                Map<String, String> bindings = new LinkedHashMap<String, String>();
                for (int i = 0; i < row.size(); ++i) bindings.put(answer.getVariableOrder().get(i), row.get(i).getRendered());
                values.add(new IContextFederation.ValueRow(bindings));
            }
        }
        List<IContextFederation.EvidenceInjection> supplied = new ArrayList<IContextFederation.EvidenceInjection>();
        for (SuppliedEvidence fact : answer.getRequest().getEvidence()) {
            List<IContextFederation.Revision> supports = new ArrayList<IContextFederation.Revision>();
            for (RevisionRef ref : fact.getSupports()) supports.add(revision(ref));
            supplied.add(new IContextFederation.EvidenceInjection(fact.diagnosticStatement(),
                    Collections.<String, String>emptyMap(), supports));
        }
        List<IContextFederation.CausalDemand> demands = new ArrayList<IContextFederation.CausalDemand>();
        for (FrontierDemand demand : answer.getUnresolvedFrontiers()) {
            List<Integer> positions = new ArrayList<Integer>();
            for (int i = 0; i < demand.getParent().getArgumentCount(); ++i) {
                positions.add(demand.getParentProjection().getChildPosition(i));
            }
            demands.add(new IContextFederation.CausalDemand(demand.getParent().getDiagnosticSource(),
                    demand.getQuery().getDiagnosticSource(), positions));
        }
        IContextFederation.FrontierTruth truth = answer.getTruth() == FrontierAnswer.Truth.NULL
                ? IContextFederation.FrontierTruth.UNKNOWN
                : IContextFederation.FrontierTruth.valueOf(answer.getTruth().name());
        return new IContextFederation.CausalStep(wave, rootQuery, answer.getInvocation().getFrontier().getDiagnosticSource(),
                answer.sourceDescription(), truth, values, supplied, demands);
    }

    private static IContextFederation.Revision revision(RevisionRef ref) {
        return new IContextFederation.Revision(ref.getContextId(), ref.getRevision());
    }

    private static Node node(List<Node> nodes, FrontierDomain frontier, List<ContextConnection> connections, Mind source, RevisionRef sourceRef) {
        for (Node one : nodes) {
            if (one.invocation.getFrontier().sameSemanticQuery(frontier)) {
                return one;
            }
        }
        Node created = new Node(frontier, connections, source, sourceRef);
        nodes.add(created);
        return created;
    }

    private static final class Node {
        final FrontierInvocation invocation;
        FrontierAggregate conflict;
        boolean validateRows;
        final List<Node> rowValidations=new ArrayList<Node>();
        final List<Target> targets = new ArrayList<Target>();
        Node(FrontierDomain frontier, List<ContextConnection> connections, Mind source, RevisionRef sourceRef) {
            invocation = FrontierInvocation.create(frontier);
            validateRows = !frontier.isGround();
            if (source != null) targets.add(new Target(source, sourceRef));
            for (ContextConnection connection : connections) {
                targets.add(new Target(connection));
            }
        }
        boolean complete() {
            for (Target target : targets) {
                if (target.answer == null) return false;
            }
            return true;
        }
        List<FrontierAnswer> answers() {
            if (!validateRows) return rawAnswers();
            List<FrontierAnswer> filtered=new ArrayList<FrontierAnswer>();
            for (FrontierAnswer answer : rawAnswers()) {
                List<List<FrontierAnswer.ValueRef>> rows=new ArrayList<List<FrontierAnswer.ValueRef>>();
                if (answer.getTruth()==FrontierAnswer.Truth.TRUE) {
                    for (List<FrontierAnswer.ValueRef> row : answer.getValues()) {
                        List<org.kanger.SemanticTermSnapshot> values=new ArrayList<org.kanger.SemanticTermSnapshot>();
                        for (FrontierAnswer.ValueRef value : row) values.add(value.getSemantic());
                        FrontierDomain specialized=invocation.getFrontier().specialize(answer.getVariableOrder(),values);
                        for (Node validation : rowValidations) {
                            if (validation.complete() && validation.conflict==null
                                    && validation.invocation.getFrontier().sameSemanticQuery(specialized)
                                    && FrontierAggregate.of(validation.rawAnswers()).getTruth()==FrontierAggregate.Truth.TRUE) {
                                rows.add(row); break;
                            }
                        }
                    }
                }
                filtered.add(new FrontierAnswer(answer.getSource(),answer.getRequest(),
                        rows.isEmpty() ? FrontierAnswer.Truth.NULL : FrontierAnswer.Truth.TRUE,
                        answer.getVariableOrder(),rows,Collections.<String>emptyList(),
                        rows.isEmpty() ? answer.getUnresolvedFrontiers() : Collections.<FrontierDemand>emptyList(), false, answer.getProofs()));
            }
            return filtered;
        }
        List<FrontierAnswer> rawAnswers() {
            List<FrontierAnswer> answers = new ArrayList<FrontierAnswer>();
            for (Target target : targets) {
                if (target.answer != null) answers.add(target.answer);
            }
            return answers;
        }
    }

    private static final class Target {
        final ContextConnection connection;
        final Mind source;
        final RevisionRef ref;
        final List<Edge> edges = new ArrayList<Edge>();
        final Map<FrontierExecutionState, FrontierAnswer> cache = new LinkedHashMap<FrontierExecutionState, FrontierAnswer>();
        FrontierAnswer answer;
        Target(ContextConnection connection) {
            this.connection = connection; this.source = null; this.ref = connection.getTarget();
        }
        Target(Mind source, RevisionRef ref) {
            this.connection = null; this.source = source; this.ref = ref;
        }
    }

    private static final class Edge {
        final Node child;
        final FrontierDemand demand;
        Edge(Node child, FrontierDemand demand) { this.child = child; this.demand = demand; }
    }

    private static final class Call {
        final Target target;
        final Future<FrontierAnswer> future;
        final FrontierAnswer cached;
        Call(Target target, Future<FrontierAnswer> future) {
            this.target = target; this.future = future; this.cached = null;
        }
        Call(Target target, FrontierAnswer cached) {
            this.target = target; this.cached = cached; this.future = null;
        }
        FrontierAnswer answer() throws Exception {
            if (cached != null) return cached;
            try {
                return future.get();
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new RuntimeException(cause);
            }
        }
    }

    static final class Conflict {
        final FrontierDomain frontier;
        final FrontierAggregate aggregate;
        Conflict(FrontierDomain frontier, FrontierAggregate aggregate) {
            this.frontier = frontier; this.aggregate = aggregate;
        }
    }

    static final class Result {
        private final List<FrontierAnswer> answers;
        private final int nodes;
        private final List<Conflict> conflicts;
        private final boolean rootConflict;
        private final int waves;
        private final int calls;
        private final List<IContextFederation.CausalStep> steps;
        Result(List<FrontierAnswer> answers, List<Node> nodes, int waves, int calls, List<IContextFederation.CausalStep> steps) {
            this.steps = Collections.unmodifiableList(new ArrayList<IContextFederation.CausalStep>(steps));
            this.answers = Collections.unmodifiableList(new ArrayList<FrontierAnswer>(answers));
            this.rootConflict = nodes.get(0).conflict != null;
            this.nodes = nodes.size(); this.waves = waves; this.calls = calls;
            List<Conflict> observed = new ArrayList<Conflict>();
            for (Node node : nodes) {
                if (node.conflict != null) observed.add(new Conflict(node.invocation.getFrontier(), node.conflict));
            }
            this.conflicts = Collections.unmodifiableList(observed);
        }
        List<IContextFederation.CausalStep> getSteps() { return steps; }
        boolean hasRootConflict() { return rootConflict; }
        boolean hasConflict() { return !conflicts.isEmpty(); }
        List<Conflict> getConflicts() { return conflicts; }
        List<FrontierAnswer> getAnswers() { return answers; }
        int getNodeCount() { return nodes; }
        int getWaves() { return waves; }
        int getCalls() { return calls; }
    }
}
