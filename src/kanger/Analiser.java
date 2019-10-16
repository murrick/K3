package kanger;

import kanger.enums.ArgumentType;
import kanger.enums.LogMode;
import kanger.primitives.Argument;
import kanger.primitives.Hypotese;
import kanger.units.Domain;
import kanger.units.Right;
import kanger.units.TValue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

// !@x a(x) -> b(x), @y b(y) -> c(y), @z c(z) -> d(z);

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 */
public class Analiser {


    private final User user;

    public Analiser(User user) {
        this.user = user;
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

//    public boolean checkSequence(Tree t, Tree u, boolean logging) throws RuntimeErrorException {
//
//        Set<Domain> coincidence = new HashSet<>();
//        List<Domain> sequence = new ArrayList<>();
//        if (t.getId() != u.getId()) {
//            sequence.addAll(t.getSequence());
//            sequence.addAll(u.getSequence());
//        } else {
//            sequence.addAll(t.getSequence());
//        }
//
//        user.getMind().getClosedDomains().reset();
//
//        // Контроль системных предикатов
//        for (int k = 0; k < sequence.size(); ++k) {
//            Domain a = sequence.get(k);
//            if (a.isSystem() && a.isCalculated() /*&& !a.isAntc()*/) {
//                a.setClosed();
//            }
//        }
//
//        // Основной цикл сравнения последовательности
//        for (int k = 0; k < sequence.size(); ++k) {
//            Domain a = sequence.get(k);
//
//            //TODO: Системные пока отключил
////            if ((!a.isClosed() || a.getRight().isQuery()) && a.isSystem() && a.isComplete()) {
////                int res = a.execSystem();
////                if (res == 1) {
////                    a.setClosed();
////
//////                    if (logging) {
//////                        mind.getLog().createTVar(LogMode.ANALIZER, "System predicate resolved : ");
//////                        for (Domain x : sequence) {
//////                            mind.getLog().createTVar(LogMode.ANALIZER, "\t" + x.toString());
//////                        }
//////                        mind.getLog().createTVar(LogMode.ANALIZER, "Сoincidence : ");
//////                        mind.getLog().createTVar(LogMode.ANALIZER, "\t" + a.toString());
//////                        mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
//////                    }
////
////                }
////            }
//
//            if (!a.isStored() || a.isClosed()) {
//                continue;
//            }
//
//            for (int j = k + 1; j < sequence.size(); ++j) {
//                Domain b = sequence.get(j);
//
//                if (!b.isStored() || b.isClosed()) {
//                    continue;
//                }
//
////                if (b.recalculate()) {
////                    b.setQueued(true);
////                }
//                if (a.getId() != b.getId()
//                        && a.getPredicate().getId() == b.getPredicate().getId()
//                        && a.isAntc() != b.isAntc()
////                        && !a.isPairedWith(b)
////                        && (!a.isDestFor() || a.getRight().isQuery() /*|| a.isUsed()*/)
////                        && (!b.isDestFor() || b.getRight().isQuery() /*|| b.isUsed()*/)
////                        && a.isQuery() != b.isQuery()
//                ) {
//                    boolean equals = true;
//                    for (int i = 0; i < a.getPredicate().getRange(); ++i) {
//                        Argument xa = a.getArguments().get(i);
//                        Argument xb = b.getArguments().get(i);
//                        if (!xa.isEmpty() && !xb.isEmpty()
////                                && !(a.isExcluded() && !b.isProduced())
////                                && !(b.isExcluded() && !a.isProduced())
////                                && !a.isDestFor(i, b)
////                                && !b.isDestFor(i, a)
//                                //&& (!a.isDestFor() || xa.getValue().getRight().isQuery() /*|| a.isUsed()*/)
////                                && (!b.isDestFor() || xb.getValue().getRight().isQuery() /*|| b.isUsed()*/)
//                                //                                    && !(xa.isTVariable() && xb.isTVariable() && xa.getTVariable().getId() == xb.getTVariable().getId())
//                                //                                    && (!xa.isDestFor(b) || a.getRight().isQuery() || b.getRight().isQuery())
//                                //                                    && (!xb.isDestFor(a) || b.getRight().isQuery() || a.getRight().isQuery())
//                                && xa.getValue().getId() == xb.getValue().getId()) {
//                        } else {
//                            equals = false;
//                        }
//                    }
//                    if (equals) {
//                        if (!a.isClosed()) {
//                            a.setClosed();
//                        }
//                        if (!b.isClosed()) {
//                            b.setClosed();
//                        }
//                    }
//                }
//
//            }
//        }
//
////         Контроль звершенности последовательности
//        boolean result = false;
//        for (int k = 0; k < sequence.size(); ++k) {
//            Domain a = sequence.get(k);
//            if (a.isClosed()) {
//                coincidence.add(a);
//                result = true;
//            }
//        }
//
//        if (result) {
//
////            t.setUsed();
////            u.setUsed();
//
//
//            if (logging) {
//                user.getMind().getLog().add(LogMode.ANALIZER, "Sequence resolved : ");
//                for (Domain x : sequence) {
//                    user.getMind().getLog().add(LogMode.ANALIZER, "\t" + x.toString());
//                }
//                if (!coincidence.isEmpty()) {
//                    user.getMind().getLog().add(LogMode.ANALIZER, "Сoincidence : ");
//                    for (Domain x : coincidence) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "\t" + x.toString());
//                    }
//                }
//            }
//
//            for (Domain d : sequence) {
//                if (d.isComplete() && !d.isClosed() && !(d.isExcluded() && d.isQuery()) && !d.isStored()) {
//                    result = false;
//                    if (user.getMind().getHypotesisStore().find(null, d.getPredicate(), d.getArguments()) == null) {
//                        user.getMind().getHypotesisStore().add(!d.isAntc(), d.isQuery(), d.getPredicate(), d.getArguments());
//                    }
//                    if (logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                        user.getMind().getLog().add(LogMode.ANALIZER, "NOT in condition: " + d.toString());
//                    }
//                }
//            }
//
//            for (Domain d : coincidence) {
//                for (Domain q : coincidence) {
//                    if (d.getId() != q.getId() && !d.isQuery() && !q.isQuery()) {
//                        for (int i = 0; i < d.getPredicate().getRange(); ++i) {
//                            if ((d.get(i).isTSet() && q.get(i).isTSet() && d.get(i).getT().getId() == q.get(i).getT().getId())
//                                    || d.isDestFor(i, q)
//                                    || q.isDestFor(i, d)) {
//                                result = false;
//                                if (logging) {
//                                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                                    user.getMind().getLog().add(LogMode.ANALIZER, "Blocked pair: " + d.toString() + " and " + q.toString());
//                                }
//                                break;
//                            }
//                        }
//                    }
//                }
//            }
//
//
//            if (logging) {
//                user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
//            }
//
//
//            if (result) {
//                t.setClosed(true);
//                u.setClosed(true);
//                collectResults(coincidence);
//            }
//
//
//        } else {
//            for (int k = 0; k < sequence.size(); ++k) {
//                Domain d = sequence.get(k);
//                if (d.isComplete()
//                        && !d.isClosed()
//                        && (d.isQuery() || !d.isStored())
//                        && (d.isProduced() || d.isExcluded())
//                        && (d.isProduced() || d.isStored())
//                        && user.getMind().getHypotesisStore().find(null, d.getPredicate(), d.getArguments()) == null) {
//                    user.getMind().getHypotesisStore().add(!d.isAntc(), d.isQuery(), d.getPredicate(), d.getArguments());
//                    if (logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
//                        user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
//                    }
//                }
//            }
//
//
////            collectHypotesis(t.getSequence(), logging);
////            collectHypotesis(u.getSequence(), logging);
//        }
//
//
//        return result;
//    }

//    private boolean collectHypotesis(List<Domain> sequence, boolean logging) {
//        boolean occurs = false;
//        boolean append = false;
////        for (Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
////            TODO: Тут коллизия какая-то. Пока не знаю как разрешить
////            if (d.isComplete() && d.isStored() && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
////                if ((!d.isStored() && !d.isExcluded()) && d.isQuery() && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
////                if (logging) {
////                    mind.getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
////                }
////                occurs = true;
////                mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());
////
////            }
////        }
//
////        if (logging && occurs) {
////            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
////        }
//
//        occurs = false;
//        List<Domain> coincidence = new ArrayList<>();
//        List<Domain> discrepancies = new ArrayList<>();
//        for (Domain d : sequence) {
//            if (d.isComplete() && !d.isClosed()) {
//                if (!d.isExcluded() && !d.isStored()) {
//                    append = true;
//                    if (!d.isSystem() && user.getMind().getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                        coincidence.add(d);
//                    }
//                } else {
//                    occurs = true;
//                    if ((d.isQuery() || d.isStored()) && d.isProduced() && !d.isExcluded()) {
//                        append = true;
//                        if (!d.isSystem() && user.getMind().getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
//                            discrepancies.add(d);
//                        }
//                    }
//                }
//            }
//        }
//
//
//        if (occurs) {
//            if (!coincidence.isEmpty()) {
//                for (Domain d : coincidence) {
//                    if (logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "Not in condition: " + d.toString());
//                    }
//                    user.getMind().getHypotesisStore().add(!d.isAntc(), false, d.getPredicate(), d.getArguments());
//                }
//                if (logging) {
//                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                }
//            } else if (!discrepancies.isEmpty()) {
//                for (Domain d : discrepancies) {
//                    if (logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
//                    }
//                    user.getMind().getHypotesisStore().add(!d.isAntc(), false, d.getPredicate(), d.getArguments());
//                }
//                if (logging) {
//                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                }
//            }
//        }
//
//        return append;
//    }

//    private boolean checkTree(List<TVariable> tvars, int tIndex, Queue<Tree> set, boolean logging) throws RuntimeErrorException {
//        boolean result = false;
//        if (tIndex >= tvars.size()) {
//
//            Set<Function> fs = new HashSet<>();
//            Set<Domain> sd = new HashSet<>();
////            SortedSet<HypotesisStore> hypotesis = new TreeSet<>();
//
//            user.getMind().getClosedDomains().reset();
//
//            for (Tree t : set) {
//                for (Tree x : set) {
//                    if (!x.isExcluded(t) && (!t.isClosed() || !x.isClosed())) {
//                        if (checkSequence(t, x, logging)) {
//                            result = true;
//                        }
//                    }
//                }
//            }
////            for (Tree t : set) {
////                for (Tree x : set) {
////TODO: Это исключение ветвления. Не очень понимаю зачем это. Убрал
////                    if (!x.isExcluded(t)) {
//
//            //TODO: Восстановить сбор гипотез
////            if (checkSequence(logging)) {
////                result = true;
////                collectResults();
//////                    }
//////                    }
////            }
//
////            for (Right r = mind.getRights().getRoot(); r != null; r = r.getNext()) {
////                for (Tree sequence : r.getTree()) {
////                    for (Domain d : sequence.getSequence()) {
////                        if (!d.isClosed() && !d.isExcluded()) {
////                            result = false;
////                            mind.getHypotesisStore().add(!d.isAntc(), false /*d.isQuery()*/, d.getPredicate(), d.getArguments());
////
//////                        if (showFalse) {
////                            if (logging) {
////                                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
////                                mind.getLog().add(LogMode.ANALIZER, "NOT in condition: " + d.toString());
////                            }
//////                        }
////                        }
////                    }
////
////                }
////            }
//
//
////            }
//
//            for (Tree t : set) {
//                for (Domain d : t.getSequence()) {
//                    fs.addAll(d.getFunctions());
//                    if (d.isSystem()) {
//                        sd.add(d);
//                    }
//                }
//            }
//
////            mind.getCalculated().reset();
////
////            for (Function f : fs) {
////                if (!f.isCalculable() || f.isComplete()) {
////                    f.clearResult();
////                    mind.getCalculator().calculate(f);
////                }
////            }
//
////            mind.getSubstituted().reset();
//
////            boolean occurs = false;
////            for (Domain d : sd) {
////                if (d.isUsed() /*&& !d.isAntc()*/) {
////                    occurs = true;
////                    result = true;
////                }
////                int res = d.execSystem();
////                if (res == 0) { //(res == 0 && !d.isAntc()) || (res == 1 && d.isAntc())) {
////                    result = d.isAntc();
////                    occurs = true;
////                } else if (res == 1) {
////                    //TODO: Срабатывает временами неверно
//////                    if (d.isQuery()) {
////                        result = !d.isAntc();
//////                    }
////                    occurs = true;
////                }
////            }
//
////            if (occurs) {
////                collectResults(!result, sd);
////            }
//
//
//        } else {
//            TVariable t = tvars.get(tIndex);
//            TValue v = t.rewind();
//            if (v != null) {
//                do {
//                    user.getMind().getTValues().set(t, v);
//                    if (checkTree(tvars, tIndex + 1, set, logging)) {
//                        result = true;
//                    }
//                } while ((v = t.next(v)) != null);
//            } else {
//                if (checkTree(tvars, tIndex + 1, set, logging)) {
//                    result = true;
//                }
//            }
//        }
//
//        return result;
//    }


    //    public boolean analiseTree(Tree t, boolean logging) throws RuntimeErrorException {
//        mind.getClosedDimains().reset();
//        mind.getSubstituted().reset();
//        mind.getCalculated().reset();
//        mind.getQueuedDomains().reset();
//        List<TVariable> vars = t.getTVariables(true);
//        return checkTree(t, vars, 0, logging);
//    }


    public boolean analise(Right right, boolean logging) throws IOException, ClassNotFoundException {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

        if (logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "============= ANALISER ====================");
        }

//        Queue<Tree> set = new LinkedList<>();
//        for (Right rx = user.getMind().getRights().getRoot(); rx != null; rx = rx.getNext()) {
//            set.addAll(rx.getTree());
//        }

//        Set<TVariable> tvars = new HashSet<>();
//        for (Tree t : set) {
//            tvars.addAll(t.getTVariables(true));
//
////                for(Function f: t.getFunctions()) {~
////                    f.clearResult();
////                }
//        }

//        for(Predicate p = mind.getPredicates().getRoot(); p != null; p = p.getNext()) {
//            if(p.checkSolves())
//                return true;
//        }
//        return false;

//        Set<Tree> query = new HashSet<>();
//        for (Right r = user.getMind().getRights().getRoot(); r != null; r = r.getNext()) {
//            if (r.isQuery()) {
//                query.addAll(r.getTree());
//            }
//        }

//        mind.getClosedDimains().reset();
//        mind.getQueuedDomains().reset();
//        user.getMind().getUsedTrees().reset();
//        user.getMind().getClosedTrees().reset();
//        user.getMind().getUsedDomains().clear();
        user.getMind().getSolutions().clear();
        user.getMind().getValues().clear();

//        if (checkDatabase(logging)) {
//            for (Tree t : set) {
//                mind.getDatabase().collectResults(t.getSequence());
//            }
//            result = true;
//        } else {

        result = checkDatabase(right, logging);

        //TODO: 2
        // Проверка на то что все ветки запроса закрылись
//        if (result && right != null) {
//            for (List<Domain> branch : right.getTree()) {
//                boolean success = false;
//                for (Domain d : branch) {
//                    if (d.isCalculated() /*|| !d.getArguments().getTVariables(true).isEmpty()*/) {
//                        success = true;
//                        break;
//                    } else if (user.getMind().getSolutions().containsPair(d)) {
//                        success = true;
//                        break;
//                    } else {
//                        for (long id : user.getMind().getRights().getDatabase(-1)) {
//                            Right r = user.getMind().getRights().get(id);
////                            if (r.getId() < user.getMind().getRights().getFirstId()) {
////                                break;
////                            } else {
//                                for (Cause c : r.getCauses()) {
//                                    if (c.getSrcId() == d.getId()) {
//                                        success = true;
//                                        break;
//                                    }
//                                }
////                            }
//                        }
//                    }
//
//                }
//                if (!success) {
//                    result = false;
//                    if (logging) {
//                        for (Domain d : branch) {
//                            user.getMind().getLog().add(LogMode.ANALIZER, "Unresolved: \t" + d.toString());
//                        }
//                    }
//                }
//            }
////            for (long id : user.getMind().getRights().getDatabase(-1)) {
////                Right r = user.getMind().getRights().get(id);
////                if (r.getId() < user.getMind().getRights().getFirstId()) {
////                    break;
////                } else {
////                    if (r.getDomain().isQuery()
////                            && !r.getDomain().isCalculated()
////                            && !r.isGenerated()
////                            && !user.getMind().getSolutions().contains(r)) {
////                        result = false;
////                        if(logging) {
////                            user.getMind().getLog().add(LogMode.ANALIZER, "Unresolved: \t" + r.toString());
////                        }
////                    }
////                }
////            }
//            if (!result && logging) {
//                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//            }
//        }

        if (!result) {

//            result = checkTree(new ArrayList<>(tvars), 0, set, logging);
            //todo только из базы?
//            Record stop = null;
//            if (user.getMind().getNext() != null) {
//                stop = user.getMind().getNext().getDatabase().getRoot();
//            }

            boolean occurs = false;
            for (long id : user.getMind().getRights().getDatabase(-1)) {
                Right r = user.getMind().getRights().get(id);
                if (!r.isDeleted()) {
                    if (r.getId() < user.getMind().getRights().getFirstId()) {
                        break;
                    }
                    Domain d = r.getDomain();
                    for (Argument a : d.getArguments()) {
                        if (!a.isEmpty() && a.getType() == ArgumentType.CVARIABLE && a.getValue().getId() > user.getMind().getTerms().getFirstId()) {
                            d = null;
                            break;
                        }
                    }
                    if (d != null && !d.isQuery()
                            && user.getMind().getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                        Hypotese h = user.getMind().getHypotesisStore().add(!d.isAntc(), d.isQuery(), d.getPredicate(), d.getArguments());
                        occurs = true;
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
                        }
                    }
                }
            }

            if (occurs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
            }


//            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
//                if (tree.getSequence().size() > 1) {
//                    List<Domain> pretendents = new ArrayList<>();
//                    for (Domain d : tree.getSequence()) {
//                        if (d.isStored()) {
//                            pretendents.reset();
//                            break;
//                        } else if (d.isComplete() && !d.isExcluded() && !d.isUsed()) {
//                            pretendents.add(d);
//                        }
//                    }
//                    if (pretendents.size() == 1 && user.getMind().getHypotesisStore().find(!pretendents.get(0).isAntc(), pretendents.get(0).getPredicate(), pretendents.get(0).getArguments()) == null) {
//                        user.getMind().getHypotesisStore().add(!pretendents.get(0).isAntc(), pretendents.get(0).isQuery(), pretendents.get(0).getPredicate(), pretendents.get(0).getArguments());
//                        if (logging) {
//                            user.getMind().getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + pretendents.get(0).toString());
//                            user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
//                        }
//                    }
//                }
//            }
//        } else {
//            if (!user.getMind().getValues().isEmpty()) {
//                user.getMind().getValues().normalize();
//            }
        }

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
//            countUsed = mind.getParentTrees().size();
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
//        } while (countClosed != mind.getClosedTrees().size() || countUsed != mind.getParentTrees().size());

//        }
        if (logging) {
            user.getMind().getLog().add(LogMode.TIMING, "* Analising time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
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

//    private Domain contains(Domain d, Set<Domain> set) {
//        for (Domain x : set) {
//            if (x.equalsBase(d)) {
//                return x;
//            }
//        }
//        return null;
//    }


//    public void collectResults(Iterable<Domain> sequence) {
//
//        Set<Domain> suc = new HashSet<>();
//        Set<Domain> ant = new HashSet<>();
//
//        for (Domain d : sequence) {
//            if (d.isClosed()) {
//
////                mind.getSolutions().add(d);
//                if (d.isQuery()) {
//                    for (TVariable tv : d.getTVariables(true)) {
//                        user.getMind().getValues().add(tv.getCurrent());
//                    }
//                }
//
////                if (d.isStored()) {
////                    for (Argument a : d.getArguments()) {
////                        if (a.isVSet()) {
////                            user.getMind().getValues().add(a.getV());
////                        }
////                    }
////                }
//
//                if (d.isAntc()) {
//                    ant.add(d);
//                } else {
//                    suc.add(d);
//                }
//            }
//        }
//
////        for (Domain d : sequence) {
////            if (d.isClosed() && d.isQuery() && !d.getRight().isQuery()) {
//////                if (d.isSystem()) {
////
//////                    mind.getSolutions().add(d);
////                for (TVariable tv : d.getTVariables(true)) {
////                    mind.getValues().add(tv, d);
////                }
//////                } else if (d.isAntc()) {
//////                    ant.add(d);
//////                } else {
//////                    suc.add(d);
//////                }
////            }
////        }
//
//        for (Domain d : ant) {
//            Domain q = contains(d, suc);
//            if (q == null) {
//                user.getMind().getSolutions().add(d);
////                for (TVariable tv : d.getTVariables(true)) {
////                    mind.getValues().add(tv, d);
////                }
//            } else if (!d.isQuery()) {
//                user.getMind().getSolutions().add(d);
////                for (TVariable tv : d.getTVariables(true)) {
////                    mind.getValues().add(tv, d);
////                }
//            } else if (!q.isQuery()) {
//                user.getMind().getSolutions().add(q);
////                for (TVariable tv : q.getTVariables(true)) {
////                    mind.getValues().add(tv, q);
////                }
//            } else {
//                user.getMind().getSolutions().add(d);
////                for (TVariable tv : d.getTVariables(true)) {
////                    mind.getValues().add(tv, d);
////                }
//                user.getMind().getSolutions().add(q);
////                for (TVariable tv : q.getTVariables(true)) {
////                    mind.getValues().add(tv, q);
////                }
//            }
//        }
//        for (Domain d : suc) {
//            Domain q = contains(d, ant);
//            if (q == null) {
//                user.getMind().getSolutions().add(d);
////                for (TVariable tv : d.getTVariables(true)) {
////                    mind.getValues().add(tv, d);
////                }
//            }
//        }
//
////        for (Domain d : suc) {
////            if (contains(d, ant)) {
////                if (!hypotesis) {
//////                    int sz = mind.getSolutions().size();
//////                    mind.getSolutions().createTVar(d);
////
//////                    if (sz != mind.getSolutions().size()) {
////                    for (TVariable tv : d.getTVariables(true)) {
////                        mind.getValues().add(tv, d);
////                    }
//////                    }
////                }
////            }
//////            else if (hypotesis) {
//////                mind.getHypotesisStore().createTVar(d.getPredicate(), d.getArguments());
//////            }
////        }
////        if (hypotesis) {
////            for (Domain d : suc) {
////                if (!contains(d, ant) /*&& !d.getRight().isQuery()*/) {
//////                    mind.getHypotesisStore().add(true, d.getPredicate(), d.getArguments());
////                }
////            }
////        }
//
//        //result = checkSequence(t, logging);
//
//    }

    //TODO: !index(qwerty); ?$x $y index(x), y : x;

    private boolean checkRight(Right p, Set<Right> orfans, boolean logging) throws IOException, ClassNotFoundException {
        boolean result = false;
        if (p.getDomain().isCalculated()) {

            boolean valid = p.getDomain().isQuery();
            if (!valid) {
                for (TValue v : p.getSolves()) {
                    if (v.getTVar().isQuery()) {
                        valid = true;
                        break;
                    }
                }
            }
            if (valid) {
//                    user.getMind().getResults().addSolve(p);

//                for (TValue v : p.getDomain().getArguments().getTValues(true)) {
//                    user.getMind().getValues().add(v);
//                }

//                user.getMind().getValues().addSystem(p.getSolves());
                user.getMind().getValues().add(p.getSolves());
            }

            if (logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "Calculated coincidence: ");
                user.getMind().getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
            }
            result = true;
        } else {
            for (long iq : user.getMind().getRights().getDatabase(p.getId())) {
                Right q = user.getMind().getRights().get(iq);
                if (//q.getId() < p.getId()                            &&
                        !q.isDeleted()
                                && p.getDomain().equalsBase(q.getDomain())
                                && p.getDomain().isAntc() != q.getDomain().isAntc()) {

//                    Set<Domain> sequence = new HashSet<>();

//                    for (Domain parent : p.getParents()) {
//                        parent.apply(p);
//                        parent.setClosed();
//                        sequence.add(parent);
//
////                        if(parent.isQuery()) {
////                            for (TVariable tv : parent.getTVariables(true)) {
////                                user.getMind().getValues().add(tv.getCurrent());
////                            }
////                        } else {
////                            user.getMind().getSolutions().add(p);
////                        }
//                    }
//
//                    for (Domain parent : q.getParents()) {
//                        parent.apply(q);
//                        parent.setClosed();
//                        sequence.add(parent);
//
////                        if(parent.isQuery()) {
////                            for (TVariable tv : parent.getTVariables(true)) {
////                                user.getMind().getValues().add(tv.getCurrent());
////                            }
////                        } else {
////                            user.getMind().getSolutions().add(p);
////                        }
//                    }

                    if (p.getDomain().isQuery() && p.getDomain().getArguments().getCVariables(true).isEmpty()) {
//                            user.getMind().getResults().addSolve(p, q);
                        user.getMind().getSolutions().add(q);
                        user.getMind().getValues().add(p.getSolves());
//                            for (Argument a : p.getDomain().getArguments()) {
//                                if (a.isVSet()) {
//                                    user.getMind().getValues().add(a.getV());
//                                }
//                            }
                    } else if (q.getDomain().isQuery() && q.getDomain().getArguments().getCVariables(true).isEmpty()) {
//                            user.getMind().getResults().addSolve(q, p);
                        user.getMind().getSolutions().add(p);
                        user.getMind().getValues().add(q.getSolves());
//                            System.out.println("!!!!!!--- " + q.getSolves());
//                            for (Argument a : q.getDomain().getArguments()) {
//                                if (a.isVSet()) {
//                                    user.getMind().getValues().add(a.getV());
//                                }
//                            }
                    }

//                        if (p.getDomain().isQuery() && !q.getDomain().isQuery()) {
//                            user.getMind().getSolutions().add(q);
//                        } else if (!p.getDomain().isQuery() && q.getDomain().isQuery()) {
//                            user.getMind().getSolutions().add(p);
////                        } else {
////                            user.getMind().getSolutions().add(q);
////                            user.getMind().getSolutions().add(p);
//                        }

                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "Database coincidence: ");
                        user.getMind().getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                        user.getMind().getLog().add(LogMode.ANALIZER, "\t" + q.toString());
                        user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
                    }
//                    collectResults(sequence);
                    result = true;
                }
            }

            if (!result && p.getDomain().isQuery() && !p.getDomain().isUsed()) {
                orfans.add(p);
            }
        }
        return result;
    }

    public boolean checkDatabase(Right right, boolean logging) throws IOException, ClassNotFoundException {

        boolean result = false;
        Set<Right> orfans = new HashSet<>();

//        if (right != null) {
//            result = checkRight(right, orfans, logging);
//        } else {
        for (long id : user.getMind().getRights().getDatabase(-1)) {
            Right p = user.getMind().getRights().get(id);
            if (!p.isDeleted() && checkRight(p, orfans, logging)) {
                result = true;
            }
        }
//        }

//        Set<Right> toDelete = new HashSet<>();
//        for(Right r : orfans) {
//            if(user.getMind().getSolutions().containsPair(r.getDomain())) {
//                toDelete.add(r);
//            }
//        }
//        orfans.removeAll(toDelete);

        // Контроль закрытия всех веток запроса
        if (!orfans.isEmpty()) {
            result = false;
            if (logging) {
                for (Right r : orfans) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Unresolved: \t" + r.getDomain().toString());
                }
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return result;
    }

}
