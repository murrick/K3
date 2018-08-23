/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kanger;

import kanger.enums.LogMode;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.*;

import java.util.*;

/**
 * @author murray
 */
public class Linker {

    private final Mind mind;

    public Linker(Mind mind) {
        this.mind = mind;
    }

    private boolean linkFunctions(Domain master, Domain slave, int level, boolean logging, Set<Function> selected) throws RuntimeErrorException {

        if (level >= master.getPredicate().getRange()) {

            boolean occurrs = false;
            for (Function f : selected) {
                if (!f.isCalculated() && f.isCalculable() /*&& !f.isComplete()*/) {
                    if (mind.getCalculator().calculate(f) > 0) {
                        occurrs = true;
//                            mind.getFValues().add(f);
//                            if (logging) {
//                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
//                            }
                    }
                }
            }

            if (occurrs && logging) {
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
//            if (logging && occurrs) {
//                logComparsion(master);
//                logComparsion(slave);
//                mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
//            }
            return occurrs;

        } else {

            boolean isQuery = mind.getQuery() != null;
            //ПОДСТАНОВКИ Результатов функций
//            for (int i = 0; i <= level; ++i) {

//            if (master.get(level).isFSet() && !master.get(level).getF().getName().toString().equals("_add")) {
//                System.out.println(master);
//            }

            if (master.get(level).isFSet() && !master.get(level).getF().isCalculated()) {
                if (!slave.get(level).isEmpty()
                        && master.get(level).getF().isCalculable()
                        && !master.get(level).getF().isComplete()) {
                    master.get(level).getF().setResult(slave.get(level).getValue());
                    selected.add(master.get(level).getF());
                } else if (master.get(level).getF().isCalculable()
                        && master.get(level).getF().isComplete()) {
                    selected.add(master.get(level).getF());
                } else if (!master.get(level).getF().isCalculable()) {
                    selected.add(master.get(level).getF());
                }
            }

//            if (slave.get(level).isFSet() && !slave.get(level).getF().getName().toString().equals("_add")) {
//                System.out.println(slave);
//            }

            if (slave.get(level).isFSet() && !slave.get(level).getF().isCalculated()) {
                if (!master.get(level).isEmpty()
                        && slave.get(level).getF().isCalculable()
                        && !slave.get(level).getF().isComplete()) {
                    slave.get(level).getF().setResult(slave.get(level).getValue());
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


    private boolean linkDomains(Domain master, Domain slave, int level, boolean logging, boolean occurrsSubst, boolean occurrsMaster, boolean occurrsSlave) {

        if (level >= master.getPredicate().getRange()) {

            if (occurrsMaster || occurrsSlave) {
                if (occurrsMaster) {
                    logComparsion(logging, master);
                }
                if (occurrsSlave) {
                    logComparsion(logging, slave);
                }
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
            return occurrsSubst;

        } else {

            boolean isQuery = mind.getQuery() != null;


            //ПОДСТАНОВКИ T-переменных
            for (int i = 0; i <= level; ++i) {

//                if("xx".equals(master.get(i).getValue() + "")) {
//                    System.out.println("m: " + master);
//                }
//                if("xx".equals(slave.get(i).getValue() + "")) {
//                    System.out.println("s: " + slave);
//                }

                if (master.get(i).isTSet()
                        && !slave.get(i).isEmpty()
                        && !slave.isDestFor(i, master)
//                        && !master.isDestFor(i, slave)
//                        && master.get(i).getT().isEmpty()
                        && master.getVarOrder(i) >= slave.getVarOrder(i)
//                        && (!slave.get(level).getValue().isCVar() || !master.isAntc() || slave.get(level).getValue().getIndex() < master.get(level).getT().getIndex())
//                        && (!master.isDestFor() || (!master.get(level).isEmpty() && master.get(level).getValue().getRight().isQuery()))
                ) {
                    TValue s;
                    if (!master.get(i).getT().contains(slave.get(i).getValue())) {
                        s = master.get(i).getT().setValue(slave.get(i).getValue());
                        s.setClosed();
                        occurrsSubst = true;
                    } else {
                        s = master.get(i).getT().find(slave.get(i).getValue());
                    }
                    s.addSolve(i, slave, master);
                    if (slave.isQuery() || master.isQuery()) {
                        s.setQuery();
                    }
                    occurrsMaster = true;
                }

                if (slave.get(i).isTSet()
                        && !master.get(i).isEmpty()
                        && !master.isDestFor(i, slave)
//                        && !slave.isDestFor(i, master)
//                        && slave.get(i).getT().isEmpty()
                        && slave.getVarOrder(i) >= master.getVarOrder(i)
//                        && (!master.get(level).getValue().isCVar() || !slave.isAntc() || master.get(level).getValue().getIndex() < slave.get(level).getT().getIndex())
//                        && (!slave.isDestFor() || (!slave.get(level).isEmpty() && slave.get(level).getValue().getRight().isQuery()))
                ) {
                    TValue s;
                    if (!slave.get(i).getT().contains(master.get(i).getValue())) {
                        s = slave.get(i).getT().setValue(master.get(i).getValue());
                        s.setClosed();
                        occurrsSubst = true;
                    } else {
                        s = slave.get(i).getT().find(master.get(i).getValue());
                    }
                    s.addSolve(i, master, slave);
                    if (master.isQuery() || slave.isQuery()) {
                        s.setQuery();
                    }
                    occurrsSlave = true;
                }
            }

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

            return linkDomains(master, slave, level + 1, logging, occurrsSubst, occurrsMaster, occurrsSlave);
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
//                            (a.isTSet() && a.getT().isEmpty())
//                                    || (a.isFSet() && !a.getF().isCalculated())
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
            }
        }
        return !block;
    }

    public boolean updateDomains(SortedSet<TVariable> tvars, Queue<Tree> masterSet, Queue<Tree> slaveSet, boolean logging) throws RuntimeErrorException {
        boolean result = false;

        if (tvars.isEmpty()) {
            for (Tree master : masterSet) { //query == null ? set : query.getTree()) {
                for (Tree slave : slaveSet) {

                    if (checkSystem(logging, master) && checkSystem(logging, slave)) {
                        mind.getTValues().mark();
                        mind.getFValues().mark();
                        mind.getClosedValues().clear();
                        mind.getBlockedValues().clear();

                        for (Domain d1 : master.getSequence()) {
                            for (Domain d2 : slave.getSequence()) {


                                if (d1.getId() != d2.getId()
                                        && d1.isAntc() != d2.isAntc()
                                        && d1.getPredicate().getId() == d2.getPredicate().getId()
                                ) {
//                                    linkFunctions(d1, d2, 0, logging, new HashSet<Function>());

                                    if (linkDomains(d1, d2, 0, logging, false, false, false)) {

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

                                        result = true;
                                    }
                                    linkFunctions(d1, d2, 0, logging, new HashSet<Function>());

//                                } else if (d1.getId() == d2.getId() && d1.isSystem()) {
//                                    linkFunctions(d1, d2, 0, logging, false, false);
                                }
                            }
                        }

//                        if (checkSystem(logging, master) && checkSystem(logging, slave)) {
                        logCommit(logging);
                        mind.getTValues().commit();
                        mind.getFValues().commit();
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
                }
            }
        } else {
            TVariable t = tvars.last(); //.get(tIndex);
            TValue v = t.rewind();
            if (v != null) {
                do {

//                    if("xx".equals(v.getValue() + "")) {
//                        System.out.println("v: " + v);
//                    }

                    mind.getTValues().set(t, v);
                    if (updateDomains(tvars.headSet(t), masterSet, slaveSet, logging)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);

//                mind.getTValues().set(t, null);
//                if (!updateDomains(tvars.headSet(t), set, logging)) {
//                    result = false;
//                }

            } else {
                if (updateDomains(tvars.headSet(t), masterSet, slaveSet, logging)) {
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

                mind.getTValues().mark();
                mind.getFValues().mark();
                mind.getClosedValues().clear();
                mind.getBlockedValues().clear();

                for (Domain d : master.getSequence()) {
//                    if (saveF == mind.getFValues().getRoot()) {
                    for (Function f : d.getFunctions()) {
                        mind.getCalculator().calculate(f);
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
                mind.getTValues().commit();
                mind.getFValues().commit();

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
                    mind.getTValues().set(t, v);
                    if (!calcFunctions(tvars.headSet(t), set, logging)) {
                        result = false;
                    }
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
        set.addAll(r.getTree());

        do {
            added = false;
            Set<Tree> tmp = new HashSet<>();
            for (Tree t : set) {
                for (Domain d : t.getSequence()) {

                    for (Right rx = mind.getRights().getRoot(); rx != null; rx = rx.getNext()) {
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
        return set;
    }

    public void link(boolean logging) throws RuntimeErrorException {
        link(null, logging);
    }

    public void link(Right r, boolean logging) throws RuntimeErrorException {


        int pass = 0;
//        if (r == null) {
//            mind.clearQueryStatus();
//            mind.getTValues().clear();
//        }

//        mind.getExcludedTrees().clear();
        //TODO: Нужно сделать динамический сет
        Queue<Tree> slave = r != null ? getActualTrees(r) : mind.getActualTrees();
        Queue<Tree> master;
//        if (r != null) {
//            master = new LinkedList<>();
//            master.addAll(r.getTree());
//        } else {
        master = slave;
//        }


        TValue saveT = null;
        FValue saveF = null;
        do {
            saveT = mind.getTValues().getRoot();
            saveF = mind.getFValues().getRoot();

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, String.format("============= LINKER PASS %03d =============", ++pass));
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

                mind.getUsedDomains().clear();
                mind.getUsedTrees().clear();

//                calcFunctions(tset, slave, logging);
                while (updateDomains(tset, master, slave, logging)) ;
                calcFunctions(tset, slave, logging);
//                if (!res) {
//                    break;
//                }

                Tree top = slave.poll();
                slave.add(top);
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

        } while (saveT != mind.getTValues().getRoot() || saveF != mind.getFValues().getRoot());

        mind.getClosedValues().clear();
        mind.getBlockedValues().clear();

//        } while (mind.getSubstituted().size() > 0 || mind.getCalculated().size() > 0);

    }

    private void logBlocked(boolean logging, Domain d) {
        if (logging) {
            mind.getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
        }
    }

    private void logComparsion(boolean logging, Domain d) {
        if (logging && d.isDest()) {
            for (TVariable t : d.getTVariables(true)) {
                if (!t.isEmpty()) {
                    for (int i = 0; i < t.getDstSolves().size(); ++i) {
                        Domain dst = t.getDstSolves().get(i);
                        Domain src = t.getSrcSolves().get(i);

                        if (dst.getPredicate().getId() == d.getPredicate().getId()) {
                            boolean found = false;
                            for (Domain r : t.getUsage()) {
                                if (dst.getId() != r.getId()) {
                                    mind.getLog().add(LogMode.ANALIZER, "Result: " + r.toString());
                                    found = true;
                                }
                            }
                            if (!found) {
                                mind.getLog().add(LogMode.ANALIZER, "Confirmed: " + src);
//                        if (d.getRight().isQuery()) {
//                            a.getT().getDstSolve().setAcceptor(false);
//                        }
                            }
                            mind.getLog().add(LogMode.ANALIZER, "From right  : " + t.getRight().toString());
                            mind.getLog().add(LogMode.ANALIZER, "\tAcceptor: " + dst.toString());
                            mind.getLog().add(LogMode.ANALIZER, "\tDonor   : " + src.toString());
                        }
                    }
                }
            }
        }
    }

    private void logCommit(boolean logging) {
        if (logging) {
            if (mind.getTValues().getRoot() != mind.getTValues().getMark() || mind.getFValues().getRoot() != mind.getFValues().getMark()) {
                for (TValue t = mind.getTValues().getRoot(); t != mind.getTValues().getMark(); t = t.getNext()) {
                    if (t.isClosed()) {
                        mind.getLog().add(LogMode.ANALIZER, "CLOSED:\t" + t.toString());
                    }
                }
//                for (FValue t = mind.getFValues().getRoot(); t != mind.getFValues().getMark(); t = t.getNext()) {
//                    mind.getLog().add(LogMode.ANALIZER, (t.isClosed() ? "COMMIT:\t" : "RELEASE:\t") + t.toString());
//                }
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
    }
}
