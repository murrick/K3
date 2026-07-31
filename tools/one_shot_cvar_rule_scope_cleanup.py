from pathlib import Path

path = Path("kanger/src/org/kanger/Mind.java")
source = path.read_text(encoding="utf-8")
old = """    public void unlinkCVar(ITerm term) {
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
"""
new = """    public void unlinkCVar(ITerm term) {
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
"""
count = source.count(old)
if count != 1:
    raise RuntimeError(f"Mind.java: expected one generated unlink block, found {count}")
path.write_text(source.replace(old, new, 1), encoding="utf-8")
print("Hardened rule-scoped C-variable unlink lifecycle")
