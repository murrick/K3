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
 */

package org.kanger;

import org.kanger.enums.DataType;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.LogStore;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Quznetsov on 26.05.15.
 */
public class Analyzer {


    private final transient Mind mind;
    private final LogStore log;

    public Analyzer(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }


    public boolean analyze(Rule rule, boolean logging) throws Exception {
        boolean result = false;

        long start = System.currentTimeMillis();

        if (logging) {
            log.add(LogMode.ANALYZER, "============= ANALYZER ====================");
        }

        mind.getSolutions().clear();
        mind.getValues().clear();

        result = checkDatabase(null, logging, rule);

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

        if (logging) {
            log.add(LogMode.TIMING, "* Analyzing time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
        }
        return result;
    }

    private Collection<IRule> candidatesFor(Rule p,
                                            boolean selectById,
                                            boolean observePlanning) throws Exception {
        Collection<IRule> result;
        if (!selectById) {
            result = mind.getRules().findByDomain(
                    p.getDomain().getPredicateId(),
                    !p.getDomain().isAntc());
        } else {
            List<IRule> all = new ArrayList<>();
            for (IRule rule : mind.getRules()) {
                all.add(rule);
            }
            result = all;
        }

        if (observePlanning) {
            SemanticPlanningTelemetry.record(
                    p,
                    mind,
                    SemanticYieldPlanner.estimate(
                            p, result, mind, selectById));
        }
        return result;
    }

    private boolean checkRight(Rule p,
                               Set<Rule> orfans,
                               Set<Long> list,
                               boolean logging,
                               Rule planningRule) throws Exception {
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
            boolean observePlanning = planningRule != null
                    && planningRule.getId() == p.getId();

            for (IRule q : candidatesFor(p, selectById, observePlanning)) {
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
                        if (p.getDomain().isQuery(mind) && p.getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(q);
                            mind.getValues().add(p.getSolves());
                        } else if (((Rule) q).getDomain().isQuery(mind) && ((Rule) q).getDomain().getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(p);
                            mind.getValues().add(((Rule) q).getSolves());
                        } else {
                            List<TValue> vList = new ArrayList<>();
                            for (TVariable t : mind.getTVars()) {
                                if (t.isQuery(mind)) {
                                    if (!t.isEmpty()) {
                                        vList.add(t.getCurrent());
                                    } else {
                                        vList.clear();
                                        break;
                                    }
                                }
                            }
                            if (!vList.isEmpty()) {
                                mind.getValues().add(vList);
                            }
                        }

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

    public boolean checkDatabase(Set<Long> list, boolean logging) throws Exception {
        return checkDatabase(list, logging, null);
    }

    private boolean checkDatabase(Set<Long> list,
                                  boolean logging,
                                  Rule planningRule) throws Exception {

        boolean result = false;
        boolean calculated = false;

        Set<Rule> orfans = new HashSet<>();

        for (IRule p : mind.getRules()) {
            if (!p.isDeleted(mind)
                    && p.isStored()
                    && (list == null || list.contains(p.getId()))
                    && checkRight((Rule) p, orfans, list, logging, planningRule)) {
                if (((Rule) p).getDomain().isCalculated(mind)) {
                    calculated = true;
                }
                result = true;
            }
        }

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
}
