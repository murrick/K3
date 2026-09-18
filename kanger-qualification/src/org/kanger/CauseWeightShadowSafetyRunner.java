package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.*;

public final class CauseWeightShadowSafetyRunner {
    private static final class TestCause extends Cause {
        final Solve donor = new Solve();
        TestCause(ArgumentsList args) { donor.getArguments().addAll(args); }
        @Override public Solve getDonor() { return donor; }
        @Override public int hashCode() { return System.identityHashCode(this); }
        @Override public boolean equals(Object o) { return this == o; }
    }
    private static ArgumentsList args(ITerm... terms) {
        ArgumentsList result = new ArgumentsList();
        for (ITerm term : terms) result.add(term == null ? new Argument() : new Argument(term));
        return result;
    }
    private static int weight(ArgumentsList own, ArgumentsList donor, Mind mind) throws Exception {
        Method ids = CachedDomain.class.getDeclaredMethod("resolvedIds", ArgumentsList.class, Mind.class);
        Method weight = CachedDomain.class.getDeclaredMethod("resolvedWeight", List.class, ArgumentsList.class, Mind.class);
        ids.setAccessible(true); weight.setAccessible(true);
        return (Integer) weight.invoke(null, ids.invoke(null, own, mind), donor, mind);
    }
    public static void main(String[] ignored) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("cause-shadow-").toString());
        System.setProperty("kanger.experiment.shadowCauseWeights", "true");
        Mind mind = new Mind(new User());
        ITerm a = mind.getTerms().add("a"), b = mind.getTerms().add("b"), c = mind.getTerms().add("c");
        require(weight(args(a,a,b,null), args(a,a,null), mind) == 2, "duplicate own/donor and empty");
        require(weight(args(a,b), args(), mind) == 0, "empty donor");
        require(weight(args(), args(a), mind) == 0, "empty own");
        TVariable v = mind.getTVars().createTVar(new Rule(mind), mind.getTerms().add("v"));
        ArgumentsList dynamic = new ArgumentsList(); dynamic.add(new Argument(v));
        v.setCurrent(mind.getTValues().add(v, a));
        require(weight(dynamic, args(a), mind) == 1, "bound a");
        v.setCurrent(mind.getTValues().add(v, b));
        require(weight(dynamic, args(a), mind) == 0, "binding changed");
        v.setCurrent(null);
        require(weight(dynamic, args(a), mind) == 0, "binding cleared");
        CachedDomain domain = new CachedDomain(mind);
        domain.getArguments().addAll(args(a,b,c));
        TestCause zero = new TestCause(args()), one = new TestCause(args(a)), three = new TestCause(args(a,b,c));
        Map<ArgumentsList,Set<ICause>> map = new HashMap<>();
        map.put(domain.getArguments().convertBase(mind), new HashSet<ICause>(Arrays.asList(zero,one,three)));
        mind.getDomainCauses().put(domain,map);
        Set<ICause> expected = new HashSet<ICause>(Arrays.asList(one,three));
        require(domain.getCauses(mind).equals(expected), "remove minimum only, retain intermediate");
        require(domain.getCauses(mind).equals(expected), "memo hit");
        System.out.println("CAUSE_WEIGHT_SHADOW_BOUNDARIES_PASS");
    }
    private static void require(boolean yes, String label) { if (!yes) throw new AssertionError(label); }
}
