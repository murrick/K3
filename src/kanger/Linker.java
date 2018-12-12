
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.exception.ParametersIncompleteException;
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

    public void link(boolean logging) {
        link(null, logging);
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
                if (!tree.getSequence().get(0).getTVariables(true).isEmpty()) {
                    waiters.add(tree.getSequence().get(0));
                } else {
                    tree.getSequence().get(0).setStored();
                }
            }
        }


        Record saveR;
        TValue saveT;
        FValue saveF;

        do {

            Map<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> causesMap = new HashMap<>();

            saveR = user.getMind().getDatabase().getRoot();
            saveT = user.getMind().getTValues().getRoot();
            saveF = user.getMind().getFValues().getRoot();

            user.getMind().getProducedDomains().clear();

            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {

                final Tree t = tree;
                SortedSet<TVariable> tvars = new TreeSet<>();
                tvars.addAll(tree.getTVariables(true));


                rotateVariables(tvars, logging, new IRunnable() {
                    @Override
                    public Object run(Object o) {
                        boolean result = false;
                        boolean logging = (boolean) o;

                        user.getMind().getUsedDomains().clear();

                        if (linkDomains(t, logging)) {
                            result = true;
                        }
                        if (calcFunctions(t, logging)) {
                            result = true;
                        }

                        if (linkDatabase(t, waiters, causesMap, logging)) {
                            result = true;
                        }
                        return result;
                    }
                });
            }

            updateDatabase(causesMap, logging);


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

    private boolean linkDomains(Tree treeSlave, boolean logging) {

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
                                markExcluded(substMaster, master, slave, logging);
                                markExcluded(substSlave, slave, master, logging);
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

    private boolean markExcluded(TValue[] subst, Domain master, Domain slave, boolean logging) {
//        boolean applied = false;
        Right r = null;
        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
            if (subst[i] != null && (subst[i].addSolve(i, master, slave) || !master.isExcluded(slave.getArguments()))) {
                r = subst[i].getTVar().getRight();
//                applied = true;
                if (logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Closed: " + subst[i]);
                }

            }
        }
        if (r != null) {
            master.setExcluded(slave.getArguments());
            if (logging) {
                user.getMind().pushDebugLevel();
                user.getMind().setDebugLevel(user.getMind().getDebugLevel() & ~(Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS));
                user.getMind().getLog().add(LogMode.ANALIZER, "From right: " + master.getRight());
                user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + master);
                user.getMind().popDebugLevel();
                user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + slave);
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return r != null;
    }

    private void updateCauses(Domain d, Map<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> causesMap) {
        System.out.println("STOR: " + d);
        for (TValue v : d.getTValues(true)) {
            System.out.println("\t---- Right:" + v.getTVar().getRight().toString().replaceAll("\n", " "));
            for (TValue.Solve s : v.getSolves()) {
                //Record r = user.getMind().getDatabase().find(s.getSrc());
                //if (r != null) {
                boolean found = false;
                for (Argument a : d.getArguments()) {
                    if (!a.isEmpty() && a.getValue().getId() == v.getValue().getId()) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    System.out.println("\t\t" + s.getSrc() + " -> " + s.getDst());
                }
                //}
            }
        }
        System.out.println("--------------------------------------------");

        Map<Right, Set<Record>> causes = null;
        for (Map.Entry<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> e : causesMap.entrySet()) {
            if (e.getKey().containsKey(d) && d.isEqualsArguments(e.getKey().get(d))) {
                causes = e.getValue();
                break;
            }
        }
        if (causes == null) {
            try {
                causes = new HashMap<>();
                Map<Domain, List<Argument>> key = new HashMap<>();
                key.put(d, d.convertArguments());
                causesMap.put(key, causes);
            } catch (ParametersIncompleteException e) {
                e.printStackTrace();
            }
        }

        for (TValue v : d.getTValues(true)) {
            Set<Record> set;
            if (!causes.containsKey(v.getTVar().getRight())) {
                set = new HashSet<>();
                causes.put(v.getTVar().getRight(), set);
            } else {
                set = causes.get(v.getTVar().getRight());
            }
            for (TValue.Solve s : v.getSolves()) {
                Record r = user.getMind().getDatabase().find(s.getSrc());
                if (r != null) {
                    boolean found = false;
                    for (Argument a : d.getArguments()) {
                        if (!a.isEmpty() && a.getValue().getId() == v.getValue().getId()) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        set.add(r);
                    }
                } else {
                    System.out.println("!!!!!!!!!-------------" + s.getSrc());
                }
            }
        }
    }

    private boolean linkDatabase(Tree tree, Set<Domain> waiters, Map<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> causesMap, boolean logging) {

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
                occurs = true;
                if (!d.isStored()) {
                    result = true;
                    d.setProduced(user.getMind().getDatabase().getTag());
                    updateCauses(d, causesMap);
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record: " + d);
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        updateCauses(d, causesMap);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (x): " + d);
                        }
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                occurs = true;
                for (Domain d : calculated) {
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        updateCauses(d, causesMap);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (c): " + d);
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
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        updateCauses(d, causesMap);
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (a): " + d);
                        }
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

        return result;
    }

    private boolean updateDatabase(Map<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> causesMap, boolean logging) {
        boolean result = false;
        for (Map.Entry<Domain, Map<Integer, Set<List<Argument>>>> e : user.getMind().getProducedDomains().entrySet()) {
            Domain d = e.getKey();
            for (Map.Entry<Integer, Set<List<Argument>>> tags : e.getValue().entrySet()) {
                for (List<Argument> args : tags.getValue()) {
                    result = true;
                    d.apply(args);
                    Record x;
                    if (d.getTVariables(true).isEmpty()) {
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

                    Map<Right, Set<Record>> map = null;
                    for (Map.Entry<Map<Domain, List<Argument>>, Map<Right, Set<Record>>> z : causesMap.entrySet()) {
                        if (z.getKey().containsKey(d) && d.isEqualsArguments(z.getKey().get(d))) {
                            map = z.getValue();
                            break;
                        }
                    }

                    if (map != null) {
                        x.getCauses().clear();

                        for (Map.Entry<Right, Set<Record>> z : map.entrySet()) {
                            if (!x.getCauses().containsKey(z.getKey())) {
                                x.getCauses().put(z.getKey(), new HashSet<>());
                            }
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + z.getKey().toString().replaceAll("(.*)\n(.*)", " "));
                            }
                            for (Record r : z.getValue()) {
                                if (d.isIntersected(r.getDomain())) {
                                    x.getCauses().get(z.getKey()).add(r);
                                    if (logging) {
                                        user.getMind().getLog().add(LogMode.ANALIZER, "\t\t Using: " + r.getDomain().toString());
                                    }
                                }
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


    public boolean calcFunctions(Tree master, boolean logging) {
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


