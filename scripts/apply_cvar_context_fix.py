from pathlib import Path
import re

# Temporary compile-before-commit patch for the diagnostic branch. The product
# change binds C-variable links to the active DictionaryFactory Mind instead of
# the stale Mind cached by a hydrated Term. This file is removed by finalization.
mind_path = Path("kanger/src/org/kanger/Mind.java")
mind = mind_path.read_text(encoding="utf-8")

replacement = '''    /** Bind C-variable links to an explicit active Mind context. */
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

    private void clearCVarLinks() {'''

pattern = r"    public void unlinkCVar\(ITerm term\) \{.*?    private void clearCVarLinks\(\) \{"
mind, count = re.subn(pattern, replacement, mind, count=1, flags=re.S)
if count != 1:
    if "public void linkCVar(ITerm parent, ITerm child)" not in mind:
        raise RuntimeError("Mind C-variable lifecycle block not found")
mind_path.write_text(mind, encoding="utf-8")

factory_path = Path("kanger/src/org/kanger/factory/DictionaryFactory.java")
factory = factory_path.read_text(encoding="utf-8")
old = "            childTerm.setParent(parent);\n            parentTerm.setChild(t);\n"
if old in factory:
    factory = factory.replace(old, "            mind.linkCVar(parent, t);\n", 1)
elif "mind.linkCVar(parent, t);" not in factory:
    raise RuntimeError("DictionaryFactory C-variable binding not found")
factory_path.write_text(factory, encoding="utf-8")
