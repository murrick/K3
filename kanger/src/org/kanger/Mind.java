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

import org.kanger.calculator.Calculator;
import org.kanger.compiler.Compiller;
import org.kanger.compiler.Leaf;
import org.kanger.compiler.Parser;
import org.kanger.compiler.Token;
import org.kanger.enums.*;
import org.kanger.factory.*;
import org.kanger.exception.TransactionSettlementException;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;
import org.kanger.primitives.LogEntry;
import org.kanger.primitives.TVariableSet;
import org.kanger.stores.HypothesisStore;
import org.kanger.stores.LogStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;
import org.kanger.units.*;

import java.util.*;

/**
 * Активный логический контекст KANGER, объединяющий состояние знаний,
 * транзакционный уровень и временную рабочую область логического вывода.
 *
 * <p><strong>Архитектурная роль.</strong> Корневой {@code Mind} представляет
 * текущее пользовательское рабочее состояние, связанное с одним {@link User}.
 * Дочерний {@code Mind} представляет транзакционный overlay над родительским
 * уровнем: он наследует видимое состояние, накапливает изменения и завершает
 * их ровно одним из двух путей — {@link #commit(IMind)} или
 * {@link #release(IMind)}. Класс одновременно координирует фабрики единиц,
 * compiler/linker/analyzer pipeline и query-local stores, но не является
 * владельцем внешнего storage-модуля.</p>
 *
 * <p><strong>Владение и публикация.</strong> Корень создаётся вызывающим кодом
 * из явного {@link IUser}; дочерний уровень создаётся из явного родительского
 * {@link IMind} и связывается с ним через {@link #getNext()}. Все уровни одной
 * цепочки удерживают тот же {@code User}. Активный контекст передаётся явно;
 * создание {@code Mind} не публикует его через {@code User.currentMind} и не
 * использует этот compatibility slot для внутреннего lifecycle.</p>
 *
 * <p><strong>Транзакции.</strong> Создание дочернего уровня резервирует одну
 * незавершённую транзакцию у родителя. Успешный commit атомарно объединяет
 * factory overlays, deletion/restoration state и результат исполнения;
 * release переносит только диагностический и результатный runtime-state,
 * отбрасывая логические изменения. Любой путь завершения обязан снять ровно
 * одну reservation, включая отказ конструктора и исключения частичного
 * commit. Корневые {@code pack/update/flush} выполняются только после
 * завершения последней активной дочерней транзакции.</p>
 *
 * <p><strong>Persistence.</strong> Дочерний {@code Mind} самостоятельно не
 * является durable state и не записывает storage. Persistence достигается на
 * корневом уровне через фабрики, базы, принадлежащие {@link User}, и его
 * {@link User#flush()} после достижения transaction quiescence. Поэтому
 * наличие объекта или успешное выполнение запроса само по себе не означает,
 * что существует отдельная persistent-версия этого {@code Mind}.</p>
 *
 * <p><strong>Временное состояние.</strong> Hypotheses, solutions, values,
 * C-variable links, domain/rule usage maps, flood control и linker indexes
 * принадлежат конкретному runtime-контексту. {@link #clearMind()} удаляет эти
 * ссылки и переинициализирует private linker state; закрытие или очистку всей
 * цепочки координирует {@code User}. Persistent фабричное состояние и
 * query-local caches имеют разные lifecycle и не должны смешиваться.</p>
 *
 * <p><strong>Concurrency и инварианты.</strong> Внутренний locker защищает
 * композицию транзакций, transaction reservation и локальные overlays; он не
 * превращает весь объект в свободно разделяемый immutable snapshot. Код,
 * взаимодействующий с внутренними блокировками фабрик, обязан сохранять
 * установленный lock order и не выполнять Mind-dependent hydration под
 * чужими metadata locks. Цепочка {@code next} должна оставаться конечной, а
 * каждый дочерний уровень — завершаться ровно один раз.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Вызывающая сторона
 * должна удерживать фактически актуальный {@code IMind}, учитывать значение,
 * возвращаемое storage lifecycle-операциями, и парно завершать каждый
 * созданный дочерний уровень. {@code Mind} нельзя трактовать как независимую
 * копию базы, скрытый thread-local context или замену владельцу ресурсов
 * {@link User}.</p>
 *
 * @see User
 * @see IMind
 */
public class Mind implements IMind {

    private static final boolean DEBUG_DISABLE_FALSE_CHECK = false;
    private static final int FLOOD_CONTROL_LIMIT = 10000;

    private final Object locker = new Object();
    //
    private long id = 0;
    private IMind next = null;
    //
    private final Map<ITerm, ITerm> cvarChilds = new HashMap<>();
    private final Map<ITerm, Map<Long, ITerm>> cvarChildrenByRule = new HashMap<>();
    private final Map<ITerm, ITerm> cvarParents = new HashMap<>();
    private final Map<UnitType, Set<Long>> deleted = new HashMap<>();
    private final Map<UnitType, Set<Long>> restored = new HashMap<>();
    private PortableMindLayer portableRebaseResidue = PortableMindLayer.empty();
    private final Map<Long, Set<IRule>> usedRules = new HashMap<>();
    private final Map<Domain, Set<ArgumentsList>> usedDomains = new HashMap<>();
    private final Map<Domain, Set<ArgumentsList>> excludedDomains = new HashMap<>();
    private final Map<Domain, List<List<ITerm>>> calculatedDomains = new HashMap<>();
    private final Map<Domain, List<List<ITerm>>> producedDomains = new HashMap<>();
    private final Map<Domain, Map<ArgumentsList, Set<ICause>>> domainCauses = new HashMap<>();
    private final Map<Domain, Map<ArgumentsList, SortedSet<TValue>>> domainSolves = new HashMap<>();
    private final Map<TVariable, Set<TValue>> queryValues = new HashMap<>();
    private final Map<TVariableSet, List<TSolve>> ruleSolves = new LinkedHashMap<>();
    private long ruleSolvesVersion;
    private boolean ruleSolvesExposed;
    //
    private final Map<TVariable, long[]> floodControl = new HashMap<>();
    private final Stack<Integer> debugLevelStack = new Stack<>();
    //
    private DictionaryFactory terms = null;                    // Словарь констант
    private PredicateFactory predicates = null;                 // Предикаты
    private DomainFactory domains = null;                          // Список доменов
    private RuleFactory rules = null;                             // Список правил
    private TVariableFactory tVars = null;                      // t-переменные
    private TValueFactory tValues = null;                          // Подставленные значения
    private FunctionFactory functions = null;                    // Функции
    private FValueFactory fValues = null;                          // Решения функций
    private CommentFactory comments = null;
    private LibraryFactory library = null;                            // Системная библиотека функций и предикатов
    //
    private HypothesisStore hypothesis = null;                                // Список гипотез
    private HypothesisStore tempHypothesis = null;                                // Список гипотез
    //
    private SolutionsStore solves = null;                         // Список решений
    private ValuesStore values = null;                               // Список значений
    private LogStore log = null;                                        // Протокол вывода

    private Calculator calculator = null;                             // Калькулятор
    private Analyzer analyzer = null;                                   // Анализатор
    private Compiller compiler = null;                                   // Компилятор
    private Linker linker = null;                                         // Линкер
    private LinkerStatistics lastLinkerStatistics = new LinkerStatistics();
    private ContextQualification lastCompileQualification = null;

    private boolean changed = false;
    private Boolean queryResult = null;
    private String querySource = "";
    private final List<FrontierDomain> frontierDomains = new ArrayList<>();
    private CausalFrontierCapture causalFrontierCapture;
    private QueryPass queryPass = QueryPass.SILENCE;
    private List<IContextFederation.ExplainPass> activeExplainPasses;
    private final List<IContextFederation.FrontierObservation> queryConflicts = new ArrayList<>();
    private User user = null;
    private String compliedLine = "";
    //
    private boolean otherOpinionsPossible;
    @Override public boolean hasOtherContextOpinions() { return otherOpinionsPossible; }
    @Override public Map<String, IContextFederation.Opinion> collectContextOpinions(String locator) throws Exception {
        return user.getContextOpinionSession().collect(this, locator);
    }
    @Override public Map<String, IContextFederation.Opinion> getContextOpinions(String locator) throws Exception {
        return user.getContextOpinionSession().saved(this, locator);
    }

    private boolean logging = true;
    private int debugLevel = Enums.DEBUG_LEVEL_DEBUG | (Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS);
    private int floodControlLimit = FLOOD_CONTROL_LIMIT;
    private Rule acceptedRule = null;
    private int transactionCounter = 0;
    private Object connectionCheckpoint;
    private String operationDescription;
    String proposedDescription;
    long descriptionOrder;
    private static final java.util.concurrent.atomic.AtomicLong DESCRIPTION_ORDER =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Experimental query policy that allows hypotheses containing
     * existential/C-variable terms. Such hypotheses can be useful, but they
     * are not a generally valid fallback when concrete hypotheses are absent.
     * The policy is therefore disabled by default and inherited by child Mind
     * transactions only when explicitly enabled by the caller.
     */
    private boolean includeAbstractiveHypothesis = false;

    public Mind(IUser user) throws Exception {
        this.user = (User) user;
        this.user.nextId();
        init();
    }

    public Mind(IMind root) throws Exception {
        this(root, false);
    }

    /**
     * Creates a technical child whose canonical Term/Predicate additions are
     * confined to child overlays instead of the historically shared parent
     * factories.
     *
     * <p>This is the operation boundary used by federation: foreign query
     * constants, projected values and ephemeral evidence may participate in
     * ordinary compiler/linker/analyzer semantics, but they must disappear
     * when the child is released and must never reach a read-only Context
     * snapshot root.</p>
     */
    public static Mind ephemeralChild(IMind root) throws Exception {
        return new Mind(root, true);
    }

    /** Writable private overlay; settlement never reaches its immutable snapshot parent. */
    public static Mind contextConnectionLayer(IMind root) throws Exception {
        Mind layer = new Mind(root, true);
        layer.connectionLayer = true;
        return layer;
    }

    private boolean connectionLayer;
    private boolean commandNoOp;

    /** Native authoring intent was valid but selected no change. */
    public boolean wasLastCommandNoOp() { return commandNoOp; }

