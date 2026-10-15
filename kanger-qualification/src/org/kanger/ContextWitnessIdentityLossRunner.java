/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.enums.QueryPass;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Mechanical boundary diagnostic, NOT a native correctness qualification. */
public final class ContextWitnessIdentityLossRunner {
    private static int checks;
    private static void require(boolean value,String label) {
        ++checks; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-loss-").toString());
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        require(mind.compile("!$x out(x,1);",null,false),"consistent existential base");
        Rule primary = null;
        for (Object value : mind.getRules()) if (((Rule)value).isStored()) primary = (Rule)value;
        require(primary != null,"primary witness rule exists");
        Domain base = primary.getDomain(); Term root = (Term)base.get(0).getValue(mind);
        Rule producer = new Rule(mind); mind.getRules().register(producer);
        Term child = (Term)mind.getTerms().createCVar(producer,root.getName(mind),root);
        ArgumentsList arguments = new ArgumentsList();
        arguments.add(new Argument((org.kanger.interfaces.ITerm)child)); arguments.add(new Argument(base.get(1).getValue(mind)));
        Domain negative = mind.getDomains().add(base.getPredicate(),!base.isAntc(),arguments,producer);
        require(child.getParentId(mind) == root.getId(),"transient binding retains the actual witness");
        require(negative.equalsBase(base),"opposite transient instance compares equal before rebinding");
        Rule owner = new Rule(mind); mind.getRules().register(owner); mind.setQueryPass(QueryPass.ACCEPT);
        ArgumentsList rebound = GeneratedCVarMaterializer.rebindForGeneratedRule(arguments,negative,mind,owner);
        Domain durable = mind.getDomains().add(base.getPredicate(),negative.isAntc(),rebound,owner);
        Term replacement = (Term)durable.get(0).getValue(mind);
        require(replacement.getId() != root.getId() && replacement.getParent(mind) == null,
                "ACCEPT materialization allocates an unrelated durable root");
        require(replacement.getRuleId() == owner.getId(),"replacement belongs to the generated owner");
        require(!durable.equalsBase(base),"same opposite instance no longer compares equal after rebinding");
        require(negative.equalsBase(base),"source binding itself remains intact");
        require(base.isAntc() != durable.isAntc(),"polarity was not the lost information");
        mind.setQueryPass(QueryPass.CHECK);
        require(GeneratedCVarMaterializer.rebindForGeneratedRule(arguments,negative,mind,owner) == arguments,
                "CHECK bypasses durable rebinding");
        System.out.println("CONTEXT_WITNESS_IDENTITY_LOSS_DIAGNOSTIC_PASS checks="+checks);
        System.exit(0);
    }
}
