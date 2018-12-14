
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

        do {

            saveR = user.getMind().getDatabase().getRoot();
            saveT = user.getMind().getTValues().getRoot();
            saveF = user.getMind().getFValues().getRoot();

            user.getMind().getProducedDomains().clear();

            for (Right rt = user.getMind().getRights().getRoot(); rt != null; rt = rt.getNext()) {

                final Right r = rt;
                SortedSet<TVariable> tvars = new TreeSet<>();
                tvars.addAll(rt.getTVariables(true));


                rotateVariables(tvars, logging, new IRunnable() {
                        @Override
                        public Object run(Object o) {
                            boolean result = false;
                            boolean logging = (boolean) o;
                            final Set<Cause> causes = new HashSet<>();
                            
                            for (Tree t : r.getTree()) {

                                if (linkDomains(t, causes, logging)) {
                                    result = true;
                                }
                                if (calcFunctions(t, causes, logging)) {
                                    result = true;
                                }

                                if (linkDatabase(t, waiters, causes, logging)) {
                                    result = true;
                                }
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

    private boolean linkDomains(Tree treeSlave, Set<Cause> causes, boolean logging) {

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

    private boolean markExcluded(TValue[] subst, Domain master, Domain slave, Set<Cause> causes, boolean logging) {
        Right r = null;
        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
            if (subst[i] != null && (subst[i].addCause(i, master, slave) || !master.isExcluded(slave.getArguments()))) {
                r = subst[i].getTVar().getRight();
                causes.add(subst[i].getCause(i, master, slave));
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
                user.getMind().getLog().add(LogMode.ANALIZER, "From right: " + r); //master.getRight());
                user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + master);
                user.getMind().popDebugLevel();
                user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + slave);
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return r != null;
    }

    private void updateCauses(Domain d) {
        System.out.println("STOR: " + d);
        for (TValue v : d.getArguments().getTValues(true)) {
            System.out.println("\t---- Right:" + v.getTVar().getRight().toString().replaceAll("\n", " "));
            for (Cause s : v.getCauses()) {
                System.out.println("\t\t" + s.getSrc() + " -> " + s.getDst());
            }
        }
        System.out.println("--------------------------------------------");

    }

    private boolean linkDatabase(Tree tree, Set<Domain> waiters, Set<Cause> causes, boolean logging) {

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
                d.addCauses(causes);
                occurs = true;
                if (!d.isStored()) {
                    result = true;
                    d.setProduced(user.getMind().getDatabase().getTag());
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record: " + d);
                        boolean rightShowed = false;
                        for (Cause c : d.getCauses()) {
                            if (!rightShowed) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + c.getDst().getRight());
                                rightShowed = true;
                            }
                            user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
                        }
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
                    d.addCauses(causes);
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (x): " + d);
                            boolean rightShowed = false;
                            for (Cause c : d.getCauses()) {
                                if (!rightShowed) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + c.getDst().getRight());
                                    rightShowed = true;
                                }
                                user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
                            }
                        }
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                occurs = true;
                for (Domain d : calculated) {
                    d.addCauses(causes);
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (c): " + d);
                            boolean rightShowed = false;
                            for (Cause c : d.getCauses()) {
                                if (!rightShowed) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + c.getDst().getRight());
                                    rightShowed = true;
                                }
                                user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
                            }
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
                    d.addCauses(causes);
                    if (!d.isStored()) {
                        result = true;
                        d.setProduced(user.getMind().getDatabase().getTag());
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB assumed record (a): " + d);
                            boolean rightShowed = false;
                            for (Cause c : d.getCauses()) {
                                if (!rightShowed) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + c.getDst().getRight());
                                    rightShowed = true;
                                }
                                user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
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

        }

        return result;
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
                    d.apply(args);
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
                    x.getCauses().addAll(d.getCauses());

//                    boolean vxr = false;
//                    for(TValue v : d.getTValues(true)) {
//                        for(Cause c : v.getCauses()) {
//                            if(c.getSrc().getArguments().equalsBase(c.getArguments()) && !isRecurse(c, null)) {
//                                x.getCauses().add(c);
//                                if(logging) {
//                                    if(!vxr) {
//                                        user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom right: " + v.getTVar().getRight());
//                                        vxr = true;
//                                    }
//                                    user.getMind().getLog().add(LogMode.ANALIZER, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
//                                }
//                            }
//                        }
//                    }

                }
            }
        }

        if (result && logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }


    public boolean calcFunctions(Tree master, Set<Cause> causes, boolean logging) {
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


