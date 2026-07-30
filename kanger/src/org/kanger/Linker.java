/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.*;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Hypothesis;
import org.kanger.primitives.Solve;
import org.kanger.primitives.TVariableSet;
import org.kanger.stores.LogStore;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Linker {

    private final transient Mind mind;
    private final LogStore log;

    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skippedPasses = 0;
    private final LinkerStatistics statistics = new LinkerStatistics();
    private int currentPass = 0;

    /**
     * Query-local tuple index used only while Linker rotates substitutions.
     * TSolve is already transient execution state; the index stores references
     * to those existing tuples and is cleared at the start of every link().
     */
    private final Map<TVariableSet, Map<Long, Map<Long, List<TSolve>>>> solveIndex = new HashMap<>();
    private final Map<TVariableSet, Integer> indexedSolveCounts = new HashMap<>();
    private final Set<TVariableSet> unarySolveKeys = new HashSet<>();
    private final Set<TSolve> indexedSolves = Collections.newSetFromMap(
            new IdentityHashMap<TSolve, Boolean>());

    public Linker(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }

    public LinkerStatistics snapshotStatistics() {
        return statistics.snapshot();
    }

    private void clearSolveIndex() {
        solveIndex.clear();
        indexedSolveCounts.clear();
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

    private void synchronizeSolveIndex() throws Exception {
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

    private static final class DeferredSolveCandidate {
        private final long operationId;
        private final Object[] substitution;

        private DeferredSolveCandidate(long operationId, Object[] substitution) {
            this.operationId = operationId;
            this.substitution = substitution;
        }
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

    private void addOppositeNatives(Set<IRule> ruleSet,
                                    IRule source,
                                    Set<DomainKey> expanded,
                                    boolean positional) throws Exception {
        for (List<Domain> branch : ((Rule) source).getTree()) {
            for (Domain domain : branch) {
                DomainKey candidateKey = new DomainKey(
                        domain.getPredicateId(), !domain.isAntc());
                if (expanded.add(candidateKey)) {
                    if (positional) {
                        ruleSet.addAll(mind.getRules().findByDomain(
                                domain, candidateKey.antc));
                    } else {
                        ruleSet.addAll(mind.getRules().findByDomain(
                                candidateKey.predicateId, candidateKey.antc));
                    }
                }
            }
        }
    }

    public void link(Rule rule, boolean logging) throws Exception {

        mind.getExcludedDomains().clear();
        mind.getUsedDomains().clear();
        mind.getCalculatedDomains().clear();
        mind.getUsedRules().clear();
        mind.getFloodControl().clear();

        mind.getRuleSolves().clear();
        clearSolveIndex();

        int passCounter = 0;

        solvedPasses = 0;
        dumpedPasses = 0;
        skippedPasses = 0;
        statistics.reset();
        currentPass = 0;

        final Map<IRule, Set<Cause>> causes = new HashMap<>();

        Rule top = mind.getRules().getTop();
        long topId = top == null ? -1 : top.getId();

        do {

            ++passCounter;
            currentPass = passCounter;
            statistics.incrementPasses();
            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", passCounter));
            }

            mind.getRules().dropAction();
            mind.getTValues().dropAction();
            mind.getFValues().dropAction();
            mind.getHypothesis().dropAction();
            mind.getTempHypothesis().dropAction();

            Set<IRule> ruleSet = new HashSet<>();
            if (rule != null) {

                for (List<Domain> list : rule.getTree()) {
                    for (Domain d : list) {
                        if ("rule(1)".equals(d.getPredicate(mind).toString(mind)) && d.get(0).getType() == ArgumentType.TVARIABLE) {
                            for (IRule r : mind.getRules()) {
                                if (!r.isDeleted(mind) && r.getId() < d.getRuleId()) {
                                    TValue s = null;
                                    TVariable t = (TVariable) d.get(0).getObject(mind);
                                    ITerm tm = mind.getTerms().add(r.getId());
                                    s = mind.getTValues().find(t, tm);
                                    if (s == null) {
                                        s = mind.getTValues().add(t, tm);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (rule != null) {
                Set<DomainKey> expanded = new HashSet<>();
                ruleSet.add(rule);
                addOppositeNatives(ruleSet, rule, expanded, true);
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        if (((Rule) r).isUsed(mind)) {
                            ruleSet.add(r);
                            addOppositeNatives(ruleSet, r, expanded, false);
                        } else if (r.isGenerated() && r.getId() > topId) {
                            ruleSet.add(r);
                            addOppositeNatives(ruleSet, r, expanded, false);
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

            List<IRule> leftList = new ArrayList<>();
            List<IRule> ruleList = new ArrayList<>();

            leftList.addAll(ruleSet);
            Collections.sort(leftList, new Comparator<IRule>() {
                @Override
                public int compare(IRule o1, IRule o2) {
                    return (int) (o2.getId() - o1.getId());
                }
            });
            ruleList.addAll(ruleSet);
            Collections.sort(ruleList, new Comparator<IRule>() {
                @Override
                public int compare(IRule o1, IRule o2) {
                    return (int) (o1.getId() - o2.getId());
                }
            });


            rotator(leftList, causes, logging);
            rotator(ruleList, causes, logging);


        } while (mind.getRules().isAction()
                || mind.getTValues().isAction()
                || mind.getFValues().isAction()
                || mind.getTempHypothesis().isAction()
                || mind.getHypothesis().isAction()
        );

        if (logging) {
            log.add(LogMode.TIMING, String.format("* LINKER Solved passes: %03d", solvedPasses));
            log.add(LogMode.TIMING, String.format("* LINKER Dumped passes: %03d", dumpedPasses));
            log.add(LogMode.TIMING, String.format("* LINKER Skipped passes: %03d", skippedPasses));
        }
    }

    private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging) throws Exception {

        boolean used = false;
        final Map<DomainKey, List<IRule>> domainIndex = buildDomainIndex(ruleList);

        for (IRule r : ruleList) {

            statistics.incrementRuleVisits();
            mind.getProducedDomains().clear();
            mind.getDomainSolves().clear();
            mind.getDomainCauses().clear();

            final SortedSet<TVariable> tvars = new TreeSet<>();
            for (List<Domain> tree : ((Rule) r).getTree()) {
                for (Domain d : tree) {
                    tvars.addAll(d.getArguments().getTVariables(mind));
                }
            }

            boolean wasUsed = ((Rule) r).isUsed(mind);

            for (List<Domain> tree : ((Rule) r).getTree()) {

                statistics.incrementBranchVisits();
                final List<Domain> t = tree;

                rotateVariables(tvars, tvars, new IReactor() {
                    @Override
                    public Object run(Object o) {
                        statistics.incrementTerminalRotations();
                        boolean result = false;
                        try {
                            if (linkDomains(t, selectDomainCandidates(t, domainIndex), causes, logging)) {
                                result = true;
                            }
                            statistics.incrementFunctionEvaluations();
                            if (calcFunctions(t, causes, logging)) {
                                result = true;
                            }
                            statistics.incrementDatabaseEvaluations();
                            if (linkDatabase(t, causes, tvars, logging)) {
                                result = true;
                            }
                        } catch (Exception e) {
                            System.err.println(new Date());
                            e.printStackTrace(System.err);
                            result = false;
                        }

                        return result;
                    }
                });
            }

            updateDatabase(logging);
            if (!wasUsed && ((Rule) r).isUsed(mind)) {
                used = true;
            }
        }

        return used;
    }

    private boolean rotateVariables(final SortedSet<TVariable> tvars, final SortedSet<TVariable> base, final IReactor runnable) throws Exception {
        final boolean[] result = new boolean[]{false, false};
        if (tvars.isEmpty()) {
            result[0] = (boolean) runnable.run(tvars);
        } else {
            final TVariable t = tvars.last();

            if (t.getFloodCounter() > mind.getFloodControlLimit()) {
                throw new RuntimeErrorException("Flood limit exceeded (" + mind.getFloodControlLimit() + ")");
            }
            mind.getTValues().forEach(t, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    result[1] = true;
                    t.setCurrent((TValue) o);
                    if (isValidFor(base.tailSet(t))) {
                        if (rotateVariables(tvars.headSet(t), base, runnable)) {
                            result[0] = true;
                        }
                    }
                    return true;
                }
            });

            if (!result[1]) {
                if (rotateVariables(tvars.headSet(t), base, runnable)) {
                    result[0] = true;
                }
            }
        }
        return result[0];
    }

    /**
     * Checks whether the partial binding currently assembled by
     * {@link #rotateVariables(SortedSet, SortedSet, IReactor)} is compatible
     * with the transient {@link TSolve} tuples already registered in
     * {@code mind.getRuleSolves()}.
     *
     * <p>This is not a general validity predicate and it does not choose the
     * "best" value for a variable. It is a runtime join condition. Variables
     * are rotated independently, while a TSolve records values that were
     * produced together. The check prevents the rotation from constructing a
     * Cartesian combination of individually legal values that never belonged
     * to one compatible TSolve tuple.</p>
     *
     * <p>The {@code tail} contains the current variable and the variables that
     * have already received current values in the descending rotation. For the
     * first variable in that tail, the method examines solve keys containing
     * that variable and accepts the partial binding when at least one such key
     * can support it:</p>
     * <ul>
     *     <li>If no solve key contains the current variable, the binding is not
     *     constrained here and is accepted.</li>
     *     <li>A unary solve key is treated as sufficient by the historical
     *     contract; this method does not re-check its value.</li>
     *     <li>For a multi-variable key, candidates are first selected by the
     *     current variable/value pair. Every other already-bound variable that
     *     occurs in the same TSolve must then have the same current TValue.</li>
     *     <li>Different solve keys are alternatives: compatibility with one of
     *     them is sufficient.</li>
     * </ul>
     *
     * <p>The query-local index is only an acceleration structure over existing
     * TSolve objects. It must not change these acceptance rules. Any future
     * rewrite should first characterize the correlated, unary, absent-key and
     * partially-bound cases with regression tests; otherwise a seemingly local
     * simplification can silently change the set of substitutions explored by
     * the Linker.</p>
     */
    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {
        synchronizeSolveIndex();
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

    private boolean linkDomains(List<Domain> treeSlave, Collection<IRule> ruleList, Map<IRule, Set<Cause>> causes, boolean logging) throws Exception {

        Map<Solve, List<DeferredSolveCandidate>> variants = new HashMap<>();
        boolean result = false;

        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (IRule rule : ruleList) {
                    statistics.incrementCandidateRuleVisits();
                    for (List<Domain> treeMaster : ((Rule) rule).getTree()) {
                        for (Domain master : treeMaster) {
                            statistics.incrementDomainPairs();
                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {
                                long operationId = statistics.incrementUnificationAttempts(
                                        currentPass == 1, rule.isQuery(), rule.isGenerated());
                                int operationEffects = 0;
                                TValue[] substMaster = new TValue[master.getRange()];
                                TValue[] substSlave = new TValue[slave.getRange()];

                                mind.getTValues().mark();
                                mind.getFValues().mark();

                                boolean success = true;
                                boolean applied = false;

                                // Отсечение несовпадений по константам

                                boolean blockRight = false;
                                boolean blockLeft = false;

                                for (int i = 0; i < master.getRange(); ++i) {
                                    if (master.get(i).getType() == ArgumentType.TVARIABLE) {
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
                                    } else {
                                        blockRight = true;
                                    }
                                }

                                for (int i = 0; i < slave.getRange(); ++i) {
                                    if (slave.get(i).getType() == ArgumentType.TVARIABLE) {
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
                                    } else {
                                        blockLeft = true;
                                    }
                                }

                                if (success) {
                                    for (int i = 0; i < slave.getRange(); ++i) {

                                        // Подстановка снизу вверх
                                        if (!blockRight) {
                                            if (master.get(i).getType() == ArgumentType.TVARIABLE /*&& master.get(i).isEmpty(mind)*/) {
                                                if (!slave.get(i).isEmpty(mind)) {
                                                    Term tm = (Term) slave.get(i).getValue(mind);
                                                    TVariable t = (TVariable) master.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && slave.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind), tm);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }
                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
                                                        statistics.incrementNewTValues();
                                                        operationEffects |= LinkerStatistics.EFFECT_NEW_TVALUE;
                                                        result = true;
                                                    }
                                                    substMaster[i] = s;
                                                    slave.setUsed(mind);
                                                    master.setUsed(mind);
                                                    applied = true;

                                                }
                                            }
                                        }

                                        // Подстановка сверху вниз
                                        if (!blockLeft) {
                                            if (slave.get(i).getType() == ArgumentType.TVARIABLE && slave.get(i).isEmpty(mind)) {
                                                if (!master.get(i).isEmpty(mind)) {

                                                    Term tm = (Term) master.get(i).getValue(mind);
                                                    TVariable t = (TVariable) slave.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && master.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind), tm);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }

                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
                                                        statistics.incrementNewTValues();
                                                        operationEffects |= LinkerStatistics.EFFECT_NEW_TVALUE;
                                                        result = true;
                                                    }

                                                    substSlave[i] = s;
                                                    master.setUsed(mind);
                                                    slave.setUsed(mind);
                                                    applied = true;

                                                }
                                            }
                                        }

                                        if (!applied) {
                                            substMaster[i] = null;
                                            substSlave[i] = null;
                                        }

                                    }
                                }

                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();
                                    } else if (!master.isSubstitutable() && !slave.isSubstitutable()) {
                                        ++solvedPasses;
                                        operationEffects |= LinkerStatistics.EFFECT_USED_ONLY;
                                        master.setUsed(mind);
                                        slave.setUsed(mind);
                                    } else {
                                        ++dumpedPasses;
                                    }
                                    operationEffects |= markExcluded(result, substMaster, master, slave, causes, variants, operationId, logging);
                                    operationEffects |= markExcluded(result, substSlave, slave, master, causes, variants, operationId, logging);

                                    ((Rule) master.getRule()).setUsed(mind);
                                    ((Rule) slave.getRule()).setUsed(mind);
                                } else {
                                    ++skippedPasses;
                                    mind.getTValues().release();
                                    mind.getFValues().release();
                                }
                                statistics.recordOperationEffectMask(operationEffects);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Solve, List<DeferredSolveCandidate>> variantsList : variants.entrySet()) {
                for (DeferredSolveCandidate candidate : variantsList.getValue()) {
                    Object[] subst = candidate.substitution;
                    List<TValue> list = new ArrayList<>();
                    for (Object x : subst) {
                        if (x == null) {
                        } else if (x instanceof TValue) {
                            list.add((TValue) x);
                        }
                    }
                    SemanticEffectTelemetry.recordDeferredContribution(
                            list, candidate.operationId);
                    mind.addTSolve(list);
                }
            }

        }
        return result;
    }

    private int markExcluded(boolean result, TValue[] subst, Domain master, Domain slave, Map<IRule, Set<Cause>> causes, Map<Solve, List<DeferredSolveCandidate>> variants, long operationId, boolean logging) throws Exception {
        IRule r = null;
        boolean occurrs = false;
        int effects = 0;


        List<TValue> list = new ArrayList<>();
        for (int i = 0; i < slave.getRange(); ++i) {
            if (subst[i] != null) {
                if (subst[i] instanceof Collection) {
                    list.addAll((Collection<TValue>) subst[i]);
                } else {
                    list.add(subst[i]);
                }
            }
        }
        for (TValue v : list) {
            r = v.getTVar(mind).getRule(mind);
            if (logging && result) {
                log.add(LogMode.ANALYZER, "Closed: " + v.toString(mind));
            }
            occurrs = true;
        }

        if (r != null) {

            if (occurrs) {
                Cause s = new Cause(master, slave, mind);
                if (!causes.containsKey(r)) {
                    causes.put(r, new HashSet<>());
                }
                if (causes.get(r).add(s)) {
                    effects |= LinkerStatistics.EFFECT_NEW_CAUSE;
                }
            }

            master.setExcluded(slave.getArguments(), mind);
            if (!variants.containsKey(master)) {
                variants.put(master, new ArrayList<>());
            }
            variants.get(master).add(new DeferredSolveCandidate(operationId, subst));
            effects |= LinkerStatistics.EFFECT_DEFERRED_SOLVE_CANDIDATE;

            if (occurrs && result && logging) {
                mind.pushDebugLevel();
                mind.setDebugLevel(mind.getDebugLevel() & ~(Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS));
                log.add(LogMode.ANALYZER, "From right: " + r); //master.getRight());
                log.add(LogMode.ANALYZER, "\tAcceptor: " + master);
                mind.popDebugLevel();
                log.add(LogMode.ANALYZER, "\tDonor   : " + slave);
                log.add(LogMode.ANALYZER, "-------------------------------------------");
            }
        }
        return effects;
    }


    /**
     * Historical branch-closure and deferred-materialization procedure.
     *
     * <p>Despite its name, this method does not write a record directly to the
     * persistent rule store. It is invoked for one terminal rotation of the
     * rule variables, after {@link #linkDomains(List, Collection, Map, boolean)}
     * and {@link #calcFunctions(List, Map, boolean)}. It interprets the current
     * state of the branch and may:</p>
     * <ul>
     *     <li>mark a Domain as produced for later materialization by
     *     {@link #updateDatabase(boolean)};</li>
     *     <li>attach the current TValue solution and the available Causes;</li>
     *     <li>record a calculated or waiter-assisted consequence;</li>
     *     <li>create temporary alternative Hypotheses when the branch cannot be
     *     reduced to one consequence.</li>
     * </ul>
     *
     * <p>The method is an old, compact encoding of several semantic rules, not
     * a conventional storage helper. Its observable processing order is part of
     * the current behavior:</p>
     * <ol>
     *     <li>Execute and validate system Domains through {@code checkSystem}.</li>
     *     <li>Capture the current non-empty TValue binding as the solve attached
     *     to any produced Domain.</li>
     *     <li>Identify Domains that can be provisionally treated as assumed by
     *     an opposite waiter with compatible constant positions.</li>
     *     <li>Classify the branch into calculated, excluded, unresolved
     *     candidates and already stored Domains. A system or incomplete Domain
     *     clears only part of that classification and terminates the scan.</li>
     *     <li>Try, in order, the single-candidate closure, the excluded-only
     *     closure, the calculated-only closure, and the waiter-assisted
     *     closure.</li>
     *     <li>If no earlier strategy suppresses fallback and several candidates
     *     remain, create temporary alternative Hypotheses.</li>
     * </ol>
     *
     * <p>The local booleans must not be reinterpreted from their names:</p>
     * <ul>
     *     <li>{@code result} is not a complete "state changed" signal. It is set
     *     only by the ordinary production paths. For example, the special
     *     {@code rule(1)} path can call {@code setProduced}, and temporary
     *     hypotheses can be added, while this method still returns false.</li>
     *     <li>{@code occurs} is a historical fallback-suppression flag. In
     *     different branches it means that a special case was recognized, a
     *     strategy was applicable, or a strategy was attempted. It may become
     *     true even when no Domain is actually produced.</li>
     * </ul>
     *
     * <p>This method should therefore be treated as a frozen legacy semantic
     * kernel. Do not reorder independent-looking blocks, merge conditions,
     * replace {@code occurs} with {@code result}, or extract "clean" helpers on
     * the assumption that the names describe a formal state machine. A future
     * replacement should start from an explicit semantic model and a decision
     * table, using the current implementation and its regression corpus as an
     * executable historical oracle. Until then, behavioral changes belong here
     * only when a concrete contradiction is reproduced and protected by a
     * focused regression test.</p>
     */
    private boolean linkDatabase(List<Domain> tree, Map<IRule, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {

        boolean result = false;
        boolean occurs = false;

        if (checkSystem(tree, logging)) {

            List<TValue> solve = new ArrayList<>();
            for (TVariable t : tvars) {
                if (!t.isEmpty()) {
                    solve.add(t.getCurrent());
                }
            }

            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidates = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();
            Set<Domain> stored = new HashSet<>();

            for (Domain d : tree) {

                for (Domain master : mind.getDomains().getWaiters()) {
                    if (master.getPredicateId() == d.getPredicateId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                        boolean success = true;
                        for (int i = 0; i < d.getRange(); ++i) {
                            if (master.get(i).getType() == ArgumentType.TVARIABLE) {
                            } else if (master.get(i).getValue(mind).getId() == d.get(i).getValue(mind).getId()) {
                            } else {
                                success = false;
                                break;
                            }
                        }
                        if (success) {
                            assumed.add(d);
                        }
                    }
                }


            }

            for (Domain d : tree) {
                if ("rule(1)".equals(d.getPredicate(mind).toString(mind)) && !d.get(0).isEmpty(mind) && d.get(0).getValue(mind).getType() == DataType.NUMERIC) {
                    d.setProduced(mind);
                    log.add(LogMode.STORAGE, "DB assumed record (r): " + d);
                    occurs = true;
                } else if (d.isCalculated(mind)) {
                    calculated.add(d);
                } else if (d.isSystem(mind) || !d.isComplete()) {
                    excluded.clear();
                    candidates.clear();
                    break;
                } else if (d.isExcluded(mind)) {
                    excluded.add(d);
                } else {
                    candidates.add(d);
                }
                if (d.isStored(mind)) {
                    stored.add(d);
                }
            }

            if (candidates.size() == 1) {
                for (Domain d : candidates) {
                    occurs = true;
                    if (!d.isStored(mind) && (d.setCauses(causes.get(d.getRule()), mind) || !calculated.isEmpty() || !excluded.isEmpty())) {
                        boolean term = false;
                        boolean abst = false;
                        for (IArgument a : d.getArguments()) {
                            if (a.getValue(mind).isCVariable() && !((Term) a.getValue(mind)).isDomini()) {
                                abst = true;
                            } else {
                                term = true;
                            }
                        }
                        boolean skip = !term && abst;
                        if (!skip) {
                            result = true;
                            d.setProduced(mind);
                            d.setSolves(solve, mind);
                            if (logging) {
                                log.add(LogMode.STORAGE, "DB assumed record: " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRule()), mind)) {
                        result = true;
                        d.setProduced(mind);
                        d.setSolves(solve, mind);
                        if (logging) {
                            log.add(LogMode.STORAGE, "DB assumed record (x): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }

            if (!calculated.isEmpty() && candidates.isEmpty() /*&& tree.size() - excluded.size() == calculated.size()*/) {
                occurs = true;
                for (Domain d : calculated) {
                    if (!d.isStored(mind)) {
                        result = true;
                        d.setProduced(mind);
                        d.setCauses(causes.get(d.getRule()), mind);
                        d.setSolves(solve, mind);
                        if (logging) {
                            log.add(LogMode.STORAGE, "DB assumed record (c): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }

            if (!occurs && !assumed.isEmpty() && tree.size() > 1) {
                candidates.clear();
                excluded.clear();
                for (Domain d : tree) {
                    if (d.isComplete() && !d.isCalculated(mind) && !d.isSystem(mind) && !assumed.contains(d)) {
                        occurs = true;
                        if (!d.isExcluded(mind)) {
                            candidates.add(d);
                        } else {
                            excluded.add(d);
                        }
                    }
                }
                if (candidates.size() == 1 && !excluded.isEmpty()) {
                    Domain d = candidates.toArray(new Domain[]{})[0];
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRule()), mind)) {
                        occurs = true;
                        result = true;
                        d.setProduced(mind);
                        d.setSolves(solve, mind);
                        if (logging) {
                            log.add(LogMode.STORAGE, "DB assumed record (a): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }

            if (!occurs && candidates.size() > 1) {
                for (Domain d : candidates) {
                    if (!d.isStored(mind) && d.isComplete() && !d.isUsed(mind)) {

                        if (!mind.includeAbstractiveHypothesis()) {
                            for (IArgument a : d.getArguments()) {
                                if (a.getValue(mind).isCVariable()) {
                                    d = null;
                                    break;
                                }
                            }
                        }

                        if (d != null) {
                            Hypothesis tmp = new Hypothesis(d, mind);
//                            IRule rx = mind.getRules().find(tmp);
                            if (mind.getTempHypothesis().find(tmp) == null /*&& (rx == null || rx.isDeleted(mind))*/) {
                                mind.getTempHypothesis().add(tmp);
                                if (logging) {
                                    log.add(LogMode.ANALYZER, "Hypothesis alternate assumed: " + tmp.toString(mind));
                                }
                            }
                        }
                    }
                }
            }

            if (result) {
                if (logging) {
                    log.add(LogMode.STORAGE, "-------------------------------------------");
                }
            }
        }
        return result;
    }

    private void logCauses(LogMode mode, Domain d) throws Exception {
        boolean ruleShowed = false;
        if (d.getCauses(mind) != null) {
            for (ICause c : d.getCauses(mind)) {
                if (!ruleShowed) {
                    log.add(mode, "\tFrom rule: " + c.getRule(mind));
                    ruleShowed = true;
                }
                log.add(mode, "\t\tUsing: " + ((Cause) c).getDonor().toString(mind));
            }
        }
    }

    private boolean updateDatabase(boolean logging) throws Exception {
        boolean result = false;
        for (Map.Entry<Domain, List<List<ITerm>>> e : mind.getProducedDomains().entrySet()) {
            Domain d = e.getKey();
            for (List<ITerm> args : e.getValue()) {
                result = true;
                d.getArguments().applyStamp(mind, args);
                for (int i = 0; i < d.getRange(); ++i) {
                    if (d.getArguments().get(i).getType() == ArgumentType.FUNCTION
                            && ((Function) d.getArguments().get(i).getObject(mind)).isCalculable()
                            && ((Function) d.getArguments().get(i).getObject(mind)).isEmpty(mind)) {
                        ((Function) d.getArguments().get(i).getObject(mind)).clear();
                        mind.getCalculator().calculate((Function) d.getArguments().get(i).getObject(mind), logging);
                    }
                }
                if (d.isComplete()) {

                    IRule x = d.createStored(mind);
                    if (d.isUsed(mind)) {
                        ((Rule) x).getDomain().setUsed(mind);
                    }
                    if (logging) {
                        log.add(LogMode.STORAGE, "DB add record: " + d + " -> " + x);
                    }

                    if (d.isCalculated(mind)) {
                        ((Rule) x).getDomain().setCalculated(mind);
                    }
                    if (d.getCauses(mind) != null) {
                        x.getCauses().clear();
                        x.getCauses().addAll(d.getCauses(mind));
                    }
                    if (d.getSolves(mind) != null) {
                        ((Rule) x).getSolves().clear();
                        ((Rule) x).getSolves().addAll(d.getSolves(mind));
                    }
                }
            }
        }

        if (result && logging) {
            log.add(LogMode.ANALYZER, "-------------------------------------------");
        }
        return result;
    }


    public boolean calcFunctions(List<Domain> master, Map<IRule, Set<Cause>> causes, boolean logging) throws Exception {
        boolean result = false;

        for (Domain d : master) {
            for (Function f : d.getArguments().getFunctions(mind)) {
                if (f.isCalculable() && f.isEmpty(mind)) {
                    f.clear();
                    if (mind.getCalculator().calculate(f, logging)) {
                        result = true;
                    }
                }
            }
        }

        if (result && logging) {
            log.add(LogMode.ANALYZER, "-------------------------------------------");
        }
        return result;
    }

    public boolean checkSystem(List<Domain> tree, boolean logging) throws Exception {
        boolean block = false;
        boolean success = false;
        List<List<TValue>> solves = new ArrayList<>();
        for (Domain d : tree) {
            if (d.isSystem(mind)) {

                int res = d.execSystem(mind);
                for (IArgument a : d.getArguments()) {
                    if (a.isEmpty(mind)) {
                        res = -2;
                        break;
                    }
                }

                if (res == 0) {
                    if (d.isAntc()) {
                        d.setCalculated(mind);
                        success = true;
                    } else {
                        block = true;
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setCalculated(mind);
                        success = true;
                    } else {
                        block = true;
                    }
                }
                if (!block & d.isComplete()) {
                    List<TValue> list = new ArrayList<>();
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        list.add(t.getCurrent());
                    }
                    if (!list.isEmpty()) {
                        solves.add(list);
                    }
                }
            }
        }

        if (success && !block) {
            for (Domain d : tree) {
                if (d.isSystem(mind) && !d.isCalculated(mind)) {
                    block = true;
                }
            }
            if (block) {
                for (Domain d : tree) {
                    if (d.isCalculated(mind)) {
                        d.unCalculated(mind);
                    }
                }
            } else if (!solves.isEmpty()) {
                for (List<TValue> list : solves) {
                    mind.addTSolve(list);
                }
            }
        }

        return !block;
    }
}
