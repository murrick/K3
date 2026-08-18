/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Focused regression gate for rule-scoped C-variable child identity and
 * generated-rule materialization convergence.
 */
public final class KangerCVarRuleScopeSafetyRunner {

    private KangerCVarRuleScopeSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-cvar-rule-scope-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser("cvar-rule-scope", "cvar-rule-scope");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);

            verifyRuleScopedChildren(mind);
            verifyGeneratedMaterializationConvergence();

            System.out.println("CVAR_RULE_SCOPE_PASS per-rule identity");
            System.out.println("CVAR_RULE_SCOPE_PASS selective unlink");
            System.out.println("CVAR_RULE_SCOPE_PASS generated materialization convergence");
            System.out.println("CVAR_RULE_SCOPE_PASS transient witness exclusion from Values");
            System.out.println("CVAR_RULE_SCOPE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void verifyRuleScopedChildren(Mind mind) throws Exception {
        IRule sourceRule = rule(1001L);
        IRule targetRuleA = rule(2001L);
        IRule targetRuleB = rule(2002L);
        ITerm name = mind.getTerms().add("scope-variable");

        Term parent = (Term) mind.getTerms().createCVar(sourceRule, name, null);
        Term childA = (Term) mind.getTerms().createCVar(targetRuleA, name, parent);

        require(parent.getChild(mind, targetRuleA.getId()) == childA,
                "target Rule A must resolve its own child");
        require(parent.getChild(mind, targetRuleB.getId()) == null,
                "target Rule B must not alias Rule A before its child exists");

        Term childB = (Term) mind.getTerms().createCVar(targetRuleB, name, parent);

        require(parent.getChild(mind, targetRuleA.getId()) == childA,
                "Rule A child must survive creation of Rule B child");
        require(parent.getChild(mind, targetRuleB.getId()) == childB,
                "Rule B must resolve a distinct child");
        require(childA != childB && childA.getId() != childB.getId(),
                "different Rule scopes must have different child identities");
        require(childA.getParent(mind) == parent && childB.getParent(mind) == parent,
                "both children must retain the same source parent");
        require(childA.getRuleId() == targetRuleA.getId(),
                "Rule A id must be stored on child A");
        require(childB.getRuleId() == targetRuleB.getId(),
                "Rule B id must be stored on child B");

        mind.unlinkCVar(childA);
        require(parent.getChild(mind, targetRuleA.getId()) == null,
                "unlinking child A must remove only Rule A projection");
        require(parent.getChild(mind, targetRuleB.getId()) == childB,
                "unlinking child A must preserve Rule B projection");
        require(childB.getParent(mind) == parent,
                "surviving child must retain its reverse parent edge");

        mind.unlinkCVar(parent);
        require(parent.getChild(mind, targetRuleB.getId()) == null,
                "unlinking parent must remove all remaining projections");
        require(childB.getParent(mind) == null,
                "unlinking parent must remove reverse child edges");
    }

    private static void verifyGeneratedMaterializationConvergence() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        User user = (User) UserFactory.createUser("cvar-materialization-" + suffix,
                "cvar-materialization-" + suffix);
        new UDF().init(user);
        new DB().init(user);
        Mind mind = (Mind) new Mind(user).clearWorkspace();

        mind.compile("!@x $y parent(y,x);"
                + "!@x ~parent(x,x);"
                + "!@x (male(x) || female(x)) && ~(male(x) && female(x));"
                + "!@x @y father(x,y) -> male(x), parent(x,y);"
                + "!@x @y mother(x,y) -> female(x), parent(x,y);"
                + "!mother(Mary,Sarah);"
                + "!mother(Alice,Bob);");

        ClosureSnapshot baseline = ClosureSnapshot.capture(mind);
        require(baseline.abstractRuleIds.size() >= 2,
                "fixture must materialize at least two abstract generated statements");
        require(baseline.abstractTermIds.size() == baseline.abstractRuleIds.size(),
                "independent generated statements must own distinct existential witnesses");

        require(Boolean.TRUE.equals(mind.query("?$x parent(x,Sarah);", null, false)),
                "parent query must be true");
        List<ITerm> parentValues = mind.getValues().getValues("x");
        require(parentValues.size() == 1,
                "transient existential witness must not be exported as an additional Value");
        require(!parentValues.get(0).isCVariable()
                        && "Mary".equals(String.valueOf(parentValues.get(0).getValue())),
                "only the concrete parent Mary must be exported from the fixture");

        for (int cycle = 1; cycle <= 4; ++cycle) {
            require(Boolean.TRUE.equals(mind.query("?", null, false)),
                    "bare qualification must remain valid at cycle " + cycle);
            ClosureSnapshot current = ClosureSnapshot.capture(mind);
            require(current.generatedCount == baseline.generatedCount,
                    "generated closure grew at qualification cycle " + cycle);
            require(current.abstractRuleIds.equals(baseline.abstractRuleIds),
                    "abstract generated Rule identities changed at qualification cycle " + cycle);
            require(current.abstractTermIds.equals(baseline.abstractTermIds),
                    "durable existential witness identities changed at qualification cycle " + cycle);
        }
    }

    private static IRule rule(final long id) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getId".equals(method.getName())) {
                    return id;
                }
                if ("toString".equals(method.getName())) {
                    return "Rule(" + id + ")";
                }
                if ("hashCode".equals(method.getName())) {
                    return Long.valueOf(id).hashCode();
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                throw new UnsupportedOperationException(method.getName());
            }
        };
        return (IRule) Proxy.newProxyInstance(
                IRule.class.getClassLoader(),
                new Class<?>[]{IRule.class},
                handler);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ClosureSnapshot {
        private final int generatedCount;
        private final Set<Long> abstractRuleIds;
        private final Set<Long> abstractTermIds;

        private ClosureSnapshot(int generatedCount,
                                Set<Long> abstractRuleIds,
                                Set<Long> abstractTermIds) {
            this.generatedCount = generatedCount;
            this.abstractRuleIds = abstractRuleIds;
            this.abstractTermIds = abstractTermIds;
        }

        private static ClosureSnapshot capture(Mind mind) throws Exception {
            int generated = 0;
            Set<Long> abstractRules = new HashSet<>();
            Set<Long> abstractTerms = new HashSet<>();

            for (IRule candidate : mind.getRules()) {
                Rule rule = (Rule) candidate;
                if (rule.isDeleted(mind) || !rule.isGenerated()) {
                    continue;
                }
                ++generated;
                for (java.util.List<Domain> branch : rule.getTree()) {
                    for (Domain domain : branch) {
                        for (IArgument argument : domain.getArguments()) {
                            if (argument.isEmpty(mind)) {
                                continue;
                            }
                            ITerm value = argument.getValue(mind);
                            if (value == null || !value.isCVariable()) {
                                continue;
                            }
                            Term term = (Term) value;
                            String raw = String.valueOf(term.getValue());
                            require(!raw.startsWith("*"),
                                    "generated Rule retains transient C-variable child: " + rule);
                            require(term.getParent(mind) == null,
                                    "durable generated C-variable must be a root: " + rule);
                            require(term.getRuleId() == rule.getId(),
                                    "durable generated C-variable must belong to its materialized Rule: " + rule);
                            abstractRules.add(rule.getId());
                            abstractTerms.add(term.getId());
                        }
                    }
                }
            }
            return new ClosureSnapshot(generated, abstractRules, abstractTerms);
        }
    }
}
