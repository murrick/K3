from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    source = file_path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one anchor, found {count}")
    file_path.write_text(source.replace(old, new, 1), encoding="utf-8")


# Mind: retain the historical one-child map only as a compatibility view,
# and add the semantic authority keyed by parent C-variable and target Rule id.
replace_once(
    "kanger/src/org/kanger/Mind.java",
    """    private final Map<ITerm, ITerm> cvarChilds = new HashMap<>();
    private final Map<ITerm, ITerm> cvarParents = new HashMap<>();
""",
    """    private final Map<ITerm, ITerm> cvarChilds = new HashMap<>();
    private final Map<ITerm, Map<Long, ITerm>> cvarChildrenByRule = new HashMap<>();
    private final Map<ITerm, ITerm> cvarParents = new HashMap<>();
""",
)

replace_once(
    "kanger/src/org/kanger/Mind.java",
    """    public Map<ITerm, ITerm> getCvarChilds() {
        return cvarChilds;
    }

    public Map<ITerm, ITerm> getCvarParents() {
        return cvarParents;
    }

    /** Bind C-variable links to an explicit active Mind context. */
    public void linkCVar(ITerm parent, ITerm child) {
        if (parent != null && child != null) {
            cvarParents.put(child, parent);
            cvarChilds.put(parent, child);
        }
    }

    public void unlinkCVar(ITerm term) {
        if (term == null) {
            return;
        }
        Set<ITerm> linked = new HashSet<>();
        linked.add(term);
        ITerm child = cvarChilds.get(term);
        if (child != null) linked.add(child);
        ITerm parent = cvarParents.get(term);
        if (parent != null) linked.add(parent);
        for (Map.Entry<ITerm, ITerm> entry : cvarChilds.entrySet()) {
            if (entry.getKey().equals(term) || entry.getValue().equals(term)) {
                linked.add(entry.getKey());
                linked.add(entry.getValue());
            }
        }
        for (Map.Entry<ITerm, ITerm> entry : cvarParents.entrySet()) {
            if (entry.getKey().equals(term) || entry.getValue().equals(term)) {
                linked.add(entry.getKey());
                linked.add(entry.getValue());
            }
        }
        cvarChilds.entrySet().removeIf(e -> linked.contains(e.getKey()) || linked.contains(e.getValue()));
        cvarParents.entrySet().removeIf(e -> linked.contains(e.getKey()) || linked.contains(e.getValue()));
    }

    private void clearCVarLinks() {
        cvarChilds.clear();
        cvarParents.clear();
    }
""",
    """    /**
     * Historical one-child view retained for binary/source compatibility.
     * Semantic lookup must use {@link #getCVarChild(ITerm, long)} because one
     * parent C-variable can have a distinct projection in each target Rule.
     */
    @Deprecated
    public Map<ITerm, ITerm> getCvarChilds() {
        return cvarChilds;
    }

    public Map<ITerm, ITerm> getCvarParents() {
        return cvarParents;
    }

    /**
     * Return the canonical child projection of {@code parent} in the binding
     * scope of {@code targetRuleId}, searching parent Mind contexts when the
     * current transaction does not own that projection.
     */
    public ITerm getCVarChild(ITerm parent, long targetRuleId) {
        Map<Long, ITerm> children = cvarChildrenByRule.get(parent);
        ITerm child = children == null ? null : children.get(targetRuleId);
        if (child == null && next != null) {
            return ((Mind) next).getCVarChild(parent, targetRuleId);
        }
        return child;
    }

    /** Bind a C-variable projection to its explicit target Rule scope. */
    public void linkCVar(ITerm parent, ITerm child) {
        if (parent == null || child == null) {
            return;
        }
        if (!(child instanceof Term)) {
            throw new IllegalArgumentException("C-variable child must be a Term");
        }

        long targetRuleId = ((Term) child).getRuleId();
        if (targetRuleId < 0) {
            throw new IllegalStateException("C-variable child has no target Rule");
        }

        Map<Long, ITerm> children = cvarChildrenByRule.get(parent);
        if (children == null) {
            children = new HashMap<>();
            cvarChildrenByRule.put(parent, children);
        }

        ITerm displaced = children.put(targetRuleId, child);
        if (displaced != null && !displaced.equals(child)) {
            cvarParents.remove(displaced);
        }
        cvarParents.put(child, parent);

        // Compatibility view only. Linker and new code must use rule-scoped lookup.
        cvarChilds.put(parent, child);
    }

    public void unlinkCVar(ITerm term) {
        if (term == null) {
            return;
        }

        Map<Long, ITerm> ownedChildren = cvarChildrenByRule.remove(term);
        if (ownedChildren != null) {
            for (ITerm child : ownedChildren.values()) {
                cvarParents.remove(child);
            }
            cvarChilds.remove(term);
        }

        ITerm parent = cvarParents.remove(term);
        if (parent != null) {
            Map<Long, ITerm> siblings = cvarChildrenByRule.get(parent);
            if (siblings != null) {
                Iterator<Map.Entry<Long, ITerm>> iterator = siblings.entrySet().iterator();
                while (iterator.hasNext()) {
                    if (term.equals(iterator.next().getValue())) {
                        iterator.remove();
                    }
                }
                if (siblings.isEmpty()) {
                    cvarChildrenByRule.remove(parent);
                }
            }

            cvarChilds.remove(parent);
            if (siblings != null && !siblings.isEmpty()) {
                cvarChilds.put(parent, siblings.values().iterator().next());
            }
        }

        // Remove any stale compatibility edge that points to the deleted term.
        cvarChilds.entrySet().removeIf(entry -> term.equals(entry.getValue()));
    }

    private void clearCVarLinks() {
        cvarChilds.clear();
        cvarChildrenByRule.clear();
        cvarParents.clear();
    }
""",
)

