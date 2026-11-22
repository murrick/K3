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
            checkNativeClosureChain();
            checkAcyclicReuseWithWideInputs();
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
        checkFreshEqualityPatterns(pattern, positions, mind);
        checkConstructedValueReuse(pattern, positions, mind);
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

    private static void checkFreshEqualityPatterns(ContextSetPattern pattern, int[] positions, Mind mind)
            throws Exception {
        SemanticTermSnapshot known = SemanticTermSnapshot.capture(mind.getTerms().add("Named"));
        SemanticTermSnapshot fresh1 = SemanticTermSnapshot.capture(mind.getTerms().add("FreshOne"));
        SemanticTermSnapshot fresh2 = SemanticTermSnapshot.capture(mind.getTerms().add("FreshTwo"));
        List<SemanticTermSnapshot> values = java.util.Arrays.asList(known, fresh1, fresh2);
        Map<String, SemanticTermSnapshot> outcomes = new HashMap<>();
        for (int left = 0; left < values.size(); left++) {
            for (int right = 0; right < values.size(); right++) {
                // Rename anonymous values by first occurrence, preserving equality.
                int first = left == 0 ? 0 : 1;
                int second = right == 0 ? 0 : (left == 0 || left == right ? 1 : 2);
                Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
                assignment.put(positions[0], values.get(first));
                assignment.put(positions[1], values.get(second));
                outcomes.put(first + ":" + second, pattern.instantiate(assignment, () -> {}));
            }
        }
        java.util.Set<String> expected = new java.util.HashSet<>(
                java.util.Arrays.asList("0:0", "0:1", "1:0", "1:1", "1:2"));
        if (!outcomes.keySet().equals(expected))
            throw new AssertionError("Missing or extra equality pattern: " + outcomes.keySet());
        if (outcomes.get("1:1").semanticallyEquals(outcomes.get("1:2")))
            throw new AssertionError("Equal and distinct fresh assignments collapsed");
        if (!outcomes.get("0:1").semanticallyEquals(outcomes.get("1:0")))
            throw new AssertionError("Set equality must ignore member order");
    }

    private static void checkConstructedValueReuse(ContextSetPattern pattern, int[] positions, Mind mind)
            throws Exception {
        SemanticTermSnapshot seed = SemanticTermSnapshot.capture(mind.getTerms().add("Seed"));
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        List<SemanticTermSnapshot> history = new java.util.ArrayList<>(java.util.Arrays.asList(seed, marker));
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        assignment.put(positions[1], marker);
        SemanticTermSnapshot previous = seed;
        for (int depth = 1; depth <= 4; depth++) {
            assignment.put(positions[0], previous);
            SemanticTermSnapshot next = pattern.instantiate(assignment, () -> {});
            if (!SemanticTermSnapshot.isOrdinaryValue(next.materialize())
                    || history.stream().anyMatch(next::semanticallyEquals))
                throw new AssertionError("Expected a new admissible set at depth " + depth);
            Object[] expected = new Object[depth + 1];
            expected[0] = "Seed";
            java.util.Arrays.fill(expected, 1, expected.length, "Marker");
            if (!next.semanticallyEquals(SemanticTermSnapshot.capture(mind.getTerms().add(expected))))
                throw new AssertionError("Flat membership or marker multiplicity changed at depth " + depth);
            history.add(next);
            previous = next;
        }
        assignment.put(positions[0], previous);
        int[] work = { 5 };
        try {
            pattern.instantiate(assignment, () -> {
                if (--work[0] < 0) throw new IllegalStateException("Set substitution work limit");
            });
            throw new AssertionError("Expected the repeated members to consume the work budget");
        } catch (IllegalStateException limit) {
            if (!"Set substitution work limit".equals(limit.getMessage())) throw limit;
        }
        SemanticTermSnapshot retried = pattern.instantiate(assignment, () -> {});
        SemanticTermSnapshot expected = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"Seed", "Marker", "Marker", "Marker", "Marker", "Marker"}));
        if (!retried.semanticallyEquals(expected))
            throw new AssertionError("Budget refusal altered the input or subsequent substitution");
    }

    private static void checkNativeClosureChain() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        String rules = "!start(Seed);"
                + " !@x start(x)->middle([x,Marker]);"
                + " !@x middle(x)->finish([x,Marker]);";
        if (!mind.compile(rules)) throw new AssertionError("Native set chain fixture");
        SemanticTermSnapshot first = querySingleValue(mind, "?$x middle(x);");
        SemanticTermSnapshot second = querySingleValue(mind, "?$x finish(x);");
        SemanticTermSnapshot expected = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"Seed", "Marker"}));
        if (!first.semanticallyEquals(expected)) throw new AssertionError("First native set differs");
        if (first.semanticallyEquals(second)) throw new AssertionError("Native chain collapsed nested values");
        SemanticTermSnapshot flat = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"Seed", "Marker", "Marker"}));
        if (!flat.semanticallyEquals(second))
            throw new AssertionError("Native flattening baseline changed");
        List<org.kanger.interfaces.ITerm> members = second.materialize().semanticMembers();
        if (members.size() != 3 || members.stream().anyMatch(member -> member.getType() == org.kanger.enums.DataType.SET))
            throw new AssertionError("Expected flat native members including the repeated marker");
    }

    private static void checkAcyclicReuseWithWideInputs() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        String declaration = "!@x input(x)->output([x,Marker]);";
        if (!mind.compile(declaration + " !input([Seed,Marker]); !input([Seed,Marker,Marker]);"
                + " !input([Seed,Marker,Marker,Marker]);"))
            throw new AssertionError("Acyclic constructor fixture");
        if (!Boolean.TRUE.equals(mind.query("?$x output(x);", null, false)))
            throw new AssertionError("Acyclic constructor produced no values");
        List<SemanticTermSnapshot> outcomes = new java.util.ArrayList<>();
        for (org.kanger.interfaces.ITerm value : mind.getValues().getValues("x"))
            outcomes.add(SemanticTermSnapshot.capture(value));
        if (outcomes.size() != 3) throw new AssertionError("Distinct input multiplicities collapsed");
        for (int markers = 2; markers <= 4; markers++) {
            Object[] members = new Object[markers + 1];
            members[0] = "Seed";
            java.util.Arrays.fill(members, 1, members.length, "Marker");
            SemanticTermSnapshot expected = SemanticTermSnapshot.capture(mind.getTerms().add(members));
            if (outcomes.stream().filter(expected::semanticallyEquals).count() != 1)
                throw new AssertionError("Output did not preserve input multiplicity and add one member");
        }
    }

    private static SemanticTermSnapshot querySingleValue(Mind mind, String query) throws Exception {
        if (!Boolean.TRUE.equals(mind.query(query, null, false))) throw new AssertionError(query);
        List<org.kanger.interfaces.ITerm> values = mind.getValues().getValues("x");
        if (values.size() != 1) throw new AssertionError("Expected exactly one value for " + query);
        return SemanticTermSnapshot.capture(values.get(0));
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
