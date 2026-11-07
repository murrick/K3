/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.ContextProductionCapture.Fact;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Detached whole-rule arithmetic instances. No closure, consistency verdict or admission authority. */
public final class ContextArithmeticRuleInspection {
    private static final class Slot {
        final SemanticTermSnapshot value;
        final int variable;
        final ContextArithmeticExpression expression;

        Slot(SemanticTermSnapshot value, int variable, ContextArithmeticExpression expression) {
            this.value = value;
            this.variable = variable;
            this.expression = expression;
        }

        SemanticTermSnapshot instantiate(Map<Integer, SemanticTermSnapshot> assignment, Runnable work) throws Exception {
            work.run();
            if (expression == null) return variable < 0 ? value : assignment.get(variable);
            Map<Integer, SemanticTermSnapshot> inputs = new LinkedHashMap<>();
            for (int variable : expression.variables) {
                work.run();
                inputs.put(variable, assignment.get(variable));
            }
            return expression.evaluate(inputs, work);
        }
    }

    private static final class Atom {
        final String predicate;
        final boolean positive;
        final List<Slot> arguments;

        Atom(String predicate, boolean positive, List<Slot> arguments) {
            this.predicate = predicate;
            this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }
    }

    public static final class Premise {
        public final Fact fact;
        public final ContextProductionCapture.Support proof;

        private Premise(Fact fact, ContextProductionCapture.Support proof) {
            this.fact = fact;
            this.proof = proof;
        }

        public static Premise primary(ContextProductionNativeBinding.Snapshot snapshot, Source source, int clause) {
            for (ContextProductionNativeBinding.Entry entry : snapshot.primary) {
                if (!entry.source.equals(source)) continue;
                if (clause < 0 || clause >= entry.clauses.size()) throw new IllegalArgumentException("Exact primary clause required");
                Clause selected = entry.clauses.get(clause);
                Fact fact = selected.literals.size() == 1 ? selected.literals.get(0).fact() : null;
                if (fact == null) throw new IllegalArgumentException("Ground primary unit required");
                return new Premise(fact, new ContextProductionCapture.Support(
                        Collections.singleton(entry.source), 0));
            }
            throw new IllegalArgumentException("Primary source is absent from the snapshot");
        }
    }

    /** Detached forward consequence; later family use requires exact cut/source validation. */
    public static final class Step {
        public final Source rule;
        public final int branch;
        public final List<Premise> premises;
        public final Premise conclusion;

        private Step(Source rule, int branch, List<Premise> premises, Premise conclusion) {
            this.rule = rule;
            this.branch = branch;
            this.premises = Collections.unmodifiableList(new ArrayList<>(premises));
            this.conclusion = conclusion;
        }
    }

    public final Source source;
    public final String origin;
    public final Set<Integer> variables;
    private final List<List<Atom>> branches;

    private ContextArithmeticRuleInspection(Source source, String origin, Set<Integer> variables, List<List<Atom>> branches) {
        this.source = source;
        this.origin = origin;
        this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
        this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
    }

