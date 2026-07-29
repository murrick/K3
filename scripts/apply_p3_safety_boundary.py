from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


def replace_exact(source: str, old: str, new: str, expected: int, label: str) -> str:
    count = source.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} matches, found {count}")
    return source.replace(old, new)


linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")

linker = replace_once(
    linker,
    "    private void addOppositeNatives(Set<IRule> ruleSet,\n"
    "                                    IRule source,\n"
    "                                    Set<DomainKey> expanded) throws Exception {\n",
    "    private void addOppositeNatives(Set<IRule> ruleSet,\n"
    "                                    IRule source,\n"
    "                                    Set<DomainKey> expanded,\n"
    "                                    boolean positional) throws Exception {\n",
    "Linker method signature",
)

linker = replace_once(
    linker,
    "                if (expanded.add(candidateKey)) {\n"
    "                    ruleSet.addAll(mind.getRules().findByDomain(\n"
    "                            domain, candidateKey.antc));\n"
    "                }\n",
    "                if (expanded.add(candidateKey)) {\n"
    "                    if (positional) {\n"
    "                        ruleSet.addAll(mind.getRules().findByDomain(\n"
    "                                domain, candidateKey.antc));\n"
    "                    } else {\n"
    "                        ruleSet.addAll(mind.getRules().findByDomain(\n"
    "                                candidateKey.predicateId, candidateKey.antc));\n"
    "                    }\n"
    "                }\n",
    "Linker candidate selection",
)

linker = replace_once(
    linker,
    "                addOppositeNatives(ruleSet, rule, expanded);\n",
    "                addOppositeNatives(ruleSet, rule, expanded, true);\n",
    "initial positional call",
)
linker = replace_exact(
    linker,
    "                            addOppositeNatives(ruleSet, r, expanded);\n",
    "                            addOppositeNatives(ruleSet, r, expanded, false);\n",
    2,
    "secondary predicate calls",
)
linker_path.write_text(linker, encoding="utf-8")

analyzer_path = Path("kanger/src/org/kanger/Analyzer.java")
analyzer = analyzer_path.read_text(encoding="utf-8")
analyzer = replace_once(
    analyzer,
    "            return mind.getRules().findByDomain(\n"
    "                    p.getDomain(), !p.getDomain().isAntc());\n",
    "            return mind.getRules().findByDomain(\n"
    "                    p.getDomain().getPredicateId(),\n"
    "                    !p.getDomain().isAntc());\n",
    "Analyzer predicate-only fallback",
)
analyzer_path.write_text(analyzer, encoding="utf-8")
