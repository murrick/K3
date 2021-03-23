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

    //TODO: ПРИБИТЬ
//    int cccc = 0;
    //TODO: ?$x mother(John,x); - неопределен !!!!!!!!!!!!!!


    public Linker(Mind mind) {
        this.mind = mind;
        this.log = mind.getLog();
    }

    public void link(Rule rule, boolean logging) throws Exception {

        //TODO: XPR-менты
//        rule = null;

        mind.getExcludedDomains().clear();
        mind.getUsedDomains().clear();
        mind.getCalculatedDomains().clear();
        mind.getUsedRules().clear();
        mind.getFloodControl().clear();

        mind.getRuleSolves().clear();

        int passCounter = 0;

        solvedPasses = 0;
        dumpedPasses = 0;
        skippedPasses = 0;

        final Map<IRule, Set<Cause>> causes = new HashMap<>();

        Rule top = mind.getRules().getTop();
        long topId = top == null ? -1 : top.getId();

//        int sz = 0;
        do {

            if (logging) {
                log.add(LogMode.ANALYZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }

//            sz = mind.getRightSolves().size();

            mind.getRules().dropAction();
            mind.getTValues().dropAction();
            mind.getFValues().dropAction();

            Set<IRule> ruleSet = new HashSet<>();
            if (rule != null) {

                for (List<Domain> list : rule.getTree()) {
                    for (Domain d : list) {
                        if ("rule(1)".equals(d.getPredicate().toString()) && d.get(0).getType() == ArgumentType.TVARIABLE) {
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
//
            if (rule != null) {
                ruleSet.add(rule);
                ruleSet.addAll(rule.getNatives());
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind)) {
                        if (((Rule) r).isUsed(mind)) {
                            ruleSet.add(r);
                            ruleSet.addAll(((Rule) r).getNatives());
                        } else if (r.isGenerated() && r.getId() > topId) {
                            ruleSet.add(r);
                            ruleSet.addAll(((Rule) r).getNatives());
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

//            System.err.println("--------------");
//            for(Right r : rightSet) {
//                System.err.println(r);
//            }
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

//            break;

        } while (mind.getRules().isAction()
                || mind.getTValues().isAction()
                || mind.getFValues().isAction()
//                || mind.getRightSolves().size() > sz
        );

        if (logging) {
            log.add(LogMode.TIMING, String.format("* LINKER Solved passes: %03d", solvedPasses));
            log.add(LogMode.TIMING, String.format("* LINKER Dumped passes: %03d", dumpedPasses));
            log.add(LogMode.TIMING, String.format("* LINKER Skipped passes: %03d", skippedPasses));
        }

//        if (logging) {
//            if (mind.getUsedRights().containsKey(0L) && !mind.getUsedRights().get(0L).isEmpty()) {
//                log.add(LogMode.ANALIZER, String.format("---------- LINKER USED RIGHTS -------------"));
//                for (Right r : mind.getUsedRights().get(0L)) {
//                    log.add(LogMode.ANALIZER, r.toString());
//                }
//            }
//        }
    }

    private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging) throws Exception {

        boolean used = false;

//        mind.getRightSolves().clear();

//        final SortedSet<TVariable> tvars = new TreeSet<>();
//        for (Right r : rightList) {
//            for (List<Domain> tree : r.getTree()) {
//                for (Domain d : tree) {
//                    tvars.addAll(d.getArguments().getTVariables(mind, true));
//                }
//            }
//        }

        for (IRule r : ruleList) {

            //TODO: Костыль
//            r.setMind(mind);

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

//            if (!mind.getRightSolves().isEmpty()) {
//                System.err.println("========================================");
//                for (Map.Entry<TVariableSet, List<TSolve>> e : mind.getRightSolves().entrySet()) {
//                    for (TSolve s : e.getValue()) {
//                        if (!s.getSolve().isEmpty()) {
//                            System.err.println("---- " + " : " + s);
//                        } else {
//                            //TODO: Убрать
//                            System.err.println("?");
//                        }
//                    }
//                }
//            }


            for (List<Domain> tree : ((Rule) r).getTree()) {

                final List<Domain> t = tree;

//                mind.getTSolves().forEach(new IReactor() {
//                    @Override
//                    public Object run(Object o) throws Exception {
//                        ((TSolve) o).getSolve().get(0).getTVar().getRight();
//                        System.err.println("---- " + ((TSolve) o).getSolve().get(0).getTVar().getRight() + " : " + o);
//                        return null;
//                    }
//                });

                rotateVariables(tvars, tvars, new IReactor() {

//                for(List<TSolve> list : mind.getRightSolves().values()) {
//                    for(TSolve s : list) {
//                        for(TValue v : s.getSolve()) {
//                            v.getTVar().setCurrent(null);
//                        }
//                    }
//                }
//
//                for (TVariable v : tvars) {
//                    v.setCurrent(null);
//                }

                    //                rotateRights(0, mind.getRightSolves(), new IReactor() {
                    @Override
                    public Object run(Object o) {
                        boolean result = false;
//                            boolean logging = (boolean) o;

                        try {

//                            for (List<Domain> tree : r.getTree()) {
//
//                                final List<Domain> t = tree;

//                            Map<Right, List<Object[]>> variants = new HashMap<>();


                            if (linkDomains(t, ruleList, causes, logging)) {
                                result = true;
                            }
                            if (calcFunctions(t, causes, logging)) {
                                result = true;
                            }

                            if (linkDatabase(t, causes, /*(SortedSet<TVariable>) o*/ tvars, logging)) {
                                result = true;
                            }

//                            }

//                            if(variants.containsKey(r)) {


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

//            if (!mind.getRightSolves().isEmpty()) {
//                System.err.println("========================================");
//                for (Map.Entry<TVariableSet, List<TSolve>> e : mind.getRightSolves().entrySet()) {
//                    for (TSolve s : e.getValue()) {
//                        if (!s.getSolve().isEmpty()) {
//                            System.err.println("---- " + " : " + s);
//                        } else {
//                            //TODO: Убрать
//                            System.err.println("?");F
//                        }
//                    }
//                }
//            }

            if (!wasUsed && ((Rule) r).isUsed(mind)) {
                used = true;
            }
        }

        return used;
    }

//    private boolean rotateRights(int pos, Map<TVariableSet, List<TSolve>> solves, IReactor runnable) throws Exception {
//        boolean result = false;
//        if (pos >= solves.size()) {
//            result = (boolean) runnable.run(null);
//        } else {
//            TVariableSet key = new ArrayList<>(solves.keySet()).get(pos);
//            List<TSolve> tv = solves.get(key);
//            int i = -1;
//            while (++i < tv.size()) {
//                TSolve s = tv.get(i);
//                Set<TVariable> temp = s.activate();
//                if (rotateRights(++pos, solves, runnable)) {
//                    result = true;
//                }
//                if (temp != null) {
//                    if (temp != null) {
//                        for (TVariable t : temp) {
//                            t.setCurrent(null);
//                        }
//                    }
//                }
//            }
////            if (!result[1]) {
////                if (rotateRights(++pos, solves, runnable)) {
////                    result[0] = true;
////                }
////            }
//
//        }
//
//        return result;
//    }

//    private List<TValue> getTSolves(TValue v) throws Exception {
//        List<TValue> list = new ArrayList<>();
//        Right r = v.getTVar().getRight();
//        for(TSolve s : r.getTSolves()) {
//            if(s.getValue(v.getTVar()).getValue().getId() == v.getId()) {
//                list.addAll(s.getSolve());
//                break;
//            }
//        }
//
//    }

    private boolean rotateVariables(final SortedSet<TVariable> tvars, final SortedSet<TVariable> base, final IReactor runnable) throws Exception {
        final boolean[] result = new boolean[]{false, false};
        if (tvars.isEmpty()) {
            result[0] = (boolean) runnable.run(tvars);
        } else {
            final TVariable t = tvars.last();

            if (t.getFloodCounter() > mind.getFloodControlLimit()) {
                throw new RuntimeErrorException("Flood limit exceeded (" + mind.getFloodControlLimit() + ")");
            }
//            result[1] = true;

//            t.setCurrent(null);
//            if (rotateVariables(tvars.headSet(t), base, runnable)) {
//                result[0] = true;
//            }

//            final Object[] top = new Object[2];
//            top[0] = mind.getTValues().getRoot(t);
//            top[1] = 0;
            mind.getTValues().forEach(t, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
//                    if(((TValue)top[0]).getId() < mind.getTValues().getRoot(t).getId()) {
//                        top[0] = mind.getTValues().getRoot(t);
//                        top[1] = ((int) top[1]) + 1;
//
//                        if((int) top[1] > 100) {
//                            throw new RuntimeErrorException("Looks like never-ending recursion");
//                        }
//                    }
//                    if (!((TValue) o).getValue().isCVariable() || !((TValue) o).getValue().getSlaves().isEmpty()) {
                    result[1] = true;
                    t.setCurrent((TValue) o);
                    if (isValidFor(base.tailSet(t))) {
//                    mind.getTValues().set(t, (TValue) o);
                        if (rotateVariables(tvars.headSet(t), base, runnable)) {
                            result[0] = true;
                        }
                    }
//                    }
                    return true;
                }
            });


            if (!result[1]) {
//                if(isValidFor(base.tailSet(t))) {
                if (rotateVariables(tvars.headSet(t), base, runnable)) {
                    result[0] = true;
                }
//                }
            }
        }
        return result[0];
    }

//    private boolean isValidFor(TValue[] subst) throws Exception {
//        boolean nulls = true;
//        for (TValue v : subst) {
//            if (v != null) {
//                nulls = false;
//                break;
//            }
//        }
//        if (subst.length > 1 && !nulls) {
//            for (TVariableSet key : mind.getRightSolves().keySet()) {
//                for (TSolve s : mind.getRightSolves().get(key)) {
//                    if (s.isValid(subst)) {
//                        return true;
//                    }
//                }
//            }
//            return false;
//        }
//        return true;
//    }

    private boolean isValidFor(SortedSet<TVariable> tail) {
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
//        if(!(!found || result)) {
//            System.err.println(t);
//        }
        return !found || result;
    }

    private boolean linkDomains(List<Domain> treeSlave, Collection<IRule> ruleList, Map<IRule, Set<Cause>> causes, boolean logging) throws Exception {

        Map<Solve, List<Object[]>> variants = new HashMap<>();
        boolean result = false;

        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (IRule rule : ruleList /*mind.getRights()*/) {
//                    if (right.isDeleted() || !right.getPredicates().contains(slave.getPredicateId())) {
//                        continue;
//                    }

//                    System.err.println("\t" + right.getId() + ": " + right);


//                                                        TValue s = mind.getTValues().find(slave.get(i).getT(mind), tm);


                    for (List<Domain> treeMaster : ((Rule) rule).getTree()) {
                        for (Domain master : treeMaster) {

                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {

                                //TODO: Костыль
//                                master.setMind(mind);
//                                slave.setMind(mind);

                                TValue[] substMaster = new TValue[master.getRange()];
                                TValue[] substSlave = new TValue[slave.getRange()];

//                                master.recalculate(true);
//                                slave.recalculate(true);

                                mind.getTValues().mark();
                                mind.getFValues().mark();

                                boolean success = true;
                                boolean applied = false;

                                // Отсечение несовпадений по константам

                                boolean blockRight = false;
                                boolean blockLeft = false;

//                                boolean constFound = false;
//                                boolean cvarFound = false;
//                                boolean clear = false;

                                boolean acceptable = false;
                                for (int i = 0; i < master.getRange(); ++i) {
                                    if (master.get(i).getType() == ArgumentType.TVARIABLE /*&& !blockByOrder(i, master, slave)*/) {
//                                        acceptable = true;
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()
//                                            && slave.get(i).getValue(mind).getId() == ((Term) master.get(i).getValue(mind)).getParentId()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()
//                                            && ((Term) slave.get(i).getValue(mind)).getParentId() == master.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isXVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).isCVariable()) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId()) {
                                    } else {
//                                        if(slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                            cvarFound = true;
//                                        } else {
//                                            clear = true;
//                                        }
                                        blockRight = true;
//                                        break;
                                    }
//                                            || slave.get(i).isEmpty(mind)
////                                            || slave.get(i).getValue(mind).isCVariable()
//                                            || (!slave.get(i).isEmpty(mind)
//                                            && !master.get(i).isEmpty(mind)
//                                            && (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()
////                                            || (master.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId())
////                                            || (slave.get(i).getValue(mind).isXVariable() && slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId())
////                                            || master.get(i).getValue(mind).getChilds().contains(slave.get(i).getValue(mind).getId())
////                                            || slave.get(i).getValue(mind).getChilds().contains(master.get(i).getValue(mind).getId())
//                                    ))) {
////                                        if(slave.get(i).getType() == ArgumentType.TERM && !slave.get(i).getValue(mind).isCVariable()) {
////                                            constFound = true;
////                                        }
////                                        if(!slave.get(i).isEmpty(mind) && slave.get(i).getValue(mind).isCVariable()) {
////                                            cvarFound = true;
////                                        }
//                                    } else {
//                                        blockRight = true;
//                                        break;
//                                    }
                                }

                                if (acceptable) {
                                    blockRight = false;
                                }

//                                if(!blockRight && cvarFound && !constFound) {
//                                    blockRight = true;
//                                }

//                                constFound = false;
//                                cvarFound = false;
                                acceptable = false;
                                for (int i = 0; i < slave.getRange(); ++i) {
                                    if (slave.get(i).getType() == ArgumentType.TVARIABLE /*&& !blockByOrder(i, slave, master)*/) {
//                                        acceptable = true;
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()
//                                            && slave.get(i).getValue(mind).getId() == ((Term) master.get(i).getValue(mind)).getParentId()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()
//                                            && ((Term) slave.get(i).getValue(mind)).getParentId() == master.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isXVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).isCVariable()) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId()) {
                                    } else {
//                                        if(slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                            cvarFound = true;
//                                        } else {
//                                            clear = true;
//                                        }
                                        blockLeft = true;
//                                        break;
                                    }

//                                    if (slave.get(i).getType() == ArgumentType.TVARIABLE //&& !blockByOrder(i, slave, master))
//                                            || master.get(i).isEmpty(mind)
////                                            || master.get(i).getValue(mind).isCVariable()
//                                            || (!slave.get(i).isEmpty(mind)
//                                            && !master.get(i).isEmpty(mind)
//                                            && (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()
////                                            || (slave.get(i).getValue(mind).isXVariable() && slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId())
////                                            || (master.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId())
////                                            || master.get(i).getValue(mind).getChilds().contains(slave.get(i).getValue(mind).getId())
////                                            || slave.get(i).getValue(mind).getChilds().contains(master.get(i).getValue(mind).getId())
//                                    ))) {
////                                        if(master.get(i).getType() == ArgumentType.TERM && !master.get(i).getValue(mind).isCVariable()) {
////                                            constFound = true;
////                                        }
////                                        if(!master.get(i).isEmpty(mind) && master.get(i).getValue(mind).isCVariable()) {
////                                            cvarFound = true;
////                                        }
//                                    } else {
//                                        blockLeft = true;
//                                        break;
//                                    }
                                }

                                if (acceptable) {
                                    blockLeft = false;
                                }

//                                if(!blockLeft && cvarFound && !constFound) {
//                                    blockLeft = true;
//                                }

//                                    } !slave.get(i).getType() == ArgumentType.TVARIABLE
//                                            && (!master.get(i).isFSet() || !master.get(i).getF(mind).isEmpty())
//                                            && (!slave.get(i).isFSet() || !slave.get(i).getF(mind).isEmpty())
//                                            && (!slave.get(i).isCVar(mind) || !slave.get(i).getValue(mind).getSlaves().isEmpty())
//                                            && (!master.get(i).isCVar(mind) || !master.get(i).getValue(mind).getSlaves().isEmpty())
//                                            && (master.get(i).isEmpty(mind)
//                                            || slave.get(i).isEmpty(mind)
//                                            || master.get(i).getValue(mind).getId() != slave.get(i).getValue(mind).getId())) {
//                                        success = false;
//                                        break;
//                                    }


//                                if(blockRight && blockLeft && cvarFound && !clear) {
//                                    System.err.println(master + " <- " + slave);
////                                    for (int i = 0; i < slave.getRange(); ++i) {
////                                        if (master.get(i).getType() == ArgumentType.TVARIABLE && !slave.get(i).isEmpty(mind) && !slave.get(i).getValue(mind).isCVariable()) {
////                                            blockRight = false;
////                                        }
////                                        if (slave.get(i).getType() == ArgumentType.TVARIABLE && !master.get(i).isEmpty(mind) && !master.get(i).getValue(mind).isCVariable()) {
////                                            blockLeft = false;
////                                        }
////                                    }
//
//                                }

//                                Set<IRule> usedRules = new HashSet<>();

                                if (success) {

//                                    boolean masterComplete = master.isComplete(); // && master.isQuery();
//                                    boolean slaveComplete = slave.isComplete(); // && slave.isQuery();

//                                    System.err.println(blockLeft + " " +master + " <- " + blockLeft + " " + slave);

                                    for (int i = 0; i < slave.getRange(); ++i) {

                                        // Подстановка снизу вверх
//                                        if(slaveComplete) {
                                        if (!blockRight) {
                                            if (master.get(i).getType() == ArgumentType.TVARIABLE /*&& master.get(i).isEmpty(mind)*/) {
                                                if (!slave.get(i).isEmpty(mind)) {

//                                            if (master.getVarOrder(mind, i) < slave.getVarOrder(mind, i)) {
//                                                    if (blockByOrder(i, master, slave)) {
//                                                System.err.println(slave + " -> " + master);
//                                                    } else {
//                                            if(master.getRightId() == slave.get(i).getValue(mind).getRightId()) {
//                                                System.err.println("=== " + master.getVarOrder(i) + ":" + master + " <-- " + slave.getVarOrder(i) + ":" + slave);
//                                            }

//                                            long parentId = -1;

                                                    Term tm = (Term) slave.get(i).getValue(mind);
                                                    TVariable t = (TVariable) master.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && slave.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {

//                                                        s = mind.getTValues().findCVariable(t, tm);
//                                                        if (s == null) {
//                                                            Term tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind));
//                                                            tn.setParent(tm);
//                                                            tm = tn;
//                                                        }
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(master.getRule(), tm.getName(mind));
                                                            tn.setParent(tm);
                                                            tm.setChild(tn);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }
                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
//                                                        usedRules.add(master.getRule());
//                                                s.setParentId(parentId);
                                                        result = true;
                                                    }

//                                                    if (master.get(i).isEmpty(mind)) {
//                                                        master.get(i).getT(mind).setCurrent(s);
//                                                    }

                                                    substMaster[i] = s;
                                                    slave.setUsed(mind);
                                                    master.setUsed(mind);
                                                    applied = true;

                                                }
                                            }
//                                            } else if(slave.isQuery()){
//                                                success = false;
//                                                break;

                                        }
//                                        }
//                                        }


                                        // Подстановка сверху вниз
//                                        if(masterComplete) {
                                        if (!blockLeft) {
                                            if (slave.get(i).getType() == ArgumentType.TVARIABLE && slave.get(i).isEmpty(mind)) {
                                                if (!master.get(i).isEmpty(mind)) {

//                                            if (slave.getVarOrder(mind, i) < master.getVarOrder(mind, i)) {
//                                                    if (blockByOrder(i, slave, master)) {
//                                                System.err.println(master + " -> " + slave);
//                                                    } else {

//                                            if(slave.getRightId() == master.get(i).getValue(mind).getRightId()) {
//                                                System.err.println("=== " + slave.getVarOrder(i) + ":" + slave + " <-- " + master.getVarOrder(i) + ":" + master);
//                                            }

//                                            long parentId = -1;
                                                    Term tm = (Term) master.get(i).getValue(mind);
                                                    TVariable t = (TVariable) slave.get(i).getObject(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && tm.getParentId(mind) == -1 && master.getRuleId() == tm.getRuleId() && !tm.isDomini() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
//                                                        s = mind.getTValues().findCVariable(t, tm);
//                                                        if (s == null) {
//                                                            Term tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind));
//                                                            tn.setParent(tm);
//                                                            tm = tn;
//                                                        }
                                                        Term tn = (Term) tm.getChild(mind);
                                                        if (tn == null) {
                                                            tn = (Term) mind.getTerms().createCVar(slave.getRule(), tm.getName(mind));
                                                            tn.setParent(tm);
                                                            tm.setChild(tn);
                                                        }
                                                        tm = tn;
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }

//                                                        TValue s = mind.getTValues().find(slave.get(i).getT(mind), tm);
                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
//                                                        usedRules.add(slave.getRule());
//                                                s.setParentId(parentId);
                                                        result = true;
                                                    }

//                                                    if (slave.get(i).isEmpty(mind)) {
//                                                        slave.get(i).getT(mind).setCurrent(s);
//                                                    }

                                                    substSlave[i] = s;
                                                    master.setUsed(mind);
                                                    slave.setUsed(mind);
                                                    applied = true;

                                                }
//                                            } else if(master.isQuery()){
//                                                success = false;
//                                                break;
                                            }
                                        }
//                                        }
//                                        }

//                                        if (master.get(i).isFSet()
//                                                && master.get(i).getF().isCalculable()
//                                                && master.get(i).getF().isEmpty()
//                                                && !slave.get(i).isEmpty()) {
//
//                                            long id = mind.getFValues().getLastId();
//                                            master.get(i).getF().setResult(slave.get(i).getValue());
//                                            if (mind.getCalculator().calculate(master.get(i).getF(), logging)
//                                                    && id < mind.getFValues().getLastId()) {
//                                                result = true;
//                                                List<TValue> list = new ArrayList<>();
//                                                for (TValue v : mind.getTValues()) {
//                                                    if (v.getId() <= id) {
//                                                        break;
//                                                    } else {
//                                                        list.add(v);
//                                                    }
//                                                }
//                                                substMaster[i] = list;
//                                                slave.setUsed();
//                                                master.setUsed();
//                                                applied = true;
//                                            }
//                                        }
//
//                                        if (slave.get(i).isFSet()
//                                                && slave.get(i).getF().isCalculable()
//                                                && slave.get(i).getF().isEmpty()
//                                                && !master.get(i).isEmpty()) {
//
//                                            long id = mind.getFValues().getLastId();
//                                            slave.get(i).getF().setResult(master.get(i).getValue());
//                                            if (mind.getCalculator().calculate(slave.get(i).getF(), logging)
//                                                    && id < mind.getFValues().getLastId()) {
//                                                result = true;
//                                                List<TValue> list = new ArrayList<>();
//                                                for (TValue v : mind.getTValues()) {
//                                                    if (v.getId() <= id) {
//                                                        break;
//                                                    } else {
//                                                        list.add(v);
//                                                    }
//                                                }
//                                                substSlave[i] = list;
//                                                slave.setUsed();
//                                                master.setUsed();
//                                                applied = true;
//                                            }
//                                        }

                                        if (!applied) {
//                                            if (master.get(i).isEmpty(mind) || slave.get(i).isEmpty(mind)
//                                                    || master.get(i).getValue(mind).getId() != slave.get(i).getValue(mind).getId()) {
//                                                success = false;
//                                                break;
//                                            } else {
                                            substMaster[i] = null;
                                            substSlave[i] = null;
//                                            }
//                                        } else {
//                                            master.getRight().setUsed();
                                        }

                                    }
                                }

                                //TODO: Нужно исключить подстановки в занятые места
//                                if(success && (!isValidFor(substMaster) || !isValidFor(substSlave))) {
//                                    success = false;
//                                }

                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();
                                    } else if (!master.isSubstitutable() && !slave.isSubstitutable()) {
                                        ++solvedPasses;
                                        master.setUsed(mind);
                                        slave.setUsed(mind);
                                    } else {
                                        ++dumpedPasses;
                                    }
                                    markExcluded(result, substMaster, master, slave, causes, variants, logging);
                                    markExcluded(result, substSlave, slave, master, causes, variants, logging);

                                    ((Rule) master.getRule()).setUsed(mind);
                                    ((Rule) slave.getRule()).setUsed(mind);
//                                    for(Right r : usedRights) {
//                                        r.setUsed();
//                                    }
                                } else {
                                    ++skippedPasses;

//                                    System.err.println("--- " + master);
//                                    System.err.println("--- " + slave);
//                                    System.err.println("--- ");

                                    mind.getTValues().release();
                                    mind.getFValues().release();
                                }
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Solve, List<Object[]>> variantsList : variants.entrySet()) {
                for (Object[] subst : variantsList.getValue()) {
                    List<TValue> list = new ArrayList<>();
                    for (Object x : subst) {
                        if (x == null) {
                        } else if (x instanceof TValue) {
                            list.add((TValue) x);
                        }
                    }
                    TSolve s = mind.addTSolve(list);
//                    s.setVariant(variantsList.getKey());
                }
            }

        }
        return result;
    }

//    private boolean blockByOrder(int pos, Domain master, Domain slave) throws Exception {
////        if (slave.get(pos).getValue(mind).isCVariable()) {
////            if (master.getRightId() == slave.get(pos).getValue(mind).getRightId()) {
////                return master.get(pos).getT(mind).getIndex() < slave.get(pos).getValue(mind).getIndex();
//////            } else if (master.isQuery()){
////            } else if (!slave.get(pos).getType() == ArgumentType.TVARIABLE) {
//        master.calcVarOrders(mind);
//        return master.getVarOrder(mind, pos) < slave.getVarOrder(mind, pos);
////            }
////        }
////        return false;
//    }

//    private TSolve findTSolve(List<TValue> list) {
//        TSolve tmp = new TSolve(list, mind);
//            for (TSolve t : mind.getRightSolves()) {
//                if (tmp.equalsTo(t)) {
//                    return t;
//                }
//            }
//        return null;
//    }
//
//    public TSolve addTSolve(List<TValue> list) {
//        TSolve tmp = findTSolve(list);
//        if (tmp != null) {
//            return tmp;
//        } else {
//            tmp = new TSolve(list, mind);
//            mind.getRightSolves().add(tmp);
//            return tmp;
//        }
//    }

    private boolean markExcluded(boolean result, TValue[] subst, Domain master, Domain slave, Map<IRule, Set<Cause>> causes, Map<Solve, List<Object[]>> variants, boolean logging) throws Exception {
        IRule r = null;
        boolean occurrs = false;


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
//            if (!master.isExcluded(slave.getArguments())) {
            r = v.getTVar(mind).getRule(mind);
            if (logging && result) {
                log.add(LogMode.ANALYZER, "Closed: " + v.toString(mind));
            }
            occurrs = true;
//            }
        }


//        boolean complete = true;
//        List<TValue> list = new ArrayList<>();
//        for (int i = 0; i < slave.getRange(); ++i) {
//            if (subst[i] != null) {
//                if (subst[i] instanceof Collection) {
//                    list.addAll((Collection<TValue>) subst[i]);
//                } else {
//                    list.add((TValue) subst[i]);
//                }
//            } else {
//                complete = false;
//            }
//        }


//        if (!list.isEmpty()) {
//
//            // Отсечение конфликтных подстановок
//            for (int i = 0; i < list.size(); ++i) {
//                for (int j = 0; j < list.size(); ++j) {
//                    if (list.get(i).getTVar().getId() == list.get(j).getTVar().getId() && list.get(i).getValue().getId() != list.get(j).getValue().getId()) {
//                        return false;
//                    }
//                }
//            }
//
////            boolean caused = false;
//            TSolve t = mind.findTSolve(list);
//            Cause s = new Cause(mind, master, slave);
////            if (!t.getCauses().contains(s)) {
////                t.getCauses().add(s);
////                caused = true;
////            }
//
//            if (t == null || !t.getCauses().contains(s) || !master.isExcluded(slave.getArguments())) {
//                r = master.getRight();
//                if (t == null) {
//                    t = mind.addTSolve(list);
//                }
//                if (!t.getCauses().contains(s)) {
//                    t.getCauses().add(s);
//                    if (!causes.containsKey(r)) {
//                        causes.put(r, new HashSet<>());
//                    }
//                    causes.get(r).add(s);
//                    if (logging) {
//                        log.add(LogMode.ANALIZER, "Closed: " + t);
//                    }
//                    occurrs = true;
//                }
//            }
//        }
//            }
//        }
        if (r != null) {

            if (occurrs /*&& !result*/) {
                Cause s = new Cause(master, slave, mind);
                if (!causes.containsKey(r)) {
                    causes.put(r, new HashSet<>());
                }
                causes.get(r).add(s);
            }

//            mind.addTSolve(list);

            master.setExcluded(slave.getArguments(), mind);
            if (!variants.containsKey(master)) {
                variants.put(master, new ArrayList<>());
            }
            variants.get(master).add(subst);

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
        return r != null;
    }


//    private void logBranch(List<Domain> tree, boolean logging) {
//        if (!tree.isEmpty() && logging) {
//            log.add(LogMode.ANALIZER, "With branch:");
//
//            for (Domain d : tree) {
//                log.add(LogMode.ANALIZER, "\t" + d.toString());
//            }
//        }
//    }

    private boolean linkDatabase(List<Domain> tree, Map<IRule, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {

        boolean result = false;
        boolean occurs = false;


//        long tag = mind.getTValues().incTag();

//        for (Domain d : tree) {
//            d.recalculate(true);
//        }

        if (checkSystem(tree, logging)) {

//            for (Domain d : tree) {
//                d.recalculate(true);
//            }


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
//                if(!d.isComplete()) {
//                    continue;
//                }

//                for(Function f : d.getArguments().getFunctions()) {
//                    f.clear();
//                    mind.getCalculator().calculate(f, logging);
//                }


                for (Domain master : mind.getDomains().getWaiters()) {
                    if (master.getPredicateId() == d.getPredicateId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                        boolean success = true;
                        for (int i = 0; i < d.getRange(); ++i) {
                            if (master.get(i).getType() == ArgumentType.TVARIABLE /*&& master.getVarOrder(mind, i) >= d.getVarOrder(mind, i)*/) {
//                            } else if(master.get(i).isEmpty(mind) || d.get(i).isEmpty(mind)) {
                            } else if (master.get(i).getValue(mind).getId() == d.get(i).getValue(mind).getId()) {
                            } else {
//                                    || master.get(i).getValue(mind).isCVariable()) {
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
//                if(!d.isComplete()) {
//                    continue;
//                }

                if ("rule(1)".equals(d.getPredicate().toString()) && !d.get(0).isEmpty(mind) && d.get(0).getValue(mind).getType() == DataType.NUMERIC) {
                    d.setProduced(mind);
                    log.add(LogMode.STORAGE, "DB assumed record (r): " + d);
                    occurs = true;
//                    calculated.add(d);
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


            //TODO: Интересно. Сделать репликацию c-переменных
//            if (excluded.size() > 1) {
//                boolean cFound = false;
//                boolean xFound = false;
//                for (Domain d : excluded) {
//                    if (!d.getArguments().getCVariables(mind).isEmpty()) {
//                        cFound = true;
//                    } else {
//                        xFound = true;
//                    }
//                }
//                if (cFound && xFound) {
////                    excluded.clear();
//                }
//            }

            if (candidates.size() == 1) {
                for (Domain d : candidates) {
//                    Domain d = candidates.toArray(new Domain[]{})[0];
                    occurs = true;
                    if (!d.isStored(mind) && (d.setCauses(causes.get(d.getRule()), mind) || !calculated.isEmpty() || !excluded.isEmpty())) {

//                    System.err.println();
//                    System.err.println(d);
//                    System.err.println(d.getArguments().getTValues(mind, true));
//                    TVariableSet ts = new TVariableSet(d.getArguments().getTValues(mind, true));
////                    for(List<TSolve> lst : mind.getRightSolves().values()) {
//                        if(mind.getRightSolves().containsKey(ts)) {
//                            for (TSolve s : mind.getRightSolves().get(ts)) {
//                                System.err.println("---  " + s);
//                            }
//                        }
////                    }

//                    boolean block = false;
//                    for(IArgument a : d.getArguments()) {
//                        if((a.getType() == ArgumentType.TERM || a.getType() == ArgumentType.TVARIABLE) && a.getValue(mind).isCVariable()) {
//                            block = true;
//                            break;
//                        }
//                    }

////                    for(ITerm t : d.getArguments().getCVariables(mind)) {
////                        if(((Term) t).isXVariable()) {
////                            ++x;
////                        } else {
////                            ++c;
////                        }
////                    }
//
//                    block = z > 0 || (c == 0 && x > 0); //(c == 0 && x > 0);
                        boolean skip = false; //d.getRange() == 1 && d.getArguments().get(0).getValue(mind).isCVariable();
                        boolean term = false;
                        boolean abst = false;
                        Set<ITerm> cs = new HashSet<>();
                        for (IArgument a : d.getArguments()) {
                            if (a.getValue(mind).isCVariable() && !((Term) a.getValue(mind)).isDomini()) {
                                abst = true;
                            } else {
                                term = true;
                            }
                        }
                        skip = !term && abst;
//                                break;
//                            } else {
//                                if(((Term) a.getValue(mind)).getParent(mind) != null) {
//                                    cs.add(((Term) a.getValue(mind)).getParent(mind));
//                                } else {
//                                    cs.add(a.getValue(mind));
//                                }
//                            }
//                        }
//                        if(skip && cs.size() > 1) {
//                            skip = false;
//                        }

//
                        // Нет смысла записывать в базу утверждение единственный параметр в котором - c-переменная
                        if (!skip) {
//                    if (!block) {

//                        if (d.getArguments().getCVariables(mind).size() != d.getArguments().size()) {
                            result = true;
                            d.setProduced(mind);
//                        d.setTag(tag);
//                    d.setCauses(causes.get(d.getRight()));
                            d.setSolves(solve, mind);
                            if (logging) {
//                            logBranch(tree, logging);
                                log.add(LogMode.STORAGE, "DB assumed record: " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
//                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRule()), mind)) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag = mind.getTValues().incTag());
//                        d.setCauses(causes.get(d.getRight()));
                            d.setSolves(solve, mind);
                            if (logging) {
//                                logBranch(tree, logging);
                                log.add(LogMode.STORAGE, "DB assumed record (x): " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
                }
                //TODO: Сомнительно, но вроде работает с ?$x $y index(qwerty) -> index(x), y : x;
            } //else
            if (!calculated.isEmpty() && candidates.isEmpty() /*&& tree.size() - excluded.size() == calculated.size()*/) {
                occurs = true;
                for (Domain d : calculated) {
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored(mind) /*|| d.isQuery()*/) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag = mind.getTValues().incTag());
                            d.setCauses(causes.get(d.getRule()), mind);
                            d.setSolves(solve, mind);
                            if (logging) {
//                                logBranch(tree, logging);
                                log.add(LogMode.STORAGE, "DB assumed record (c): " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
                }
            }

            if (!occurs && !assumed.isEmpty() && tree.size() > 1) {
                candidates.clear();
                excluded.clear();
                for (Domain d : tree) {
                    if (d.isComplete() && !d.isCalculated(mind) && !d.isSystem(mind) && !assumed.contains(d)) {
                        if (!d.isExcluded(mind)) {
                            candidates.add(d);
                        } else {
                            excluded.add(d);
                        }
                    }
                }
                if (candidates.size() == 1 && !excluded.isEmpty()) {
                    Domain d = candidates.toArray(new Domain[]{})[0];
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRule()), mind)) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag);
//                        d.setCauses(causes.get(d.getRight()));
                            d.setSolves(solve, mind);
                            if (logging) {
//                                logBranch(tree, logging);
                                log.add(LogMode.STORAGE, "DB assumed record (a): " + d);
                                logCauses(LogMode.STORAGE, d);
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
//                log.add(mode, "\t\tUsing: " + mind.getRights().find(c.getDonor()).getDomain().toString());
            }
        }
    }

    //    private boolean isRecurse(Cause top, Cause c) {
//        if(c == null) {
//            return isRecurse(top, top);
//        } else {
//            for(TValue v : c.getSrc().getTValues(true)) {
//                if(v != null && v.getCauses() != null) {
//                    for (Cause x : v.getCauses()) {
//                        if (x.getSrc().equalsBase(top.getSrc())) {
//                            return true;
//                        }
//                        if (isRecurse(top, x)) {
//                            return true;
//                        }
//                    }
//                }
//            }
//            return false;
//        }
//    }
//

//    private boolean analizeProduces(List<ArgList> main, List<ArgList> calculated, List<Right> solves) {
//        boolean result = false;
//
//        if(!mind.getProducedDomains().isEmpty()) {
//            for (Map.Entry<Domain, List<ArgList>> master : mind.getProducedDomains().entrySet()) {
//                Domain dMaster = master.getKey();
//                for (ArgList aMaster : master.getValue()) {
//                    if (dMaster.isCalculated(aMaster)) {
//                        ArgList row = new ArgList();
//                        for (TValue v : dMaster.getSolves(aMaster)) {
//                            row.add(new Argument(v));
//                        }
//                        if (!calculated.contains(row)) {
//                            calculated.add(row);
//                        }
//                        result = true;
//                    } else {
//                        for (Right right : mind.getRights().getDatabase()) {
//                            if (!right.getDomain().isCalculated()
//                                    && dMaster.getPredicate().getId() == right.getDomain().getPredicate().getId()
//                                    && dMaster.isAntc() != right.getDomain().isAntc()
//                                    && aMaster.equalsBase(right.getDomain().getArguments())) {
//                                if (dMaster.isQuery()) {
//                                    ArgList row = new ArgList();
//                                    for (TValue v : dMaster.getSolves(aMaster)) {
//                                        row.add(new Argument(v));
//                                    }
//                                    if (!main.contains(row)) {
//                                        main.add(row);
//                                    }
//                                    if(!solves.contains(right)) {
//                                        solves.add(right);
//                                    }
//                                    result = true;
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            for(Right master : mind.getRights().getDatabase()) {
//                for (Right right : mind.getRights().getDatabase(master.getId())) {
//                    if (!right.getDomain().isCalculated()
//                            && master.getDomain().getPredicate().getId() == right.getDomain().getPredicate().getId()
//                            && master.getDomain().isAntc() != right.getDomain().isAntc()
//                            && master.getDomain().getArguments().equalsBase(right.getDomain().getArguments())) {
//                        if (master.getDomain().isQuery()) {
//                            if(!solves.contains(right)) {
//                                solves.add(right);
//                            }
//                        } else {
//                            if(solves.contains(master)) {
//                                solves.add(master);
//                            }
//                        }
//                        result = true;
//                    }
//                }
//            }
//        }
//
//
//        return result;
//    }

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
                if (d.isComplete() /*&& !d.isCalculated(mind)*/) {

//                for(Function f : d.getArguments().getFunctions()) {
//                    f.clear();
//                    mind.getCalculator().calculate(f, logging);
//                }


//                d.recalculate(true);

//                for (int i = 0; i < args.size(); /*d.getPredicate().getRange();*/ ++i) {
//                    if (d.getArguments().get(i).getType() == ArgumentType.TVARIABLE) {
//                        if (d.getArguments().get(i).getT().find(args.get(i).getValue()) != null) {
//                            d.getArguments().get(i).getT().setValue(args.get(i).getValue());
//                        }
//                    } else if (d.getArguments().get(i).isFSet()) {
//                        //TODO: Добавить обработку функций
//                    }
//                }

                    IRule x;
//                if (d.getArguments().getTVariables(true).isEmpty()) {
//                    x = d.setStored();
//                    if (logging) {
//                        log.add(LogMode.ANALIZER, "DB set record: " + d);
//                    }
//                } else {
//                    for(Term t : d.getArguments().getCVariables(mind)) {
//                        t.toCVariable();
//                    }

                    x = d.createStored(mind);
                    if (d.isUsed(mind)) {
                        ((Rule) x).getDomain().setUsed(mind);
                    }
                    if (logging) {
                        log.add(LogMode.STORAGE, "DB add record: " + d + " -> " + x);
                    }
//                }

                    if (d.isCalculated(mind)) {
                        ((Rule) x).getDomain().setCalculated(mind);
                    }
                    if (d.getCauses(mind) != null) {
                        x.getCauses().clear();
                        x.getCauses().addAll(d.getCauses(mind));
//                        x.washCauses();

                        //TODO: XPRMNT
//                        x.getDomain().setCauses(d.getCauses());

//                    for(Cause c : x.getCauses()) {
//                        if(!c.getDst().isStored()) {
//                            c.getDst().createStored();
//                        }
//                    }
                    }
                    if (d.getSolves(mind) != null) {
                        ((Rule) x).getSolves().clear();
                        ((Rule) x).getSolves().addAll(d.getSolves(mind));
                    }

//                    if(!rightList.contains(x)) {
//                        rightList.add(x);
//                    }
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

//        if (checkSystem(master, logging)) {
//            for (Domain d : master) {
//                for (Function f : d.getArguments().getFunctions()) {
//                    if (f.isCalculable() && f.isEmpty()) {
//                        if (mind.getCalculator().calculate(f, logging)) {
//                            result = true;
//                        }
//                    }
//                }
//            }
//        }

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

//                d.pushValues();

//                List<TValue> list = new ArrayList<>();
//                for (TVariable t : d.getArguments().getTVariables(true)) {
//                    list.add(t.getCurrent());
//                }

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

//                if (block && logging) {
//                    log.add(LogMode.ANALIZER, "Blocker: " + d.toString());
//                }

                if (!block & d.isComplete()) {
                    List<TValue> list = new ArrayList<>();
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        list.add(t.getCurrent());
                    }
//                    Right r = d.getRight();
                    if (!list.isEmpty()) {
                        solves.add(list);
                    }
                }
//                d.popValues();

//                List<TVariable> ts = d.getArguments().getTVariables(true);
//                for (int i = 0; i < ts.size(); ++i) {
//                    if (list.get(i) != null) {
//                        ts.get(i).setCurrent(list.get(i));
//                    }
//                }
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

    private boolean isValid(Domain d) throws Exception {
        return true;

//        List<TValue> list = d.getArguments().getTValues(mind, true);
//        TVariableSet ts = new TVariableSet(list);
////        System.err.println(d);
//        if(mind.getRightSolves().containsKey(ts)) {
//            for (TSolve s : mind.getRightSolves().get(ts)) {
////                System.err.println("== " + d);
//                if(s.contains(list)) {
//                    return true;
//                }
//            }
//        }
//
////        System.err.println("-- " + d);
//        return false;
    }
}

//TODO Запрсы +(insert), -(delete), =(update)
//TODO счетчик ссылок для сборки мусор
//TODO time для каждой записи в базе
//TODO id для каждой зависи в базе??
