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
 * Исполнитель насыщения и оркестратор неподвижной точки KANGER для одного
 * {@link Mind}.
 *
 * <p><strong>Роль и владение.</strong> Каждый {@code Mind} создаёт собственный
 * {@code Linker}; экземпляр хранит ссылку на этот runtime-контекст и его
 * {@link LogStore}. Linker не владеет каноническими Rule, TValue, FValue или
 * Hypothesis и не является самостоятельной транзакцией. Он координирует их
 * фабрики, transient solve-state, вычисления и материализацию внутри уже
 * зарезервированного вызывающей стороной Mind.</p>
 *
 * <p><strong>Граница одного запуска.</strong> {@link #link(Rule, boolean)}
 * очищает query-local used/excluded/calculated/flood state, canonical
 * {@code ruleSolves}, производный tuple-index и статистику. Ускорители
 * {@code solveIndex}, {@code indexedSolveCounts}, {@code unarySolveKeys} и
 * {@code indexedSolves} содержат только ссылки на существующие {@link TSolve}
 * текущего запуска; они не являются semantic authority и не переживают новый
 * {@code link()}.</p>
 *
 * <p><strong>Активный Rule-set.</strong> Полное насыщение обходит все видимые
 * неудалённые Rule. Rule-scoped запуск начинает с переданного правила и
 * расширяет множество opposite-polarity native candidates, уже used Rules и
 * новых generated Rules текущей границы. Candidate indexes сокращают число
 * проверок, но итоговое сопоставление, hydration и semantic validation остаются
 * в Linker и Rule/Domain.</p>
 *
 * <p><strong>Порядок прохода.</strong> Каждый saturation pass дважды обходит
 * один и тот же Rule-set: сначала в строгом порядке убывания полного
 * {@code long Rule id}, затем в строгом порядке возрастания. Первый проход
 * распространяет факты в исторически установленном направлении, второй
 * замыкает обратные зависимости. Этот порядок является наблюдаемым инвариантом
 * алгоритма и не эквивалентен произвольной итерации {@link HashSet}.</p>
 *
 * <p><strong>Неподвижная точка.</strong> В начале каждого pass Linker сбрасывает
 * continuation signals {@code RuleFactory}, {@code TValueFactory},
 * {@code FValueFactory}, temporary hypothesis и final hypothesis. Следующий
 * pass выполняется только если хотя бы один из этих владельцев сообщил о
 * пережившем операцию эффекте. Signal не является самостоятельным результатом:
 * speculative mutation, откатанная локальным checkpoint, не должна оставлять
 * ложное требование продолжения.</p>
 *
 * <p><strong>Unification и checkpoints.</strong> {@code linkDomains()} для
 * каждого opposite-polarity candidate открывает парные локальные checkpoints
 * TValue/FValue. Новая подстановка или surviving ground used-state завершает
 * их commit; discarded substitutable/no-op match и failed candidate завершают
 * release. Вся пара должна быть закрыта в том же candidate-operation frame:
 * незакрытая рамка меняет смысл последующих nested commit/release и поэтому
 * является lifecycle defect, а не только retention.</p>
 *
 * <p><strong>Подстановки и совместимость.</strong> Переменные вращаются от
 * старших к младшим по их установленному порядку, а {@code isValidFor()}
 * проверяет совместимость текущей частичной подстановки с уже известными
 * {@code TSolve}. Это query-local join/filter; tuple-index ускоряет lookup, но
 * не создаёт решений и не отменяет каноническую проверку значений.</p>
 *
 * <p><strong>Побочные эффекты.</strong> Во время прохода Linker может создать
 * TValue/FValue, отметить Domain и Rule used/calculated/excluded, накопить
 * {@link Cause}, добавить TSolve, temporary/final hypotheses и classified
 * produced Domains. {@code linkDatabase()} выполняет retained legacy
 * branch-closure и только помечает materialization candidates;
 * {@code updateDatabase()} после завершения Rule-rotation применяет stamp,
 * довычисляет необходимые Function values и создаёт canonical stored Rule через
 * Domain/RuleFactory. Такое разделение препятствует изменению Rule-set внутри
 * незавершённого branch traversal.</p>
 *
 * <p><strong>Functions и system predicates.</strong> {@code calcFunctions()}
 * передаёт calculable occurrences в принадлежащий Mind {@code Calculator};
 * {@code checkSystem()} вычисляет algorithmic predicates, управляет calculated
 * marks и публикует полные transient solves. Linker не определяет binding mode
 * Function/UDF и не владеет canonical FValue identity — эти контракты остаются
 * у соответствующих фабрик и Calculator.</p>
 *
 * <p><strong>Ошибки и flood control.</strong> Ошибки одной terminal branch,
 * возникшие внутри domain/function/database evaluation, диагностируются в
 * {@code stderr} и делают результат этой branch ложным; владельцы локальных
 * checkpoints обязаны оставить state согласованным до выхода с исключением.
 * Превышение flood limit при вращении переменных выбрасывает
 * {@link RuntimeErrorException} наружу и не является частичным логическим
 * ответом.</p>
 *
 * <p><strong>Persistence и cleanup.</strong> Linker напрямую не открывает и не
 * обновляет storage. Durable publication проходит через factory/Domain paths,
 * а физический pack/cleanup выполняется владельцами Mind и фабрик. Сам Linker
 * сохраняет только runtime statistics и query-local acceleration structures,
 * которые переинициализируются на следующем запуске.</p>
 *
 * <p><strong>Concurrency и caller obligations.</strong> Mutable counters,
 * indexes, current substitutions и связанные Mind stores не делают Linker
 * independently thread-safe. Публичный query/transaction protocol должен
 * сериализовать работу через reservation/locking Mind и не вызывать один
 * экземпляр Linker конкурентно. Вызывающий код не должен менять Rule-set,
 * factory checkpoints или query-local solve maps во время насыщения и не должен
 * трактовать acceleration metadata либо statistics как часть логического
 * результата.</p>
 *
 * <p><strong>Замороженные границы.</strong> Исторический control flow
 * {@code linkDatabase()} и compatibility join {@code isValidFor()} являются
 * semantic kernels. Их переименование, декомпозиция или оптимизация допустимы
 * только после отдельного equivalence proof и regression gate; архитектурный
 * Javadoc сам по себе не разрешает такой рефакторинг.</p>
 *
 * @see Mind
 * @see Rule
 * @see Domain
 * @see TValue
 * @see FValue
 * @see LinkerStatistics
 */
public class Linker {

    private final transient Mind mind;
    private final LogStore log;

    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skippedPasses = 0;
    private final LinkerStatistics statistics = new LinkerStatistics();
    private int currentPass = 0;
    // Diagnostic frozen next-pass proposal; never controls execution.
    private Set<Long> shadowRules;
    private boolean shadowActivation;
    private boolean tracePairInputs;
    private boolean argumentPlan;

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

    /**
     * Создаёт per-Mind исполнитель насыщения.
     *
     * <p>Экземпляр заимствует Mind и его LogStore, но не приобретает
     * самостоятельного transaction или storage ownership. Caller не должен
     * передавать один Linker другому Mind или запускать его конкурентно.</p>
     *
     * @param mind активный runtime-контекст, чьи фабрики и query-local stores
     *             используются во всех последующих вызовах
     */
    public Linker(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }

    static int compareRuleIdsDescending(IRule left, IRule right) {
        return Long.compare(right.getId(), left.getId());
    }

    static int compareRuleIdsAscending(IRule left, IRule right) {
        return Long.compare(left.getId(), right.getId());
    }

    /**
     * Возвращает отделённый снимок диагностических счётчиков последнего
     * запуска Linker.
     *
     * <p>Снимок не является частью логического ответа, не владеет runtime
     * объектами Mind и не изменяется следующими проходами. Значения пригодны
     * для profiling/qualification, но не для управления семантикой запроса.</p>
     *
     * @return независимый snapshot текущей статистики
     */
    public LinkerStatistics snapshotStatistics() {
        return statistics.snapshot();
    }

    private void clearSolveIndex() {
        lastSolveVersion = Long.MIN_VALUE;
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

    private long lastSolveVersion = Long.MIN_VALUE;

    private void synchronizeSolveIndex() throws Exception {
        statistics.recordSolveSync(0);
        boolean enabled = Boolean.getBoolean("kanger.experiment.versionedSolveSync");
        boolean exposed = mind.ruleSolvesExposed();
        long version = mind.ruleSolvesVersion();
        if (enabled && !exposed && version == lastSolveVersion) {
            statistics.recordSolveSync(2);
            if (Boolean.getBoolean("kanger.experiment.verifySolveSync")
                    || "factory-verify".equals(System.getProperty("kanger.experiment.latent"))) {
                Map<TVariableSet, Integer> before = new HashMap<>(indexedSolveCounts);
                int indexedBefore = indexedSolves.size();
                synchronizeSolveIndexReference(true);
                if (!before.equals(indexedSolveCounts) || indexedBefore != indexedSolves.size())
                    throw new AssertionError("Solve index changed during proposed unchanged-version skip");
            }
            return;
        }
        statistics.recordSolveSync(1);
        if (enabled && exposed) statistics.recordSolveSync(3);
        synchronizeSolveIndexReference(false);
        lastSolveVersion = version;
    }

    private void synchronizeSolveIndexReference(boolean verification) throws Exception {
        boolean profile = statistics.profileSolveSync();
        long groups = 0, added = 0, tuples = 0, unchanged = 0;
        for (Map.Entry<TVariableSet, List<TSolve>> entry : mind.ruleSolvesInternal().entrySet()) {
            int indexed = indexedSolveCounts.containsKey(entry.getKey())
                    ? indexedSolveCounts.get(entry.getKey()) : 0;
            List<TSolve> solves = entry.getValue();
            if (profile) {
                ++groups;
                tuples += solves.size();
                if (indexed == solves.size()) ++unchanged;
            }
            for (int i = indexed; i < solves.size(); ++i) {
                if (profile) ++added;
                indexSolve(solves.get(i));
            }
            indexedSolveCounts.put(entry.getKey(), solves.size());
        }
        if (profile) statistics.recordSolveSyncWork(verification, groups, added, tuples, unchanged);
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
            Map<DomainKey, List<IRule>> index,
            Map<List<IRule>, Map<Long, Integer>> positions) throws Exception {
        statistics.recordCandidateSelection(0, 1);
        if (tree.size() != 1) {
            statistics.recordCandidateSelection(1, 1);
            return Collections.emptyList();
        }
        Domain slave = tree.get(0);
        List<IRule> candidates = index.get(
                new DomainKey(slave.getPredicateId(), !slave.isAntc()));
        if (candidates == null || candidates.isEmpty()) {
            statistics.recordCandidateSelection(2, 1);
            return Collections.emptyList();
        }

        long resolveStart = statistics.stageClock();
        List<IRule> resolved = mind.getRules().findByResolvedDomain(
                slave, !slave.isAntc(), statistics.resolvedProfileSink());
        statistics.finishStage(5, resolveStart);
        statistics.recordCandidateSelection(3, resolved.size());
        if (resolved.isEmpty()) {
            return Collections.emptyList();
        }

        if (positions != null && resolved.size() < candidates.size()) {
            Map<Long, Integer> rank = positions.get(candidates);
            if (rank == null) {
                rank = new HashMap<>();
                for (int i = 0; i < candidates.size(); i++) rank.put(candidates.get(i).getId(), i);
                positions.put(candidates, rank);
                statistics.recordCandidateSelection(6, candidates.size());
            }
            SortedSet<Integer> retained = new TreeSet<>();
            for (IRule candidate : resolved) {
                Integer position = rank.get(candidate.getId());
                if (position != null) retained.add(position);
            }
            statistics.recordCandidateSelection(7, resolved.size());
            List<IRule> filtered = new ArrayList<>(retained.size());
            for (int position : retained) filtered.add(candidates.get(position));
            if ("factory-verify".equals(System.getProperty("kanger.experiment.latent"))) {
                List<IRule> expected = intersectCandidateReference(candidates, resolved);
                if (expected.size() != filtered.size()) throw new AssertionError("Candidate intersection count mismatch");
                for (int i = 0; i < expected.size(); i++)
                    if (expected.get(i) != filtered.get(i)) throw new AssertionError("Candidate intersection order/identity mismatch");
            }
            statistics.recordCandidateSelection(5, filtered.size());
            return filtered;
        }
        statistics.recordCandidateSelection(4, candidates.size());
        List<IRule> filtered = intersectCandidateReference(candidates, resolved);
        statistics.recordCandidateSelection(5, filtered.size());
        return filtered;
    }

    private List<IRule> intersectCandidateReference(List<IRule> candidates, List<IRule> resolved) {
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

    private Map<DomainKey, List<IRule>> buildFactoryDomainIndex(Collection<IRule> rules) throws Exception {
        final Map<Long, Integer> rank = new HashMap<>();
        Map<Long, IRule> active = new HashMap<>();
        for (IRule rule : rules) {
            rank.put(rule.getId(), rank.size());
            active.put(rule.getId(), rule);
        }
        Map<DomainKey, List<IRule>> result = new HashMap<>();
        for (boolean antc : new boolean[]{false, true}) {
            for (Map.Entry<Long, Set<Long>> entry : mind.getRules().snapshotDomainRuleIds(antc, active.keySet()).entrySet()) {
                List<IRule> bucket = new ArrayList<>();
                for (long id : entry.getValue()) bucket.add(active.get(id));
                Collections.sort(bucket, new Comparator<IRule>() {
                    @Override public int compare(IRule a, IRule b) {
                        return Integer.compare(rank.get(a.getId()), rank.get(b.getId()));
                    }
                });
                result.put(new DomainKey(entry.getKey(), antc), bucket);
            }
        }
        return result;
    }

    private void verifyFactoryDomainIndex(Collection<IRule> rules, Map<DomainKey, List<IRule>> result) throws Exception {
        if ("factory-verify".equals(System.getProperty("kanger.experiment.latent"))) {
            Map<DomainKey, List<IRule>> reference = buildDomainIndex(rules);
            if (!reference.keySet().equals(result.keySet())) throw new AssertionError("Factory candidate signature mismatch");
            for (DomainKey key : reference.keySet()) {
                List<IRule> expected = reference.get(key), actual = result.get(key);
                if (expected.size() != actual.size()) throw new AssertionError("Factory candidate count mismatch");
                for (int i = 0; i < expected.size(); i++)
                    if (expected.get(i) != actual.get(i)) throw new AssertionError("Factory candidate identity/order mismatch");
            }
        }
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

    /**
     * Выполняет насыщение принадлежащего экземпляру Mind до неподвижной точки.
     *
     * <p>Перед первым pass очищаются query-local used/excluded/calculated,
     * flood, TSolve-index и statistics state. При {@code rule == null}
     * обходятся все видимые неудалённые Rule. Ненулевой seed ограничивает
     * начальный набор переданным Rule и транзитивно добавляемыми
     * opposite-polarity, already-used и новыми generated кандидатами.</p>
     *
     * <p>Метод координирует Rule/TValue/FValue actions, функции, системные
     * предикаты, hypotheses и отложенную materialization. Он не открывает
     * отдельную пользовательскую транзакцию: caller обязан вызвать его внутри
     * корректно зарезервированного Mind workflow. Исключение, включая flood
     * control, не означает частичный логический результат.</p>
     *
     * @param rule seed Rule для локализованного насыщения или {@code null}
     *             для полного видимого Rule-set
     * @param logging {@code true}, если pass/timing и semantic events должны
     *                публиковаться в LogStore Mind
     * @throws Exception при ошибке unification, вычисления, materialization
     *                   или превышении flood limit
     */
    public void link(Rule rule, boolean logging) throws Exception {
        occurrenceObservation = Boolean.getBoolean("kanger.experiment.profileOccurrences") ? new long[5] : null;
        rebindObservation = Boolean.getBoolean("kanger.experiment.profileRebinding") ? new long[3] : null;
        pairPhases = Boolean.getBoolean("kanger.experiment.profilePairPhases") ? new long[6] : null;
        long invocationStart = statistics.stageClock();
        boolean completed = false;
        try {
            linkMeasured(rule, logging);
            completed = true;
        } finally {
            statistics.finishStage(6, invocationStart);
            if (pairPhases != null)
                System.err.println("PAIR_PHASES completed=" + completed + " counts=" + java.util.Arrays.toString(pairPhases));
            if (rebindObservation != null)
                System.err.println("REBIND_OBSERVATION completed=" + completed
                        + " counts=" + java.util.Arrays.toString(rebindObservation));
            if (occurrenceObservation != null)
                System.err.println("OCCURRENCE_OBSERVATION pass=" + mind.getQueryPass()
                        + " completed=" + completed + " counts=" + java.util.Arrays.toString(occurrenceObservation));
            if (Boolean.getBoolean("kanger.experiment.traceInvocations"))
                System.err.println("LINK_INVOCATION completed=" + completed
                        + " pass=" + mind.getQueryPass()
                        + " stages=" + java.util.Arrays.toString(statistics.getStageNanos())
                        + " resolved=" + java.util.Arrays.toString(statistics.getResolvedProfile())
                        + " sync=" + java.util.Arrays.toString(statistics.getSolveSync())
                        + " syncWork=" + java.util.Arrays.toString(statistics.getSolveSyncWork()));
        }
    }

    private void linkMeasured(Rule rule, boolean logging) throws Exception {

        mind.getExcludedDomains().clear();
        mind.getUsedDomains().clear();
        mind.getCalculatedDomains().clear();
        mind.getUsedRules().clear();
        mind.getFloodControl().clear();

        mind.ruleSolvesInternal().clear(); // clearSolveIndex below resets the sync baseline too.
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
        final boolean traceBindings = Boolean.getBoolean("kanger.experiment.traceBindings");
        final boolean traceTuples = Boolean.getBoolean("kanger.experiment.traceTuples");
        shadowActivation = Boolean.getBoolean("kanger.experiment.shadowActivation");
        shadowRules = null;
        argumentPlan = Boolean.getBoolean("kanger.experiment.argumentPlan");
        if (argumentPlan && !System.getProperty("kanger.experiment.latent", "off").startsWith("factory"))
            throw new IllegalArgumentException("argumentPlan requires factory mode selected before Mind construction");
        tracePairInputs = Boolean.getBoolean("kanger.experiment.tracePairInputs")
                || Boolean.getBoolean("kanger.experiment.verifyPairOutputs");

        do {
            final Map<TVariable, Set<Long>> bindingsBefore = traceBindings || traceTuples || shadowActivation
                    ? observeBindings(observeConsumers()) : null;
            final Map<List<Long>, Set<Long>> tuplesBefore = traceTuples || shadowActivation ? observeTuples() : null;
            final Set<Long> rulesBefore = shadowActivation ? visibleRuleIds() : null;

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

            long prepareStart = statistics.stageClock();
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
                    return compareRuleIdsDescending(o1, o2);
                }
            });
            ruleList.addAll(ruleSet);
            Collections.sort(ruleList, new Comparator<IRule>() {
                @Override
                public int compare(IRule o1, IRule o2) {
                    return compareRuleIdsAscending(o1, o2);
                }
            });


            Map<DomainKey, List<IRule>> ascending = null, descending = null;
            if (Boolean.getBoolean("kanger.experiment.factoryCandidates")) {
                ascending = buildFactoryDomainIndex(ruleList);
                descending = new HashMap<>();
                for (Map.Entry<DomainKey, List<IRule>> entry : ascending.entrySet()) {
                    List<IRule> reversed = new ArrayList<>(entry.getValue());
                    Collections.reverse(reversed);
                    descending.put(entry.getKey(), reversed);
                }
            }
            statistics.finishStage(7, prepareStart);
            rotator(leftList, causes, logging, descending);
            rotator(ruleList, causes, logging, ascending);
            if (traceBindings) recordBindingChanges(bindingsBefore);
            if (traceTuples) recordTupleChanges(tuplesBefore, bindingsBefore);
            if (shadowActivation) {
                int covered = 0;
                for (IRule candidate : ruleSet)
                    if (shadowRules == null || shadowRules.contains(candidate.getId())) ++covered;
                statistics.recordActivationTrace("pass=" + currentPass + ",reference-rules=" + ruleSet.size()
                        + ",covered=" + covered
                        + ",proposal=" + (shadowRules == null ? "ALL" : shadowRules.toString()));
                shadowRules = proposeActivation(bindingsBefore, tuplesBefore, rulesBefore);
            }
            statistics.recordPassActions(mind.getRules().isAction(),
                    mind.getTValues().isAction(), mind.getFValues().isAction(),
                    mind.getTempHypothesis().isAction(), mind.getHypothesis().isAction());

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

    /** Diagnostic visible Rule identities, including ground rules. */
    private Set<Long> visibleRuleIds() throws Exception {
        Set<Long> ids = new TreeSet<>();
        for (IRule rule : mind.getRules()) if (!rule.isDeleted(mind)) ids.add(rule.getId());
        return ids;
    }

    private Set<Long> proposeActivation(Map<TVariable, Set<Long>> beforeBindings,
            Map<List<Long>, Set<Long>> beforeTuples, Set<Long> beforeRules) throws Exception {
        Map<TVariable, Set<Long>> consumers = observeConsumers();
        Map<TVariable, Set<Long>> bindings = observeBindings(consumers);
        Set<Long> seed = visibleRuleIds();
        seed.removeAll(beforeRules);
        for (Map.Entry<TVariable, Set<Long>> entry : bindings.entrySet()) {
            if (!entry.getValue().equals(beforeBindings.getOrDefault(entry.getKey(), Collections.<Long>emptySet())))
                seed.addAll(consumers.get(entry.getKey()));
        }
        Map<List<Long>, Set<Long>> tuples = observeTuples();
        for (Map.Entry<List<Long>, Set<Long>> entry : tuples.entrySet()) {
            if (!beforeTuples.containsKey(entry.getKey())) seed.addAll(entry.getValue());
        }
        for (Map.Entry<List<Long>, Set<Long>> entry : beforeTuples.entrySet()) {
            if (!tuples.containsKey(entry.getKey())) seed.addAll(entry.getValue());
        }
        // Compare one hop with component closure, without changing execution.
        boolean closure = Boolean.getBoolean("kanger.experiment.shadowClosure");
        Set<Long> result = new TreeSet<>(seed);
        int size;
        do {
            size = result.size();
            Set<DomainKey> opposite = new HashSet<>();
            for (IRule rule : mind.getRules()) {
                if (rule.isDeleted(mind) || !result.contains(rule.getId())) continue;
                for (List<Domain> branch : ((Rule) rule).getTree()) for (Domain d : branch)
                    opposite.add(new DomainKey(d.getPredicateId(), !d.isAntc()));
            }
            for (IRule rule : mind.getRules()) {
                if (rule.isDeleted(mind)) continue;
                for (List<Domain> branch : ((Rule) rule).getTree()) for (Domain d : branch)
                    if (opposite.contains(new DomainKey(d.getPredicateId(), d.isAntc()))) result.add(rule.getId());
            }
        } while (closure && result.size() != size);
        return result;
    }

    private long observedTupleCount() {
        long count = 0;
        for (List<TSolve> solves : mind.ruleSolvesInternal().values()) count += solves.size();
        return count;
    }

    /** Diagnostic canonical tuple keys and current argument consumers. */
    private Map<List<Long>, Set<Long>> observeTuples() throws Exception {
        Map<TVariable, Set<Long>> consumers = observeConsumers();
        Map<List<Long>, Set<Long>> result = new LinkedHashMap<>();
        for (List<TSolve> solves : mind.ruleSolvesInternal().values()) {
            for (TSolve solve : solves) {
                List<Long> ids = new ArrayList<>();
                Set<Long> rules = new TreeSet<>();
                for (TValue value : solve.getSolve()) {
                    ids.add(value.getId());
                    rules.addAll(consumers.getOrDefault(value.getTVar(mind), Collections.<Long>emptySet()));
                }
                Collections.sort(ids);
                result.put(ids, rules);
            }
        }
        return result;
    }

    private void recordTupleChanges(Map<List<Long>, Set<Long>> before,
                                    Map<TVariable, Set<Long>> bindingsBefore) throws Exception {
        Map<List<Long>, Set<Long>> after = observeTuples();
        Set<Long> oldValues = new HashSet<>();
        for (Set<Long> values : bindingsBefore.values()) oldValues.addAll(values);
        List<String> rows = new ArrayList<>();
        for (Map.Entry<List<Long>, Set<Long>> tuple : after.entrySet()) {
            if (!before.containsKey(tuple.getKey())) {
                rows.add("pass=" + currentPass + ",added=" + tuple.getKey()
                        + ",all-values-preexisting=" + oldValues.containsAll(tuple.getKey())
                        + ",consumers=" + tuple.getValue());
            }
        }
        for (Map.Entry<List<Long>, Set<Long>> tuple : before.entrySet()) {
            if (!after.containsKey(tuple.getKey())) rows.add("pass=" + currentPass
                    + ",removed=" + tuple.getKey() + ",consumers-before=" + tuple.getValue());
        }
        Collections.sort(rows);
        for (String row : rows) statistics.recordTupleTrace(row);
    }

    /** Diagnostic scan of the same argument occurrences used by rotator. */
    private Map<TVariable, Set<Long>> observeConsumers() throws Exception {
        Map<TVariable, Set<Long>> result = new TreeMap<>();
        for (IRule rule : mind.getRules()) {
            if (rule.isDeleted(mind)) continue;
            for (List<Domain> branch : ((Rule) rule).getTree()) {
                for (Domain domain : branch) {
                    for (TVariable variable : domain.getArguments().getTVariables(mind)) {
                        Set<Long> rules = result.get(variable);
                        if (rules == null) {
                            rules = new TreeSet<>();
                            result.put(variable, rules);
                        }
                        rules.add(rule.getId());
                    }
                }
            }
        }
        return result;
    }

    private Map<TVariable, Set<Long>> observeBindings(Map<TVariable, Set<Long>> consumers) throws Exception {
        Map<TVariable, Set<Long>> result = new TreeMap<>();
        for (TVariable variable : consumers.keySet()) {
            final Set<Long> ids = new TreeSet<>();
            mind.getTValues().forEach(variable, new IReactor() {
                @Override public Object run(Object value) {
                    ids.add(((TValue) value).getId());
                    return true;
                }
            });
            result.put(variable, ids);
        }
        return result;
    }

    private void recordBindingChanges(Map<TVariable, Set<Long>> before) throws Exception {
        Map<TVariable, Set<Long>> consumers = observeConsumers();
        Map<TVariable, Set<Long>> after = observeBindings(consumers);
        Set<TVariable> variables = new TreeSet<>(before.keySet());
        variables.addAll(after.keySet());
        for (TVariable variable : variables) {
            Set<Long> added = new TreeSet<>(after.getOrDefault(variable, Collections.<Long>emptySet()));
            added.removeAll(before.getOrDefault(variable, Collections.<Long>emptySet()));
            Set<Long> removed = new TreeSet<>(before.getOrDefault(variable, Collections.<Long>emptySet()));
            removed.removeAll(after.getOrDefault(variable, Collections.<Long>emptySet()));
            if (added.isEmpty() && removed.isEmpty()) continue;
            Set<Long> direct = consumers.getOrDefault(variable, Collections.<Long>emptySet());
            Set<Long> tupleConsumers = new TreeSet<>();
            for (Map.Entry<TVariableSet, List<TSolve>> entry : mind.ruleSolvesInternal().entrySet()) {
                if (!entry.getKey().contains(variable)) continue;
                for (TSolve solve : entry.getValue()) {
                    for (TValue value : solve.getSolve()) {
                        tupleConsumers.addAll(consumers.getOrDefault(value.getTVar(mind), Collections.<Long>emptySet()));
                    }
                }
            }
            tupleConsumers.removeAll(direct);
            statistics.recordBindingTrace("pass=" + currentPass + ",variable=" + variable.getId()
                    + ",owner=" + variable.getRuleId() + ",added=" + added + ",removed=" + removed
                    + ",consumers=" + direct + ",tuple-extra=" + tupleConsumers);
        }
    }

    private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging,
                            final Map<DomainKey, List<IRule>> preparedIndex) throws Exception {

        boolean used = false;
        long setupStart = statistics.stageClock();
        final Map<DomainKey, List<IRule>> domainIndex = preparedIndex == null ? buildDomainIndex(ruleList) : preparedIndex;
        final Map<List<IRule>, Map<Long, Integer>> candidatePositions = Boolean.getBoolean("kanger.experiment.indexedIntersection")
                ? new IdentityHashMap<List<IRule>, Map<Long, Integer>>() : null;
        if (preparedIndex != null) verifyFactoryDomainIndex(ruleList, domainIndex);
        // Experimental, discardable snapshot. Default remains the reference path.
        final String latentMode = System.getProperty("kanger.experiment.latent", "off");
        final boolean factoryLatent = "factory".equals(latentMode) || "factory-verify".equals(latentMode);
        if (!"off".equals(latentMode) && !"index".equals(latentMode) && !"verify".equals(latentMode) && !factoryLatent) {
            throw new IllegalArgumentException("Unknown latent experiment mode: " + latentMode);
        }
        final Map<IRule, Map<DomainKey, List<Domain>>> latent = factoryLatent
                && (occurrenceObservation != null || Boolean.getBoolean("kanger.experiment.reuseOccurrenceLists"))
                ? new IdentityHashMap<IRule, Map<DomainKey, List<Domain>>>()
                : ("off".equals(latentMode) || factoryLatent ? null : buildLatentDomains(ruleList));
        statistics.finishStage(8, setupStart);

        for (IRule r : ruleList) {
            long ruleStart = statistics.stageClock();
            final boolean outsideProposal = shadowActivation && shadowRules != null && !shadowRules.contains(r.getId());
            final long tuplesAtEntry = outsideProposal ? observedTupleCount() : 0;
            final long attemptsAtEntry = outsideProposal ? statistics.getUnificationAttempts() : 0;
            final long valuesAtEntry = outsideProposal ? statistics.getNewTValues() : 0;

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
            statistics.finishStage(9, ruleStart);

            for (List<Domain> tree : ((Rule) r).getTree()) {

                statistics.incrementBranchVisits();
                final List<Domain> t = tree;

                long rotationStart = statistics.stageClock();
                rotateVariables(tvars, tvars, new IReactor() {
                    @Override
                    public Object run(Object o) {
                        long callbackStart = statistics.stageClock();
                        statistics.incrementTerminalRotations();
                        boolean result = false;
                        try {
                            long stageStart = statistics.stageClock();
                            Collection<IRule> candidates = selectDomainCandidates(t, domainIndex, candidatePositions);
                            statistics.finishStage(0, stageStart);
                            stageStart = statistics.stageClock();
                            if (linkDomains(t, candidates, causes, logging,
                                    latent, "verify".equals(latentMode) || "factory-verify".equals(latentMode), factoryLatent)) {
                                result = true;
                            }
                            statistics.finishStage(1, stageStart);
                            statistics.incrementFunctionEvaluations();
                            stageStart = statistics.stageClock();
                            if (calcFunctions(t, causes, logging)) {
                                result = true;
                            }
                            statistics.finishStage(2, stageStart);
                            statistics.incrementDatabaseEvaluations();
                            stageStart = statistics.stageClock();
                            if (linkDatabase(t, causes, tvars, logging)) {
                                result = true;
                            }
                            statistics.finishStage(3, stageStart);
                        } catch (Exception e) {
                            System.err.println(new Date());
                            e.printStackTrace(System.err);
                            result = false;
                        }

                        statistics.finishStage(11, callbackStart);
                        return result;
                    }
                });
                statistics.finishStage(10, rotationStart);
            }

            long updateStart = statistics.stageClock();
            updateDatabase(logging);
            statistics.finishStage(4, updateStart);
            if (outsideProposal) {
                long tuplesAdded = observedTupleCount() - tuplesAtEntry;
                long attempts = statistics.getUnificationAttempts() - attemptsAtEntry;
                long values = statistics.getNewTValues() - valuesAtEntry;
                if (attempts != 0 || tuplesAdded != 0 || values != 0)
                    statistics.recordActivationTrace("outside:pass=" + currentPass + ",rule=" + r.getId()
                            + ",unifications=" + attempts + ",tvalue-events=" + values + ",tuple-delta=" + tuplesAdded);
            }
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
                    long validityStart = statistics.stageClock();
                    boolean valid = isValidFor(base.tailSet(t));
                    statistics.finishStage(12, validityStart);
                    if (valid) {
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
     * Проверяет, совместима ли текущая частичная подстановка с уже
     * зарегистрированными TSolve.
     *
     * <p>{@code tail} является непустым suffix вращаемых TVariable; первая
     * переменная уже имеет current TValue. Для каждого TVariableSet,
     * содержащего её, метод требует существования хотя бы одного TSolve,
     * совместимого со всеми уже назначенными переменными из suffix. Unary
     * tuple не добавляет межпеременного ограничения. Если релевантных
     * TVariableSet нет, частичная подстановка допустима.</p>
     *
     * <p>Это query-local join/filter, а не создание TValue/TSolve и не
     * доказательство Rule. Tuple index только ускоряет поиск и синхронно
     * достраивается из authoritative {@code mind.getRuleSolves()}.</p>
     *
     * @param tail непустой упорядоченный suffix переменных с установленными
     *             current bindings для уже вращаемой части
     * @return {@code true}, если ограничений нет или найден совместимый
     *         зарегистрированный tuple; иначе {@code false}
     * @throws Exception если current binding или tuple metadata не могут
     *                   быть разрешены в текущем Mind
     */
    private boolean isValidFor(SortedSet<TVariable> tail) throws Exception {
        long syncStart = statistics.stageClock();
        synchronizeSolveIndex();
        statistics.finishStage(13, syncStart);
        final TVariable t = tail.first();
        boolean found = false;
        boolean result = false;
        if (tail.size() > 1) {
            for (TVariableSet key : mind.ruleSolvesInternal().keySet()) {
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

    private Map<IRule, Map<DomainKey, List<Domain>>> buildLatentDomains(Collection<IRule> rules) throws Exception {
        Map<IRule, Map<DomainKey, List<Domain>>> result = new IdentityHashMap<>();
        for (IRule rule : rules) {
            Map<DomainKey, List<Domain>> buckets = new HashMap<>();
            for (List<Domain> branch : ((Rule) rule).getTree()) {
                for (Domain domain : branch) {
                    DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                    List<Domain> bucket = buckets.get(key);
                    if (bucket == null) { bucket = new ArrayList<>(); buckets.put(key, bucket); }
                    bucket.add(domain); // Preserve repeated occurrences and traversal order.
                }
            }
            result.put(rule, buckets);
        }
        return result;
    }

    // Diagnostic only: calls, requested slots, repeated calls/slots, changed ordered identities.
    private long[] occurrenceObservation;
    // Diagnostic only: reused lists, rebound occurrences, inclusive loop nanoseconds.
    private long[] rebindObservation;
    // Pair count; preparation, guards, substitution, completion, deferred tuple nanoseconds.
    private long[] pairPhases;

    private long finishPairPhase(int phase, long started) {
        if (pairPhases == null) return 0;
        long now = System.nanoTime();
        pairPhases[phase] += now - started;
        return now;
    }

    private List<List<Domain>> latentBranches(IRule rule, Domain slave,
            Map<IRule, Map<DomainKey, List<Domain>>> index, boolean verify, boolean factory) throws Exception {
        if (index == null && !factory) return ((Rule) rule).getTree();
        DomainKey key = index == null ? null : new DomainKey(slave.getPredicateId(), !slave.isAntc());
        Map<DomainKey, List<Domain>> seen = factory && index != null ? index.get(rule) : null;
        List<Domain> previous = seen == null ? null : seen.get(key);
        boolean reuse = factory && previous != null
                && Boolean.getBoolean("kanger.experiment.reuseOccurrenceLists");
        List<Domain> selected;
        if (reuse) {
            selected = previous;
            // Preserve eager rebinding, including duplicate occurrences, before pair checkpoints.
            long rebindStart = rebindObservation == null ? 0 : System.nanoTime();
            for (Domain domain : selected) domain.setMind(mind);
            if (rebindObservation != null) {
                rebindObservation[0]++;
                rebindObservation[1] += selected.size();
                rebindObservation[2] += System.nanoTime() - rebindStart;
            }
            if (verify) {
                List<Domain> reference = mind.getRules().getLatentDomainCandidates(rule, slave);
                if (reference.size() != selected.size()) throw new AssertionError("Reused occurrence count changed");
                for (int i = 0; i < reference.size(); i++)
                    if (reference.get(i) != selected.get(i)) throw new AssertionError("Reused occurrence identity/order changed");
            }
        } else {
            selected = factory ? mind.getRules().getLatentDomainCandidates(rule, slave) : index.get(rule).get(key);
        }
        if (selected == null) selected = Collections.emptyList();
        if (factory && index != null) {
            if (seen == null) { seen = new HashMap<>(); index.put(rule, seen); }
            seen.put(key, selected);
        }
        if (factory && occurrenceObservation != null) {
            occurrenceObservation[0]++;
            occurrenceObservation[1] += selected.size();
            if (previous != null) {
                occurrenceObservation[2]++;
                occurrenceObservation[3] += selected.size();
                boolean same = previous.size() == selected.size();
                if (same) for (int i = 0; i < selected.size(); i++)
                    if (previous.get(i) != selected.get(i)) { same = false; break; }
                if (!same) occurrenceObservation[4]++;
            }
        }
        if (verify) {
            List<Domain> reference = new ArrayList<>();
            for (List<Domain> branch : ((Rule) rule).getTree()) {
                for (Domain master : branch) {
                    if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {
                        reference.add(master);
                    }
                }
            }
            if (reference.size() != selected.size()) throw new AssertionError("Latent candidate count mismatch");
            for (int i = 0; i < reference.size(); i++) {
                if (reference.get(i) != selected.get(i)) throw new AssertionError("Latent candidate identity/order mismatch");
            }
        }
        return Collections.singletonList(selected);
    }

    /** Values exposed to rotation for variables occurring in this pair. */
    private Set<Long> observedPairBindings(Domain master, Domain slave) throws Exception {
        final Set<Long> ids = new HashSet<>();
        Set<TVariable> variables = new TreeSet<>();
        variables.addAll(master.getArguments().getTVariables(mind));
        variables.addAll(slave.getArguments().getTVariables(mind));
        for (TVariable variable : variables) mind.getTValues().forEach(variable, new IReactor() {
            @Override public Object run(Object value) {
                ids.add(((TValue) value).getId());
                return true;
            }
        });
        return ids;
    }

    private long observedEntryCount(Map<?, ? extends Collection<?>> map) {
        long count = 0;
        for (Collection<?> entries : map.values()) count += entries.size();
        return count;
    }

    /** Argument projection only: not a complete semantic memoization key. */
    private String observedPairInput(Domain master, Domain slave, boolean carriedResult) throws Exception {
        StringBuilder key = new StringBuilder().append(carriedResult);
        for (Domain domain : Arrays.asList(master, slave)) {
            key.append('|').append(domain.getId());
            for (int i = 0; i < domain.getRange(); i++) {
                IArgument argument = domain.get(i);
                key.append(':').append(argument.getType()).append('=');
                if (argument.isEmpty(mind)) key.append("empty");
                else key.append(argument.getValue(mind).getId());
                if (argument.getType() == ArgumentType.TVARIABLE) {
                    TValue value = ((TVariable) argument.getObject(mind)).getCurrent();
                    key.append('@').append(value == null ? "none" : Long.toString(value.getId()));
                }
            }
        }
        return key.toString();
    }

    private String observedSubstitution(TValue[] values) {
        List<String> ids = new ArrayList<>();
        for (TValue value : values) ids.add(value == null ? "null" : Long.toString(value.getId()));
        return ids.toString();
    }

    private boolean plannedVariable(Domain domain, String plan, int position) {
        return plan == null ? domain.get(position).getType() == ArgumentType.TVARIABLE : plan.charAt(position) == 'v';
    }

    private boolean linkDomains(List<Domain> treeSlave, Collection<IRule> ruleList, Map<IRule, Set<Cause>> causes, boolean logging,
            Map<IRule, Map<DomainKey, List<Domain>>> latent, boolean verify, boolean factory) throws Exception {

        Map<Solve, List<DeferredSolveCandidate>> variants = new HashMap<>();
        boolean result = false;

        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (IRule rule : ruleList) {
                    statistics.incrementCandidateRuleVisits();
                    for (List<Domain> treeMaster : latentBranches(rule, slave, latent, verify, factory)) {
                        for (Domain master : treeMaster) {
                            statistics.incrementDomainPairs();
                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {
                                long phaseStarted = pairPhases == null ? 0 : System.nanoTime();
                                if (pairPhases != null) pairPhases[0]++;
                                String masterPlan = argumentPlan ? mind.getRules().getLatentArgumentPlan(master) : null;
                                String slavePlan = argumentPlan ? mind.getRules().getLatentArgumentPlan(slave) : null;
                                long operationId = statistics.incrementUnificationAttempts(
                                        currentPass == 1, rule.isQuery(), rule.isGenerated());
                                if (tracePairInputs) statistics.observePairInput(
                                        observedPairInput(master, slave, result), currentPass, operationId);
                                Set<Long> bindingsAtEntry = tracePairInputs ? observedPairBindings(master, slave) : null;
                                long usedAtEntry = tracePairInputs ? observedEntryCount(mind.getUsedDomains()) : 0;
                                long excludedAtEntry = tracePairInputs ? observedEntryCount(mind.getExcludedDomains()) : 0;
                                long rulesUsedAtEntry = tracePairInputs ? observedEntryCount(mind.getUsedRules()) : 0;
                                boolean pairCommitted = false;
                                boolean resultAtEntry = result;
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
                                phaseStarted = finishPairPhase(1, phaseStarted);

                                for (int i = 0; i < master.getRange(); ++i) {
                                    if (plannedVariable(master, masterPlan, i)) {
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
                                    } else {
                                        blockRight = true;
                                    }
                                }

                                for (int i = 0; i < slave.getRange(); ++i) {
                                    if (plannedVariable(slave, slavePlan, i)) {
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
                                    } else {
                                        blockLeft = true;
                                    }
                                }

                                phaseStarted = finishPairPhase(2, phaseStarted);
                                if (success) {
                                    for (int i = 0; i < slave.getRange(); ++i) {

                                        // Подстановка снизу вверх
                                        if (!blockRight) {
                                            if (plannedVariable(master, masterPlan, i) /*&& master.get(i).isEmpty(mind)*/) {
                                                if (!slave.get(i).isEmpty(mind)) {
                                                    Term tm = (Term) slave.get(i).getValue(mind);
                                                    TVariable t = (TVariable) master.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && slave.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind, master.getRuleId());
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
                                            if (plannedVariable(slave, slavePlan, i) && slave.get(i).isEmpty(mind)) {
                                                if (!master.get(i).isEmpty(mind)) {

                                                    Term tm = (Term) master.get(i).getValue(mind);
                                                    TVariable t = (TVariable) slave.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && master.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        Term tn = (Term) tm.getChild(mind, slave.getRuleId());
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

                                phaseStarted = finishPairPhase(3, phaseStarted);
                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();
                                        pairCommitted = true;
                                    } else if (!master.isSubstitutable() && !slave.isSubstitutable()) {
                                        ++solvedPasses;
                                        operationEffects |= LinkerStatistics.EFFECT_USED_ONLY;
                                        master.setUsed(mind);
                                        slave.setUsed(mind);
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();
                                        pairCommitted = true;
                                    } else {
                                        ++dumpedPasses;
                                        mind.getTValues().release();
                                        mind.getFValues().release();
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
                                if (tracePairInputs) statistics.observePairEffects(operationId, operationEffects);
                                if (tracePairInputs) {
                                    statistics.observePairOutput(operationId,
                                            "result=" + result + ",commit=" + pairCommitted + ",success=" + success
                                            + ",master=" + observedSubstitution(substMaster)
                                            + ",slave=" + observedSubstitution(substSlave));
                                    Set<Long> bindingsAfter = observedPairBindings(master, slave);
                                    Set<Long> added = new HashSet<>(bindingsAfter);
                                    added.removeAll(bindingsAtEntry);
                                    bindingsAtEntry.removeAll(bindingsAfter);
                                    statistics.observePairBoundary(operationId, pairCommitted, added.size(), bindingsAtEntry.size(),
                                            observedEntryCount(mind.getUsedDomains()) - usedAtEntry,
                                            observedEntryCount(mind.getExcludedDomains()) - excludedAtEntry,
                                            observedEntryCount(mind.getUsedRules()) - rulesUsedAtEntry,
                                            !resultAtEntry && result, result);
                                }
                                finishPairPhase(4, phaseStarted);
                            }
                        }
                    }
                }
            }

            long deferredStarted = pairPhases == null ? 0 : System.nanoTime();
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
                    long tupleCount = tracePairInputs ? observedTupleCount() : 0;
                    mind.addTSolve(list);
                    if (tracePairInputs && observedTupleCount() > tupleCount)
                        statistics.observePairNewTuple(candidate.operationId);
                }
            }
            finishPairPhase(5, deferredStarted);

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
     * Классифицирует terminal branch и регистрирует её отложенный
     * семантический эффект.
     *
     * <p>Историческое имя не обозначает физический database I/O. После
     * {@link #checkSystem(List, boolean)} метод собирает текущий solve,
     * разделяет Domains на stored, calculated, excluded, assumed и обычные
     * candidates, после чего либо отмечает ровно выводимый Domain как
     * produced, либо добавляет альтернативные temporary hypotheses.
     * Canonical Rule создаётся позже в {@code updateDatabase()}, после
     * завершения branch traversal.</p>
     *
     * <p>Возвращаемое значение сообщает только о новом produced-domain
     * эффекте, который требует продолжения saturation. Создание одной
     * hypothesis само по себе не превращает результат в {@code true}; её
     * собственный action signal учитывается владельцем store. Метод может
     * изменять produced domains, causes, solves, calculated/used marks,
     * temporary hypotheses и diagnostic log.</p>
     *
     * @param tree terminal conjunction текущей Rule branch
     * @param causes накопленные provenance-связи по Rule
     * @param tvars переменные Rule, из current bindings которых строится
     *              solve для materialized результата
     * @param logging {@code true} для публикации classification/provenance
     *                событий в LogStore
     * @return {@code true}, если зарегистрирован новый produced Domain;
     *         иначе {@code false}
     * @throws Exception при ошибке разрешения Domain, system predicate,
     *                   provenance или hypothesis state
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


    /**
     * Вычисляет пустые calculable Function occurrences в переданных Domains.
     *
     * <p>Каждое подходящее вхождение очищается и передаётся Calculator
     * текущего Mind. Canonical FValue identity, UDF binding и invalidation
     * остаются обязанностью Calculator/FunctionFactory/FValueFactory. Метод
     * не выполняет отдельный saturation pass и не использует параметр
     * {@code causes}; параметр сохранён в исторической сигнатуре.</p>
     *
     * @param master Domains текущей branch
     * @param causes исторический параметр provenance; методом не изменяется
     * @param logging {@code true} для публикации вычислительных событий
     * @return {@code true}, если хотя бы одно вычисление создало новый
     *         surviving result
     * @throws Exception при ошибке разрешения или выполнения функции
     */
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

    /**
     * Выполняет системные предикаты branch и определяет, блокируют ли они
     * дальнейшую классификацию.
     *
     * <p>Успешные complete system Domains получают calculated mark. Если
     * любой системный предикат противоречит своей polarity либо после
     * частичного успеха остаётся невычисленный system Domain, все marks
     * текущей попытки снимаются и branch блокируется. Полные наборы current
     * TValue публикуются как TSolve только после согласованного успеха всей
     * системной части.</p>
     *
     * <p>{@code true} означает отсутствие доказанного блока, а не
     * обязательную вычисленность каждого ещё неполного предиката.</p>
     *
     * @param tree terminal conjunction, содержащая обычные и/или системные
     *             Domains
     * @param logging сохранённый orchestration flag; непосредственный вывод
     *                этим методом не производится
     * @return {@code false}, если системная часть отвергает branch;
     *         иначе {@code true}
     * @throws Exception при ошибке выполнения системного предиката или
     *                   регистрации TSolve
     */
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
