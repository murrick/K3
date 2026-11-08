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
        private final List<Clause> primaryDeclaration;

        private Premise(Fact fact, ContextProductionCapture.Support proof) {
            this(fact, proof, null);
        }

        private Premise(Fact fact, ContextProductionCapture.Support proof, List<Clause> primaryDeclaration) {
            this.fact = fact;
            this.proof = proof;
            this.primaryDeclaration = primaryDeclaration == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(primaryDeclaration));
        }

        public static Premise primary(Mind mind, ContextProductionInventory inventory,
                                      long rule, int clause, int maximumWork) throws Exception {
            ContextArithmeticRuleInspection declaration = inspect(mind, inventory, rule, maximumWork);
            if (!declaration.variables.isEmpty()) throw new IllegalArgumentException("Ground primary unit required");
            List<Clause> instances = declaration.instantiate(Collections.emptyMap(), maximumWork);
            if (clause < 0 || clause >= instances.size()) throw new IllegalArgumentException("Exact primary clause required");
            Clause selected = instances.get(clause);
            Fact fact = selected.literals.size() == 1 ? selected.literals.get(0).fact() : null;
            if (fact == null) throw new IllegalArgumentException("Ground primary unit required");
            return new Premise(fact, new ContextProductionCapture.Support(Collections.singleton(declaration.source), 0), instances);
        }

        public static Premise primary(ContextProductionNativeBinding.Snapshot snapshot, Source source, int clause) {
            for (ContextProductionNativeBinding.Entry entry : snapshot.primary) {
                if (!entry.source.equals(source)) continue;
                if (clause < 0 || clause >= entry.clauses.size()) throw new IllegalArgumentException("Exact primary clause required");
                Clause selected = entry.clauses.get(clause);
                Fact fact = selected.literals.size() == 1 ? selected.literals.get(0).fact() : null;
                if (fact == null) throw new IllegalArgumentException("Ground primary unit required");
                return new Premise(fact, new ContextProductionCapture.Support(
                        Collections.singleton(entry.source), 0), entry.clauses);
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
        return apply(branch, premises, maximumDepth, ContextGroundProductionRounds.budget(maximumWork));
    }

    private Step apply(int branch, List<Premise> premises, int maximumDepth, Runnable work) throws Exception {
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

    static void requireCoverage(Set<Source> owned, List<ContextArithmeticRuleInspection> rules,
                                List<Premise> seeds, Runnable work) throws Exception {
        Map<Source, ContextArithmeticRuleInspection> declarations = new HashMap<>();
        for (ContextArithmeticRuleInspection rule : rules) {
            work.run();
            if (!owned.contains(rule.source)) throw new IllegalArgumentException("Declaration belongs to another owner");
            if (declarations.put(rule.source, rule) != null) throw new IllegalArgumentException("Duplicate whole declaration");
        }
        for (Source source : owned) {
            work.run();
            List<Premise> primary = new ArrayList<>();
            for (Premise seed : seeds) {
                work.run();
                if (seed.primaryDeclaration != null && seed.proof.depth == 0
                        && seed.proof.sources.equals(Collections.singleton(source))) primary.add(seed);
            }
            List<Fact> required = new ArrayList<>();
            ContextArithmeticRuleInspection declaration = declarations.get(source);
            if (declaration != null) {
                if (declaration.variables.isEmpty()) {
                    for (List<Atom> branch : declaration.branches) {
                        work.run();
                        if (branch.size() == 1) {
                            Atom atom = branch.get(0);
                            List<SemanticTermSnapshot> values = new ArrayList<>();
                            for (Slot slot : atom.arguments) values.add(slot.instantiate(Collections.emptyMap(), work));
                            required.add(Fact.ground(atom.predicate, atom.positive, values));
                        }
                    }
                }
            } else {
                if (primary.isEmpty()) throw new IllegalArgumentException("Missing whole original declaration");
                for (Clause clause : primary.get(0).primaryDeclaration) {
                    work.run();
                    Fact fact = clause.literals.size() == 1 ? clause.literals.get(0).fact() : null;
                    if (fact == null) throw new IllegalArgumentException("Missing non-unit original declaration");
                    required.add(fact);
                }
            }
            for (Fact fact : required) {
                boolean found = false;
                for (Premise seed : primary) {
                    work.run();
                    found |= fact.equivalent(seed.fact);
                }
                if (!found) throw new IllegalArgumentException("Missing primary unit of the whole original Source");
            }
        }
    }

    void preflight(Runnable work) {
        for (List<Atom> branch : branches) {
            int heads = 0;
            Set<Integer> bound = new LinkedHashSet<>();
            for (Atom atom : branch) {
                work.run();
                if (atom.positive) heads++;
                else for (Slot slot : atom.arguments) {
                    work.run();
                    if (slot.expression != null) throw new IllegalArgumentException("Arithmetic body inversion remains unsupported");
                    if (slot.variable >= 0) bound.add(slot.variable);
                }
            }
            if (heads != 1 || !bound.equals(variables))
                throw new IllegalArgumentException("Complete range-restricted definite arithmetic rule required");
        }
    }

    private static boolean match(Atom atom, Premise premise, Map<Integer, SemanticTermSnapshot> assignment, Runnable work) {
        work.run();
        if (!premise.fact.positive || !atom.predicate.equals(premise.fact.predicate)
                || atom.arguments.size() != premise.fact.arguments.size()) return false;
        for (int i = 0; i < atom.arguments.size(); i++) {
            work.run();
            Slot slot = atom.arguments.get(i);
            SemanticTermSnapshot actual = premise.fact.arguments.get(i);
            SemanticTermSnapshot expected = slot.variable < 0 ? slot.value : assignment.putIfAbsent(slot.variable, actual);
            if (expected != null && !expected.semanticallyEquals(actual)) return false;
        }
        return true;
    }

    void produce(List<Premise> frozen, int maximumDepth, Runnable work, java.util.function.Consumer<Step> output) throws Exception {
        for (int branch = 0; branch < branches.size(); branch++) {
            List<Atom> body = new ArrayList<>();
            for (Atom atom : branches.get(branch)) {
                work.run();
                if (!atom.positive) body.add(atom);
            }
            int[] next = new int[body.size()];
            List<Premise> donors = new ArrayList<>(Collections.nCopies(body.size(), null));
            List<Map<Integer, SemanticTermSnapshot>> assignments = new ArrayList<>();
            assignments.add(Collections.emptyMap());
            int depth = 0;
            while (depth >= 0) {
                work.run();
                if (depth == body.size()) {
                    output.accept(apply(branch, donors, maximumDepth, work));
                    depth--;
                } else if (next[depth] == frozen.size()) {
                    next[depth] = 0;
                    depth--;
                } else {
                    Premise donor = frozen.get(next[depth]++);
                    Map<Integer, SemanticTermSnapshot> assignment = new LinkedHashMap<>(assignments.get(depth));
                    if (match(body.get(depth), donor, assignment, work)) {
                        donors.set(depth, donor);
                        if (assignments.size() == depth + 1) assignments.add(assignment);
                        else assignments.set(depth + 1, assignment);
                        depth++;
                    }
                }
            }
        }
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
