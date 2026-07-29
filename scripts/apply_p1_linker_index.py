from pathlib import Path

path = Path("kanger/src/org/kanger/Linker.java")
source = path.read_text(encoding="utf-8")

constructor = """    public Linker(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }
"""

helpers = """    public Linker(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }

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

    private Map<DomainKey, List<IRule>> buildDomainIndex(Collection<IRule> ruleList) throws Exception {
        Map<DomainKey, List<IRule>> index = new HashMap<>();
        for (IRule candidate : ruleList) {
            Set<DomainKey> indexedKeys = new HashSet<>();
            for (List<Domain> branch : ((Rule) candidate).getTree()) {
                for (Domain domain : branch) {
                    DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                    if (indexedKeys.add(key)) {
                        List<IRule> bucket = index.get(key);
                        if (bucket == null) {
                            bucket = new ArrayList<>();
                            index.put(key, bucket);
                        }
                        bucket.add(candidate);
                    }
                }
            }
        }
        return index;
    }

    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
                                                      Map<DomainKey, List<IRule>> index) {
        if (tree.size() != 1) {
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        return candidates == null ? Collections.<IRule>emptyList() : candidates;
    }
"""

rotator = """    private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging) throws Exception {

        boolean used = false;
"""

rotator_indexed = """    private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging) throws Exception {

        boolean used = false;
        final Map<DomainKey, List<IRule>> domainIndex = buildDomainIndex(ruleList);
"""

old_call = """                            if (linkDomains(t, ruleList, causes, logging)) {
"""
new_call = """                            if (linkDomains(t, selectDomainCandidates(t, domainIndex), causes, logging)) {
"""

for name, needle, replacement in [
    ("constructor", constructor, helpers),
    ("rotator", rotator, rotator_indexed),
    ("linkDomains call", old_call, new_call),
]:
    count = source.count(needle)
    if count != 1:
        raise SystemExit(f"Expected exactly one {name} match, found {count}")
    source = source.replace(needle, replacement, 1)

path.write_text(source, encoding="utf-8")