# Term: add explicit rule-scoped lookup while retaining the old method for
# compatibility with callers outside the repository.
replace_once(
    "kanger/src/org/kanger/units/Term.java",
    """    public ITerm getChild(Mind mind) {
        ITerm t = mind.getCvarChilds().get(this);
        if (t == null && mind.getNext() != null) {
            return getChild((Mind) mind.getNext());
        } else {
            return t;
        }
    }

    public void setChild(ITerm child) {
""",
    """    /**
     * Historical unscoped lookup. New inference code must select a child by
     * target Rule through {@link #getChild(Mind, long)}.
     */
    @Deprecated
    public ITerm getChild(Mind mind) {
        ITerm t = mind.getCvarChilds().get(this);
        if (t == null && mind.getNext() != null) {
            return getChild((Mind) mind.getNext());
        } else {
            return t;
        }
    }

    /** Return this C-variable's canonical projection for one target Rule. */
    public ITerm getChild(Mind mind, long targetRuleId) {
        return mind.getCVarChild(this, targetRuleId);
    }

    public void setChild(ITerm child) {
""",
)

# Linker: the receiving Domain's Rule is the binding scope of the projection.
replace_once(
    "kanger/src/org/kanger/Linker.java",
    """                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind), tm);
""",
    """                                                        Term tn = (Term) tm.getChild(mind, master.getRuleId());
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind), tm);
""",
)
replace_once(
    "kanger/src/org/kanger/Linker.java",
    """                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind), tm);
""",
    """                                                        Term tn = (Term) tm.getChild(mind, slave.getRuleId());
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind), tm);
""",
)

# Document the recovered invariant at the construction boundary.
replace_once(
    "kanger/src/org/kanger/factory/DictionaryFactory.java",
    """    public ITerm createCVar(IRule r, ITerm name, ITerm parent) throws Exception {
""",
    """    /**
     * Create a C-variable descriptor owned by {@code r}.
     *
     * <p>When {@code parent} is non-null, the new {@code *N} term is not a
     * concrete substitution. It is the canonical projection of the parent
     * C-variable into the independent binding scope of the target Rule. A
     * parent may therefore have several children, but at most one child for
     * each target Rule id. Reusing that rule-scoped identity prevents repeated
     * linker passes from producing an unbounded chain of equivalent variables,
     * while keeping different Rule-local sets of T-variables isolated.</p>
     *
     * <p>The child receives its target Rule before {@link Mind#linkCVar(ITerm,
     * ITerm)} publishes the transient adjacency. The adjacency belongs to the
     * active Mind lifecycle and is not persistent knowledge.</p>
     *
     * @param r target Rule whose T-variable set defines the binding scope
     * @param name source-level variable name retained for display
     * @param parent source C-variable, or {@code null} for a root descriptor
     * @return the newly allocated root or rule-scoped child descriptor
     */
    public ITerm createCVar(IRule r, ITerm name, ITerm parent) throws Exception {
""",
)

runner = r'''/*
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
'''
runner_path = Path("kanger-console/src/org/kanger/KangerCVarRuleScopeSafetyRunner.java")
if runner_path.exists():
    raise RuntimeError(f"{runner_path}: already exists")
runner_path.write_text(runner, encoding="utf-8")

replace_once(
    ".github/workflows/kanger-ci.yml",
    """      - name: Linker checkpoint balance invariant
        run: >-
          mvn --batch-mode --no-transfer-progress
          -Dexec.mainClass=org.kanger.KangerLinkerCheckpointBalanceSafetyRunner
          -Dexec.classpathScope=test
          exec:java
""",
    """      - name: C-variable Rule-scope invariant
        run: >-
          mvn --batch-mode --no-transfer-progress
          -Dexec.mainClass=org.kanger.KangerCVarRuleScopeSafetyRunner
          -Dexec.classpathScope=test
          exec:java

      - name: Linker checkpoint balance invariant
        run: >-
          mvn --batch-mode --no-transfer-progress
          -Dexec.mainClass=org.kanger.KangerLinkerCheckpointBalanceSafetyRunner
          -Dexec.classpathScope=test
          exec:java
""",
)

print("Patched rule-scoped C-variable projection")
