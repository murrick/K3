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

import org.kanger.enums.DataType;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.LogStore;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Интерпретатор насыщенного состояния Mind и граница формирования ответа.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Analyzer} запускается после
 * работы {@link Linker} и читает уже сформированные Rule, TValue и variable
 * bindings. Он распознаёт рассчитанные домены, совпадения противоположной
 * полярности, специальный запрос {@code rule(1)}, наполняет query-local stores
 * решений и значений и определяет, закрыты ли все ветви запроса. Класс не
 * выполняет saturation, не создаёт canonical Rule identity и не владеет
 * транзакцией.</p>
 *
 * <p><strong>Жизненный цикл ответа.</strong> {@link #analyze(Rule, boolean)}
 * очищает {@link org.kanger.stores.SolutionsStore} и
 * {@link org.kanger.stores.ValuesStore} текущего Mind, затем проверяет всю
 * видимую базу. Переданный параметр {@code rule} сохраняется как historical
 * compatibility surface и не ограничивает текущий полный анализ. Результаты
 * существуют в runtime-контексте Mind и не являются persistence publication.</p>
 *
 * <p><strong>Совпадения.</strong> Calculated domain считается совпадением и
 * публикует solves, когда сам домен или связанная TVariable относится к запросу.
 * Для обычного домена выбираются кандидаты противоположной полярности через
 * RuleFactory candidate index; при {@code rule(1)} используется полный Rule-set
 * и сравнение по точному {@code long} ID. Deleted Rule и текущий Rule исключаются.
 * Historical ID-order filter предотвращает повторную симметричную обработку
 * одной пары и является частью существующей семантики.</p>
 *
 * <p><strong>Закрытие запроса.</strong> Query domain, не имеющий совпадения и
 * не помеченный used, попадает в набор unresolved branches. Если такие ветви
 * остаются и ни один calculated result не закрыл запрос, общий ответ
 * принудительно становится false. Это проверка полноты результата, а не
 * доказательство отсутствия отдельных локальных совпадений.</p>
 *
 * <p><strong>Гипотезы.</strong> Только при отсутствии основного результата
 * Analyzer рассматривает stored, complete, non-query Rule текущего или
 * восстановленного Mind. Abstractive candidates фильтруются настройкой
 * {@link Mind#includeAbstractiveHypothesis()}; дубликаты в RuleFactory и
 * HypothesisStore не добавляются. Гипотеза является runtime semantic effect и
 * не заменяет persistent Rule.</p>
 *
 * <p><strong>Порядок и side effects.</strong> Обход Rule-set, candidate order,
 * special-ID selection, добавление solutions/values и admission hypotheses
 * являются историческим semantic kernel. Документация не разрешает менять их
 * порядок, симметрию или критерии. Logging добавляет только Analyzer/Timing
 * записи и не влияет на результат.</p>
 *
 * <p><strong>Concurrency.</strong> Экземпляр привязан к одному {@link Mind} и
 * его query-local stores. Concurrent analyze одного Mind не поддерживается;
 * внешняя сторона должна сериализовать link/analyze lifecycle.</p>
 *
 * @see Linker
 * @see Mind
 * @see Hypothesis
 */
public class Analyzer {


    private final transient Mind mind;
    private final LogStore log;
    private final List<ContextQualification.CollisionWitness> collisions =
            new ArrayList<ContextQualification.CollisionWitness>();

    public Analyzer(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }


    public boolean analyze(Rule rule, boolean logging) throws Exception {
        boolean result = false;
        collisions.clear();
        mind.clearFrontierDomains();

        long start = System.currentTimeMillis();

        if (logging) {
            log.add(LogMode.ANALYZER, "============= ANALYZER ====================");
        }

        mind.getSolutions().clear();
        mind.getValues().clear();

        result = checkDatabase(null, logging);

        if (!result) {

            boolean occurs = false;
            for (IRule r : mind.getRules()) {
                if (r.isStored()
                        && !r.isQuery()
                        && !r.isDeleted(mind)
                        && ((Rule) r).getDomain().isComplete()
                        && (((Rule) r).getMindId() == mind.getId() || r.isRestored(mind))) {

                    if (!mind.includeAbstractiveHypothesis()) {
                        for (IArgument a : r.getArguments()) {
                            if (a.getValue(mind).isCVariable()) {
                                r = null;
                                break;
                            }
                        }
                    }

                    if (r != null) {
                        Hypothesis tmp = new Hypothesis(r, mind);
                        IRule rx = mind.getRules().find(tmp);
                        if (mind.getHypothesis().find(tmp) == null && (rx == null || rx.isDeleted(mind))) {

                            mind.getHypothesis().add(tmp);
                            occurs = true;
                            if (logging) {
                                log.add(LogMode.ANALYZER, "Hypothesis assumed: " + tmp.toString(mind));
                            }
                        }
                    }
                }
            }

            if (occurs && logging) {
                log.add(LogMode.ANALYZER, "===========================================");
            }
        }

        // Several substitutions can produce the same unordered SET record.
        // Recover their correlated tuples rather than the record's last solve stamp.
        if (result && rule != null && rule.isQuery() && rule.getTree().size() == 1
                && rule.getTree().get(0).size() == 1) {
            Domain domain = rule.getTree().get(0).get(0);
            boolean setPattern = false;
            for (org.kanger.units.Function function : domain.getArguments().getFunctions(mind)) {
                if (function.getBinding() == org.kanger.enums.FunctionBinding.INFRASTRUCTURE
                        && "_set".equals(function.getName(mind).getValue())) setPattern = true;
            }
            if (setPattern) {
                List<TVariable> variables = domain.getArguments().getTVariables(mind);
                for (List<org.kanger.units.TSolve> group : mind.ruleSolvesInternal().values()) {
                    for (org.kanger.units.TSolve solve : group) {
                        List<TValue> row = solve.getSolve();
                        if (row.size() != variables.size() || row.isEmpty()) continue;
                        Set<TVariable> bound = new HashSet<>();
                        for (TValue value : row) {
                            TVariable variable = value.getTVar(mind);
                            if (variable.getRuleId() == rule.getId()
                                    && SemanticTermSnapshot.isOrdinaryValue(value.getValue(mind)))
                                bound.add(variable);
                        }
                        if (bound.containsAll(variables)) mind.getValues().add(row);
                    }
                }
            }
        }

        if (logging) {
            log.add(LogMode.TIMING, "* Analyzing time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
        }
        return result;
    }

    private Collection<IRule> candidatesFor(Rule p, boolean selectById) throws Exception {
        if (!selectById) {
            return mind.getRules().findByDomain(
                    p.getDomain().getPredicateId(),
                    !p.getDomain().isAntc());
        }

        List<IRule> result = new ArrayList<>();
        for (IRule rule : mind.getRules()) {
            result.add(rule);
        }
        return result;
    }

    private boolean checkRight(Rule p, Set<Rule> orfans, Set<Long> list, boolean logging) throws Exception {
        boolean result = false;
        if (p.getDomain().isCalculated(mind)) {

            boolean valid = p.getDomain().isQuery(mind);
            if (!valid) {
                for (TValue v : p.getSolves()) {
                    if (v.getTVar(mind).isQuery(mind)) {
                        valid = true;
                        break;
                    }
                }
            }

            if (valid) {
                mind.getValues().add(p.getSolves());
            }

            if (logging) {
                log.add(LogMode.ANALYZER, "Calculated coincidence: ");
                log.add(LogMode.ANALYZER, "\t" + p.toString());
                log.add(LogMode.ANALYZER, "===========================================");
            }
            result = true;
        } else {
            boolean selectById = "rule(1)".equals(
                    p.getDomain().getPredicate(mind).toString(mind))
                    && p.getDomain().get(0).getValue(mind).getType() == DataType.NUMERIC;

            for (IRule q : candidatesFor(p, selectById)) {
                if (q.isDeleted(mind) || q.getId() == p.getId()) {
                    continue;
                }

                if (selectById) {
                    if (q.getId() == ((Double) p.getDomain().get(0).getValue(mind).getValue()).longValue()) {
                        mind.getSolutions().add(q);
                        if (logging) {
                            log.add(LogMode.ANALYZER, "Select by id: ");
                            log.add(LogMode.ANALYZER, "\t" + q.toString());
                            log.add(LogMode.ANALYZER, "===========================================");
                        }
                    }
                    result = true;

                } else {

                    if (!q.isStored() || (list == null && q.getId() > p.getId()) || (list != null && list.contains(q.getId()))) {
                        continue;
                    }
                    if (p.getDomain().equalsBase(((Rule) q).getDomain())
                            && p.getDomain().isAntc() != ((Rule) q).getDomain().isAntc()) {
                        recordCollision(p, (Rule) q);
                        if (p.getDomain().isQuery(mind) && p.getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(q);
                            mind.getValues().add(p.getSolves());
                        } else if (((Rule) q).getDomain().isQuery(mind) && ((Rule) q).getDomain().getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(p);
                            mind.getValues().add(((Rule) q).getSolves());

                        }
                        // Abstract collisions establish truth without a concrete tuple.
                        // Do not export current TVars from unrelated dictionary rotations.

                        if (logging) {
                            log.add(LogMode.ANALYZER, "Database coincidence: ");
                            log.add(LogMode.ANALYZER, String.format("\t%03d: %s", p.getId(), p.toString()));
                            log.add(LogMode.ANALYZER, String.format("\t%03d: %s", q.getId(), q.toString()));
                            log.add(LogMode.ANALYZER, "===========================================");
                        }
                        result = true;
                    }
                }
            }

            if (!result && p.getDomain().isQuery(mind) && !p.getDomain().isUsed(mind)) {
                orfans.add(p);
            }
        }
        return result;
    }


    private void recordCollision(Rule left, Rule right) throws Exception {
        CollisionProofCapture.record(mind, left, right);
        ContextQualification.CollisionWitness witness =
                new ContextQualification.CollisionWitness(
                        diagnosticRule(left), diagnosticRule(right));
        if (!collisions.contains(witness)) {
            collisions.add(witness);
        }
    }

    private String diagnosticRule(Rule rule) throws Exception {
        String origin = rule.getOrigin();
        if (origin != null && !origin.trim().isEmpty()) {
            return origin.trim();
        }
        return rule.getDomain().toString(mind);
    }

    List<ContextQualification.CollisionWitness> getCollisionWitnesses() {
        return new ArrayList<ContextQualification.CollisionWitness>(collisions);
    }

    public boolean checkDatabase(Set<Long> list, boolean logging) throws Exception {

        boolean result = false;
        boolean calculated = false;

        Set<Rule> orfans = new HashSet<>();

        for (IRule p : mind.getRules()) {
            if (!p.isDeleted(mind) && p.isStored() && (list == null || list.contains(p.getId())) && checkRight((Rule) p, orfans, list, logging)) {
                if (((Rule) p).getDomain().isCalculated(mind)) {
                    calculated = true;
                }
                result = true;
            }
        }

        List<FrontierDomain> frontier =
                new ArrayList<FrontierDomain>();

        /*
         * Historical stored-query orphans remain the first frontier source.
         * They include ground ordinary queries such as ?male(Tom).
         */
        for (Rule unresolved : orfans) {
            addFrontier(
                    unresolved.getDomain(),
                    frontier);
        }

        /*
         * A query Domain containing an unbound TVariable may remain part of a
         * non-stored query Rule. Such a Rule is intentionally skipped by the
         * historical checkDatabase loop above, even though the Domain itself
         * is unresolved and externally answerable. Expose that Domain without
         * changing Analyzer truth/hypothesis semantics.
         */
        for (IRule candidate : mind.getRules()) {
            Rule query = (Rule) candidate;
            if (query.isDeleted(mind)
                    || query.isStored()
                    || !query.isQuery()) {
                continue;
            }
            for (List<Domain> branch : query.getTree()) {
                for (Domain domain : branch) {
                    if (domain.isQuery(mind)
                            && !domain.isUsed(mind)
                            && !domain.isCalculated(mind)) {
                        addFrontier(
                                domain,
                                frontier);
                    }
                }
            }
        }

        /*
         * Linker may materialize a single demanded premise as a generated
         * stored succedent Rule. That Rule intentionally no longer carries the
         * historical query flag, but its operation-local mindId identifies it
         * as demand produced by this saturation rather than inherited durable
         * knowledge.
         */
        for (IRule candidate : mind.getRules()) {
            Rule generated = (Rule) candidate;
            if (generated.isDeleted(mind)
                    || !generated.isStored()
                    || !generated.isGenerated()
                    || generated.getMindId() != mind.getId()) {
                continue;
            }
            Domain domain = generated.getDomain();
            if (!domain.isAntc()
                    && domain.isComplete()
                    && !domain.isUsed(mind)
                    && !domain.isCalculated(mind)) {
                addFrontier(
                        domain,
                        frontier);
            }
        }

        mind.replaceFrontierDomains(frontier);

        // Контроль закрытия всех веток запроса
        if (!orfans.isEmpty() && !calculated) {
            result = false;
            if (logging) {
                for (Rule r : orfans) {
                    log.add(LogMode.ANALYZER, "Unresolved: \t" + r.getDomain().toString());
                }
                log.add(LogMode.ANALYZER, "-------------------------------------------");
            }
        }
        return result;
    }

    private void addFrontier(
            Domain domain,
            List<FrontierDomain> frontier) throws Exception {
        if (!isExternalizable(domain)) {
            return;
        }
        FrontierDomain descriptor =
                FrontierDomain.capture(domain, mind);
        if (descriptor == null) {
            return;
        }
        for (FrontierDomain existing : frontier) {
            if (existing.semanticallyEquivalent(
                    descriptor)) {
                return;
            }
        }
        frontier.add(descriptor);
    }

    private boolean isExternalizable(Domain domain)
            throws Exception {
        if (domain.getPredicate(mind).isSystem(mind)) {
            return false;
        }
        return !"rule(1)".equals(
                domain.getPredicate(mind).toString(mind));
    }
}
