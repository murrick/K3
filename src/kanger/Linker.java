
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.interfaces.IRunnable;
import kanger.primitives.*;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
            if (tree.getSequence().size() == 1 && !tree.getSequence().get(0).getTVariables(true).isEmpty()) {
                waiters.add(tree.getSequence().get(0));
            }
        }


        Record saveR;
        TValue saveT;
        FValue saveF;

        user.getMind().getClosedTrees().clear();

        do {

            saveR = user.getMind().getDatabase().getRoot();
            saveT = user.getMind().getTValues().getRoot();
            saveF = user.getMind().getFValues().getRoot();


            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {

                final Tree t = tree;
                SortedSet<TVariable> tvars = new TreeSet<>();
                tvars.addAll(tree.getTVariables(true));

                rotateVariables(tvars, logging, new IRunnable() {
                    @Override
                    public Object run(Object o) {
                        boolean result = false;
                        boolean logging = (boolean) o;


                        if (linkDomains(t, logging)) {
                            result = true;
                        }
                        if (calcFunctions(t, logging)) {
                            result = true;
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                            }
                        }

                        if (updateDatabase(t, waiters, logging)) {
                            result = true;
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                            }
                        }


                        return result;
                    }
                });
            }


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
        boolean applied = false;
        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
            if (subst[i] != null && (subst[i].addSolve(i, master, slave) || !master.isExcluded(slave.getArguments()))) {
                applied = true;
                if (logging) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Closed: " + subst[i]);
                }

            }
        }
        if (applied) {
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
        return applied;
    }

    private boolean updateDatabase(Tree tree, Set<Domain> waiters, boolean logging) {

        boolean result = false;
        boolean occurrs = false;
        if (checkSystem(tree, logging)) {

            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidades = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();

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
                    candidades.clear();
                    break;
                } else if (d.isExcluded()) {
                    excluded.add(d);
                } else {
                    candidades.add(d);
                }
            }

            if (candidades.size() == 1) {
                Domain d = candidades.toArray(new Domain[]{})[0];
                occurrs = true;
                if (!d.isStored()) {
                    result = true;
                    if (excluded.isEmpty() && !d.isCalculated() && d.getTVariables(true).isEmpty() /*|| d.getRight().isQuery())*/) {
                        Record x = d.setStored();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + x);
                        }
                    } else {
                        Record x = d.createStored();
                        if (d.isCalculated()) {
                            x.getDomain().setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                        }
                    }
                }
            } else if (!excluded.isEmpty() && candidades.isEmpty()) {
                occurrs = true;
                for (Domain d : excluded) {
                    if (!d.isStored()) {
                        result = true;
                        Record x = d.createStored();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record (x): " + x);
                        }
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                occurrs = true;
                for (Domain d : calculated) {
                    if (!d.isStored()) {
                        result = true;
                        Record x = d.createStored();
                        if (d.isCalculated()) {
                            x.getDomain().setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record (c): " + x);
                        }
                    }
                }
            }


            if (!occurrs && !assumed.isEmpty() && tree.getSequence().size() > 1) {
                candidades.clear();
                excluded.clear();
                for (Domain d : tree.getSequence()) {
                    if (d.isComplete() && !d.isCalculated() && !d.isSystem() && !assumed.contains(d)) {
                        if (!d.isExcluded()) {
                            candidades.add(d);
                        } else {
                            excluded.add(d);
                        }
                    }
                }
                if (candidades.size() == 1 && !excluded.isEmpty()) {
                    Domain d = candidades.toArray(new Domain[]{})[0];
                    if (!d.isStored()) {
                        result = true;
                        if (d.getTVariables(true).isEmpty()) {
                            Record x = d.setStored();
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "DB set record (a): " + x);
                            }
                        } else {
                            Record x = d.createStored();
                            if (d.isCalculated()) {
                                x.getDomain().setCalculated();
                            }
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "DB add record (a): " + x);
                            }
                        }
                    }
                }
            }
        }
        if (result) {
            user.getMind().getDatabase().incTag();
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


