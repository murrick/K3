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
import java.util.List;
import java.util.Map;

/**
 * Executes one frontier query against exactly one foreign Context revision.
 *
 * <p>This class intentionally attaches the target snapshot directly and never
 * opens that target's ConnectionVector. Therefore federation cannot recurse:
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
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (request == null) {
            throw new NullPointerException("request");
        }

        RevisionRef target = connection.getTarget();
        return execute(
                connection.getTargetLocation(),
                target,
                request);
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
        if (request == null) {
            throw new NullPointerException("request");
        }
        request.executionState(target);
        FrontierInvocation invocation = request.getInvocation();
        FrontierDomain frontier = invocation.getFrontier();
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

        Mind mind = Mind.ephemeralChild(root);
        try {
            for (SuppliedEvidence fact : request.getEvidence()) {
                fact.materialize(mind);
            }
            CausalFrontierCapture.Result result = CausalFrontierCapture.query(mind,
                    frontier.getQuerySource(),
                    frontier.projectFixedArguments(mind),
                    false);

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

            return new FrontierAnswer(
                    target,
                    request,
                    FrontierAnswer.truth(result.getTruth()),
                    order,
                    rows,
                    hypotheses,
                    result.getDemands());
        } finally {
            mind.getSolutions().clear();
            mind.getValues().clear();
            root.release(mind);
            root = (Mind) root.closeStorage();
            user.setCurrentMind(root);
        }
    }
}
