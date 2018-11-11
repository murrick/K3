package kanger;

import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.*;
import kanger.stores.HypotesisStore;

import java.util.*;

// !@x a(x) -> b(x), @y b(y) -> c(y), @z c(z) -> d(z);

/**
 * Created by murray on 26.05.15.
 */
public class Analiser {

    private static final boolean DEBUG_DISABLE_FALSE_CHECK = false;

    private final Mind mind;
    private boolean isInsertion = false;

    public Analiser(Mind mind) {
        this.mind = mind;
    }

//    public Boolean checkSystem(Domain a, List<Domain> sequence, boolean logging) {
//        Boolean result = null;
//        if (mind.getCalculator().exists(a.getPredicate())) {
//            try {
//                int res = mind.getCalculator().execute(a);
//
//                if ((res == 1 && !a.isAntc())) {
//                    result = true;
//                } else if (res == 0 && !a.isAntc()) {
//                    result = false;
//                }
//
//                if (res != -1) {
//                    mind.getSolutions().createTVar(a);
//
//                    if (logging) {
//                        mind.getLog().createTVar(LogMode.ANALIZER, "Sequence resolved : ");
//                        for (Domain x : sequence) {
//                            mind.getLog().createTVar(LogMode.ANALIZER, "\t" + x.toString());
//                        }
//                        mind.getLog().createTVar(LogMode.ANALIZER, "Сoincidence : ");
//                        mind.getLog().createTVar(LogMode.ANALIZER, "\t" + a.toString());
//                    }
//
//                    List<TVariable> list = a.getRight().getTVariables(true);
//                    if (!list.isEmpty()) {
//                        if (logging) {
//                            mind.getLog().createTVar(LogMode.ANALIZER, "Values : ");
//                        }
//                        for (TVariable tv : list) {
//                            if (!tv.isEmpty()) {
//                                mind.getValues().createTVar(tv, a);
//                                if (logging) {
//                                    mind.getLog().createTVar(LogMode.ANALIZER, "\t" + tv.getVarName() + "=" + tv.getValue());
//                                }
//                            }
//                        }
//                    }
//
//                    if (logging) {
//                        mind.getLog().createTVar(LogMode.ANALIZER, "===========================================");
//                    }
//
//                }
//            } catch (RuntimeErrorException e) {
//                e.printStackTrace();
//            }
//        }
//        return result;
//    }

    public boolean checkSequence(boolean logging) throws RuntimeErrorException {

        List<Domain> coincidence = new ArrayList<>();
//        mind.getClosedDomains().clear();

        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
            if (d.isUsed() && !d.isClosed()) {
                d.setClosed();
                coincidence.add(d);
            }
        }

        for (Predicate p = mind.getPredicates().getRoot(); p != null; p = p.getNext()) {
            for (Domain a : p.getSolves()) {
                if (!a.isStored() || a.isClosed()) {
                    continue;
                }
                for (Domain b : p.getSolves()) {
                    if (!b.isStored() || b.isClosed()) {
                        continue;
                    }

                    if (a.getId() != b.getId()
//                            && a.getPredicate().getId() == b.getPredicate().getId()
                            && a.isAntc() != b.isAntc()) {
                        boolean equals = true;
                        for (int i = 0; i < a.getPredicate().getRange(); ++i) {
                            Argument xa = a.getArguments().get(i);
                            Argument xb = b.getArguments().get(i);
                            if (!xa.isEmpty() && !xb.isEmpty()
                                    && xa.getValue().getId() == xb.getValue().getId()) {
                            } else {
                                equals = false;
                            }
                        }
                        if (equals) {
                            if (!a.isClosed()) {
                                a.setClosed();
                                coincidence.add(a);
                            }
                            if (!b.isClosed()) {
                                b.setClosed();
                                coincidence.add(b);
                            }

                        }
                    }
                }
            }
        }


