package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.IReactor;
import org.kanger.primitives.TVariableSet;
import org.kanger.units.*;

/** Mutations occur inside terminal callbacks of the real private rotation loop. */
public final class RotationFrontierSafetyRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("frontier-safety-").toString());
        System.setProperty("kanger.experiment.profileRotations", "true");
        for (String scenario : Arrays.asList("publish", "outer", "alias", "empty", "unary", "free", "union", "null", "snapshot")) {
            List<Integer> reference = run(scenario, "off");
            for (String mode : Arrays.asList("shadow", "filter", "verify", "selected")) {
                List<Integer> actual = run(scenario, mode);
                if (!reference.equals(actual)) throw new AssertionError(scenario + ": " + reference + " != " + actual);
            }
            System.out.println("FRONTIER_BOUNDARY_PASS " + scenario + " " + reference);
        }
    }

    private static List<Integer> run(String scenario, String mode) throws Exception {
        System.setProperty("kanger.experiment.shadowRotationFrontier", String.valueOf(mode.equals("shadow") || mode.equals("verify")));
        System.setProperty("kanger.experiment.filterRotationFrontier", String.valueOf(mode.equals("filter") || mode.equals("verify")));
        System.setProperty("kanger.experiment.versionedSolveSync", "true");
        System.setProperty("kanger.experiment.selectRotationIds", String.valueOf(mode.equals("selected")));
        Mind mind = new Mind(new User());
        Rule rule = new Rule(mind);
        TVariable a = mind.getTVars().createTVar(rule, mind.getTerms().add("a"));
        TVariable b = mind.getTVars().createTVar(rule, mind.getTerms().add("b"));
        TVariable c = mind.getTVars().createTVar(rule, mind.getTerms().add("c"));
        SortedSet<TVariable> suffix = new TreeSet<>(Arrays.asList(a,b));
        final TVariable inner = suffix.first(), outer = suffix.last();
        final TValue one = mind.getTValues().add(inner, mind.getTerms().add(1));
        final TValue two = mind.getTValues().add(inner, mind.getTerms().add(2));
        TValue three = mind.getTValues().add(inner, mind.getTerms().add(3));
        final TValue left = mind.getTValues().add(outer, mind.getTerms().add(10));
        final TValue right = mind.getTValues().add(outer, mind.getTerms().add(20));
        outer.setCurrent(scenario.equals("null") ? null : left);
        if (!scenario.equals("free")) mind.addTSolve(Arrays.asList(one, scenario.equals("empty") ? right : left));
        if (scenario.equals("outer")) mind.addTSolve(Arrays.asList(two, right));
        if (scenario.equals("unary")) mind.addTSolve(Collections.singletonList(three));
        if (scenario.equals("union")) {
            TValue other = mind.getTValues().add(c, mind.getTerms().add(30));
            mind.addTSolve(Arrays.asList(two, other));
        }
        final Map<TVariableSet,List<TSolve>> alias = scenario.equals("alias") ? mind.getRuleSolves() : null;
        final List<Integer> visits = new ArrayList<>();
        IReactor terminal = new IReactor() {
            public Object run(Object ignored) throws Exception {
                TValue current = inner.getCurrent();
                visits.add(current == one ? 1 : current == two ? 2 : 3);
                if (current == one) {
                    if (scenario.equals("snapshot")) {
                        TValue fresh = mind.getTValues().add(inner, mind.getTerms().add(4));
                        mind.addTSolve(Arrays.asList(fresh, left));
                    }
                    if (scenario.equals("publish")) mind.addTSolve(Arrays.asList(two, left));
                    if (scenario.equals("outer")) outer.setCurrent(right);
                    if (alias != null) alias.values().iterator().next().add(new TSolve(Arrays.asList(two,left), mind));
                }
                return false;
            }
        };
        Method rotate = Linker.class.getDeclaredMethod("rotateVariables", SortedSet.class, SortedSet.class, IReactor.class);
        rotate.setAccessible(true);
        long before = Linker.experimentalRotationProfile()[1];
        rotate.invoke(new Linker(mind), new TreeSet<>(Collections.singletonList(inner)), suffix, terminal);
        long checks = Linker.experimentalRotationProfile()[1] - before;
        if (scenario.equals("empty") && checks != ((mode.equals("filter") || mode.equals("selected")) ? 0 : 3))
            throw new AssertionError("Expected actual rejection bypass: " + mode + " " + checks);
        if (scenario.equals("alias") && checks != 3)
            throw new AssertionError("Exposed map must retain oracle checks");
        if (inner.getCurrent() != three) throw new AssertionError("Final current binding changed: " + scenario);
        return visits;
    }
}
