package org.kanger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.ICache;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.*;

/** Equal returned booleans do not certify equivalent hydration effects. */
public final class StoredLookupWitness {
    private static int checks;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label); ++checks;
    }
    private static String run(boolean reuse, int mode) throws Exception {
        Mind root = new Mind(new User()), child = new Mind(root);
        ITerm a = root.getTerms().add("a"), b = root.getTerms().add("b");
        Rule rule = new Rule(root); rule.setId(900001); rule.setMindId(root.getId());
        TVariable variable = root.getTVars().createTVar(rule, root.getTerms().add("shared"));
        root.getTValues().set(variable, new TValue(variable, a));
        child.getTValues().set(variable, new TValue(variable, b));
        Predicate predicate = new Predicate(root.getTerms().add("stored"), 1); predicate.setId(910001);
        ArgumentsList ruleArgs = new ArgumentsList(); ruleArgs.add(new Argument(variable));
        Domain stored = new Domain(predicate, false, ruleArgs, rule);
        rule.getTree().get(0).add(stored); rule.setStored(root); rule.setMind(root);
        Field field = RuleFactory.class.getDeclaredField("cache"); field.setAccessible(true);
        // Controlled native cache fixture: canonical hash is captured with root a.
        ((ICache) field.get(root.getRules())).add(rule);
        ArgumentsList sourceArgs = new ArgumentsList(); sourceArgs.add(new Argument(a));
        Domain source = new Domain(predicate, false, sourceArgs, rule); source.setMind(root);
        check(source.isStored(root), "first result");
        check(variable.getMind() == root && variable.getValue() == a, "first hydration");
        if (mode == 0) variable.setMind(child);
        else if (mode == 1) {
            Predicate otherPredicate = new Predicate(root.getTerms().add("other"),1); otherPredicate.setId(910002);
            Domain other = new Domain(otherPredicate, false, ruleArgs, rule);
            other.setMind(child);
        } else rule.setDeleted(true, root);
        if (mode < 2) check(variable.getMind() == child && variable.getValue() == b, "intervening selection");
        boolean second = reuse ? true : source.isStored(root);
        if (mode < 2) {
            check(second, "equal returned booleans");
            check(variable.getMind() == (reuse ? child : root), "second context");
            check(variable.getValue() == (reuse ? b : a), "second visible value");
            return "true:"+(variable.getValue()==a?"root-a":"child-b");
        }
        check(second == reuse, "deletion changes ordinary result");
        return String.valueOf(second);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("stored-lookup-witness-").toString());
        for (int mode = 0; mode < 3; ++mode) {
            String reference = run(false, mode), reused = run(true, mode);
            check(!reference.equals(reused), "observable divergence");
            System.out.println("STORED_WITNESS mode="+mode+" reference="+reference+" reused="+reused);
        }
        System.out.println("STORED_LOOKUP_WITNESS_OK scenarios=3 checks="+checks);
    }
}
