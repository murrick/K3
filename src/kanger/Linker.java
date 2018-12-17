
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.interfaces.IRunnable;
import kanger.primitives.*;

import java.util.*;

/**
 * @author Dmitry G. Qusnetsov
 */
public class Linker {

    private final User user;

    public Linker(User user) {
        this.user = user;
    }

    public void link(Right right, boolean logging) {

        user.getMind().getProducedDomains().clear();
        user.getMind().getUsedDomains().clear();
        user.getMind().getCalculatedDomains().clear();

        user.getMind().getClosedTrees().clear();


        for (Function f = user.getMind().getFunctions().getRoot(); f != null; f = f.getNext()) {
            if (!f.isCalculable()) {
                new Calculator(user).calculate(f, logging);
            }
        }

        final Set<Domain> waiters = new HashSet<>();
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (tree.getSequence().size() == 1) {
                if (!tree.getSequence().get(0).getArguments().getTVariables(true).isEmpty()) {
                    waiters.add(tree.getSequence().get(0));
                } else {
                    tree.getSequence().get(0).setStored();
                }
            }
        }


        Record saveR;
        TValue saveT;
        FValue saveF;

        int passCounter = 0;
        do {

            if (logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }

            saveR = user.getMind().getDatabase().getRoot();
            saveT = user.getMind().getTValues().getRoot();
            saveF = user.getMind().getFValues().getRoot();

            final Map<Right, Set<Cause>> causes = new HashMap<>();

            SortedSet<Tree> treeSet = new TreeSet<>();
            if (right == null) {
                for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
                    treeSet.add(tree);
                }
            } else {
                Set<Right> rights = new HashSet<>();
                for (Tree tree : right.getTree()) {
                    for(Domain d : tree.getSequence()) {
                        for(Tree t : d.getPredicate().getLinkedTrees()) {
                            rights.add(t.getSequence().get(0).getRight());
                        }
                    }
                }
                for(Right r : rights) {
                    treeSet.addAll(r.getTree());
                }
            }


            user.getMind().getProducedDomains().clear();
            //TODO: !! Надо думать надо полным обходом всех вариантов. Или это только сбор гипотез?

//            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            for (Tree tree : treeSet) {

                final Tree t = tree;
                SortedSet<TVariable> tvars = new TreeSet<>();
                tvars.addAll(t.getTVariables(true));



