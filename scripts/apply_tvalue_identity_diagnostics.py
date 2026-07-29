from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


path = Path("kanger/src/org/kanger/factory/TValueFactory.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "import org.kanger.units.TValue;\nimport org.kanger.units.TVariable;\n",
    "import org.kanger.units.Term;\nimport org.kanger.units.TValue;\nimport org.kanger.units.TVariable;\n",
    "Term import",
)

source = replace_once(
    source,
    "    private boolean action = false;\n",
    "    private boolean action = false;\n"
    "    private long diagnosticAddCount = 0L;\n",
    "diagnostic counter",
)

source = replace_once(
    source,
    "        additionsStack.clear();\n        indexInitialized = base != null;\n",
    "        additionsStack.clear();\n"
    "        indexInitialized = base != null;\n"
    "        diagnosticAddCount = 0L;\n",
    "diagnostic reset",
)

old = '''        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(((User) mind.getUser()).nextId(SCHEMA));
'''
new = '''        if (t == null) {
            ++diagnosticAddCount;
            if (Boolean.parseBoolean(System.getProperty("kanger.diagnostics", "false"))
                    && (diagnosticAddCount <= 20L || diagnosticAddCount % 100L == 0L)) {
                StringBuilder diagnostic = new StringBuilder();
                diagnostic.append("[KANGER-TVALUE] add#").append(diagnosticAddCount)
                        .append(" mind=").append(mind.getId())
                        .append(" tvar=").append(tv.getId())
                        .append(" tvarIndex=").append(tv.getIndex())
                        .append(" term=").append(o.getId())
                        .append(" termType=").append(o.getType())
                        .append(" cvar=").append(o.isCVariable());
                try {
                    diagnostic.append(" tvarRule=")
                            .append(tv.getRule(mind) == null ? -1L : tv.getRule(mind).getId());
                } catch (Exception error) {
                    diagnostic.append(" tvarRule=<error>");
                }
                if (o instanceof Term) {
                    Term term = (Term) o;
                    diagnostic.append(" termIndex=").append(term.getIndex())
                            .append(" termRule=").append(term.getRuleId())
                            .append(" parent=").append(term.getParentId(mind))
                            .append(" child=")
                            .append(term.getChild(mind) == null ? -1L : term.getChild(mind).getId())
                            .append(" domini=").append(term.isDomini());
                }
                TValue probe = new TValue(tv, o);
                diagnostic.append(" hash=").append(probe.getHash())
                        .append(" hashCandidates=").append(cache.find(probe.getHash()).size());
                System.err.println(diagnostic.toString());
            }
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(((User) mind.getUser()).nextId(SCHEMA));
'''
source = replace_once(source, old, new, "TValue identity diagnostics")
path.write_text(source, encoding="utf-8")
