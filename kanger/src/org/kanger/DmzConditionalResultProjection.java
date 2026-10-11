/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzConditionalResult.*;
import org.kanger.DmzStoredRetractionTransaction.*;

/** Copies historical observations only. Never replays or accesses a live Mind. */
final class DmzConditionalResultProjection {
    private DmzConditionalResultProjection() {}
    static DmzConditionalResult project(ConditionalQueryResult result) {
        if (result == null) throw new NullPointerException("result");
        return new DmzConditionalResult(Disposition.valueOf(result.disposition.name()), answer(result.direct),
                family(result.family), packet(result.authorityConflict), result.retryDiscovery, result.restartRequired,
                result.candidate, result.queryStatement, result.source, result.sourceRule,
                result.traversalWork, result.maximumDepth, result.maximumNodes, result.workLimit);
    }
    private static Ground ground(TerminalSupportCapture.Ground ground) {
        if (ground.arguments.size() != 1) throw new IllegalArgumentException("Qualified unary ground required");
        Object value = ground.arguments.get(0).materialize().getValue();
        if (!(value instanceof Number)) throw new IllegalArgumentException("Qualified integer ground required");
        return new Ground(ground.predicate, ground.sign, new java.math.BigDecimal(value.toString()).toBigIntegerExact(),
                ground.arguments.get(0).getType());
    }
    private static List<Source> sources(List<DmzReplayProvenance.Binding> bindings) {
        List<Source> result = new ArrayList<Source>();
        for (DmzReplayProvenance.Binding binding : bindings)
            result.add(new Source(binding.context, binding.revision, binding.sourceRule,
                    binding.authority == DmzReplayProvenance.Authority.TARGET_Q, binding.duplicate));
        return result;
    }
    private static List<Support> supports(List<ConflictSupport> supports) {
        List<Support> result = new ArrayList<Support>();
        for (ConflictSupport support : supports) result.add(new Support(sources(support.accepted), sources(support.pending)));
        return result;
    }
    private static Packet packet(ConsequenceAuditFailure packet) {
        if (packet == null) return null;
        List<Conflict> result = new ArrayList<Conflict>();
        for (ConsequenceConflict conflict : packet.conflicts)
            result.add(new Conflict(ground(conflict.ground), supports(conflict.supporting), supports(conflict.opposing)));
        return new Packet(ConflictKind.valueOf(packet.kind.name()), result);
    }
    private static List<Visibility> visibility(List<ReplayAppliedGround> grounds) {
        List<Visibility> result = new ArrayList<Visibility>();
        for (ReplayAppliedGround ground : grounds) result.add(new Visibility(ground(ground.ground), ground.proofs, ground.visible));
        return result;
    }
    private static Path path(ReplayPath path) {
        List<Choice> choices = new ArrayList<Choice>();
        for (ReplayPathStep step : path.steps)
            choices.add(new Choice(packet(step.evidence), ground(step.conflict), Side.valueOf(step.side.name()), visibility(step.grounds)));
        return new Path(packet(path.root), packet(path.remaining), PathDisposition.valueOf(path.disposition.name()), choices);
    }
    private static Answer answer(QueryResult answer) {
        return answer == null ? null : new Answer(CandidateMode.valueOf(answer.candidateMode.name()), answer.value,
                answer.hypotheses, answer.hypothesisAssertions);
    }
    private static List<Opinion> opinions(List<ReplayOpinion> opinions) {
        List<Opinion> result = new ArrayList<Opinion>();
        for (ReplayOpinion opinion : opinions) result.add(new Opinion(path(opinion.path), answer(opinion.application.query)));
        return result;
    }
    private static Family family(ReplayTraversalResult family) {
        if (family == null) return null;
        List<Frontier> frontier = new ArrayList<Frontier>();
        for (ReplayFrontier entry : family.frontier)
            frontier.add(new Frontier(path(entry.path), Stop.valueOf(entry.reason.name()), entry.recognizedInTraversal));
        ReplayRootFrontier root = family.unstartedRoot;
        return new Family(Stop.valueOf(family.outcome.name()), opinions(family.opinions), opinions(family.declined), frontier,
                root == null ? null : new RootFrontier(packet(root.evidence), root.conflictIndex, root.completedBranches,
                        Stop.valueOf(root.reason.name())), family.traversalWork, family.generatedNodes,
                family.maximumDepth, family.maximumNodes, family.workLimit);
    }
}
