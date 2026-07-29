from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")

linker = replace_once(
    linker,
    "    private int skippedPasses = 0;\n",
    "    private int skippedPasses = 0;\n"
    "    private final LinkerStatistics statistics = new LinkerStatistics();\n",
    "statistics field",
)

linker = replace_once(
    linker,
    "    public Linker(Mind mind) {\n"
    "        this.mind = mind;\n"
    "        this.log = mind.getLog();\n"
    "    }\n",
    "    public Linker(Mind mind) {\n"
    "        this.mind = mind;\n"
    "        this.log = mind.getLog();\n"
    "    }\n\n"
    "    public LinkerStatistics snapshotStatistics() {\n"
    "        return statistics.snapshot();\n"
    "    }\n",
    "statistics snapshot",
)

linker = replace_once(
    linker,
    "        skippedPasses = 0;\n\n"
    "        final Map<IRule, Set<Cause>> causes = new HashMap<>();\n",
    "        skippedPasses = 0;\n"
    "        statistics.reset();\n\n"
    "        final Map<IRule, Set<Cause>> causes = new HashMap<>();\n",
    "statistics reset",
)

linker = replace_once(
    linker,
    "        do {\n\n"
    "            if (logging) {\n"
    "                log.add(LogMode.ANALYZER, String.format(\"---------- LINKER PASS %03d ---------------\", ++passCounter));\n"
    "            }\n",
    "        do {\n\n"
    "            ++passCounter;\n"
    "            statistics.incrementPasses();\n"
    "            if (logging) {\n"
    "                log.add(LogMode.ANALYZER, String.format(\"---------- LINKER PASS %03d ---------------\", passCounter));\n"
    "            }\n",
    "pass counter",
)

linker = replace_once(
    linker,
    "        for (IRule r : ruleList) {\n\n"
    "            mind.getProducedDomains().clear();\n",
    "        for (IRule r : ruleList) {\n\n"
    "            statistics.incrementRuleVisits();\n"
    "            mind.getProducedDomains().clear();\n",
    "rule visits",
)

linker = replace_once(
    linker,
    "            for (List<Domain> tree : ((Rule) r).getTree()) {\n\n"
    "                final List<Domain> t = tree;\n",
    "            for (List<Domain> tree : ((Rule) r).getTree()) {\n\n"
    "                statistics.incrementBranchVisits();\n"
    "                final List<Domain> t = tree;\n",
    "branch visits",
)

linker = replace_once(
    linker,
    "                    public Object run(Object o) {\n"
    "                        boolean result = false;\n"
    "                        try {\n",
    "                    public Object run(Object o) {\n"
    "                        statistics.incrementTerminalRotations();\n"
    "                        boolean result = false;\n"
    "                        try {\n",
    "terminal rotations",
)

linker = replace_once(
    linker,
    "                            if (calcFunctions(t, causes, logging)) {\n",
    "                            statistics.incrementFunctionEvaluations();\n"
    "                            if (calcFunctions(t, causes, logging)) {\n",
    "function evaluations",
)

linker = replace_once(
    linker,
    "                            if (linkDatabase(t, causes, tvars, logging)) {\n",
    "                            statistics.incrementDatabaseEvaluations();\n"
    "                            if (linkDatabase(t, causes, tvars, logging)) {\n",
    "database evaluations",
)

linker = replace_once(
    linker,
    "                for (IRule rule : ruleList) {\n"
    "                    for (List<Domain> treeMaster : ((Rule) rule).getTree()) {\n",
    "                for (IRule rule : ruleList) {\n"
    "                    statistics.incrementCandidateRuleVisits();\n"
    "                    for (List<Domain> treeMaster : ((Rule) rule).getTree()) {\n",
    "candidate rule visits",
)

linker = replace_once(
    linker,
    "                        for (Domain master : treeMaster) {\n"
    "                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {\n",
    "                        for (Domain master : treeMaster) {\n"
    "                            statistics.incrementDomainPairs();\n"
    "                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {\n"
    "                                statistics.incrementUnificationAttempts();\n",
    "domain pair metrics",
)

linker = replace_once(
    linker,
    "                                                    if (s == null) {\n"
    "                                                        s = mind.getTValues().add(t, tm);\n"
    "                                                        result = true;\n"
    "                                                    }\n"
    "                                                    substMaster[i] = s;\n",
    "                                                    if (s == null) {\n"
    "                                                        s = mind.getTValues().add(t, tm);\n"
    "                                                        statistics.incrementNewTValues();\n"
    "                                                        result = true;\n"
    "                                                    }\n"
    "                                                    substMaster[i] = s;\n",
    "master TValue metrics",
)

linker = replace_once(
    linker,
    "                                                    if (s == null) {\n"
    "                                                        s = mind.getTValues().add(t, tm);\n"
    "                                                        result = true;\n"
    "                                                    }\n\n"
    "                                                    substSlave[i] = s;\n",
    "                                                    if (s == null) {\n"
    "                                                        s = mind.getTValues().add(t, tm);\n"
    "                                                        statistics.incrementNewTValues();\n"
    "                                                        result = true;\n"
    "                                                    }\n\n"
    "                                                    substSlave[i] = s;\n",
    "slave TValue metrics",
)

linker_path.write_text(linker, encoding="utf-8")

mind_path = Path("kanger/src/org/kanger/Mind.java")
mind = mind_path.read_text(encoding="utf-8")
mind = replace_once(
    mind,
    "    public Calculator getCalculator() {\n"
    "        return calculator;\n"
    "    }\n",
    "    public Calculator getCalculator() {\n"
    "        return calculator;\n"
    "    }\n\n"
    "    public LinkerStatistics getLinkerStatistics() {\n"
    "        return linker.snapshotStatistics();\n"
    "    }\n",
    "Mind statistics getter",
)
mind_path.write_text(mind, encoding="utf-8")
