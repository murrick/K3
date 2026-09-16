/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.*;
import org.kanger.udf.UDF;

/** Tests the actual reuse branch with oracle lookup disabled. */
public final class LatentOccurrenceReuseRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("occurrence-reuse-").toString());
        System.setProperty("kanger.experiment.latent", "factory");
        System.setProperty("kanger.experiment.reuseOccurrenceLists", "true");
        User user = new User(); new UDF().init(user); Mind root = new Mind(user);
        require(root.compile("!@x p(x) -> q(x); !p(1);"), "compile");
        List<Domain> domains = new ArrayList<>();
        for (IRule r : root.getRules()) for (List<Domain> branch : ((Rule) r).getTree()) domains.addAll(branch);
        IRule target = null; Domain source = null; TVariable variable = null;
        for (IRule r : root.getRules()) for (List<Domain> branch : ((Rule) r).getTree())
            for (Domain d : branch) for (Domain s : domains)
                if (d.getPredicateId() == s.getPredicateId() && d.isAntc() != s.isAntc()
                        && !d.getArguments().getTVariables(root).isEmpty()) {
                    target = r; source = s; variable = d.getArguments().getTVariables(root).get(0);
                }
        require(target != null, "compatible variable occurrence");
        Mind child = new Mind(root);
        try {
            Linker linker = new Linker(child);
            Method method = Linker.class.getDeclaredMethod("latentBranches", IRule.class, Domain.class,
                    Map.class, boolean.class, boolean.class);
            method.setAccessible(true);
            Map<Object, Object> memo = new IdentityHashMap<>();
            List<?> first = (List<?>) ((List<?>) method.invoke(linker, target, source, memo, false, true)).get(0);
            require(!first.isEmpty(), "nonempty row");
            root.getTVars().get(variable.getId());
            require(variable.getMind() == root, "context deliberately switched");
            List<?> second = (List<?>) ((List<?>) method.invoke(linker, target, source, memo, false, true)).get(0);
            require(first == second, "list was not reused");
            require(variable.getMind() == child, "reuse did not restore child variable context");
            for (Object domain : second) require(((Domain) domain).getMind() == child, "domain context");
            List<?> fresh = (List<?>) ((List<?>) method.invoke(linker, target, source,
                    new IdentityHashMap<Object, Object>(), false, true)).get(0);
            require(fresh != second, "list escaped rotator scope");
            require(fresh.size() == second.size(), "fresh count");
            for (int i = 0; i < fresh.size(); i++) require(fresh.get(i) == second.get(i), "canonical identity");
        } finally { root.release(child); }
        System.out.println("OCCURRENCE_REUSE_CONTEXT_PASS");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
