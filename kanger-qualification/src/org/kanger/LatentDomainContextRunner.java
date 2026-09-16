/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;
import org.kanger.udf.UDF;

/** Counterexamples to treating identical Domain pointers as context-free references. */
public final class LatentDomainContextRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("domain-context-").toString());
        User user = new User(); new UDF().init(user);
        Mind root = new Mind(user);
        require(root.compile("!@x p(x) -> q(x);"), "compile");
        Domain domain = null; TVariable variable = null;
        for (IRule rule : root.getRules())
            for (List<Domain> branch : ((Rule) rule).getTree())
                for (Domain candidate : branch) {
                    List<TVariable> variables = candidate.getArguments().getTVariables(root);
                    if (!variables.isEmpty()) { domain = candidate; variable = variables.get(0); }
                }
        require(domain != null, "variable domain");
        long owner = variable.getMindId();
        List<String> evidence = new ArrayList<>();
        Mind child = new Mind(root);
        try {
            Domain rootView = root.getDomains().get(domain.getId());
            Domain childView = child.getDomains().get(domain.getId());
            require(rootView == childView, "shared canonical domain");
            require(childView.getMind() == child && variable.getMind() == child, "child binding");
            evidence.add("same-pointer=true; retained-parent-pointer-now-has-child-context=true");

            // A variable factory lookup can change the variable context independently of Domain.
            require(root.getTVars().get(variable.getId()) == variable, "shared canonical variable");
            require(childView.getMind() == child && variable.getMind() == root, "split contexts");
            evidence.add("domain-context=child; variable-context=root; domain-only-context-guard-insufficient=true");

            require(child.getDomains().get(domain.getId()) == childView, "identity after rebind");
            require(variable.getMind() == child, "reference lookup restores variable context");
            evidence.add("reference-lookup-restores-variable-context=true");

            root.getTVars().get(variable.getId());
            childView.setMind(child);
            require(variable.getMind() == child, "explicit rebind restores variable context");
            require(variable.getMindId() == owner, "stable owner unchanged");
            evidence.add("explicit-setMind-restores-variable-context=true; stable-owner-unchanged=true");
        } finally { root.release(child); }
        require(root.getDomains().get(domain.getId()) == domain, "root identity after rollback");
        require(domain.getMind() == root && variable.getMind() == root, "root context after rollback lookup");
        evidence.add("rollback-followed-by-root-lookup-restores-root-context=true");
        evidence.add("DOMAIN_CONTEXT_PASS");
        Files.write(Paths.get(args[0]), evidence, StandardCharsets.UTF_8);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
