/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.math.BigInteger;
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
            checkNativeDiagonalGrowth();
            checkSymbolicIdentities();
            checkSharedRemainderEquality();
            checkCyclicSetEqualities();
            checkCancelledSetVariable();
            checkScaledSetEquality();
            checkUnequalSetCoefficientsWithFixedSide();
            checkSetCountDivisibility();
            checkIndependentUnequalEqualities();
            checkDependentUnequalEqualities();
            checkSameSignCoefficients();
            auditSameSignCoefficients();
            checkTwoPositionConjunction();
            checkOneUnknownInWidePattern();
            checkSetBindingPropagation();
            checkSharedUnknownPropagation();
            checkMultipleCancelledUnknowns();
            checkNativeConditionalCollision();
            checkConditionalCollisionCaptureBoundary();
            checkSourcedSetApplications();
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
        checkExplicitDomainClosure(setBranch, rule, mind, john, mary, instances);
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
        BigInteger large = BigInteger.ONE.shiftLeft(80);
        if (!symbolicCount(mind, "middle", large, null).equals(large.add(BigInteger.ONE)))
            throw new AssertionError("Fixed marker must add one member at arbitrary input width");
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
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        SemanticTermSnapshot seed = SemanticTermSnapshot.capture(mind.getTerms().add("Seed"));
        checkSymbolicMultiplicity(mind, second, marker, 0, 2);
        checkSymbolicMultiplicity(mind, second, seed, 1, 1);
        SemanticTermSnapshot sameWidth = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"Seed", "Seed", "Marker"}));
        if (sameWidth.semanticallyEquals(second)
                || !symbolicCount(mind, "finish", BigInteger.ZERO, marker).equals(BigInteger.ONE))
            throw new AssertionError("Same width must not hide distinct member counts");
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

    private static void checkNativeDiagonalGrowth() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!seed(Marker); !@x seed(x)->twice([x,x]);"
                + " !@x twice(x)->four([x,x]); !@x four(x)->eight([x,x]);"))
            throw new AssertionError("Native diagonal fixture");
        String[] outputs = {"twice", "four", "eight"};
        BigInteger width = BigInteger.ONE;
        for (int i = 0; i < outputs.length; i++) {
            width = symbolicCount(mind, outputs[i], width, null);
            SemanticTermSnapshot actual = querySingleValue(mind, "?$x " + outputs[i] + "(x);");
            Object[] markers = new Object[2 << i];
            java.util.Arrays.fill(markers, "Marker");
            SemanticTermSnapshot expected = SemanticTermSnapshot.capture(mind.getTerms().add(markers));
            if (!actual.semanticallyEquals(expected)
                    || !width.equals(BigInteger.valueOf(actual.materialize().semanticMembers().size())))
                throw new AssertionError("Native diagonal constructor did not preserve both occurrences: " + outputs[i]);
        }
    }

    private static void checkSymbolicIdentities() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x source(x)->p([x,Marker]), q([Marker,x]), r([x,x]), s([x,Other]);"))
            throw new AssertionError("Symbolic identity fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern first = java.util.Objects.requireNonNull(patterns.get("p"));
        if (!first.sameFlatExpression(patterns.get("q"), () -> {})
                || first.sameFlatExpression(patterns.get("r"), () -> {})
                || first.sameFlatExpression(patterns.get("s"), () -> {}))
            throw new AssertionError("Incorrect constructor identity");
        java.util.Set<Integer> variables = new java.util.HashSet<>();
        first.collectVariables(variables, () -> {});
        Map<Integer, SemanticTermSnapshot> assignment = java.util.Collections.singletonMap(
                variables.iterator().next(), SemanticTermSnapshot.capture(mind.getTerms().add("Fresh")));
        SemanticTermSnapshot value = first.instantiate(assignment, () -> {});
        for (String predicate : java.util.Arrays.asList("q", "r", "s")) {
            boolean equal = value.semanticallyEquals(patterns.get(predicate).instantiate(assignment, () -> {}));
            if (equal != "q".equals(predicate))
                throw new AssertionError("Identity check differs from concrete substitution: " + predicate);
        }
        Map<Integer, SemanticTermSnapshot> matching = java.util.Collections.singletonMap(
                variables.iterator().next(), SemanticTermSnapshot.capture(mind.getTerms().add("Marker")));
        SemanticTermSnapshot doubled = patterns.get("r").instantiate(matching, () -> {});
        if (!first.instantiate(matching, () -> {}).semanticallyEquals(doubled))
            throw new AssertionError("Different expressions should coincide at x=Marker");
        ContextSetPattern.SharedInputEquality.Kind always = ContextSetPattern.SharedInputEquality.Kind.ALWAYS;
        ContextSetPattern.SharedInputEquality.Kind never = ContextSetPattern.SharedInputEquality.Kind.NEVER;
        if (first.solveSharedInputEquality(patterns.get("q"), () -> {}).kind != always
                || first.solveSharedInputEquality(patterns.get("s"), () -> {}).kind != never)
            throw new AssertionError("Wrong unconditional equality result");
        for (ContextSetPattern.SharedInputEquality solution : java.util.Arrays.asList(
                first.solveSharedInputEquality(patterns.get("r"), () -> {}),
                patterns.get("r").solveSharedInputEquality(first, () -> {}))) {
            if (solution.kind != ContextSetPattern.SharedInputEquality.Kind.EXACT_MEMBERS
                    || solution.members.size() != 1
                    || !solution.members.get(0).semanticallyEquals(matching.values().iterator().next()))
                throw new AssertionError("Equality must require exactly one Marker member");
        }
        List<SemanticTermSnapshot> recovered = first.inferSingleInputMembers(value, () -> {})
                .orElseThrow(AssertionError::new);
        if (recovered.size() != 1 || !recovered.get(0).semanticallyEquals(assignment.values().iterator().next()))
            throw new AssertionError("Inversion did not remove the fixed marker");
        if (patterns.get("r").inferSingleInputMembers(value, () -> {}).isPresent())
            throw new AssertionError("A duplicate constructor cannot produce two different singleton counts");
        List<SemanticTermSnapshot> halved = patterns.get("r").inferSingleInputMembers(doubled, () -> {})
                .orElseThrow(AssertionError::new);
        if (halved.size() != 1 || !halved.get(0).semanticallyEquals(matching.values().iterator().next()))
            throw new AssertionError("Inversion did not divide repeated member counts");
    }

    private static void checkSharedRemainderEquality() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,Marker,Common]), q([y,Other,Common]);"))
            throw new AssertionError("Shared remainder fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), right = patterns.get("q");
        java.util.Set<Integer> leftPositions = new java.util.HashSet<>(), rightPositions = new java.util.HashSet<>();
        left.collectVariables(leftPositions, () -> {});
        right.collectVariables(rightPositions, () -> {});
        int x = leftPositions.iterator().next(), y = rightPositions.iterator().next();
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        SemanticTermSnapshot other = SemanticTermSnapshot.capture(mind.getTerms().add("Other"));
        for (ContextSetPattern.SharedRemainderEquality solution : java.util.Arrays.asList(
                left.solveSharedRemainderEquality(right, () -> {}),
                right.solveSharedRemainderEquality(left, () -> {}))) {
            List<SemanticTermSnapshot> xRequired = solution.requiredMembers.get(x);
            List<SemanticTermSnapshot> yRequired = solution.requiredMembers.get(y);
            if (xRequired.size() != 1 || yRequired.size() != 1
                    || !xRequired.get(0).semanticallyEquals(other) || !yRequired.get(0).semanticallyEquals(marker))
                throw new AssertionError("Common constant did not cancel or required members reversed");
        }
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        assignment.put(x, SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Other", "Fresh", "Fresh"})));
        assignment.put(y, SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Fresh", "Fresh"})));
        if (!left.instantiate(assignment, () -> {}).semanticallyEquals(right.instantiate(assignment, () -> {})))
            throw new AssertionError("Shared remainder failed concrete replay");
        assignment.put(y, marker);
        if (left.instantiate(assignment, () -> {}).semanticallyEquals(right.instantiate(assignment, () -> {})))
            throw new AssertionError("Removing only one remainder must break equality");
        List<SemanticTermSnapshot> domain = java.util.Arrays.asList(marker, other,
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker"})),
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Other"})),
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Fresh"})),
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Other", "Fresh"})));
        ContextSetPattern.SharedRemainderEquality condition = left.solveSharedRemainderEquality(right, () -> {});
        int equalPairs = 0;
        for (SemanticTermSnapshot xValue : domain) {
            for (SemanticTermSnapshot yValue : domain) {
                assignment.put(x, xValue);
                assignment.put(y, yValue);
                boolean expected = left.instantiate(assignment, () -> {})
                        .semanticallyEquals(right.instantiate(assignment, () -> {}));
                if (condition.matches(assignment, () -> {}) != expected
                        || assignment.get(x) != xValue || assignment.get(y) != yValue)
                    throw new AssertionError("Residual condition disagrees with concrete replay or changed bindings");
                if (expected) equalPairs++;
            }
        }
        if (equalPairs != 5) throw new AssertionError("Expected both scalar and singleton inputs, plus shared Fresh");
    }

    private static void checkCyclicSetEqualities() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y @z source(x,y,z)->p([x]), q([y]), r([z]), s([x,Marker]), t([z,Other]);"))
            throw new AssertionError("Cyclic equality fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern x = patterns.get("p"), y = patterns.get("q");
        ContextSetPattern z = patterns.get("r"), shifted = patterns.get("s");
        ContextSetPattern.SharedRemainderEquality xy = x.solveSharedRemainderEquality(y, () -> {});
        ContextSetPattern.SharedRemainderEquality yz = y.solveSharedRemainderEquality(z, () -> {});
        ContextSetPattern.SharedRemainderEquality zx = z.solveSharedRemainderEquality(shifted, () -> {});
        ContextSetPattern.SharedRemainderEquality reversed =
                shifted.solveSharedRemainderEquality(z, () -> {});
        ContextSetPattern.SharedRemainderEquality balanced = z.solveSharedRemainderEquality(x, () -> {});
        if (ContextSetPattern.SharedRemainderEquality.widthsConsistent(
                java.util.Arrays.asList(xy, yz, zx), () -> {})
                || ContextSetPattern.SharedRemainderEquality.widthsConsistent(
                        java.util.Arrays.asList(reversed, yz, xy), () -> {})
                || !ContextSetPattern.SharedRemainderEquality.widthsConsistent(
                        java.util.Arrays.asList(xy, yz, balanced), () -> {}))
            throw new AssertionError("Width offsets must reject only the inconsistent cycles in this fixture");

        ContextSetPattern.SharedRemainderEquality equalWidth =
                patterns.get("t").solveSharedRemainderEquality(shifted, () -> {});
        List<ContextSetPattern.SharedRemainderEquality> equalWidthCycle =
                java.util.Arrays.asList(xy, yz, equalWidth);
        if (!ContextSetPattern.SharedRemainderEquality.widthsConsistent(equalWidthCycle, () -> {}))
            throw new AssertionError("Replacing Other with Marker leaves total width unchanged");

        if (ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(equalWidthCycle, () -> {})
                || ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(
                        java.util.Arrays.asList(equalWidth, yz, xy), () -> {})
                || !ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(
                        java.util.Arrays.asList(equalWidth, xy), () -> {})
                || !ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(
                        java.util.Arrays.asList(xy, yz, balanced), () -> {}))
            throw new AssertionError("Member counts must detect the equal-width contradiction");

        java.util.Set<Integer> positions = new java.util.HashSet<>();
        x.collectVariables(positions, () -> {});
        int xPosition = positions.iterator().next();
        positions.clear();
        y.collectVariables(positions, () -> {});
        int yPosition = positions.iterator().next();
        positions.clear();
        z.collectVariables(positions, () -> {});
        int zPosition = positions.iterator().next();
        Map<Integer, SemanticTermSnapshot> witness = ContextSetPattern.SharedRemainderEquality.witness(
                java.util.Arrays.asList(yz, equalWidth), () -> {}).orElseThrow(AssertionError::new);
        if (!y.instantiate(witness, () -> {}).semanticallyEquals(z.instantiate(witness, () -> {}))
                || !patterns.get("t").instantiate(witness, () -> {})
                        .semanticallyEquals(shifted.instantiate(witness, () -> {}))
                || xy.matches(witness, () -> {}))
            throw new AssertionError("Constructed multiset witness disagrees with concrete equality");
        if (ContextSetPattern.SharedRemainderEquality.witness(equalWidthCycle, () -> {}).isPresent())
            throw new AssertionError("Inconsistent equal-width cycle returned a witness");
        Map<Integer, SemanticTermSnapshot> emptyWitness = ContextSetPattern.SharedRemainderEquality.witness(
                java.util.Arrays.asList(xy, yz, balanced), () -> {}).orElseThrow(AssertionError::new);
        if (emptyWitness.size() != 3 || !xy.matches(emptyWitness, () -> {}) || !yz.matches(emptyWitness, () -> {})
                || !balanced.matches(emptyWitness, () -> {}))
            throw new AssertionError("Constant-free component must have a complete empty-set assignment");

        List<SemanticTermSnapshot> domain = java.util.Arrays.asList(
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{})),
                SemanticTermSnapshot.capture(mind.getTerms().add("Marker")),
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Marker"})));
        boolean[] witnessedPairsOfConditions = new boolean[3];
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        for (SemanticTermSnapshot a : domain) {
            for (SemanticTermSnapshot b : domain) {
                for (SemanticTermSnapshot c : domain) {
                    assignment.put(xPosition, a);
                    assignment.put(yPosition, b);
                    assignment.put(zPosition, c);
                    SemanticTermSnapshot px = x.instantiate(assignment, () -> {});
                    SemanticTermSnapshot py = y.instantiate(assignment, () -> {});
                    SemanticTermSnapshot pz = z.instantiate(assignment, () -> {});
                    boolean first = px.semanticallyEquals(py);
                    boolean second = py.semanticallyEquals(pz);
                    boolean third = pz.semanticallyEquals(shifted.instantiate(assignment, () -> {}));
                    if (first != xy.matches(assignment, () -> {})
                            || second != yz.matches(assignment, () -> {})
                            || third != zx.matches(assignment, () -> {}))
                        throw new AssertionError("Symbolic equality disagrees with concrete substitution");
                    if (first && second && third) throw new AssertionError("Inconsistent cycle has a witness");
                    witnessedPairsOfConditions[0] |= first && second;
                    witnessedPairsOfConditions[1] |= second && third;
                    witnessedPairsOfConditions[2] |= third && first;
                }
            }
        }
        for (List<ContextSetPattern.SharedRemainderEquality> pair : java.util.Arrays.asList(
                java.util.Arrays.asList(xy, yz), java.util.Arrays.asList(yz, zx), java.util.Arrays.asList(zx, xy))) {
            if (!ContextSetPattern.SharedRemainderEquality.widthsConsistent(pair, () -> {}))
                throw new AssertionError("Width check rejected a pair with a concrete witness");
        }
        for (boolean witnessed : witnessedPairsOfConditions)
            if (!witnessed) throw new AssertionError("Each pair of conditions must have a witness");
    }

    private static void checkCancelledSetVariable() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y @w source(x,y,w)->p([x,w]), q([y,w]), r([w]), s([y,Marker]);"))
            throw new AssertionError("Cancelled variable fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), right = patterns.get("q");
        ContextSetPattern w = patterns.get("r"), shifted = patterns.get("s");
        ContextSetPattern.SharedRemainderEquality equality = left.solveSharedRemainderEquality(right, () -> {});
        ContextSetPattern.SharedRemainderEquality constrainsW = w.solveSharedRemainderEquality(shifted, () -> {});
        for (List<ContextSetPattern.SharedRemainderEquality> constraints : java.util.Arrays.asList(
                java.util.Collections.singletonList(equality), java.util.Arrays.asList(equality, constrainsW))) {
            Map<Integer, SemanticTermSnapshot> assignment =
                    ContextSetPattern.SharedRemainderEquality.witness(constraints, () -> {}).orElseThrow(AssertionError::new);
            if (assignment.size() != 3
                    || !left.instantiate(assignment, () -> {}).semanticallyEquals(right.instantiate(assignment, () -> {})))
                throw new AssertionError("Witness must bind positions of both original expressions");
            if (constraints.size() == 2
                    && !w.instantiate(assignment, () -> {}).semanticallyEquals(shifted.instantiate(assignment, () -> {})))
                throw new AssertionError("A cancelled position can still be constrained by another equality");
        }
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        w.collectVariables(positions, () -> {});
        int wPosition = positions.iterator().next();
        positions.clear();
        shifted.collectVariables(positions, () -> {});
        int yPosition = positions.iterator().next();
        List<ContextSetPattern.SharedRemainderEquality> constraints = java.util.Arrays.asList(equality, constrainsW);
        SemanticTermSnapshot fresh = SemanticTermSnapshot.capture(mind.getTerms().add("Fresh"));
        Map<Integer, SemanticTermSnapshot> fixed = new HashMap<>();
        fixed.put(yPosition, fresh);
        Map<Integer, SemanticTermSnapshot> anchored = ContextSetPattern.SharedRemainderEquality
                .witness(constraints, fixed, () -> {}).orElseThrow(AssertionError::new);
        if (anchored.get(yPosition) != fresh
                || !left.instantiate(anchored, () -> {}).semanticallyEquals(right.instantiate(anchored, () -> {}))
                || !w.instantiate(anchored, () -> {}).semanticallyEquals(shifted.instantiate(anchored, () -> {})))
            throw new AssertionError("Anchored witness must preserve the exact scalar and propagate its members");
        fixed.put(wPosition, SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker"})));
        if (ContextSetPattern.SharedRemainderEquality.witness(constraints, fixed, () -> {}).isPresent())
            throw new AssertionError("Conflicting fixed values returned a witness");
        fixed.remove(yPosition);
        fixed.put(wPosition, SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{})));
        if (ContextSetPattern.SharedRemainderEquality.witness(constraints, fixed, () -> {}).isPresent())
            throw new AssertionError("A fixed anchor requiring a negative count returned a witness");

        positions.clear();
        left.collectVariables(positions, () -> {});
        positions.remove(wPosition);
        int xPosition = positions.iterator().next();
        List<SemanticTermSnapshot> inputs = java.util.Arrays.asList(
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{})),
                SemanticTermSnapshot.capture(mind.getTerms().add("Marker")),
                fresh,
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Fresh"})),
                SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Marker"})));
        List<SemanticTermSnapshot> wCandidates = new java.util.ArrayList<>(inputs);
        wCandidates.add(SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Fresh", "Marker"})));
        wCandidates.add(SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Marker", "Marker"})));
        int compatible = 0;
        for (SemanticTermSnapshot yValue : inputs) {
            for (SemanticTermSnapshot wValue : wCandidates) {
                Map<Integer, SemanticTermSnapshot> candidate = new HashMap<>();
                candidate.put(yPosition, yValue);
                candidate.put(wPosition, wValue);
                java.util.Optional<Map<Integer, SemanticTermSnapshot>> symbolic =
                        ContextSetPattern.SharedRemainderEquality.witness(constraints, candidate, () -> {});
                boolean concrete = false;
                for (SemanticTermSnapshot xValue : inputs) {
                    candidate.put(xPosition, xValue);
                    concrete |= left.instantiate(candidate, () -> {}).semanticallyEquals(right.instantiate(candidate, () -> {}))
                            && w.instantiate(candidate, () -> {}).semanticallyEquals(shifted.instantiate(candidate, () -> {}));
                }
                if (symbolic.isPresent() != concrete)
                    throw new AssertionError("Fixed-input solver disagrees with bounded concrete enumeration");
                if (symbolic.isPresent()) {
                    Map<Integer, SemanticTermSnapshot> result = symbolic.get();
                    if (result.get(yPosition) != yValue || result.get(wPosition) != wValue
                            || !left.instantiate(result, () -> {}).semanticallyEquals(right.instantiate(result, () -> {}))
                            || !w.instantiate(result, () -> {}).semanticallyEquals(shifted.instantiate(result, () -> {})))
                        throw new AssertionError("Returned witness did not preserve its fixed inputs and both equalities");
                    compatible++;
                }
            }
        }
        if (compatible != inputs.size()) throw new AssertionError("Expected one compatible w for each fixed y");
    }

    private static void checkScaledSetEquality() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,Marker,Marker]), r([y,y,Marker,Other]),"
                + " s([x]), t([y,Marker]), u([y,Other]);"))
            throw new AssertionError("Scaled equality fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), doubled = patterns.get("q");
        ContextSetPattern.SharedRemainderEquality possible = left.solveSharedRemainderEquality(doubled, () -> {});
        ContextSetPattern.SharedRemainderEquality impossible =
                left.solveSharedRemainderEquality(patterns.get("r"), () -> {});
        Map<Integer, SemanticTermSnapshot> witness = ContextSetPattern.SharedRemainderEquality
                .witness(java.util.Collections.singletonList(possible), () -> {}).orElseThrow(AssertionError::new);
        List<ContextSetPattern.SharedRemainderEquality> rejected = java.util.Collections.singletonList(impossible);
        if (!possible.matches(witness, () -> {})
                || !left.instantiate(witness, () -> {}).semanticallyEquals(doubled.instantiate(witness, () -> {}))
                || impossible.matches(witness, () -> {})
                || ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(rejected, () -> {})
                || ContextSetPattern.SharedRemainderEquality.witness(rejected, () -> {}).isPresent())
            throw new AssertionError("Scaled equality must divide the multiplicity of each member separately");

        ContextSetPattern unitLeft = patterns.get("s"), unitMarker = patterns.get("t"), unitOther = patterns.get("u");
        ContextSetPattern.SharedRemainderEquality sameOffset = unitLeft.solveSharedRemainderEquality(unitMarker, () -> {});
        ContextSetPattern.SharedRemainderEquality differentOffset = unitLeft.solveSharedRemainderEquality(unitOther, () -> {});
        List<ContextSetPattern.SharedRemainderEquality> compatible = java.util.Arrays.asList(possible, sameOffset);
        List<ContextSetPattern.SharedRemainderEquality> collision = java.util.Arrays.asList(possible, differentOffset);
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        unitMarker.collectVariables(positions, () -> {});
        int y = positions.iterator().next();
        SemanticTermSnapshot fixed = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Fresh", "Fresh"}));
        Map<Integer, SemanticTermSnapshot> anchor = java.util.Collections.singletonMap(y, fixed);
        Map<Integer, SemanticTermSnapshot> together = ContextSetPattern.SharedRemainderEquality
                .witness(compatible, anchor, () -> {}).orElseThrow(AssertionError::new);
        if (together.get(y) != fixed
                || !left.instantiate(together, () -> {}).semanticallyEquals(doubled.instantiate(together, () -> {}))
                || !unitLeft.instantiate(together, () -> {}).semanticallyEquals(unitMarker.instantiate(together, () -> {})))
            throw new AssertionError("Scaled and unit constraints lost the shared fixed input");

        Map<Integer, SemanticTermSnapshot> unitWitness = ContextSetPattern.SharedRemainderEquality
                .witness(java.util.Collections.singletonList(differentOffset), anchor, () -> {}).orElseThrow(AssertionError::new);
        if (!unitLeft.instantiate(unitWitness, () -> {}).semanticallyEquals(unitOther.instantiate(unitWitness, () -> {}))
                || !ContextSetPattern.SharedRemainderEquality.widthsConsistent(collision, () -> {})
                || ContextSetPattern.SharedRemainderEquality.multiplicitiesConsistent(collision, () -> {})
                || ContextSetPattern.SharedRemainderEquality.witness(collision, anchor, () -> {}).isPresent())
            throw new AssertionError("Individually feasible scaled and unit conditions cannot share this binding");
    }

    private static void checkUnequalSetCoefficientsWithFixedSide() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,y]);"))
            throw new AssertionError("Unequal coefficient fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), right = patterns.get("q");
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        right.collectVariables(positions, () -> {});
        int y = positions.iterator().next();
        SemanticTermSnapshot two = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"Marker", "Marker"}));
        SemanticTermSnapshot one = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        Map<Integer, SemanticTermSnapshot> fixed = java.util.Collections.singletonMap(y, two);
        Map<Integer, SemanticTermSnapshot> witness = left.inferFromFixedPattern(right, fixed, () -> {})
                .orElseThrow(AssertionError::new);
        if (witness.get(y) != two
                || !left.instantiate(witness, () -> {}).semanticallyEquals(right.instantiate(witness, () -> {})))
            throw new AssertionError("Expected a concrete witness with the original fixed input");
        if (left.inferFromFixedPattern(right, java.util.Collections.singletonMap(y, one), () -> {}).isPresent())
            throw new AssertionError("Three copies cannot be produced by doubling any input");
    }

    private static void checkSetCountDivisibility() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,y,y,Marker,Other]),"
                + " r([y,y,y,y,Marker,Marker]), s([x,x,Marker]), t([y,y,y,y,Marker,Marker,Marker,Marker,Marker,Marker]);"))
            throw new AssertionError("Count divisibility fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), odd = patterns.get("q"), even = patterns.get("r");
        if (left.satisfiesCountDivisibility(odd, () -> {})
                || odd.satisfiesCountDivisibility(left, () -> {})
                || !left.satisfiesCountDivisibility(even, () -> {})
                || left.satisfiesCountDivisibility(patterns.get("s"), () -> {})
                || !left.satisfiesCountDivisibility(left, () -> {}))
            throw new AssertionError("Divisibility must account for each residual member and cancelled coefficients");

        java.util.Set<Integer> positions = new java.util.HashSet<>();
        even.collectVariables(positions, () -> {});
        int y = positions.iterator().next();
        SemanticTermSnapshot one = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        Map<Integer, SemanticTermSnapshot> witness = left.inferFromFixedPattern(
                even, java.util.Collections.singletonMap(y, one), () -> {}).orElseThrow(AssertionError::new);
        if (!left.instantiate(witness, () -> {}).semanticallyEquals(even.instantiate(witness, () -> {})))
            throw new AssertionError("Divisible case must retain its concrete control witness");

        for (Map<Integer, SemanticTermSnapshot> inferred : java.util.Arrays.asList(
                left.inferTwoInputs(even, () -> {}).orElseThrow(AssertionError::new),
                even.inferTwoInputs(left, () -> {}).orElseThrow(AssertionError::new))) {
            if (!left.instantiate(inferred, () -> {}).semanticallyEquals(even.instantiate(inferred, () -> {})))
                throw new AssertionError("Two unknowns must produce a witness in either orientation");
        }
        ContextSetPattern shifted = patterns.get("t");
        Map<Integer, SemanticTermSnapshot> shiftedWitness = left.inferTwoInputs(shifted, () -> {})
                .orElseThrow(AssertionError::new);
        if (!left.instantiate(shiftedWitness, () -> {}).semanticallyEquals(shifted.instantiate(shiftedWitness, () -> {})))
            throw new AssertionError("A negative intermediate count must be shifted to a nonnegative solution");
        if (left.inferTwoInputs(odd, () -> {}).isPresent())
            throw new AssertionError("Indivisible residual must have no concrete witness");
    }

    private static void checkIndependentUnequalEqualities() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,y,y]), r([x,x,x]),"
                + " s([y,y,y,y,Marker]), t([y,y,y,y,Marker,Marker]), u([x,x,x,Marker,Marker]);"))
            throw new AssertionError("Independent unequal equality fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q"), r = patterns.get("r");
        ContextSetPattern s = patterns.get("s"), t = patterns.get("t"), u = patterns.get("u");
        Map<Integer, SemanticTermSnapshot> first = p.inferTwoInputs(q, () -> {}).orElseThrow(AssertionError::new);
        Map<Integer, SemanticTermSnapshot> second = r.inferTwoInputs(s, () -> {}).orElseThrow(AssertionError::new);
        if (!p.instantiate(first, () -> {}).semanticallyEquals(q.instantiate(first, () -> {}))
                || !r.instantiate(second, () -> {}).semanticallyEquals(s.instantiate(second, () -> {}))
                || p.inferTwoEqualities(q, r, s, () -> {}).isPresent()
                || p.inferTwoEqualities(q, u, q, () -> {}).isPresent())
            throw new AssertionError("Joint counts must be both integral and nonnegative");

        for (Map<Integer, SemanticTermSnapshot> witness : java.util.Arrays.asList(
                p.inferTwoEqualities(q, r, t, () -> {}).orElseThrow(AssertionError::new),
                t.inferTwoEqualities(r, q, p, () -> {}).orElseThrow(AssertionError::new))) {
            if (!p.instantiate(witness, () -> {}).semanticallyEquals(q.instantiate(witness, () -> {}))
                    || !r.instantiate(witness, () -> {}).semanticallyEquals(t.instantiate(witness, () -> {})))
                throw new AssertionError("Joint witness must satisfy both original equalities in either orientation");
        }
    }

    private static void checkDependentUnequalEqualities() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,y,y,Marker,Marker]),"
                + " r([x]), s([y,y,Marker]), t([y,y,Other]), u([y,Marker]), v([y,Marker,Marker]), j([x,x,y]);"))
            throw new AssertionError("Dependent unequal equality fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q"), r = patterns.get("r");
        ContextSetPattern s = patterns.get("s"), t = patterns.get("t");
        for (Map<Integer, SemanticTermSnapshot> witness : java.util.Arrays.asList(
                p.inferTwoEqualities(q, r, s, () -> {}).orElseThrow(AssertionError::new),
                s.inferTwoEqualities(r, p, q, () -> {}).orElseThrow(AssertionError::new))) {
            if (!p.instantiate(witness, () -> {}).semanticallyEquals(q.instantiate(witness, () -> {}))
                    || !r.instantiate(witness, () -> {}).semanticallyEquals(s.instantiate(witness, () -> {})))
                throw new AssertionError("Proportional equations with matching residues must share a witness");
        }
        if (!r.inferTwoInputs(t, () -> {}).isPresent()
                || p.inferTwoEqualities(q, r, t, () -> {}).isPresent())
            throw new AssertionError("Proportional widths can hide inconsistent member residues");

        Map<Integer, SemanticTermSnapshot> identity = p.inferTwoEqualities(p, q, q, () -> {})
                .orElseThrow(AssertionError::new);
        SemanticTermSnapshot empty = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{}));
        if (identity.size() != 2 || !identity.values().stream().allMatch(empty::semanticallyEquals))
            throw new AssertionError("Two identities must return all variables bound");
        Map<Integer, SemanticTermSnapshot> oneIdentity = p.inferTwoEqualities(p, r, s, () -> {})
                .orElseThrow(AssertionError::new);
        if (!r.instantiate(oneIdentity, () -> {}).semanticallyEquals(s.instantiate(oneIdentity, () -> {})))
            throw new AssertionError("An identity must preserve the other equation");
        if (p.inferTwoEqualities(p, s, t, () -> {}).isPresent()
                || r.inferTwoEqualities(s, s, t, () -> {}).isPresent())
            throw new AssertionError("A false constant residual cannot be ignored after variable cancellation");

        ContextSetPattern one = patterns.get("u"), two = patterns.get("v"), withCommon = patterns.get("j");
        Map<Integer, SemanticTermSnapshot> oneFree = q.inferTwoEqualities(q, withCommon, two, () -> {})
                .orElseThrow(AssertionError::new);
        if (oneFree.size() != 2 || !oneFree.values().stream().anyMatch(empty::semanticallyEquals)
                || !withCommon.instantiate(oneFree, () -> {}).semanticallyEquals(two.instantiate(oneFree, () -> {})))
            throw new AssertionError("A one-position row must retain the free position");
        if (withCommon.inferTwoEqualities(one, q, q, () -> {}).isPresent())
            throw new AssertionError("A one-position row cannot assign a fractional multiplicity");
    }

    private static void checkSameSignCoefficients() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x,y,y,y,y]),"
                + " q([y,Marker]), r([y,Marker,Marker,Marker,Marker,Marker]);"))
            throw new AssertionError("Same-sign coefficient fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q"), r = patterns.get("r");
        if (!p.satisfiesCountDivisibility(q, () -> {}) || p.inferTwoInputs(q, () -> {}).isPresent()
                || p.inferTwoEqualities(q, q, p, () -> {}).isPresent())
            throw new AssertionError("The divisible equation 2x + 3y = 1 has no nonnegative solution");
        for (Map<Integer, SemanticTermSnapshot> witness : java.util.Arrays.asList(
                p.inferTwoEqualities(r, r, p, () -> {}).orElseThrow(AssertionError::new),
                r.inferTwoInputs(p, () -> {}).orElseThrow(AssertionError::new))) {
            if (!p.instantiate(witness, () -> {}).semanticallyEquals(r.instantiate(witness, () -> {})))
                throw new AssertionError("The nonnegative solution to 2x + 3y = 5 must replay");
        }
    }

    private static void auditSameSignCoefficients() throws Exception {
        for (int[] coefficients : new int[][]{{1, 4}, {2, 3}, {4, 6}, {5, 7}}) {
            int a = coefficients[0], b = coefficients[1];
            User user = new User();
            Mind mind = new Mind(user);
            user.setCurrentMind(mind);
            String leftArguments = String.join(",", java.util.Collections.nCopies(a, "x")) + ","
                    + String.join(",", java.util.Collections.nCopies(b + 1, "y"));
            StringBuilder rule = new StringBuilder("!@x @y source(x,y)->p([").append(leftArguments).append("])");
            for (int residual = 0; residual <= 12; residual++) {
                rule.append(", target").append(residual).append("([y");
                for (int count = 0; count < residual; count++) rule.append(",Marker");
                rule.append("])");
            }
            if (!mind.compile(rule.append(';').toString())) throw new AssertionError("Same-sign audit fixture");
            Map<String, ContextSetPattern> patterns = capturePatterns(mind);
            ContextSetPattern left = patterns.get("p");
            for (int residual = 0; residual <= 12; residual++) {
                ContextSetPattern right = patterns.get("target" + residual);
                boolean expected = false;
                for (int x = 0; x <= residual / a; x++) {
                    for (int y = 0; y <= residual / b; y++) expected |= a * x + b * y == residual;
                }
                for (java.util.Optional<Map<Integer, SemanticTermSnapshot>> result : java.util.Arrays.asList(
                        left.inferTwoInputs(right, () -> {}), right.inferTwoInputs(left, () -> {}))) {
                    if (result.isPresent() != expected)
                        throw new AssertionError("Set solver disagrees with enumeration for " + a + ", " + b + ", " + residual);
                    if (result.isPresent() && !left.instantiate(result.get(), () -> {})
                            .semanticallyEquals(right.instantiate(result.get(), () -> {})))
                        throw new AssertionError("Enumerated positive case did not replay");
                }
            }
        }
    }

    private static void checkTwoPositionConjunction() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x]), q([y,y,y,y]), r([x,x,x]),"
                + " s([y,y,y,y,Marker,Marker]), t([x]), u([y,Marker]), v([y,Other]), w([x,Marker,Marker]);"))
            throw new AssertionError("Conjunction fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q"), r = patterns.get("r");
        ContextSetPattern s = patterns.get("s"), t = patterns.get("t"), u = patterns.get("u");
        List<ContextSetPattern> left = java.util.Arrays.asList(p, r, t);
        List<ContextSetPattern> right = java.util.Arrays.asList(q, s, u);
        Map<Integer, SemanticTermSnapshot> witness = ContextSetPattern.inferTwoPositionConjunction(left, right, () -> {})
                .orElseThrow(AssertionError::new);
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).instantiate(witness, () -> {}).semanticallyEquals(right.get(i).instantiate(witness, () -> {})))
                throw new AssertionError("Conjunction witness disagrees with original expressions");
        }
        if (ContextSetPattern.inferTwoPositionConjunction(left,
                java.util.Arrays.asList(q, s, patterns.get("v")), () -> {}).isPresent())
            throw new AssertionError("A witness of the basis must fail an incompatible third row");

        if (!ContextSetPattern.inferTwoPositionConjunction(java.util.Arrays.asList(q, p, p),
                java.util.Arrays.asList(q, patterns.get("w"), patterns.get("w")), () -> {}).isPresent())
            throw new AssertionError("A variable absent from the selected basis must still be bound");
    }

    private static void checkOneUnknownInWidePattern() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @a @b @c @d @e @f source(x,a,b,c,d,e,f)->p([x,x,a,b,c,d,e,f]),"
                + " q([a,b,c,d,e,f,Marker,Marker]), r([a,b,c,d,e,f,Marker]), s([a,b,c,d,e,Marker,Marker]);"))
            throw new AssertionError("Wide pattern fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern left = patterns.get("p"), right = patterns.get("q");
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        right.collectVariables(positions, () -> {});
        Map<Integer, SemanticTermSnapshot> fixed = new HashMap<>();
        for (int position : positions) {
            fixed.put(position, SemanticTermSnapshot.capture(
                    mind.getTerms().add(new Object[]{"Fresh" + position, "Fresh" + position})));
        }
        Map<Integer, SemanticTermSnapshot> witness = left.inferFromFixedPattern(right, fixed, () -> {})
                .orElseThrow(AssertionError::new);
        if (fixed.size() != 6 || witness.size() != 7
                || !left.instantiate(witness, () -> {}).semanticallyEquals(right.instantiate(witness, () -> {})))
            throw new AssertionError("Wide pattern did not infer the seventh position");
        for (Map.Entry<Integer, SemanticTermSnapshot> value : fixed.entrySet()) {
            if (witness.get(value.getKey()) != value.getValue())
                throw new AssertionError("Wide pattern changed a fixed binding");
        }
        if (left.inferFromFixedPattern(patterns.get("r"), fixed, () -> {}).isPresent()
                || left.inferFromFixedPattern(patterns.get("s"), fixed, () -> {}).isPresent())
            throw new AssertionError("Wide pattern accepted an indivisible or missing fixed contribution");
    }

    private static void checkSetBindingPropagation() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y @z source(x,y,z)->p([x,x]), q([y,y,Marker,Marker]),"
                + " r([z]), s([x,Other]), t([y,y,Marker]);"))
            throw new AssertionError("Propagation fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q");
        ContextSetPattern r = patterns.get("r"), s = patterns.get("s");
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        q.collectVariables(positions, () -> {});
        int y = positions.iterator().next();
        SemanticTermSnapshot fresh = SemanticTermSnapshot.capture(mind.getTerms().add("Fresh"));
        Map<Integer, SemanticTermSnapshot> fixed = java.util.Collections.singletonMap(y, fresh);
        List<ContextSetPattern> left = java.util.Arrays.asList(r, p), right = java.util.Arrays.asList(s, q);
        for (ContextSetPattern.PropagatedBindings result : java.util.Arrays.asList(
                ContextSetPattern.propagateFixedPatterns(left, right, fixed, () -> {}),
                ContextSetPattern.propagateFixedPatterns(java.util.Arrays.asList(q, s),
                        java.util.Arrays.asList(p, r), fixed, () -> {}))) {
            if (result.status != ContextSetPattern.PropagatedBindings.Status.SOLVED
                    || result.assignment.size() != 3 || result.assignment.get(y) != fresh
                    || !p.instantiate(result.assignment, () -> {}).semanticallyEquals(q.instantiate(result.assignment, () -> {}))
                    || !r.instantiate(result.assignment, () -> {}).semanticallyEquals(s.instantiate(result.assignment, () -> {})))
                throw new AssertionError("Set binding chain must replay independent of equation order");
        }
        ContextSetPattern.PropagatedBindings conflict = ContextSetPattern.propagateFixedPatterns(
                java.util.Arrays.asList(r, p, p), java.util.Arrays.asList(s, q, patterns.get("t")), fixed, () -> {});
        ContextSetPattern.PropagatedBindings incomplete = ContextSetPattern.propagateFixedPatterns(
                left, right, java.util.Collections.emptyMap(), () -> {});
        if (conflict.status != ContextSetPattern.PropagatedBindings.Status.CONFLICT || !conflict.assignment.isEmpty()
                || incomplete.status != ContextSetPattern.PropagatedBindings.Status.INCOMPLETE || !incomplete.assignment.isEmpty())
            throw new AssertionError("Propagation must distinguish contradiction from lack of a starting binding");
    }

    private static void checkSharedUnknownPropagation() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y source(x,y)->p([x,x,y]), q([x,Marker,Other]),"
                + " r([x,y]), s([x,Marker]), t([x,Other]), u([y]);"))
            throw new AssertionError("Shared unknown fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        patterns.get("u").collectVariables(positions, () -> {});
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        Map<Integer, SemanticTermSnapshot> fixed = java.util.Collections.singletonMap(positions.iterator().next(), marker);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q"), r = patterns.get("r"), s = patterns.get("s");
        for (ContextSetPattern.PropagatedBindings result : java.util.Arrays.asList(
                ContextSetPattern.propagateFixedPatterns(java.util.Arrays.asList(r, p), java.util.Arrays.asList(s, q), fixed, () -> {}),
                ContextSetPattern.propagateFixedPatterns(java.util.Arrays.asList(q, s), java.util.Arrays.asList(p, r), fixed, () -> {}))) {
            if (result.status != ContextSetPattern.PropagatedBindings.Status.SOLVED
                    || !p.instantiate(result.assignment, () -> {}).semanticallyEquals(q.instantiate(result.assignment, () -> {}))
                    || !r.instantiate(result.assignment, () -> {}).semanticallyEquals(s.instantiate(result.assignment, () -> {})))
                throw new AssertionError("Shared unknown must be inferred after cancelling its common contribution");
        }
        ContextSetPattern.PropagatedBindings free = ContextSetPattern.propagateFixedPatterns(
                java.util.Collections.singletonList(r), java.util.Collections.singletonList(s), fixed, () -> {});
        SemanticTermSnapshot empty = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{}));
        if (free.status != ContextSetPattern.PropagatedBindings.Status.SOLVED
                || free.assignment.size() != 2 || !free.assignment.values().stream().anyMatch(empty::semanticallyEquals)
                || !r.instantiate(free.assignment, () -> {}).semanticallyEquals(s.instantiate(free.assignment, () -> {}))
                || ContextSetPattern.propagateFixedPatterns(java.util.Collections.singletonList(r),
                        java.util.Collections.singletonList(patterns.get("t")), fixed, () -> {}).status
                        != ContextSetPattern.PropagatedBindings.Status.CONFLICT)
            throw new AssertionError("Cancelled unknown must distinguish a free position from a constant contradiction");
        ContextSetPattern.PropagatedBindings unresolved = ContextSetPattern.propagateFixedPatterns(
                java.util.Arrays.asList(r, p), java.util.Arrays.asList(r, q), java.util.Collections.emptyMap(), () -> {});
        if (unresolved.status != ContextSetPattern.PropagatedBindings.Status.INCOMPLETE || !unresolved.assignment.isEmpty())
            throw new AssertionError("An identity must not choose a value while another row still constrains it");
    }

    private static void checkMultipleCancelledUnknowns() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @w @z source(x,w,z)->p([x,w,z]), q([w,z,Marker]),"
                + " r([w]), s([x,Other]), t([z]), u([w,Marker]), v([w,z,Other]);"))
            throw new AssertionError("Multiple cancelled positions fixture");
        Map<String, ContextSetPattern> patterns = capturePatterns(mind);
        ContextSetPattern p = patterns.get("p"), q = patterns.get("q");
        ContextSetPattern r = patterns.get("r"), s = patterns.get("s");
        ContextSetPattern t = patterns.get("t"), u = patterns.get("u");
        List<ContextSetPattern> left = java.util.Arrays.asList(t, r, p), right = java.util.Arrays.asList(u, s, q);
        ContextSetPattern.PropagatedBindings result = ContextSetPattern.propagateFixedPatterns(
                left, right, java.util.Collections.emptyMap(), () -> {});
        if (result.status != ContextSetPattern.PropagatedBindings.Status.SOLVED || result.assignment.size() != 3)
            throw new AssertionError("Cancelling two unbound positions must start the propagation chain");
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).instantiate(result.assignment, () -> {})
                    .semanticallyEquals(right.get(i).instantiate(result.assignment, () -> {})))
                throw new AssertionError("Propagated witness must replay every original row");
        }
        if (ContextSetPattern.propagateFixedPatterns(java.util.Collections.singletonList(q),
                java.util.Collections.singletonList(patterns.get("v")), java.util.Collections.emptyMap(), () -> {}).status
                != ContextSetPattern.PropagatedBindings.Status.CONFLICT)
            throw new AssertionError("Multiple cancelled positions cannot hide a constant mismatch");
    }

    private static Map<String, ContextSetPattern> capturePatterns(Mind mind) throws Exception {
        Map<String, ContextSetPattern> patterns = new HashMap<>();
        for (IRule item : mind.getRules()) {
            if (item.isGenerated()) continue;
            Rule rule = (Rule) item;
            for (List<Domain> branch : rule.getTree()) {
                for (Domain literal : branch) {
                    for (IArgument argument : literal.getArguments()) {
                        if (argument.getType() != ArgumentType.FUNCTION) continue;
                        patterns.put(literal.getPredicate(mind).getName(mind), ContextSetPattern.capture(
                                (Function) argument.getObject(mind), rule, mind, () -> {}));
                    }
                }
            }
        }
        return patterns;
    }

    private static void checkNativeConditionalCollision() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x trigger(x)->signed([x,Marker]), ~signed([x,x]);"))
            throw new AssertionError("Conditional collision fixture");

        List<ContextSetPattern> conclusions = new java.util.ArrayList<>();
        for (IRule item : mind.getRules()) {
            if (item.isGenerated()) continue;
            Rule rule = (Rule) item;
            for (List<Domain> branch : rule.getTree()) {
                for (Domain literal : branch) {
                    if (!"signed".equals(literal.getPredicate(mind).getName(mind))) continue;
                    Function constructor = (Function) literal.getArguments().get(0).getObject(mind);
                    conclusions.add(ContextSetPattern.capture(constructor, rule, mind, () -> {}));
                }
            }
        }
        if (conclusions.size() != 2) throw new AssertionError("Two signed constructors required");
        ContextSetPattern.SharedInputEquality condition =
                conclusions.get(0).solveSharedInputEquality(conclusions.get(1), () -> {});
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        if (condition.kind != ContextSetPattern.SharedInputEquality.Kind.EXACT_MEMBERS
                || condition.members.size() != 1 || !condition.members.get(0).semanticallyEquals(marker))
            throw new AssertionError("Wrong condition for the signed collision");
        if (!Boolean.TRUE.equals(mind.query("!trigger(Fresh);", null, false)))
            throw new AssertionError("Noncolliding native assertion refused");
        if (Boolean.TRUE.equals(mind.query("!trigger(Marker);", null, false)))
            throw new AssertionError("Native assertion admitted the predicted collision");
        if (!Boolean.TRUE.equals(mind.query("?trigger(Fresh);", null, false))
                || Boolean.TRUE.equals(mind.query("?trigger(Marker);", null, false)))
            throw new AssertionError("Refusal changed the previous accepted assertion or leaked the rejected one");
    }

    private static void checkConditionalCollisionCaptureBoundary() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        org.kanger.interfaces.IContextResults.Revision positive =
                new org.kanger.interfaces.IContextResults.Revision(new java.util.UUID(0, 1), 1);
        org.kanger.interfaces.IContextResults.Revision negative =
                new org.kanger.interfaces.IContextResults.Revision(new java.util.UUID(0, 2), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(mind);
                CollisionProofCapture capture = CollisionProofCapture.begin();
                TerminalSupportCapture terminal = TerminalSupportCapture.begin(mind)) {
            if (!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(mind, positive, 1,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x trigger(x)->signed([x,Marker]);"))
                    || !Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(mind, negative, 2,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x trigger(x)->~signed([x,x]);")))
                throw new AssertionError("Sourced rules refused");
            if (Boolean.TRUE.equals(mind.query("!trigger(Marker);", null, false)))
                throw new AssertionError("Expected set collision");
            List<DmzReplayProvenance.Binding> bindings = journal.settlementSnapshot().accepted;
            if (bindings.size() != 2 || !bindings.stream().anyMatch(binding ->
                    binding.context.equals(positive.getContextId()) && binding.revision == 1 && binding.sourceRule == 1)
                    || !bindings.stream().anyMatch(binding ->
                    binding.context.equals(negative.getContextId()) && binding.revision == 1 && binding.sourceRule == 2))
                throw new AssertionError("Original rule origins missing from the replay journal");
            List<CollisionProofCapture.Conflict> conflicts = capture.snapshot();
            if (conflicts.size() != 1) throw new AssertionError("Expected one captured collision");
            if (!conflicts.get(0).observations.captureActive
                    || !terminal.applicationGapSnapshot().contains("unsupported-argument")
                    || !conflicts.get(0).observations.applications.isEmpty())
                throw new AssertionError("Terminal capture boundary changed");
            for (CollisionProofCapture.Graph graph : java.util.Arrays.asList(
                    conflicts.get(0).left, conflicts.get(0).right)) {
                CollisionProofCapture.Node root = graph.nodes.get(graph.root);
                if (graph.nodes.size() != 1 || !root.causes.isEmpty() || !root.sources.isEmpty()
                        || !root.origin.contains("signed([Marker,Marker])"))
                    throw new AssertionError("Collision capture no longer has the documented diagnostic-only shape");
            }
        }
    }

    private static void checkSourcedSetApplications() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        ContextProductionCapture.Source positive = new ContextProductionCapture.Source(
                new java.util.UUID(0, 1), 1, 1, true, "Source");
        ContextProductionCapture.Source negative = new ContextProductionCapture.Source(
                new java.util.UUID(0, 2), 1, 2, false, "Negative");
        SemanticTermSnapshot marker = SemanticTermSnapshot.capture(mind.getTerms().add("Marker"));
        SemanticTermSnapshot repeated = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"Marker", "Marker"}));
        ContextProductionCapture.Fact trigger = ContextProductionCapture.Fact.ground(
                "trigger", true, java.util.Collections.singletonList(marker));
        ContextProductionCapture.Fact conclusion = ContextProductionCapture.Fact.ground(
                "signed", true, java.util.Collections.singletonList(repeated));
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind, 10000)) {
            String positiveRule = "!@x trigger(x)->signed([x,Marker]);";
            String negativeRule = "!@x trigger(x)->~signed([x,x]);";
            capture.replay(mind, positive, positiveRule);
            capture.replay(mind, negative, negativeRule);
            ContextProductionCapture.OperationApplications fresh =
                    capture.observeApplications(mind, "!trigger(Fresh);");
            if (!Boolean.TRUE.equals(fresh.result))
                throw new AssertionError("Expected the control operation to succeed");
            ContextProductionCapture.Fact freshTrigger = ContextProductionCapture.Fact.ground(
                    "trigger", true, java.util.Collections.singletonList(
                            SemanticTermSnapshot.capture(mind.getTerms().add("Fresh"))));
            List<ContextProductionCapture.Application> controls = fresh.applications;
            if (!findSignedPair(controls, freshTrigger, positive, negative).isEmpty())
                throw new AssertionError("Safe operation yielded a collision pair");
            for (ContextProductionCapture.Source source : java.util.Arrays.asList(positive, negative))
                if (controls.stream().noneMatch(application -> application.producers.contains(source)
                        && application.premises.size() == 1
                        && freshTrigger.equivalent(application.premises.get(0))))
                    throw new AssertionError("Control must exercise both rules");

            ContextProductionCapture.OperationApplications rejected =
                    capture.observeApplications(mind, "!trigger(Marker);");
            if (Boolean.TRUE.equals(rejected.result)) throw new AssertionError("Expected rejection");
            List<ContextProductionCapture.Application> pair =
                    findSignedPair(rejected.applications, trigger, positive, negative);
            if (!findSignedPair(fresh.applications, trigger, positive, negative).isEmpty())
                throw new AssertionError("Earlier operation acquired later evidence");
            if (pair.size() != 2 || !conclusion.equivalent(pair.get(0).conclusion)
                    || !conclusion.opposite().equivalent(pair.get(1).conclusion))
                throw new AssertionError("Expected both products for the same rejected operation");
            auditSetApplication(pair.get(0), positiveRule);
            auditSetApplication(pair.get(1), negativeRule);

        }
    }

    private static List<ContextProductionCapture.Application> findSignedPair(
            List<ContextProductionCapture.Application> operation, ContextProductionCapture.Fact trigger,
            ContextProductionCapture.Source positive, ContextProductionCapture.Source negative) {
        for (ContextProductionCapture.Application first : operation) {
            if (!first.conclusion.positive || !fromTrigger(first, trigger, positive)) continue;
            ContextProductionCapture.Fact opposite = first.conclusion.opposite();
            for (ContextProductionCapture.Application second : operation) {
                if (opposite.equivalent(second.conclusion) && fromTrigger(second, trigger, negative))
                    return java.util.Arrays.asList(first, second);
            }
        }
        return java.util.Collections.emptyList();
    }

    private static boolean fromTrigger(ContextProductionCapture.Application application,
            ContextProductionCapture.Fact trigger, ContextProductionCapture.Source source) {
        return application.premises.size() == 1 && trigger.equivalent(application.premises.get(0))
                && application.producers.equals(java.util.Collections.singletonList(source));
    }

    private static void auditSetApplication(ContextProductionCapture.Application application, String original)
            throws Exception {
        if (!original.equals(application.origin)) throw new AssertionError("Application origin changed");
        User user = new User();
        Mind replay = new Mind(user);
        user.setCurrentMind(replay);
        if (!replay.compile(original)) throw new AssertionError("Isolated declaration replay failed");
        Object[] conclusion = {application.conclusion.arguments.get(0).materialize()};
        if (replay.query("?signed(?);", conclusion, false) != null)
            throw new AssertionError("Conclusion already supported without the observed premise");
        ContextProductionCapture.Fact premise = application.premises.get(0);
        if (!Boolean.TRUE.equals(replay.query(premise.assertion(),
                new Object[]{premise.arguments.get(0).materialize()}, false)))
            throw new AssertionError("Isolated premise replay failed");
        if (!Boolean.valueOf(application.conclusion.positive)
                .equals(replay.query("?signed(?);", conclusion, false)))
            throw new AssertionError("Independent application replay disagrees with observed sign or set");
    }

    private static void checkSymbolicMultiplicity(Mind mind, SemanticTermSnapshot set,
            SemanticTermSnapshot leaf, int inputCount, int expected) throws Exception {
        BigInteger first = symbolicCount(mind, "middle", BigInteger.valueOf(inputCount), leaf);
        BigInteger second = symbolicCount(mind, "finish", first, leaf);
        long observed = set.materialize().semanticMembers().stream()
                .filter(member -> member.equalsTo(leaf.materialize())).count();
        if (!second.equals(BigInteger.valueOf(expected)) || observed != expected)
            throw new AssertionError("Symbolic count disagrees with native set membership");
    }

    private static BigInteger symbolicCount(Mind mind, String predicate, BigInteger input,
            SemanticTermSnapshot member) throws Exception {
        for (IRule item : mind.getRules()) {
            if (item.isGenerated()) continue;
            Rule rule = (Rule) item;
            for (List<Domain> branch : rule.getTree()) {
                for (Domain literal : branch) {
                    if (!predicate.equals(literal.getPredicate(mind).getName(mind))) continue;
                    for (IArgument argument : literal.getArguments()) {
                        if (argument.getType() != ArgumentType.FUNCTION) continue;
                        ContextSetPattern constructor = ContextSetPattern.capture(
                                (Function) argument.getObject(mind), rule, mind, () -> {});
                        java.util.Set<Integer> positions = new java.util.HashSet<>();
                        constructor.collectVariables(positions, () -> {});
                        if (positions.size() != 1) throw new AssertionError("One input variable required");
                        Map<Integer, BigInteger> counts =
                                java.util.Collections.singletonMap(positions.iterator().next(), input);
                        return member == null ? constructor.resultWidth(counts, () -> {})
                                : constructor.resultMultiplicity(member, counts, () -> {});
                    }
                }
            }
        }
        throw new AssertionError("Missing constructor " + predicate);
    }

    private static SemanticTermSnapshot querySingleValue(Mind mind, String query) throws Exception {
        if (!Boolean.TRUE.equals(mind.query(query, null, false))) throw new AssertionError(query);
        List<org.kanger.interfaces.ITerm> values = mind.getValues().getValues("x");
        if (values.size() != 1) throw new AssertionError("Expected exactly one value for " + query);
        return SemanticTermSnapshot.capture(values.get(0));
    }

    private static void checkExplicitDomainClosure(List<Domain> branch, Rule rule, Mind mind,
            SemanticTermSnapshot john, SemanticTermSnapshot mary,
            List<ContextProductionCapture.Clause> instances) throws Exception {
        List<SemanticTermSnapshot> domain = java.util.Arrays.asList(john, mary);
        SemanticTermSnapshot outside = ContextProductionCapture.Clause.firstValueOutsideDomain(
                instances, domain, () -> {}).orElseThrow(AssertionError::new);
        SemanticTermSnapshot repeatedJohn = SemanticTermSnapshot.capture(
                mind.getTerms().add(new Object[]{"John", "John"}));
        if (!outside.semanticallyEquals(repeatedJohn))
            throw new AssertionError("Closure check did not return the first constructed value");

        SemanticTermSnapshot emptySet = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[0]));
        List<SemanticTermSnapshot> closed = java.util.Collections.singletonList(emptySet);
        List<ContextProductionCapture.Clause> closedInstances =
                ContextProductionCapture.Clause.enumerateSetInstances(branch, rule, mind, closed, 1, () -> {});
        if (ContextProductionCapture.Clause.firstValueOutsideDomain(closedInstances, closed, () -> {}).isPresent())
            throw new AssertionError("The empty set is closed under self-concatenation");
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
