/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextProductionCapture.Source;

/** Sealed synchronous arithmetic transport. No consistency, saturation or admission certificate. */
public final class ContextArithmeticFamilyRoundInspection {
    private ContextArithmeticFamilyRoundInspection() {}

    public static final class Cut {
        public final String group;
        public final List<ContextProductionInventory> members;
        public final List<ContextArithmeticRuleInspection> rules;
        public final List<Premise> seeds;

        public Cut(String group, List<ContextProductionInventory> members,
                   List<ContextArithmeticRuleInspection> rules, List<Premise> seeds) {
            this.group = TrustGroups.validate(group);
            if (members == null || members.isEmpty() || (group == null && members.size() != 1)
                    || members.contains(null) || rules == null || rules.contains(null) || seeds == null || seeds.contains(null))
                throw new IllegalArgumentException("Complete arithmetic cut manifest required");
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            this.seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
        }
    }

    public static final class Collision {
        public final String cut;
        public final Premise left, right;

        private Collision(String cut, Premise left, Premise right) {
            this.cut = cut;
            this.left = left;
            this.right = right;
        }
    }

    public static final class ConstraintCollision {
        public final String cut;
        public final ContextArithmeticRuleInspection.ConstraintViolation evidence;

        private ConstraintCollision(String cut, ContextArithmeticRuleInspection.ConstraintViolation evidence) {
            this.cut = cut;
            this.evidence = evidence;
        }
    }

    public static final class Frame {
        public final List<Premise> common;
        public final List<List<Premise>> privateFrontiers;
        public final boolean changed;
        public final List<Collision> collisions;
        public final List<ConstraintCollision> constraints;

        public boolean refuted() { return !collisions.isEmpty() || !constraints.isEmpty(); }
        final ContextProductionInventory x;
        final List<ContextArithmeticRuleInspection> localRules;
        final List<Cut> cuts;
        final Set<Source> universe;

        private Frame(ContextProductionInventory x, List<ContextArithmeticRuleInspection> localRules,
                      List<Cut> cuts, Set<Source> universe, List<Premise> common,
                      List<List<Premise>> privateFrontiers, boolean changed, List<Collision> collisions, List<ConstraintCollision> constraints) {
            this.x = x;
            this.localRules = Collections.unmodifiableList(new ArrayList<>(localRules));
            this.cuts = Collections.unmodifiableList(new ArrayList<>(cuts));
            this.universe = Collections.unmodifiableSet(new HashSet<>(universe));
            this.common = Collections.unmodifiableList(new ArrayList<>(common));
            List<List<Premise>> copy = new ArrayList<>();
            for (List<Premise> frontier : privateFrontiers)
                copy.add(Collections.unmodifiableList(new ArrayList<>(frontier)));
            this.privateFrontiers = Collections.unmodifiableList(copy);
            this.changed = changed;
            this.collisions = Collections.unmodifiableList(new ArrayList<>(collisions));
            this.constraints = Collections.unmodifiableList(new ArrayList<>(constraints));
        }
    }

    public enum Status { STABLE_PENDING, COLLISION, INCOMPLETE }

    public static final class Iteration {
        public final Status status;
        public final String reason;
        public final Frame frame;
        public final int completedBarriers;

        private Iteration(Status status, String reason, Frame frame, int completedBarriers) {
            this.status = status;
            this.reason = reason;
            this.frame = frame;
            this.completedBarriers = completedBarriers;
        }
    }

