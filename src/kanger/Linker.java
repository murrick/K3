
package kanger;

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

        DatabaseFactory.Record saveRec;
      
        Set<Domain> waiters = new HashSet<>();
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (tree.getSequence().size() == 1 && !tree.getSequence().get(0).getTVariables(true).isEmpty()) {
                waiters.add(tree.getSequence().get(0));
            }
        }
        
        do {

            saveRec = user.getMind().getDatabase().getRoot();
            boolean occurrs = false;

            for (DatabaseFactory.Record r = user.getMind().getDatabase().getRoot(); r != null; r = r.getNext()) {
                Domain slave = r.getDomain();
                for (Domain master : slave.getPredicate().getLinked()) {
                    if (master.isAntc() != slave.isAntc()) {
                        boolean success = true;
                        TValue subst[] = new TValue[slave.getPredicate().getRange()];
                        user.getMind().getTValues().mark();
                        for (int i = 0; i < slave.getPredicate().getRange(); ++i) {                           
                            if (master.get(i).isTSet() && master.getVarOrder(i) >= slave.getVarOrder(i)) {
                                TValue s = user.getMind().getTValues().find(master.get(i).getT(), slave.get(i).getValue());
                                if (s == null) {
                                    s = user.getMind().getTValues().add(master.get(i).getT(), slave.get(i).getValue());
                                }
                                subst[i] = s;
                            } else if (master.get(i).isEmpty()
                                    || slave.get(i).isEmpty()
                                    || master.get(i).getValue().getId() != slave.get(i).getValue().getId()) {
                                success = false;
                                break;
                            } else {
                                subst[i] = null;
                            }
                        }
                        if (success) {
                            user.getMind().getTValues().commit();
                            success = false;
                            for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
                                if (subst[i] != null) {
                                    if (subst[i].addSolve(i, master, slave)) {
                                        success = true;
                                        if (logging) {
                                            user.getMind().getLog().add(LogMode.ANALIZER, "Closed: " + subst[i]);
                                        }
                                    }
                                }
                            }
                            if (success) {
                                master.apply(slave);
                                master.setExcluded();
                                if (logging) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "From right: " + master.getRight());
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tAcceptor: " + master);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tDonor   : " + slave);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                                }
                            }
                        } else {
                            user.getMind().getTValues().release();
                        }
                    }
                }
            }

            for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
                if (updateDatabase(null, tree, waiters, logging)) {
                    occurrs = true;
                }
            }
            if (occurrs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }

        } while ((saveRec == null && user.getMind().getDatabase().getRoot() != null)
                || (user.getMind().getDatabase().getRoot() != null && user.getMind().getDatabase().getRoot().getId() != saveRec.getId()));
    }

    private boolean updateDatabase(SortedSet<TVariable> tvars, Tree tree, Set<Domain> waiters, boolean logging) {

        boolean result = false;
        if (tvars == null) {
            tvars = new TreeSet<>();
            tvars.addAll(tree.getTVariables(true));
        }
        if (tvars.isEmpty()) {
            Set<Domain> excluded = new HashSet<>();
            Set<Domain> candidades = new HashSet<>();
            for (Domain d : tree.getSequence()) {

                if (d.isStored() || !d.isComplete()) {
                    excluded.clear();
                    candidades.clear();
                    break;
                } else if (d.isExcluded()) {
                    excluded.add(d);
                } else {
                    candidades.add(d);
                }
                
                for(Domain master : waiters) {
                    if(master.isAntc() != d.isAntc()) {
                        boolean success = true;                       
                        user.getMind().getTValues().mark();
                        for (int i = 0; i < d.getPredicate().getRange(); ++i) {                           
                            if (master.get(i).isTSet() && master.getVarOrder(i) >= d.getVarOrder(i)) {                         
                            } else if (master.get(i).isEmpty()
                                       || d.get(i).isEmpty()
                                       || master.get(i).getValue().getId() != d.get(i).getValue().getId()) {
                                success = false;
                                break;
                            } 
                        }
                       
                        if(success) {
                            d.setUsed();
                        }
                    }
                }
            }
            if (candidades.size() == 1) {
                Domain d = candidades.toArray(new Domain[]{})[0];
                result = true;
                if (excluded.isEmpty() && (d.getTVariables(true).isEmpty() || d.getRight().isQuery())) {
                    Domain x = d.setStored();
                    x.setProduced();
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + d);
                    }
                } else {
                    Domain x = d.createStored();
                    x.setProduced();
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                    }
                }
            } else if (candidades.isEmpty()) {
                for (Domain d : excluded) {
                    result = true;
                    Domain x = d.createStored();
                    x.setProduced();
                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                    }
                }
            }
        } else {
            TVariable t = tvars.last(); //.get(tIndex);
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


