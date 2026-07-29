from pathlib import Path


def replace_once(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} match, found {count}")
    return source.replace(old, new, 1)


rule_path = Path("kanger/src/org/kanger/factory/RuleFactory.java")
source = rule_path.read_text(encoding="utf-8")

source = replace_once(
    source,
    """    private RuleFactory parentIndex = null;
    private final Map<DomainKey, LinkedHashSet<Long>> localDomainIndex = new HashMap<>();
    private final Stack<Map<DomainKey, LinkedHashSet<Long>>> domainIndexStack = new Stack<>();
    private boolean domainIndexInitialized = false;
""",
    """    private RuleFactory parentIndex = null;
    private final Map<DomainKey, LinkedHashSet<Long>> localDomainIndex = new HashMap<>();
    private final Map<Long, LinkedHashSet<Long>> localTermIndex = new HashMap<>();
    private final Stack<Map<DomainKey, LinkedHashSet<Long>>> domainIndexStack = new Stack<>();
    private final Stack<Map<Long, LinkedHashSet<Long>>> termIndexStack = new Stack<>();
    private boolean domainIndexInitialized = false;
""",
    "RuleFactory index fields")

source = replace_once(
    source,
    """        parentIndex = base;
        localDomainIndex.clear();
        domainIndexStack.clear();
        domainIndexInitialized = base != null;
""",
    """        parentIndex = base;
        localDomainIndex.clear();
        localTermIndex.clear();
        domainIndexStack.clear();
        termIndexStack.clear();
        domainIndexInitialized = base != null;
""",
    "RuleFactory transaction indexes")

source = replace_once(
    source,
    """    private Map<DomainKey, LinkedHashSet<Long>> copyDomainIndex() {
        Map<DomainKey, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : localDomainIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private void ensureDomainIndex() throws Exception {
""",
    """    private Map<DomainKey, LinkedHashSet<Long>> copyDomainIndex() {
        Map<DomainKey, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : localDomainIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private Map<Long, LinkedHashSet<Long>> copyTermIndex() {
        Map<Long, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : localTermIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private void ensureDomainIndex() throws Exception {
""",
    "RuleFactory index snapshots")

source = replace_once(
    source,
    """        localDomainIndex.clear();
        for (Object value : cache) {
""",
    """        localDomainIndex.clear();
        localTermIndex.clear();
        for (Object value : cache) {
""",
    "RuleFactory rebuild indexes")

source = replace_once(
    source,
    """        Set<DomainKey> indexed = new HashSet<>();
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
""",
    """        Set<DomainKey> indexed = new HashSet<>();
        rule.getTerms().add(rule.getOriginId());
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                rule.getTerms().addAll(domain.getTerms(mind, true));
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
        for (long termId : rule.getTerms()) {
            LinkedHashSet<Long> ids = localTermIndex.get(termId);
            if (ids == null) {
                ids = new LinkedHashSet<>();
                localTermIndex.put(termId, ids);
            }
            ids.add(rule.getId());
        }
    }

    private void unindexRule(Rule rule) throws Exception {
""",
    "RuleFactory index rule")

source = replace_once(
    source,
    """        Set<DomainKey> indexed = new HashSet<>();
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
""",
    """        Set<DomainKey> indexed = new HashSet<>();
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
        for (long termId : rule.getTerms()) {
            LinkedHashSet<Long> ids = localTermIndex.get(termId);
            if (ids != null) {
                ids.remove(rule.getId());
                if (ids.isEmpty()) {
                    localTermIndex.remove(termId);
                }
            }
        }
    }

    private void collectDomainIds(DomainKey key, LinkedHashSet<Long> result) throws Exception {
""",
    "RuleFactory unindex rule")

source = replace_once(
    source,
    """    private void mergeDomainIndex(RuleFactory child) throws Exception {
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
""",
    """    private void collectTermIds(long termId, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectTermIds(termId, result);
        }
        ensureDomainIndex();
        LinkedHashSet<Long> local = localTermIndex.get(termId);
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
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : child.localTermIndex.entrySet()) {
            LinkedHashSet<Long> ids = localTermIndex.get(entry.getKey());
            if (ids == null) {
                ids = new LinkedHashSet<>();
                localTermIndex.put(entry.getKey(), ids);
            }
            ids.addAll(entry.getValue());
        }
    }

    public List<IRule> findByDomain(long predicateId, boolean antc) throws Exception {
""",
    "RuleFactory merge and term collection")

source = replace_once(
    source,
    """        return result;
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
""",
    """        return result;
    }

    public boolean hasActiveRuleWithTerm(long termId) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectTermIds(termId, ids);
        for (long id : ids) {
            Rule rule = get(id);
            if (rule != null
                    && !rule.isDeleted(mind)
                    && rule.containsTerm(termId, mind)) {
                return true;
            }
        }
        return false;
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
""",
    "RuleFactory term lookup")

source = replace_once(
    source,
    """        ensureDomainIndex();
        domainIndexStack.push(copyDomainIndex());
        promotionStack.push(new HashSet<>(primaryPromotions));
""",
    """        ensureDomainIndex();
        domainIndexStack.push(copyDomainIndex());
        termIndexStack.push(copyTermIndex());
        promotionStack.push(new HashSet<>(primaryPromotions));
""",
    "RuleFactory mark indexes")

source = replace_once(
    source,
    """        if (!domainIndexStack.isEmpty()) {
            domainIndexStack.pop();
        }
        if (!promotionStack.isEmpty()) {
""",
    """        if (!domainIndexStack.isEmpty()) {
            domainIndexStack.pop();
        }
        if (!termIndexStack.isEmpty()) {
            termIndexStack.pop();
        }
        if (!promotionStack.isEmpty()) {
""",
    "RuleFactory commit indexes")

source = replace_once(
    source,
    """        if (!domainIndexStack.isEmpty()) {
            localDomainIndex.clear();
            localDomainIndex.putAll(domainIndexStack.pop());
            domainIndexInitialized = true;
        }
        if (!promotionStack.isEmpty()) {
""",
    """        if (!domainIndexStack.isEmpty()) {
            localDomainIndex.clear();
            localDomainIndex.putAll(domainIndexStack.pop());
            domainIndexInitialized = true;
        }
        if (!termIndexStack.isEmpty()) {
            localTermIndex.clear();
            localTermIndex.putAll(termIndexStack.pop());
            domainIndexInitialized = true;
        }
        if (!promotionStack.isEmpty()) {
""",
    "RuleFactory release indexes")

rule_path.write_text(source, encoding="utf-8")


tvalue_path = Path("kanger/src/org/kanger/factory/TValueFactory.java")
tvalue = tvalue_path.read_text(encoding="utf-8")
tvalue = replace_once(
    tvalue,
    """                boolean found = false;
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind) && ((Rule) r).containsTerm(((TValue) o).getValue(mind).getId(), mind)) {
                        found = true;
                        break;
                    }
                }
""",
    """                boolean found = mind.getRules().hasActiveRuleWithTerm(
                        ((TValue) o).getValue(mind).getId());
""",
    "TValueFactory pack rule scan")
tvalue_path.write_text(tvalue, encoding="utf-8")
