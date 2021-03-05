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
import org.kanger.units.Rule;
import org.kanger.units.TValue;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Quznetsov on 26.05.15.
 */
public class Analyzer {


    private final transient Mind mind;
    private final LogStore log;

    public Analyzer(Mind mind) {
        this.mind = mind;
        this.log = (LogStore) mind.getLog();
    }


    public boolean analyze(Rule rule, boolean logging) throws Exception {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

        if (logging) {
            log.add(LogMode.ANALYZER, "============= ANALYZER ====================");
        }

        mind.getSolutions().clear();
        mind.getValues().clear();

        result = checkDatabase(null /*right*/, logging);

        if (!result) {

            boolean occurs = false;
            for (IRule r : mind.getRules()) {
                if (r.isStored() && !r.isDeleted(mind) && (((Rule) r).getMindId() == mind.getId() || r.isRestored(mind))) {

//                    if (r.getMindId() != mind.getId() && !r.isRestored(mind)) {
//                        continue;
//                    }
//                    Domain d = r.getDomain();
                    for (IArgument a : r.getArguments()) {
                        if (a.isEmpty(mind) || (a.getValue(mind).isCVariable() /*&& a.getValue(mind).getMindId() != mind.getId()*/)) {
                            r = null;
                            break;
                        }
                    }
                    if (r != null && !r.isQuery()) { //d.isQuery(mind)) {
                        Hypothesis tmp = new Hypothesis(r, mind);
                        IRule rx = mind.getRules().find(tmp);
                        if (mind.getHypothesis().find(tmp) == null && (rx == null || rx.isDeleted(mind))) {
//                            && mind.getHypothesisStore().find(/*null,*/ !d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                            Hypothesis h = mind.getHypothesisStore().add(/*true,*/ !d.isAntc(), d.isQuery(mind), d.getPredicate(), d.getArguments());
//                            tmp.setAntc(true);
                            mind.getHypothesis().add(tmp);
                            occurs = true;
                            if (logging) {
                                log.add(LogMode.ANALYZER, "Hypothesis assumed: " + tmp.toString());
                            }
                        }
                    }
//                } else if (!r.isDeleted() && r.getTree().size() == 1) {
//                    for(Domain d : r.getTree().get(0)) {
//                        Hypothesis tmp = new Hypothesis(d, mind);
//                        if (mind.getHypothesisStore().find(tmp) == null && mind.getRights().find(tmp) == null) {
////                            && mind.getHypothesisStore().find(/*null,*/ !d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
////                            Hypothesis h = mind.getHypothesisStore().add(/*true,*/ !d.isAntc(), d.isQuery(mind), d.getPredicate(), d.getArguments());
////                            tmp.setAntc(true);
//                            mind.getHypothesisStore().add(tmp);
//                            occurs = true;
//                            if (logging) {
//                                log.add(LogMode.ANALIZER, "Hypothesis assumed: " + tmp.toString());
//                            }
//                        }
//                    }
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
//            if (p.getDomain().getArguments().get(2).getValue(mind).getType() == DataType.NUMERIC &&
//                    (1011.0 == (double) p.getDomain().getArguments().get(2).getValue(mind).getValue())) {
//                System.err.println("!");
//            }

//            boolean trigger = false;
            for (IRule q : mind.getRules()) {

//                if (p.getDomain().equalsBase(q.getDomain())
//                        && p.getDomain().isAntc() != q.getDomain().isAntc()) {
//                    System.err.println("!!");
//                }

                if (q.isDeleted(mind) || q.getId() == p.getId()) {
                    continue;
                }

                if ("rule(1)".equals(p.getDomain().getPredicate().toString()) && p.getDomain().get(0).getValue(mind).getType() == DataType.NUMERIC) {
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

//                System.err.println(p.getId() + " --- " + q.getId());
//                if(q.getId() == p.getId()) {
//                    trigger = true;
//                }

                    if (p.getDomain().equalsBase(((Rule) q).getDomain())
                            && p.getDomain().isAntc() != ((Rule) q).getDomain().isAntc()) {
                        //&& p.getDomain().getArguments().getCVariables(mind).size() != p.getDomain().getRange()) {

                        //TODO: Костыль
                        if (((Rule) q).getMind() == null) {
                            ((Rule) q).setMind(mind);
                        }
                        if (p.getDomain().isQuery(mind) && p.getDomain().getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(q);
                            mind.getValues().add(p.getSolves());
                        } else if (((Rule) q).getDomain().isQuery(mind) && ((Rule) q).getDomain().getArguments().getCVariables(mind).isEmpty()) {
                            mind.getSolutions().add(p);
                            mind.getValues().add(((Rule) q).getSolves());
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

//    private boolean equalsCVars(Domain p, Domain q) throws Exception {
//        for (int i = 0; i < p.getArguments().size(); ++i) {
//            if(p.get(i).isCVar(mind) && q.get(i).isCVar(mind)) {
//                if(p.get(i).getValue(mind).getRightId() == q.get(i).getValue(mind).getRightId()) {
//
//                }
//            } else {
//                return true;
//            }
//        }
//    }

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

        // Контроль закрытия всех веток запроса
        if (!orfans.isEmpty() && !calculated) {
            result = false;
//            for(Right r : orfans) {
//                if(r.isStored()) {
//                    Hypothesis tmp = new Hypothesis(r.getDomain(), mind);
//                    tmp.setAntc(!tmp.isAntc());
//                    if (mind.getHypothesisStore().find(tmp) == null /*&& mind.getRights().find(tmp) == null*/) {
////                            && mind.getHypothesisStore().find(/*null,*/ !d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
////                            Hypothesis h = mind.getHypothesisStore().add(/*true,*/ !d.isAntc(), d.isQuery(mind), d.getPredicate(), d.getArguments());
////                            tmp.setAntc(true);
//                        mind.getHypothesisStore().add(tmp);
//                        if (logging) {
//                            log.add(LogMode.ANALIZER, "Hypothesis assumed: " + tmp.toString());
//                        }
//                    }
//                }
//            }
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
