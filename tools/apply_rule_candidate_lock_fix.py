from pathlib import Path


def patch_candidate_index() -> None:
    path = Path("kanger/src/org/kanger/factory/RuleCandidateIndex.java")
    text = path.read_text()

    field_anchor = (
        "    private final Map<Mind, Map<BatchKey, BatchSummary>> "
        "batchSummaries = new WeakHashMap<>();\n"
    )
    if "    private long version = 0L;\n" not in text:
        text = text.replace(
            field_anchor,
            field_anchor + "    private long version = 0L;\n",
            1,
        )

    replacements = [
        (
            """            positions.clear();
            batchSummaries.clear();
""",
            """            positions.clear();
            batchSummaries.clear();
            ++version;
""",
        ),
        (
            """            signatures.release();
            fallbackSignatures.release();
            positions.release();
""",
            """            signatures.release();
            fallbackSignatures.release();
            positions.release();
            batchSummaries.clear();
            ++version;
""",
        ),
        (
            """            positions.mergeFrom(childSnapshot.positions);
            batchSummaries.clear();
""",
            """            positions.mergeFrom(childSnapshot.positions);
            batchSummaries.clear();
            ++version;
""",
        ),
        (
            """        try {
            batchSummaries.clear();
            boolean positional = positionalEligible(rule);
""",
            """        try {
            batchSummaries.clear();
            ++version;
            boolean positional = positionalEligible(rule);
""",
        ),
        (
            """        try {
            batchSummaries.clear();
            for (List<Domain> branch : rule.getTree()) {
""",
            """        try {
            batchSummaries.clear();
            ++version;
            for (List<Domain> branch : rule.getTree()) {
""",
        ),
    ]
    for old, new in replacements:
        if old not in text:
            raise RuntimeError(f"Candidate index patch anchor not found: {old!r}")
        text = text.replace(old, new, 1)

    start = text.index("    private BatchSummary batchSummary(")
    end = text.index("    private Long resolvedTermId(", start)
    replacement = """    private BatchSummary computeBatchSummary(Domain source,
                                             boolean candidateAntc,
                                             Mind activeMind,
                                             LinkedHashSet<Long> selected) throws Exception {
        LinkedHashSet<Long> batchedIds = new LinkedHashSet<>();
        for (long id : selected) {
            Rule candidate = (Rule) activeMind.getRules().get(id);
            if (candidate != null
                    && batchGeneratedNonSubstitutablePair(
                    source, candidate, candidateAntc, activeMind)) {
                batchedIds.add(id);
            }
        }
        return new BatchSummary(batchedIds);
    }

    private void markCachedBatchUsed(Domain source,
                                     Rule sourceRule,
                                     Mind activeMind,
                                     BatchSummary summary) throws Exception {
        if (!summary.batchedIds.isEmpty()) {
            source.setUsed(activeMind);
            sourceRule.setUsed(activeMind);
        }
    }

    void collectResolvedLocal(Domain source, boolean candidateAntc, Mind mind,
                              LinkedHashSet<Long> result) throws Exception {
        SignatureKey signature = signature(source, candidateAntc);
        Long[] resolvedTermIds = new Long[source.getRange()];
        for (int position = 0; position < source.getRange(); ++position) {
            resolvedTermIds[position] = resolvedTermId(source.get(position), mind);
        }

        boolean batchEligible = !source.isSubstitutable();
        Rule sourceRule = batchEligible ? (Rule) source.getRule() : null;
        Mind activeMind = batchEligible
                ? (sourceRule.getMind() == null ? mind : sourceRule.getMind())
                : null;
        BatchKey batchKey = batchEligible
                ? new BatchKey(signature, sourceRule.isGenerated())
                : null;

        LinkedHashSet<Long> selected;
        BatchSummary summary = null;
        long observedVersion;

        writeLock.lock();
        try {
            selected = signatures.get(signature);
            if (selected.isEmpty()) return;
            LinkedHashSet<Long> fallback = fallbackSignatures.get(signature);
            for (int position = 0; position < resolvedTermIds.length; ++position) {
                Long termId = resolvedTermIds[position];
                if (termId == null) continue;
                LinkedHashSet<Long> compatible = positions.get(
                        new PositionKey(signature, position, termId));
                compatible.addAll(positions.get(
                        new PositionKey(signature, position, WILDCARD_TERM_ID)));
                compatible.addAll(fallback);
                selected.retainAll(compatible);
                if (selected.isEmpty()) return;
            }
            observedVersion = version;
            if (batchEligible) {
                Map<BatchKey, BatchSummary> byKey = batchSummaries.get(activeMind);
                summary = byKey == null ? null : byKey.get(batchKey);
            }
        } finally {
            writeLock.unlock();
        }

        if (batchEligible) {
            boolean cached = summary != null;
            if (summary == null) {
                summary = computeBatchSummary(
                        source, candidateAntc, activeMind, selected);

                writeLock.lock();
                try {
                    if (version == observedVersion) {
                        Map<BatchKey, BatchSummary> byKey = batchSummaries.get(activeMind);
                        if (byKey == null) {
                            byKey = new HashMap<>();
                            batchSummaries.put(activeMind, byKey);
                        }
                        BatchSummary existing = byKey.get(batchKey);
                        if (existing == null) {
                            byKey.put(batchKey, summary);
                        } else {
                            summary = existing;
                            cached = true;
                        }
                    }
                } finally {
                    writeLock.unlock();
                }
            }
            if (cached) {
                markCachedBatchUsed(source, sourceRule, activeMind, summary);
            }
            selected.removeAll(summary.batchedIds);
        }
        result.addAll(selected);
    }

"""
    path.write_text(text[:start] + replacement + text[end:])


def patch_rule_factory() -> None:
    path = Path("kanger/src/org/kanger/factory/RuleFactory.java")
    text = path.read_text()
    old = """        ensureDomainIndex();
        synchronized (metadataLock) {
            candidateIndex.collectResolvedLocal(source, candidateAntc, mind, result);
        }
"""
    new = """        ensureDomainIndex();
        candidateIndex.collectResolvedLocal(source, candidateAntc, mind, result);
"""
    if old not in text:
        raise RuntimeError("RuleFactory resolved candidate lock boundary not found")
    path.write_text(text.replace(old, new, 1))


patch_candidate_index()
patch_rule_factory()
