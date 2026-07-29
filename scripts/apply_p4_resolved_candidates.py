from pathlib import Path


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return source.replace(old, new, 1)


candidate_path = Path("kanger/src/org/kanger/factory/RuleCandidateIndex.java")
candidate = candidate_path.read_text(encoding="utf-8")

candidate = replace_once(
    candidate,
    "package org.kanger.factory;\n\n"
    "import org.kanger.enums.ArgumentType;\n",
    "package org.kanger.factory;\n\n"
    "import org.kanger.Mind;\n"
    "import org.kanger.enums.ArgumentType;\n",
    "Mind import",
)

candidate = replace_once(
    candidate,
    "import org.kanger.units.Domain;\n"
    "import org.kanger.units.Rule;\n",
    "import org.kanger.units.Domain;\n"
    "import org.kanger.units.Rule;\n"
    "import org.kanger.units.TValue;\n"
    "import org.kanger.units.TVariable;\n",
    "TValue imports",
)

resolved_method = '''
    void collectResolvedLocal(Domain source,
                              boolean candidateAntc,
                              Mind mind,
                              LinkedHashSet<Long> result) throws Exception {
        SignatureKey signature = signature(source, candidateAntc);
        LinkedHashSet<Long> selected = signatures.get(signature);
        if (selected.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> fallback = fallbackSignatures.get(signature);

        for (int position = 0; position < source.getRange(); ++position) {
            IArgument argument = source.get(position);
            Long termId = resolvedTermId(argument, mind);
            if (termId == null) {
                continue;
            }

            LinkedHashSet<Long> compatible = positions.get(
                    new PositionKey(signature, position, termId));
            compatible.addAll(positions.get(
                    new PositionKey(signature, position, WILDCARD_TERM_ID)));
            compatible.addAll(fallback);
            selected.retainAll(compatible);
            if (selected.isEmpty()) {
                return;
            }
        }
        result.addAll(selected);
    }

    private Long resolvedTermId(IArgument argument, Mind mind) throws Exception {
        if (argument.getType() == ArgumentType.TERM) {
            return argument.getId();
        }
        if (argument.getType() == ArgumentType.TVARIABLE) {
            TVariable variable = (TVariable) argument.getObject(mind);
            TValue current = variable.getCurrent();
            return current == null ? null : current.getValueId();
        }
        return null;
    }

'''

candidate = replace_once(
    candidate,
    "    private SignatureKey signature(Domain domain, boolean antc) throws Exception {\n",
    resolved_method
    + "    private SignatureKey signature(Domain domain, boolean antc) throws Exception {\n",
    "resolved local lookup",
)
candidate_path.write_text(candidate, encoding="utf-8")

factory_path = Path("kanger/src/org/kanger/factory/RuleFactory.java")
factory = factory_path.read_text(encoding="utf-8")

factory_methods = '''
    private void collectResolvedCandidateIds(Domain source,
                                             boolean candidateAntc,
                                             LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectResolvedCandidateIds(source, candidateAntc, result);
        }
        ensureDomainIndex();
        candidateIndex.collectResolvedLocal(source, candidateAntc, mind, result);
    }

    /**
     * Resolve current query TValue assignments to term IDs before selecting
     * candidates. The returned Rules are still checked by normal unification.
     */
    public List<IRule> findByResolvedDomain(Domain source,
                                            boolean candidateAntc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectResolvedCandidateIds(source, candidateAntc, ids);
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

factory = replace_once(
    factory,
    "    public boolean hasActiveRuleWithTerm(long termId) throws Exception {\n",
    factory_methods
    + "    public boolean hasActiveRuleWithTerm(long termId) throws Exception {\n",
    "RuleFactory resolved lookup",
)
factory_path.write_text(factory, encoding="utf-8")

linker_path = Path("kanger/src/org/kanger/Linker.java")
linker = linker_path.read_text(encoding="utf-8")
old_select = '''    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
                                                       Map<DomainKey, List<IRule>> index) {
        if (tree.size() != 1) {
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        return candidates == null ? Collections.<IRule>emptyList() : candidates;
    }
'''
new_select = '''    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
                                                       Map<DomainKey, List<IRule>> index) throws Exception {
        if (tree.size() != 1) {
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<IRule> resolved = mind.getRules().findByResolvedDomain(
                slave, !slave.isAntc());
        if (resolved.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> allowedIds = new HashSet<>();
        for (IRule candidate : resolved) {
            allowedIds.add(candidate.getId());
        }
        List<IRule> filtered = new ArrayList<>();
        for (IRule candidate : candidates) {
            if (allowedIds.contains(candidate.getId())) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }
'''
linker = replace_once(linker, old_select, new_select, "Linker resolved candidates")
linker_path.write_text(linker, encoding="utf-8")
