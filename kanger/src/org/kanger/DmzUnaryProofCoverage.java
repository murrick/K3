/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Exhaustive finite unary unit-step audit, not general KANGER proof completeness.
 * Detached primary inventory is captured before the operation. No native solve,
 * new rule storage, TVar assignment or branch restriction occurs here.
 */
final class DmzUnaryProofCoverage {
    static final class Result {
        final boolean supported, current, covered, truncated;
        final boolean complete = false;
        final int expectedSteps;
        final List<String> gaps;
        Result(boolean supported, boolean current, boolean truncated, int expected, List<String> gaps) {
            this.supported = supported; this.current = current; this.truncated = truncated;
            expectedSteps = expected; this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            covered = supported && current && !truncated && gaps.isEmpty();
        }
    }
    private static final class Budget {
        final int limit;
        int work;
        boolean truncated;
        Budget(int limit, int work) { this.limit = limit; this.work = work; }
        boolean take() {
            if (work >= limit) { truncated = true; return false; }
            ++work; return true;
        }
    }
    private static final class Clause {
        final String[] predicates = new String[2];
        final boolean[] signs = new boolean[2];
        final List<DmzReplayProvenance.Binding> sources;
        Clause(List<DmzReplayProvenance.Binding> sources) { this.sources = sources; }
    }
    private static final class Fact {
        final TerminalSupportCapture.Ground ground;
        final List<DmzReplayProvenance.Binding> sources;
        Fact(TerminalSupportCapture.Ground ground, List<DmzReplayProvenance.Binding> sources) {
            this.ground = ground; this.sources = sources;
        }
    }
    private static final class Step {
        final Clause clause;
        final TerminalSupportCapture.Ground conclusion, premise;
        Step(Clause clause, TerminalSupportCapture.Ground conclusion, TerminalSupportCapture.Ground premise) {
            this.clause = clause; this.conclusion = conclusion; this.premise = premise;
        }
    }
    private final DmzCollisionProofGuard guard;
    private final List<Clause> clauses = new ArrayList<Clause>();
    private final List<Fact> facts = new ArrayList<Fact>();
    private final List<SemanticTermSnapshot> values = new ArrayList<SemanticTermSnapshot>();
    private final List<String> inventoryGaps = new ArrayList<String>();
    private final Budget inventoryBudget;
    private DmzUnaryProofCoverage(TerminalSupportCapture capture, Mind target, int budget) throws Exception {
        if (budget <= 0) throw new IllegalArgumentException("Positive coverage budget required");
        guard = DmzCollisionProofGuard.beforeOperation(capture, target);
        inventoryBudget = new Budget(budget, 0);
        Map<UUID, Long> pins = new HashMap<UUID, Long>();
        for (IRule candidate : target.getRules()) {
            if (!inventoryBudget.take()) break;
            if (candidate.isDeleted(target)) continue;
            Rule rule = (Rule) candidate;
            if (rule.isQuery() || target.getRules().isGenerated(rule) || rule.getTree().size() != 1) {
                inventoryGaps.add("unsupported-baseline-rule:" + rule.getId()); break;
            }
            List<Domain> row = rule.getTree().get(0);
            List<DmzReplayProvenance.Binding> sources = new ArrayList<DmzReplayProvenance.Binding>();
            for (DmzReplayProvenance.SourceObservation source : DmzReplayProvenance.observedSources(target, rule.getId())) {
                if (!inventoryBudget.take()) break;
                if (source.outcome != DmzReplayProvenance.Outcome.ACCEPTED && source.outcome != DmzReplayProvenance.Outcome.PENDING)
                    continue;
                Long pin = pins.put(source.binding.context, source.binding.revision);
                if (pin != null && pin.longValue() != source.binding.revision)
                    inventoryGaps.add("inconsistent-context-revisions");
                sources.add(source.binding);
            }
            if (inventoryBudget.truncated) break;
            if (sources.isEmpty()) { inventoryGaps.add("missing-baseline-source:" + rule.getId()); break; }
            if (row.size() != 1 && row.size() != 2) { inventoryGaps.add("unsupported-clause-arity:" + rule.getId()); break; }
            long variable = -1;
            Clause clause = new Clause(Collections.unmodifiableList(sources));
            for (int i = 0; i < row.size(); ++i) {
                if (!inventoryBudget.take()) break;
                Domain literal = row.get(i);
                if (literal.isSystem(target) || literal.isCalculated(target) || literal.isQuery(target)
                        || literal.getArguments().size() != 1) {
                    inventoryGaps.add("unsupported-literal:" + rule.getId()); break;
                }
                IArgument argument = literal.getArguments().get(0);
                if (row.size() == 1) {
                    if (argument.getType() != ArgumentType.TERM || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(target))) {
                        inventoryGaps.add("unsupported-primary-argument:" + rule.getId()); break;
                    }
                    SemanticTermSnapshot value = SemanticTermSnapshot.capture(argument.getValue(target));
                    if (value.getType() != DataType.STRING && value.getType() != DataType.NUMERIC) {
                        inventoryGaps.add("unsupported-primary-value:" + rule.getId()); break;
                    }
                    TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(
                            new Solve(literal.getPredicate(), literal.isAntc(), literal.getArguments().convertBase(target)), target);
                    facts.add(new Fact(ground, clause.sources));
                    boolean found = false;
                    for (SemanticTermSnapshot previous : values) found |= previous.semanticallyEquals(value);
                    if (!found) values.add(value);
                } else {
                    if (argument.getType() != ArgumentType.TVARIABLE || (variable >= 0 && variable != argument.getId())) {
                        inventoryGaps.add("unsupported-variable-pattern:" + rule.getId()); break;
                    }
                    variable = argument.getId();
                    clause.predicates[i] = literal.getPredicate().getName(target);
                    clause.signs[i] = literal.isAntc();
                }
            }
            if (inventoryBudget.truncated || !inventoryGaps.isEmpty()) break;
            if (row.size() == 2) clauses.add(clause);
        }
    }
    static DmzUnaryProofCoverage beforeOperation(TerminalSupportCapture capture, Mind target, int budget) throws Exception {
        return new DmzUnaryProofCoverage(capture, target, budget);
    }
    Result audit(DmzProvisionalCollisionProof proof, Mind target) throws Exception {
        Budget budget = new Budget(inventoryBudget.limit, inventoryBudget.work);
        boolean supported = inventoryGaps.isEmpty() && !inventoryBudget.truncated;
        boolean current = guard.isCurrent(proof, target);
        List<String> gaps = new ArrayList<String>(inventoryGaps);
        if (!current) gaps.add("proof-baseline-not-current");
        if (!supported || !current)
            return new Result(supported, current, inventoryBudget.truncated || proof != null && proof.truncated, 0, gaps);
        List<TerminalSupportCapture.Ground> known = new ArrayList<TerminalSupportCapture.Ground>();
        for (Fact fact : facts) add(known, fact.ground);
        List<Step> steps = new ArrayList<Step>();
        boolean changed;
        do {
            changed = false;
            for (Clause clause : clauses) for (SemanticTermSnapshot value : values) for (int slot = 0; slot < 2; ++slot) {
                if (!budget.take()) return new Result(true, true, true, 0, gaps);
                TerminalSupportCapture.Ground premise = ground(clause.predicates[1 - slot], !clause.signs[1 - slot], value);
                if (!has(known, premise)) continue;
                TerminalSupportCapture.Ground conclusion = ground(clause.predicates[slot], clause.signs[slot], value);
                changed |= add(known, conclusion);
                boolean found = false;
                for (Step previous : steps)
                    found |= previous.clause == clause && previous.conclusion.equivalent(conclusion);
                if (!found) steps.add(new Step(clause, conclusion, premise));
            }
        } while (changed);
        List<TerminalSupportCapture.Ground> relevant = new ArrayList<TerminalSupportCapture.Ground>();
        add(relevant, proof.event.left.nodes.get(proof.event.left.root).ground);
        add(relevant, proof.event.right.nodes.get(proof.event.right.root).ground);
        do {
            changed = false;
            for (Step step : steps) {
                if (!budget.take()) return new Result(true, true, true, 0, gaps);
                if (has(relevant, step.conclusion)) changed |= add(relevant, step.premise);
            }
        } while (changed);
        for (TerminalSupportCapture.Ground atom : relevant)
            if (!has(known, atom)) gaps.add("root-outside-unit-closure");
        int expected = 0;
        for (Step step : steps) if (has(relevant, step.conclusion)) {
            ++expected;
            boolean found = false;
            for (int i = 0; i < proof.graph.observed.steps.size(); ++i) {
                if (!budget.take()) return new Result(true, true, true, expected, gaps);
                DmzObservedProofGraph.Step observed = proof.graph.observed.steps.get(i);
                if (!observed.application.ground.equivalent(step.conclusion) || observed.premises.size() != 1
                        || !proof.graph.observed.nodes.get(observed.premises.get(0)).ground.equivalent(step.premise)) continue;
                if (containsAll(proof.graph.steps.get(i).ruleSources, step.clause.sources)) { found = true; break; }
            }
            if (!found) gaps.add("missing-unit-application:" + step.clause.sources.get(0).nativeRule + ":" + step.conclusion.predicate);
        }
        for (Fact fact : facts) if (has(relevant, fact.ground)) {
            List<DmzReplayProvenance.Binding> observed = new ArrayList<DmzReplayProvenance.Binding>();
            for (int i = 0; i < proof.graph.observed.steps.size(); ++i) {
                DmzObservedProofGraph.Step step = proof.graph.observed.steps.get(i);
                for (int p = 0; p < step.premises.size(); ++p) {
                    if (!budget.take()) return new Result(true, true, true, expected, gaps);
                    if (proof.graph.observed.nodes.get(step.premises.get(p)).ground.equivalent(fact.ground)
                            && step.application.supports.get(p).primary) observed.addAll(proof.graph.steps.get(i).primarySources.get(p));
                }
            }
            if (!containsAll(observed, fact.sources)) gaps.add("missing-primary-alternative:" + fact.ground.predicate);
        }
        return new Result(true, true, false, expected, gaps);
    }
    private static boolean containsAll(List<DmzReplayProvenance.Binding> actual, List<DmzReplayProvenance.Binding> expected) {
        return actual.containsAll(expected); // Bindings retain source-occurrence object identity.
    }
    private static TerminalSupportCapture.Ground ground(String predicate, boolean sign, SemanticTermSnapshot value) {
        return new TerminalSupportCapture.Ground(predicate, sign, Collections.singletonList(value));
    }
    private static boolean has(List<TerminalSupportCapture.Ground> atoms, TerminalSupportCapture.Ground wanted) {
        for (TerminalSupportCapture.Ground atom : atoms) if (atom.equivalent(wanted)) return true;
        return false;
    }
    private static boolean add(List<TerminalSupportCapture.Ground> atoms, TerminalSupportCapture.Ground atom) {
        if (has(atoms, atom)) return false;
        atoms.add(atom); return true;
    }
}
