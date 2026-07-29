from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


rule_path = Path("kanger/src/org/kanger/factory/RuleFactory.java")
rule = rule_path.read_text(encoding="utf-8")

rule = replace_once(
    rule,
    "    private boolean domainIndexInitialized = false;\n",
    "    private boolean domainIndexInitialized = false;\n"
    "    private final RuleCandidateIndex candidateIndex = new RuleCandidateIndex();\n",
    "candidate field",
)

rule = replace_once(
    rule,
    "        localDomainIndex.clear();\n"
    "        localTermIndex.clear();\n"
    "        domainIndexStack.clear();\n",
    "        localDomainIndex.clear();\n"
    "        localTermIndex.clear();\n"
    "        candidateIndex.clear();\n"
    "        domainIndexStack.clear();\n",
    "transaction clear",
)

rule = replace_once(
    rule,
    "        localDomainIndex.clear();\n"
    "        localTermIndex.clear();\n"
    "        for (Object value : cache) {\n",
    "        localDomainIndex.clear();\n"
    "        localTermIndex.clear();\n"
    "        candidateIndex.clear();\n"
    "        for (Object value : cache) {\n",
    "root rebuild clear",
)

rule = replace_once(
    rule,
    "        for (long termId : rule.getTerms()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(termId);\n"
    "            if (ids == null) {\n"
    "                ids = new LinkedHashSet<>();\n"
    "                localTermIndex.put(termId, ids);\n"
    "            }\n"
    "            ids.add(rule.getId());\n"
    "        }\n"
    "    }\n\n"
    "    private void unindexRule",
    "        for (long termId : rule.getTerms()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(termId);\n"
    "            if (ids == null) {\n"
    "                ids = new LinkedHashSet<>();\n"
    "                localTermIndex.put(termId, ids);\n"
    "            }\n"
    "            ids.add(rule.getId());\n"
    "        }\n"
    "        candidateIndex.indexRule(rule);\n"
    "    }\n\n"
    "    private void unindexRule",
    "index rule",
)

rule = replace_once(
    rule,
    "        for (long termId : rule.getTerms()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(termId);\n"
    "            if (ids != null) {\n"
    "                ids.remove(rule.getId());\n"
    "                if (ids.isEmpty()) {\n"
    "                    localTermIndex.remove(termId);\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private void collectDomainIds",
    "        for (long termId : rule.getTerms()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(termId);\n"
    "            if (ids != null) {\n"
    "                ids.remove(rule.getId());\n"
    "                if (ids.isEmpty()) {\n"
    "                    localTermIndex.remove(termId);\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        candidateIndex.unindexRule(rule);\n"
    "    }\n\n"
    "    private void collectDomainIds",
    "unindex rule",
)

rule = replace_once(
    rule,
    "        for (Map.Entry<Long, LinkedHashSet<Long>> entry : child.localTermIndex.entrySet()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(entry.getKey());\n"
    "            if (ids == null) {\n"
    "                ids = new LinkedHashSet<>();\n"
    "                localTermIndex.put(entry.getKey(), ids);\n"
    "            }\n"
    "            ids.addAll(entry.getValue());\n"
    "        }\n"
    "    }\n\n"
    "    public List<IRule> findByDomain",
    "        for (Map.Entry<Long, LinkedHashSet<Long>> entry : child.localTermIndex.entrySet()) {\n"
    "            LinkedHashSet<Long> ids = localTermIndex.get(entry.getKey());\n"
    "            if (ids == null) {\n"
    "                ids = new LinkedHashSet<>();\n"
    "                localTermIndex.put(entry.getKey(), ids);\n"
    "            }\n"
    "            ids.addAll(entry.getValue());\n"
    "        }\n"
    "        candidateIndex.mergeFrom(child.candidateIndex);\n"
    "    }\n\n"
    "    public List<IRule> findByDomain",
    "merge candidate index",
)

candidate_methods = '''
    private void collectCandidateIds(Domain source,
                                     boolean candidateAntc,
                                     LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectCandidateIds(source, candidateAntc, result);
        }
        ensureDomainIndex();
        candidateIndex.collectLocal(source, candidateAntc, result);
    }

    /**
     * Select by predicate/polarity/arity and direct TERM positions before any
     * Rule is hydrated. Dynamic candidate arguments are indexed as wildcards.
     */
    public List<IRule> findByDomain(Domain source, boolean candidateAntc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectCandidateIds(source, candidateAntc, ids);
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            IRule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                result.add(rule);
            }
        }
        return result;
    }

'''
rule = replace_once(
    rule,
    "    public boolean hasActiveRuleWithTerm(long termId) throws Exception {\n",
    candidate_methods + "    public boolean hasActiveRuleWithTerm(long termId) throws Exception {\n",
    "candidate lookup methods",
)

rule = replace_once(
    rule,
    "        termIndexStack.push(copyTermIndex());\n"
    "        promotionStack.push(new HashSet<>(primaryPromotions));\n",
    "        termIndexStack.push(copyTermIndex());\n"
    "        candidateIndex.mark();\n"
    "        promotionStack.push(new HashSet<>(primaryPromotions));\n",
    "candidate mark",
)

rule = replace_once(
    rule,
    "        if (!termIndexStack.isEmpty()) {\n"
    "            termIndexStack.pop();\n"
    "        }\n"
    "        if (!promotionStack.isEmpty()) {\n",
    "        if (!termIndexStack.isEmpty()) {\n"
    "            termIndexStack.pop();\n"
    "        }\n"
    "        candidateIndex.commit();\n"
    "        if (!promotionStack.isEmpty()) {\n",
    "candidate commit",
)

rule = replace_once(
    rule,
    "        if (!termIndexStack.isEmpty()) {\n"
    "            localTermIndex.clear();\n"
    "            localTermIndex.putAll(termIndexStack.pop());\n"
    "            domainIndexInitialized = true;\n"
    "        }\n"
    "        if (!promotionStack.isEmpty()) {\n",
    "        if (!termIndexStack.isEmpty()) {\n"
    "            localTermIndex.clear();\n"
    "            localTermIndex.putAll(termIndexStack.pop());\n"
    "            domainIndexInitialized = true;\n"
    "        }\n"
    "        candidateIndex.release();\n"
    "        if (!promotionStack.isEmpty()) {\n",
    "candidate release",
)

rule_path.write_text(rule, encoding="utf-8")

linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")
linker = replace_once(
    linker,
    "                    ruleSet.addAll(mind.getRules().findByDomain(\n"
    "                            candidateKey.predicateId, candidateKey.antc));\n",
    "                    ruleSet.addAll(mind.getRules().findByDomain(\n"
    "                            domain, candidateKey.antc));\n",
    "Linker positional selection",
)
linker_path.write_text(linker, encoding="utf-8")

analyzer_path = Path("kanger/src/org/kanger/Analyzer.java")
analyzer = analyzer_path.read_text(encoding="utf-8")
analyzer = replace_once(
    analyzer,
    "            return mind.getRules().findByDomain(\n"
    "                    p.getDomain().getPredicateId(),\n"
    "                    !p.getDomain().isAntc());\n",
    "            return mind.getRules().findByDomain(\n"
    "                    p.getDomain(), !p.getDomain().isAntc());\n",
    "Analyzer positional selection",
)
analyzer_path.write_text(analyzer, encoding="utf-8")
