/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.CausalFrontierCapture;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Term;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Executes one frontier query against one independent revision or a prepared
 * trust commune. Commune knowledge is local to X, with all exact pins retained.
 *
 * <p>This class intentionally attaches the target snapshot directly and never
 * opens that target's own ConnectionVector. Therefore federation cannot recurse:
 * X may ask A, but this execution cannot cause A to ask B.</p>
 */
final class LocalFrontierExecutor {

    private LocalFrontierExecutor() {
    }

    static FrontierAnswer execute(
            ContextConnection connection,
            FrontierDomain frontier) throws Exception {
        return execute(connection, FrontierInvocation.create(frontier));
    }

    static FrontierAnswer execute(
            ContextConnection connection,
            FrontierInvocation invocation) throws Exception {
        return execute(connection, FrontierRequest.initial(invocation));
    }

    static FrontierAnswer execute(
            ContextConnection connection,
            FrontierRequest request) throws Exception {
        return execute(connection, request, false);
    }

    static FrontierAnswer execute(ContextConnection connection, FrontierRequest request,
            boolean enumerate) throws Exception {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }

        RevisionRef target = connection.getTarget();
        if (connection.commune() != null)
            return execute(connection.commune().mind(), target, request, enumerate);
        if (!connection.getInitialization().isEmpty())
            return execute(connection.layer(), target, request, enumerate);
        return execute(
                connection.getTargetLocation(),
                target,
                request, enumerate);
    }

    static FrontierAnswer execute(
            Path targetLocation,
            RevisionRef target,
            FrontierDomain frontier) throws Exception {
        return execute(targetLocation, target, FrontierInvocation.create(frontier));
    }

    static FrontierAnswer execute(
            Path targetLocation,
            RevisionRef target,
            FrontierInvocation invocation) throws Exception {
        return execute(targetLocation, target, FrontierRequest.initial(invocation));
    }

    static FrontierAnswer execute(
            Path targetLocation,
            RevisionRef target,
            FrontierRequest request) throws Exception {
        return execute(targetLocation, target, request, false);
    }

    private static FrontierAnswer execute(Path targetLocation, RevisionRef target,
            FrontierRequest request, boolean enumerate) throws Exception {
        if (request == null) {
            throw new NullPointerException("request");
        }
        request.executionState(target);
        User user = new User();
        String logicalName =
                "frontier-" + target.getContextId().toString();

        ContextSnapshotData data =
                new ContextSnapshotData(
                        targetLocation,
                        logicalName,
                        Long.valueOf(target.getRevision()));
        data.init(user);

        Mind root = new Mind(user);
        user.setCurrentMind(root);
        root = (Mind) root.useStorage(logicalName);
        user.setCurrentMind(root);

        if (!target.getContextId().equals(data.getContextId())
                || target.getRevision() != data.getRevision()) {
            root = (Mind) root.closeStorage();
            user.setCurrentMind(root);
            throw new IllegalStateException(
                    "Frontier target changed during exact-revision attach: "
                            + target);
        }

        try {
            return execute(root, target, request, enumerate);
        } finally {
            root = (Mind) root.closeStorage();
            user.setCurrentMind(root);
        }
    }

    /** Local X probe also uses native inference in a discarded child, never its federation provider. */
    static FrontierAnswer execute(Mind root, RevisionRef target, FrontierRequest request) throws Exception {
        return execute(root, target, request, false);
    }

    static FrontierAnswer execute(Mind root, RevisionRef target, FrontierRequest request,
            boolean enumerate) throws Exception {
        request.executionState(target);
        FrontierDomain frontier = request.getInvocation().getFrontier();
        Mind mind = Mind.ephemeralChild(root);
        mind.clearContextProofs();
        try {
            for (SuppliedEvidence fact : request.getEvidence()) {
                fact.materialize(mind);
            }
            CausalFrontierCapture.Result result = enumerate && !frontier.isGround()
                    ? CausalFrontierCapture.enumerate(mind, frontier.getQuerySource(), frontier.projectFixedArguments(mind), false)
                    : CausalFrontierCapture.query(mind, frontier.getQuerySource(), frontier.projectFixedArguments(mind), false);

            List<String> order = new ArrayList<String>();
            for (FrontierDomain.VariableState variable
                    : frontier.getVariables()) {
                if (!variable.isBound()) {
                    order.add(variable.getName());
                }
            }

            List<List<FrontierAnswer.ValueRef>> rows =
                    new ArrayList<List<FrontierAnswer.ValueRef>>();
            for (Map<String, ITerm> row : mind.getValues()) {
                ArrayList<FrontierAnswer.ValueRef> tuple =
                        new ArrayList<FrontierAnswer.ValueRef>();
                boolean complete = true;
                for (String variable : order) {
                    ITerm value = row.get(variable);
                    if (value == null) {
                        complete = false;
                        break;
                    }
                    Term term = (Term) value;
                    tuple.add(
                            FrontierAnswer.ValueRef.capture(term));
                }
                if (complete) {
                    rows.add(tuple);
                }
            }

            List<String> hypotheses =
                    new ArrayList<String>();
            for (IHypothesis hypothesis : mind.getHypothesis()) {
                hypotheses.add(
                        ((Hypothesis) hypothesis).toString(mind));
            }

            Map<String,List<org.kanger.interfaces.internal.IContextFederation.ProofCause>> proofs =
                    Boolean.TRUE.equals(result.getTruth()) ? org.kanger.ContextProofProjection.captureFacts(mind,
                            new org.kanger.interfaces.internal.IContextFederation.Revision(target.getContextId(), target.getRevision()))
                            : new java.util.LinkedHashMap<String,List<org.kanger.interfaces.internal.IContextFederation.ProofCause>>();
            java.util.Set<String> provenFacts = new java.util.HashSet<>();
            if (frontier.isGround()) {
                provenFacts.add(org.kanger.ContextProofProjection.factKey(frontier.getPredicateName(), !frontier.isNegated(),
                        frontier.semanticArguments(Collections.<String>emptyList(), Collections.<org.kanger.SemanticTermSnapshot>emptyList())));
            } else for (List<FrontierAnswer.ValueRef> row : rows) {
                List<org.kanger.SemanticTermSnapshot> arguments = new ArrayList<>();
                for (FrontierAnswer.ValueRef value : row) arguments.add(value.getSemantic());
                provenFacts.add(org.kanger.ContextProofProjection.factKey(frontier.getPredicateName(), !frontier.isNegated(),
                        frontier.semanticArguments(order, arguments)));
            }
            proofs.keySet().retainAll(provenFacts);
            return new FrontierAnswer(
                    target,
                    request,
                    FrontierAnswer.truth(result.getTruth()),
                    order,
                    rows,
                    hypotheses,
                    result.getDemands(), enumerate, proofs).fromCommune(mind.getCommuneName(), mind.getCommuneMembers());
        } finally {
            root.discardEphemeral(mind);
        }
    }
}
