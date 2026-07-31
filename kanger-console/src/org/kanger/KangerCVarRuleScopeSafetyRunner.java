/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Term;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused regression gate for rule-scoped C-variable child identity.
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

            System.out.println("CVAR_RULE_SCOPE_PASS per-rule identity");
            System.out.println("CVAR_RULE_SCOPE_PASS selective unlink");
            System.out.println("CVAR_RULE_SCOPE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
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
}
