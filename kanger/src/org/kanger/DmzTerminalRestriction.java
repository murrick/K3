/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.List;
import java.util.ArrayList;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;

/** Opt-in transaction-local restriction for a direct unary application.
 * Production/primary source pairs are retained independently; recursive restrictions are unsupported.
 */
final class DmzTerminalRestriction implements AutoCloseable {
    private static final ThreadLocal<DmzTerminalRestriction> ACTIVE = new ThreadLocal<DmzTerminalRestriction>();
    private final DmzTerminalRestriction previous;
    private final Thread owner = Thread.currentThread();
    private final Mind boundary;
    private final DmzProofWitnesses.Witness blocked;
    private boolean closed;
    private int denied;
    private int queryDenied;
    private DmzTerminalRestriction(Mind boundary, DmzProofWitnesses.Witness blocked) {
        this.boundary = boundary; this.blocked = blocked; previous = ACTIVE.get(); ACTIVE.set(this);
    }
    static void validate(DmzProofWitnesses.Witness blocked, Mind target) {
        if (blocked.step < 0 || blocked.premises.size() != 1 || blocked.premises.get(0).step >= 0
                || blocked.source.authority != DmzReplayProvenance.Authority.EXTERNAL)
            throw new IllegalStateException("Only direct externally sourced unit applications can be enforced");
        requireAccepted(target, blocked.source);
        requireAccepted(target, blocked.premises.get(0).source);
        checkPairBudget(target, blocked);
    }
    private static List<DmzReplayProvenance.Binding> requireAccepted(Mind target, DmzReplayProvenance.Binding binding) {
        List<DmzReplayProvenance.Binding> result = new ArrayList<DmzReplayProvenance.Binding>();
        for (DmzReplayProvenance.SourceObservation source : DmzReplayProvenance.observedSources(target, binding.nativeRule)) {
            if (source.outcome != DmzReplayProvenance.Outcome.ACCEPTED)
                throw new IllegalStateException("Restriction requires settled source occurrences");
            result.add(source.binding);
        }
        if (!result.contains(binding)) throw new IllegalStateException("Selected source occurrence is absent");
        return result;
    }
    private boolean local(Mind mind) {
        for (Mind current = mind; current != null; current = (Mind) current.getNext()) if (current == boundary) return true;
        return false;
    }
    /** Frozen per-application exclusions, never a global removal of a production label. */
    static List<TerminalSupportCapture.SourcePair> excludedPairs(Mind mind, long rule,
            TerminalSupportCapture.Ground conclusion, long evidence, TerminalSupportCapture.Ground premise) {
        List<TerminalSupportCapture.SourcePair> result = new ArrayList<TerminalSupportCapture.SourcePair>();
        for (DmzTerminalRestriction restriction = ACTIVE.get(); restriction != null; restriction = restriction.previous) {
            DmzProofWitnesses.Witness witness = restriction.blocked;
            if (restriction.local(mind) && rule == witness.source.nativeRule
                    && evidence == witness.premises.get(0).source.nativeRule
                    && conclusion.equivalent(witness.graph.observed.nodes.get(witness.node).ground)
                    && premise.equivalent(witness.graph.observed.nodes.get(witness.premises.get(0).node).ground)) {
                requireAccepted(mind, witness.source); requireAccepted(mind, witness.premises.get(0).source);
                result.add(new TerminalSupportCapture.SourcePair(witness.source, witness.premises.get(0).source));
            }
        }
        return result;
    }
    private static void checkPairBudget(Mind mind, DmzProofWitnesses.Witness witness) {
        long rules = requireAccepted(mind, witness.source).size();
        long facts = requireAccepted(mind, witness.premises.get(0).source).size();
        if (rules * facts > 10000L) throw new IllegalStateException("Direct source-pair budget exceeded");
    }
    private boolean noAlternative(Mind mind) {
        checkPairBudget(mind, blocked);
        List<TerminalSupportCapture.SourcePair> excluded = excludedPairs(mind, blocked.source.nativeRule,
                blocked.graph.observed.nodes.get(blocked.node).ground, blocked.premises.get(0).source.nativeRule,
                blocked.graph.observed.nodes.get(blocked.premises.get(0).node).ground);
        for (DmzReplayProvenance.Binding rule : requireAccepted(mind, blocked.source))
            for (DmzReplayProvenance.Binding primary : requireAccepted(mind, blocked.premises.get(0).source)) {
                if (rule.context.equals(primary.context) && rule.revision != primary.revision) continue;
                if (!TerminalSupportCapture.SourcePair.excludes(excluded, rule, primary)) return false;
            }
        return true;
    }
    static DmzTerminalRestriction begin(Mind boundary, DmzProofWitnesses.Witness blocked) {
        return new DmzTerminalRestriction(boundary, blocked);
    }
    static boolean denies(Mind mind, Domain conclusion, List<Domain> tree) throws Exception {
        DmzTerminalRestriction restriction = ACTIVE.get();
        if (restriction == null) return false;
        // Consult stacked scopes separately: an outer restriction still applies in its descendants.
        for (; restriction != null; restriction = restriction.previous) if (restriction.matches(mind, conclusion, tree)) {
            ++restriction.denied; return true;
        }
        return false;
    }
    private boolean matches(Mind mind, Domain conclusion, List<Domain> tree) throws Exception {
        boolean local = false;
        for (Mind current = mind; current != null; current = (Mind) current.getNext()) if (current == boundary) { local = true; break; }
        if (!local || conclusion.getRule() == null || conclusion.getRule().getId() != blocked.source.nativeRule) return false;
        TerminalSupportCapture.Ground expected = blocked.graph.observed.nodes.get(blocked.node).ground;
        TerminalSupportCapture.Ground actual = TerminalSupportCapture.Ground.capture(new Solve(conclusion.getPredicate(),
                conclusion.isAntc(), conclusion.getArguments().convertBase(mind)), mind);
        if (!expected.equivalent(actual)) return false;
        if (tree.size() != 2) throw new IllegalStateException("Restricted application changed shape");
        requireAccepted(mind, blocked.source); requireAccepted(mind, blocked.premises.get(0).source);
        for (Domain premise : tree) if (premise != conclusion) {
            if (!premise.isExcluded(mind)) throw new IllegalStateException("Restricted premise must be satisfied");
            Solve wanted = new Solve(premise.getPredicate(), !premise.isAntc(), premise.getArguments().convertBase(mind));
            TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(wanted, mind);
            if (!blocked.graph.observed.nodes.get(blocked.premises.get(0).node).ground.equivalent(ground)) return false;
            IRule evidence = mind.getRules().find(wanted);
            return evidence != null && !evidence.isDeleted(mind) && !mind.getRules().isGenerated(evidence)
                    && evidence.getId() == blocked.premises.get(0).source.nativeRule && noAlternative(mind);
        }
        return false;
    }
    static boolean deniesQueryPair(Mind mind, Domain left, Domain right) throws Exception {
        DmzTerminalRestriction active = ACTIVE.get();
        if (active == null) return false;
        if (mind.getQueryPass() != org.kanger.enums.QueryPass.CHECKFALSE
                && mind.getQueryPass() != org.kanger.enums.QueryPass.CHECKTRUE) return false;
        for (DmzTerminalRestriction restriction = active; restriction != null; restriction = restriction.previous)
            if (restriction.queryPair(mind, left, right) || restriction.queryPair(mind, right, left)) {
                ++restriction.queryDenied; return true;
            }
        return false;
    }
    private boolean queryPair(Mind mind, Domain production, Domain opposite) throws Exception {
        boolean local = false;
        for (Mind current = mind; current != null; current = (Mind) current.getNext()) if (current == boundary) { local = true; break; }
        if (!local || production.getRule() == null || production.getRule().getId() != blocked.source.nativeRule) return false;
        TerminalSupportCapture.Ground expected = blocked.graph.observed.nodes.get(blocked.node).ground;
        if (!production.getPredicate().getName(mind).equals(expected.predicate) || production.isAntc() != expected.sign) return false;
        TerminalSupportCapture.Ground candidate = TerminalSupportCapture.Ground.capture(new Solve(opposite.getPredicate(),
                !opposite.isAntc(), opposite.getArguments().convertBase(mind)), mind);
        if (!expected.equivalent(candidate)) return false;
        requireAccepted(mind, blocked.source); requireAccepted(mind, blocked.premises.get(0).source);
        // The originally selected primary support must still exist; a query assumption is not a substitute.
        for (IRule rule : mind.getRules()) if (rule.getId() == blocked.premises.get(0).source.nativeRule
                && !rule.isDeleted(mind) && !mind.getRules().isGenerated(rule)) return noAlternative(mind);
        return false;
    }
    int queryDeniedCount() { return queryDenied; }
    int deniedCount() { return denied; }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || ACTIVE.get() != this) throw new IllegalStateException("Restriction scopes must close on owner thread in stack order");
        closed = true; if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
