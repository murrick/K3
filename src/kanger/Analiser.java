package kanger;

import kanger.compiler.SysOp;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public boolean checkSequence(Tree t, Tree u, boolean logging) throws RuntimeErrorException {
        boolean result = false;

        List<Domain> sequence = new ArrayList<>();
        sequence.addAll(t.getSequence());
        sequence.addAll(u.getSequence());

        mind.getClosedDomains().clear();

        boolean showFalse = true;
        for (int k = 0; k < sequence.size(); ++k) {
            Domain a = sequence.get(k);

            //TODO: Системные пока отключил
//            if ((!a.isClosed() || a.getRight().isQuery()) && a.isSystem() && a.isSubstituted()) {
//                int res = a.execSystem();
//                if (res == 1) {
//                    a.setClosed();
//
////                    if (logging) {
////                        mind.getLog().createTVar(LogMode.ANALIZER, "System predicate resolved : ");
////                        for (Domain x : sequence) {
////                            mind.getLog().createTVar(LogMode.ANALIZER, "\t" + x.toString());
////                        }
////                        mind.getLog().createTVar(LogMode.ANALIZER, "Сoincidence : ");
////                        mind.getLog().createTVar(LogMode.ANALIZER, "\t" + a.toString());
////                        mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
////                    }
//
//                }
//            }

            if (a.isClosed()) {
                continue;
            }

            for (int j = k + 1; j < sequence.size(); ++j) {
                Domain b = sequence.get(j);

                if (b.isClosed()) {
                    continue;
                }

//                if (b.recalculate()) {
//                    b.setQueued(true);
//                }
                if (a.getId() != b.getId()
                        && a.getPredicate().getId() == b.getPredicate().getId()
                        && a.isAntc() != b.isAntc()
//                        && !a.isPairedWith(b)
//                        && (!a.isDestFor() || a.getRight().isQuery() /*|| a.isUsed()*/)
//                        && (!b.isDestFor() || b.getRight().isQuery() /*|| b.isUsed()*/)
//                        && a.isQuery() != b.isQuery()
                ) {
                    boolean equals = true;
                    for (int i = 0; i < a.getPredicate().getRange(); ++i) {
                        Argument xa = a.getArguments().get(i);
                        Argument xb = b.getArguments().get(i);
                        if (!xa.isEmpty() && !xb.isEmpty()
//                                && (!a.isDestFor() || xa.getValue().getRight().isQuery() /*|| a.isUsed()*/)
//                                && (!b.isDestFor() || xb.getValue().getRight().isQuery() /*|| b.isUsed()*/)
                                //                                    && !(xa.isTSet() && xb.isTSet() && xa.getT().getId() == xb.getT().getId())
                                //                                    && (!xa.isDestFor(b) || a.getRight().isQuery() || b.getRight().isQuery())
                                //                                    && (!xb.isDestFor(a) || b.getRight().isQuery() || a.getRight().isQuery())
                                && xa.getValue().getId() == xb.getValue().getId()) {
                        } else {
                            equals = false;
                        }
                    }
                    if (equals) {
                        result = true;

                        if (!a.isClosed() || !b.isClosed() || !t.isUsed() || !u.isUsed()) {
                            a.setClosed();
                            b.setClosed();
                            t.setUsed();
                            u.setUsed();

//                            if (a.getRight().isQuery()) {
//                                mind.getSolutions().createTVar(b.toString());
//                            } else if ((b.getRight().isQuery())) {
//                                mind.getSolutions().createTVar(a.toString());
//                            } else {
//                                mind.getSolutions().createTVar(a.toString());
//                                mind.getSolutions().createTVar(b.toString());
//                            }
                            if (logging) {
                                mind.getLog().add(LogMode.ANALIZER, "Sequence resolved : ");
                                for (Domain x : sequence) {
                                    mind.getLog().add(LogMode.ANALIZER, "\t" + x.toString());
                                }
                                mind.getLog().add(LogMode.ANALIZER, "Сoincidence : ");
                                mind.getLog().add(LogMode.ANALIZER, "\t" + a.toString());
                                mind.getLog().add(LogMode.ANALIZER, "\t" + b.toString());
                                a.setUsed();
                                b.setUsed();
                            }
                            showFalse = true;

//                            List<TVariable> list;
//                            if (a.getRight().isQuery()) {
//                                list = a.getTVariables(true);
//                            } else if (b.getRight().isQuery()) {
//                                list = b.getTVariables(true);
//                            } else {
//                                list = getTVariables(Arrays.asList(a, b));
//                            }
//
//                            if (!list.isEmpty()) {
//                                if (logging) {
//                                    mind.getLog().createTVar(LogMode.ANALIZER, "Values : ");
//                                }
//                                for (TVariable tv : list) {
//                                    mind.getValues().createTVar(tv.getName() + "=" + tv.getValue());
//                                    if (logging) {
//                                        mind.getLog().createTVar(LogMode.ANALIZER, "\t" + tv.getName() + "=" + tv.getValue());
//                                    }
//                                }
//                            }
                            if (logging) {
                                mind.getLog().add(LogMode.ANALIZER, "===========================================");
                            }
                        } else {
                            showFalse = false;
                        }

                    }
//                    } else {
//                        if()
//                        result = false;
//                        break;
                }

            }
        }


        if (result) {
            for (Domain d : sequence) {
//                d.recalculate();
//                if (!d.isClosed() && !d.isDestFor() /*&& !d.isSystem()*/) {
                //TODO: Вместо isQuery надо какой-то другой критерий в примере с a(nnn)
                if (!d.isClosed() && !d.isDestFor() /*&& !d.isQuery()*/) {
                    result = false;
                    mind.getHypotesisStore().add(!d.isAntc(), d.getPredicate(), d.getArguments());

//                    if (!d.isQueued()) {
//                    mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
                    if (showFalse) {
                        if (logging) {
                            mind.getLog().add(LogMode.ANALIZER, "NOT in condition: " + d.toString());
                            mind.getLog().add(LogMode.ANALIZER, "===========================================");
                        }
                    }
                }
//                }

            }
            if (result) {
                t.setClosed(true);
                u.setClosed(true);
                collectResults(false, sequence);


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
            } else {
            }
        }

        if (!result) {
            collectResults(true, sequence);
        }

        return result;
    }

    private boolean recurseTree(List<TVariable> tvars, int tIndex, Set<Tree> set, boolean logging) throws RuntimeErrorException {
        boolean result = false;
        if (tIndex >= tvars.size()) {

            Set<Function> fs = new HashSet<>();
            Set<Domain> sd = new HashSet<>();
//            SortedSet<HypotesisStore> hypotesis = new TreeSet<>();

            for (Tree t : set) {
                for (Tree x : set) {
                    if (t.getId() != x.getId() && !x.isExcluded(t)) {
                        if (checkSequence(t, x, logging)) {
                            result = true;
                        }
                    }
                }
            }

            for (Tree t : set) {
                for (Domain d : t.getSequence()) {
                    fs.addAll(d.getFunctions());
                    if (d.isSystem()) {
                        sd.add(d);
                    }
                }
            }

//            mind.getCalculated().clear();
//
//            for (Function f : fs) {
//                if (!f.isCalculable() || f.isSubstituted()) {
//                    f.clearResult();
//                    mind.getCalculator().calculate(f);
//                }
//            }

//            mind.getSubstituted().clear();

            boolean occurrs = false;
            for (Domain d : sd) {
                int res = d.execSystem();
                if (res == 0) { //(res == 0 && !d.isAntc()) || (res == 1 && d.isAntc())) {
                    result = false;
                    occurrs = true;
                } else if (res == 1) {
                    //TODO: Срабатывает временами неверно
                    if (d.isQuery()) {
                        result = true;
                    }
                    occurrs = true;
                }
            }

            if (occurrs) {
                collectResults(!result, sd);
            }


        } else {
            TVariable t = tvars.get(tIndex);
            TValue v = t.rewind();
            mind.getSubstituted().add(t);
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
            mind.getSubstituted().remove(t);
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

    private void collectResults(boolean hypotesis, Iterable<Domain> sequence) throws RuntimeErrorException {

        Set<Domain> suc = new HashSet<>();
        Set<Domain> ant = new HashSet<>();

        for (Domain d : sequence) {
            if (d.isSystem()) {
                //TODO: Было закомментировано почему-то
                for (TVariable tv : d.getTVariables(true)) {
                    mind.getValues().add(tv, d);
                }
            } else if (d.isClosed() /*|| d.isDestFor()*/) {
                if (d.isAntc()) {
                    ant.add(d);
                } else {
                    suc.add(d);
                }
            }
        }
        for (Domain d : ant) {
            if (contains(d, suc)) {
                if (!hypotesis) {
//                    int sz = mind.getSolutions().size();
                    mind.getSolutions().add(d);

//                    if (sz != mind.getSolutions().size()) {
                    for (TVariable tv : d.getTVariables(true)) {
                        mind.getValues().add(tv, d);
                    }
//                    }
                }
            } else if (hypotesis /*&& !d.getRight().isQuery()*/) {
                //TODO: Отключил для эксперимента
//                mind.getHypotesisStore().add(true, d.getPredicate(), d.getArguments());
            }
        }
        for (Domain d : suc) {
            if (contains(d, ant)) {
                if (!hypotesis) {
//                    int sz = mind.getSolutions().size();
//                    mind.getSolutions().createTVar(d);

//                    if (sz != mind.getSolutions().size()) {
                    for (TVariable tv : d.getTVariables(true)) {
                        mind.getValues().add(tv, d);
                    }
//                    }
                }
            }
//            else if (hypotesis) {
//                mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
//            }
        }
        if (hypotesis) {
            for (Domain d : suc) {
                if (!contains(d, ant) /*&& !d.getRight().isQuery()*/) {
                    //TODO: Отключил для эксперимента
//                    mind.getHypotesisStore().add(true, d.getPredicate(), d.getArguments());
                }
            }
        }

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

        mind.getSubstituted().clear();
        mind.getCalculated().clear();


        Set<Tree> set = mind.getActualTrees();
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
        mind.clearQueryStatus();

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

//        List<Right> todo = new ArrayList<>();
//        for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
//            if (r.equals(target)) {
//                todo.createTVar(r);
//            }
//        }
//        for (Right r : todo) {
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

        mind.getHypotesisStore().enable(!testMode);
        mind.getValues().enable(!testMode);
        mind.getSolutions().enable(!testMode);
        mind.getLog().enable(!testMode);

        mind.getLog().clear();
        mind.getSolutions().clear();
        mind.getValues().clear();
        mind.getHypotesisStore().clear();

//        mind.reset();
        mind.clearQueryStatus();
//        mind.clearLinks();
//        mind.mark();

        //mind.clear();
//        mind.release();
//        isHypotheses = false;
        isInsertion = false;

        mind.getLog().add(LogMode.ANALIZER, "============= CHECKING ===================");
        mind.getLinker().link(true);
        mind.mark();
//        mind.mark();

        if (analiser(true)) {
            mind.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
            res = null;
        } else {
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

                    mind.clearQueryStatus();

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

                        //mind.getLinker().link(r, true);
                        mind.getLinker().link(true);
                        if (analiser(true)) {
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

                        if (!DEBUG_DISABLE_FALSE_CHECK) {

                            mind.getLog().add(LogMode.ANALIZER, "============= FALSE CHECKING ==============");

//
//                            analiser(false);
                            mind.clearQueryStatus();

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
                                mind.getLinker().link(r, true);

                                if (analiser(true)) {
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

                        mind.clearQueryStatus();

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
                            mind.getLinker().link(r, true);

                            if (analiser(true)) {
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
                            } else //                                if (!isInsertion) {
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
                                if (mind.getHypotesisStore().getRoot() != null && mind.getHypotesisStore().size() > 0) {
                                    mind.getLog().add(LogMode.ANALIZER, String.format("Result: WHO KNOWS? %d Hypotheses", mind.getHypotesisStore().size()));
                                } else {
                                    mind.getLog().add(LogMode.ANALIZER, "Result: WHO KNOWS? No Hypotheses.");
                                }
                        }


                        mind.release();

                    }
                    break;
                }
            }
        }

        mind.getHypotesisStore().enable(storeH);
        mind.getValues().enable(storeV);
        mind.getSolutions().enable(storeS);
        mind.getLog().enable(storeL);

        return res;
    }

    private void logResult() {
        if (mind.getSolutions().size() > 0) {
            mind.getLog().add(LogMode.SOLVES, "Solves:");
            int i = 0;
            for (Solution log : mind.getSolutions().getRoot()) {
                mind.getLog().add(LogMode.SOLVES, String.format("\tSolution %03d: %s", ++i, log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
            mind.getLog().add(LogMode.VALUES, "Values:");
            int i = 0;
            for (TMeaning log : mind.getValues().getRoot()) {
                mind.getLog().add(LogMode.VALUES, String.format("\tSolution %03d: %s", ++i, log.toString()));
            }
        }

    }

}