    public static Iteration iterate(Frame initial, int maximumRounds, int maximumDepth,
                                    int maximumWork, int capacity) {
        if (initial == null || maximumRounds < 1 || maximumDepth < 1 || maximumWork < 1 || capacity < 1)
            throw new IllegalArgumentException("Positive bounded arithmetic iteration required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        int completed = 0;
        try {
            checkFrontier(initial.common, maximumDepth, capacity, work);
            for (List<Premise> frontier : initial.privateFrontiers)
                checkFrontier(frontier, maximumDepth, capacity, work);
            if ((long) initial.collisions.size() + initial.constraints.size() > capacity) throw new IllegalStateException("Arithmetic collision capacity");
            for (ConstraintCollision constraint : initial.constraints) {
                work.run();
                if (constraint.evidence.proof.depth > maximumDepth)
                    throw new IllegalStateException("Carried arithmetic constraint depth limit");
            }
            if (initial.refuted())
                return new Iteration(Status.COLLISION, "Sourced collision at the initial barrier", initial, 0);
            Frame current = initial;
            for (int round = 0; round < maximumRounds; round++) {
                work.run();
                current = advance(current, maximumDepth, work, capacity);
                completed++;
                if (current.refuted())
                    return new Iteration(Status.COLLISION, "Sourced collision at a complete family barrier", current, completed);
                if (!current.changed)
                    return new Iteration(Status.STABLE_PENDING, "Complete unchanged arithmetic proof frontier; model audit pending", current, completed);
            }
            return new Iteration(Status.INCOMPLETE, "Arithmetic round limit before an unchanged barrier", null, completed);
        } catch (Exception stopped) {
            return new Iteration(Status.INCOMPLETE, stopped.getMessage(), null, completed);
        }
    }

    private static void checkFrontier(List<Premise> frontier, int maximumDepth, int capacity, Runnable work) {
        if (frontier.size() > capacity) throw new IllegalStateException("Arithmetic frontier capacity");
        for (Premise premise : frontier) {
            work.run();
            if (premise.proof.depth > maximumDepth) throw new IllegalStateException("Carried arithmetic proof depth limit");
        }
    }

    private static void sources(ContextProductionInventory inventory, Set<Source> target, Runnable work) {
        for (ContextProductionInventory.Row row : inventory.primary) {
            work.run();
            target.add(new Source(inventory.context, inventory.revision, row.nativeRule, inventory.localX,
                    "inventory:" + inventory.fingerprint));
        }
    }

    public static Frame start(ContextProductionInventory x, List<ContextArithmeticRuleInspection> localRules,
                              List<Premise> localSeeds, List<Cut> cuts, int maximumWork, int capacity) throws Exception {
        if (x == null || !x.localX || localRules == null || localRules.contains(null)
                || localSeeds == null || localSeeds.contains(null) || cuts == null || cuts.contains(null) || capacity < 1)
            throw new IllegalArgumentException("Bounded arithmetic family required");
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        Set<UUID> contexts = new HashSet<>();
        contexts.add(x.context);
        Set<String> groups = new HashSet<>();
        Set<Source> universe = new HashSet<>();
        sources(x, universe, work);
        Set<Source> local = new HashSet<>(universe);
        List<Premise> common = new ArrayList<>();
        validate(localRules, localSeeds, local, work);
        ContextArithmeticRuleInspection.requireCoverage(local, localRules, localSeeds, work);
        for (Premise seed : localSeeds) ContextArithmeticRoundInspection.merge(common, seed, capacity, work);
        List<List<Premise>> privateFrontiers = new ArrayList<>();
        for (Cut cut : cuts) {
            work.run();
            if (cut.group != null && !groups.add(cut.group)) throw new IllegalArgumentException("Split trusted cut");
            Set<Source> selected = new HashSet<>(local);
            for (ContextProductionInventory member : cut.members) {
                work.run();
                if (member.localX || !contexts.add(member.context)) throw new IllegalArgumentException("Repeated or local foreign member");
                sources(member, selected, work);
            }
            universe.addAll(selected);
            validate(cut.rules, cut.seeds, selected, work);
            Set<Source> owned = new HashSet<>(selected);
            owned.removeAll(local);
            ContextArithmeticRuleInspection.requireCoverage(owned, cut.rules, cut.seeds, work);
            List<Premise> frontier = new ArrayList<>();
            for (Premise seed : cut.seeds) {
                work.run();
                if (supportedByX(seed)) throw new IllegalArgumentException("Private seed must have foreign-only ancestry");
                ContextArithmeticRoundInspection.merge(frontier, seed, capacity, work);
            }
            privateFrontiers.add(frontier);
        }
        return frame(x, localRules, cuts, universe, common, privateFrontiers, false, Integer.MAX_VALUE, work, capacity);
    }

    private static void validate(List<ContextArithmeticRuleInspection> rules, List<Premise> seeds,
                                 Set<Source> universe, Runnable work) {
        for (ContextArithmeticRuleInspection rule : rules) {
            work.run();
            if (!universe.contains(rule.source)) throw new IllegalArgumentException("Rule outside cut manifest");
            rule.preflight(work);
        }
        for (Premise seed : seeds) for (Source source : seed.proof.sources) {
            work.run();
            if (!universe.contains(source)) throw new IllegalArgumentException("Seed outside cut manifest");
        }
    }

    private static boolean supportedByX(Premise premise) {
        for (Source source : premise.proof.sources) if (source.localX) return true;
        return false;
    }

    public static Frame advance(Frame input, int maximumDepth, int maximumWork, int capacity) throws Exception {
        if (input == null || maximumDepth < 1 || capacity < 1) throw new IllegalArgumentException("Bounded sealed frame required");
        return advance(input, maximumDepth, ContextGroundProductionRounds.budget(maximumWork), capacity);
    }

    private static Frame advance(Frame input, int maximumDepth, Runnable work, int capacity) throws Exception {
        if (input.refuted()) throw new IllegalArgumentException("A refuted arithmetic frame cannot advance");
        List<Premise> common = new ArrayList<>();
        for (Premise premise : input.common) ContextArithmeticRoundInspection.merge(common, premise, capacity, work);
        ContextArithmeticRoundInspection.Result local = ContextArithmeticRoundInspection.inspect(
                input.x, Collections.emptyList(), null, input.localRules, Collections.emptyList(),
                input.common, input.universe, maximumDepth, work, capacity);
        boolean changed = merge(common, local.common, capacity, work);
        List<List<Premise>> privateFrontiers = new ArrayList<>();
        for (int i = 0; i < input.cuts.size(); i++) {
            work.run();
            Cut cut = input.cuts.get(i);
            List<ContextArithmeticRuleInspection> rules = new ArrayList<>(input.localRules);
            rules.addAll(cut.rules);
            ContextArithmeticRoundInspection.Result result = ContextArithmeticRoundInspection.inspect(
                    input.x, cut.members, cut.group, rules, input.privateFrontiers.get(i),
                    input.common, input.universe, maximumDepth, work, capacity);
            changed |= merge(common, result.common, capacity, work);
            List<Premise> privateState = new ArrayList<>(input.privateFrontiers.get(i));
            List<Premise> products = new ArrayList<>();
            for (Premise premise : result.frontier) {
                work.run();
                if (!supportedByX(premise)) products.add(premise);
            }
            changed |= merge(privateState, products, capacity, work);
            privateFrontiers.add(privateState);
        }
        return frame(input.x, input.localRules, input.cuts, input.universe, common, privateFrontiers, changed,
                maximumDepth, work, capacity);
    }

    private static Frame frame(ContextProductionInventory x, List<ContextArithmeticRuleInspection> localRules,
            List<Cut> cuts, Set<Source> universe, List<Premise> common, List<List<Premise>> privateFrontiers,
            boolean changed, int maximumDepth, Runnable work, int capacity) throws Exception {
        List<Collision> literals = collisions(x, cuts, common, privateFrontiers, work, capacity);
        List<ConstraintCollision> guards = constraints(x, localRules, cuts, common, privateFrontiers,
                maximumDepth, work, capacity - literals.size());
        return new Frame(x, localRules, cuts, universe, common, privateFrontiers, changed, literals, guards);
    }

    private static List<ConstraintCollision> constraints(ContextProductionInventory x,
            List<ContextArithmeticRuleInspection> localRules, List<Cut> cuts, List<Premise> common,
            List<List<Premise>> privateFrontiers, int maximumDepth, Runnable work, int capacity) throws Exception {
        List<ConstraintCollision> result = new ArrayList<>();
        scanConstraints(ContextDependentCutInspection.local(x.context), localRules, common,
                maximumDepth, work, capacity, result);
        for (int i = 0; i < cuts.size(); i++) {
            work.run();
            Cut cut = cuts.get(i);
            List<ContextArithmeticRuleInspection> rules = new ArrayList<>(localRules);
            rules.addAll(cut.rules);
            List<Premise> visible = new ArrayList<>(common);
            visible.addAll(privateFrontiers.get(i));
            String key = cut.group == null ? ContextDependentCutInspection.independent(cut.members.get(0).context)
                    : ContextDependentCutInspection.trusted(cut.group);
            scanConstraints(key, rules, visible, maximumDepth, work, capacity, result);
        }
        return result;
    }

    private static void scanConstraints(String cut, List<ContextArithmeticRuleInspection> rules,
            List<Premise> visible, int maximumDepth, Runnable work, int capacity,
            List<ConstraintCollision> result) throws Exception {
        for (ContextArithmeticRuleInspection rule : rules) {
            work.run();
            rule.constraints(visible, maximumDepth, work, evidence -> {
                if (result.size() == capacity) throw new IllegalStateException("Arithmetic constraint capacity");
                result.add(new ConstraintCollision(cut, evidence));
            });
        }
    }

    private static List<Collision> collisions(ContextProductionInventory x, List<Cut> cuts,
                                               List<Premise> common, List<List<Premise>> privateFrontiers,
                                               Runnable work, int capacity) {
        List<Collision> result = new ArrayList<>();
        scan(ContextDependentCutInspection.local(x.context), common, result, work, capacity);
        for (int i = 0; i < cuts.size(); i++) {
            work.run();
            Cut cut = cuts.get(i);
            String key = cut.group == null ? ContextDependentCutInspection.independent(cut.members.get(0).context)
                    : ContextDependentCutInspection.trusted(cut.group);
            List<Premise> visible = new ArrayList<>(common);
            for (Premise premise : privateFrontiers.get(i)) {
                work.run();
                visible.add(premise);
            }
            scan(key, visible, result, work, capacity);
        }
        return result;
    }

    private static void scan(String cut, List<Premise> visible, List<Collision> result, Runnable work, int capacity) {
        for (int i = 0; i < visible.size(); i++) {
            for (int j = i + 1; j < visible.size(); j++) {
                work.run();
                Premise left = visible.get(i), right = visible.get(j);
                if (left.fact.positive != right.fact.positive && left.fact.equivalent(right.fact.opposite())) {
                    if (result.size() == capacity) throw new IllegalStateException("Arithmetic collision capacity");
                    result.add(new Collision(cut, left, right));
                }
            }
        }
    }

    private static boolean merge(List<Premise> target, List<Premise> additions, int capacity, Runnable work) {
        boolean changed = false;
        for (Premise premise : additions) {
            work.run();
            changed |= ContextArithmeticRoundInspection.merge(target, premise, capacity, work);
        }
        return changed;
    }
}
