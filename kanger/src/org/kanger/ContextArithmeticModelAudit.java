/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextArithmeticFamilyRoundInspection.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextProductionCapture.Fact;
import org.kanger.ContextProductionCapture.Source;

/** Finite positive-relation witnesses over the full ordinary value domain. No native admission authority. */
public final class ContextArithmeticModelAudit {
    private ContextArithmeticModelAudit() {}

    public static final class Model {
        public final String cut;
        public final List<Fact> positiveFacts;
        public final long obligations;

        private Model(String cut, List<Fact> facts, long obligations) {
            this.cut = cut;
            this.positiveFacts = Collections.unmodifiableList(new ArrayList<>(facts));
            this.obligations = obligations;
        }
    }

    public static final class Audit {
        public final boolean verified;
        public final String reason;
        public final List<Model> models;

        private Audit(boolean verified, String reason, List<Model> models) {
            this.verified = verified;
            this.reason = reason;
            this.models = Collections.unmodifiableList(new ArrayList<>(models));
        }
    }

    public static Audit inspect(Iteration iteration, int maximumWork) {
        if (iteration == null || maximumWork < 1) throw new IllegalArgumentException("Bounded completed iteration required");
        if (iteration.status != Status.STABLE_PENDING || iteration.frame == null)
            return new Audit(false, "Complete stable arithmetic iteration required", Collections.emptyList());
        return inspectFrame(iteration.frame, maximumWork);
    }

    static Audit inspectFrame(Frame frame, int maximumWork) {
        Runnable work = ContextGroundProductionRounds.budget(maximumWork);
        try {
            if (frame == null || frame.changed || frame.refuted())
                throw new IllegalStateException("Unchanged unrefuted frame required");
            List<Model> models = new ArrayList<>();
            models.add(model(ContextDependentCutInspection.local(frame.x.context), frame.localRules,
                    frame.common, owned(Collections.singletonList(frame.x), work), work));
            for (int i = 0; i < frame.cuts.size(); i++) {
                work.run();
                Cut cut = frame.cuts.get(i);
                List<ContextArithmeticRuleInspection> rules = new ArrayList<>(frame.localRules);
                rules.addAll(cut.rules);
                List<Premise> visible = new ArrayList<>(frame.common);
                visible.addAll(frame.privateFrontiers.get(i));
                List<ContextProductionInventory> inventories = new ArrayList<>();
                inventories.add(frame.x);
                inventories.addAll(cut.members);
                String key = cut.group == null ? ContextDependentCutInspection.independent(cut.members.get(0).context)
                        : ContextDependentCutInspection.trusted(cut.group);
                models.add(model(key, rules, visible, owned(inventories, work), work));
            }
            return new Audit(true, "All original definite rules and signed facts satisfy separate cut models", models);
        } catch (Exception failure) {
            return new Audit(false, failure.getMessage(), Collections.emptyList());
        }
    }

    private static Set<Source> owned(List<ContextProductionInventory> inventories, Runnable work) {
        Set<Source> sources = new HashSet<>();
        for (ContextProductionInventory inventory : inventories) for (ContextProductionInventory.Row row : inventory.primary) {
            work.run();
            sources.add(new Source(inventory.context, inventory.revision, row.nativeRule, inventory.localX,
                    "inventory:" + inventory.fingerprint));
        }
        return sources;
    }

    private static Model model(String key, List<ContextArithmeticRuleInspection> rules, List<Premise> visible,
                               Set<Source> sources, Runnable work) throws Exception {
        ContextArithmeticRuleInspection.requireCoverage(sources, rules, visible, work);
        List<Fact> positives = new ArrayList<>();
        for (Premise premise : visible) {
            work.run();
            if (premise.fact.positive && !ContextArithmeticRuleInspection.modelContains(positives, premise.fact, work))
                positives.add(premise.fact);
        }
        for (Premise premise : visible) {
            work.run();
            if (!premise.fact.positive && ContextArithmeticRuleInspection.modelContains(positives, premise.fact.opposite(), work))
                throw new IllegalStateException("Arithmetic model violates a negative fact");
        }
        long obligations = 0;
        for (ContextArithmeticRuleInspection rule : rules) {
            work.run();
            obligations += rule.auditModel(positives, work);
        }
        return new Model(key, positives, obligations);
    }
}
