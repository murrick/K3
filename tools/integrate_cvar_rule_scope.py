from pathlib import Path


def replace_once(path, old, new):
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError("Expected exactly one anchor in %s, found %d" % (path, count))
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


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

        Set<ITerm> affectedParents = new HashSet<>();

        // If the removed term is a parent, drop every Rule-scoped projection.
        Map<Long, ITerm> ownedChildren = cvarChildrenByRule.remove(term);
        if (ownedChildren != null) {
            for (ITerm child : ownedChildren.values()) {
                cvarParents.remove(child);
            }
        }

        // If the removed term is a child, drop only its projection and retain
        // siblings belonging to other target Rules. Scan the authority map as
        // well as the reverse map so damaged one-sided adjacency is repairable.
        ITerm reverseParent = cvarParents.remove(term);
        if (reverseParent != null) {
            affectedParents.add(reverseParent);
        }
        Iterator<Map.Entry<ITerm, Map<Long, ITerm>>> parentIterator =
                cvarChildrenByRule.entrySet().iterator();
        while (parentIterator.hasNext()) {
            Map.Entry<ITerm, Map<Long, ITerm>> parentEntry = parentIterator.next();
            Iterator<Map.Entry<Long, ITerm>> childIterator =
                    parentEntry.getValue().entrySet().iterator();
            boolean removed = false;
            while (childIterator.hasNext()) {
                if (term.equals(childIterator.next().getValue())) {
                    childIterator.remove();
                    removed = true;
                }
            }
            if (removed) {
                affectedParents.add(parentEntry.getKey());
            }
            if (parentEntry.getValue().isEmpty()) {
                parentIterator.remove();
            }
        }

        // B7.1 deliberately exercises legacy and damaged one-sided links.
        // Scrub every direct edge involving the removed term even when that
        // edge was never published into cvarChildrenByRule.
        for (Map.Entry<ITerm, ITerm> entry : cvarChilds.entrySet()) {
            if (term.equals(entry.getValue())) {
                affectedParents.add(entry.getKey());
            }
        }
        cvarChilds.entrySet().removeIf(entry ->
                term.equals(entry.getKey()) || term.equals(entry.getValue()));
        cvarParents.entrySet().removeIf(entry ->
                term.equals(entry.getKey()) || term.equals(entry.getValue()));

        // The legacy one-child view is non-authoritative, but keep it coherent
        // for callers that still inspect it: select any surviving Rule child.
        for (ITerm parent : affectedParents) {
            cvarChilds.remove(parent);
            Map<Long, ITerm> siblings = cvarChildrenByRule.get(parent);
            if (siblings != null && !siblings.isEmpty()) {
                cvarChilds.put(parent, siblings.values().iterator().next());
            }
        }
    }

    private void clearCVarLinks() {
        cvarChilds.clear();
        cvarChildrenByRule.clear();
        cvarParents.clear();
    }
""",
)

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

replace_once(
    "kanger/src/org/kanger/Linker.java",
    "Term tn = (Term) tm.getChild(mind);",
    "Term tn = (Term) tm.getChild(mind, master.getRuleId());",
)
replace_once(
    "kanger/src/org/kanger/Linker.java",
    "Term tn = (Term) tm.getChild(mind);",
    "Term tn = (Term) tm.getChild(mind, slave.getRuleId());",
)

replace_once(
    ".github/workflows/kanger-ci.yml",
    """      - name: Linker checkpoint balance invariant
""",
    """      - name: C-variable Rule-scope invariant
        run: >-
          mvn --batch-mode --no-transfer-progress
          -Dexec.mainClass=org.kanger.KangerCVarRuleScopeSafetyRunner
          -Dexec.classpathScope=test
          exec:java

      - name: Linker checkpoint balance invariant
""",
)

print("C-variable Rule-scope integration patch applied")
