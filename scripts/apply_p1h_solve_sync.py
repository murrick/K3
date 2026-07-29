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
    """    private final Map<TVariableSet, Map<Long, Map<Long, List<TSolve>>>> solveIndex = new HashMap<>();
    private final Set<TVariableSet> unarySolveKeys = new HashSet<>();
    private final Set<TSolve> indexedSolves = Collections.newSetFromMap(
            new IdentityHashMap<TSolve, Boolean>());
""",
    """    private final Map<TVariableSet, Map<Long, Map<Long, List<TSolve>>>> solveIndex = new HashMap<>();
    private final Map<TVariableSet, Integer> indexedSolveCounts = new HashMap<>();
    private final Set<TVariableSet> unarySolveKeys = new HashSet<>();
    private final Set<TSolve> indexedSolves = Collections.newSetFromMap(
            new IdentityHashMap<TSolve, Boolean>());
""",
    "solve index fields")

replace_once(
    """    private void clearSolveIndex() {
        solveIndex.clear();
        unarySolveKeys.clear();
        indexedSolves.clear();
    }
""",
    """    private void clearSolveIndex() {
        solveIndex.clear();
        indexedSolveCounts.clear();
        unarySolveKeys.clear();
        indexedSolves.clear();
    }
""",
    "clearSolveIndex")

replace_once(
    """    private List<TSolve> getSolveCandidates(TVariableSet key,
                                            TVariable variable,
                                            TValue value) {
""",
    """    private void synchronizeSolveIndex() throws Exception {
        for (Map.Entry<TVariableSet, List<TSolve>> entry : mind.getRuleSolves().entrySet()) {
            int indexed = indexedSolveCounts.containsKey(entry.getKey())
                    ? indexedSolveCounts.get(entry.getKey()) : 0;
            List<TSolve> solves = entry.getValue();
            for (int i = indexed; i < solves.size(); ++i) {
                indexSolve(solves.get(i));
            }
            indexedSolveCounts.put(entry.getKey(), solves.size());
        }
    }

    private List<TSolve> getSolveCandidates(TVariableSet key,
                                            TVariable variable,
                                            TValue value) {
""",
    "solve index synchronization")

replace_once(
    """    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {
        final TVariable t = tail.first();
""",
    """    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {
        synchronizeSolveIndex();
        final TVariable t = tail.first();
""",
    "isValidFor synchronization")

replace_once(
    """                    TSolve s = mind.addTSolve(list);
                    indexSolve(s);
""",
    """                    mind.addTSolve(list);
""",
    "explicit solve indexing")

path.write_text(source, encoding="utf-8")
