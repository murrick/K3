/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Verify shared variable identities and native set equality after substitution. */
public final class ContextSetPatternRunner {
    public static void main(String[] args) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y a(x,y)->b([x,y]);")) throw new AssertionError("Fixture");
        for (IRule item : mind.getRules()) {
            if (item.isGenerated()) continue;
            check((Rule) item, mind);
            System.out.println("CONTEXT_SET_PATTERN_PASS");
            return;
        }
        throw new AssertionError("Missing native rule");
    }

    private static void check(Rule rule, Mind mind) throws Exception {
        ContextSetPattern pattern = null;
        int[] positions = new int[2];
        boolean body = false;
        List<Domain> setBranch = null;
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                if ("a".equals(domain.getPredicate(mind).getName(mind))) {
                    for (int i = 0; i < positions.length; i++) {
                        IArgument argument = domain.getArguments().get(i);
                        positions[i] = ((TVariable) argument.getObject(mind)).getIndex() - rule.getVarIndex();
                    }
                    body = true;
                }
                for (IArgument argument : domain.getArguments()) {
                    if (argument.getType() != ArgumentType.FUNCTION) continue;
                    Function function = (Function) argument.getObject(mind);
                    if (ContextSetPattern.isConstructor(function, mind)) {
                        pattern = ContextSetPattern.capture(function, rule, mind, () -> {});
                        setBranch = branch;
                    }
                }
            }
        }
        if (pattern == null || !body || positions[0] == positions[1]) throw new AssertionError("Missing shared scope");
        Map<Integer, SemanticTermSnapshot> values = new HashMap<>();
        SemanticTermSnapshot john = SemanticTermSnapshot.capture(mind.getTerms().add("John"));
        SemanticTermSnapshot mary = SemanticTermSnapshot.capture(mind.getTerms().add("Mary"));
        values.put(positions[0], john);
        values.put(positions[1], mary);
        SemanticTermSnapshot first = pattern.instantiate(values, () -> {});
        SemanticTermSnapshot expected = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"John", "Mary"}));
        if (!first.semanticallyEquals(expected)) throw new AssertionError("Native set changed after substitution");
        ContextProductionCapture.Clause original = ContextProductionCapture.Clause.instantiateSetClause(
                setBranch, rule, mind, values, () -> {});
        checkClause(original, john, mary, first);
        values.put(positions[0], mary);
        values.put(positions[1], john);
        if (!first.semanticallyEquals(pattern.instantiate(values, () -> {})))
            throw new AssertionError("Permutation changed set equality");
        ContextProductionCapture.Clause swapped = ContextProductionCapture.Clause.instantiateSetClause(
                setBranch, rule, mind, values, () -> {});
        checkClause(swapped, mary, john, first);
        if (original.equivalent(swapped)) throw new AssertionError("Equal sets hid distinct clause bindings");
        List<ContextProductionCapture.Clause> instances = ContextProductionCapture.Clause.enumerateSetInstances(
                setBranch, rule, mind, java.util.Arrays.asList(john, mary), 4, () -> {});
        if (instances.size() != 4 || instances.stream().filter(original::equivalent).count() != 1
                || instances.stream().filter(swapped::equivalent).count() != 1)
            throw new AssertionError("Finite enumeration lost correlated assignments");
        try {
            ContextProductionCapture.Clause.enumerateSetInstances(
                    setBranch, rule, mind, java.util.Arrays.asList(john, mary), 3, () -> {});
            throw new AssertionError("Instance limit was ignored");
        } catch (IllegalStateException limit) {
            if (!"Set clause instance limit".equals(limit.getMessage())) throw limit;
        }
        auditFiniteDomain(setBranch, rule, mind);
        checkFreshQuotientCounterexample(pattern, positions, mind);
        values.remove(positions[1]);
        try {
            pattern.instantiate(values, () -> {});
            throw new AssertionError("Missing variable was accepted");
        } catch (IllegalArgumentException expectedFailure) {
            // A set constructor cannot guess a missing assignment.
        }
    }
    private static void checkFreshQuotientCounterexample(ContextSetPattern pattern, int[] positions, Mind mind)
            throws Exception {
        // Neither value occurs in the declaration. The scalar quotient gives
        // both the same fresh representative, regardless of actual equality.
        SemanticTermSnapshot first = SemanticTermSnapshot.capture(mind.getTerms().add("FreshOne"));
        SemanticTermSnapshot second = SemanticTermSnapshot.capture(mind.getTerms().add("FreshTwo"));
        if (first.semanticallyEquals(second)) throw new AssertionError("Distinct fresh values required");
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        assignment.put(positions[0], first);
        assignment.put(positions[1], first);
        SemanticTermSnapshot equalMembers = pattern.instantiate(assignment, () -> {});
        assignment.put(positions[1], second);
        SemanticTermSnapshot distinctMembers = pattern.instantiate(assignment, () -> {});
        if (equalMembers.semanticallyEquals(distinctMembers))
            throw new AssertionError("Native set construction lost the distinction between fresh assignments");
    }

    private static void auditFiniteDomain(List<Domain> branch, Rule rule, Mind mind) throws Exception {
        List<String> names = java.util.Arrays.asList("John", "Mary", "Fresh");
        List<SemanticTermSnapshot> domain = new java.util.ArrayList<>();
        for (String name : names) domain.add(SemanticTermSnapshot.capture(mind.getTerms().add(name)));
        List<ContextProductionCapture.Clause> actual = ContextProductionCapture.Clause.enumerateSetInstances(
                branch, rule, mind, domain, 9, () -> {});
        java.util.Set<List<String>> remaining = new java.util.HashSet<>();
        for (String left : names) {
            for (String right : names) remaining.add(java.util.Arrays.asList(left, right));
        }
        for (ContextProductionCapture.Clause instance : actual) {
            ContextProductionCapture.Fact body = instance.literals.stream()
                    .filter(atom -> "a".equals(atom.predicate)).findFirst().orElseThrow(AssertionError::new).fact();
            String left = body.arguments.get(0).materialize().toString();
            String right = body.arguments.get(1).materialize().toString();
            if (!remaining.remove(java.util.Arrays.asList(left, right)))
                throw new AssertionError("Duplicate or out-of-domain assignment: " + left + ", " + right);
            SemanticTermSnapshot expected = SemanticTermSnapshot.capture(
                    mind.getTerms().add(new Object[]{left, right}));
            checkClause(instance, domain.get(names.indexOf(left)), domain.get(names.indexOf(right)), expected);
        }
        if (!remaining.isEmpty()) throw new AssertionError("Missing assignments: " + remaining);
    }

    private static void checkClause(ContextProductionCapture.Clause clause, SemanticTermSnapshot left,
            SemanticTermSnapshot right, SemanticTermSnapshot set) {
        if (!clause.variables.isEmpty() || clause.literals.size() != 2)
            throw new AssertionError("Expected a ground clause with both literals");
        for (ContextProductionCapture.Atom atom : clause.literals) {
            ContextProductionCapture.Fact fact = atom.fact();
            if ("a".equals(fact.predicate) && !fact.positive
                    && fact.arguments.get(0).semanticallyEquals(left)
                    && fact.arguments.get(1).semanticallyEquals(right)) continue;
            if ("b".equals(fact.predicate) && fact.positive
                    && fact.arguments.get(0).semanticallyEquals(set)) continue;
            throw new AssertionError("Wrong substitution or polarity: " + fact.predicate);
        }
    }
}
