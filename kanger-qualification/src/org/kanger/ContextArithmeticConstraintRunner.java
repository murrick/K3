/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextArithmeticFamilyRoundInspection.*;
import org.kanger.ContextArithmeticRuleInspection.Premise;

public final class ContextArithmeticConstraintRunner {
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
            inventory = ContextProductionInventory.inspect(mind, UUID.randomUUID(), 0, local, Collections.emptyList(), 1000);
            for (ContextProductionInventory.Row row : inventory.primary) {
                if (row.origin.contains("@")) rules.add(ContextArithmeticRuleInspection.inspect(mind, inventory, row.nativeRule, 100000));
                else seeds.add(Premise.primary(mind, inventory, row.nativeRule, 0, 100000));
            }
        }

        Cut cut() { return new Cut(null, Collections.singletonList(inventory), rules, seeds); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-constraint-").toString());
        definiteNegativeResiduals();
        boundBodyExpressions();
        Fixture producer = new Fixture(false, "!@x seed(x)->next(x+1);");
        Fixture guard = new Fixture(false, "!@x next(x)->~blocked(x);");
        Fixture safeX = new Fixture(true, "!seed(1);");
        Iteration safe = run(safeX, Arrays.asList(producer.cut(), guard.cut()), 16);
        require(safe.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(safe, 1000000).verified,
                "satisfied arithmetic constraint has independently checked models");
        require(safe.frame.common.stream().anyMatch(value -> !value.fact.positive && value.fact.predicate.equals("blocked")),
                "negative residual becomes common X-supported knowledge");
        Fixture privateBlocker = new Fixture(false, "!blocked(2);");
        Iteration receiving = run(safeX, Arrays.asList(producer.cut(), guard.cut(), privateBlocker.cut()), 16);
        require(receiving.status == Status.COLLISION && receiving.completedBarriers == 2
                && receiving.frame.collisions.stream().anyMatch(value -> value.cut.equals(
                        ContextDependentCutInspection.independent(privateBlocker.inventory.context))),
                "transported negative residual is qualified against a third private cut");
        Fixture x = new Fixture(true, "!seed(1); !blocked(2);");
        Iteration collision = run(x, Arrays.asList(producer.cut(), guard.cut()), 16);
        require(collision.status == Status.COLLISION && collision.completedBarriers == 1
                && collision.frame.constraints.size() == 1 && !collision.frame.collisions.isEmpty(),
                "pure negative constraint refutes a merged arithmetic product");
        ConstraintCollision evidence = collision.frame.constraints.get(0);
        require(evidence.cut.equals(ContextDependentCutInspection.independent(guard.inventory.context))
                && evidence.evidence.source.equals(guard.rules.get(0).source)
                && evidence.evidence.proof.sources.size() == 4 && evidence.evidence.proof.depth == 2
                && evidence.evidence.premises.size() == 2, "whole constraint Source and every donor survive in the proof");
        Iteration reversed = run(x, Arrays.asList(guard.cut(), producer.cut()), 16);
        require(reversed.status == Status.COLLISION && reversed.frame.constraints.get(0).evidence.proof.equals(
                evidence.evidence.proof), "constraint proof is independent of cut order");
        Iteration shallow = run(x, Arrays.asList(producer.cut(), guard.cut()), 1);
        require(shallow.status == Status.INCOMPLETE && shallow.frame == null, "constraint proof respects cumulative depth");
        Fixture different = new Fixture(true, "!seed(1); !blocked(3);");
        Iteration unequal = run(different, Arrays.asList(producer.cut(), guard.cut()), 16);
        require(unequal.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(unequal, 1000000).verified,
                "constraint requires the same variable value in both premises");

        Fixture anchor = new Fixture(true, "!anchor(1);");
        Fixture left = new Fixture(false, "!next(2);");
        Fixture right = new Fixture(false, "!blocked(2); !@x next(x)->~blocked(x);");
        Iteration independent = run(anchor, Arrays.asList(left.cut(), right.cut()), 16);
        require(independent.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(independent, 1000000).verified,
                "raw facts from independent siblings do not trigger a private constraint");
        List<Premise> seeds = new ArrayList<>(left.seeds);
        seeds.addAll(right.seeds);
        Cut trusted = new Cut("team", Arrays.asList(left.inventory, right.inventory), right.rules, seeds);
        Iteration joint = run(anchor, Collections.singletonList(trusted), 16);
        require(joint.status == Status.COLLISION && joint.completedBarriers == 0
                && joint.frame.constraints.get(0).cut.equals(ContextDependentCutInspection.trusted("team")),
                "trusted private constraint is checked at the initial barrier");
        boolean modelRefused = false;
        try {
            right.rules.get(0).auditModel(Arrays.asList(left.seeds.get(0).fact, right.seeds.get(0).fact),
                    ContextGroundProductionRounds.budget(100000));
        } catch (IllegalStateException invalid) { modelRefused = true; }
        require(modelRefused, "independent model evaluator rejects a violated constraint");
        require(!ContextArithmeticModelAudit.inspect(collision, 1000000).verified, "refuted iterations expose no valid model");
        System.out.println("CONTEXT_ARITHMETIC_CONSTRAINT_PASS checks=" + checks);
        System.exit(0);
    }

    private static void definiteNegativeResiduals() throws Exception {
        Fixture x = new Fixture(true, "!seed(1); !~next(2);");
        Fixture rule = new Fixture(false, "!@x seed(x)&&gate(x)->next(x+1);");
        Fixture gate = new Fixture(false, "!gate(1);");
        Fixture expected = new Fixture(true, "!~gate(1);");
        Iteration safe = run(x, Collections.singletonList(rule.cut()), 16);
        Premise residual = safe.frame.common.stream().filter(value -> value.fact.equivalent(expected.seeds.get(0).fact))
                .findFirst().orElseThrow(AssertionError::new);
        require(safe.status == Status.STABLE_PENDING && residual.proof.sources.size() == 3
                && residual.proof.depth == 1 && residual.proof.sources.contains(rule.rules.get(0).source),
                "bound negative head produces exact signed fact with full original support");
        require(ContextArithmeticModelAudit.inspect(safe, 1000000).verified, "safe definite residual has checked models");
        Iteration result = run(x, Arrays.asList(rule.cut(), gate.cut()), 16);
        require(result.status == Status.COLLISION && result.completedBarriers == 1
                && result.frame.collisions.stream().anyMatch(value -> value.cut.equals(
                        ContextDependentCutInspection.independent(gate.inventory.context))),
                "bound negative head transports ~gate(1) into the receiving cut");
        Iteration reverse = run(x, Arrays.asList(gate.cut(), rule.cut()), 16);
        require(reverse.status == Status.COLLISION && reverse.completedBarriers == result.completedBarriers,
                "definite residual conflict is independent of cut order");
        for (String source : Arrays.asList("!seed(1); !~next(3);", "!seed(1); !next(2);")) {
            Fixture control = new Fixture(true, source);
            Iteration compatible = run(control, Arrays.asList(rule.cut(), gate.cut()), 16);
            require(compatible.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(compatible, 1000000).verified
                    && compatible.frame.common.stream().noneMatch(value -> value.fact.equivalent(expected.seeds.get(0).fact)),
                    "negative-head donor must have opposite sign and exact computed arguments");
        }
        Fixture negativeHead = new Fixture(true, "!~done(1);");
        Fixture plainHead = new Fixture(false, "!@x gate(x)->done(x);");
        Iteration headBound = run(negativeHead, Collections.singletonList(plainHead.cut()), 16);
        require(headBound.status == Status.STABLE_PENDING && headBound.frame.common.stream().anyMatch(
                value -> value.fact.equivalent(expected.seeds.get(0).fact))
                && ContextArithmeticModelAudit.inspect(headBound, 1000000).verified,
                "ordinary negative head alone binds the residual variable");
        Fixture anchor = new Fixture(true, "!anchor(1);");
        Fixture privateRule = new Fixture(false, "!seed(1); !~next(2); !@x seed(x)&&gate(x)->next(x+1);");
        Iteration separate = run(anchor, Arrays.asList(privateRule.cut(), gate.cut()), 16);
        require(separate.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(separate, 1000000).verified
                && separate.frame.common.stream().noneMatch(value -> value.fact.equivalent(expected.seeds.get(0).fact)),
                "private definite residual does not cross independent siblings without X support");
        List<Premise> trustedSeeds = new ArrayList<>(privateRule.seeds);
        trustedSeeds.addAll(gate.seeds);
        Cut trusted = new Cut("residual", Arrays.asList(privateRule.inventory, gate.inventory), privateRule.rules, trustedSeeds);
        Iteration joined = run(anchor, Collections.singletonList(trusted), 16);
        require(joined.status == Status.COLLISION, "TRUST combines signed definite residual premises");
        Fixture chain = new Fixture(false, "!@x seed(x)&&permit(x)->gate(x);");
        Fixture permit = new Fixture(false, "!permit(1);");
        Iteration chained = run(x, Arrays.asList(rule.cut(), chain.cut(), permit.cut()), 16);
        require(chained.status == Status.COLLISION && chained.completedBarriers == 2,
                "negative residuals propagate through two frozen barriers");
        Iteration shallow = run(x, Arrays.asList(rule.cut(), chain.cut(), permit.cut()), 1);
        require(shallow.status == Status.INCOMPLETE && shallow.frame == null,
                "signed definite residual chain respects cumulative proof depth without prefix");
    }

    private static void boundBodyExpressions() throws Exception {
        Fixture x = new Fixture(true, "!seed(1); !next(2); !pair(1,2);");
        for (String body : Arrays.asList("seed(x)&&next(x+1)", "next(x+1)&&seed(x)")) {
            Fixture rule = new Fixture(false, "!@x " + body + "->done(x+2);");
            Iteration result = run(x, Collections.singletonList(rule.cut()), 16);
            require(result.status == Status.STABLE_PENDING && result.frame.common.stream().anyMatch(
                    value -> value.fact.positive && value.fact.predicate.equals("done")),
                    "bound arithmetic body derives regardless of premise order");
            require(ContextArithmeticModelAudit.inspect(result, 1000000).verified,
                    "bound arithmetic body passes independent assignment enumeration");
            Fixture mismatch = new Fixture(true, "!seed(1); !next(3);");
            Iteration absent = run(mismatch, Collections.singletonList(rule.cut()), 16);
            require(absent.status == Status.STABLE_PENDING && absent.frame.common.stream().noneMatch(
                    value -> value.fact.predicate.equals("done"))
                    && ContextArithmeticModelAudit.inspect(absent, 1000000).verified,
                    "unequal computed body does not produce a conclusion");
        }
        Fixture reordered = new Fixture(false, "!@x pair(x+1,x)->done(x+2);");
        Fixture donor = new Fixture(true, "!pair(2,1);");
        Fixture expected = new Fixture(true, "!done(3);");
        require(reordered.rules.get(0).apply(0, donor.seeds, 16, 100000).conclusion.fact
                .equivalent(expected.seeds.get(0).fact), "direct apply binds later argument before checking expression");
        Fixture badDonor = new Fixture(true, "!pair(3,1);");
        boolean directRefused = false;
        try { reordered.rules.get(0).apply(0, badDonor.seeds, 16, 100000); }
        catch (IllegalArgumentException mismatch) { directRefused = true; }
        require(directRefused, "direct apply rejects unequal expression after complete binding");
        Fixture guard = new Fixture(false, "!@x seed(x)&&pair(x,x+1)->~blocked(x);");
        Fixture blocker = new Fixture(false, "!blocked(1);");
        Iteration denied = run(x, Arrays.asList(guard.cut(), blocker.cut()), 16);
        require(denied.status == Status.COLLISION && denied.completedBarriers == 1,
                "computed constraint body transports negative residual to another cut");
        Fixture safe = new Fixture(true, "!seed(1); !pair(1,3);");
        Iteration allowed = run(safe, Arrays.asList(guard.cut(), blocker.cut()), 16);
        require(allowed.status == Status.STABLE_PENDING && ContextArithmeticModelAudit.inspect(allowed, 1000000).verified,
                "constraint arithmetic mismatch remains compatible");
        Fixture unbound = new Fixture(false, "!@x next(x+1)->done(x);");
        boolean refused = false;
        try { run(x, Collections.singletonList(unbound.cut()), 16); }
        catch (IllegalArgumentException unsupported) { refused = true; }
        require(refused, "inverse-only body remains explicitly unsupported");
        Fixture missing = new Fixture(false, "!@x seed(x)&&next(x+1)->done(x+2);");
        boolean auditRefused = false;
        try { missing.rules.get(0).auditModel(Arrays.asList(x.seeds.get(0).fact,
                x.seeds.get(1).fact, x.seeds.get(2).fact), ContextGroundProductionRounds.budget(100000)); }
        catch (IllegalStateException invalid) { auditRefused = true; }
        require(auditRefused, "independent audit detects missing computed-body conclusion");
    }

    private static Iteration run(Fixture x, List<Cut> cuts, int depth) throws Exception {
        Frame frame = ContextArithmeticFamilyRoundInspection.start(x.inventory, x.rules, x.seeds, cuts, 1000000, 100);
        return ContextArithmeticFamilyRoundInspection.iterate(frame, 8, depth, 1000000, 100);
    }

    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
}