    private Mind(IMind root,
                 boolean isolateCanonicalFactories) throws Exception {
        next = root;
        user = (User) root.getUser();
        id = user.nextId(); //root.getId() + 1;
        init();

        Mind parent = (Mind) root;
        connectionLayer = parent.connectionLayer;
        if (!isolateCanonicalFactories && user.getCurrentMind() == root) user.getContextOpinionSession().invalidate();
        operationDescription = parent.operationDescription;
        parent.incTransactionCounter();
        boolean initialized = false;
        try {
            if (isolateCanonicalFactories) {
                terms.transaction(
                        (DictionaryFactory) root.getTerms());
                predicates.transaction(
                        (PredicateFactory) root.getPredicates());
            } else {
                terms = (DictionaryFactory) root.getTerms();
                predicates = (PredicateFactory) root.getPredicates();
            }

            library.transaction((LibraryFactory) root.getLibrary());

            domains.transaction(parent.getDomains());
            rules.transaction((RuleFactory) root.getRules());
            comments.transaction(parent.getComments());
            tVars.transaction(parent.getTVars());
            tValues.transaction(parent.getTValues());
            functions.transaction(parent.getFunctions());
            fValues.transaction(parent.getFValues());

            debugLevel = root.getDebugLevel();

            includeAbstractiveHypothesis = parent.includeAbstractiveHypothesis();
            causalFrontierCapture = parent.causalFrontierCapture;
            initialized = true;
        } finally {
            if (!initialized) {
                parent.abortTransactionStart();
            }
        }
    }

    public void requireWritableContext() throws Exception {
        if (isStorageUsed() && user.getData().isReadOnly() && !connectionLayer)
            throw new org.kanger.exception.CommandErrorException("The selected Context revision is read-only");
    }

    public void proposeRevisionDescription(String description) throws Exception {
        String text = RevisionDescriptions.validate(description);
        if (text != null && !text.isEmpty()) {
            proposedDescription = text;
            descriptionOrder = DESCRIPTION_ORDER.incrementAndGet();
        }
    }

    public String getProposedRevisionDescription() {
        Mind selected = this;
        for (Mind m = this; m != null; m = (Mind) m.getNext()) {
            if (m.descriptionOrder > selected.descriptionOrder) selected = m;
        }
        return selected.proposedDescription;
    }

    public String resolveRevisionDescription(String explicit, String fallback) throws Exception {
        String text = RevisionDescriptions.validate(explicit);
        if (text != null && !text.isEmpty()) return text;
        text = getProposedRevisionDescription();
        return text == null || text.isEmpty() ? RevisionDescriptions.automatic(fallback) : text;
    }

    void inheritRevisionDescription(Mind source) {
        for (Mind m = source; m != null; m = (Mind) m.getNext()) {
            if (m.descriptionOrder > descriptionOrder) {
                proposedDescription = m.proposedDescription;
                descriptionOrder = m.descriptionOrder;
            }
        }
    }

    public static Mind preparePublicationStack(Mind source, Mind detachedRoot) throws Exception {
        UserTransactionStackSnapshot.requireExplicitSquashTopology(source);
        Mind current = UserTransactionStackSnapshot.capture(source).replayOverBaseline(detachedRoot);
        current.inheritRevisionDescription(source);
        while (current.getNext() != null) {
            Mind parent = (Mind) current.getNext();
            if (!parent.commitUserTransaction(current)) {
                throw new org.kanger.exception.CommandErrorException("Publication candidate commit rejected");
            }
            current = parent;
        }
        return current;
    }

    public void requirePublicationQuiescence() {
        UserTransactionStackSnapshot.requireExplicitSquashTopology(this);
    }

    public void checkpointUserConnections() throws Exception {
        if (isStorageUsed() && user.getData() instanceof IContextFederation) {
            connectionCheckpoint = ((IContextFederation) user.getData()).checkpointConnections();
        }
    }

    Object getConnectionCheckpoint() { return connectionCheckpoint; }
    void setConnectionCheckpoint(Object checkpoint) { connectionCheckpoint = checkpoint; }

    private void init() throws Exception {
        terms = new DictionaryFactory(this);                    // Словарь констант
        predicates = new PredicateFactory(this);                 // Предикаты
        functions = new FunctionFactory(this);                    // Функции
        library = new LibraryFactory(this);                            // Пользовательсткая библиотека функций и предикатов

        domains = new DomainFactory(this);                          // Список доменов
        rules = new RuleFactory(this);                             // Список правил
        comments = new CommentFactory(this);

        tVars = new TVariableFactory(this);                      // t-переменные
        tValues = new TValueFactory(this);                          // Подставленные значения

        fValues = new FValueFactory(this);                          // Решения функций

        hypothesis = new HypothesisStore(this);                                // Список гипотез
        tempHypothesis = new HypothesisStore(this);                                // Список гипотез
        solves = new SolutionsStore(this);                         // Список решений
        values = new ValuesStore(this);                               // Список значений

        log = new LogStore(this);                                        // Протокол вывода

        calculator = new Calculator(this);                             // Калькулятор
        compiler = new Compiller(this);                                   // Компилятор
        analyzer = new Analyzer(this);                                   // Анализатор
        linker = new Linker(this);                                         // Линкер

        floodControlLimit = Integer.parseInt(user.getProperty("flood.limit", FLOOD_CONTROL_LIMIT + ""));
        transactionCounter = 0;
    }


    @Override
    public boolean commit(IMind m) throws Exception {
        return commit(m, true);
    }

    /**
     * Commit boundary for an explicit user-visible transaction.
     * Semantic rejection restores the parent and leaves the child
     * reservation live so the user can amend or roll back that level.
     * The public IMind.commit contract deliberately keeps historical
     * settle-on-rejection behavior for technical/internal callers.
     */
    boolean commitUserTransaction(IMind m) throws Exception {
        return commit(m, false);
    }

    private boolean commit(IMind m, boolean settleRejectedChild) throws Exception {
        user.getContextOpinionSession().invalidate();
        synchronized (locker) {
            Mind child = (Mind) m;
            boolean sequencedBy = rules.isSequencedBy((RuleFactory) child.getRules());
            boolean[] activeCheckpoints = new boolean[8];
            boolean reservationFinished = false;
            boolean factoriesCompleted = sequencedBy;
            Map<UnitType, Set<Long>> saveDeleted = copyUnitState(deleted);
            Map<UnitType, Set<Long>> saveRestored = copyUnitState(restored);
            PortableMindLayer savePortableRebaseResidue = portableRebaseResidue.copy();
            PortableMindLayer childPortableState = null;

            try {
                childPortableState = PortableMindLayer.capture(child);
                if (!sequencedBy) {
                    markCompositeCheckpoints(activeCheckpoints);
                }

                functions.commit(child.getFunctions());
                fValues.commit(child.getFValues());
                tVars.commit(child.getTVars());
                tValues.commit(child.getTValues());
                domains.commit(child.getDomains());
                Set<Long> list = rules.commit((RuleFactory) child.getRules());
                comments.commit(child.getComments());
                library.commit((LibraryFactory) child.getLibrary());

                mergeUnitState(child);
                PortableMindLayer.mergeCommittedChild(this, childPortableState);

                if (!sequencedBy) {
                    Boolean rejected = analyzer.checkDatabase(list, false);
                    if (rejected != null && rejected) {
                        Throwable rollbackFailure = releaseCompositeCheckpoints(activeCheckpoints, null);
                        restoreUnitState(saveDeleted, saveRestored);
                        portableRebaseResidue = savePortableRebaseResidue;
                        if (rollbackFailure != null) {
                            rethrow(rollbackFailure);
                        }

                        if (settleRejectedChild) {
                            boolean rootQuiescent = finishTransactionReservationLocked();
                            reservationFinished = true;
                            try {
                                finalizeTransactionRootLocked(rootQuiescent);
                                copyCommitResult(child);
                            } catch (Throwable finalizationFailure) {
                                throw new TransactionSettlementException(
                                        TransactionSettlementException.Outcome.REJECTED,
                                        finalizationFailure);
                            }
                        }
                        return false;
                    }

                    completeCompositeCheckpoints(activeCheckpoints);
                    factoriesCompleted = true;
                }

                inheritRevisionDescription(child);
                boolean rootQuiescent = finishTransactionReservationLocked();
                reservationFinished = true;
                try {
                    if (rootQuiescent && isStorageUsed()
                            && user.getData() instanceof org.kanger.interfaces.internal.IRevisionPublication) {
                        ((org.kanger.interfaces.internal.IRevisionPublication) user.getData())
                                .setNextRevisionDescription(resolveRevisionDescription(null,
                                        child.operationDescription == null ? "Committed transaction" : child.operationDescription));
                    }
                    finalizeTransactionRootLocked(rootQuiescent);
                    if (rootQuiescent) { proposedDescription = null; descriptionOrder = 0; }
                    copyCommitResult(child);
                } catch (Throwable finalizationFailure) {
                    throw new TransactionSettlementException(
                            TransactionSettlementException.Outcome.COMMITTED,
                            finalizationFailure);
                }
                return true;
            } catch (Throwable failure) {
                Throwable propagated = failure;
                if (!factoriesCompleted) {
                    propagated = releaseCompositeCheckpoints(activeCheckpoints, propagated);
                    restoreUnitState(saveDeleted, saveRestored);
                    portableRebaseResidue = savePortableRebaseResidue;
                }
                if (!reservationFinished) {
                    try {
                        finishFailedTransactionLocked();
                    } catch (Throwable finishFailure) {
                        propagated.addSuppressed(finishFailure);
                    }
                }
                rethrow(propagated);
                throw new AssertionError("unreachable");
            }
        }
    }

    private void markCompositeCheckpoints(boolean[] active) throws Exception {
        functions.mark();
        active[0] = true;
        fValues.mark();
        active[1] = true;
        tVars.mark();
        active[2] = true;
        tValues.mark();
        active[3] = true;
        domains.mark();
        active[4] = true;
        rules.mark();
        active[5] = true;
        comments.mark();
        active[6] = true;
        library.mark();
        active[7] = true;
    }

    private void completeCompositeCheckpoints(boolean[] active) throws Exception {
        functions.commit();
        active[0] = false;
        fValues.commit();
        active[1] = false;
        tVars.commit();
        active[2] = false;
        tValues.commit();
        active[3] = false;
        domains.commit();
        active[4] = false;
        rules.commit();
        active[5] = false;
        comments.commit();
        active[6] = false;
        library.commit();
        active[7] = false;
    }

