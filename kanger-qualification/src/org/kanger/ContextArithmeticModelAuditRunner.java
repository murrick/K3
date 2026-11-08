/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticFamilyRoundInspection.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;
import org.kanger.ContextArithmeticModelAudit.*;

public final class ContextArithmeticModelAuditRunner {
    private static int checks;

    private static final class Fixture {
        final ContextProductionInventory inventory;
        final List<ContextArithmeticRuleInspection> rules = new ArrayList<>();
        final List<Premise> seeds = new ArrayList<>();

        Fixture(boolean local, String source) throws Exception {
            User user = new User();
            Mind mind = new Mind(user);
            user.setCurrentMind(mind);
            if (!mind.compile(source, null, false)) throw new AssertionError("fixture " + source);
            inventory = ContextProductionInventory.inspect(mind, UUID.randomUUID(), 0,
                    local, Collections.emptyList(), 1000);
            for (ContextProductionInventory.Row row : inventory.primary) {
                if (row.origin.contains("@")) rules.add(ContextArithmeticRuleInspection.inspect(mind, inventory, row.nativeRule, 100000));
                else seeds.add(Premise.primary(mind, inventory, row.nativeRule, 0, 100000));
            }
        }

        Cut cut() { return new Cut(null, Collections.singletonList(inventory), rules, seeds); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-model-audit-").toString());
        Fixture x = new Fixture(true, "!seed(1); !@x out(x)->local(x+1);");
        Fixture a = new Fixture(false, "!@x seed(x)->middle(x+1); !private_seed(4); !@x private_seed(x)->hidden(x+1);");
        Fixture b = new Fixture(false, "!@x middle(x)->out(x+1);");
        Frame initial = start(x, Arrays.asList(a.cut(), b.cut()));
        Audit premature = ContextArithmeticModelAudit.inspectFrame(initial, 1000000);
        require(!premature.verified && premature.models.isEmpty()
                && premature.reason.contains("violates an original rule"), "independent model audit detects a missing production");
        Iteration closed = ContextArithmeticFamilyRoundInspection.iterate(initial, 8, 16, 1000000, 100);
        Audit checked = ContextArithmeticModelAudit.inspect(closed, 1000000);
        require(checked.verified && checked.models.size() == 3, "separate complete models for X, A and B");
        Model am = model(checked, ContextDependentCutInspection.independent(a.inventory.context));
        Model bm = model(checked, ContextDependentCutInspection.independent(b.inventory.context));
        require(has(am, "hidden") && !has(bm, "hidden") && has(bm, "out"), "private truths are cut-local while common products are shared");
        require(checked.models.stream().mapToLong(value -> value.obligations).sum() > 0,
                "original arithmetic implications have checked enabled instances");
        Audit stopped = ContextArithmeticModelAudit.inspect(closed, 1);
        require(!stopped.verified && stopped.models.isEmpty(), "audit work stop exposes no model prefix");
        Iteration prefix = ContextArithmeticFamilyRoundInspection.iterate(initial, 1, 16, 1000000, 100);
        require(!ContextArithmeticModelAudit.inspect(prefix, 1000000).verified, "an incomplete iteration cannot be audited as stable");

        Fixture anchor = new Fixture(true, "!anchor(1);");
        Fixture positive = new Fixture(false, "!p(1);");
        Fixture negative = new Fixture(false, "!~p(1);");
        Iteration siblings = ContextArithmeticFamilyRoundInspection.iterate(
                start(anchor, Arrays.asList(positive.cut(), negative.cut())), 2, 16, 1000000, 100);
        Audit separate = ContextArithmeticModelAudit.inspect(siblings, 1000000);
        require(separate.verified && has(model(separate, ContextDependentCutInspection.independent(positive.inventory.context)), "p")
                && !has(model(separate, ContextDependentCutInspection.independent(negative.inventory.context)), "p"),
                "independent opposite sibling facts satisfy separate models");
        Fixture dormant = new Fixture(false, "!@x fun(x)->next(x+1);");
        Iteration emptyBodyRelation = ContextArithmeticFamilyRoundInspection.iterate(
                start(anchor, Collections.singletonList(dormant.cut())), 2, 16, 1000000, 100);
        Audit empty = ContextArithmeticModelAudit.inspect(emptyBodyRelation, 1000000);
        require(empty.verified && !has(model(empty, ContextDependentCutInspection.independent(dormant.inventory.context)), "fun"),
                "empty antecedent relation gives a universal model without a finite-domain assumption");
        boolean immutable = false;
        try { checked.models.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "checked models are immutable");
        System.out.println("CONTEXT_ARITHMETIC_MODEL_AUDIT_PASS checks=" + checks);
        System.exit(0);
    }

    private static Frame start(Fixture x, List<Cut> cuts) throws Exception {
        return ContextArithmeticFamilyRoundInspection.start(x.inventory, x.rules, x.seeds, cuts, 1000000, 100);
    }

    private static Model model(Audit audit, String key) {
        return audit.models.stream().filter(value -> value.cut.equals(key)).findFirst().orElseThrow(AssertionError::new);
    }

    private static boolean has(Model model, String predicate) {
        return model.positiveFacts.stream().anyMatch(fact -> fact.predicate.equals(predicate));
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}
