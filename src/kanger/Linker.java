
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Reactor;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.Cause;
import kanger.units.*;

import java.util.*;

/**
 * @author Dmitry G. Qusnetsov
 */
public class Linker {

    private final User user;

    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skipedPasses = 0;

    public Linker(User user) {
        this.user = user;
    }

    public void link(Right right, boolean logging) throws RuntimeErrorException {

//        user.getMind().getProducedDomains().reset();
        user.getMind().getExcludedDomains().clear();
        user.getMind().getUsedDomains().clear();
        user.getMind().getCalculatedDomains().clear();
//        user.getMind().getDomainCauses().reset();

//        for (Function f : user.getMind().getFunctions()) {
//            if (!f.isCalculable()) {
//                new Calculator(user).calculate(f, logging);
//            }
//        }
//
//        final Set<Domain> waiters = new HashSet<>();
//        final Map<Long, Set<Long>> links = new HashMap<>();
//        for (Right r : user.getMind().getRights()) {
//            for (List<Domain> tree : r.getTree()) {
//                if (tree.size() == 1) {
//                    if (!tree.get(0).getArguments().getTVariables(true).isEmpty()) {
//                        waiters.add(tree.get(0));
//                    } else {
//                        tree.get(0).setStored();
//                    }
//                }
//                for (Domain d : tree) {
//                    if (!links.containsKey(d.getPredicate().getId())) {
//                        links.put(d.getPredicate().getId(), new HashSet<>());
//                    }
//                    links.get(d.getPredicate().getId()).add(r.getId());
//                }
//            }
//        }

        long saveR;
        long saveT;
        long saveF;

        int passCounter = 0;
        List<ArgList> main = new ArrayList<>();
        List<ArgList> calculated = new ArrayList<>();
        List<Right> solves = new ArrayList<>();
        boolean result = false;

        solvedPasses = 0;
        dumpedPasses = 0;
        skipedPasses = 0;


        do {

            if (logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }

            saveR = user.getMind().getRights().getLastId();
            saveT = user.getMind().getTValues().getLastId();
            saveF = user.getMind().getFValues().getLastId();


            final Map<Right, Set<Cause>> causes = new HashMap<>();

//            SortedSet<Tree> treeSet = new TreeSet<>();
////            if (right == null) {
//            for (Tree tree : user.getMind().getTrees()) {
//                treeSet.add(tree);
//            }
//            } else {
//                Set<Right> rights = new HashSet<>();
//                for (Tree tree : right.getTree()) {
//                    for(Domain d : tree.getSequence()) {
//                        for(Tree t : d.getPredicate().getLinkedTrees()) {
//                            rights.add(t.getSequence().get(0).getRight());
//                        }
//                    }
//                }
//                for(Right r : rights) {
//                    treeSet.addAll(r.getTree());
//                }
//            }

            List<Right> rightList = new ArrayList<>();
            if (right != null) {
                rightList.add(right);
            } else {
                for (Right r : user.getMind().getRights()) {
                    rightList.add(r);
                }
            }

//            final SortedSet<TVariable> tvars = new TreeSet<>();
//            for (Right rr : user.getMind().getRights()) {
//                for (List<Domain> tree : rr.getTree()) {
//                    for (Domain d : tree) {
//                        tvars.addAll(d.getArguments().getTVariables(true));
//                    }
//                }
//            }

            for (Right r : user.getMind().getRights()) {

                user.getMind().getProducedDomains().clear();
                user.getMind().getDomainSolves().clear();
                user.getMind().getDomainCauses().clear();

                final SortedSet<TVariable> tvars = new TreeSet<>();
                for (Right rr : r.getNatives()) {
                    for (List<Domain> tree : r.getTree()) {
                        for (Domain d : tree) {
                            tvars.addAll(d.getArguments().getTVariables(true));
                        }
                    }
                }

//                final Set<Domain> waiters = new HashSet<>();
//                for (Tree tree : r.getTree()) {
//                    if (tree.getSequence().size() == 1) {
//                        if (!tree.getSequence().get(0).getArguments().getTVariables(true).isEmpty()) {
//                            waiters.add(tree.getSequence().get(0));
//                        } else {
//                            tree.getSequence().get(0).setStored();
//                        }
//                    }
//                }

//            user.getMind().getExcludedDomains().reset();
                //TODO: !! Надо думать надо полным обходом всех вариантов. Или это только сбор гипотез?


//            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
                for (List<Domain> tree : r.getTree()) {

                    final List<Domain> t = tree;
//                    SortedSet<TVariable> tvars = new TreeSet<>();
//                    for (Domain d : tree) {
//                        tvars.addAll(d.getArguments().getTVariables(true));
//                    }

                    rotateVariables(tvars, logging, new Reactor() {
                        @Override
                        public Object run(Object o) {
                            boolean result = false;
                            boolean logging = (boolean) o;

                            try {
                                if (linkDomains(t, causes, logging)) {
                                    result = true;
                                }
                                if (calcFunctions(t, causes, logging)) {
                                    result = true;
                                }

                                List<TValue> solve = new ArrayList<>();
                                for (TVariable t : tvars) {
                                    if (!t.isEmpty()) {
                                        solve.add(t.getCurrent());
                                    }
                                }

                                if (linkDatabase(t, causes, solve, logging)) {
                                    result = true;
                                }
                            } catch (RuntimeErrorException e) {
                                e.printStackTrace(System.err);
                                result = false;
                            }

                            return result;
                        }
                    });
                }

//                if(analizeProduces(main, calculated, solves)) {
//                    result = true;
//                }
                updateDatabase(logging);
            }

//            if(saveT != user.getMind().getTValues().getLastId()) {
//                long tag = user.getMind().getTValues().incTag();
//                Iterator<TValue> iterator = user.getMind().getTValues().iterator();
//                while (iterator.hasNext()) {
//                    TValue v = iterator.next();
//                    if (v.getId() >= saveT) {
//                        v.setTag(saveT);
//                    } else {
//                        break;
//                    }
//                }
//            }


        } while (saveR != user.getMind().getRights().getLastId()
                || saveT != user.getMind().getTValues().getLastId()
                || saveF != user.getMind().getFValues().getLastId()
        );

        if (logging) {
            user.getMind().getLog().add(LogMode.TIMING, String.format("* LINKER Solved passes: %03d", solvedPasses));
            user.getMind().getLog().add(LogMode.TIMING, String.format("* LINKER Dumped passes: %03d", dumpedPasses));
            user.getMind().getLog().add(LogMode.TIMING, String.format("* LINKER Skiped passes: %03d", skipedPasses));
        }


//        if(result) {
//            System.out.println(user.getMind().getQueryPass() + ": OK");
//            if(!solves.isEmpty()) {
//                for(Right r : solves) {
//                    System.out.println(r);
//                }
//            }
//            if (!main.isEmpty()) {
//                System.out.println("--- main");
//                for (ArgList row : main) {
//                    System.out.println(row);
//                }
//                System.out.println("---");
//            } else if (!calculated.isEmpty()) {
//                System.out.println("--- calculated");
//                for (ArgList row : calculated) {
//                    System.out.println(row);
//                }
//                System.out.println("---");
//            }
//        }

    }