    private Throwable releaseCompositeCheckpoints(boolean[] active, Throwable primary) {
        Throwable failure = primary;
        for (int i = active.length - 1; i >= 0; --i) {
            if (!active[i]) {
                continue;
            }
            try {
                releaseCompositeCheckpoint(i);
            } catch (Throwable rollbackFailure) {
                if (failure == null) {
                    failure = rollbackFailure;
                } else if (failure != rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            } finally {
                active[i] = false;
            }
        }
        return failure;
    }

    private void releaseCompositeCheckpoint(int index) throws Exception {
        switch (index) {
            case 0:
                functions.release();
                break;
            case 1:
                fValues.release();
                break;
            case 2:
                tVars.release();
                break;
            case 3:
                tValues.release();
                break;
            case 4:
                domains.release();
                break;
            case 5:
                rules.release();
                break;
            case 6:
                comments.release();
                break;
            case 7:
                library.release();
                break;
            default:
                throw new IllegalArgumentException("Unknown composite checkpoint " + index);
        }
    }

    private Map<UnitType, Set<Long>> copyUnitState(Map<UnitType, Set<Long>> source) {
        Map<UnitType, Set<Long>> copy = new HashMap<>();
        for (Map.Entry<UnitType, Set<Long>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    private void mergeUnitState(Mind child) {
        Set<UnitType> unitTypes = new HashSet<>();
        unitTypes.addAll(child.getDeleted().keySet());
        unitTypes.addAll(child.getRestored().keySet());

        for (UnitType unitType : unitTypes) {
            Set<Long> childDeleted = child.getDeleted().get(unitType);
            Set<Long> childRestored = child.getRestored().get(unitType);
            Set<Long> ids = new HashSet<>();
            if (childDeleted != null) {
                ids.addAll(childDeleted);
            }
            if (childRestored != null) {
                ids.addAll(childRestored);
            }

            for (Long unitId : ids) {
                boolean deletes = childDeleted != null && childDeleted.contains(unitId);
                boolean restores = childRestored != null && childRestored.contains(unitId);

                if (deletes) {
                    deleted.computeIfAbsent(unitType, key -> new HashSet<>()).add(unitId);
                }
                if (restores) {
                    restored.computeIfAbsent(unitType, key -> new HashSet<>()).add(unitId);
                }

                // A single child marker supersedes the opposite state inherited
                // from this parent. A deliberate child pair is valid state and
                // must remain a pair after commit; visibility order resolves it.
                if (deletes && !restores) {
                    Set<Long> restoredIds = restored.get(unitType);
                    if (restoredIds != null) {
                        restoredIds.remove(unitId);
                    }
                } else if (restores && !deletes) {
                    Set<Long> deletedIds = deleted.get(unitType);
                    if (deletedIds != null) {
                        deletedIds.remove(unitId);
                    }
                }
            }
        }
    }

    private void restoreUnitState(Map<UnitType, Set<Long>> saveDeleted,
                                  Map<UnitType, Set<Long>> saveRestored) {
        deleted.clear();
        restored.clear();
        deleted.putAll(copyUnitState(saveDeleted));
        restored.putAll(copyUnitState(saveRestored));
    }

    private void copyCommitResult(Mind child) throws Exception {
        log.commit(child.getLog());
        queryResult = child.getQueryResult();
        compliedLine = child.getCompliedString();
        lastLinkerStatistics = child.linker.snapshotStatistics();
        replaceFrontierDomains(child.frontierDomains);
        queryConflicts.clear(); queryConflicts.addAll(child.queryConflicts);
        otherOpinionsPossible = child.otherOpinionsPossible;
    }

    private void finishFailedTransactionLocked() {
        if (transactionCounter <= 0) {
            throw new IllegalStateException("Transaction counter underflow for Mind " + id);
        }
        --transactionCounter;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    private void update() throws Exception {
        terms.update();
        predicates.update();
        library.update();

        functions.update();
        fValues.update();
        tVars.update();
        tValues.update();
        domains.update();
        rules.update();
        comments.update();

        user.flush();
    }

    @Override
    public void release(IMind m) throws Exception {
        synchronized (locker) {

            log.commit((LogStore) m.getLog());
            solves.commit((SolutionsStore) m.getSolutions());
            values.commit((ValuesStore) m.getValues());

            queryResult = m.getQueryResult();
            compliedLine = m.getCompliedString();
            lastLinkerStatistics = ((Mind) m).linker.snapshotStatistics();
            replaceFrontierDomains(((Mind) m).frontierDomains);
            queryConflicts.clear(); queryConflicts.addAll(((Mind) m).queryConflicts);
            otherOpinionsPossible = ((Mind) m).otherOpinionsPossible;

            Object checkpoint = ((Mind) m).connectionCheckpoint;
            if (checkpoint != null && user.getData() instanceof IContextFederation) {
                ((IContextFederation) user.getData()).restoreConnections(checkpoint);
            }
            finishTransactionLocked();
        }
    }

    /**
     * Settles an {@link #ephemeralChild(IMind)} without publishing any of its
     * presentation state.
     *
     * <p>The child factories are operation-local overlays and are deliberately
     * discarded. This is the federation no-result boundary: an unresolved
     * foreign pass must not erase the caller's existing local query
     * Values/Solutions/logs while still consuming exactly one transaction
     * reservation.</p>
     */
    public void discardEphemeral(IMind m) throws Exception {
        if (m == null) {
            throw new IllegalArgumentException(
                    "ephemeral Mind must not be null");
        }
        synchronized (locker) {
            finishTransactionLocked();
        }
    }

    protected void clearMind() throws Exception {
        synchronized (locker) {
            terms.clear();
            predicates.clear();
            library.clear();

            domains.clear();
            tVars.clear();
            tValues.clear();
            rules.clear();
            comments.clear();
            functions.clear();
            fValues.clear();

            solves.clear();
            values.clear();
            hypothesis.clear();
            tempHypothesis.clear();

            clearCVarLinks();
            usedRules.clear();
            usedDomains.clear();
            excludedDomains.clear();
            calculatedDomains.clear();
            producedDomains.clear();
            domainCauses.clear();
            domainSolves.clear();
            queryValues.clear();
            clearRuleSolves();
            floodControl.clear();

            deleted.clear();
            restored.clear();
            portableRebaseResidue = PortableMindLayer.empty();

            acceptedRule = null;
            queryResult = null;
            querySource = "";
            frontierDomains.clear();
            queryConflicts.clear();
            queryPass = QueryPass.SILENCE;
            compliedLine = "";
            lastLinkerStatistics = new LinkerStatistics();

            // Linker owns additional query-local indexes that are intentionally
            // private. Replacing the execution component drops those references
            // together with the public transient maps cleared above.
            linker = new Linker(this);
        }
    }

    public void pack() throws Exception {
        library.pack();

        // Deleted rule indexes still need their persistent domains while unloading.
        rules.pack();
        tVars.pack();
        domains.pack();
        comments.pack();
        fValues.pack();
        functions.pack();

        tValues.pack();

        terms.pack();
        clearCVarLinks();
        predicates.pack();

        deleted.clear();
        restored.clear();
        portableRebaseResidue = PortableMindLayer.empty();
    }


    public QueryPass getQueryPass() {
        return queryPass;
    }

    public void setQueryPass(QueryPass queryPass) {
        this.queryPass = queryPass;
    }

    public void pushDebugLevel() {
        debugLevelStack.push(debugLevel);
    }

    public void popDebugLevel() {
        debugLevel = debugLevelStack.pop();
    }

    @Override
    public IUser getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public IMind getNext() {
        return next;
    }

    public void setNext(Mind next) {
        this.next = next;
    }

    @Override
    public int getDebugLevel() {
        return debugLevel;
    }

    @Override
    public void setDebugLevel(int debugLevel) {
        this.debugLevel = debugLevel;
    }

    @Override
    public DictionaryFactory getTerms() {
        return terms;
    }

    @Override
    public PredicateFactory getPredicates() {
        return predicates;
    }

    public DomainFactory getDomains() {
        return domains;
    }

    @Override
    public ValuesStore getValues() {
        return values;
    }

    @Override
    public List<Map<String, ITerm>> getValues(final ValuesOrder... order) throws Exception {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        for (ValuesOrder key : order) {
            if (key == null) {
                throw new IllegalArgumentException("order key must not be null");
            }
        }

        List<Map<String, ITerm>> rows = new ArrayList<>();
        for (Map<String, ITerm> row : getValues()) {
            rows.add(new LinkedHashMap<>(row));
        }

        if (order.length == 0 || rows.size() < 2) {
            return rows;
        }

        Collections.sort(rows, new Comparator<Map<String, ITerm>>() {
            @Override
            public int compare(Map<String, ITerm> left, Map<String, ITerm> right) {
                for (ValuesOrder key : order) {
                    ITerm l = left.get(key.getField());
                    ITerm r = right.get(key.getField());
                    int compared;
                    if (l == null && r == null) {
                        compared = 0;
                    } else if (l == null) {
                        compared = -1;
                    } else if (r == null) {
                        compared = 1;
                    } else {
                        compared = l.compareTo(r);
                    }
                    if (key.getDirection() == ValuesOrder.Direction.DESC) {
                        compared = -compared;
                    }
                    if (compared != 0) {
                        return compared;
                    }
                }
                return 0;
            }
        });
        return rows;
    }

    @Override
    public RuleFactory getRules() {
        return rules;
    }

    public CommentFactory getComments() {
        return comments;
    }

    public TVariableFactory getTVars() {
        return tVars;
    }

    public FunctionFactory getFunctions() {
        return functions;
    }

    @Override
    public LibraryFactory getLibrary() {
        return library;
    }

    @Override
    public HypothesisStore getHypothesis() {
        return hypothesis;
    }

    public HypothesisStore getTempHypothesis() {
        return tempHypothesis;
    }

    @Override
    public LogStore getLog() {
        return log;
    }

    @Override
    public SolutionsStore getSolutions() {
        return solves;
    }

    public TValueFactory getTValues() {
        return tValues;
    }

    public FValueFactory getFValues() {
        return fValues;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean b) {
        changed = b;
    }

    public void link(Rule r, boolean logging) throws Exception {
        if (causalFrontierCapture != null) {
            causalFrontierCapture.beginLink(this, r);
        }
        linker.link(r, logging);
    }

    public boolean analyze(Rule rule, boolean logging) throws Exception {
        boolean result = analyzer.analyze(rule, logging);
        if (causalFrontierCapture != null) {
            causalFrontierCapture.afterAnalyze(this, rule, result);
        }
        return result;
    }

    CausalFrontierCapture getCausalFrontierCapture() {
        return causalFrontierCapture;
    }

    void setCausalFrontierCapture(CausalFrontierCapture capture) {
        causalFrontierCapture = capture;
    }

    @Override
    public boolean compile(String src) throws Exception {
        return compile(src, null, true);
    }

    @Override
    public boolean compile(String src, Object[] ext) throws Exception {
        return compile(src, ext, true);
    }

    private static String compilerInput(String source) {
        String input = source == null ? "" : source;
        if (input.isEmpty()) {
            return input;
        }
        char last = input.charAt(input.length() - 1);
        return last == '\r' || last == '\n' ? input : input + '\n';
    }

    public boolean compile(String src, Object[] ext, boolean logging) throws Exception {
        user.getContextOpinionSession().invalidate();
        requireWritableContext();
        lastCompileQualification = null;
        ContextSourceMetadata.Parsed sourceMetadata =
                ContextSourceMetadata.parse(src);
        IContextFederation sourceFederation = null;
        IContextFederation.SourceDependencyPlan sourceDependencyPlan = null;

        if (sourceMetadata.isPresent()) {
            IData data = user.getData();
            if (!isStorageUsed()
                    || !(data instanceof IContextFederation)) {
                throw new IllegalStateException(
                        "Context dependency metadata requires an active Context federation storage");
            }
            sourceFederation =
                    (IContextFederation) data;
            sourceDependencyPlan =
                    sourceFederation.prepareSourceDependencies(
                            sourceMetadata.getRequests());

        }

        src = compilerInput(sourceMetadata.getSource());
        this.logging = logging;

        getQueryValues().clear();
        getLog().clear();
        getSolutions().clear();
        getValues().clear();
        getHypothesis().clear();

        Token t = null;
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
            Mind m = tx.mind();
            m.setQueryPass(QueryPass.ACCEPT);
            int previousPos = 0;
            Queue<ITerm> externals = convertExternals(ext);

            while ((t = Tools.extractLine(src, t)) != null) {
                String comment = src.substring(previousPos, t.getPos()).trim();
                if (previousPos == 0) {
                    String[] cc = Parser.extractComments(comment);
                    if (cc.length > 1 && !cc[0].isEmpty()) {
                        m.getComments().add(CommentFactory.HEADER_ID, cc[0].trim());
                        comment = comment.substring(cc[0].length()).trim();
                    }
                }
                previousPos = t.getPos() + t.getLen();

                Object r = m.compileLine(t.getToken(src), false, externals, t.getPos());
                if (!comment.isEmpty() && r instanceof Rule) {
                    m.getComments().add(((Rule) r).getId(), comment);
                }
            }

            if (t != null && src.length() > t.getPos() + t.getLen()) {
                String comment = src.substring(t.getPos() + t.getLen()).trim();
                if (!comment.isEmpty()) {
                    m.getComments().add(CommentFactory.FOOTER_ID, comment);
                }
            }

            m.link(null, logging);
            Boolean ar = m.analyze(null, logging);
            lastCompileQualification =
                    new ContextQualification(
                            !Boolean.TRUE.equals(ar),
                            m.analyzer.getCollisionWitnesses());

            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
                }
                tx.rollback();
                return false;
            } else {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "SUCCESS: No Collisions in Program");
                }
                if (sourceDependencyPlan != null) {
                    m.checkpointUserConnections();
                    sourceFederation.installSourceDependencies(
                            sourceDependencyPlan);
                }
                tx.commit();
                return true;
            }
        }
    }

    public Object compileLine(String line, boolean query, Queue<ITerm> externals) throws Exception {
        return compileLine(line, query, externals, 0);
    }

    private Object compileLine(String line, boolean query, Queue<ITerm> externals, int sourceOffset) throws Exception {
        String orig = line.trim();
        compliedLine = orig;
        Object r = null;
        Boolean suc = null;

        switch (line.charAt(0)) {
            case Enums.FOO:
                r = Parser.implement(line.substring(1), this, null, sourceOffset + 1);
                if (r != null) {
                    library.add((Operation) r);
                }
                break;
            case Enums.INS:
            case Enums.ANT:
                suc = true;
                break;
            case Enums.DEL:
            case Enums.SUC:
                suc = false;
                break;
        }
        if (suc != null) {
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
                Mind x = tx.mind();
                Leaf p = Parser.parse(line.substring(1), sourceOffset + 1);

                r = x.compiler.compileLine(p, suc, orig, query, externals);
                x.setCompliedLine(compliedLine);
                if (r instanceof Rule && ((Rule) r).isSecond()) {
                    tx.rollback();
                    commandNoOp = line.charAt(0) == Enums.ANT || line.charAt(0) == Enums.INS;
                    log.add(LogMode.ANALYZER, "WARNING: Rule is duplicated: " + r);
                    r = null;
                } else if (r instanceof Rule) {
                    tx.commit();
                    log.add(LogMode.ANALYZER, "Compiled: " + ((Rule) r).getOrigin());
                    log.add(LogMode.ANALYZER, (Rule) r);
                    for (IRule rx : rules) {
                        if (rx.getId() > ((Rule) r).getId() /*&& rx.isGenerated()*/) {
                            log.add(LogMode.ANALYZER, "Extracted: " + rx.getOrigin());
                        }
                    }
                } else {
                    tx.rollback();
                }
            }
        }
        return r;
    }

    @Override
    public Boolean query(String line) throws Exception {
        return query(line, null);
    }

    @Override
    public Boolean query(String line, Object[] ext) throws Exception {
        querySource = line;
        queryPass = QueryPass.SILENCE;
        queryResult = query(line, ext, true);
        return queryResult;
    }

    @Override
    public String getCompliedString() {
        return compliedLine;
    }

    public void setCompliedLine(String compliedLine) {
        this.compliedLine = compliedLine;
    }

    @Override
    public String getVersion() {
        return Version.VERSION_S;
    }

    public Map<Domain, Set<ArgumentsList>> getUsedDomains() {
        return usedDomains;
    }

    public Map<Domain, Set<ArgumentsList>> getExcludedDomains() {
        return excludedDomains;
    }

    public Map<Domain, List<List<ITerm>>> getProducedDomains() {
        return producedDomains;
    }

    public Map<Domain, List<List<ITerm>>> getCalculatedDomains() {
        return calculatedDomains;
    }

    public Map<Domain, Map<ArgumentsList, Set<ICause>>> getDomainCauses() {
        return domainCauses;
    }

    public Map<Domain, Map<ArgumentsList, SortedSet<TValue>>> getDomainSolves() {
        return domainSolves;
    }

    public Map<Long, Set<IRule>> getUsedRules() {
        return usedRules;
    }

    public Map<TVariableSet, List<TSolve>> getRuleSolves() {
        ruleSolvesExposed = true;
        return ruleSolves;
    }

    Map<TVariableSet, List<TSolve>> ruleSolvesInternal() {
        return getClass() == Mind.class ? ruleSolves : getRuleSolves();
    }
    long ruleSolvesVersion() { return ruleSolvesVersion; }
    boolean ruleSolvesExposed() { return ruleSolvesExposed || getClass() != Mind.class; }
    private void clearRuleSolves() { ruleSolves.clear(); ++ruleSolvesVersion; }

    public Map<TVariable, long[]> getFloodControl() {
        return floodControl;
    }

    public Map<UnitType, Set<Long>> getDeleted() {
        return deleted;
    }

    public Map<UnitType, Set<Long>> getRestored() {
        return restored;
    }

    PortableMindLayer getPortableRebaseResidue() {
        return portableRebaseResidue;
    }

    void setPortableRebaseResidue(PortableMindLayer state) {
        portableRebaseResidue = state == null ? PortableMindLayer.empty() : state;
    }

    /**
     * Historical one-child view retained for binary/source compatibility.
     * Semantic lookup must use {@link #getCVarChild(ITerm, long)} because one
     * parent C-variable can have a distinct projection in each target Rule.
     */
    @Deprecated
    public Map<ITerm, ITerm> getCvarChilds() {
        return cvarChilds;
    }

    public Map<ITerm, ITerm> getCvarParents() {
        return cvarParents;
    }

    /**
     * Return the canonical child projection of {@code parent} in the binding
     * scope of {@code targetRuleId}, searching parent Mind contexts when the
     * current transaction does not own that projection.
     */
    public ITerm getCVarChild(ITerm parent, long targetRuleId) {
        Map<Long, ITerm> children = cvarChildrenByRule.get(parent);
        ITerm child = children == null ? null : children.get(targetRuleId);
        if (child == null && next != null) {
            return ((Mind) next).getCVarChild(parent, targetRuleId);
        }
        return child;
    }

    /** Bind a C-variable projection to its explicit target Rule scope. */
    public void linkCVar(ITerm parent, ITerm child) {
        if (parent == null || child == null) {
            return;
        }
        if (!(child instanceof Term)) {
            throw new IllegalArgumentException("C-variable child must be a Term");
        }

        long targetRuleId = ((Term) child).getRuleId();
        if (targetRuleId < 0) {
            throw new IllegalStateException("C-variable child has no target Rule");
        }

        Map<Long, ITerm> children = cvarChildrenByRule.get(parent);
        if (children == null) {
            children = new HashMap<>();
            cvarChildrenByRule.put(parent, children);
        }

        ITerm displaced = children.put(targetRuleId, child);
        if (displaced != null && !displaced.equals(child)) {
            cvarParents.remove(displaced);
        }
        cvarParents.put(child, parent);

        // Compatibility view only. Linker and new code must use rule-scoped lookup.
        cvarChilds.put(parent, child);
    }

    public void unlinkCVar(ITerm term) {
        if (term == null) {
            return;
        }

        Set<ITerm> affectedParents = new HashSet<>();

        // If the removed term is a parent, drop every Rule-scoped projection.
        Map<Long, ITerm> ownedChildren = cvarChildrenByRule.remove(term);
        if (ownedChildren != null) {
            for (ITerm child : ownedChildren.values()) {
                cvarParents.remove(child);
            }
        }

        // If the removed term is a child, drop only its projection and retain
        // siblings belonging to other target Rules. Scan the authority map as
        // well as the reverse map so damaged one-sided adjacency is repairable.
        ITerm reverseParent = cvarParents.remove(term);
        if (reverseParent != null) {
            affectedParents.add(reverseParent);
        }
        Iterator<Map.Entry<ITerm, Map<Long, ITerm>>> parentIterator =
                cvarChildrenByRule.entrySet().iterator();
        while (parentIterator.hasNext()) {
            Map.Entry<ITerm, Map<Long, ITerm>> parentEntry = parentIterator.next();
            Iterator<Map.Entry<Long, ITerm>> childIterator =
                    parentEntry.getValue().entrySet().iterator();
            boolean removed = false;
            while (childIterator.hasNext()) {
                if (term.equals(childIterator.next().getValue())) {
                    childIterator.remove();
                    removed = true;
                }
            }
            if (removed) {
                affectedParents.add(parentEntry.getKey());
            }
            if (parentEntry.getValue().isEmpty()) {
                parentIterator.remove();
            }
        }

        // B7.1 deliberately exercises legacy and damaged one-sided links.
        // Scrub every direct edge involving the removed term even when that
        // edge was never published into cvarChildrenByRule.
        for (Map.Entry<ITerm, ITerm> entry : cvarChilds.entrySet()) {
            if (term.equals(entry.getValue())) {
                affectedParents.add(entry.getKey());
            }
        }
        cvarChilds.entrySet().removeIf(entry ->
                term.equals(entry.getKey()) || term.equals(entry.getValue()));
        cvarParents.entrySet().removeIf(entry ->
                term.equals(entry.getKey()) || term.equals(entry.getValue()));

        // The legacy one-child view is non-authoritative, but keep it coherent
        // for callers that still inspect it: select any surviving Rule child.
        for (ITerm parent : affectedParents) {
            cvarChilds.remove(parent);
            Map<Long, ITerm> siblings = cvarChildrenByRule.get(parent);
            if (siblings != null && !siblings.isEmpty()) {
                cvarChilds.put(parent, siblings.values().iterator().next());
            }
        }
    }

    private void clearCVarLinks() {
        cvarChilds.clear();
        cvarChildrenByRule.clear();
        cvarParents.clear();
    }

    public Map<TVariable, Set<TValue>> getQueryValues() {
        return queryValues;
    }

    @Override
    public String getQueryString() {
        return querySource;
    }

    /**
     * Returns the unresolved ordinary query Domains captured by the most
     * recent Analyzer pass. The returned objects are immutable operation-local
     * descriptors; an empty list preserves the historical single-Context path.
     */
    public List<FrontierDomain> getFrontierDomains() {
        return Collections.unmodifiableList(
                new ArrayList<FrontierDomain>(frontierDomains));
    }

    void clearFrontierDomains() {
        frontierDomains.clear();
    }

    void replaceFrontierDomains(
            Collection<FrontierDomain> frontier) {
        frontierDomains.clear();
        if (frontier != null) {
            frontierDomains.addAll(frontier);
        }
    }

    @Override
    public Boolean getQueryResult() {
        return queryResult;
    }

    public void setQueryResult(Boolean queryResult) {
        this.queryResult = queryResult;
    }

    public boolean isSystem(IPredicate p) throws Exception {
        return calculator.exists(p);
    }

    public int executeSystem(Domain d) throws Exception {
        for (int i = 0; i < d.getRange(); ++i) {
            if (d.getArguments().get(i).getType() == ArgumentType.FUNCTION
                    && ((Function) d.getArguments().get(i).getObject(this)).isCalculable()
                    && ((Function) d.getArguments().get(i).getObject(this)).isEmpty(this)) {
//                d.getArguments().get(i).getF().clear();
                calculator.calculate((Function) d.getArguments().get(i).getObject(this), logging);
            }
        }

        return calculator.execute(d);
    }


    /////////////////////////////////////
    private String invert(String line) {
        if (line.charAt(0) == Enums.SUC) {
            return String.format("%c%s", Enums.ANT, line.substring(1));
        } else {
            return String.format("%c%s", Enums.SUC, line.substring(1));
        }
    }

    public List<IRule> getProductions(IRule r) {
        List<IRule> productions = new ArrayList<>();
        for (IRule pr : getRules()) {
            if (pr.getId() > r.getId()) {
                if (pr.isGenerated()) {
                    productions.add(pr);
                }
            } else {
                break;
            }
        }
        return productions;
    }

    private Queue<ITerm> convertExternals(Object[] ext) throws Exception {
        Queue<ITerm> externals = new LinkedList<>();
        if (ext != null) {
            for (Object o : ext) {
                ITerm t = getTerms().add(o);
                externals.add(t);
            }
        }
        return externals;
    }


    public Boolean queryInsert(String line, Object[] ext, boolean logging) throws Exception {
        commandNoOp = false;
        Boolean res = null;
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
            Mind m = tx.mind();
            m.setQueryPass(QueryPass.INSERT);
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "============= INSERT ======================");
            }

            line = invert(line);
            line = invert(line);

            setCompliedLine(line);
            Rule r = (Rule) m.compileLine(
                    line, true, convertExternals(ext));
            if (r != null && !r.isSecond()) {

                m.link(r, logging);
                boolean ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                    }
                    tx.rollback();
                    res = null;
                } else {

                    List<IRule> productions = m.getProductions(r);
                    if (!productions.isEmpty()) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALYZER, "SUCCESS: Solves to append (" + productions.size() + "):");
                        }
                        for (IRule pr : productions) {
                            ((Rule) pr).setQuery(false);
                            ((Rule) pr).setGenerated(false);
                            ((Rule) pr).primitivize();
                            if (logging) {
                                m.getLog().add(LogMode.SOLVES, String.format("\tProduced %03d:\t%s", pr.getId(), pr.toString()));
                            }
                        }
                    } else if (logging) {
                        m.getLog().add(LogMode.ANALYZER, String.format("WARNING: No candidates to append"));
                    }

                    r.setDeleted(true, m);
                    tx.commit();
                    setChanged(true);
                    res = true;
                }
            } else {
                if (logging && r != null && r.isSecond()) {
                    m.getLog().add(LogMode.ANALYZER, "Rule already exists: " + r);
                }
                commandNoOp = m.commandNoOp || (r != null && r.isSecond());
                tx.rollback();
            }

            hypothesis.clear();
            tempHypothesis.clear();
            return res;
        }
    }

    public Boolean queryAccept(String line, Object[] ext, boolean logging) throws Exception {
        commandNoOp = false;
        Boolean res = null;
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
            Mind m = tx.mind();
            m.setQueryPass(QueryPass.ACCEPT);
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "============= ACCEPTING ===================");
            }

            setCompliedLine(line);
            Rule r = (Rule) m.compileLine(line, false, convertExternals(ext));
            if (r != null && !r.isSecond()) {
                boolean ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                    }
                    tx.rollback();
                    res = null;
                } else {
                    m.link(r, logging);
                    ar = m.analyze(r, logging);
                    if (ar) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALYZER, "ERROR: Conflict in new rule");
                        }
                        tx.rollback();
                        res = null;
                    } else {
                        if (logging) {
                            m.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d:\t%s", r.getId(), r.toString()));
                            List<IRule> productions = m.getProductions(r);
                            if (!productions.isEmpty()) {
                                for (IRule pr : productions) {
                                    m.getLog().add(LogMode.SOLVES, String.format("\tProduced %03d:\t%s", pr.getId(), pr.toString()));
                                }
                            }
                            m.getLog().add(LogMode.ANALYZER, "SUCCESS: New rule accepted");
                        }
                        tx.commit();
                        setChanged(true);
                        acceptedRule = r;
                        res = true;
                    }
                }
            } else {
                if (logging && r != null) {
                    m.getLog().add(LogMode.ANALYZER, "WARNING: Right is duplicated: " + r);
                }
                commandNoOp = m.commandNoOp || (r != null && r.isSecond());
                tx.rollback();
            }

            hypothesis.clear();
            tempHypothesis.clear();
            return res;
        }
    }

    public Boolean queryDelete(String line, Object[] ext, boolean logging) throws Exception {
        commandNoOp = false;
        Boolean res = null;

        setQueryPass(QueryPass.DELETE);
        if (logging) {
            log.add(LogMode.ANALYZER, "============= DELETE ======================");
        }

        Operation op = getLibrary().find(line.substring(1).replaceAll(";", ""));
        if (op != null) {
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
                Mind m = tx.mind();
                op.setDeleted(true, m);
                m.getLog().add(LogMode.ANALYZER, "SUCCESS: Deleted function " + line.substring(1));
                tx.commit();
                res = true;
            }
        } else {
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
                Mind x = tx.mind();
                Set<IRule> set = new HashSet<>();
                line = invert(line);
                setCompliedLine(line);
                Rule r = (Rule) x.compileLine(line, true, convertExternals(ext));
                if (r != null && !r.isSecond()) {
                    x.link(r, logging);
                    boolean ar = x.analyze(r, logging);
                    if (ar && x.getSolutions().size() > 0) {
                        for (IRule rx : x.getSolutions()) {
                            set.add(rx);
                        }
                    }
                }
                if (set.isEmpty() && logging) {
                    x.getLog().add(LogMode.ANALYZER, "WARNING: No candidates to delete");
                }
                commandNoOp = set.isEmpty();
                tx.rollback();
                if (!set.isEmpty()) {
                    removeResult(set, logging);
                    res = true;
                    hypothesis.clear();
                    tempHypothesis.clear();
                }
            }
        }
        return res;
    }

    ContextQualification qualifyCurrentContext(boolean logging) throws Exception {
        ContextQualification result;

        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
            Mind m = tx.mind();
            m.setQueryPass(QueryPass.CHECK);
            boolean found = false;
            for (IRule rx : m.getRules()) {
                if (rx.isGenerated()) {
                    if (logging) {
                        m.getLog().add(LogMode.STORAGE,
                                "Delete produced rule: " + String.format("%03d: %s", rx.getId(), rx));
                    }
                    ((Rule) rx).setDeleted(true, m);
                    found = true;
                }
            }
            if (found && logging) {
                m.getLog().add(LogMode.STORAGE, "-------------------------------------------");
            }

            m.link(null, logging);
            boolean collision = m.analyze(null, logging);
            List<ContextQualification.CollisionWitness> witnesses =
                    m.analyzer.getCollisionWitnesses();

            if (collision) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
                }
                tx.rollback();
                result = new ContextQualification(false, witnesses);
            } else {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "SUCCESS: No Collisions in Program");
                }
                tx.commit();
                result = new ContextQualification(true, witnesses);
            }
            hypothesis.clear();
            tempHypothesis.clear();
        }
        return result;
    }

    public ContextQualification getLastCompileQualification() {
        return lastCompileQualification;
    }

    public Boolean queryCheck(boolean logging) throws Exception {
        return qualifyCurrentContext(logging).isValid();
    }

    private boolean hasQueryFederation() throws Exception {
        if (!isStorageUsed()) return false;
        IData data = user.getData();
        return data instanceof IContextFederation && isStorageUsed()
                && ((IContextFederation) data).hasConnectedContexts();
    }

    /** Explicit local publisher boundary; queries never invoke this operation. */
    public long saveContextConnections() throws Exception {
        synchronized (locker) {
            if (next!=null || transactionCounter!=0 || user.getCurrentMind()!=this)
                throw new org.kanger.exception.CommandErrorException("Context save requires the settled current root; commit or roll back active transactions first");
            IData data=user.getData();
            if (!(data instanceof IContextFederation))
                throw new org.kanger.exception.CommandErrorException("Context federation is unavailable");
            return ((IContextFederation)data).saveConnections(this);
        }
    }

    private boolean isolateQueryRuntime() throws Exception {
        return isStorageUsed() && user.getData() instanceof org.kanger.interfaces.internal.IRevisionPublication;
    }

    private Queue<ITerm> queryExternals(Object[] ext) throws Exception {
        if (ext == null || ext.length == 0 || !isolateQueryRuntime()) return convertExternals(ext);
        Mind parameters = Mind.ephemeralChild(this);
        try {
            return parameters.convertExternals(ext);
        } finally {
            discardEphemeral(parameters);
        }
    }

    private Queue<ITerm> localQueryExternals(Mind target, Queue<ITerm> externals) throws Exception {
        if (!isolateQueryRuntime()) return externals;
        Queue<ITerm> local = new LinkedList<ITerm>();
        for (ITerm term : externals) local.add(target.getTerms().projectSemantic(SemanticTermSnapshot.capture(term).materialize()));
        return local;
    }

    private TechnicalMindTransaction queryTransaction() throws Exception {
        if (isolateQueryRuntime()) {
            // Query-only constants must not publish X or erase its working topology.
            return TechnicalMindTransaction.beginIsolated(this);
        }
        return TechnicalMindTransaction.begin(this);
    }

    public Boolean queryCheckFalse(String line, Object[] ext, boolean logging) throws Exception {
        return queryCheckFalseCanonical(
                line, queryExternals(ext), logging);
    }

    private Boolean queryCheckFalseCanonical(
            String line,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        Boolean res = null;
        try (TechnicalMindTransaction tx = queryTransaction()) {
            Mind m = tx.mind();
            externals = localQueryExternals(m, externals);
            m.setQueryPass(QueryPass.CHECKFALSE);
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "============= FALSE CHECKING ==============");
            }

            Rule r = (Rule) m.compileLine(
                    invert(line), true, externals);
            setCompliedLine(line);
            if (r != null && !r.isSecond()) {
                boolean ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "Result: FALSE");
                        logResult(m);
                    }
                    res = false;
                    hypothesis.clear();
                    tempHypothesis.clear();
                } else {
                    m.link(r, logging);
                    ar = m.analyze(r, logging);
                    if (ar) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALYZER, "Result: FALSE");
                            logResult(m);
                        }
                        res = false;
                        hypothesis.clear();
                        tempHypothesis.clear();
                    } else {
                        hypothesis.commit(m.getHypothesis());
                        tempHypothesis.commit(m.getTempHypothesis());

                        if (!hypothesis.isEmpty()) {
                            Set<IHypothesis> toDelete = new HashSet<>();
                            for (IHypothesis h : hypothesis) {
                                for (ITerm t : ((ArgumentsList) h.getArguments()).getCVariables(this)) {
                                    if (((Term) t).getRule(this).isQuery()) {
                                        toDelete.add(h);
                                    }
                                }
                            }
                            hypothesis.removeAll(toDelete);
                        }
                        if (!tempHypothesis.isEmpty()) {
                            Set<IHypothesis> toDelete = new HashSet<>();
                            for (IHypothesis h : tempHypothesis) {
                                for (ITerm t : ((ArgumentsList) h.getArguments()).getCVariables(this)) {
                                    if (((Term) t).getRule(this).isQuery()) {
                                        toDelete.add(h);
                                    }
                                }
                            }
                            tempHypothesis.removeAll(toDelete);
                        }
                    }
                }
            } else if (r != null && r.isSecond()) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "Rule already defined: " + r);
                    m.getLog().add(LogMode.ANALYZER, "Result: TRUE");
                    logResult(m);
                }
                res = true;
                hypothesis.clear();
                tempHypothesis.clear();
            }
            tx.rollback();
            return res;
        }
    }

    public Boolean queryCheckTrue(String line, Object[] ext, boolean logging) throws Exception {
        return queryCheckTrueCanonical(
                line, queryExternals(ext), logging);
    }

    private Boolean queryCheckTrueCanonical(
            String line,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        Boolean res = null;
        try (TechnicalMindTransaction tx = queryTransaction()) {
            Mind m = tx.mind();
            externals = localQueryExternals(m, externals);
            m.setQueryPass(QueryPass.CHECKTRUE);
            if (logging) {
                m.getLog().add(LogMode.ANALYZER, "============= TRUE CHECKING ===============");
            }

            Rule r = (Rule) m.compileLine(
                    line, true, externals);
            setCompliedLine(line);
            if (r != null && !r.isSecond()) {
                if (causalFrontierCapture != null && causalFrontierCapture.enumerates(r, m)) {
                    m.link(r, logging);
                }
                boolean ar = m.analyze(r, logging);
                if (ar) {
                    if (logging) {
                        m.getLog().add(LogMode.ANALYZER, "Result: TRUE");
                        logResult(m);
                    }
                    res = true;
                    hypothesis.clear();
                    tempHypothesis.clear();
                } else {
                    m.link(r, logging);
                    ar = m.analyze(r, logging);
                    if (ar) {
                        if (logging) {
                            m.getLog().add(LogMode.ANALYZER, "Result: TRUE");
                            logResult(m);
                        }
                        res = true;
                        hypothesis.clear();
                        tempHypothesis.clear();
                    } else {
                        hypothesis.commit(m.getHypothesis());
                        if (hypothesis.isEmpty()) {
                            if (!m.getTempHypothesis().isEmpty()) {
                                for (IHypothesis tmp : m.getTempHypothesis()) {
                                    IRule rx = getRules().find((Hypothesis) tmp);
                                    if (hypothesis.find(tmp) == null && (rx == null || rx.isDeleted(this))) {
                                        hypothesis.add(tmp);
                                        if (logging) {
                                            log.add(LogMode.ANALYZER, "Hypothesis moved: " + ((Hypothesis) tmp).toString(this));
                                        }
                                    }
                                }
                            }
                        }

                        if (logging) {
                            if (!hypothesis.isEmpty()) {
                                m.getLog().add(LogMode.ANALYZER, String.format("Result: WHO KNOWS? Hypothesis found"));
                            } else {
                                m.getLog().add(LogMode.ANALYZER, "Result: WHO KNOWS? No Hypothesis.");
                            }
                        }
                    }
                }
            } else if (r != null && r.isSecond()) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "Rule already defined: " + r);
                    m.getLog().add(LogMode.ANALYZER, "Result: FALSE");
                    logResult(m);
                }
                res = false;
                hypothesis.clear();
                tempHypothesis.clear();
            }
            tx.rollback();
            return res;
        }
    }

    /**
     * Executes a query whose external parameters are already canonical Terms
     * of this Mind. This avoids value re-parsing at cross-Context runtime
     * boundaries while preserving the historical FALSE-then-TRUE query
     * lifecycle.
     */
    public Boolean queryCanonical(
            String line,
            Queue<ITerm> externals,
            boolean logging) throws Exception {
        if (line == null || line.isEmpty()
                || line.charAt(0) != Enums.SUC) {
            throw new IllegalArgumentException(
                    "Canonical query requires a query source");
        }

        this.logging = logging;
        querySource = line;
        queryPass = QueryPass.SILENCE;
        acceptedRule = null;
        frontierDomains.clear();

        getQueryValues().clear();
        getLog().clear();
        getSolutions().clear();
        getValues().clear();
        getHypothesis().clear();
        hypothesis.clear();
        tempHypothesis.clear();

        Queue<ITerm> source =
                externals == null
                        ? new LinkedList<ITerm>()
                        : new LinkedList<ITerm>(externals);

        Boolean result = null;
        if (!DEBUG_DISABLE_FALSE_CHECK) {
            result = queryCheckFalseCanonical(
                    line,
                    new LinkedList<ITerm>(source),
                    logging);
        }
        if (result == null) {
            result = queryCheckTrueCanonical(
                    line,
                    new LinkedList<ITerm>(source),
                    logging);
        }
        queryResult = result;
        return result;
    }

    /**
     * Extends an ordinary query through direct Context federation, including
     * locally proven free-variable queries whose rows are still incomplete, while preserving the historical FALSE-then-TRUE KANGER
     * lifecycle.
     *
     * <p>Local inference has already run before this method is entered. The
     * federation capability receives the live initiating Mind for unresolved
     * continuation or complete ordinary atomic enumeration. Each pass executes in an isolated ephemeral
     * child; resolved Values/Solutions are published back by the capability,
     * while unresolved passes are discarded without touching the existing
     * local presentation state.</p>
     */
    private Boolean continueFederatedQuery(
            String line,
            Object[] ext,
            boolean logging) throws Exception {
        if (!isStorageUsed()
                || line == null
                || line.length() <= 1
                || line.charAt(0) != Enums.SUC) {
            return null;
        }

        IData data = user.getData();
        if (!(data instanceof IContextFederation)) {
            return null;
        }

        IContextFederation federation =
                (IContextFederation) data;
        if (!federation.hasConnectedContexts()) {
            return null;
        }

        // Local checking may already have logged partial result blocks. The
        // connected pass owns the final presentation; keep its inference trace.
        if (logging && log.getRoot() != null) {
            log.getRoot().removeIf(entry -> entry.getType() == LogMode.SOLVES
                    || entry.getType() == LogMode.VALUES);
        }
        Queue<ITerm> externals = queryExternals(ext);
        boolean conflict = false;

        if (!DEBUG_DISABLE_FALSE_CHECK) {
            IContextFederation.QueryResult opposite =
                    federation.continueFederatedQuery(
                            this,
                            invert(line),
                            new LinkedList<ITerm>(externals),
                            logging);
            conflict = federatedConflict(opposite);
            recordExplainPass(
                    IContextFederation.ExplainPolarity.FALSE_PASS,
                    opposite);
            if (opposite.isResolved()) {
                hypothesis.clear();
                tempHypothesis.clear();
                if (logging) {
                    log.add(
                            LogMode.ANALYZER,
                            "Result: FALSE");
                    logResult(this);
                }
                return false;
            }
        }

        IContextFederation.QueryResult positive =
                federation.continueFederatedQuery(
                        this,
                        line,
                        new LinkedList<ITerm>(externals),
                        logging);
        recordExplainPass(
                IContextFederation.ExplainPolarity.TRUE_PASS,
                positive);
        if (positive.isResolved()) {
            hypothesis.clear();
            tempHypothesis.clear();
            if (logging) {
                log.add(
                        LogMode.ANALYZER,
                        "Result: TRUE");
                logResult(this);
            }
            return true;
        }

        if (conflict || federatedConflict(positive)) {
            hypothesis.clear();
            tempHypothesis.clear();
        }
        return null;
    }

    private static boolean federatedConflict(IContextFederation.QueryResult result) {
        for (IContextFederation.FrontierObservation observation : result.getObservations()) {
            if (observation.getTruth() == IContextFederation.FrontierTruth.CONFLICT) return true;
        }
        return false;
    }

    /** Requests semantic trace capture only for an active ctx explain query. */
    public boolean isExplainQueryActive() {
        return activeExplainPasses != null;
    }

    public IContextFederation.ExplainResult explainQuery(
            String line) throws Exception {
        if (line == null
                || line.isEmpty()
                || line.charAt(0) != Enums.SUC) {
            throw new IllegalArgumentException(
                    "Context explain requires a KANGER query beginning with ?");
        }
        if (!isStorageUsed()
                || !(user.getData() instanceof IContextFederation)) {
            throw new IllegalStateException(
                    "Context explain requires an active Context federation storage");
        }
        if (activeExplainPasses != null) {
            throw new IllegalStateException(
                    "Nested Context explain is not supported");
        }

        IContextFederation federation =
                (IContextFederation) user.getData();
        ArrayList<IContextFederation.ExplainPass> passes =
                new ArrayList<IContextFederation.ExplainPass>();
        activeExplainPasses = passes;
        try {
            Boolean answer =
                    query(line, null, false);
            IContextFederation.FrontierTruth finalTruth =
                    explainTruth(answer, passes);
            IContextFederation.FrontierTruth localTruth =
                    passes.isEmpty()
                            ? finalTruth
                            : IContextFederation.FrontierTruth.UNKNOWN;

            ArrayList<IContextFederation.ValueRow> values =
                    new ArrayList<IContextFederation.ValueRow>();
            for (Map<String, ITerm> row : getValues()) {
                LinkedHashMap<String, String> bindings =
                        new LinkedHashMap<String, String>();
                for (Map.Entry<String, ITerm> binding
                        : row.entrySet()) {
                    ITerm value = binding.getValue();
                    bindings.put(
                            binding.getKey(),
                            value == null
                                    ? ""
                                    : value.toString());
                }
                values.add(
                        new IContextFederation.ValueRow(
                                bindings));
            }

            ArrayList<String> solutions =
                    new ArrayList<String>();
            for (IRule solution : getSolutions()) {
                solutions.add(
                        ((Rule) solution).toString(this));
            }

            return new IContextFederation.ExplainResult(
                    federation.federationSnapshot(),
                    localTruth,
                    finalTruth,
                    passes,
                    values,
                    solutions);
        } finally {
            activeExplainPasses = null;
        }
    }

    private void recordExplainPass(
            IContextFederation.ExplainPolarity polarity,
            IContextFederation.QueryResult continuation) {
        for (IContextFederation.FrontierObservation observation : continuation.getObservations()) {
            int kinds = (observation.getTrueSources().isEmpty() ? 0 : 1)
                    + (observation.getFalseSources().isEmpty() ? 0 : 1)
                    + (observation.getUnknownSources().isEmpty() ? 0 : 1);
            if (kinds > 1 || observation.getTruth() == IContextFederation.FrontierTruth.CONFLICT)
                otherOpinionsPossible = true;
            if (observation.getTruth() != IContextFederation.FrontierTruth.CONFLICT) continue;
            boolean seen=false;
            for (IContextFederation.FrontierObservation old : queryConflicts)
                if (old.getQuerySource().equals(observation.getQuerySource())) { seen=true; break; }
            if (!seen) queryConflicts.add(observation);
        }
        if (activeExplainPasses != null) {
            activeExplainPasses.add(
                    new IContextFederation.ExplainPass(
                            polarity,
                            continuation));
        }
    }

    /** Detached conflicts observed by the last query; safe rows remain ordinary Values. */
    public List<IContextFederation.FrontierObservation> getQueryConflicts() {
        return Collections.unmodifiableList(new ArrayList<>(queryConflicts));
    }

    private IContextFederation.FrontierTruth explainTruth(
            Boolean answer,
            List<IContextFederation.ExplainPass> passes) {
        if (answer != null) {
            return answer.booleanValue()
                    ? IContextFederation.FrontierTruth.TRUE
                    : IContextFederation.FrontierTruth.FALSE;
        }
        for (IContextFederation.ExplainPass pass : passes) {
            for (IContextFederation.FrontierObservation observation
                    : pass.getContinuation().getObservations()) {
                if (observation.getTruth()
                        == IContextFederation.FrontierTruth.CONFLICT) {
                    return IContextFederation.FrontierTruth.CONFLICT;
                }
            }
        }
        return IContextFederation.FrontierTruth.UNKNOWN;
    }

    public Boolean query(String line, Object[] ext, boolean logging) throws Exception {
        commandNoOp = false;
        if (!line.isEmpty() && line.charAt(0)!=Enums.SUC) requireWritableContext();
        user.getContextOpinionSession().invalidate();
        Queue<ITerm> opinionParameters = line.charAt(0) == Enums.SUC && isStorageUsed()
                && user.getData() instanceof IContextFederation ? queryExternals(ext) : new LinkedList<ITerm>();
        this.logging = logging;
        String previousDescription = operationDescription;
        int operation = line.charAt(0);
        operationDescription = operation == Enums.ANT ? RevisionDescriptions.automatic("Accepted: " + line)
                : operation == Enums.DEL ? RevisionDescriptions.automatic("Deleted: " + line)
                : operation == Enums.INS ? RevisionDescriptions.automatic("Restored: " + line) : null;
        try {
            Boolean res = null;
            acceptedRule = null;
            frontierDomains.clear();
            queryConflicts.clear();
            otherOpinionsPossible = false;

            getQueryValues().clear();
            getLog().clear();
            getSolutions().clear();
            getValues().clear();
            getHypothesis().clear();

            long queryStart = System.currentTimeMillis();

            int key = line.charAt(0);
            switch (key) {

                case Enums.INS:
                    res = queryInsert(line, ext, logging);
                    break;

                case Enums.ANT:
                    res = queryAccept(line, ext, logging);
                    break;

                case Enums.DEL:
                    res = queryDelete(line, ext, logging);
                    break;

                case Enums.FOO:
                    try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
                        Mind m = tx.mind();
                        IOperation o = Parser.implement(line, m, null);
                        if (o != null) {
                            IOperation x = m.getLibrary().add(o);
                            if (x.getId() == o.getId()) {
                                m.getLog().add(LogMode.ANALYZER, "Function updated: " + x.toString());
                            } else {
                                m.getLog().add(LogMode.ANALYZER, "New function implemented: " + x.toString());
                            }
                            tx.commit();
                            res = true;
                        } else {
                            m.getLog().add(LogMode.ANALYZER, "Implementation error: " + line);
                            tx.rollback();
                            res = false;
                        }
                    }
                    break;
                case Enums.SUC:
                    hypothesis.clear();
                    tempHypothesis.clear();
                    if (line.length() == 1) {
                        res = queryCheck(logging);
                    } else {
                        if (!DEBUG_DISABLE_FALSE_CHECK) {
                            res = queryCheckFalse(line, ext, logging);
                        }
                        if (res == null) {
                            res = queryCheckTrue(line, ext, logging);
                        }
                        if (res == null || (!getValues().isEmpty()
                                && isStorageUsed() && user.getData() instanceof IContextFederation
                                && ((IContextFederation) user.getData()).hasConnectedContexts())) {
                            res = continueFederatedQuery(
                                    line, ext, logging);
                        }

                    }
                    break;
            }

            if (logging && !queryConflicts.isEmpty()) {
                StringBuilder summary=new StringBuilder("Result: ").append(res==null ? "CONFLICT" : res ? "TRUE" : "FALSE");
                for (IContextFederation.FrontierObservation conflict : queryConflicts)
                    summary.append(Enums.LINE_SEPARATOR).append("Conflicting substitution: ").append(conflict.getQuerySource());
                log.add(LogMode.ANALYZER,summary.toString());
            } else if (logging && key == Enums.SUC && line.length() > 1 && res == null) {
                // An unresolved federated pass can leave a diagnostic entry
                // after the local result. Publish the final query status last.
                log.add(LogMode.ANALYZER, hypothesis.isEmpty()
                        ? "Result: WHO KNOWS? No Hypothesis."
                        : "Result: WHO KNOWS? Hypothesis found");
            }
            if (logging) {
                log.add(LogMode.TIMING, "* QUERY Processing time \t" + ((System.currentTimeMillis() - queryStart) / 1000.0));
            }

            if (line.charAt(0) == Enums.SUC && line.length() > 1 && isStorageUsed()) {
                if (res == null && user.getData() instanceof IContextFederation
                        && ((IContextFederation) user.getData()).hasConnectedContexts())
                    otherOpinionsPossible = true;
                user.getContextOpinionSession().remember(this, line, opinionParameters);
                if (logging && otherOpinionsPossible) log.add(LogMode.COMMON, "Other opinions may be available (ctx opinions)");
            }
            return res;
        } finally {
            operationDescription = previousDescription;
        }
    }

    private void removeResult(Set<IRule> set, boolean logging) throws Exception {
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(this)) {
            Mind m = tx.mind();

            for (IRule r : set) {
                ((Rule) r).setDeleted(true, m);
            }
            for (IRule r : m.getRules()) {
                if (r.isGenerated() && !r.isDeleted(m)) {
                    set.add(r);
                    ((Rule) r).setDeleted(true, m);
                }
            }

            m.link(null, logging);
            Boolean ar = m.analyze(null, logging);

            Set<IRule> success = new HashSet<>();
            for (IRule r : set) {
                if (r.isDeleted(m)) {
                    success.add(r);
                }
            }

            if (ar) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "ERROR: Collisions in Program");
                }
                tx.rollback();
            } else if (success.isEmpty()) {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "WARNING: No rules have been deleted");
                }
                tx.rollback();
            } else {
                if (logging) {
                    m.getLog().add(LogMode.ANALYZER, "SUCCESS: Deleted " + success.size() + " rules");
                    for (IRule r : success) {
                        m.getLog().add(LogMode.SOLVES, String.format("\tDeleted %03d: %s", r.getId(), r.toString()));
                    }
                }
                tx.commit();
            }
        }
    }

    private void logResult(Mind mind) {
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solutions (" + mind.getSolutions().size() + "):");
            for (IRule log : mind.getSolutions()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", log.getId(), log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
            mind.getLog().add(LogMode.VALUES, "Values (" + mind.getValues().size() + "):");
            int i = 0;
            for (Map<String, ITerm> map : mind.getValues()) {
                String s = String.format("\tRow %03d: ", ++i);
                for (Map.Entry<String, ITerm> row : map.entrySet()) {
                    if (!s.endsWith(" ")) {
                        s += " ";
                    }
                    s += row.getKey() + "=" + formatValue(row.getValue());
                }
                mind.getLog().add(LogMode.VALUES, s);
            }
        }
    }

    private String formatValue(Object o) {
        if (o instanceof byte[]) {
            String s = "#";
            for (byte x : ((byte[]) o)) {
                s += String.format("%02X", x & 0xFF);
            }
            return s;
        } else {
            return o.toString();
        }
    }


    public boolean isLogging() {
        return logging;
    }

    public Calculator getCalculator() {
        return calculator;
    }

    public LinkerStatistics getLinkerStatistics() {
        return lastLinkerStatistics;
    }

    public TSolve findTSolve(List<TValue> list) throws Exception {
        TVariableSet ts = new TVariableSet(list, this);
        if (ruleSolvesInternal().containsKey(ts)) {
            TSolve tmp = new TSolve(list, this);
            for (TSolve t : ruleSolvesInternal().get(ts)) {
                if (tmp.equalsTo(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    public TSolve addTSolve(List<TValue> list) throws Exception {
        TSolve tmp = findTSolve(list);
        if (tmp != null) {
            SemanticEffectTelemetry.recordTSolveCandidate(false);
            return tmp;
        } else {
            tmp = new TSolve(list, this);
            TVariableSet ts = new TVariableSet(tmp, this);
            if (!ruleSolvesInternal().containsKey(ts)) {
                ruleSolvesInternal().put(ts, new ArrayList<>());
            }
            ruleSolvesInternal().get(ts).add(tmp);
            ++ruleSolvesVersion;
            SemanticEffectTelemetry.recordTSolve(list);
            SemanticEffectTelemetry.recordTSolveCandidate(true);
            return tmp;
        }
    }

    public String getSourceCode() throws Exception {
        String str = "";
        IData sourceData = user.getData();
        if (isStorageUsed()
                && sourceData instanceof IContextFederation) {
            IContextFederation federation =
                    (IContextFederation) sourceData;
            str += ContextSourceMetadata.canonicalHeader(
                    federation.sourceDependencies(),
                    Enums.LINE_SEPARATOR);
            str += Enums.LINE_SEPARATOR;
        }

        SortedMap<Long, IRule> map = new TreeMap<>();
        for (IRule r : getRules()) {
            if (!r.isGenerated()) {
                map.put(r.getId(), r);
            }
        }
        Comment c = getComments().get(CommentFactory.HEADER_ID);
        if (c != null) {
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        for (IRule r : map.values()) {
            c = getComments().get(r.getId());
            if (c != null) {
                str += Enums.LINE_SEPARATOR;
                for (String s : c.getComment().split("\\R")) {
                    str += s + Enums.LINE_SEPARATOR;
                }
            }
            for (String s : r.getOrigin().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        for (IOperation o : getLibrary()) {
            if (!o.isDeleted(this)) {
                str += Enums.LINE_SEPARATOR;
                for (String s : o.asString().split("\\R")) {
                    str += s + Enums.LINE_SEPARATOR;
                }
            }
        }
        c = getComments().get(CommentFactory.FOOTER_ID);
        if (c != null) {
            str += Enums.LINE_SEPARATOR;
            for (String s : c.getComment().split("\\R")) {
                str += s + Enums.LINE_SEPARATOR;
            }
        }
        return str;
    }

    @Override
    public IRule getAcceptedRule() {
        return acceptedRule;
    }

    @Override
    public int getFloodControlLimit() {
        return floodControlLimit;
    }

    @Override
    public void setFloodControlLimit(int floodControlLimit) {
        this.floodControlLimit = floodControlLimit;
    }

    @Override
    public int getTransactionLevel() {
        int level = 0;
        for (IMind m = this; m.getNext() != null; m = m.getNext()) {
            ++level;
        }
        return level;
    }

    @Override
    public boolean isEmptyLevel() {
        for (IRule r : rules) {
            if (!r.isDeleted(this) && ((Rule) r).getMindId() == id) {
                return false;
            }
        }
        for (IOperation s : library) {
            if (!s.isDeleted(this) && ((Operation) s).getMindId() == id) {
                return false;
            }
        }
        return true;
    }

    public boolean isUnitDeleted(IUnit unit) {
        UnitType unitType = unit.getUnitType();
        long unitId = unit.getId();
        for (IMind level = this; level != null; level = level.getNext()) {
            Mind current = (Mind) level;
            synchronized (current.locker) {
                Set<Long> restoredIds = current.restored.get(unitType);
                if (restoredIds != null && restoredIds.contains(unitId)) {
                    return false;
                }
                Set<Long> deletedIds = current.deleted.get(unitType);
                if (deletedIds != null && deletedIds.contains(unitId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUnitDeleted(IUnit unit, boolean on) {
        synchronized (locker) {
            UnitType unitType = unit.getUnitType();
            long unitId = unit.getId();
            if (on) {
                if (!isUnitDeleted(unit)) {
                    Set<Long> restoredIds = restored.get(unitType);
                    if (restoredIds != null) {
                        restoredIds.remove(unitId);
                    }
                    if (!isUnitDeleted(unit)) {
                        Set<Long> deletedIds = deleted.get(unitType);
                        if (deletedIds == null) {
                            deletedIds = new HashSet<>();
                            deleted.put(unitType, deletedIds);
                        }
                        deletedIds.add(unitId);
                    }
                }
            } else if (isUnitDeleted(unit)) {
                Set<Long> deletedIds = deleted.get(unitType);
                if (deletedIds != null) {
                    deletedIds.remove(unitId);
                }
                if (unit.getMindId() != id || isUnitDeleted(unit)) {
                    Set<Long> restoredIds = restored.get(unitType);
                    if (restoredIds == null) {
                        restoredIds = new HashSet<>();
                        restored.put(unitType, restoredIds);
                    }
                    restoredIds.add(unitId);
                }
            }
        }
    }

    @Override
    public IMind getTop() {
        IMind m = this;
        for (; m.getNext() != null; m = m.getNext()) ;
        return m;
    }

    @Override
    public IMind useStorage(String name) throws Exception {
        return user.use(this, name);
    }

    @Override
    public boolean isStorageExists(String name) throws Exception {
        return user.getData().exists(name);
    }

    @Override
    public IMind closeStorage() throws Exception {
        return user.close(this);
    }

    @Override
    public IMind clearWorkspace() throws Exception {
        user.getContextOpinionSession().invalidate();
        return user.clear(this);
    }

    @Override
    public IMind reindexStorage(String name) throws Exception {
        return user.reindex(null, this, name);
    }

    @Override
    public IMind reindexStorage(String name, IReactor<String> reactor) throws Exception {
        return user.reindex(reactor, this, name);
    }

    //TODO: Обработка удаления внешней базы
    @Override
    public IMind removeStorage(String name) throws Exception {
        return user.remove(this, name);
    }

    @Override
    public boolean isStorageUsed() {
        return !user.isClosed();
    }

    @Override
    public String getStorageName() {
        return user.getStorageName();
    }

    @Override
    public Collection<String> getStoragesList() {
        return user.getStoragesList();
    }

    /**
     * Consumes exactly one child reservation and returns whether the current
     * Mind has reached root quiescence. This is the irreversible settlement
     * boundary: callers must mark the reservation finished immediately after
     * this method returns, before pack/update/flush is attempted.
     */
    private boolean finishTransactionReservationLocked() {
        if (transactionCounter <= 0) {
            throw new IllegalStateException("Transaction counter underflow for Mind " + id);
        }
        --transactionCounter;
        return next == null && transactionCounter == 0;
    }

    /**
     * Runs root-only post-settlement work. Failure here cannot reopen or retry
     * the child transaction because its reservation has already been consumed.
     */
    private void finalizeTransactionRootLocked(boolean rootQuiescent) throws Exception {
        if (rootQuiescent) {
            // A pinned generation may retain unused vocabulary. Opening or
            // querying it is not permission to collect its persistent records.
            if (isStorageUsed() && user.getData().isReadOnly()) return;
            pack();
            update();
        }
    }

    private void finishTransactionLocked() throws Exception {
        boolean rootQuiescent = finishTransactionReservationLocked();
        finalizeTransactionRootLocked(rootQuiescent);
    }

    private void abortTransactionStart() throws Exception {
        synchronized (locker) {
            finishTransactionLocked();
        }
    }

    public int incTransactionCounter() {
        synchronized (locker) {
            return ++transactionCounter;
        }
    }

    /**
     * Package-private lifecycle probe used by the owning {@link User}. A root
     * Mind can be referenced directly while one or more child reservations are
     * still open, so {@link #getTransactionLevel()} alone is not sufficient to
     * prove storage-lifecycle quiescence.
     */
    int pendingTransactionCount() {
        synchronized (locker) {
            return transactionCounter;
        }
    }

    boolean hasPendingTransactions() {
        return pendingTransactionCount() > 0;
    }

    @Override
    public ILogEntry getCurrentLogRecord(LogMode mode) {
        ILogEntry e = log.getCurrent(mode);
        if(e == null) {
            e = new LogEntry(mode, "No events was recorded");
        }
        return e;
    }

    @Override
    public void clearLog() {
        log.clear();
    }

    @Override
    public void optimizeHypothesis() throws Exception {
        hypothesis.optimize();
    }

    public List<List<String>> formatTree(IRule r) throws Exception {
        List<List<String>> list = new ArrayList<>();
        int depth = 0;
        for (List<Domain> t : ((Rule) r).getTree()) {
            List<String> v = new ArrayList<>();
            list.add(v);
            int len = 0;
            for (Domain d : t) {
                String s = d.toString();
                len = Math.max(len, s.length());
                v.add(s);
            }
            depth = Math.max(depth, v.size());
            for (int i = 0; i < v.size(); ++i) {
                String s = v.get(i);
                while (s.length() < len) {
                    s += " ";
                }
                v.set(i, s);
            }
        }
        for (List<String> v : list) {
            int len = v.get(0).length();
            String s = " ";
            while (s.length() < len) {
                s += " ";
            }
            while (v.size() < depth) {
                v.add(s);
            }
        }
        return list;
    }

    /**
     * Returns whether the current transaction may emit hypotheses containing
     * existential/C-variable terms.
     */
    public boolean includeAbstractiveHypothesis() {
        return includeAbstractiveHypothesis;
    }

    /**
     * Enables or disables the experimental abstractive-hypothesis policy for
     * this Mind. Child transactions inherit the selected value. The default is
     * false; callers must opt in explicitly.
     */
    public void includeAbstractiveHypothesis(boolean includeAbstractiveHypothesis) {
        this.includeAbstractiveHypothesis = includeAbstractiveHypothesis;
    }
}