        if (!coincidence.isEmpty()) {
            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, "Сoincidence : ");
                for (Domain d : coincidence) {
                    mind.getLog().add(LogMode.ANALIZER, "\t" + d.toString());
                }
            }


//            for (Domain d : sequence) {
//                if (!d.isClosed() && !d.isDest()) {
//                    result = false;
//                    mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());
//
//                    if (showFalse) {
//                        if (logging) {
//                            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                            mind.getLog().add(LogMode.ANALIZER, "NOT in condition: " + d.toString());
//                        }
//                    }
//                }
////                }
//
//            }

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, "===========================================");
            }

//            if (result) {
//                t.setClosed(true);
//                u.setClosed(true);
//            collectResults(false, coincidence);


//                for (Domain d : sequence) {
//
//                    if (d.isClosed() || d.isDestFor() || d.isSystem() || d.isQuery()) {
////                            if (d.getRight().isQuery()) {
//                        int sz = mind.getSolutions().size();
//                        mind.getSolutions().createTVar(d);
//
//                        if (sz != mind.getSolutions().size()) {
//                            for (TVariable tv : d.getTVariables(true)) {
//                                mind.getValues().createTVar(tv, d);
//                            }
//                        }
//                    }
//                }


//            } else if(t.isUsed()) {
//                for (Domain d : t.getSequence()) {
//                    if (!d.isUsed()) {
//                        mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
//                    }
//                }
//            } else {
//                collectResults(true,sequence);
//            }
        } else {
            boolean occurs = false;
            for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
                //TODO: Тут коллизия какая-то. Пока не знаю как разрешить
                if (d.isStored() && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                if ((!d.isStored() && !d.isExcluded()) && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Hypotesis: " + d.toString());
                    }
                    occurs = true;
                    mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());

                }
            }
            if (logging && occurs) {
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }

//            collectResults(true, sequence);


        return !coincidence.isEmpty();
    }

    private boolean recurseTree(List<TVariable> tvars, int tIndex, Queue<Tree> set, boolean logging) throws RuntimeErrorException {
        boolean result = false;
        if (tIndex >= tvars.size()) {

//            Set<Function> fs = new HashSet<>();
//            Set<Domain> sd = new HashSet<>();
//            SortedSet<HypotesisStore> hypotesis = new TreeSet<>();

//            List<Domain> dataBase = new ArrayList<>();
//            for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//                if (d.isStored()) {
//                    dataBase.add(d);
//                }
//            }

//            for (Tree t : set) {
//                for (Tree x : set) {
//TODO: Это исключение ветвления. Не очень понимаю зачем это. Убрал
//                    if (!x.isExcluded(t)) {

            //TODO: Заменить на контроль базы данных!!!!!!!!!!!!
            if (checkSequence(logging)) {
                result = true;
                collectResults();
//                    }
//                    }
            }
//            }

//            for (Tree t : set) {
//                for (Domain d : t.getSequence()) {
//                    fs.addAll(d.getFunctions());
//                    if (d.isSystem()) {
//                        sd.add(d);
//                    }
//                }
//            }

//            mind.getCalculated().clear();
//
//            for (Function f : fs) {
//                if (!f.isCalculable() || f.isComplete()) {
//                    f.clearResult();
//                    mind.getCalculator().calculate(f);
//                }
//            }

//            mind.getSubstituted().clear();

//            boolean occurrs = false;
//            for (Domain d : sd) {
//                if (d.isUsed() /*&& !d.isAntc()*/) {
//                    occurrs = true;
//                    result = true;
//                }
//                int res = d.execSystem();
//                if (res == 0) { //(res == 0 && !d.isAntc()) || (res == 1 && d.isAntc())) {
//                    result = d.isAntc();
//                    occurrs = true;
//                } else if (res == 1) {
//                    //TODO: Срабатывает временами неверно
////                    if (d.isQuery()) {
//                        result = !d.isAntc();
////                    }
//                    occurrs = true;
//                }
//            }

//            if (occurrs) {
//                collectResults(!result, sd);
//            }


        } else {
            TVariable t = tvars.get(tIndex);
            TValue v = t.rewind();
            if (v != null) {
                do {
                    mind.getTValues().set(t, v);
                    if (recurseTree(tvars, tIndex + 1, set, logging)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);
            } else {
                if (recurseTree(tvars, tIndex + 1, set, logging)) {
                    result = true;
                }
            }
        }

        return result;
    }

    private boolean contains(Domain d, Set<Domain> set) {
        for (Domain x : set) {
            if (x.equalsBase(d)) {
                return true;
            }
        }
        return false;
    }

    private void collectResults() {

        Set<Domain> suc = new HashSet<>();
        Set<Domain> ant = new HashSet<>();

//        System.out.println("--------------------------");
//        for (Domain d : sequence) {
        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {

            if (d.isClosed() /*|| (d.isStored() && d.isQuery()) || (d.isExcluded() && d.isQuery()) *//*|| (d.isStored() && d.isProduced())) && d.getRight().isQuery()*/) {
//                System.out.println(d);
//                if (d.isSystem()) {

                mind.getSolutions().add(d);
//                if (d.isClosed() && d.isExcluded()) {
                if (d.isQuery()) {
                    for (TVariable tv : d.getTVariables(true)) {
                        mind.getValues().add(tv, d);
                    }
                }
//                } else if (d.isAntc()) {
//                    ant.add(d);
//                } else {
//                    suc.add(d);
//                }
            }
        }

//        for (Domain d : sequence) {
//            if ((d.isClosed() || d.isExcluded()  || (d.isStored() && d.isProduced())) && d.isQuery() && !d.getRight().isQuery()) {
////                if (d.isSystem()) {
//
//                    mind.getSolutions().add(d);
//                for (TVariable tv : d.getTVariables(true)) {
//                    mind.getValues().add(tv, d);
//                }
////                } else if (d.isAntc()) {
////                    ant.add(d);
////                } else {
////                    suc.add(d);
////                }
//            }
//        }

//        for (Domain d : ant) {
//            if (contains(d, suc)) {
//                mind.getSolutions().add(d);
//                for (TVariable tv : d.getTVariables(true)) {
//                    mind.getValues().add(tv, d);
//                }
//            }
//        }

//        for (Domain d : suc) {
//            if (contains(d, ant)) {
//                if (!hypotesis) {
////                    int sz = mind.getSolutions().size();
////                    mind.getSolutions().createTVar(d);
//
////                    if (sz != mind.getSolutions().size()) {
//                    for (TVariable tv : d.getTVariables(true)) {
//                        mind.getValues().add(tv, d);
//                    }
////                    }
//                }
//            }
////            else if (hypotesis) {
////                mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
////            }
//        }
//        if (hypotesis) {
//            for (Domain d : suc) {
//                if (!contains(d, ant) /*&& !d.getRight().isQuery()*/) {
////                    mind.getHypotesisStore().add(true, d.getPredicate(), d.getArguments());
//                }
//            }
//        }

        //result = checkSequence(t, logging);

    }

    //    public boolean analiseTree(Tree t, boolean logging) throws RuntimeErrorException {
//        mind.getClosedDimains().clear();
//        mind.getSubstituted().clear();
//        mind.getCalculated().clear();
//        mind.getQueuedDomains().clear();
//        List<TVariable> vars = t.getTVariables(true);
//        return recurseTree(t, vars, 0, logging);
//    }


    public boolean analiser(boolean logging) throws RuntimeErrorException {
        boolean result = false;
        int counter = 0;

        if (logging) {
            mind.getLog().add(LogMode.ANALIZER, "============= ANALISER ====================");
        }

        Queue<Tree> set = new LinkedList<>();
        for (Right rx = mind.getRights().getRoot(); rx != null; rx = rx.getNext()) {
            set.addAll(rx.getTree());
        }

        Set<TVariable> tvars = new HashSet<>();
        for (Tree t : set) {
            tvars.addAll(t.getTVariables(true));

//                for(Function f: t.getFunctions()) {~
//                    f.clearResult();
//                }
        }

//        for(Predicate p = mind.getPredicates().getRoot(); p != null; p = p.getNext()) {
//            if(p.checkSolves())
//                return true;
//        }
//        return false;
        Set<Tree> query = new HashSet<>();
        for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
            if (r.isQuery()) {
                query.addAll(r.getTree());
            }
        }

//        mind.getClosedDimains().clear();
//        mind.getQueuedDomains().clear();
        mind.getUsedTrees().clear();
        mind.getClosedDomains().clear();

        result = recurseTree(new ArrayList<>(tvars), 0, set, logging);

//        for (Tree t = mind.getTrees().getRoot(); t != null; t = t.getNext()) {
//            if (analiseTree(t, logging)) {
//                result = true;
//            }
//            for (Tree x : query) {
//                if (t.getId() != x.getId()) {
//                    t.getSequence().addAll(x.getSequence());
//                    if (analiseTree(t, logging)) {
//                        result = true;
//                    }
//                    t.getSequence().removeAll(x.getSequence());
//                }
//            }
//        }
//        int countUsed;
//        int countClosed;
//
//        do {
//            countUsed = mind.getUsedTrees().size();
//            countClosed = mind.getClosedTrees().size();
//            for (Tree t = mind.getTrees().getRoot(); t != null; t = t.getNext()) {
//                for (Tree x = mind.getTrees().getRoot(); x != null; x = x.getNext()) {
//                    if (t.getId() != x.getId() && !t.isClosed() && !x.isClosed() && t.isUsed() && x.isUsed()) {
//                        t.getSequence().addAll(x.getSequence());
//                        if (analiseTree(t, logging)) {
//                            result = true;
//                        }
//                        t.getSequence().removeAll(x.getSequence());
//                    }
//                }
//            }
//        } while (countClosed != mind.getClosedTrees().size() || countUsed != mind.getUsedTrees().size());
        return result;
    }

    //    ///////////////////////////////
