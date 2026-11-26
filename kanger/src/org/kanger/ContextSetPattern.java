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

        private SharedRemainderEquality(Map<Integer, List<SemanticTermSnapshot>> requiredMembers,
                List<Integer> originalPositions) {
            this.requiredMembers = Collections.unmodifiableMap(requiredMembers);
            this.originalPositions = Collections.unmodifiableList(originalPositions);
        }

        /** Evaluate only the flattened contributions, leaving every original binding intact. */
        boolean matches(Map<Integer, SemanticTermSnapshot> assignment, Runnable work) {
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

        private static int countMember(List<SemanticTermSnapshot> members, SemanticTermSnapshot member, Runnable work) {
            int count = 0;
            for (SemanticTermSnapshot candidate : members) {
                work.run();
                if (candidate.semanticallyEquals(member)) count++;
            }
            return count;
        }

        private static Optional<Map<Integer, Long>> solveCounts(List<SharedRemainderEquality> conditions,
                java.util.function.ToIntFunction<List<SemanticTermSnapshot>> measure,
                java.util.function.IntUnaryOperator fixedCount, Runnable work) {
            Map<Integer, List<CountOffset>> neighbors = new HashMap<>();
            for (SharedRemainderEquality condition : conditions) {
                work.run();
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
        if (difference.size() != 2 || !difference.containsValue(1) || !difference.containsValue(-1))
            throw new IllegalArgumentException("Two opposite unit-count set variables required");

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
        Map<Integer, List<SemanticTermSnapshot>> required = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : difference.entrySet()) {
            required.put(entry.getKey(), Collections.unmodifiableList(entry.getValue() == 1 ? rightOnly : leftOnly));
        }
        return new SharedRemainderEquality(required, originalPositions);
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
