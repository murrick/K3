/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.exception.RuntimeErrorException;
import kanger.exception.SubstitutionException;
import kanger.primitives.*;

import java.util.*;

/**
 * @author murray
 */
public class Linker {

    private final User user;

    public Linker(User user) {
        this.user = user;
    }

    private boolean linkFunctions(Domain master, Domain slave, int level, boolean logging, Set<Function> selected) {

        if (level >= master.getPredicate().getRange()) {

            boolean occurrs = false;
            for (Function f : selected) {
                if (!f.isCalculated() && f.isCalculable()) {
                    if (new Calculator(user).calculate(f) > 0) {
                        occurrs = true;
//                            mind.getFValues().add(f);
//                            if (logging) {
//                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
//                            }
                    }
                }
            }

            if (occurrs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
//            if (logging && occurrs) {
//                logComparsion(master);
//                logComparsion(slave);
//                mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
//            }
            return occurrs;

        } else {

//            boolean isQuery = mind.getQuery() != null;
            //ПОДСТАНОВКИ Результатов функций
//            for (int i = 0; i <= level; ++i) {

//            if (master.get(level).isFunction() && !master.get(level).getFunction().getName().toString().equals("_add")) {
//                System.out.println(master);
//            }

            if (master.get(level).isFSet() && !master.get(level).getF().isCalculated()) {
                if (!slave.get(level).isEmpty()
                    && master.get(level).getF().isCalculable()
                    && !master.get(level).getF().isComplete()) {
                    master.get(level).getF().setValue(slave.get(level).getValue());
                    selected.add(master.get(level).getF());
                } else if (master.get(level).getF().isCalculable()
                           && master.get(level).getF().isComplete()) {
                    selected.add(master.get(level).getF());
                } else if (!master.get(level).getF().isCalculable()) {
                    selected.add(master.get(level).getF());
                }
            }

//            if (slave.get(level).isFunction() && !slave.get(level).getFunction().getName().toString().equals("_add")) {
//                System.out.println(slave);
//            }

            if (slave.get(level).isFSet() && !slave.get(level).getF().isCalculated()) {
                if (!master.get(level).isEmpty()
                    && slave.get(level).getF().isCalculable()
                    && !slave.get(level).getF().isComplete()) {
                    slave.get(level).getF().setValue(slave.get(level).getValue());
                    selected.add(slave.get(level).getF());
                } else if (slave.get(level).getF().isCalculable()
                           && slave.get(level).getF().isComplete()) {
                    selected.add(slave.get(level).getF());
                } else if (!slave.get(level).getF().isCalculable()) {
                    selected.add(slave.get(level).getF());
                }
            }

//            }
            return linkFunctions(master, slave, level + 1, logging, selected);
        }

    }


    private boolean linkDomains(Domain master, Domain slave, int level, Term[] solves, boolean logging, boolean occurrsSubst, boolean occurrsMaster, boolean occurrsSlave) throws SubstitutionException {

        if (level >= master.getPredicate().getRange()) {

            if (occurrsMaster || occurrsSlave) {
                List<Argument> args = new ArrayList<>();
                for (Term t : solves) {
                    args.add(new Argument(t));
                }

                boolean result = false;
                if (occurrsMaster) {
                    master.setExcluded(args);
                    result = logComparsion(logging, master) || result;
                }
                if (occurrsSlave) {
                    slave.setExcluded(args);
                    result = logComparsion(logging, slave) || result;
                }
                if (result) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }
            return occurrsSubst;

        } else {

//            boolean isQuery = mind.getQuery() != null;


            //ПОДСТАНОВКИ T-переменных
            //TODO: Не уверен что не надо проходить каждый раз заново. Надо проверить на длинных запросах
//            for (int i = 0; i <= level; ++i) {
            int i = level;

//                if("xx".equals(master.get(i).getValue() + "")) {
//            System.out.println("m: " + master + " s:" + slave);
//                }
//                if("xx".equals(slave.get(i).getValue() + "")) {
//                    System.out.println("s: " + slave);
//                }

            if (master.get(i).isTSet()
                && !slave.get(i).isEmpty()
                && !slave.isDestFor(i, master)
                && !slave.isExcluded()
//                        && !master.isDestFor(i, slave)

//                        && master.get(i).getTVariable().isEmpty()

                && master.getVarOrder(i) >= slave.getVarOrder(i) //|| slave.getTVarCount() != master.getTVarCount() || slave.getCVarCount() != master.getCVarCount())
//                        && (!slave.get(level).getValue().isCVariable() || !master.isAntc() || slave.get(level).getValue().getIndex() < master.get(level).getTVariable().getIndex())
//                        && (!master.isDestFor() || (!master.get(level).isEmpty() && master.get(level).getValue().getRight().isQuery()))
                ) {
                TValue s;
                if (!master.get(i).getT().contains(slave.get(i).getValue())) {
                    s = master.get(i).getT().addValue(slave.get(i).getValue());
                    s.setClosed();

//                    System.out.println("Closed: " + master.get(i).getTVariable());
                    occurrsSubst = true;
                    //TODO: Перенес
                    s.addSolve(i, slave, master);
//                } else {
//                    s = master.get(i).getTVariable().find(slave.get(i).getValue());
//                }
                    if (slave.isQuery() /*|| master.isQuery()*/) {
                        s.setQuery();
                    }
                } else {
                    s = master.get(i).getT().find(slave.get(i).getValue());
                    s.setClosed();
                    s.addSolve(i, slave, master);
                    if (slave.isQuery() /*|| master.isQuery()*/) {
                        s.setQuery();
                    }
                }
                solves[i] = s.getValue();
                occurrsMaster = true;

            }

            if (slave.get(i).isTSet()
                && !master.get(i).isEmpty()
                && !master.isDestFor(i, slave)
                && !master.isExcluded()
//                        && !slave.isDestFor(i, master)

//                        && slave.get(i).getTVariable().isEmpty()

                && slave.getVarOrder(i) >= master.getVarOrder(i) //|| slave.getTVarCount() != master.getTVarCount() || slave.getCVarCount() != master.getCVarCount())
//                        && (!master.get(level).getValue().isCVariable() || !slave.isAntc() || master.get(level).getValue().getIndex() < slave.get(level).getTVariable().getIndex())
//                        && (!slave.isDestFor() || (!slave.get(level).isEmpty() && slave.get(level).getValue().getRight().isQuery()))
                ) {
                TValue s;
                if (!slave.get(i).getT().contains(master.get(i).getValue())) {
                    s = slave.get(i).getT().addValue(master.get(i).getValue());
                    s.setClosed();
//                    System.out.println("Closed: " + slave.get(i).getTVariable());
                    occurrsSubst = true;
                    //TODO: Перенес
                    s.addSolve(i, master, slave);
//                } else {
//                    s = slave.get(i).getTVariable().find(master.get(i).getValue());
//                }
                    if (master.isQuery() /*|| slave.isQuery()*/) {
                        s.setQuery();
                    }
                } else {
                    s = slave.get(i).getT().find(master.get(i).getValue());
                    s.setClosed();
                    s.addSolve(i, master, slave);
                    if (master.isQuery() /*|| slave.isQuery()*/) {
                        s.setQuery();
                    }
                }
                solves[i] = s.getValue();
                occurrsSlave = true;

            }

            if ((!occurrsSlave && !occurrsSlave && (slave.get(i).isEmpty() || master.get(i).isEmpty() || master.get(i).getValue().getId() != slave.get(i).getValue().getId()))) {
                throw new SubstitutionException(master + " > " + slave);
            }
//            }

//            if(occurrsMaster) {
//                for (Function f : master.getFunctions()) {
//                    if(!f.isCalculated()) {
//                        mind.getCalculator().calculate(f);
//                    }
//                }
//            }
//            if(occurrsSlave) {
//                for (Function f : slave.getFunctions()) {
//                    if(!f.isCalculated()) {
//                        mind.getCalculator().calculate(f);
//                    }
//                }
//            }

            return linkDomains(master, slave, level + 1, solves, logging, occurrsSubst, occurrsMaster, occurrsSlave);
        }
    }


    public boolean checkSystem(boolean logging, Tree tree) {
        boolean block = false;
        for (Domain d : tree.getSequence()) {
            if (d.isSystem() && !d.isUsed()) {
                int res = d.execSystem();

                // Проверка полноты предиката
                for (Argument a : d.getArguments()) {
                    if (!a.isCalculated()) {
                        res = -2;
                        break;
                    }
//                    if (
//                            (a.isTVariable() && a.getTVariable().isEmpty())
//                                    || (a.isFunction() && !a.getFunction().isCalculated())
//                    ) {
//                        res = -2;
//                        break;
//                    }
                }

//                for (TVariable t : d.getTVariables(true)) {
//                    if (t.isEmpty()) {
//                        res = -2;
//                        break;
//                    }
//                }
//
//                //
//                for (Function f : d.getFunctions()) {
//                    if (!f.isCalculated()) {
//                        res = -2;
//                        break;
//                    }
//                }

                if (res == 0) {
                    if (d.isAntc()) {
                        d.setUsed();
                    } else if (!d.isQuery()) {
                        block = true;
                        logBlocked(logging, d);
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setUsed();
                    } else if (!d.isQuery()) {
                        block = true;
                        logBlocked(logging, d);
                    }
                }

                if (d.isUsed() && d.isComplete()) {
                    for (TVariable t : d.getTVariables(true)) {
                        for (Domain x : t.getUsage()) {
                            if (d.getId() != x.getId() && !x.isProduced()) {
                                x.setCalculated();
                                x.setProduced();
                            }
                        }
                    }
                }

            }
        }
        return !block;
    }

    public boolean updateDomains(SortedSet<TVariable> tvars, Queue<Tree> masterSet, Queue<Tree> slaveSet, int level, boolean logging) {
        boolean result = false;

        if (tvars.isEmpty()) {
            for (Tree master : masterSet) { //query == null ? set : query.getTree()) {
                for (Tree slave : slaveSet) {

                    user.getMind().getClosedValues().clear();
                    user.getMind().getBlockedValues().clear();

                    if (checkSystem(logging, master) && checkSystem(logging, slave)) {
                        user.getMind().getTValues().mark();
                        user.getMind().getFValues().mark();
//                        mind.getClosedValues().clear();
//                        mind.getBlockedValues().clear();


                        for (Domain d1 : master.getSequence()) {
//                            if (d1.isExcluded()) continue;
                            for (Domain d2 : slave.getSequence()) {
//                                if (d2.isExcluded()) continue;


                                if (d1.getId() != d2.getId()
                                    && d1.isAntc() != d2.isAntc()
                                    && d1.getPredicate().getId() == d2.getPredicate().getId()
//                                        && !d1.isExcluded()
//                                        && !d2.isExcluded()
                                    ) {

//                                    if(d1.isQuery() || d2.isQuery()) {
//                                        System.out.println("d1: " + d1);
//                                        System.out.println("d2: " + d2);
//                                    }

//                                    linkFunctions(d1, d2, 0, logging, new HashSet<Function>());

                                    try {
                                        user.getMind().getTValues().mark();
                                        if (linkDomains(d1, d2, 0, new Term[d1.getPredicate().getRange()], logging, false, false, false)) {

//                                        for(Argument ma : d1.getArguments()) {
//                                            if ("xx".equals(ma.getValue() + "")) {
//                                                System.out.println("m: " + d1);
//                                            }
//                                        }
//                                        for(Argument sl : d2.getArguments()) {
//                                            if ("xx".equals(sl.getValue() + "")) {
//                                                System.out.println("s: " + d2);
//                                            }
//                                        }

//                                        if(d1.isQuery() || d2.isQuery()) {
//                                            System.out.println("!");
//                                        }

                                            result = true;
                                            linkFunctions(d1, d2, 0, logging, new HashSet<Function>());
                                        } else {
                                            linkFunctions(d1, d2, 0, logging, new HashSet<Function>());
                                        }
                                        user.getMind().getTValues().commit();
                                    } catch (SubstitutionException e) {
                                        user.getMind().getTValues().release();
                                    }


//                                } else if (d1.getId() == d2.getId() && d1.isSystem()) {
//                                    linkFunctions(d1, d2, 0, logging, false, false);
                                }
                            }
                        }

//                        if (checkSystem(logging, master) && checkSystem(logging, slave)) {
                        logCommit(logging);
                        user.getMind().getTValues().commit();
                        user.getMind().getFValues().commit();
//                        } else {
//                            mind.getFValues().release();
//                            mind.getFValues().release();
//                            result = false;
//                        }

                    } else {
//                        System.out.println("!!");
//                        mind.getTValues().release();
//                        mind.getFValues().release();
//                        result = false;
                    }
                    updateDatabase(master, logging);
                    updateDatabase(slave, logging);
                }
            }

        } else {
            TVariable t = tvars.last(); //.get(tIndex);
            TValue v = t.rewind();
            if (v != null) {
                do {

                    user.getMind().getTValues().set(t, v);
                    if (updateDomains(tvars.headSet(t), masterSet, slaveSet, ++level, logging)) {
                        result = true;
                    }
                    user.getMind().getTValues().set(t, v);
                } while ((v = t.next(v)) != null);

//                mind.getTValues().set(t, null);
//                if (!updateDomains(tvars.headSet(t), set, logging)) {
//                    result = false;
//                }

            } else {
                if (updateDomains(tvars.headSet(t), masterSet, slaveSet, ++level, logging)) {
                    result = true;
                }
            }
        }
        return result;
    }

    public boolean calcFunctions(SortedSet<TVariable> tvars, Queue<Tree> set, boolean logging) throws RuntimeErrorException {
        boolean result = true;
        if (tvars.isEmpty()) {

//            FValue saveF = mind.getFValues().getRoot();

            for (Tree master : set) { //query == null ? set : query.getTree()) {

                user.getMind().getTValues().mark();
                user.getMind().getFValues().mark();
                user.getMind().getClosedValues().clear();
                user.getMind().getBlockedValues().clear();

                for (Domain d : master.getSequence()) {
//                    if (saveF == mind.getFValues().getRoot()) {
                    for (Function f : d.getFunctions()) {
                        new Calculator(user).calculate(f);
//                        if ((f.isCalculable() || !f.isCalculated()) && f.isComplete()) {
//                            f.clearResult();
//                            if (mind.getCalculator().calculate(f) > 0) {
//                                mind.getFValues().add(f);
//                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
//                            }
//                        }
                    }
//                    }
                }

//                for (Domain d : master.getSequence()) {
//                    if (d.isSystem()) {
//                        int res = d.execSystem();
//                        for (TVariable t : d.getTVariables(true)) {
//                            if (!t.isEmpty()) {
//                                if (res == 0) {
//                                    mind.getTValues().get(t).setBlocked();
//                                } else if (res == 1) {
//                                    mind.getTValues().get(t).setClosed();
//                                }
//                            }
//                        }
//                    }
//                }

                logCommit(logging);
                user.getMind().getTValues().commit();
                user.getMind().getFValues().commit();

            }

//            if (!testDomains(tvars, 0, set, logging)) {
//                result = false;
//            }
//
////            if (result) {
//            logCommit(logging);
//            mind.getTValues().rollback();
//            mind.getFValues().rollback();
////            } else {
////                logCommit("RELEASE (F):", logging);
////                mind.getTValues().release();
////                mind.getFValues().release();
////            }

        } else {
            TVariable t = tvars.last();
            TValue v = t.rewind();
            if (v != null) {

                do {
                    user.getMind().getTValues().set(t, v);
                    if (!calcFunctions(tvars.headSet(t), set, logging)) {
                        result = false;
                    }
                    user.getMind().getTValues().set(t, v);
                } while ((v = t.next(v)) != null);

//                mind.getTValues().set(t, null);
//                if (!calcFunctions(tvars.headSet(t), set, logging)) {
//                    result = false;
//                }

            } else {
                if (!calcFunctions(tvars.headSet(t), set, logging)) {
                    result = false;
                }
            }
        }
        return result;
    }

//    public boolean testDomains(List<TVariable> tvars, int tIndex, Tree master, boolean logging) throws RuntimeErrorException {
//        boolean result = true;
//        if (tIndex >= tvars.size()) {
//
//            Set<Domain> sp = new HashSet<>();
//
//                for (Domain d : master.getSequence()) {
//                    if (d.isSystem()) {
//                        int res = d.execSystem();
//                        if (res == 0) { //(res == 0 && !d.isAntc()) || (res == 1 && d.isAntc())) {
//                            for (TVariable t : d.getTVariables(true)) {
//                                mind.getTValues().createCVar(t).setBlocked();
//                            }
//                            result = false;
//                        } else if (res == 1) {
//                            for (TVariable t : d.getTVariables(true)) {
//                                mind.getTValues().createCVar(t).setClosed();
//                            }
//                            d.setClosed();
//                        }
//                    }
//                }
//
//
////            for (Domain d : sp) {
////                if (d.isSystem()) {
////                    int res = d.execSystem();
////                    if (res == 0) { //(res == 0 && !d.isAntc()) || (res == 1 && d.isAntc())) {
////                        for(TVariable t : d.getTVariables(true)) {
////                            mind.getTValues().createCVar(t).setBlocked();
////                        }
////                        result = false;
////                    } else if(res == 1){
////                        d.setClosed();
////                    }
////                }
////            }
//
//        } else {
//            TVariable t = tvars.createCVar(tIndex);
//            TValue v = t.rewind();
//            if (v != null) {
//                mind.getSubstituted().createTVar(t);
//                do {
//                    mind.getTValues().set(t, v);
//                    if (!testDomains(tvars, tIndex + 1, master, logging)) {
//                        result = false;
//                    }
//                } while ((v = t.next(v)) != null);
//                mind.getSubstituted().remove(t);
//            } else {
//                if (!testDomains(tvars, tIndex + 1, master, logging)) {
//                    result = false;
//                }
//            }
//        }
//        return result;
//    }


    public Queue<Tree> getActualTrees(Right r) {
        boolean added = false;
        Queue<Tree> set = new LinkedList<>();

        if (r != null) {
            set.addAll(r.getTree());
            do {
                added = false;
                Set<Tree> tmp = new HashSet<>();
                for (Tree t : set) {
                    for (Domain d : t.getSequence()) {
                        for (Right rx = user.getMind().getRights().getRoot(); rx != null; rx = rx.getNext()) {
                            for (Tree tx : rx.getTree()) {
                                if (!set.contains(tx) && tx.contains(d)) {
                                    tmp.add(tx);
                                    added = true;
                                }
                            }
                        }
                    }
                }
                set.addAll(tmp);
            } while (added);
        } else {
            for (Right rx = user.getMind().getRights().getRoot(); rx != null; rx = rx.getNext()) {
                set.addAll(rx.getTree());
            }
        }
        return set;
    }

    public void link(boolean logging) throws RuntimeErrorException {
        user.getMind().getExcludedTrees().clear();

        user.getMind().getUsedDomains().clear();
        user.getMind().getCalculatedDomains().clear();

        user.getMind().getExcludedDomains().clear();
        user.getMind().getProducedDomains().clear();

        link(null, logging);
    }

    public void link(Right r, boolean logging) throws RuntimeErrorException {

        long start = System.currentTimeMillis();

        int pass = 0;
//        if (r == null) {
//            mind.clearQueryStatus();
//            mind.getTValues().clear();
//        }

//        mind.getExcludedTrees().clear();
        //TODO: Нужно сделать динамический сет
        Queue<Tree> slave = getActualTrees(r);
        Queue<Tree> master;
//        if (r != null) {
//            master = new LinkedList<>();
//            master.addAll(r.getTree());
//        } else {
        master = slave;
//        }
//todo добвалять produced

        TValue saveT = null;
        FValue saveF = null;
        Domain saveB = null;


//        mind.getQueryValues().clear();

        do {
            slave = getActualTrees(r);
            master = slave;

            saveT = user.getMind().getTValues().getRoot();
            saveF = user.getMind().getFValues().getRoot();
            saveB = user.getMind().getDatabase().getRoot();

            if (logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, String.format("============= LINKER PASS %03d =============", ++pass));
            }

            SortedSet<TVariable> tset = new TreeSet<>();
            for (Tree t : slave) {
                tset.addAll(t.getTVariables(true));

//                for(Function f: t.getFunctions()) {~
//                    f.clearResult();
//                }
            }

//            if (r != null) {
//                for (Tree t : r.getTree()) {
//                    tset.addAll(t.getTVariables(true));
//                }
//            }

//            for (int i = 0; i < slave.size(); ++i) {

//            mind.getUsedDomains().clear();
//            mind.getExcludedDomains().clear();
//            mind.getProducedDomains().clear();
//            mind.getStoredDomains().clear();
//            user.getMind().getUsedTrees().clear();

//                calcFunctions(tset, slave, logging);
            while (updateDomains(tset, master, slave, 0, logging)) ;
            calcFunctions(tset, slave, logging);


//                if (!res) {
//                    break;
//                }

//            Tree top = slave.poll();
//            slave.add(top);
//            }


//            for (Tree t: set) {
//                for (Domain d : t.getSequence()) {
//                    d.execSystem();
//                }
//            }
//            for (Tree t : set) {
//                for (Function f : t.getFunctions()) {
//                    if (f.isComplete()) {
//                        f.clearResult();
//                        mind.getCalculator().calculate(f);
//                    }
//                }
//            }


//            for (Tree t : set) {
//                for (Function f : t.getFunctions()) {
//                    if (f.isCalculated()) {
//                        f.clearResult();
//                        mind.getCalculator().calculate(f);
//                    }
//                }
//            }

//            set = mind.getActualTrees();

        } while (saveT != user.getMind().getTValues().getRoot() || saveF != user.getMind().getFValues().getRoot() || saveB != user.getMind().getDatabase().getRoot());

        user.getMind().getClosedValues().clear();
        user.getMind().getBlockedValues().clear();

//        } while (mind.getSubstituted().size() > 0 || mind.getCalculated().size() > 0);

//        if (r == null) {
//            mind.getStoredDomains().clear();
//            mind.getExcludedDomains().clear();
//            mind.getProducedDomains().clear();
//            mind.getQueryValues().clear();
//        }

        if (logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "* Linking time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
        }

    }

