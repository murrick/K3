
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.factory.DatabaseFactory;
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

        DatabaseFactory.Record saveRec;

        for (Function f = user.getMind().getFunctions().getRoot(); f != null; f = f.getNext()) {
            if (!f.isCalculable()) {
                new Calculator(user).calculate(f, logging);
            }
        }

        Set<Domain> waiters = new HashSet<>();
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (tree.getSequence().size() == 1 && !tree.getSequence().get(0).getTVariables(true).isEmpty()) {
                waiters.add(tree.getSequence().get(0));
            }
        }

        do {

            saveRec = user.getMind().getDatabase().getRoot();

            linkDomains(null, logging);
            boolean occurrs = false;
            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
                if (!tree.isClosed() && updateDatabase(null, tree, waiters, logging)) {
                    occurrs = true;
                }
            }
            if (occurrs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }


        } while ((saveRec == null && user.getMind().getDatabase().getRoot() != null)
                || (user.getMind().getDatabase().getRoot() != null && user.getMind().getDatabase().getRoot().getId() != saveRec.getId()));
    }

    private boolean linkDomains(SortedSet<TVariable> tvars, boolean logging) {

        boolean result = false;
        if (tvars == null) {
            tvars = new TreeSet<>();
            tvars.addAll(user.getMind().getDatabase().getTVariables(true));
        }
        if (tvars.isEmpty()) {

//            for (DatabaseFactory.Record r = user.getMind().getDatabase().getRoot(); r != null; r = r.getNext()) {
            for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
                if (d.isStored() || d.isSingleInTree()) {
                    Domain slave = d;
                    for (Domain master : slave.getPredicate().getLinked()) {
                        if (master.isAntc() != slave.isAntc()) {
                            TValue[] substMaster = new TValue[slave.getPredicate().getRange()];
                            TValue[] substSlave = new TValue[slave.getPredicate().getRange()];
                            user.getMind().getTValues().mark();
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
//                                } else if(master.get(i).isFSet()
//                                        && master.get(i).getF().isCalculable()
//                                        && !master.get(i).getF().isCalculated()
//                                        && !slave.get(i).isEmpty()) {
//                                    FValue  s = user.getMind().getFValues().add(master.get(i).getT(), slave.get(i).getValue());
//                                        result = true;
//                                    substMaster[i] = s;
//                                    applied = true;
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
                                markExcluded(substMaster, master, slave, logging);
                                markExcluded(substSlave, slave, master, logging);
                            } else {
                                user.getMind().getTValues().release();
                            }
                        }
                    }
                }

                if (d.isSystem() && !d.isCalculated()) {
                    d.pushValues();
                    int res = d.execSystem();

                    for (Argument a : d.getArguments()) {
                        if (!a.isCalculated()) {
                            res = -2;
                            break;
                        }
                    }

                    boolean block = false;
                    if (res == 0) {
                        if (d.isAntc()) {
//                            d.setCalculated();
                        } else if (!d.isQuery()) {
                            block = true;
                        }
                    } else if (res == 1) {
                        if (!d.isAntc()) {
//                            d.setCalculated();
                        } else if (!d.isQuery()) {
                            block = true;
                        }
                    }

                    if (block && logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
                    }
                    d.popValues();
                }

            }

//            for (Domain d = user.getMind().getDomains().getRoot(); d != null; d = d.getNext()) {
//                if (d.isSystem() && !d.isCalculated()) {
//                    int res = d.execSystem();
//
//                    for (Argument a : d.getArguments()) {
//                        if (!a.isCalculated()) {
//                            res = -2;
//                            break;
//                        }
//                    }
//
//                    boolean block = false;
//                    if (res == 0) {
//                        if (d.isAntc()) {
//                            d.setCalculated();
//                        } else if (!d.isQuery()) {
//                            block = true;
//                        }
//                    } else if (res == 1) {
//                        if (!d.isAntc()) {
//                            d.setCalculated();
//                        } else if (!d.isQuery()) {
//                            block = true;
//                        }
//                    }
//
//                    if (block && logging) {
//                        user.getMind().getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
//                    }
//                }
//            }


        } else {
            TVariable t = tvars.last();
            TValue v = t.rewind();
            if (v != null) {
                do {
                    user.getMind().getTValues().set(t, v);
                    if (linkDomains(tvars.headSet(t), logging)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);

            } else {
                if (linkDomains(tvars.headSet(t), logging)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean markExcluded(TValue[] subst, Domain master, Domain slave, boolean logging) {
        boolean applied = false;
        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
            if (subst[i] != null) {
                if (subst[i].addSolve(i, master, slave)) {
                    applied = true;
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "Closed: " + subst[i]);
                    }
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

    private boolean updateDatabase(SortedSet<TVariable> tvars, Tree tree, Set<Domain> waiters, boolean logging) {

        boolean result = false;
        if (tvars == null) {
            tvars = new TreeSet<>();
            tvars.addAll(tree.getTVariables(true));
        }
        if (tvars.isEmpty()) {
            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidades = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();

            for (Domain d : tree.getSequence()) {
                for (Domain master : waiters) {
                    if (master.getPredicate().getId() == d.getPredicate().getId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                        boolean success = true;
                        user.getMind().getTValues().mark();
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
                if (d.isComplete() && d.isSystem()) {
                    int res = d.execSystem();

                    for (Argument a : d.getArguments()) {
                        if (!a.isCalculated()) {
                            res = -2;
                            break;
                        }
                    }

                    if (res == 0) {
                        if (d.isAntc()) {
                            d.setCalculated();
                        }
                    } else if (res == 1) {
                        if (!d.isAntc()) {
                            d.setCalculated();
                        }
                    }
                }

                if (d.isCalculated()) {
                    calculated.add(d);
                } else if (d.isStored() || d.isSystem() || !d.isComplete()) {
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
                result = true;
                if (!d.isStored()) {
                    if (excluded.isEmpty() && !d.isCalculated() && (d.getTVariables(true).isEmpty() || d.getRight().isQuery())) {
                        Domain x = d.setStored();
                        x.setProduced();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + x);
                        }
                    } else {
                        Domain x = d.createStored();
                        x.setProduced();
                        if (d.isCalculated()) {
                            x.setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                        }
                    }
                } else {
                    d.setProduced();
                }
            } else if (!excluded.isEmpty() && candidades.isEmpty()) {
                for (Domain d : excluded) {
                    result = true;
                    if (!d.isStored()) {
                        Domain x = d.createStored();
                        x.setProduced();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                        }
                    } else {
                        d.setProduced();
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                for (Domain d : calculated) {
                    result = true;
                    if (!d.isStored()) {
                        Domain x = d.createStored();
                        x.setProduced();
                        if (d.isCalculated()) {
                            x.setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                        }
                    } else {
                        d.setProduced();
                    }
                }
            }


            if (!result && tree.getSequence().size() > 1) {
                candidades.clear();
                for (Domain d : tree.getSequence()) {
                    if (d.isComplete() && !d.isExcluded() && !assumed.contains(d)) {
                        candidades.add(d);
                    }
                }
                if (candidades.size() == 1) {
                    candidades.toArray(new Domain[]{})[0].setProduced();
                }
            }
        } else {
            TVariable t = tvars.last();
            TValue v = t.rewind();
            if (v != null) {
                do {
                    user.getMind().getTValues().set(t, v);
                    if (updateDatabase(tvars.headSet(t), tree, waiters, logging)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);

            } else {
                if (updateDatabase(tvars.headSet(t), tree, waiters, logging)) {
                    result = true;
                }
            }
        }
        return result;
    }


}


