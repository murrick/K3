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
        final String[] names = new String[2];
        final boolean[] signs = new boolean[2];
    }
    private final Mind target;
    private final Map<Long, Row> baseline = new HashMap<Long, Row>();
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
            if (rule.isQuery() || rule.getTree().size() != 1 || target.getRules().isGenerated(rule)) {
                gaps.add("unsupported-baseline-rule"); continue;
            }
            List<Domain> row = rule.getTree().get(0);
            if (row.size() == 1) {
                TerminalSupportCapture.Ground fact = atom(rule, target);
                if (fact == null) gaps.add("unsupported-baseline-fact"); else add(facts, fact);
            } else if (row.size() == 2) {
                Clause clause = new Clause(); long variable = -1; boolean valid = true;
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
