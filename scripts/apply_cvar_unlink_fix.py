from pathlib import Path

path = Path("kanger/src/org/kanger/Mind.java")
source = path.read_text(encoding="utf-8")

old = '''    public void unlinkCVar(ITerm term) {
        if (term == null) {
            return;
        }

        Set<ITerm> linked = new HashSet<>();
        linked.add(term);

        ITerm child = cvarChilds.get(term);
        if (child != null) {
            linked.add(child);
        }
        ITerm parent = cvarParents.get(term);
        if (parent != null) {
            linked.add(parent);
        }
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

        cvarChilds.entrySet().removeIf(entry -> linked.contains(entry.getKey()) || linked.contains(entry.getValue()));
        cvarParents.entrySet().removeIf(entry -> linked.contains(entry.getKey()) || linked.contains(entry.getValue()));
    }
'''

new = '''    public void unlinkCVar(ITerm term) {
        if (term == null) {
            return;
        }

        // cvarChilds and cvarParents form the two directions of individual
        // parent-child edges. Unlink only edges incident to the deleted term.
        // In particular, deleting an obsolete child must not erase a newer
        // child that has already replaced it for the same parent.
        ITerm currentChild = cvarChilds.remove(term);
        if (currentChild != null && term.equals(cvarParents.get(currentChild))) {
            cvarParents.remove(currentChild);
        }

        ITerm currentParent = cvarParents.remove(term);
        if (currentParent != null && term.equals(cvarChilds.get(currentParent))) {
            cvarChilds.remove(currentParent);
        }

        // Remove stale reverse edges only when the deleted node itself is one
        // endpoint. Do not walk through neighbouring nodes and remove their
        // other, still-current edges.
        Iterator<Map.Entry<ITerm, ITerm>> childIterator = cvarChilds.entrySet().iterator();
        while (childIterator.hasNext()) {
            Map.Entry<ITerm, ITerm> edge = childIterator.next();
            if (term.equals(edge.getKey()) || term.equals(edge.getValue())) {
                ITerm parent = edge.getKey();
                ITerm child = edge.getValue();
                childIterator.remove();
                if (parent.equals(cvarParents.get(child))) {
                    cvarParents.remove(child);
                }
            }
        }

        Iterator<Map.Entry<ITerm, ITerm>> parentIterator = cvarParents.entrySet().iterator();
        while (parentIterator.hasNext()) {
            Map.Entry<ITerm, ITerm> edge = parentIterator.next();
            if (term.equals(edge.getKey()) || term.equals(edge.getValue())) {
                ITerm child = edge.getKey();
                ITerm parent = edge.getValue();
                parentIterator.remove();
                if (child.equals(cvarChilds.get(parent))) {
                    cvarChilds.remove(parent);
                }
            }
        }
    }
'''

if old not in source:
    if "Unlink only edges incident to the deleted term" in source:
        raise SystemExit(0)
    raise RuntimeError("Mind.unlinkCVar anchor not found")

path.write_text(source.replace(old, new, 1), encoding="utf-8")