    private Domain updateDatabase(Tree tree, boolean logging) {
        Domain produced = null;
        if (tree.getSequence().size() == 1) {
            if (tree.getSequence().get(0).getTVariables(true).size() == 0) {
                produced = tree.getSequence().get(0);
            } else if (tree.getSequence().get(0).isComplete()) {
                produced = tree.getSequence().get(0);
            }
        } else {
            for (Domain d : tree.getSequence()) {
                if (d.isComplete() && d.isProduced() && !(d.isCalculated() && !d.isExcluded()) /*&& !d.isStored()*/ /*&& !d.isExcluded()*/ && !d.isUsed()) {
                    if (produced == null) {
                        produced = d;
                    } else {
                        produced = null;
                        break;
                    }
                }
            }
        }

        if (produced != null) {
            if (!produced.isStored()) {
//                Domain solve = produced.getPredicate().containsSolve(produced);
//            if (produced.isQuery()) {
//                System.out.println("produced: " + produced);
//            }

                int save = user.getMind().getDebugLevel();
                user.getMind().setDebugLevel(0);
                Domain d = produced.setStored();
                String origin = d.toString(); 
                user.getMind().setDebugLevel(save);

                boolean found = false;
                for (Right r = user.getMind().getRights().getRoot(); r != null; r = r.getNext()) {
                    if (origin.equals(r.getOrig())) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Right r = user.getMind().getRights().add();
                    Tree t = user.getMind().getTrees().add();
                    t.setRight(r);
                    t.setGenerated(true);
                    d.setRight(r);
                    t.getSequence().add(d);
                    r.getTree().add(t);
                    r.setGenerated(true);
                    r.setOrig(origin); 
                }


//                if(produced.isExcluded()) {
//                    produced.setUsed();
//                }
                if (logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "DB record: " + produced.toString());
                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }
        } else {

            boolean excluded = true;
            for (Domain d : tree.getSequence()) {
                if (!d.isExcluded() || d.isStored()) {
                    excluded = false;
                    break;
                }
            }
            if (excluded) {
                boolean occurs = false;
                for (Domain d : tree.getSequence()) {
                    if (!d.isStored()) {
                        d.setStored();
//                        if(d.isExcluded()) {
//                            d.setUsed();
//                        }
                        occurs = true;
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB record: " + d.toString());
                        }
                    }
                }
                if (logging && occurs) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }

        }

