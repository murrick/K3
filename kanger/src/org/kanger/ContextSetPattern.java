/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.Term;
import org.kanger.units.TVariable;

/** A flat native set template with variable positions relative to the owning rule. */
final class ContextSetPattern {
    private static final class Member {
        final int variable;
        final SemanticTermSnapshot constant;

        Member(int variable, SemanticTermSnapshot constant) {
            this.variable = variable;
            this.constant = constant;
        }
    }

    private final List<Member> members;

    private ContextSetPattern(List<Member> members) {
        this.members = Collections.unmodifiableList(members);
    }

    static boolean isConstructor(Function function, Mind mind) throws Exception {
        return function.getBinding() == FunctionBinding.INFRASTRUCTURE
                && "_set".equals(function.getName(mind).getValue());
    }

    static ContextSetPattern capture(Function function, Rule owner, Mind mind, Runnable work)
            throws Exception {
        if (!isConstructor(function, mind)) throw new IllegalArgumentException("Native set constructor required");
        int arity = function.getRange();
        if (arity < 1 || function.getArguments().size() != arity + 1)
            throw new IllegalStateException("Invalid native set constructor arguments");
        List<Member> members = new ArrayList<>();
        // The final function argument is its result slot, outside the set members.
        for (int i = 0; i < arity; i++) {
            work.run();
            IArgument argument = function.getArguments().get(i);
            if (argument.getType() == ArgumentType.TVARIABLE) {
                members.add(new Member(variablePosition(argument, owner, mind), null));
            } else if (argument.getType() == ArgumentType.TERM
                    && SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))) {
                members.add(new Member(-1, SemanticTermSnapshot.capture(argument.getValue(mind))));
            } else {
                throw new IllegalStateException("Native unordered-set patterns require structured term qualification");
            }
        }
        return new ContextSetPattern(members);
    }

    void collectVariables(java.util.Set<Integer> variables, Runnable work) {
        for (Member member : members) {
            work.run();
            if (member.variable >= 0) variables.add(member.variable);
        }
    }

    static int variablePosition(IArgument argument, Rule owner, Mind mind) throws Exception {
        TVariable variable = (TVariable) argument.getObject(mind);
        int index = variable.getIndex() - owner.getVarIndex();
        if (variable.getRuleId() != owner.getId() || index < 0)
            throw new IllegalStateException("Variable does not belong to the rule");
        return index;
    }

    static SemanticTermSnapshot requireBinding(int index, Map<Integer, SemanticTermSnapshot> assignment) {
        SemanticTermSnapshot value = assignment.get(index);
        if (value == null) throw new IllegalArgumentException("Missing binding for rule variable " + index);
        return value;
    }

    /** Exact result width given flattened input widths for interval-free values. */
    BigInteger resultWidth(Map<Integer, BigInteger> inputWidths, Runnable work) {
        return sumOccurrences(inputWidths, null, work);
    }

    /** Exact multiplicity of one flattened member given its count in each input. */
    BigInteger resultMultiplicity(SemanticTermSnapshot value, Map<Integer, BigInteger> inputCounts,
            Runnable work) {
        if (value == null || value.getType() == DataType.SET || value.getType() == DataType.INTERVAL)
            throw new IllegalArgumentException("Flattened set member required");
        return sumOccurrences(inputCounts, value, work);
    }

    private BigInteger sumOccurrences(Map<Integer, BigInteger> inputCounts,
            SemanticTermSnapshot value, Runnable work) {
        BigInteger total = BigInteger.ZERO;
        for (Member member : members) {
            work.run();
            if (member.variable >= 0) {
                BigInteger width = inputCounts.get(member.variable);
                if (width == null || width.signum() < 0)
                    throw new IllegalArgumentException("Missing or negative set input count: " + member.variable);
                total = total.add(width);
            } else {
                BigInteger[] width = { BigInteger.ZERO };
                member.constant.visitFlatSetMembers(leaf -> {
                    if (value == null || leaf.semanticallyEquals(value)) width[0] = width[0].add(BigInteger.ONE);
                }, work);
                total = total.add(width[0]);
            }
        }
        return total;
    }

    /** An identity of flat constructors whose rule-relative positions are aligned. */
    boolean sameFlatExpression(ContextSetPattern other, Runnable work) {
        Map<Integer, Integer> coefficients = new HashMap<>();
        List<SemanticTermSnapshot> constants = new ArrayList<>();
        collectExpression(coefficients, constants, work);
        Map<Integer, Integer> otherCoefficients = new HashMap<>();
        List<SemanticTermSnapshot> remaining = new ArrayList<>();
        other.collectExpression(otherCoefficients, remaining, work);
        if (!coefficients.equals(otherCoefficients) || constants.size() != remaining.size()) return false;
        boolean[] used = new boolean[remaining.size()];
        for (SemanticTermSnapshot constant : constants) {
            boolean matched = false;
            for (int i = 0; i < remaining.size(); i++) {
                work.run();
                if (!used[i] && constant.semanticallyEquals(remaining.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private void collectExpression(Map<Integer, Integer> coefficients,
            List<SemanticTermSnapshot> constants, Runnable work) {
        for (Member member : members) {
            work.run();
            if (member.variable >= 0) coefficients.merge(member.variable, 1, Integer::sum);
            else member.constant.visitFlatSetMembers(constants::add, work);
        }
    }

    /** One concrete witness for a single flat equality with two nonzero coefficients. */
    Optional<Map<Integer, SemanticTermSnapshot>> inferTwoInputs(ContextSetPattern other, Runnable work)
            throws Exception {
        Map<Integer, Integer> difference = new HashMap<>();
        List<SemanticTermSnapshot> leftConstants = new ArrayList<>(), rightConstants = new ArrayList<>();
        collectDifference(other, difference, leftConstants, rightConstants, work);
        Map<Integer, List<ITerm>> values = new HashMap<>();
        for (int position : difference.keySet()) {
            work.run();
            values.put(position, new ArrayList<>());
        }
        difference.values().removeIf(count -> count == 0);
        if (difference.size() != 2)
            throw new IllegalArgumentException("Two nonzero set coefficients required");
        java.util.Iterator<Map.Entry<Integer, Integer>> entries = difference.entrySet().iterator();
        Map.Entry<Integer, Integer> first = entries.next(), second = entries.next();
        int sign = Integer.signum(first.getValue());
        boolean opposite = sign != Integer.signum(second.getValue());
        BigInteger a = BigInteger.valueOf(Math.abs(first.getValue()));
        BigInteger b = BigInteger.valueOf(Math.abs(second.getValue()));
        BigInteger gcd = a.gcd(b);
        a = a.divide(gcd);
        b = b.divide(gcd);
        BigInteger inverse = a.modInverse(b);
        List<SemanticTermSnapshot> members = new ArrayList<>(leftConstants);
        members.addAll(rightConstants);
        List<SemanticTermSnapshot> checked = new ArrayList<>();
        for (SemanticTermSnapshot member : members) {
            work.run();
            if (countMember(checked, member, work) != 0) continue;
            checked.add(member);
            int residual = countMember(rightConstants, member, work) - countMember(leftConstants, member, work);
            BigInteger[] reduced = BigInteger.valueOf((long) sign * residual).divideAndRemainder(gcd);
            if (reduced[1].signum() != 0) return Optional.empty();
            // Choose the least nonnegative x in its residue class modulo b.
            BigInteger x = reduced[0].multiply(inverse).mod(b);
            BigInteger y = reduced[0].subtract(a.multiply(x)).divide(b);
            if (opposite) {
                y = y.negate();
                if (y.signum() < 0) {
                    BigInteger shift = y.negate().add(a).subtract(BigInteger.ONE).divide(a);
                    x = x.add(b.multiply(shift));
                    y = y.add(a.multiply(shift));
                }
            } else if (y.signum() < 0) {
                // Increasing x in this class can only decrease y.
                return Optional.empty();
            }
            appendCopies(values.get(first.getKey()), member, x, work);
            appendCopies(values.get(second.getKey()), member, y, work);
        }
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        for (Map.Entry<Integer, List<ITerm>> entry : values.entrySet()) {
            work.run();
            assignment.put(entry.getKey(), captureFlatSet(entry.getValue()));
        }
        return Optional.of(Collections.unmodifiableMap(assignment));
    }

    /** Choose a basis of at most two rows, then replay every supplied equality. */
    static Optional<Map<Integer, SemanticTermSnapshot>> inferTwoPositionConjunction(
            List<ContextSetPattern> left, List<ContextSetPattern> right, Runnable work) throws Exception {
        if (left.isEmpty() || left.size() != right.size())
            throw new IllegalArgumentException("Matching nonempty lists of set expressions required");
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        for (int i = 0; i < left.size(); i++) {
            left.get(i).collectVariables(positions, work);
            right.get(i).collectVariables(positions, work);
        }
        if (positions.size() != 2) throw new IllegalArgumentException("Exactly two set positions required");
        java.util.Iterator<Integer> iterator = positions.iterator();
        int x = iterator.next(), y = iterator.next();
        int first = 0, second = 0;
        long a = 0, b = 0;
        for (int i = 0; i < left.size(); i++) {
            work.run();
            Map<Integer, Integer> coefficients = new HashMap<>();
            left.get(i).collectDifference(right.get(i), coefficients, new ArrayList<>(), new ArrayList<>(), work);
            long c = coefficients.getOrDefault(x, 0), d = coefficients.getOrDefault(y, 0);
            if (a == 0 && b == 0) {
                first = second = i;
                a = c;
                b = d;
            } else if (a * d != b * c) {
                second = i;
                break;
            }
        }
        Optional<Map<Integer, SemanticTermSnapshot>> basis = left.get(first)
                .inferTwoEqualities(right.get(first), left.get(second), right.get(second), work);
        if (!basis.isPresent()) return Optional.empty();
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>(basis.get());
        for (int position : positions) {
            if (!assignment.containsKey(position)) assignment.put(position, captureFlatSet(Collections.emptyList()));
        }
        // Dependent rows have constant truth on the solutions of the chosen basis.
        for (int i = 0; i < left.size(); i++) {
            work.run();
            if (!left.get(i).instantiate(assignment, work).semanticallyEquals(right.get(i).instantiate(assignment, work)))
                return Optional.empty();
        }
        return Optional.of(Collections.unmodifiableMap(assignment));
    }

    /** Solve two constructor equalities over the same two variable positions. */
    Optional<Map<Integer, SemanticTermSnapshot>> inferTwoEqualities(ContextSetPattern right,
            ContextSetPattern secondLeft, ContextSetPattern secondRight, Runnable work) throws Exception {
        Map<Integer, Integer> first = new HashMap<>(), second = new HashMap<>();
        List<SemanticTermSnapshot> firstLeft = new ArrayList<>(), firstRight = new ArrayList<>();
        List<SemanticTermSnapshot> otherLeft = new ArrayList<>(), otherRight = new ArrayList<>();
        collectDifference(right, first, firstLeft, firstRight, work);
        secondLeft.collectDifference(secondRight, second, otherLeft, otherRight, work);
        java.util.Set<Integer> positions = new java.util.HashSet<>(first.keySet());
        positions.addAll(second.keySet());
        if (positions.isEmpty() || positions.size() > 2)
            throw new IllegalArgumentException("One or two set positions required");
        java.util.Iterator<Integer> positionsIterator = positions.iterator();
        int x = positionsIterator.next();
        int y = positionsIterator.hasNext() ? positionsIterator.next() : -1;
        BigInteger a = BigInteger.valueOf(first.getOrDefault(x, 0));
        BigInteger b = BigInteger.valueOf(first.getOrDefault(y, 0));
        BigInteger c = BigInteger.valueOf(second.getOrDefault(x, 0));
        BigInteger d = BigInteger.valueOf(second.getOrDefault(y, 0));
        BigInteger determinant = a.multiply(d).subtract(b.multiply(c));
        boolean dependent = determinant.signum() == 0;
        boolean firstZero = a.signum() == 0 && b.signum() == 0;
        boolean secondZero = c.signum() == 0 && d.signum() == 0;
        BigInteger activeX = firstZero ? c : a;
        BigInteger activeY = firstZero ? d : b;
        int activeSigns = activeX.signum() * activeY.signum();

        List<SemanticTermSnapshot> members = new ArrayList<>(firstLeft);
        members.addAll(firstRight);
        members.addAll(otherLeft);
        members.addAll(otherRight);
        List<SemanticTermSnapshot> checked = new ArrayList<>();
        List<ITerm> xValues = new ArrayList<>(), yValues = new ArrayList<>();
        for (SemanticTermSnapshot member : members) {
            work.run();
            if (countMember(checked, member, work) != 0) continue;
            checked.add(member);
            BigInteger e = BigInteger.valueOf(countMember(firstRight, member, work) - countMember(firstLeft, member, work));
            BigInteger f = BigInteger.valueOf(countMember(otherRight, member, work) - countMember(otherLeft, member, work));
            BigInteger xNumerator = e.multiply(d).subtract(b.multiply(f));
            BigInteger yNumerator = a.multiply(f).subtract(e.multiply(c));
            if (dependent) {
                if ((firstZero && e.signum() != 0) || (secondZero && f.signum() != 0)
                        || xNumerator.signum() != 0 || yNumerator.signum() != 0) return Optional.empty();
                if (activeSigns == 0 && !(firstZero && secondZero)) {
                    boolean bindsX = activeX.signum() != 0;
                    BigInteger[] count = (firstZero ? f : e).divideAndRemainder(bindsX ? activeX : activeY);
                    if (count[1].signum() != 0 || count[0].signum() < 0) return Optional.empty();
                    appendCopies(bindsX ? xValues : yValues, member, count[0], work);
                }
                continue;
            }
            BigInteger[] xCount = xNumerator.divideAndRemainder(determinant);
            BigInteger[] yCount = yNumerator.divideAndRemainder(determinant);
            if (xCount[1].signum() != 0 || yCount[1].signum() != 0
                    || xCount[0].signum() < 0 || yCount[0].signum() < 0) return Optional.empty();
            appendCopies(xValues, member, xCount[0], work);
            appendCopies(yValues, member, yCount[0], work);
        }
        if (dependent && activeSigns != 0 && !firstZero) return inferTwoInputs(right, work);
        if (dependent && activeSigns != 0) return secondLeft.inferTwoInputs(secondRight, work);
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        assignment.put(x, captureFlatSet(xValues));
        if (y >= 0) assignment.put(y, captureFlatSet(yValues));
        return Optional.of(Collections.unmodifiableMap(assignment));
    }

    private static void appendCopies(List<ITerm> values, SemanticTermSnapshot member, BigInteger copies, Runnable work)
            throws Exception {
        if (copies.compareTo(BigInteger.valueOf(Integer.MAX_VALUE - values.size())) > 0)
            throw new IllegalStateException("Set witness exceeds concrete member capacity");
        for (int i = 0, count = copies.intValueExact(); i < count; i++) {
            member.appendFlatSetMembers(values, work);
        }
    }

    private void collectDifference(ContextSetPattern other, Map<Integer, Integer> difference,
            List<SemanticTermSnapshot> leftConstants, List<SemanticTermSnapshot> rightConstants, Runnable work) {
        Map<Integer, Integer> right = new HashMap<>();
        collectExpression(difference, leftConstants, work);
        other.collectExpression(right, rightConstants, work);
        for (Map.Entry<Integer, Integer> coefficient : right.entrySet()) {
            work.run();
            difference.merge(coefficient.getKey(), -coefficient.getValue(), Integer::sum);
        }
    }

    /** Necessary divisibility condition on each member of a flat constructor equality. */
    boolean satisfiesCountDivisibility(ContextSetPattern other, Runnable work) {
        Map<Integer, Integer> difference = new HashMap<>();
        List<SemanticTermSnapshot> leftConstants = new ArrayList<>(), rightConstants = new ArrayList<>();
        collectDifference(other, difference, leftConstants, rightConstants, work);
        BigInteger divisor = BigInteger.ZERO;
        for (int coefficient : difference.values()) {
            work.run();
            divisor = divisor.gcd(BigInteger.valueOf(coefficient));
        }
        int gcd = divisor.intValueExact();
        List<SemanticTermSnapshot> members = new ArrayList<>(leftConstants);
        members.addAll(rightConstants);
        List<SemanticTermSnapshot> checked = new ArrayList<>();
        for (SemanticTermSnapshot member : members) {
            work.run();
            if (countMember(checked, member, work) != 0) continue;
            checked.add(member);
            int residual = countMember(rightConstants, member, work) - countMember(leftConstants, member, work);
            if (gcd == 0 ? residual != 0 : residual % gcd != 0) return false;
        }
        return true;
    }

    /**
     * Invert one flat constructor at a concrete result for a single variable.
     * A solution describes flattened members, since several input values can flatten identically.
     */
    Optional<List<SemanticTermSnapshot>> inferSingleInputMembers(SemanticTermSnapshot target, Runnable work) {
        Map<Integer, Integer> coefficients = new HashMap<>();
        List<SemanticTermSnapshot> constants = new ArrayList<>();
        collectExpression(coefficients, constants, work);
        if (coefficients.size() != 1) throw new IllegalArgumentException("Exactly one set variable required");
        if (target.getType() != DataType.SET) return Optional.empty();

        List<SemanticTermSnapshot> leaves = new ArrayList<>();
        target.visitFlatSetMembers(leaves::add, work);
        boolean[] used = new boolean[leaves.size()];
        for (SemanticTermSnapshot constant : constants) {
            int match = findUnusedMember(leaves, used, constant, work);
            if (match < 0) return Optional.empty();
            used[match] = true;
        }
        return divideMembers(leaves, used, coefficients.values().iterator().next(), work);
    }

    /**
     * Extend fixed bindings by solving this pattern's sole unbound variable
     * against an independently evaluable right-hand pattern.
     */
    Optional<Map<Integer, SemanticTermSnapshot>> inferFromFixedPattern(ContextSetPattern fixedPattern,
            Map<Integer, SemanticTermSnapshot> fixed, Runnable work) throws Exception {
        java.util.Set<Integer> positions = new java.util.HashSet<>();
        collectVariables(positions, work);
        if (positions.size() != 1 || fixed.containsKey(positions.iterator().next())) {
            throw new IllegalArgumentException("Exactly one unbound variable in the inferred set pattern required");
        }
        int position = positions.iterator().next();
        SemanticTermSnapshot target = fixedPattern.instantiate(fixed, work);
        Optional<List<SemanticTermSnapshot>> solution = inferSingleInputMembers(target, work);
        if (!solution.isPresent()) return Optional.empty();

        List<ITerm> values = new ArrayList<>();
        for (SemanticTermSnapshot member : solution.get()) {
            member.appendFlatSetMembers(values, work);
        }
        Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>();
        for (Map.Entry<Integer, SemanticTermSnapshot> entry : fixed.entrySet()) {
            work.run();
            assignment.put(entry.getKey(), entry.getValue());
        }
        assignment.put(position, captureFlatSet(values));
        return Optional.of(Collections.unmodifiableMap(assignment));
    }

    private static Optional<List<SemanticTermSnapshot>> divideMembers(List<SemanticTermSnapshot> leaves,
            boolean[] used, int coefficient, Runnable work) {
        List<SemanticTermSnapshot> input = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) {
            work.run();
            if (used[i]) continue;
            used[i] = true;
            SemanticTermSnapshot member = leaves.get(i);
            for (int occurrence = 1; occurrence < coefficient; occurrence++) {
                int match = findUnusedMember(leaves, used, member, work);
                if (match < 0) return Optional.empty();
                used[match] = true;
            }
            input.add(member);
        }
        return Optional.of(Collections.unmodifiableList(input));
    }

    static final class SharedInputEquality {
        enum Kind { ALWAYS, NEVER, EXACT_MEMBERS }

        final Kind kind;
        final List<SemanticTermSnapshot> members;

        private SharedInputEquality(Kind kind, List<SemanticTermSnapshot> members) {
            this.kind = kind;
            this.members = members;
        }
    }

    /** Solve equality for a single shared variable with aligned rule-relative positions. */
    SharedInputEquality solveSharedInputEquality(ContextSetPattern other, Runnable work) {
        Map<Integer, Integer> left = new HashMap<>(), right = new HashMap<>();
        List<SemanticTermSnapshot> leftConstants = new ArrayList<>(), rightConstants = new ArrayList<>();
        collectExpression(left, leftConstants, work);
        other.collectExpression(right, rightConstants, work);
        if (left.size() != 1 || !left.keySet().equals(right.keySet()))
            throw new IllegalArgumentException("Exactly one shared set variable required");
        int difference = left.values().iterator().next() - right.values().iterator().next();
        if (difference == 0) {
            return new SharedInputEquality(sameFlatExpression(other, work)
                    ? SharedInputEquality.Kind.ALWAYS : SharedInputEquality.Kind.NEVER, Collections.emptyList());
        }
        List<SemanticTermSnapshot> residual = difference > 0 ? rightConstants : leftConstants;
        List<SemanticTermSnapshot> subtract = difference > 0 ? leftConstants : rightConstants;
        boolean[] used = new boolean[residual.size()];
        for (SemanticTermSnapshot constant : subtract) {
            int match = findUnusedMember(residual, used, constant, work);
            if (match < 0) return new SharedInputEquality(SharedInputEquality.Kind.NEVER, Collections.emptyList());
            used[match] = true;
        }
        return divideMembers(residual, used, Math.abs(difference), work)
                .map(members -> new SharedInputEquality(SharedInputEquality.Kind.EXACT_MEMBERS, members))
                .orElseGet(() -> new SharedInputEquality(SharedInputEquality.Kind.NEVER, Collections.emptyList()));
    }

    /** Each variable equals its required members plus the same arbitrary flat multiset. */
    static final class SharedRemainderEquality {
        final Map<Integer, List<SemanticTermSnapshot>> requiredMembers;

        private final List<Integer> originalPositions;
        private final boolean divisible;

        private SharedRemainderEquality(Map<Integer, List<SemanticTermSnapshot>> requiredMembers,
                List<Integer> originalPositions, boolean divisible) {
            this.requiredMembers = Collections.unmodifiableMap(requiredMembers);
            this.originalPositions = Collections.unmodifiableList(originalPositions);
            this.divisible = divisible;
        }

        /** Evaluate only the flattened contributions, leaving every original binding intact. */
        boolean matches(Map<Integer, SemanticTermSnapshot> assignment, Runnable work) {
            work.run();
            if (!divisible) return false;
            java.util.Iterator<Map.Entry<Integer, List<SemanticTermSnapshot>>> entries =
                    requiredMembers.entrySet().iterator();
            List<SemanticTermSnapshot> first = remainder(entries.next(), assignment, work);
            List<SemanticTermSnapshot> second = remainder(entries.next(), assignment, work);
            if (first == null || second == null || first.size() != second.size()) return false;
            boolean[] used = new boolean[second.size()];
            for (SemanticTermSnapshot member : first) {
                int match = findUnusedMember(second, used, member, work);
                if (match < 0) return false;
                used[match] = true;
            }
            return true;
        }

        /** Reject inconsistent width offsets across shared rule-relative variable positions. */
        static boolean widthsConsistent(List<SharedRemainderEquality> conditions, Runnable work) {
            return solveCounts(conditions, List::size, position -> -1, work).isPresent();
        }

        /** Check cycles for each named member separately, using native semantic equality. */
        static boolean multiplicitiesConsistent(List<SharedRemainderEquality> conditions, Runnable work) {
            for (SharedRemainderEquality condition : conditions) {
                work.run();
                if (!condition.divisible) return false;
            }
            for (SemanticTermSnapshot member : namedMembers(conditions, work)) {
                if (!solveCounts(conditions, required -> countMember(required, member, work), position -> -1, work).isPresent()) {
                    return false;
                }
            }
            return true;
        }

        /** Build one finite multiset assignment for these equalities under the supplied work budget. */
        static Optional<Map<Integer, SemanticTermSnapshot>> witness(
                List<SharedRemainderEquality> conditions, Runnable work) throws Exception {
            return witness(conditions, Collections.emptyMap(), work);
        }

        static Optional<Map<Integer, SemanticTermSnapshot>> witness(
                List<SharedRemainderEquality> conditions, Map<Integer, SemanticTermSnapshot> fixed,
                Runnable work) throws Exception {
            Map<Integer, List<ITerm>> values = new HashMap<>();
            for (SharedRemainderEquality condition : conditions) {
                work.run();
                if (!condition.divisible) return Optional.empty();
                for (int position : condition.originalPositions) {
                    work.run();
                    values.computeIfAbsent(position, key -> new ArrayList<>());
                }
            }
            List<SemanticTermSnapshot> members = namedMembers(conditions, work);
            Map<Integer, List<SemanticTermSnapshot>> fixedMembers = new HashMap<>();
            for (SharedRemainderEquality condition : conditions) {
                for (int position : condition.requiredMembers.keySet()) {
                    work.run();
                    if (!fixed.containsKey(position) || fixedMembers.containsKey(position)) continue;
                    List<SemanticTermSnapshot> leaves = new ArrayList<>();
                    requireBinding(position, fixed).visitFlatSetMembers(leaves::add, work);
                    fixedMembers.put(position, leaves);
                    for (SemanticTermSnapshot leaf : leaves) {
                        work.run();
                        if (countMember(members, leaf, work) == 0) members.add(leaf);
                    }
                }
            }
            for (SemanticTermSnapshot member : members) {
                Optional<Map<Integer, Long>> counts = solveCounts(conditions,
                        required -> countMember(required, member, work), position -> {
                            List<SemanticTermSnapshot> leaves = fixedMembers.get(position);
                            return leaves == null ? -1 : countMember(leaves, member, work);
                        }, work);
                if (!counts.isPresent()) return Optional.empty();
                for (Map.Entry<Integer, Long> count : counts.get().entrySet()) {
                    work.run();
                    if (fixed.containsKey(count.getKey())) continue;
                    for (long copy = 0; copy < count.getValue(); copy++) {
                        member.appendFlatSetMembers(values.get(count.getKey()), work);
                    }
                }
            }
            Map<Integer, SemanticTermSnapshot> assignment = new HashMap<>(fixed);
            for (Map.Entry<Integer, List<ITerm>> value : values.entrySet()) {
                work.run();
                if (!assignment.containsKey(value.getKey())) {
                    assignment.put(value.getKey(), captureFlatSet(value.getValue()));
                }
            }
            return Optional.of(Collections.unmodifiableMap(assignment));
        }

        private static List<SemanticTermSnapshot> namedMembers(List<SharedRemainderEquality> conditions, Runnable work) {
            List<SemanticTermSnapshot> members = new ArrayList<>();
            for (SharedRemainderEquality condition : conditions) {
                work.run();
                for (List<SemanticTermSnapshot> required : condition.requiredMembers.values()) {
                    for (SemanticTermSnapshot member : required) {
                        work.run();
                        if (countMember(members, member, work) == 0) members.add(member);
                    }
                }
            }
            return members;
        }

        private static Optional<Map<Integer, Long>> solveCounts(List<SharedRemainderEquality> conditions,
                java.util.function.ToIntFunction<List<SemanticTermSnapshot>> measure,
                java.util.function.IntUnaryOperator fixedCount, Runnable work) {
            Map<Integer, List<CountOffset>> neighbors = new HashMap<>();
            for (SharedRemainderEquality condition : conditions) {
                work.run();
                if (!condition.divisible) return Optional.empty();
                java.util.Iterator<Map.Entry<Integer, List<SemanticTermSnapshot>>> entries =
                        condition.requiredMembers.entrySet().iterator();
                Map.Entry<Integer, List<SemanticTermSnapshot>> first = entries.next(), second = entries.next();
                int difference = measure.applyAsInt(second.getValue()) - measure.applyAsInt(first.getValue());
                neighbors.computeIfAbsent(first.getKey(), key -> new ArrayList<>())
                        .add(new CountOffset(second.getKey(), difference));
                neighbors.computeIfAbsent(second.getKey(), key -> new ArrayList<>())
                        .add(new CountOffset(first.getKey(), -difference));
            }
            Map<Integer, Long> counts = new HashMap<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            for (int start : neighbors.keySet()) {
                work.run();
                if (counts.putIfAbsent(start, 0L) != null) continue;
                pending.add(start);
                List<Integer> component = new ArrayList<>();
                long minimum = 0;
                Long fixedShift = null;
                while (!pending.isEmpty()) {
                    int current = pending.remove();
                    component.add(current);
                    long relative = counts.get(current);
                    minimum = Math.min(minimum, relative);
                    int fixed = fixedCount.applyAsInt(current);
                    if (fixed >= 0) {
                        long shift = fixed - relative;
                        if (fixedShift != null && fixedShift != shift) return Optional.empty();
                        fixedShift = shift;
                    }
                    for (CountOffset offset : neighbors.get(current)) {
                        work.run();
                        long expected = counts.get(current) + offset.difference;
                        Long existing = counts.putIfAbsent(offset.target, expected);
                        if (existing == null) pending.add(offset.target);
                        else if (existing != expected) return Optional.empty();
                    }
                }
                // Use the anchor when present, otherwise choose the smallest nonnegative counts.
                long shift = fixedShift == null ? -minimum : fixedShift;
                if (shift < -minimum) return Optional.empty();
                for (int position : component) {
                    work.run();
                    counts.put(position, counts.get(position) + shift);
                }
            }
            return Optional.of(counts);
        }

        private static final class CountOffset {
            final int target, difference;

            CountOffset(int target, int difference) {
                this.target = target;
                this.difference = difference;
            }
        }

        private static List<SemanticTermSnapshot> remainder(Map.Entry<Integer, List<SemanticTermSnapshot>> requirement,
                Map<Integer, SemanticTermSnapshot> assignment, Runnable work) {
            List<SemanticTermSnapshot> leaves = new ArrayList<>();
            requireBinding(requirement.getKey(), assignment).visitFlatSetMembers(leaves::add, work);
            boolean[] used = new boolean[leaves.size()];
            for (SemanticTermSnapshot member : requirement.getValue()) {
                int match = findUnusedMember(leaves, used, member, work);
                if (match < 0) return null;
                used[match] = true;
            }
            List<SemanticTermSnapshot> result = new ArrayList<>();
            for (int i = 0; i < used.length; i++) {
                work.run();
                if (!used[i]) result.add(leaves.get(i));
            }
            return result;
        }
    }

    SharedRemainderEquality solveSharedRemainderEquality(ContextSetPattern other, Runnable work) {
        Map<Integer, Integer> difference = new HashMap<>(), right = new HashMap<>();
        List<SemanticTermSnapshot> leftConstants = new ArrayList<>(), rightConstants = new ArrayList<>();
        collectExpression(difference, leftConstants, work);
        other.collectExpression(right, rightConstants, work);
        for (Map.Entry<Integer, Integer> entry : right.entrySet()) {
            work.run();
            difference.merge(entry.getKey(), -entry.getValue(), Integer::sum);
        }
        List<Integer> originalPositions = new ArrayList<>();
        for (int position : difference.keySet()) {
            work.run();
            originalPositions.add(position);
        }
        difference.values().removeIf(count -> count == 0);
        if (difference.size() != 2)
            throw new IllegalArgumentException("Two opposite equal-magnitude set coefficients required");
        int coefficient = Math.abs(difference.values().iterator().next());
        if (!difference.containsValue(coefficient) || !difference.containsValue(-coefficient))
            throw new IllegalArgumentException("Two opposite equal-magnitude set coefficients required");

        List<SemanticTermSnapshot> leftOnly = new ArrayList<>(), rightOnly = new ArrayList<>();
        boolean[] used = new boolean[rightConstants.size()];
        for (SemanticTermSnapshot member : leftConstants) {
            work.run();
            int match = findUnusedMember(rightConstants, used, member, work);
            if (match < 0) leftOnly.add(member);
            else used[match] = true;
        }
        for (int i = 0; i < used.length; i++) {
            work.run();
            if (!used[i]) rightOnly.add(rightConstants.get(i));
        }
        Optional<List<SemanticTermSnapshot>> left = divideMembers(leftOnly, new boolean[leftOnly.size()], coefficient, work);
        Optional<List<SemanticTermSnapshot>> rightResidual =
                divideMembers(rightOnly, new boolean[rightOnly.size()], coefficient, work);
        Map<Integer, List<SemanticTermSnapshot>> required = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : difference.entrySet()) {
            required.put(entry.getKey(), (entry.getValue() > 0 ? rightResidual : left).orElse(Collections.emptyList()));
        }
        return new SharedRemainderEquality(required, originalPositions, left.isPresent() && rightResidual.isPresent());
    }

    private static int countMember(List<SemanticTermSnapshot> members, SemanticTermSnapshot member, Runnable work) {
        int count = 0;
        for (SemanticTermSnapshot candidate : members) {
            work.run();
            if (candidate.semanticallyEquals(member)) count++;
        }
        return count;
    }

    private static int findUnusedMember(List<SemanticTermSnapshot> leaves, boolean[] used,
            SemanticTermSnapshot member, Runnable work) {
        for (int i = 0; i < leaves.size(); i++) {
            work.run();
            if (!used[i] && member.semanticallyEquals(leaves.get(i))) return i;
        }
        return -1;
    }

    SemanticTermSnapshot instantiate(Map<Integer, SemanticTermSnapshot> assignment, Runnable work)
            throws Exception {
        List<ITerm> values = new ArrayList<>();
        for (Member member : members) {
            work.run();
            SemanticTermSnapshot value = member.variable < 0
                    ? member.constant : requireBinding(member.variable, assignment);
            value.appendFlatSetMembers(values, work);
        }
        return captureFlatSet(values);
    }

    private static SemanticTermSnapshot captureFlatSet(List<ITerm> values) throws Exception {
        Term set = new Term();
        set.setPersistentState(DataType.SET, values, 0, 0, -1L, -1L, false);
        return SemanticTermSnapshot.capture(set);
    }
}
