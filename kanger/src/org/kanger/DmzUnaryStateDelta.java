/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Independent finite unary closure/state-delta check. No proof-coverage certificate. */
final class DmzUnaryStateDelta {
    static final class Result {
        final boolean supported, matched, truncated;
        final boolean complete = false;
        final List<String> gaps;
        Result(boolean supported, boolean truncated, List<String> gaps) {
            this.supported = supported; this.truncated = truncated;
            this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            matched = supported && !truncated && gaps.isEmpty();
        }
    }
    private static final class Row {
        final byte[] bytes;
        final String rendering;
        final boolean generated;
        Row(Rule rule, Mind mind) throws Exception {
            bytes = rule.pack().getBuffer().clone(); rendering = rule.toString(mind);
            generated = mind.getRules().isGenerated(rule);
        }
        boolean same(Rule rule, Mind mind) throws Exception {
            return generated == mind.getRules().isGenerated(rule) && rendering.equals(rule.toString(mind))
                    && Arrays.equals(bytes, rule.pack().getBuffer());
        }
    }
    private static final class Clause {
        long rule;
        final String[] names = new String[2];
        final boolean[] signs = new boolean[2];
    }
    private final Mind target;
    private final Map<Long, Row> baseline = new HashMap<Long, Row>();
    private final Map<Long, List<DmzReplayProvenance.Binding>> sources = new HashMap<Long, List<DmzReplayProvenance.Binding>>();
    private final Map<Long, TerminalSupportCapture.Ground> primary = new HashMap<Long, TerminalSupportCapture.Ground>();
    private final List<TerminalSupportCapture.Ground> facts = new ArrayList<TerminalSupportCapture.Ground>();
    private final List<Clause> clauses = new ArrayList<Clause>();
    private final List<String> gaps = new ArrayList<String>();
    private final int limit;
    private int work;
    private boolean truncated;
    private DmzUnaryStateDelta(Mind target, int limit) throws Exception {
        if (limit <= 0) throw new IllegalArgumentException("Positive delta budget required");
        this.target = target; this.limit = limit;
        for (IRule candidate : target.getRules()) {
            if (!take()) break;
            if (candidate.isDeleted(target)) continue;
            Rule rule = (Rule) candidate;
            baseline.put(rule.getId(), new Row(rule, target));
            List<DmzReplayProvenance.Binding> bindings = new ArrayList<DmzReplayProvenance.Binding>();
            for (DmzReplayProvenance.SourceObservation source : DmzReplayProvenance.observedSources(target, rule.getId())) {
                if (!take()) break;
                if (source.outcome == DmzReplayProvenance.Outcome.ACCEPTED || source.outcome == DmzReplayProvenance.Outcome.PENDING)
                    bindings.add(source.binding);
            }
            sources.put(rule.getId(), bindings);
            if (rule.isQuery() || rule.getTree().size() != 1 || target.getRules().isGenerated(rule)) {
                gaps.add("unsupported-baseline-rule"); continue;
            }
            List<Domain> row = rule.getTree().get(0);
            if (row.size() == 1) {
                TerminalSupportCapture.Ground fact = atom(rule, target);
                if (fact == null) gaps.add("unsupported-baseline-fact"); else { add(facts, fact); primary.put(rule.getId(), fact); }
            } else if (row.size() == 2) {
                Clause clause = new Clause(); clause.rule = rule.getId(); long variable = -1; boolean valid = true;
                for (int i = 0; i < 2; ++i) {
                    Domain literal = row.get(i);
                    if (!ordinary(literal, target)) { valid = false; break; }
                    IArgument argument = literal.getArguments().get(0);
                    if (argument.getType() != ArgumentType.TVARIABLE || variable >= 0 && variable != argument.getId()) { valid = false; break; }
                    variable = argument.getId(); clause.names[i] = literal.getPredicate().getName(target); clause.signs[i] = literal.isAntc();
                }
                if (valid) clauses.add(clause); else gaps.add("unsupported-baseline-clause");
            } else gaps.add("unsupported-baseline-arity");
        }
    }
    static DmzUnaryStateDelta beforeInput(Mind target, int budget) throws Exception { return new DmzUnaryStateDelta(target, budget); }
    Result audit(Mind candidate, long incomingId) throws Exception {
        List<String> errors = new ArrayList<String>(gaps);
        if (candidate != target) { errors.add("different-target"); return new Result(false, truncated, errors); }
        if (!errors.isEmpty() || truncated) return new Result(false, truncated, errors);
        // Each audit has its own remaining budget; repeated checks cannot consume the inventory.
        int remaining = limit - work;
        Map<Long, Rule> live = new HashMap<Long, Rule>();
        for (IRule item : target.getRules()) {
            if (--remaining < 0) return new Result(true, true, errors);
            if (!item.isDeleted(target)) live.put(item.getId(), (Rule) item);
        }
        for (Map.Entry<Long, Row> entry : baseline.entrySet()) {
            Rule rule = live.get(entry.getKey());
            if (rule == null || !entry.getValue().same(rule, target)) errors.add("baseline-rule-changed:" + entry.getKey());
        }
        Rule input = live.get(incomingId);
        TerminalSupportCapture.Ground incoming = input == null ? null : atom(input, target);
        if (baseline.containsKey(incomingId) || incoming == null || target.getRules().isGenerated(input)) {
            errors.add("unsupported-incoming-primary"); return new Result(false, false, errors);
        }
        List<TerminalSupportCapture.Ground> closure = new ArrayList<TerminalSupportCapture.Ground>(facts);
        add(closure, incoming);
        boolean changed;
        do {
            changed = false;
            List<TerminalSupportCapture.Ground> known = new ArrayList<TerminalSupportCapture.Ground>(closure);
            for (Clause clause : clauses) for (TerminalSupportCapture.Ground fact : known) for (int slot = 0; slot < 2; ++slot) {
                if (--remaining < 0) return new Result(true, true, errors);
                if (fact.predicate.equals(clause.names[1-slot]) && fact.sign != clause.signs[1-slot])
                    changed |= add(closure, new TerminalSupportCapture.Ground(clause.names[slot], clause.signs[slot], fact.arguments));
            }
        } while (changed);
        List<TerminalSupportCapture.Ground> actual = new ArrayList<TerminalSupportCapture.Ground>();
        for (Rule rule : live.values()) {
            if (--remaining < 0) return new Result(true, true, errors);
            TerminalSupportCapture.Ground ground = atom(rule, target);
            if (ground != null) add(actual, ground);
            if (!baseline.containsKey(rule.getId()) && rule.getId() != incomingId
                    && (!target.getRules().isGenerated(rule) || ground == null || !has(closure, ground)))
                errors.add("unexpected-new-rule:" + rule.getId());
        }
        for (TerminalSupportCapture.Ground ground : closure) if (!has(actual, ground)) errors.add("missing-unit-result:" + ground.predicate);
        return new Result(true, false, errors);
    }
    private boolean take() { if (work >= limit) { truncated = true; return false; } ++work; return true; }
    /** Checks every relevant unit edge and primary occurrence, independent of witness count. */
    Result auditRoutes(Mind candidate, DmzReplayProvenance.Binding incoming,
            DmzSourcedProofGraph graph, int root) throws Exception {
        return auditRoutes(candidate, Collections.singletonList(incoming), graph, root);
    }
    Result auditRoutes(Mind candidate, List<DmzReplayProvenance.Binding> inputs,
            DmzSourcedProofGraph graph, int root) throws Exception {
        if (inputs == null || inputs.isEmpty()) return new Result(false, false,
                Collections.singletonList("missing-incoming-source"));
        DmzReplayProvenance.Binding incoming = inputs.get(0);
        for (DmzReplayProvenance.Binding binding : inputs)
            if (binding.nativeRule != incoming.nativeRule) return new Result(false, false,
                    Collections.singletonList("different-incoming-native-rules"));
        Result state = audit(candidate, incoming.nativeRule);
        if (!state.matched) return state;
        List<String> errors = new ArrayList<String>();
        if (graph == null || root < 0 || root >= graph.observed.nodes.size()) {
            errors.add("missing-route-root"); return new Result(false, false, errors);
        }
        Map<Long, TerminalSupportCapture.Ground> seeds = new HashMap<Long, TerminalSupportCapture.Ground>(primary);
        Map<Long, List<DmzReplayProvenance.Binding>> expectedSources = new HashMap<Long, List<DmzReplayProvenance.Binding>>(sources);
        Rule input = null;
        for (IRule rule : candidate.getRules()) if (rule.getId() == incoming.nativeRule && !rule.isDeleted(candidate)) input = (Rule) rule;
        seeds.put(incoming.nativeRule, atom(input, candidate));
        expectedSources.put(incoming.nativeRule, new ArrayList<DmzReplayProvenance.Binding>(inputs));
        for (List<DmzReplayProvenance.Binding> bindings : expectedSources.values())
            if (bindings.isEmpty()) errors.add("missing-inventory-source");
        Map<java.util.UUID, Long> pins = new HashMap<java.util.UUID, Long>();
        for (List<DmzReplayProvenance.Binding> bindings : expectedSources.values())
            for (DmzReplayProvenance.Binding binding : bindings) {
                Long previous = pins.put(binding.context, binding.revision);
                if (previous != null && previous.longValue() != binding.revision) errors.add("inconsistent-context-revisions");
            }
        List<TerminalSupportCapture.Ground> closure = new ArrayList<TerminalSupportCapture.Ground>();
        for (TerminalSupportCapture.Ground seed : seeds.values()) add(closure, seed);
        List<UnitEdge> edges = new ArrayList<UnitEdge>();
        int remaining = limit - work;
        boolean changed;
        do {
            changed = false;
            List<TerminalSupportCapture.Ground> known = new ArrayList<TerminalSupportCapture.Ground>(closure);
            for (Clause clause : clauses) for (TerminalSupportCapture.Ground fact : known) for (int slot = 0; slot < 2; ++slot) {
                if (--remaining < 0) return new Result(true, true, errors);
                if (!fact.predicate.equals(clause.names[1-slot]) || fact.sign == clause.signs[1-slot]) continue;
                TerminalSupportCapture.Ground conclusion = new TerminalSupportCapture.Ground(clause.names[slot], clause.signs[slot], fact.arguments);
                changed |= add(closure, conclusion);
                boolean found = false;
                for (UnitEdge edge : edges) found |= edge.rule == clause.rule && edge.premise.equivalent(fact) && edge.conclusion.equivalent(conclusion);
                if (!found) edges.add(new UnitEdge(clause.rule, fact, conclusion));
            }
        } while (changed);
        List<TerminalSupportCapture.Ground> relevant = new ArrayList<TerminalSupportCapture.Ground>();
        add(relevant, graph.observed.nodes.get(root).ground);
        if (!has(closure, relevant.get(0))) errors.add("route-root-outside-closure");
        do {
            changed = false;
            for (UnitEdge edge : edges) {
                if (--remaining < 0) return new Result(true, true, errors);
                if (has(relevant, edge.conclusion)) changed |= add(relevant, edge.premise);
            }
        } while (changed);
        for (UnitEdge edge : edges) if (has(relevant, edge.conclusion)) {
            boolean found = false;
            for (int i = 0; i < graph.observed.steps.size(); ++i) {
                if (--remaining < 0) return new Result(true, true, errors);
                DmzObservedProofGraph.Step step = graph.observed.steps.get(i);
                if (step.premises.size() == 1 && step.application.ground.equivalent(edge.conclusion)
                        && graph.observed.nodes.get(step.premises.get(0)).ground.equivalent(edge.premise)
                        && graph.steps.get(i).ruleSources.containsAll(expectedSources.get(edge.rule))) found = true;
            }
            if (!found) errors.add("missing-unit-route:" + edge.rule + ":" + edge.conclusion.predicate);
        }
        for (Map.Entry<Long, TerminalSupportCapture.Ground> seed : seeds.entrySet()) if (has(relevant, seed.getValue())) {
            List<DmzReplayProvenance.Binding> observed = new ArrayList<DmzReplayProvenance.Binding>();
            for (int i = 0; i < graph.observed.steps.size(); ++i) {
                DmzObservedProofGraph.Step step = graph.observed.steps.get(i);
                for (int p = 0; p < step.premises.size(); ++p) {
                    if (--remaining < 0) return new Result(true, true, errors);
                    if (step.application.supports.get(p).primary && graph.observed.nodes.get(step.premises.get(p)).ground.equivalent(seed.getValue()))
                        observed.addAll(graph.steps.get(i).primarySources.get(p));
                }
            }
            if (!observed.containsAll(expectedSources.get(seed.getKey()))) errors.add("missing-primary-route-source:" + seed.getKey());
        }
        return new Result(!errors.contains("missing-inventory-source") && !errors.contains("inconsistent-context-revisions"), false, errors);
    }
    private static final class UnitEdge {
        final long rule;
        final TerminalSupportCapture.Ground premise, conclusion;
        UnitEdge(long rule, TerminalSupportCapture.Ground premise, TerminalSupportCapture.Ground conclusion) {
            this.rule = rule; this.premise = premise; this.conclusion = conclusion;
        }
    }
    private static boolean ordinary(Domain literal, Mind mind) throws Exception {
        return !literal.isSystem(mind) && !literal.isCalculated(mind) && !literal.isQuery(mind) && literal.getArguments().size() == 1;
    }
    private static TerminalSupportCapture.Ground atom(Rule rule, Mind mind) throws Exception {
        if (rule.isQuery() || rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) return null;
        Domain domain = rule.getTree().get(0).get(0);
        if (!ordinary(domain, mind) || domain.getArguments().get(0).getType() != ArgumentType.TERM) return null;
        TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(
                new Solve(domain.getPredicate(), domain.isAntc(), domain.getArguments().convertBase(mind)), mind);
        if (ground == null) return null;
        DataType type = ground.arguments.get(0).getType();
        return type == DataType.STRING || type == DataType.NUMERIC ? ground : null;
    }
    private static boolean has(List<TerminalSupportCapture.Ground> facts, TerminalSupportCapture.Ground ground) {
        for (TerminalSupportCapture.Ground fact : facts) if (fact.equivalent(ground)) return true; return false;
    }
    private static boolean add(List<TerminalSupportCapture.Ground> facts, TerminalSupportCapture.Ground ground) {
        if (has(facts, ground)) return false; facts.add(ground); return true;
    }
}
