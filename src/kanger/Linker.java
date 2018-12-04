
package kanger;

import kanger.calculator.Calculator;
import kanger.enums.Enums;
import kanger.enums.LogMode;
import kanger.factory.DatabaseFactory;
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

        Set<Domain> waiters = new HashSet<>();
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (tree.getSequence().size() == 1 && !tree.getSequence().get(0).getTVariables(true).isEmpty()) {
                waiters.add(tree.getSequence().get(0));
            }
        }

        DatabaseFactory.Record saveR;
        TValue saveT;
        FValue saveF;
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

                        if (linkDomainsForTree(t, logging)) {
                            result = true;
//                            if (logging) {
//                                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
//                            }
                        }
                        if (calcFunctionsForTree(t, logging)) {
                            result = true;
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                            }
                        }
                        if (updateDatabaseForTree(t, waiters, logging)) {
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

    private boolean linkDomains(boolean logging) {

        boolean result = false;
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (linkDomainsForTree(tree, logging)) {
                result = true;
            }
        }
        return result;
    }

    private boolean linkDomainsForTree(Tree treeSlave, boolean logging) {

        boolean result = false;
        if (treeSlave.getSequence().size() == 1) {//treeSlave.getSequence().size() == 1) { //&& checkSystem(treeSlave, logging)) {
            for (Domain slave : treeSlave.getSequence()) {

                for (Tree treeMaster : slave.getPredicate().getLinkedTrees()) {

                    if (checkSystem(treeMaster, logging) && checkSystem(treeSlave, logging)) {

                        for (Domain master : treeMaster.getSequence()) {


                            if (master.getPredicate().getId() == slave.getPredicate().getId() && master.isAntc() != slave.isAntc()) {

//                                        linkFunctions(master, slave, 0, logging, new HashSet<Function>());

                                TValue[] substMaster = new TValue[slave.getPredicate().getRange()];
                                TValue[] substSlave = new TValue[slave.getPredicate().getRange()];

                                user.getMind().getTValues().mark();
                                user.getMind().getFValues().mark();

                                boolean success = true;
                                boolean applied = false;
                                for (int i = 0; i < slave.getPredicate().getRange(); ++i) {

//                                            if (master.get(i).isFSet() && master.get(i).isEmpty()) {
//                                                master.get(i).getF().setValue(null);
//                                                if (new Calculator(user).calculate(master.get(i).getF(), logging) > 0) {
//                                                }
//                                            }
//                                            if (slave.get(i).isFSet() && slave.get(i).isEmpty()) {
//                                                slave.get(i).getF().setValue(null);
//                                                if (new Calculator(user).calculate(slave.get(i).getF(), logging) > 0) {
//                                                }
//                                            }

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
//                                            } else if (master.get(i).isFSet()
//                                                    && !slave.get(i).isEmpty()
//                                                    && master.get(i).getF().isCalculable()
//                                                    && (master.get(i).isEmpty() || master.get(i).getValue().getId() != slave.get(i).getValue().getId())) {
//                                                master.pushValues();
//                                                master.get(i).getF().setValue(slave.get(i).getValue());
//                                                if (new Calculator(user).calculate(master.get(i).getF(), logging) > 0) {
////                                                    substMaster[i] = null; //master.get(i).getF().getValue();
////                                                    applied = true;
////                                                    result = true;
//                                                }
//                                                master.popValues();
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
//                                            } else if (slave.get(i).isFSet()
//                                                    && !master.get(i).isEmpty()
//                                                    && slave.get(i).getF().isCalculable()
//                                                    && (slave.get(i).isEmpty() || slave.get(i).getValue().getId() != master.get(i).getValue().getId())) {
//                                                slave.pushValues();
//                                                slave.get(i).getF().setValue(master.get(i).getValue());
//                                                if (new Calculator(user).calculate(slave.get(i).getF(), logging) > 0) {
////                                                    substSlave[i] = null; //slave.get(i).getF().getValue();
////                                                    applied = true;
////                                                    result = true;
//                                                }
//                                                slave.popValues();
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
                                linkFunctions(master, slave, 0, logging, new HashSet<Function>());
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

    private boolean updateDatabaseS(SortedSet<TVariable> tvars, Tree tree, Set<Domain> waiters, boolean logging) {

        boolean result = false;
        if (tvars == null) {
            tvars = new TreeSet<>();
            tvars.addAll(tree.getTVariables(true));
        }
        if (tvars.isEmpty()) {

            if (checkSystem(tree, logging)) {

                Set<Domain> excluded = new HashSet<>();
                Set<Domain> calculated = new HashSet<>();
                Set<Domain> candidades = new HashSet<>();
                Set<Domain> assumed = new HashSet<>();

                for (Domain d : tree.getSequence()) {
                    for (Domain master : waiters) {
                        if (master.getPredicate().getId() == d.getPredicate().getId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                            boolean success = true;
//                            user.getMind().getTValues().mark();
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
//                if (d.isComplete() && d.isSystem()) {
//
//                    boolean occurs;
//                    int res;
//                    do {
//                        occurs = false;
//                        res = d.execSystem();
//                        for (Argument a : d.getArguments()) {
//                            if (a.isFSet() && a.getF().isCalculable() && a.isEmpty()) {
//                                if (new Calculator(user).calculate(a.getF(), logging) > 0) {
////                                    FValue s = user.getMind().getFValues().add(a.getF());
//                                    occurs = true;
//                                }
//                            }
//                        }
//
//                    } while (occurs);
//
//                    for (Argument a : d.getArguments()) {
//
//                        if (a.isEmpty()) {
//                            res = -2;
//                            break;
//                        }
//                    }
//
//                    if (res == 0) {
//                        if (d.isAntc()) {
//                            d.setCalculated();
//                        }
//                    } else if (res == 1) {
//                        if (!d.isAntc()) {
//                            d.setCalculated();
//                        }
//                    }
//                }

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
                            DatabaseFactory.Record x = d.setStored();
                            x.getDomain().setProduced();
                            if (logging) {
                                user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + x);
                            }
                        } else {
                            DatabaseFactory.Record x = d.createStored();
                            x.getDomain().setProduced();
                            if (d.isCalculated()) {
                                x.getDomain().setCalculated();
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
                            DatabaseFactory.Record x = d.createStored();
                            x.getDomain().setProduced();
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
                            DatabaseFactory.Record x = d.createStored();
                            x.getDomain().setProduced();
                            if (d.isCalculated()) {
                                x.getDomain().setCalculated();
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
            }
        } else {
            TVariable t = tvars.last();
            TValue v = t.rewind();
            if (v != null) {
                do {
                    user.getMind().getTValues().set(t, v);
                    if (updateDatabaseS(tvars.headSet(t), tree, waiters, logging)) {
                        result = true;
                    }
                } while ((v = t.next(v)) != null);

            } else {
                if (updateDatabaseS(tvars.headSet(t), tree, waiters, logging)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private boolean updateDatabase(Set<Domain> waiters, boolean logging) {

        boolean result = false;
        for (Tree tree = user.getMind().getTrees().getRoot(); tree != null; tree = tree.getNext()) {
            if (updateDatabaseForTree(tree, waiters, logging)) {
                result = true;
            }
        }
        return result;
    }

    private boolean updateDatabaseForTree(Tree tree, Set<Domain> waiters, boolean logging) {

        boolean result = false;
        boolean occurrs = false;
        if (!tree.isClosed() && checkSystem(tree, logging)) {

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
                } else if (d.isStored()) {
                    excluded.clear();
                    candidades.clear();
                    break;
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
                    if (excluded.isEmpty() && !d.isCalculated() && (d.getTVariables(true).isEmpty() || d.getRight().isQuery())) {
                        DatabaseFactory.Record x = d.setStored();
                        x.getDomain().setProduced();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB set record: " + x);
                        }
                    } else {
                        DatabaseFactory.Record x = d.createStored();
                        x.getDomain().setProduced();
                        if (d.isCalculated()) {
                            x.getDomain().setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record: " + x);
                        }
                    }
                } else {
                    d.setProduced();
                }
            } else if (!excluded.isEmpty() && candidades.isEmpty()) {
                occurrs = true;
                for (Domain d : excluded) {
                    if (!d.isStored()) {
                        result = true;
                        DatabaseFactory.Record x = d.createStored();
                        x.getDomain().setProduced();
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record (x): " + x);
                        }
                    } else {
                        d.setProduced();
                    }
                }
            } else if (!calculated.isEmpty() && tree.getSequence().size() == calculated.size()) {
                occurrs = true;
                for (Domain d : calculated) {
                    if (!d.isStored()) {
                        result = true;
                        DatabaseFactory.Record x = d.createStored();
                        x.getDomain().setProduced();
                        if (d.isCalculated()) {
                            x.getDomain().setCalculated();
                        }
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "DB add record (c): " + x);
                        }
                    } else {
                        d.setProduced();
                    }
                }
            }


            if (!occurrs && tree.getSequence().size() > 1) {
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
        }
        if (result) {
            user.getMind().getDatabase().incTag();
        }

        return result;
    }

    public boolean calcFunctions(boolean logging) {
        boolean result = false;

        for (Tree master = user.getMind().getTrees().getRoot(); master != null; master = master.getNext()) {
            if (calcFunctionsForTree(master, logging)) {
                result = true;
            }
        }

        return result;
    }

    public boolean calcFunctionsForTree(Tree master, boolean logging) {
        boolean result = false;

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

        return result;
    }

    public boolean checkSystem(Tree tree, boolean logging) {
        boolean block = false;
        for (Domain d : tree.getSequence()) {
            if (d.isSystem() /*&& !d.isCalculated()*/) {

                d.pushValues();
                boolean occurs;
                int res = d.execSystem();
//                do {
//                    occurs = false;
//                    for (Argument a : d.getArguments()) {
//                        if (a.isFSet() && a.getF().isCalculable() && a.isEmpty()) {
//                            if (new Calculator(user).calculate(a.getF(), logging)) {
//                                occurs = true;
//                            }
//                        }
//                    }
//                    res = d.execSystem();
//                    for (Argument a : d.getArguments()) {
//                        if (a.isFSet() && a.getF().isCalculable() && a.isEmpty()) {
//                            if (new Calculator(user).calculate(a.getF(), logging)) {
//                                occurs = true;
//                            }
//                        }
//                    }
//                } while (occurs);

                for (Argument a : d.getArguments()) {
                    if (a.isEmpty()) {
                        res = -2;
//                        block = true;
                        break;
                    }
                }


                if (res == 0) {
                    if (d.isAntc()) {
                        d.setCalculated();
                    } else if (!d.isQuery()) {
                        block = true;
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setCalculated();
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
        return !block;
    }

    private boolean linkFunctions(Domain master, Domain slave, int level, boolean logging, Set<Function> selected) {

        if (level >= master.getPredicate().getRange()) {

            boolean occurrs = false;
            for (Function f : selected) {
                if (f.isEmpty() && f.isCalculable()) {
                    if (new Calculator(user).calculate(f, logging)) {
                        occurrs = true;
//                            mind.getFValues().add(f);
//                            if (logging) {
//                                mind.getLog().add(LogMode.ANALIZER, "Shot function result: " + f.toString());
//                            }
                    }
                }
            }

            if (occurrs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
//            if (logging && occurrs) {
//                logComparsion(master);
//                logComparsion(slave);
//                mind.getLog().createTVar(LogMode.ANALIZER, "-------------------------------------------");
//            }
            return occurrs;

        } else {

//            boolean isQuery = mind.getQuery() != null;
            //ПОДСТАНОВКИ Результатов функций
//            for (int i = 0; i <= level; ++i) {

//            if (master.get(level).isFunction() && !master.get(level).getFunction().getName().toString().equals("_add")) {
//                System.out.println(master);
//            }

            if (master.get(level).isFSet() && master.get(level).getF().isEmpty()) {
                if (!slave.get(level).isEmpty()
                        && master.get(level).getF().isCalculable()
                        && !master.get(level).getF().isComplete()) {
                    master.get(level).getF().setValue(slave.get(level).getValue());
                    selected.add(master.get(level).getF());
                } else if (master.get(level).getF().isCalculable()
                        && master.get(level).getF().isComplete()) {
                    selected.add(master.get(level).getF());
                } else if (!master.get(level).getF().isCalculable()) {
                    selected.add(master.get(level).getF());
                }
            }

//            if (slave.get(level).isFunction() && !slave.get(level).getFunction().getName().toString().equals("_add")) {
//                System.out.println(slave);
//            }

            if (slave.get(level).isFSet() && slave.get(level).getF().isEmpty()) {
                if (!master.get(level).isEmpty()
                        && slave.get(level).getF().isCalculable()
                        && !slave.get(level).getF().isComplete()) {
                    slave.get(level).getF().setValue(slave.get(level).getValue());
                    selected.add(slave.get(level).getF());
                } else if (slave.get(level).getF().isCalculable()
                        && slave.get(level).getF().isComplete()) {
                    selected.add(slave.get(level).getF());
                } else if (!slave.get(level).getF().isCalculable()) {
                    selected.add(slave.get(level).getF());
                }
            }

//            }
            return linkFunctions(master, slave, level + 1, logging, selected);
        }

    }

}


