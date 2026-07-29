from pathlib import Path


def replace_once(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} match, found {count}")
    return source.replace(old, new, 1)


rule_path = Path("kanger/src/org/kanger/factory/RuleFactory.java")
rule_source = rule_path.read_text(encoding="utf-8")

old_fields = """    private final Mind mind;
    private boolean action = false;

    /**
     * Transaction-local overrides that make an already canonical generated
"""
new_fields = """    private final Mind mind;
    private boolean action = false;

    private static final class DomainKey {
        private final long predicateId;
        private final boolean antc;

        private DomainKey(long predicateId, boolean antc) {
            this.predicateId = predicateId;
            this.antc = antc;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof DomainKey)) {
                return false;
            }
            DomainKey other = (DomainKey) value;
            return predicateId == other.predicateId && antc == other.antc;
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(predicateId).hashCode();
            return 31 * result + (antc ? 1 : 0);
        }
    }

    /**
     * A transaction-layered metadata index. It stores Rule IDs only; semantic
     * objects remain owned by Escalera/IBase and are hydrated through get(id).
     */
    private RuleFactory parentIndex = null;
    private final Map<DomainKey, LinkedHashSet<Long>> localDomainIndex = new HashMap<>();
    private final Stack<Map<DomainKey, LinkedHashSet<Long>>> domainIndexStack = new Stack<>();
    private boolean domainIndexInitialized = false;

    /**
     * Transaction-local overrides that make an already canonical generated
"""
rule_source = replace_once(rule_source, old_fields, new_fields, "RuleFactory fields")

old_transaction_tail = """        primaryPromotions.clear();
        promotionViews.clear();
        promotionStack.clear();
        appliedPromotions.clear();
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
"""
new_transaction_tail = """        parentIndex = base;
        localDomainIndex.clear();
        domainIndexStack.clear();
        domainIndexInitialized = base != null;

        primaryPromotions.clear();
        promotionViews.clear();
        promotionStack.clear();
        appliedPromotions.clear();
    }

    private Map<DomainKey, LinkedHashSet<Long>> copyDomainIndex() {
        Map<DomainKey, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : localDomainIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private void ensureDomainIndex() throws Exception {
        if (domainIndexInitialized) {
            return;
        }
        localDomainIndex.clear();
        for (Object value : cache) {
            indexRule((Rule) value);
        }
        domainIndexInitialized = true;
    }

    private void indexRule(Rule rule) throws Exception {
        if (rule == null) {
            return;
        }
        Set<DomainKey> indexed = new HashSet<>();
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                if (indexed.add(key)) {
                    LinkedHashSet<Long> ids = localDomainIndex.get(key);
                    if (ids == null) {
                        ids = new LinkedHashSet<>();
                        localDomainIndex.put(key, ids);
                    }
                    ids.add(rule.getId());
                }
            }
        }
    }

    private void unindexRule(Rule rule) throws Exception {
        if (rule == null || !domainIndexInitialized) {
            return;
        }
        Set<DomainKey> indexed = new HashSet<>();
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                if (indexed.add(key)) {
                    LinkedHashSet<Long> ids = localDomainIndex.get(key);
                    if (ids != null) {
                        ids.remove(rule.getId());
                        if (ids.isEmpty()) {
                            localDomainIndex.remove(key);
                        }
                    }
                }
            }
        }
    }

    private void collectDomainIds(DomainKey key, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectDomainIds(key, result);
        }
        ensureDomainIndex();
        LinkedHashSet<Long> local = localDomainIndex.get(key);
        if (local != null) {
            result.addAll(local);
        }
    }

    private void mergeDomainIndex(RuleFactory child) throws Exception {
        ensureDomainIndex();
        child.ensureDomainIndex();
        for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : child.localDomainIndex.entrySet()) {
            LinkedHashSet<Long> ids = localDomainIndex.get(entry.getKey());
            if (ids == null) {
                ids = new LinkedHashSet<>();
                localDomainIndex.put(entry.getKey(), ids);
            }
            ids.addAll(entry.getValue());
        }
    }

    public List<IRule> findByDomain(long predicateId, boolean antc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectDomainIds(new DomainKey(predicateId, antc), ids);
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            IRule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                result.add(rule);
            }
        }
        return result;
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
"""
rule_source = replace_once(rule_source, old_transaction_tail, new_transaction_tail, "RuleFactory transaction tail")

old_commit_tail = """        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
                list.add(((IUnit) s).getId());
            }
        }
        action = action || base.isAction();
        return list;
"""
new_commit_tail = """        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
                list.add(((IUnit) s).getId());
            }
        }
        mergeDomainIndex(base);
        action = action || base.isAction();
        return list;
"""
rule_source = replace_once(rule_source, old_commit_tail, new_commit_tail, "RuleFactory commit tail")