    private boolean rotateVariables(SortedSet<TVariable> tvars, boolean logging, Reactor runnable) throws RuntimeErrorException {
        boolean result = false;
//        if (tvars == null) {
//            tvars = new TreeSet<>();
//            for (TVariable t : user.getMind().getTVars()) {
//                tvars.add(t);
//            }
//        }
        if (tvars.isEmpty()) {
            result = (boolean) runnable.run(logging);
        } else {
            TVariable t = tvars.last();
            Iterator<TValue> iterator = user.getMind().getTValues().iterator(t);
            if (iterator.hasNext()) {
                do {
                    TValue v = iterator.next();
                    v.linkExternal(user);
                    user.getMind().getTValues().set(t, v);
                    if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                        result = true;
                    }
                } while (iterator.hasNext());
            } else {
                if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean linkDomains(List<Domain> treeSlave, Map<Right, Set<Cause>> causes, boolean logging) throws RuntimeErrorException {

        boolean result = false;
        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (Right right : user.getMind().getRights().getLinks(slave.getPredicate())) {
                    for (List<Domain> treeMaster : right.getTree()) {
                        for (Domain master : treeMaster) {
                            if (master.getPredicate().getId() == slave.getPredicate().getId() && master.isAntc() != slave.isAntc()) {

                                TValue[] substMaster = new TValue[slave.getPredicate().getRange()];
                                TValue[] substSlave = new TValue[slave.getPredicate().getRange()];

                                user.getMind().getTValues().mark();
                                user.getMind().getFValues().mark();

                                boolean success = true;
                                boolean applied = false;

                                // Отсечение несовпадений по константам
                                for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
                                    if (!master.get(i).isTSet() && !slave.get(i).isTSet()
                                            && (master.get(i).isEmpty()
                                            || slave.get(i).isEmpty()
                                            || master.get(i).getValue().getId() != slave.get(i).getValue().getId())) {
                                        success = false;
                                        break;
                                    }
                                }

                                if (success) {
                                    for (int i = 0; i < slave.getPredicate().getRange(); ++i) {

                                        // Подстановка снизу вверх
                                        if (master.get(i).isTSet()
                                                && !slave.get(i).isEmpty()
                                                && master.getVarOrder(i) >= slave.getVarOrder(i)) {
                                            TValue s = user.getMind().getTValues().find(master.get(i).getT(), slave.get(i).getValue());
                                            if (s == null) {
                                                s = user.getMind().getTValues().add(master.get(i).getT(), slave.get(i).getValue());
                                                result = true;
                                            }
                                            substMaster[i] = s;
                                            applied = true;
                                        }

                                        // Подстановка сверху вниз
                                        if (slave.get(i).isTSet()
                                                && !master.get(i).isEmpty()
                                                && slave.getVarOrder(i) >= master.getVarOrder(i)) {
                                            TValue s = user.getMind().getTValues().find(slave.get(i).getT(), master.get(i).getValue());
                                            if (s == null) {
                                                s = user.getMind().getTValues().add(slave.get(i).getT(), master.get(i).getValue());
                                                result = true;
                                            }
                                            substSlave[i] = s;
                                            applied = true;
                                        }

                                        if (!applied) {
                                            if (master.get(i).isEmpty()
                                                    || slave.get(i).isEmpty()
                                                    || master.get(i).getValue().getId() != slave.get(i).getValue().getId()) {
                                                success = false;
                                                break;
                                            } else {
                                                substMaster[i] = null;
                                                substSlave[i] = null;
                                            }
                                        }
                                    }
                                }

                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        user.getMind().getTValues().commit();
                                        user.getMind().getFValues().commit();
                                    } else {
                                        ++dumpedPasses;
                                    }
                                    markExcluded(substMaster, master, slave, causes, logging);
                                    markExcluded(substSlave, slave, master, causes, logging);
                                } else {
                                    ++skipedPasses;

//                                    System.err.println("--- " + master);
//                                    System.err.println("--- " + slave);
//                                    System.err.println("--- ");

                                    user.getMind().getTValues().release();
                                    user.getMind().getFValues().release();
                                }

                            }
                        }
                    }
                }

            }
        }

        return result;
    }

    private boolean markExcluded(TValue[] subst, Domain master, Domain slave, Map<Right, Set<Cause>> causes, boolean logging) {
        Right r = null;
        boolean occurrs = false;
        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
            if (subst[i] != null) {
                boolean caused = false;
                Cause s = new Cause(i, master, slave);
                if (!subst[i].getCauses().contains(s)) {
                    subst[i].getCauses().add(s);
                    caused = true;
                }
                if (caused || !master.isExcluded(slave.getArguments())) {
                    r = subst[i].getTVar().getRight();
                    if (caused) {
                        if (!causes.containsKey(r)) {
                            causes.put(r, new HashSet<>());
                        }
                        causes.get(r).add(s);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "Closed: " + subst[i]);
                        }
                        occurrs = true;
                    }
                }
            }
        }
        if (r != null) {
            master.setExcluded(slave.getArguments());
            if (occurrs && logging) {
                user.getMind().pushDebugLevel();
                user.getMind().setDebugLevel(user.getMind().getDebugLevel() & ~(Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS));
                user.getMind().getLog().add(LogMode.ANALIZER, "From right: " + r); //master.getRight());
                user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + master);
                user.getMind().popDebugLevel();
                user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + slave);
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return r != null;
    }

    private boolean linkDatabase(List<Domain> tree, Map<Right, Set<Cause>> causes, List<TValue> solve, boolean logging) throws RuntimeErrorException {

        boolean result = false;
        boolean occurs = false;

        long tag = user.getMind().getTValues().incTag();

        if (checkSystem(tree, logging)) {


            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidates = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();
            Set<Domain> stored = new HashSet<>();

            for (Domain d : tree) {

                for (Domain master : user.getMind().getDomains().getWaiters()) {
                    if (master.getPredicate().getId() == d.getPredicate().getId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                        boolean success = true;
                        for (int i = 0; i < d.getPredicate().getRange(); ++i) {
                            if (master.get(i).isTSet() && master.getVarOrder(i) >= d.getVarOrder(i)) {
                            } else if (master.get(i).isEmpty()
                                    || master.get(i).getValue().getId() != d.get(i).getValue().getId()) {
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
                if (d.isCalculated()) {
                    calculated.add(d);
                } else if (d.isSystem() || !d.isComplete()) {
                    excluded.clear();
                    candidates.clear();
                    break;
                } else if (d.isExcluded()) {
                    excluded.add(d);
                } else {
                    candidates.add(d);
                }
                if (d.isStored()) {
                    stored.add(d);
                }
            }

            if (candidates.size() == 1) {
                Domain d = candidates.toArray(new Domain[]{})[0];
//                d.setCauses(causes.get(d.getRight()));
                occurs = true;
                if (!d.isStored()) {
                    result = true;
                    d.setProduced();
                    d.setTag(tag);
                    d.setCauses(causes.get(d.getRight()));
                    d.setSolves(solve);
                    if (logging) {
                        user.getMind().getLog().add(LogMode.STORAGE, "DB assumed record: " + d);
                        logCauses(LogMode.STORAGE, d);
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced();
                        d.setTag(tag = user.getMind().getTValues().incTag());
                        d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.STORAGE, "DB assumed record (x): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
                //TODO: Сомнительно, но вроде работает с ?$x $y index(qwerty) -> index(x), y : x;
            } else if (!calculated.isEmpty() && candidates.isEmpty() /*&& tree.size() - excluded.size() == calculated.size()*/) {
                occurs = true;
                for (Domain d : calculated) {
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced();
                        d.setTag(tag = user.getMind().getTValues().incTag());
                        d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.STORAGE, "DB assumed record (c): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }

            if (!occurs && !assumed.isEmpty() && tree.size() > 1) {
                candidates.clear();
                excluded.clear();
                for (Domain d : tree) {
                    if (d.isComplete() && !d.isCalculated() && !d.isSystem() && !assumed.contains(d)) {
                        if (!d.isExcluded()) {
                            candidates.add(d);
                        } else {
                            excluded.add(d);
                        }
                    }
                }
                if (candidates.size() == 1 && !excluded.isEmpty()) {
                    Domain d = candidates.toArray(new Domain[]{})[0];
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced();
                        d.setTag(tag);
                        d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.STORAGE, "DB assumed record (a): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }
            if (result) {
                if (logging) {
                    user.getMind().getLog().add(LogMode.STORAGE, "-------------------------------------------");
                }
            }

        }

        return result;
    }

    private void logCauses(LogMode mode, Domain d) {
        boolean rightShowed = false;
        if (d.getCauses() != null) {
            for (Cause c : d.getCauses()) {
                if (!rightShowed) {
                    user.getMind().getLog().add(mode, "\tFrom right: " + c.getDst().getRight());
                    rightShowed = true;
                }
                user.getMind().getLog().add(mode, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
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
//        if(!user.getMind().getProducedDomains().isEmpty()) {
//            for (Map.Entry<Domain, List<ArgList>> master : user.getMind().getProducedDomains().entrySet()) {
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
//                        for (Right right : user.getMind().getRights().getDatabase()) {
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
//            for(Right master : user.getMind().getRights().getDatabase()) {
//                for (Right right : user.getMind().getRights().getDatabase(master.getId())) {
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

    private boolean updateDatabase(boolean logging) throws RuntimeErrorException {
        boolean result = false;
        for (Map.Entry<Domain, List<ArgList>> e : user.getMind().getProducedDomains().entrySet()) {
            Domain d = e.getKey();
            for (ArgList args : e.getValue()) {
                result = true;

                for (int i = 0; i < args.size(); /*d.getPredicate().getRange();*/ ++i) {
                    if (d.getArguments().get(i).isTSet()) {
                        if (d.getArguments().get(i).getT().find(args.get(i).getValue()) != null) {
                            d.getArguments().get(i).getT().setValue(args.get(i).getValue());
                        }
                    } else if (d.getArguments().get(i).isFSet()) {
                        //TODO: Добавить обработку функций
                    }
                }

                Right x;
//                if (d.getArguments().getTVariables(true).isEmpty()) {
//                    x = d.setStored();
//                    if (logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + d);
//                    }
//                } else {
                x = d.createStored();
                if (logging) {
                    user.getMind().getLog().add(LogMode.STORAGE, "DB add record: " + d);
                }
//                }

                if (d.isCalculated()) {
                    x.getDomain().setCalculated();
                }
                if (d.getCauses() != null) {
                    x.getCauses().clear();
                    x.getCauses().addAll(d.getCauses());
                }
                if (d.getSolves() != null) {
                    x.getSolves().clear();
                    x.getSolves().addAll(d.getSolves());
                }

            }
        }

        if (result && logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }


    public boolean calcFunctions(List<Domain> master, Map<Right, Set<Cause>> causes, boolean logging) throws RuntimeErrorException {
        boolean result = false;

        if (checkSystem(master, logging)) {
            for (Domain d : master) {
                for (Function f : d.getArguments().getFunctions()) {
                    if (f.isCalculable() && f.isEmpty()) {
                        if (new Calculator(user).calculate(f, logging)) {
                            result = true;

                        }
                    }
                }
            }
        }

        if (result && logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }

    public boolean checkSystem(List<Domain> tree, boolean logging) throws RuntimeErrorException {
        boolean block = false;
        for (Domain d : tree) {
            if (d.isSystem()) {

//                d.pushValues();

                List<TValue> list = new ArrayList<>();
                for (TVariable t : d.getArguments().getTVariables(true)) {
                    list.add(t.getCurrent());
                }

                int res = d.execSystem();
                for (Argument a : d.getArguments()) {
                    if (a.isEmpty()) {
                        res = -2;
                        break;
                    }
                }

                if (res == 0) {
                    if (d.isAntc()) {
                        d.setCalculated();
                    } else {
                        block = true;
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setCalculated();
                    } else {
                        block = true;
                    }
                }
                if (block && logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
                }
//                d.popValues();

                List<TVariable> ts = d.getArguments().getTVariables(true);
                for (int i = 0; i < ts.size(); ++i) {
                    if (list.get(i) != null) {
                        ts.get(i).setCurrent(list.get(i));
                    }
                }
            }
        }
        return !block;
    }

}

//TODO Убрать Record, перевести функционал на Right
//TODO Query с параметрами
//TODO Множества {} SET
//TODO Запрсы +(insert), -(delete), =(update)
//TODO счетчик ссылок для сборки мусор
//TODO time для каждой записи в базе
//TODO id для каждой зависи в базе??
