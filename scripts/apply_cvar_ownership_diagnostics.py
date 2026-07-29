from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


path = Path("kanger/src/org/kanger/factory/DictionaryFactory.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "    private int varIndex = 0;           // Счетчик C-переменных\n",
    "    private int varIndex = 0;           // Счетчик C-переменных\n"
    "    private long diagnosticCVarCount = 0L;\n",
    "Dictionary diagnostic field",
)

old = '''        if (parent != null) {
            ((Term) t).setParent(parent);
            ((Term) parent).setChild(t);
        }
        return t;
'''
new = '''        if (parent != null) {
            ++diagnosticCVarCount;
            Term childTerm = (Term) t;
            Term parentTerm = (Term) parent;
            Mind parentMind = parentTerm.getMind();
            Mind childMind = childTerm.getMind();
            ITerm childBeforeFactory = parentTerm.getChild(mind);
            ITerm childBeforeOwner = parentMind == null ? null : parentTerm.getChild(parentMind);

            childTerm.setParent(parent);
            parentTerm.setChild(t);

            if (Boolean.parseBoolean(System.getProperty("kanger.diagnostics", "false"))
                    && (diagnosticCVarCount <= 20L || diagnosticCVarCount % 100L == 0L)) {
                ITerm childAfterFactory = parentTerm.getChild(mind);
                ITerm childAfterOwner = parentMind == null ? null : parentTerm.getChild(parentMind);
                System.err.println("[KANGER-CVAR] create#" + diagnosticCVarCount
                        + " factoryMind=" + mind.getId()
                        + " parent=" + parent.getId()
                        + " parentMind=" + (parentMind == null ? -1L : parentMind.getId())
                        + " child=" + t.getId()
                        + " childMind=" + (childMind == null ? -1L : childMind.getId())
                        + " beforeFactory=" + (childBeforeFactory == null ? -1L : childBeforeFactory.getId())
                        + " beforeOwner=" + (childBeforeOwner == null ? -1L : childBeforeOwner.getId())
                        + " afterFactory=" + (childAfterFactory == null ? -1L : childAfterFactory.getId())
                        + " afterOwner=" + (childAfterOwner == null ? -1L : childAfterOwner.getId())
                        + " factoryLinks=" + mind.getCvarChilds().size()
                        + " ownerLinks=" + (parentMind == null ? -1 : parentMind.getCvarChilds().size()));
            }
        }
        return t;
'''
source = replace_once(source, old, new, "C-variable ownership diagnostics")
path.write_text(source, encoding="utf-8")
