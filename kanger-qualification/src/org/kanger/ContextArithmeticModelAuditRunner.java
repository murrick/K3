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
        mixedDomainAudit();
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

    private static void mixedDomainAudit() throws Exception {
        Fixture typedX = new Fixture(true, "!seed(1); !next(2); !pair(2,1); !tag(\"outside\");");
        for (String body : Arrays.asList("next(x+1)&&seed(x)", "seed(x)&&next(x+1)", "pair(x+1,x)")) {
            Fixture rule = new Fixture(false, "!@x " + body + "->done(x+2);");
            Frame initial = start(typedX, Collections.singletonList(rule.cut()));
            Audit missing = ContextArithmeticModelAudit.inspectFrame(initial, 1000000);
            require(!missing.verified && missing.models.isEmpty() && missing.reason.contains("violates an original rule"),
                    "mixed-domain audit still detects the missing enabled numeric conclusion");
            Iteration closure = ContextArithmeticFamilyRoundInspection.iterate(initial, 8, 16, 1000000, 100);
            Audit audit = ContextArithmeticModelAudit.inspect(closure, 1000000);
            require(audit.verified, "unrelated string does not evaluate an impossible numeric antecedent: " + audit.reason);
            Audit stopped = ContextArithmeticModelAudit.inspect(closure, 1);
            require(!stopped.verified && stopped.models.isEmpty(), "mixed-domain guard spends bounded audit work");
        }
        Fixture empty = new Fixture(true, "!tag(\"outside\");");
        Fixture dormant = new Fixture(false, "!@x next(x+1)&&seed(x)->done(x+2);");
        Iteration none = ContextArithmeticFamilyRoundInspection.iterate(
                start(empty, Collections.singletonList(dormant.cut())), 8, 16, 1000000, 100);
        require(ContextArithmeticModelAudit.inspect(none, 1000000).verified,
                "missing ordinary relation disables an arithmetic antecedent over mixed values");
        Fixture constants = new Fixture(true, "!seed(1); !pair(2,1,2); !tag(\"outside\");");
        Fixture constantRule = new Fixture(false, "!@x pair(x+1,x,1)&&seed(x)->done(x+2);");
        Iteration mismatch = ContextArithmeticFamilyRoundInspection.iterate(
                start(constants, Collections.singletonList(constantRule.cut())), 8, 16, 1000000, 100);
        Audit constantAudit = ContextArithmeticModelAudit.inspect(mismatch, 1000000);
        require(constantAudit.verified && constantAudit.models.stream().noneMatch(value -> has(value, "done")),
                "ordinary constant mismatch disables expression comparison within the same atom");
        Fixture guard = new Fixture(false, "!@x pair(x+1,x)&&seed(x)->~blocked(x);");
        Iteration constrained = ContextArithmeticFamilyRoundInspection.iterate(
                start(typedX, Collections.singletonList(guard.cut())), 8, 16, 1000000, 100);
        require(ContextArithmeticModelAudit.inspect(constrained, 1000000).verified,
                "mixed-domain constraints retain a checked model with negative residuals");
        Fixture guardedMismatch = new Fixture(true, "!seed(1); !pair(3,1); !blocked(1); !tag(\"outside\");");
        Iteration mismatchConstraint = ContextArithmeticFamilyRoundInspection.iterate(
                start(guardedMismatch, Collections.singletonList(guard.cut())), 8, 16, 1000000, 100);
        require(mismatchConstraint.status == Status.STABLE_PENDING
                && ContextArithmeticModelAudit.inspect(mismatchConstraint, 1000000).verified,
                "computed constraint mismatch over mixed values is independently satisfiable");
        Fixture wrongType = new Fixture(true, "!seed(\"outside\"); !next(2);");
        Iteration unsupported = ContextArithmeticFamilyRoundInspection.iterate(
                start(wrongType, Collections.singletonList(dormant.cut())), 8, 16, 1000000, 100);
        Audit unsafe = ContextArithmeticModelAudit.inspect(unsupported, 1000000);
        require(unsupported.status == Status.INCOMPLETE && !unsafe.verified && unsafe.models.isEmpty(),
                "genuinely bound nonnumeric expression input remains incomplete without a model prefix");
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
