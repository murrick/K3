from pathlib import Path
import re

path = Path("kanger/src/org/kanger/Linker.java")
source = path.read_text(encoding="utf-8")

pattern = re.compile(
    r"    private Collection<IRule> selectDomainCandidates\(List<Domain> tree,\n"
    r"\s+Map<DomainKey, List<IRule>> index\) \{.*?\n"
    r"    \}\n\n"
    r"    private void addOppositeNatives",
    re.DOTALL,
)

replacement = '''    private Collection<IRule> selectDomainCandidates(List<Domain> tree,
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

    private void addOppositeNatives'''

updated, count = pattern.subn(replacement, source, count=1)
if count != 1:
    raise RuntimeError(f"Linker resolved candidate method: expected one match, found {count}")
path.write_text(updated, encoding="utf-8")