        return produced;
    }


    private void logBlocked(boolean logging, Domain d) {
        if (logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
        }
    }

    private boolean logComparsion(boolean logging, Domain d) {
        boolean result = false;
        //TODO: Отвязать от лога функционал (setProduced)
        if (logging) {
            for (TVariable t : d.getTVariables(true)) {
                if (!t.isEmpty()) {
                    for (int i = 0; i < t.getDstSolves().size(); ++i) {
                        Domain dst = t.getDstSolves().get(i);
                        Domain src = t.getSrcSolves().get(i);

                        //TODO: Не уверен, но нужно контролировать только целевые предикаты. НО! А если подстановка в обе стороны??

//                        if (!src.getRight().isQuery()) src.setExcluded();
//                        if (!dst.getRight().isQuery())
//                        dst.setExcluded();
//                        dst.setExcluded();

                        if (dst.getPredicate().getId() == d.getPredicate().getId()) {
                            boolean found = false;
                            for (Domain r : t.getUsage()) {
                                //TODO: usDest сомнитеьно. Аесли двусторонняя подстановка?
                                if (dst.getId() != r.getId() && !r.isExcluded() && !r.isProduced()) { //&& mind.getLog().find(LogMode.ANALIZER, "Result: " + r.toString()) == null) {
                                    //TODO: ! Помечать как produced. В дальнейшем использовать для подстановок. Не выводить в ллог уже помеченные
                                    r.setProduced();
                                    user.getMind().getLog().add(LogMode.ANALIZER, "Result: " + r.toString());
                                    found = true;
                                    result = true;
                                }
                            }
//                            if (!found) {
//                                mind.getLog().add(LogMode.ANALIZER, "Confirmed: " + src);

//                        if (d.getRight().isQuery()) {
//                            a.getTVariable().getDstSolve().setAcceptor(false);
//                        }


//                            }
                            if (found) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "From right  : " + t.getRight().toString());
                                user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + dst.toString());
                                user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + src.toString());
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private void logCommit(boolean logging) {
        if (logging) {
            if (user.getMind().getTValues().getRoot() != user.getMind().getTValues().getMark()) {
                for (TValue t = user.getMind().getTValues().getRoot(); t != user.getMind().getTValues().getMark(); t = t.getNext()) {
                    if (t.isClosed()) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "CLOSED:\t" + t.toString());
                        user.getMind().getLog().add(LogMode.ANALIZER, "From right  : " + t.getTVar().getRight().toString());
                        for (int i = 0; i < t.getPosSolves().size(); ++i) {
                            int saveDebugLevel = user.getMind().getDebugLevel();
                            user.getMind().setDebugLevel(saveDebugLevel & ~(Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS));
                            user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + t.getDstSolves().get(i).toString());
                            user.getMind().setDebugLevel(saveDebugLevel);
                            user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + t.getSrcSolves().get(i).toString());
                        }
                    }
                }
//                for (FValue t = mind.getFValues().getRoot(); t != mind.getFValues().getMark(); t = t.getNext()) {
//                    mind.getLog().add(LogMode.ANALIZER, (t.isClosed() ? "COMMIT:\t" : "RELEASE:\t") + t.toString());
//                }
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
    }
}


//TODO: ?$x a(x,G); - НЕ СХОДИТСЯ! Из за ранга