//    void storeHypo() {
//        for (Right r1 = mind.getRights().getRoot(); r1 != null; r1 = r1.getNext()) {
//            for (Tree t : r1.getTree()) {
//                if (!t.isClosed() && t.isUsed()) {
//                    for (Domain d : t.getSequence()) {
//                        if (!d.isUsed()) {
//                            mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
//                        }
//                    }
//                }
//            }
//        }
//    }
    //    ///////////////////////////////
    private List<Right> killInsertion(Right target, boolean withRelatedRights) {
        int flag = 0;
        mind.reset();

        mind.getUsedTrees().clear();
        mind.getClosedTrees().clear();
        mind.getExcludedTrees().clear();

        mind.getUsedDomains().clear();
        mind.getClosedDomains().clear();
        mind.getQueryValues().clear();

//        mind.clearQueryStatus();

        List<Right> rr = new ArrayList<>();

        if (mind.getHypotesisStore().size() > 0) {
            for (Hypotese h : (List<Hypotese>) mind.getHypotesisStore().getRoot()) {
//                h.getPredicate().deleteSolve(h.getSolve());
                if (withRelatedRights) {

                    for (Right r : h.getRights()) {
                        rr.add(r);
                        mind.removeInsertionRight(r);
                    }
                }
            }
        }
//        else if (target.getWidth() == 1 && target.getHeight() == 1) {
//            Solution s = target.getT().getD().getPredicate().deleteSolve(target.getT().getD().getArguments());
//            if (withRelatedRights && s != null) {
//                if (s.getRight() != null) {
//                    rr.createTVar(s.getRight());
//                    mind.removeInsertionRight(s.getRight());
//                }
//            }
//        }

//        mind.mark();
        return rr;

//        List<Right> todoo = new ArrayList<>();
//        for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
//            if (r.equals(target)) {
//                todoo.createTVar(r);
//            }
//        }
//        for (Right r : todoo) {
//            mind.removeInsertionRight(r);
//        }
    }

    /////////////////////////////////////
    private String invert(String line) {
        if (line.charAt(0) == Enums.ANT) {
            return String.format("%c%s", Enums.SUC, line.substring(1));
        } else {
            return String.format("%c%s", Enums.ANT, line.substring(1));
        }
    }

    private String resign(int sign, String line) {
        return String.format("%c%s", sign, line.substring(1));
    }

    public boolean isInsertion() {
        return isInsertion;
    }

    public Boolean query(String line, boolean testMode) throws ParseErrorException, RuntimeErrorException {
        Boolean res = null;

        boolean storeH = mind.getHypotesisStore().isEnabled();
        boolean storeV = mind.getValues().isEnabled();
        boolean storeS = mind.getSolutions().isEnabled();
        boolean storeL = mind.getLog().isEnabled();

        HypotesisStore excludeHypotesis = new HypotesisStore();

        mind.getHypotesisStore().enable(!testMode);
        mind.getValues().enable(!testMode);
        mind.getSolutions().enable(!testMode);
        mind.getLog().enable(!testMode);

        mind.getLog().clear();
        mind.getSolutions().clear();
        mind.getValues().clear();
        mind.getHypotesisStore().clear();

        mind.getUsedTrees().clear();
        mind.getClosedTrees().clear();
        mind.getExcludedTrees().clear();

        mind.getUsedDomains().clear();
        mind.getClosedDomains().clear();
        mind.getQueryValues().clear();

//        mind.reset();
//        mind.clearQueryStatus();
//        mind.clearLinks();
//        mind.mark();

        //mind.clear();
//        mind.release();
//        isHypotheses = false;
        isInsertion = false;
        long queryStart = System.currentTimeMillis();

        mind.getLog().add(LogMode.ANALIZER, "============= CHECKING ===================");
        long start = System.currentTimeMillis();
        mind.getLinker().link(true);
        System.out.println("* CHECKING Linking time \t" + ((System.currentTimeMillis() - start) / 1000.0));

        //TODO: Поменял местами с концом
        mind.release();
        mind.mark();
//        mind.mark();

        start = System.currentTimeMillis();
        Boolean ar = analiser(true);
        System.out.println("* CHECKING Analise time \t" + ((System.currentTimeMillis() - start) / 1000.0));
        if (ar) {
            mind.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
            res = null;
        } else {

//            excludeHypotesis.clear();
            excludeHypotesis.addAll(mind.getHypotesisStore().getRoot());

//            mind.mark();
            int key = line.charAt(0);
            switch (key) {

                case Enums.INS:
                    isInsertion = true;
                    line = resign(Enums.ANT, line);

                case Enums.ANT: {
//                    isHypotheses = true;

//                    mind.getSolutions().reset();
//                    mind.getValues().reset();
//                    mind.getSubstitutions().reset();
//                    mind.getLog().reset();
                    mind.getLog().add(LogMode.ANALIZER, "============= ACCEPTING ===================");

//                    mind.release();
//                    analiser(false);
//                    Right r = (Right) mind.compileLine(invert(line));
//                    if (r != null) {
//                        mind.getLog().createTVar(LogMode.ANALIZER, "Compiled: " + r.getOrig());
//                        mind.getLog().createTVar(LogMode.ANALIZER, r.getT());
//                        mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
//
//                        if (analiser(false)) {
//                            mind.getLog().createTVar(LogMode.ANALIZER, "ERROR: Conflict in new Right");
//                            res = null;
//                        } else {
//                            res = false;
//                        }
//                    }
//
//                    if (res != null) {
//                        mind.release();
//                        analiser();

//                    mind.clearQueryStatus();

                    mind.getUsedTrees().clear();
                    mind.getClosedTrees().clear();
                    mind.getExcludedTrees().clear();

                    mind.getUsedDomains().clear();
                    mind.getClosedDomains().clear();
                    mind.getQueryValues().clear();

                    mind.getSolutions().clear();
                    mind.getValues().clear();
                    mind.getHypotesisStore().clear();

                    mind.mark();
                    Right r = (Right) mind.compileLine(line);
//                    r.setQuery(true);

                    if (r != null) {
                        mind.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                        mind.getLog().add(LogMode.ANALIZER, r);
                        mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");

                        start = System.currentTimeMillis();
                        mind.getLinker().link(r, true);
                        System.out.println("* ACCEPTING Linking time \t" + ((System.currentTimeMillis() - start) / 1000.0));

                        start = System.currentTimeMillis();
                        ar = analiser(true);
                        System.out.println("* ACCEPTING Analise time \t" + ((System.currentTimeMillis() - start) / 1000.0));
                        if (ar) {
                            mind.getLog().add(LogMode.ANALIZER, "ERROR: Conflict in new Right");
                            mind.release();
                            res = null;
                        } else {
                            res = true;
                            if (!isInsertion) {
                                //mind.release();
//                                mind.getText().append(line);
//                                mind.getText().append("\r");
                                //mind.compileLine(line);
                                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution 000:\t%s", line));
//                                res = true;
                                mind.getLog().add(LogMode.ANALIZER, "SUCCESS: New Right Accepted");
                            } else {
                                mind.removeInsertionRight(r);
                                if (mind.getHypotesisStore().size() != 0) {
                                    mind.getLog().add(LogMode.SAVED, "Predicates added:");
                                    int i = 0;
                                    for (Hypotese s : (List<Hypotese>) mind.getHypotesisStore().getRoot()) {
//                                        mind.getText().append(String.format("%c%s", Enums.ANT, s.toString()) + "\r");
//                                        mind.getSolutions().createTVar(String.format("%c%s", Enums.ANT, s.toString()));
                                        mind.getLog().add(LogMode.SAVED, String.format("\tSolution %03d: \t%s", ++i, String.format("%c%s", Enums.ANT, s.toString())));
                                    }
                                }
                                mind.getLog().add(LogMode.ANALIZER, "SUCCESS: New solves: " + mind.getHypotesisStore().size());
                            }

                            mind.setChanged(true);
                            mind.commit();
                        }
                    } else {
                        mind.release();
                    }
//                    }

//                    mind.release();
//                    mind.clearQueryStatus();
                }
                break;

                case Enums.DEL:
                case Enums.WIPE:
                    SysOp op = mind.getCalculator().find(line);
                    if (op != null) {
                        if (mind.getCalculator().unregister(op.toString())) {
                            mind.getLog().add(LogMode.ANALIZER, "SUCCESS: Function removed: " + op.toString());
                        } else {
                            mind.getLog().add(LogMode.ANALIZER, "WARNING: Unable to remove function: " + op.toString());
                        }
                        break;
                    } else {
                        isInsertion = true;
                        line = resign(Enums.SUC, line);
                    }

                case Enums.SUC: {
//                    mind.release();

//                    mind.getSolutions().reset();
//                    mind.getValues().reset();
//                    mind.getSubstitutions().reset();
                    //mind.getPredicates().mark();
                    if (line.length() == 1) {
                        mind.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
                        res = true;
                    } else if (!isInsertion) {

//                        mind.release();
//                        mind.mark();
//                        isHypotheses = true;

//                        mind.getLog().clear();

//                        mind.getTValues().mark();
//                        mind.getFValues().mark();

                        if (!DEBUG_DISABLE_FALSE_CHECK) {

                            mind.getLog().add(LogMode.ANALIZER, "============= FALSE CHECKING ==============");

//
//                            analiser(false);
//                            mind.clearQueryStatus();

                            mind.getUsedTrees().clear();
                            mind.getClosedTrees().clear();
                            mind.getExcludedTrees().clear();

                            mind.getUsedDomains().clear();
                            mind.getClosedDomains().clear();
                            mind.getQueryValues().clear();

                            mind.getSolutions().clear();
                            mind.getValues().clear();
                            mind.getHypotesisStore().clear();

                            mind.mark();
                            Right r = (Right) mind.compileLine(invert(line));

                            if (r != null) {
                                r.setQuery(true);

                                mind.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                                mind.getLog().add(LogMode.ANALIZER, r);
                                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");


//                                mind.markAcceptors();
//                                mind.getRights().release();
//                                mind.getTrees().release();
//                                mind.getDomains().release();
                                start = System.currentTimeMillis();
                                mind.getLinker().link(r, true);
                                System.out.println("* FALSE CHK Linking time \t" + ((System.currentTimeMillis() - start) / 1000.0));

                                start = System.currentTimeMillis();
                                ar = analiser(true);
                                System.out.println("* FALSE CHK Analise time \t" + ((System.currentTimeMillis() - start) / 1000.0));
                                if (ar) {
                                    mind.getLog().add(LogMode.ANALIZER, "Result: FALSE");
                                    logResult();
                                    res = false;
                                }
//                                else if (!isInsertion) {
//                                    storeHypo();
////                                isHypotheses = false;
//
//                                    //mind.release();
////                            mind.getSolutions().reset();
////                            mind.getValues().reset();
////                            mind.getSubstitutions().reset();
////                            mind.getSolutions().reset();
////                            mind.getValues().reset();
////                            mind.getSubstitutions().reset();
////                            analiser();
//                                    //mind.clear();
//                                }
                            }

//                            mind.release();

//                            if (res == null) {
                            mind.release();
//                                Screen.showTValues(mind);
//                            }
//                                mind.reset();
//                                mind.clearQueryStatus();
//                                mind.getLinker().link(false);
//                                mind.releaseAcceptors();
//                            }

                        }
                    }

                    if (res == null) {
                        mind.getLog().add(LogMode.ANALIZER, "============= TRUE CHECKING ===============");

                        //mind.release();
//                        mind.release();
//                        analiser();
                        //analiser();
//                                mind.release();
//                                mind.clearQueryStatus();
//                                mind.getLinker().link(true);

//                        mind.clearQueryStatus();

                        mind.getUsedTrees().clear();
                        mind.getClosedTrees().clear();
                        mind.getExcludedTrees().clear();

                        mind.getUsedDomains().clear();
                        mind.getClosedDomains().clear();
                        mind.getQueryValues().clear();

                        mind.getSolutions().clear();
                        mind.getValues().clear();
                        //TODO: Нужно ли собирать гипотезы для отрицания? Видимо да!
//                        mind.getHypotesisStore().setAntc(false);
//                        mind.getHypotesisStore().clear();

                        mind.mark();

                        Right r = (Right) mind.compileLine(line);
                        if (r != null) {

//                            Screen.showBase(mind, false, null);
//                            Screen.showRights(mind, true);
                            r.setQuery(true);
                            mind.getLog().add(LogMode.ANALIZER, "Compiled: " + r.getOrig());
                            mind.getLog().add(LogMode.ANALIZER, r);
                            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");


//                            mind.getRights().release();
//                            mind.getTrees().release();
//                            mind.getDomains().release();
//                            if (!isInsertion) {
//                                isHypotheses = true;
//                            }
                            start = System.currentTimeMillis();
                            mind.getLinker().link(r, true);
                            System.out.println("* TRUE CHK Linking time \t" + ((System.currentTimeMillis() - start) / 1000.0));

                            start = System.currentTimeMillis();
                            ar = analiser(true);
                            System.out.println("* TRUE CHK Analise time \t" + ((System.currentTimeMillis() - start) / 1000.0));
                            if (ar) {

                                if (isInsertion) {
                                    mind.removeInsertionRight(r);
                                    List<Right> killedRights = killInsertion(r, key == Enums.WIPE);
                                    if (mind.getHypotesisStore().size() != 0) {
                                        mind.getLog().add(LogMode.SAVED, "Predicates deleted:");
                                        int i = 0;
                                        for (Hypotese s : (List<Hypotese>) mind.getHypotesisStore().getRoot()) {
//                                            mind.getText().append(String.format("%c%s", Enums.ANT, s.toString()) + "\r");
//                                            mind.getSolutions().createTVar(String.format("%c%s", Enums.ANT, s.toString()));
                                            mind.getLog().add(LogMode.SAVED, String.format("\tSolution %03d: \t%s", ++i, String.format("%c%s", Enums.ANT, s.toString())));
                                        }
                                    }
                                    if (killedRights.size() != 0) {
                                        mind.getLog().add(LogMode.SAVED, "Rights deleted:");
                                        for (Right rr : killedRights) {
                                            mind.getLog().add(LogMode.SAVED, String.format("\tRight %03d: \t%s", rr.getId(), rr.getOrig()));
                                        }
                                    }
                                    mind.getLog().add(LogMode.ANALIZER, "SUCCESS: Deleted solves: " + mind.getHypotesisStore().size());

                                } else {
                                    mind.getLog().add(LogMode.ANALIZER, "Result: TRUE");
                                    logResult();
                                    res = true;
                                }
                            } else if (isInsertion) {
                                mind.getLog().add(LogMode.ANALIZER, "Result: No predicates was deleted");
                            } else { //                                if (!isInsertion) {
                                //                                    storeHypo();
                                //                                }
                                //                                mind.release();
                                // Удаляем гипотезы,которые приведут к сходимости в случае анализа правил.
                                // Гипотезв приводящие к сходимости на уровне базы данных отсеиваются в фазе добавления
                                //TODO: Рекурсивный вызов
                                //                                if (!testMode) {
                                //                                    for (int i = 0; i < mind.getHypotesisStore().size(); ++i) {
                                //                                        String h = String.format("%c%s", Enums.SUC, mind.getHypotesisStore().createCVar(i));
                                //                                        Boolean result = query(h, true);
                                //                                        if (result != null) {
                                //                                            mind.getHypotesisStore().createCVar(i).delete();
                                //                                        }
                                //                                    }
                                //                                    mind.getHypotesisStore().pack();
                                //                                }

                                mind.getHypotesisStore().exclude(excludeHypotesis);

                                if (mind.getHypotesisStore().getRoot() != null && mind.getHypotesisStore().size() > 0) {
                                    mind.getLog().add(LogMode.ANALIZER, String.format("Result: WHO KNOWS? %d Hypotheses", mind.getHypotesisStore().size()));
                                } else {
                                    mind.getLog().add(LogMode.ANALIZER, "Result: WHO KNOWS? No Hypotheses.");
                                }
                            }
                        }

//TODO: Померял местами с началом
//                        mind.release();

                    }
                    break;
                }
            }
        }

        mind.getHypotesisStore().enable(storeH);
        mind.getValues().enable(storeV);
        mind.getSolutions().enable(storeS);
        mind.getLog().enable(storeL);

        System.out.println("* QUERY Processing time \t" + ((System.currentTimeMillis() - queryStart) / 1000.0));

        return res;
    }

    private void logResult() {
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solves (" + mind.getSolutions().size() + "):");
            int i = 0;
            for (Solution log : mind.getSolutions().getRoot()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", ++i, log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
            mind.getLog().add(LogMode.VALUES, "Values(" + mind.getValues().size() + "):");
            int i = 0;
            for (TMeaning log : mind.getValues().getRoot()) {
                mind.getLog().add(LogMode.VALUES, String.format("\tSolution %03d: %s", ++i, log.toString()));
            }
        }

    }

}
