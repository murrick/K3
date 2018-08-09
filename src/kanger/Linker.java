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

    private boolean linkFunctions(Domain master, Domain slave, int level, boolean logging, boolean occurrsMaster, boolean occurrsSlave) throws RuntimeErrorException {

        if (level >= master.getPredicate().getRange()) {

            boolean occurrs = false;
            if (occurrsMaster) {
                for (Function f : master.getFunctions()) {
                    if (!f.isCalculated() && f.isCalculable() && !f.isSubstituted()) {
                        if (mind.getCalculator().calculate(f) > 0) {
                            occurrs = true;
                            mind.getFValues().add(f);
                            if (logging) {
                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
                            }
                        }
                    }
                }
            }
            if (occurrsSlave) {
                for (Function f : slave.getFunctions()) {
                    if (!f.isCalculated() && f.isCalculable() && !f.isSubstituted()) {
                        if (mind.getCalculator().calculate(f) > 0) {
                            occurrs = true;
                            mind.getFValues().add(f);
                            if (logging) {
                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
                            }
                        }
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
            for (int i = 0; i <= level; ++i) {

                if (master.get(level).isFSet()
                        && !slave.get(level).isEmpty()
                        && !master.get(level).getF().isSubstituted()
                        && !master.get(level).getF().isCalculated()) {
                    master.get(level).getF().setResult(slave.get(level).getValue());
                    occurrsMaster = true;
                }

                if (slave.get(level).isFSet()
                        && !master.get(level).isEmpty()
                        && !slave.get(level).getF().isSubstituted()
                        && !slave.get(level).getF().isCalculated()) {
                    slave.get(level).getF().setResult(master.get(level).getValue());
                    occurrsSlave = true;
                }

            }
            return linkFunctions(master, slave, level + 1, logging, occurrsMaster, occurrsSlave);
        }

    }


    private boolean linkDomains(Domain master, Domain slave, int level, boolean logging, boolean occurrsMaster, boolean occurrsSlave) {

        if (level >= master.getPredicate().getRange()) {

            if (logging && (occurrsMaster || occurrsSlave)) {
                if (occurrsMaster) {
                    logComparsion(master);
                }
                if (occurrsSlave) {
                    logComparsion(slave);
                }
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
            return occurrsMaster || occurrsSlave;

        } else {

            boolean isQuery = mind.getQuery() != null;

            //ПОДСТАНОВКИ T-переменных
            for (int i = 0; i <= level; ++i) {

                if (master.get(i).isTSet()
                        && !slave.get(i).isEmpty()
                        && !slave.isDestFor(i, master)
//                        && !master.isDestFor(i, slave)
                        && master.get(i).getT().isEmpty()
                        && master.getVarOrder(i) >= slave.getVarOrder(i)
//                        && (!slave.get(level).getValue().isCVar() || !master.isAntc() || slave.get(level).getValue().getIndex() < master.get(level).getT().getIndex())
//                        && (!master.isDestFor() || (!master.get(level).isEmpty() && master.get(level).getValue().getRight().isQuery()))
                ) {
                    TValue s;
                    if (!master.get(i).getT().contains(slave.get(i).getValue())) {
                        s = master.get(i).getT().setValue(slave.get(i).getValue());
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
                        && slave.get(i).getT().isEmpty()
                        && slave.getVarOrder(i) >= master.getVarOrder(i)
//                        && (!master.get(level).getValue().isCVar() || !slave.isAntc() || master.get(level).getValue().getIndex() < slave.get(level).getT().getIndex())
//                        && (!slave.isDestFor() || (!slave.get(level).isEmpty() && slave.get(level).getValue().getRight().isQuery()))
                ) {
                    TValue s;
                    if (!slave.get(i).getT().contains(master.get(i).getValue())) {
                        s = slave.get(i).getT().setValue(master.get(i).getValue());
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
            return linkDomains(master, slave, level + 1, logging, occurrsMaster, occurrsSlave);
        }
    }

    public boolean updateDomains(SortedSet<TVariable> tvars, Queue<Tree> set, boolean logging) throws RuntimeErrorException {
        boolean result = true;
        if (tvars.isEmpty()) {

            for (Tree master : set) { //query == null ? set : query.getTree()) {
                for (Tree slave : set) {

                    mind.getTValues().mark();
                    mind.getFValues().mark();
                    mind.getClosedValues().clear();
                    mind.getBlockedValues().clear();

                    Set<Domain> sequence = new HashSet<>();
                    sequence.addAll(master.getSequence());
                    sequence.addAll(slave.getSequence());

//                    System.out.println();
//                    for (Domain d : sequence) {
//                        System.out.println(d);
//                    }

                    for (Domain d1 : master.getSequence()) {
                        for (Domain d2 : slave.getSequence()) {
                            if (d1.getId() != d2.getId()
                                    && d1.isAntc() != d2.isAntc()
                                    && d1.getPredicate().getId() == d2.getPredicate().getId()
                            ) {
                                linkDomains(d1, d2, 0, logging, false, false);
                                linkFunctions(d1, d2, 0, logging, false, false);
                            } else if (d1.getId() == d2.getId() && d1.isSystem()) {
                                linkFunctions(d1, d2, 0, logging, false, false);
                            }
                        }
                    }

                    for (Domain d : sequence) {
                        if (d.isSystem()) {
                            int res = d.execSystem();
                            for (TVariable t : d.getTVariables(true)) {
                                if (!t.isEmpty()) {
                                    if (res == 0) {
                                        mind.getTValues().get(t).setBlocked();
                                    } else if (res == 1) {
                                        mind.getTValues().get(t).setClosed();
                                    }
                                }
                            }
                        }
                    }

                    logCommit(logging);
                    mind.getTValues().rollback();
                    mind.getFValues().rollback();

                }
            }

//            if (!testDomains(tvars, 0, set, logging)) {
//                result = false;
//            }

//            if (result) {
//            logCommit(logging);
//            mind.getTValues().rollback();
//            mind.getFValues().rollback();
//            } else {
//                logCommit("RELEASE (D):", logging);
//                mind.getTValues().release();
//                mind.getFValues().release();
//            }


        } else {
            TVariable t = tvars.last(); //.get(tIndex);
            TValue v = t.rewind();
            if (v != null) {
                mind.getSubstituted().add(t);

                do {
                    mind.getTValues().set(t, v);
                    if (!updateDomains(tvars.headSet(t), set, logging)) {
                        result = false;
                    }
                } while ((v = t.next(v)) != null);

                mind.getTValues().set(t, null);
                if (!updateDomains(tvars.headSet(t), set, logging)) {
                    result = false;
                }

                mind.getSubstituted().remove(t);
            } else {
                if (!updateDomains(tvars.headSet(t), set, logging)) {
                    result = false;
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
                        if ((f.isCalculable() || !f.isCalculated()) && f.isSubstituted()) {
                            f.clearResult();
                            if (mind.getCalculator().calculate(f) > 0) {
                                mind.getFValues().add(f);
                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
                            }
                        }
                    }
//                    }
                }

                for (Domain d : master.getSequence()) {
                    if (d.isSystem()) {
                        int res = d.execSystem();
                        for (TVariable t : d.getTVariables(true)) {
                            if (!t.isEmpty()) {
                                if (res == 0) {
                                    mind.getTValues().get(t).setBlocked();
                                } else if (res == 1) {
                                    mind.getTValues().get(t).setClosed();
                                }
                            }
                        }
                    }
                }

                logCommit(logging);
                mind.getTValues().rollback();
                mind.getFValues().rollback();

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
                mind.getSubstituted().add(t);

                do {
                    mind.getTValues().set(t, v);
                    if (!calcFunctions(tvars.headSet(t), set, logging)) {
                        result = false;
                    }
                } while ((v = t.next(v)) != null);

                mind.getTValues().set(t, null);
                if (!updateDomains(tvars.headSet(t), set, logging)) {
                    result = false;
                }

                mind.getSubstituted().remove(t);
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
        mind.getSubstituted().clear();
        mind.getCalculated().clear();

        Queue<Tree> set = new LinkedList<>();
        if (r == null) {
            set.addAll(mind.getActualTrees());
//            mind.clearQueryStatus();
//            mind.reset();
            //функции!
        } else {
            set.addAll(r.getActualTrees());
//            mind.clearQueryStatus();
        }


//        Screen.showRights(mind, true);
//        mind.getSubstituted().clear();
//        mind.getCalculated().clear();


        set.addAll(mind.getActualTrees());

//        for (Tree t : set) {
//            for (Function f : t.getFunctions()) {
//                if (f.isCalculated()) {
//                    f.clearResult();
//                }
//            }
//
////            for (TVariable tv : t.getTVariables(true)) {
////                tv.clear();
////            }
//        }

//        if (r != null) {
//            for (Tree t : r.getTree()) {
//                for (TVariable tv : t.getTVariables(true)) {
//                    tv.clear();
//                }
//            }
//        }


        TValue saveT = null;
        FValue saveF = null;
        do {
            mind.getSubstituted().clear();
            mind.getCalculated().clear();

            saveT = mind.getTValues().getRoot();
            saveF = mind.getFValues().getRoot();

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, String.format("============= LINKER PASS %03d =============", ++pass));
            }

            SortedSet<TVariable> tset = new TreeSet<>();
            for (Tree t : set) {
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

            for (int i = 0; i < set.size(); ++i) {
                if (updateDomains(tset, set, logging)) {
                    calcFunctions(tset, set, logging);
                }

                Tree top = set.poll();
                ((LinkedList<Tree>) set).add(top);
            }


//            for (Tree t: set) {
//                for (Domain d : t.getSequence()) {
//                    d.execSystem();
//                }
//            }
//            for (Tree t : set) {
//                for (Function f : t.getFunctions()) {
//                    if (f.isSubstituted()) {
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

    private boolean logComparsion(Domain d) {
        if (d.isDest()) {
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
            return true;
        } else {
            return false;
        }
    }

    private void logCommit(boolean logging) {
        if (logging) {
            if (mind.getTValues().getRoot() != mind.getTValues().getMark() || mind.getFValues().getRoot() != mind.getFValues().getMark()) {
                for (TValue t = mind.getTValues().getRoot(); t != mind.getTValues().getMark(); t = t.getNext()) {
                    mind.getLog().add(LogMode.ANALIZER, (t.isClosed() ? "COMMIT:\t" : "RELEASE:\t") + t.toString());
                }
                for (FValue t = mind.getFValues().getRoot(); t != mind.getFValues().getMark(); t = t.getNext()) {
                    mind.getLog().add(LogMode.ANALIZER, (t.isClosed() ? "COMMIT:\t" : "RELEASE:\t") + t.toString());
                }
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
    }
}
