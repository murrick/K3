/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzConditionalResult.*;
import org.kanger.DmzStoredRetractionTransaction.*;

/** Exercises the provider-facing snapshot after native query/choice rollback. */
public final class DmzConditionalResultContractRunner {
    private static int checks;
    static void verify(ConditionalQueryResult original) {
        DmzConditionalResult result = original.toContextResult();
        require(result.disposition.name().equals(original.disposition.name())
                && result.retryDiscovery == original.retryDiscovery && result.restartRequired == original.restartRequired
                && result.candidate.equals(original.candidate) && result.queryStatement.equals(original.queryStatement)
                && result.source == original.source && result.sourceRule == original.sourceRule
                && result.traversalWork == original.traversalWork && result.workLimit == original.workLimit
                && result.maximumDepth == original.maximumDepth && result.maximumNodes == original.maximumNodes,
                "outer metadata");
        answer(result.direct, original.direct);
        packet(result.authorityConflict, original.authorityConflict);
        if (original.family == null) require(result.family == null, "absent family");
        else {
            Family family = result.family;
            ReplayTraversalResult old = original.family;
            require(family.outcome.name().equals(old.outcome.name()) && family.policy.name().equals(old.policy.name())
                    && family.traversalWork == old.traversalWork && family.generatedNodes == old.generatedNodes
                    && family.maximumDepth == old.maximumDepth && family.maximumNodes == old.maximumNodes
                    && family.workLimit == old.workLimit, "inner metadata retains zero allowance");
            opinions(family.opinions, old.opinions); opinions(family.declined, old.declined);
            require(family.frontier.size() == old.frontier.size(), "frontier size");
            for (int i = 0; i < family.frontier.size(); ++i) {
                Frontier frontier = family.frontier.get(i); ReplayFrontier before = old.frontier.get(i);
                require(frontier.reason.name().equals(before.reason.name())
                        && frontier.recognizedInTraversal == before.recognizedInTraversal, "frontier stop/recognition");
                path(frontier.path, before.path);
            }
            if (old.unstartedRoot == null) require(family.unstartedRoot == null, "absent root");
            else {
                RootFrontier root = family.unstartedRoot;
                require(root.conflictIndex == old.unstartedRoot.conflictIndex
                        && root.completedBranches == old.unstartedRoot.completedBranches
                        && root.reason.name().equals(old.unstartedRoot.reason.name()), "unstarted root");
                packet(root.packet, old.unstartedRoot.evidence);
            }
        }
        immutableGraph(result);
    }
    private static void opinions(List<Opinion> actual, List<ReplayOpinion> expected) {
        require(actual.size() == expected.size(), "opinions never merged by equal answers");
        for (int i = 0; i < actual.size(); ++i) {
            path(actual.get(i).path, expected.get(i).path);
            answer(actual.get(i).answer, expected.get(i).application.query);
        }
    }
    private static void answer(Answer actual, QueryResult expected) {
        if (expected == null) { require(actual == null, "absent answer differs from unknown Boolean"); return; }
        require(actual != null && actual.candidateMode.name().equals(expected.candidateMode.name())
                && java.util.Objects.equals(actual.value, expected.value) && actual.hypotheses.equals(expected.hypotheses)
                && actual.hypothesisAssertions.equals(expected.hypothesisAssertions), "diagnostic query snapshot");
    }
    private static void ground(Ground actual, TerminalSupportCapture.Ground expected) {
        require(actual.predicate.equals(expected.predicate) && actual.sign == expected.sign
                && actual.argument.equals(new java.math.BigDecimal(expected.arguments.get(0).materialize().getValue().toString()).toBigIntegerExact())
                && actual.argumentType == expected.arguments.get(0).getType(), "exact signed integer ground");
    }
    private static void sources(List<Source> actual, List<DmzReplayProvenance.Binding> expected) {
        require(actual.size() == expected.size(), "source occurrence count");
        for (int i = 0; i < actual.size(); ++i) {
            Source a = actual.get(i); DmzReplayProvenance.Binding e = expected.get(i);
            require(a.context.equals(e.context) && a.revision == e.revision && a.rule == e.sourceRule
                    && a.duplicate == e.duplicate && a.targetQ == (e.authority == DmzReplayProvenance.Authority.TARGET_Q), "source identity/authority");
        }
    }
    private static void supports(List<Support> actual, List<ConflictSupport> expected) {
        require(actual.size() == expected.size(), "support alternatives remain separate");
        for (int i = 0; i < actual.size(); ++i) {
            sources(actual.get(i).accepted, expected.get(i).accepted); sources(actual.get(i).pending, expected.get(i).pending);
        }
    }
    private static void packet(Packet actual, ConsequenceAuditFailure expected) {
        if (expected == null) { require(actual == null, "absent packet"); return; }
        require(actual != null && actual.kind.name().equals(expected.kind.name()) && actual.conflicts.size() == expected.conflicts.size(), "packet kind/order");
        for (int i = 0; i < actual.conflicts.size(); ++i) {
            Conflict a = actual.conflicts.get(i); ConsequenceConflict e = expected.conflicts.get(i);
            ground(a.ground, e.ground); supports(a.supporting, e.supporting); supports(a.opposing, e.opposing);
        }
    }
    private static void path(Path actual, ReplayPath expected) {
        require(actual.disposition.name().equals(expected.disposition.name()) && actual.choices.size() == expected.steps.size(), "ordered path");
        packet(actual.root, expected.root); packet(actual.remaining, expected.remaining);
        for (int i = 0; i < actual.choices.size(); ++i) {
            Choice a = actual.choices.get(i); ReplayPathStep e = expected.steps.get(i);
            packet(a.before, e.evidence); ground(a.conflict, e.conflict);
            require(a.side.name().equals(e.side.name()) && a.after.size() == e.grounds.size(), "symmetric choice");
            for (int j = 0; j < a.after.size(); ++j) {
                Visibility v = a.after.get(j); ReplayAppliedGround old = e.grounds.get(j);
                ground(v.ground, old.ground); require(v.proofs == old.proofs && v.visible == old.visible, "residual multiplicity/visibility");
            }
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void immutableGraph(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof java.util.UUID || value instanceof Enum) return;
        if (value instanceof List) {
            List list = (List)value;
            try { list.add(null); throw new AssertionError("mutable snapshot list"); }
            catch (UnsupportedOperationException expected) { ++checks; }
            for (Object element : list) immutableGraph(element);
            return;
        }
        if (value instanceof org.kanger.interfaces.IContextResults.Revision) return;
        require(value.getClass().getName().startsWith("org.kanger.interfaces.DmzConditionalResult"), "no native object/owner in public graph");
        try {
            for (Field field : value.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                require(Modifier.isFinal(field.getModifiers()), "immutable public field");
                immutableGraph(field.get(value));
            }
        } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }
    static void require(boolean value, String reason) {
        ++checks; if (!value) throw new AssertionError("DMZ public contract: " + reason);
    }
    public static void main(String[] args) {
        try {
            DmzConditionalQueryAllowanceRunner.run();
            System.out.println("DMZ_CONDITIONAL_RESULT_CONTRACT_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