    public static ContextArithmeticRuleInspection inspect(Mind mind, ContextProductionInventory inventory,
                                                          long nativeRule, int maximumWork) throws Exception {
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        current(mind, inventory);
        Rule rule = (Rule) mind.getRules().get(nativeRule);
        if (rule == null || rule.isDeleted(mind) || rule.isQuery() || mind.getRules().isGenerated(rule))
            throw new IllegalArgumentException("Visible primary arithmetic rule required");
        Set<Integer> variables = new LinkedHashSet<>();
        List<List<Atom>> branches = new ArrayList<>();
        for (List<Domain> branch : rule.getTree()) {
            work.run();
            List<Atom> atoms = new ArrayList<>();
            for (Domain domain : branch) {
                work.run();
                if (domain.isSystem(mind)) throw new IllegalStateException("Ordinary predicate required");
                List<Slot> slots = new ArrayList<>();
                for (IArgument argument : domain.getArguments()) {
                    work.run();
                    if (argument.getType() == ArgumentType.FUNCTION) {
                        ContextArithmeticExpression expression = ContextArithmeticExpression.capture(argument, rule, mind, work);
                        variables.addAll(expression.variables);
                        slots.add(new Slot(null, -1, expression));
                    } else if (argument.getType() == ArgumentType.TVARIABLE) {
                        TVariable variable = (TVariable) argument.getObject(mind);
                        if (variable.getRuleId() != rule.getId()) throw new IllegalStateException("Nonlocal rule variable");
                        int index = variable.getIndex() - rule.getVarIndex();
                        if (index < 0) throw new IllegalStateException("Invalid rule variable index");
                        variables.add(index);
                        slots.add(new Slot(null, index, null));
                    } else {
                        if (argument.getType() != ArgumentType.TERM
                                || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))
                            throw new IllegalStateException("Ordinary value or arithmetic expression required");
                        slots.add(new Slot(SemanticTermSnapshot.capture(argument.getValue(mind)), -1, null));
                    }
                }
                atoms.add(new Atom(domain.getPredicate(mind).getName(mind), domain.isAntc(), slots));
            }
            if (atoms.isEmpty()) throw new IllegalStateException("Nonempty rule branch required");
            branches.add(Collections.unmodifiableList(atoms));
        }
        if (branches.isEmpty()) throw new IllegalStateException("Whole rule required");
        Source source = new Source(inventory.context, inventory.revision, nativeRule, inventory.localX,
                "inventory:" + inventory.fingerprint);
        ContextArithmeticRuleInspection result = new ContextArithmeticRuleInspection(source, rule.getOrigin(), variables, branches);
        current(mind, inventory);
        return result;
    }

    private static void current(Mind mind, ContextProductionInventory expected) throws Exception {
        ContextProductionInventory actual = ContextProductionInventory.inspect(mind, expected.context,
                expected.revision, expected.localX, expected.initialization, 10000);
        if (!expected.fingerprint.equals(actual.fingerprint)) throw new IllegalStateException("Changed arithmetic rule inventory");
    }

    public Step apply(int branch, List<Premise> premises, int maximumDepth, int maximumWork) throws Exception {
        if (branch < 0 || branch >= branches.size() || premises == null || maximumDepth < 1)
            throw new IllegalArgumentException("Bounded exact branch premises required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        List<Premise> donors = new ArrayList<>();
        for (Premise premise : premises) {
            work.run();
            if (premise == null) throw new IllegalArgumentException("Complete premise list required");
            donors.add(premise);
        }
        Atom head = null;
        List<Atom> body = new ArrayList<>();
        for (Atom atom : branches.get(branch)) {
            work.run();
            if (atom.positive) {
                if (head != null) throw new IllegalArgumentException("One positive arithmetic conclusion required");
                head = atom;
            } else body.add(atom);
        }
        if (head == null || body.size() != donors.size()) throw new IllegalArgumentException("Complete definite rule premises required");
        Map<Integer, SemanticTermSnapshot> assignment = new LinkedHashMap<>();
        Set<Source> sources = new LinkedHashSet<>();
        sources.add(source);
        int depth = 0;
        for (int i = 0; i < body.size(); i++) {
            work.run();
            Atom atom = body.get(i);
            Premise premise = donors.get(i);
            if (!premise.fact.positive || !atom.predicate.equals(premise.fact.predicate)
                    || atom.arguments.size() != premise.fact.arguments.size())
                throw new IllegalArgumentException("Premise signature does not match");
            for (int j = 0; j < atom.arguments.size(); j++) {
                work.run();
                Slot slot = atom.arguments.get(j);
                SemanticTermSnapshot actual = premise.fact.arguments.get(j);
                if (slot.expression != null) throw new IllegalArgumentException("Arithmetic premise inversion remains unsupported");
                SemanticTermSnapshot expected = slot.variable < 0 ? slot.value : assignment.putIfAbsent(slot.variable, actual);
                if (expected != null && !expected.semanticallyEquals(actual))
                    throw new IllegalArgumentException("Inconsistent shared variable or constant");
            }
            for (Source original : premise.proof.sources) {
                work.run();
                sources.add(original);
            }
            depth = Math.max(depth, premise.proof.depth);
        }
        if (!variables.equals(assignment.keySet())) throw new IllegalArgumentException("Complete range-restricted rule assignment required");
        if (depth >= maximumDepth) throw new IllegalStateException("Arithmetic proof depth limit");
        List<SemanticTermSnapshot> values = new ArrayList<>();
        for (Slot slot : head.arguments) values.add(slot.instantiate(assignment, work));
        Premise conclusion = new Premise(Fact.ground(head.predicate, true, values),
                new ContextProductionCapture.Support(sources, depth + 1));
        return new Step(source, branch, donors, conclusion);
    }

    public List<Clause> instantiate(Map<Integer, SemanticTermSnapshot> assignment, int maximumWork) throws Exception {
        if (assignment == null || assignment.containsValue(null) || !variables.equals(assignment.keySet()))
            throw new IllegalArgumentException("Exact whole-rule assignment required");
        Map<Integer, SemanticTermSnapshot> inputs = new LinkedHashMap<>(assignment);
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        List<Clause> result = new ArrayList<>();
        for (List<Atom> branch : branches) {
            work.run();
            List<Fact> atoms = new ArrayList<>();
            for (Atom atom : branch) {
                work.run();
                List<SemanticTermSnapshot> arguments = new ArrayList<>();
                for (Slot slot : atom.arguments) arguments.add(slot.instantiate(inputs, work));
                atoms.add(Fact.ground(atom.predicate, atom.positive, arguments));
            }
            result.add(new Clause(atoms));
        }
        return Collections.unmodifiableList(result);
    }
}
