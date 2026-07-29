from pathlib import Path
import re


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


# Remove temporary Linker progress tracing.
path = Path("kanger/src/org/kanger/Linker.java")
source = path.read_text(encoding="utf-8")
source = replace_once(
    source,
    '''        do {

            ++passCounter;
            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", passCounter));
            }
''',
    '''        do {

            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }
''',
    "Linker pass tracing",
)
source, count = re.subn(
    r'''\n            if \(Boolean\.parseBoolean\(System\.getProperty\("kanger\.diagnostics", "false"\)\)\n                    && \(passCounter <= 10 \|\| passCounter % 100 == 0\)\) \{\n                System\.err\.println\("\[KANGER-LINKER\].*?\n            \}\n''',
    "\n",
    source,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("Linker diagnostic block not found")
path.write_text(source, encoding="utf-8")


# Remove temporary TValue identity tracing.
path = Path("kanger/src/org/kanger/factory/TValueFactory.java")
source = path.read_text(encoding="utf-8")
source = replace_once(source, "import org.kanger.units.Term;\n", "", "TValue Term import")
source = replace_once(source, "    private long diagnosticAddCount = 0L;\n", "", "TValue diagnostic field")
source = replace_once(source, "        diagnosticAddCount = 0L;\n", "", "TValue diagnostic reset")
source, count = re.subn(
    r'''            \+\+diagnosticAddCount;\n            if \(Boolean\.parseBoolean\(System\.getProperty\("kanger\.diagnostics", "false"\)\).*?                System\.err\.println\(diagnostic\.toString\(\)\);\n            \}\n''',
    "",
    source,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("TValue diagnostic block not found")
path.write_text(source, encoding="utf-8")


# Keep the active-context fix but remove temporary ownership tracing.
path = Path("kanger/src/org/kanger/factory/DictionaryFactory.java")
source = path.read_text(encoding="utf-8")
source = replace_once(source, "    private long diagnosticCVarCount = 0L;\n", "", "Dictionary diagnostic field")
source, count = re.subn(
    r'''        if \(parent != null\) \{\n            \+\+diagnosticCVarCount;.*?            \}\n        \}\n        return t;''',
    '''        if (parent != null) {
            mind.linkCVar(parent, t);
        }
        return t;''',
    source,
    count=1,
    flags=re.S,
)
if count != 1:
    raise RuntimeError("Dictionary ownership diagnostics not found")
path.write_text(source, encoding="utf-8")


# Make the direct persistent regression reject renewed TValue runaway.
path = Path("kanger-console/src/org/kanger/KangerDiagnosticRunner.java")
source = path.read_text(encoding="utf-8")
source = replace_once(
    source,
    '''        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after query"));
        mind.closeStorage();
        System.exit(Boolean.FALSE.equals(result) ? 0 : 1);
''',
    '''        System.out.println(Diagnostics.snapshot(mind, "set_03_01 after query"));
        int tvalueCount = ((Mind) mind).getTValues().size();
        mind.closeStorage();
        if (tvalueCount > 4) {
            throw new AssertionError("set_03_01 produced runaway TValue state: " + tvalueCount);
        }
        System.exit(Boolean.FALSE.equals(result) ? 0 : 1);
''',
    "persistent quantified regression bound",
)
path.write_text(source, encoding="utf-8")