old_add_tail = """            for (List<Domain> list : ((Rule) r).getTree()) {
                for (Domain d : list) {
                    ((Rule) r).getTerms().addAll(d.getTerms(mind, true));
                    ((Rule) r).getPredicates().add(d.getPredicateId());
                    d.setRule(r);
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        t.setRule(r);
                    }
                }
            }
            action = true;
"""
new_add_tail = """            for (List<Domain> list : ((Rule) r).getTree()) {
                for (Domain d : list) {
                    ((Rule) r).getTerms().addAll(d.getTerms(mind, true));
                    ((Rule) r).getPredicates().add(d.getPredicateId());
                    d.setRule(r);
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        t.setRule(r);
                    }
                }
            }
            indexRule((Rule) r);
            action = true;
"""
rule_source = replace_once(rule_source, old_add_tail, new_add_tail, "RuleFactory add tail")

old_lifecycle = """    public void mark() throws Exception {
        cache.mark();
        promotionStack.push(new HashSet<>(primaryPromotions));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!promotionStack.isEmpty()) {
            promotionStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!promotionStack.isEmpty()) {
            primaryPromotions.clear();
            primaryPromotions.addAll(promotionStack.pop());
            promotionViews.clear();
        }
    }
"""
new_lifecycle = """    public void mark() throws Exception {
        cache.mark();
        ensureDomainIndex();
        domainIndexStack.push(copyDomainIndex());
        promotionStack.push(new HashSet<>(primaryPromotions));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!domainIndexStack.isEmpty()) {
            domainIndexStack.pop();
        }
        if (!promotionStack.isEmpty()) {
            promotionStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!domainIndexStack.isEmpty()) {
            localDomainIndex.clear();
            localDomainIndex.putAll(domainIndexStack.pop());
            domainIndexInitialized = true;
        }
        if (!promotionStack.isEmpty()) {
            primaryPromotions.clear();
            primaryPromotions.addAll(promotionStack.pop());
            promotionViews.clear();
        }
    }
"""
rule_source = replace_once(rule_source, old_lifecycle, new_lifecycle, "RuleFactory lifecycle")

old_pack_delete = """        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
"""
new_pack_delete = """        for (Object o : toDelete) {
            unindexRule((Rule) o);
            cache.delete(((IUnit) o).getId());
        }
"""
rule_source = replace_once(rule_source, old_pack_delete, new_pack_delete, "RuleFactory pack delete")

rule_path.write_text(rule_source, encoding="utf-8")


linker_path = Path("kanger/src/org/kanger/Linker.java")
linker_source = linker_path.read_text(encoding="utf-8")

old_helper_tail = """    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
                                                      Map<DomainKey, List<IRule>> index) {
        if (tree.size() != 1) {
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        return candidates == null ? Collections.<IRule>emptyList() : candidates;
    }

    public void link(Rule rule, boolean logging) throws Exception {
"""
new_helper_tail = """    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
                                                      Map<DomainKey, List<IRule>> index) {
        if (tree.size() != 1) {
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        return candidates == null ? Collections.<IRule>emptyList() : candidates;
    }

    private void addOppositeNatives(Set<IRule> ruleSet,
                                    IRule source,
                                    Set<DomainKey> expanded) throws Exception {
        for (List<Domain> branch : ((Rule) source).getTree()) {
            for (Domain domain : branch) {
                DomainKey candidateKey = new DomainKey(
                        domain.getPredicateId(), !domain.isAntc());
                if (expanded.add(candidateKey)) {
                    ruleSet.addAll(mind.getRules().findByDomain(
                            candidateKey.predicateId, candidateKey.antc));
                }
            }
        }
    }

    public void link(Rule rule, boolean logging) throws Exception {
"""
linker_source = replace_once(linker_source, old_helper_tail, new_helper_tail, "Linker helper tail")

old_ruleset = """            if (rule != null) {
                ruleSet.add(rule);
                ruleSet.addAll(rule.getNatives());
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        if (((Rule) r).isUsed(mind)) {
                            ruleSet.add(r);
                            ruleSet.addAll(((Rule) r).getNatives());
                        } else if (r.isGenerated() && r.getId() > topId) {
                            ruleSet.add(r);
                            ruleSet.addAll(((Rule) r).getNatives());
                        }
                    }
                }
            } else {
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        ruleSet.add(r);
                    }
                }
            }
"""
new_ruleset = """            if (rule != null) {
                Set<DomainKey> expanded = new HashSet<>();
                ruleSet.add(rule);
                addOppositeNatives(ruleSet, rule, expanded);
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        if (((Rule) r).isUsed(mind)) {
                            ruleSet.add(r);
                            addOppositeNatives(ruleSet, r, expanded);
                        } else if (r.isGenerated() && r.getId() > topId) {
                            ruleSet.add(r);
                            addOppositeNatives(ruleSet, r, expanded);
                        }
                    }
                }
            } else {
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        ruleSet.add(r);
                    }
                }
            }
"""
linker_source = replace_once(linker_source, old_ruleset, new_ruleset, "Linker rule-set construction")

linker_path.write_text(linker_source, encoding="utf-8")
