from pathlib import Path

path = Path("kanger/src/org/kanger/Linker.java")
source = path.read_text(encoding="utf-8")


def replace_once(old, new, label):
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} match, found {count}")
    source = source.replace(old, new, 1)


replace_once(
    """    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skippedPasses = 0;

    public Linker(Mind mind) {
""",
    """    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skippedPasses = 0;

    /**
     * Query-local tuple index used only while Linker rotates substitutions.
     * TSolve is already transient execution state; the index stores references
     * to those existing tuples and is cleared at the start of every link().
     */
    private final Map<TVariableSet, Map<Long, Map<Long, List<TSolve>>>> solveIndex = new HashMap<>();
    private final Set<TVariableSet> unarySolveKeys = new HashSet<>();
    private final Set<TSolve> indexedSolves = Collections.newSetFromMap(
            new IdentityHashMap<TSolve, Boolean>());

    public Linker(Mind mind) {
""",
    "Linker fields")

replace_once(
    """    private static final class DomainKey {
""",
    """    private void clearSolveIndex() {
        solveIndex.clear();
        unarySolveKeys.clear();
        indexedSolves.clear();
    }

    private void indexSolve(TSolve solve) throws Exception {
        if (solve == null || !indexedSolves.add(solve)) {
            return;
        }

        TVariableSet key = new TVariableSet(solve, mind);
        if (solve.size() == 1) {
            unarySolveKeys.add(key);
        }

        Map<Long, Map<Long, List<TSolve>>> byVariable = solveIndex.get(key);
        if (byVariable == null) {
            byVariable = new HashMap<>();
            solveIndex.put(key, byVariable);
        }

        for (TValue value : solve.getSolve()) {
            long variableId = value.getTVarId();
            Map<Long, List<TSolve>> byValue = byVariable.get(variableId);
            if (byValue == null) {
                byValue = new HashMap<>();
                byVariable.put(variableId, byValue);
            }

            List<TSolve> candidates = byValue.get(value.getId());
            if (candidates == null) {
                candidates = new ArrayList<>();
                byValue.put(value.getId(), candidates);
            }
            candidates.add(solve);
        }
    }

    private List<TSolve> getSolveCandidates(TVariableSet key,
                                            TVariable variable,
                                            TValue value) {
        if (value == null) {
            return Collections.emptyList();
        }
        Map<Long, Map<Long, List<TSolve>>> byVariable = solveIndex.get(key);
        if (byVariable == null) {
            return Collections.emptyList();
        }
        Map<Long, List<TSolve>> byValue = byVariable.get(variable.getId());
        if (byValue == null) {
            return Collections.emptyList();
        }
        List<TSolve> candidates = byValue.get(value.getId());
        return candidates == null ? Collections.<TSolve>emptyList() : candidates;
    }

    private static final class DomainKey {
""",
    "solve index helpers")

replace_once(
    """        mind.getRuleSolves().clear();

        int passCounter = 0;
""",
    """        mind.getRuleSolves().clear();
        clearSolveIndex();

        int passCounter = 0;
""",
    "link initialization")

replace_once(
    """    private boolean isValidFor(SortedSet<TVariable> tail) {
        final TVariable t = tail.first();
        boolean found = false;
        boolean result = false;
        if (tail.size() > 1) {
            for (TVariableSet key : mind.getRuleSolves().keySet()) {
                if (key.contains(t)) {
                    found = true;
                    boolean success = false;
                    for (TSolve s : mind.getRuleSolves().get(key)) {
                        if (s.containsTValue(t.getCurrent())) {
                            if (s.size() > 1) {
                                boolean complete = true;
                                for (TVariable x : tail) {
                                    if (x.getId() != t.getId()) {
                                        if (s.containsTVar(x)) {
                                            if (!s.containsTValue(x.getCurrent())) {
                                                complete = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (complete) {
                                    success = true;
                                    break;
                                }
                            } else {
                                success = true;
                                break;
                            }
                        } else if (s.size() == 1) {
                            success = true;
                            break;
                        }
                    }
                    if (success) {
                        result = true;
                        break;
                    }
                }
            }
        }
        return !found || result;
    }
""",
    """    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {
        final TVariable t = tail.first();
        boolean found = false;
        boolean result = false;
        if (tail.size() > 1) {
            for (TVariableSet key : mind.getRuleSolves().keySet()) {
                if (key.contains(t)) {
                    found = true;
                    boolean success = unarySolveKeys.contains(key);
                    if (!success) {
                        for (TSolve s : getSolveCandidates(key, t, t.getCurrent())) {
                            if (s.size() > 1) {
                                boolean complete = true;
                                for (TVariable x : tail) {
                                    if (x.getId() != t.getId()) {
                                        if (s.containsTVar(x)) {
                                            if (!s.containsTValue(x.getCurrent())) {
                                                complete = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (complete) {
                                    success = true;
                                    break;
                                }
                            } else {
                                success = true;
                                break;
                            }
                        }
                    }
                    if (success) {
                        result = true;
                        break;
                    }
                }
            }
        }
        return !found || result;
    }
""",
    "isValidFor")

replace_once(
    """                    TSolve s = mind.addTSolve(list);
""",
    """                    TSolve s = mind.addTSolve(list);
                    indexSolve(s);
""",
    "TSolve registration")

path.write_text(source, encoding="utf-8")
