package kanger;

import kanger.enums.LogMode;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.*;

import java.util.*;

// !@x a(x) -> b(x), @y b(y) -> c(y), @z c(z) -> d(z);

/**
 * Created by murray on 26.05.15.
 */
public class Analiser {


    private final Mind mind;

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

        List<Domain> coincidence = new ArrayList<>();
        List<Domain> sequence = new ArrayList<>();
        if (t.getId() != u.getId()) {
            sequence.addAll(t.getSequence());
            sequence.addAll(u.getSequence());
        } else {
            sequence.addAll(t.getSequence());
        }

        mind.getClosedDomains().clear();

        // Контроль системных предикатов
        for (int k = 0; k < sequence.size(); ++k) {
            Domain a = sequence.get(k);
            if (a.isSystem() && a.isUsed() /*&& !a.isAntc()*/) {
                a.setClosed();
            }
        }

        // Основной цикл сравнения последовательности
        for (int k = 0; k < sequence.size(); ++k) {
            Domain a = sequence.get(k);

            //TODO: Системные пока отключил
//            if ((!a.isClosed() || a.getRight().isQuery()) && a.isSystem() && a.isComplete()) {
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

            if (!a.isStored() || a.isClosed()) {
                continue;
            }

            for (int j = k + 1; j < sequence.size(); ++j) {
                Domain b = sequence.get(j);

                if (!b.isStored() || b.isClosed()) {
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
                        if (!a.isClosed()) {
                            a.setClosed();
                        }
                        if (!b.isClosed()) {
                            b.setClosed();
                        }
                    }
                }

            }
        }

        // Контроль звершенности последовательности
        boolean result = false;
        for (int k = 0; k < sequence.size(); ++k) {
            Domain a = sequence.get(k);
            if (a.isClosed()) {
                coincidence.add(a);
                result = true;
            }
        }

        if (result) {

            t.setUsed();
            u.setUsed();
            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, "Sequence resolved : ");
                for (Domain x : sequence) {
                    mind.getLog().add(LogMode.ANALIZER, "\t" + x.toString());
                }
                if (!coincidence.isEmpty()) {
                    mind.getLog().add(LogMode.ANALIZER, "Сoincidence : ");
                    for (Domain x : coincidence) {
                        mind.getLog().add(LogMode.ANALIZER, "\t" + x.toString());
                    }
                }
            }


            boolean at = collectHypotesis(t.getSequence(), logging);
            if (t.getId() != u.getId()) {
                at = collectHypotesis(u.getSequence(), logging) || at;
            }
            if (at) {
                result = false;
            }


            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, "===========================================");
            }

            if (result) {
                t.setClosed(true);
                u.setClosed(true);
                collectResults(coincidence);
            }


        } else {
            collectHypotesis(t.getSequence(), logging);
            collectHypotesis(u.getSequence(), logging);
        }


        return result;
    }

    private boolean collectHypotesis(List<Domain> sequence, boolean logging) {
        boolean occurs = false;
        boolean append = false;
//        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            TODO: Тут коллизия какая-то. Пока не знаю как разрешить
//            if (d.isComplete() && d.isStored() && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                if ((!d.isStored() && !d.isExcluded()) && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                if (logging) {
//                    mind.getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
//                }
//                occurs = true;
//                mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());
//
//            }
//        }

//        if (logging && occurs) {
//            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//        }

        occurs = false;
        List<Domain> coincidence = new ArrayList<>();
        List<Domain> discrepancies = new ArrayList<>();
        for (Domain d : sequence) {
            if (d.isComplete() && !d.isClosed()) {
                if (!d.isExcluded() && !d.isStored()) {
                    append = true;
                    if (!d.isSystem() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                        coincidence.add(d);
                    }
                } else {
                    occurs = true;
                    if ((d.isQuery() || d.isStored()) && d.isProduced() && !d.isExcluded()) {
                        append = true;
                        if (!d.isSystem() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                            discrepancies.add(d);
                        }
                    }
                }
            }
        }


        if (occurs) {
            if (!coincidence.isEmpty()) {
                for (Domain d : coincidence) {
                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Not in condition: " + d.toString());
                    }
                    mind.getHypotesisStore().add(!d.isAntc(), false, d.getPredicate(), d.getArguments());
                }
                if (logging) {
                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            } else if (!discrepancies.isEmpty()) {
                for (Domain d : discrepancies) {
                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
                    }
                    mind.getHypotesisStore().add(!d.isAntc(), false, d.getPredicate(), d.getArguments());
                }
                if (logging) {
                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }
        }

        return append;
    }

    private boolean recurseTree(List<TVariable> tvars, int tIndex, Queue<Tree> set, boolean logging) throws RuntimeErrorException {
        boolean result = false;
        if (tIndex >= tvars.size()) {

            Set<Function> fs = new HashSet<>();
            Set<Domain> sd = new HashSet<>();
//            SortedSet<HypotesisStore> hypotesis = new TreeSet<>();

            for (Tree t : set) {
                for (Tree x : set) {
                    if (!x.isExcluded(t)) {
                        if (checkSequence(t, x, logging)) {
                            result = true;
                        }
                    }
                }
            }
//            for (Tree t : set) {
//                for (Tree x : set) {
//TODO: Это исключение ветвления. Не очень понимаю зачем это. Убрал
//                    if (!x.isExcluded(t)) {

            //TODO: Восстановить сбор гипотез
//            if (checkSequence(logging)) {
//                result = true;
//                collectResults();
////                    }
////                    }
//            }

//            for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
//                for (Tree sequence : r.getTree()) {
//                    for (Domain d : sequence.getSequence()) {
//                        if (!d.isClosed() && !d.isExcluded()) {
//                            result = false;
//                            mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());
//
////                        if (showFalse) {
//                            if (logging) {
//                                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                                mind.getLog().add(LogMode.ANALIZER, "NOT in condition: " + d.toString());
//                            }
////                        }
//                        }
//                    }
//
//                }
//            }


//            }

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

    private void collectResults(Iterable<Domain> sequence) throws RuntimeErrorException {

        Set<Domain> suc = new HashSet<>();
        Set<Domain> ant = new HashSet<>();

        for (Domain d : sequence) {
            if (d.isClosed() && d.getRight().isQuery()) {
//                if (d.isSystem()) {

                mind.getSolutions().add(d);
                for (TVariable tv : d.getTVariables(true)) {
                    mind.getValues().add(tv, d);
                }
//                } else if (d.isAntc()) {
//                    ant.add(d);
//                } else {
//                    suc.add(d);
//                }
            }
        }

        for (Domain d : sequence) {
            if (d.isClosed() && d.isQuery() && !d.getRight().isQuery()) {
//                if (d.isSystem()) {

//                    mind.getSolutions().add(d);
                for (TVariable tv : d.getTVariables(true)) {
                    mind.getValues().add(tv, d);
                }
//                } else if (d.isAntc()) {
//                    ant.add(d);
//                } else {
//                    suc.add(d);
//                }
            }
        }

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


    public boolean analise(boolean logging) throws RuntimeErrorException {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

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

        if (mind.getDatabase().check(logging)) {
            for (Tree t : set) {
                collectResults(t.getSequence());
            }
            result = true;
        } else {

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

        }
        if (logging) {
            mind.getLog().add(LogMode.TIMING, "* Analising time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
        }

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

}