                rotateVariables(tvars, logging, new IRunnable() {
                    @Override
                    public Object run(Object o) {
                        boolean result = false;
                        boolean logging = (boolean) o;


                        if (linkDomains(t, causes, logging)) {
                            result = true;
                        }
                        if (calcFunctions(t, causes, logging)) {
                            result = true;
                        }

                        if (linkDatabase(t, waiters, causes, logging)) {
                            result = true;
                        }

                        return result;
                    }
                });

            }

            updateDatabase(logging);


        } while (saveR != user.getMind().getDatabase().getRoot()
                || saveT != user.getMind().getTValues().getRoot()
                || saveF != user.getMind().getFValues().getRoot()
        );
    }

    private boolean rotateVariables(SortedSet<TVariable> tvars, boolean logging, IRunnable runnable) {
        boolean result = false;
        if (tvars == null) {
            tvars = new TreeSet<>();
            for (TVariable t = user.getMind().getTVars().getRoot(); t != null; t = t.getNext()) {
                tvars.add(t);
            }
        }
        if (tvars.isEmpty()) {

            result = (boolean) runnable.run(logging);

        } else {
            TVariable t = tvars.last();
            TValue v = t.rewind();
            if (v != null) {
                do {
                    user.getMind().getTValues().set(t, v);
                    if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);

            } else {
                if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean linkDomains(Tree treeSlave, Map<Right, Set<Cause>> causes, boolean logging) {

        boolean result = false;
        if (treeSlave.getSequence().size() == 1) {
            for (Domain slave : treeSlave.getSequence()) {
                for (Tree treeMaster : slave.getPredicate().getLinkedTrees()) {
                    for (Domain master : treeMaster.getSequence()) {
                        if (master.getPredicate().getId() == slave.getPredicate().getId() && master.isAntc() != slave.isAntc()) {

                            TValue[] substMaster = new TValue[slave.getPredicate().getRange()];
                            TValue[] substSlave = new TValue[slave.getPredicate().getRange()];

                            user.getMind().getTValues().mark();
                            user.getMind().getFValues().mark();

                            boolean success = true;
                            boolean applied = false;

                            for (int i = 0; i < slave.getPredicate().getRange(); ++i) {

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
                            if (success) {
                                user.getMind().getTValues().commit();
                                user.getMind().getFValues().commit();
                                markExcluded(substMaster, master, slave, causes, logging);
                                markExcluded(substSlave, slave, master, causes, logging);
                            } else {
                                user.getMind().getTValues().release();
                                user.getMind().getFValues().release();
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

    private boolean linkDatabase(Tree tree, Set<Domain> waiters, Map<Right, Set<Cause>> causes, boolean logging) {

        boolean result = false;
        boolean occurs = false;
        if (checkSystem(tree, logging)) {

            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidates = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();
            Set<Domain> stored = new HashSet<>();

            for (Domain d : tree.getSequence()) {
                for (Domain master : waiters) {
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

            for (Domain d : tree.getSequence()) {
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
//                d.addCauses(causes.get(d.getRight()));
                occurs = true;
                if (!d.isStored()) {
                    result = true;
                    d.setProduced(user.getMind().getDatabase().getTag());
                    d.addCauses(causes.get(d.getRight()));
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record: " + d);
                        logCauses(d);
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
//                    d.addCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        d.addCauses(causes.get(d.getRight()));
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (x): " + d);
                            logCauses(d);
                        }
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                occurs = true;
                for (Domain d : calculated) {
//                    d.addCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        d.addCauses(causes.get(d.getRight()));
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (c): " + d);
                            logCauses(d);
                        }
                    }
                }
            }

            if (!occurs && !assumed.isEmpty() && tree.getSequence().size() > 1) {
                candidates.clear();
                excluded.clear();
                for (Domain d : tree.getSequence()) {
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
//                    d.addCauses(causes.get(d.getRight()));
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        d.addCauses(causes.get(d.getRight()));
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (a): " + d);
                            logCauses(d);
                        }
                    }
                }
            }
            if (result) {
                user.getMind().getDatabase().incTag();
                if (logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                }
            }

        }

        return result;
    }

    private void logCauses(Domain d) {
        boolean rightShowed = false;
        if (d.getCauses() != null) {
            for (Cause c : d.getCauses()) {
                if (!rightShowed) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + c.getDst().getRight());
                    rightShowed = true;
                }
                user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
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
    private boolean updateDatabase(boolean logging) {
        boolean result = false;
        for (Map.Entry<Domain, Map<Integer, Set<ArgList>>> e : user.getMind().getProducedDomains().entrySet()) {
            Domain d = e.getKey();
            for (Map.Entry<Integer, Set<ArgList>> tags : e.getValue().entrySet()) {
                for (ArgList args : tags.getValue()) {
                    result = true;

                    for (int i = 0; i < d.getPredicate().getRange(); ++i) {
                        if (d.getArguments().get(i).isTSet()) {
                            if (d.getArguments().get(i).getT().find(args.get(i).getValue()) != null) {
                                d.getArguments().get(i).getT().setValue(args.get(i).getValue());
                            }
                        } else if (d.getArguments().get(i).isFSet()) {
                            //TODO: Добавить обработку функций
                        }
                    }

                    Record x;
                    if (d.getArguments().getTVariables(true).isEmpty()) {
                        x = d.setStored();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + d);
                        }
                    } else {
                        x = d.createStored();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + d);
                        }
                    }
                    if (d.isCalculated()) {
                        x.getDomain().setCalculated();
                    }
                    x.setTag(tags.getKey());
                    if (d.getCauses() != null) {
                        x.getCauses().clear();
                        x.getCauses().addAll(d.getCauses());
                    }
                }
            }
        }

        if (result && logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }


    public boolean calcFunctions(Tree master, Map<Right, Set<Cause>> causes, boolean logging) {
        boolean result = false;

        if (!master.getFunctions().isEmpty()) {
            if (checkSystem(master, logging)) {
                for (Domain d : master.getSequence()) {
                    for (Function f : d.getFunctions()) {
                        if (f.isCalculable() && f.isEmpty()) {
                            if (new Calculator(user).calculate(f, logging)) {
                                result = true;

                            }
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

    public boolean checkSystem(Tree tree, boolean logging) {
        boolean block = false;
        for (Domain d : tree.getSequence()) {
            if (d.isSystem()) {

                d.pushValues();
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
                d.popValues();

            }
        }
        return !block;
    }

}


