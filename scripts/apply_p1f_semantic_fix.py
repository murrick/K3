from pathlib import Path


def replace_once(path, old, new, label):
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} match, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


replace_once(
    Path("kanger/src/org/kanger/units/Rule.java"),
    """    /**
     * RuleFactory normally populates the term set when a Rule is registered.
     * A Rule loaded from persistent storage starts with an empty transient set,
     * so rebuild it lazily once. Re-scanning the complete Domain tree for every
     * TValue reachability check created the dominant post-query CPU hotspot.
     */
    public boolean containsTerm(long id, Mind mind) throws Exception {
        if (terms.isEmpty()) {
            terms.add(originId);
            for (List<Domain> row : getTree()) {
                for (Domain d : row) {
                    terms.addAll(d.getTerms(mind, true));
                }
            }
        }
        return terms.contains(id);
    }
""",
    """    public boolean containsTerm(long id, Mind mind) throws Exception {
        terms.add(originId);
        for (List<Domain> row : getTree()) {
            for (Domain d : row) {
                terms.addAll(d.getTerms(mind, true));
            }
        }
        return terms.contains(id);
    }
""",
    "Rule.containsTerm")

replace_once(
    Path("kanger/src/org/kanger/factory/RuleFactory.java"),
    """        for (long id : ids) {
            Rule rule = get(id);
            if (rule != null
                    && !rule.isDeleted(mind)
                    && rule.containsTerm(termId, mind)) {
                return true;
            }
        }
""",
    """        for (long id : ids) {
            Rule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                return true;
            }
        }
""",
    "RuleFactory.hasActiveRuleWithTerm")
