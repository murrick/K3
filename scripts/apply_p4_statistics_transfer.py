from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


path = Path("kanger/src/org/kanger/Mind.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "    private Linker linker = null;                                         // Линкер\n",
    "    private Linker linker = null;                                         // Линкер\n"
    "    private LinkerStatistics lastLinkerStatistics = new LinkerStatistics();\n",
    "statistics field",
)

source = replace_once(
    source,
    "            queryResult = m.getQueryResult();\n"
    "            compliedLine = m.getCompliedString();\n",
    "            queryResult = m.getQueryResult();\n"
    "            compliedLine = m.getCompliedString();\n"
    "            lastLinkerStatistics = ((Mind) m).linker.snapshotStatistics();\n",
    "commit transfer",
)

source = replace_once(
    source,
    "            queryResult = m.getQueryResult();\n"
    "            compliedLine = m.getCompliedString();\n\n"
    "            --transactionCounter;\n",
    "            queryResult = m.getQueryResult();\n"
    "            compliedLine = m.getCompliedString();\n"
    "            lastLinkerStatistics = ((Mind) m).linker.snapshotStatistics();\n\n"
    "            --transactionCounter;\n",
    "release transfer",
)

source = replace_once(
    source,
    "    public LinkerStatistics getLinkerStatistics() {\n"
    "        return linker.snapshotStatistics();\n"
    "    }\n",
    "    public LinkerStatistics getLinkerStatistics() {\n"
    "        return lastLinkerStatistics;\n"
    "    }\n",
    "statistics getter",
)

path.write_text(source, encoding="utf-8")
