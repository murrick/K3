
package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.units.*;

import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry G. Qusnetsov
 */
public class Linker {

    private final Mind mind;

    private int solvedPasses = 0;
    private int dumpedPasses = 0;
    private int skipedPasses = 0;

    //TODO: ПРИБИТЬ
//    int cccc = 0;


    public Linker(Mind mind) {
        this.mind = mind;
    }

    public void link(Right right, boolean logging) throws Exception {

        mind.getExcludedDomains().clear();
        mind.getUsedDomains().clear();
        mind.getCalculatedDomains().clear();
        mind.getUsedRights().clear();

        int passCounter = 0;

        solvedPasses = 0;
        dumpedPasses = 0;
        skipedPasses = 0;

        final Map<Right, Set<Cause>> causes = new HashMap<>();

        do {

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }

            mind.getRights().dropAction();
            mind.getTValues().dropAction();
            mind.getFValues().dropAction();

            Set<Right> rightSet = new HashSet<>();
            if (right != null) {
                rightSet.addAll(right.getNatives());
                if (mind.getUsedRights().containsKey(0L)) {
                    for (Right r : mind.getUsedRights().get(0L)) {
                        if (r.isUsed()) {
                            rightSet.addAll(r.getNatives());
                        }
                    }
                }
            } else {
                for (Right r : mind.getRights()) {
                    if (!r.isDeleted()) {
                        rightSet.add(r);
                    }
                }
            }

            List<Right> leftList = new ArrayList<>();
            List<Right> rightList = new ArrayList<>();

            leftList.addAll(rightSet);
            Collections.sort(leftList, new Comparator<Right>() {
                @Override
                public int compare(Right o1, Right o2) {
                    return (int) (o2.getId() - o1.getId());
                }
            });
            rightList.addAll(rightSet);
            Collections.sort(rightList, new Comparator<Right>() {
                @Override
                public int compare(Right o1, Right o2) {
                    return (int) (o1.getId() - o2.getId());
                }
            });

            rotator(leftList, causes, logging);
            rotator(rightList, causes, logging);


        } while (mind.getRights().isAction()
                || mind.getTValues().isAction()
                || mind.getFValues().isAction()
        );

        if (logging) {
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Solved passes: %03d", solvedPasses));
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Dumped passes: %03d", dumpedPasses));
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Skiped passes: %03d", skipedPasses));
        }

        if (logging) {
            if (mind.getUsedRights().containsKey(0L) && !mind.getUsedRights().get(0L).isEmpty()) {
                mind.getLog().add(LogMode.ANALIZER, String.format("---------- LINKER USED RIGHTS -------------"));
                for (Right r : mind.getUsedRights().get(0L)) {
                    mind.getLog().add(LogMode.ANALIZER, r.toString());
                }
            }
        }
    }

    private boolean rotator(Collection<Right> rightList, Map<Right, Set<Cause>> causes, boolean logging) throws Exception {

        boolean used = false;

        for (Right r : rightList) {

            mind.getProducedDomains().clear();
            mind.getDomainSolves().clear();
            mind.getDomainCauses().clear();

            final SortedSet<TVariable> tvars = new TreeSet<>();
            for (List<Domain> tree : r.getTree()) {
                for (Domain d : tree) {
                    tvars.addAll(d.getArguments().getTVariables(true));
                }
            }

            boolean wasUsed = r.isUsed();

            for (List<Domain> tree : r.getTree()) {

                final List<Domain> t = tree;

                rotateVariables(tvars, logging, new IReactor() {
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

                            if (linkDatabase(t, causes, tvars, logging)) {
                                result = true;
                            }


                        } catch (Exception e) {
                            e.printStackTrace(System.err);
                            result = false;
                        }

                        return result;
                    }
                });
            }

            updateDatabase(logging);

            if (!wasUsed && r.isUsed()) {
                used = true;
            }
        }

        return used;
    }

    private boolean rotateVariables(SortedSet<TVariable> tvars, boolean logging, IReactor runnable) throws Exception {
        final boolean[] result = new boolean[]{false, false};
        if (tvars.isEmpty()) {
            result[0] = (boolean) runnable.run(logging);
        } else {
            final TVariable t = tvars.last();
            result[1] = false;
            mind.getTValues().forEach(t, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    result[1] = true;
                    mind.getTValues().set(t, (TValue) o);
                    if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                        result[0] = true;
                    }
                    return true;
                }
            });
            if (!result[1]) {
                if (rotateVariables(tvars.headSet(t), logging, runnable)) {
                    result[0] = true;
                }
            }
        }
        return result[0];
    }

    private boolean linkDomains(List<Domain> treeSlave, Map<Right, Set<Cause>> causes, boolean logging) throws Exception {

        boolean result = false;
        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (Right right : mind.getRights()) {
                    if (right.isDeleted() || !right.getPredicates().contains(slave.getPredicateId())) {
                        continue;
                    }

//                    System.err.println("\t" + right.getId() + ": " + right);

                    for (List<Domain> treeMaster : right.getTree()) {
                        for (Domain master : treeMaster) {
                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {

                                Object[] substMaster = new TValue[master.getRange()];
                                Object[] substSlave = new TValue[slave.getRange()];

//                                master.recalculate(true);
//                                slave.recalculate(true);

                                mind.getTValues().mark();
                                mind.getFValues().mark();

                                boolean success = true;
                                boolean applied = false;

                                // Отсечение несовпадений по константам
                                for (int i = 0; i < slave.getRange(); ++i) {
                                    if (!master.get(i).isTSet() && !slave.get(i).isTSet()
                                            && (!master.get(i).isFSet() || !master.get(i).getF().isEmpty())
                                            && (!slave.get(i).isFSet() || !slave.get(i).getF().isEmpty())
                                            && (master.get(i).isEmpty()
                                            || slave.get(i).isEmpty()
                                            || master.get(i).getValue().getId() != slave.get(i).getValue().getId())) {
                                        success = false;
                                        break;
                                    }
                                }

                                if (success) {
                                    for (int i = 0; i < slave.getRange(); ++i) {

                                        // Подстановка снизу вверх
                                        if (master.get(i).isTSet()
                                                && !slave.get(i).isEmpty()
                                                && master.getVarOrder(i) >= slave.getVarOrder(i)) {
                                            TValue s = mind.getTValues().find(master.get(i).getT(), slave.get(i).getValue());
                                            if (s == null) {
                                                s = mind.getTValues().add(master.get(i).getT(), slave.get(i).getValue());
                                                result = true;
                                            }
                                            substMaster[i] = s;
                                            slave.setUsed();
                                            master.setUsed();
                                            applied = true;
                                        }

                                        // Подстановка сверху вниз
                                        if (slave.get(i).isTSet()
                                                && !master.get(i).isEmpty()
                                                && slave.getVarOrder(i) >= master.getVarOrder(i)) {
                                            TValue s = mind.getTValues().find(slave.get(i).getT(), master.get(i).getValue());
                                            if (s == null) {
                                                s = mind.getTValues().add(slave.get(i).getT(), master.get(i).getValue());
                                                result = true;
                                            }
                                            substSlave[i] = s;
                                            master.setUsed();
                                            slave.setUsed();
                                            applied = true;
                                        }

//                                        if (master.get(i).isFSet()
//                                                && master.get(i).getF().isCalculable()
//                                                && master.get(i).getF().isEmpty()
//                                                && !slave.get(i).isEmpty()) {
//
//                                            long id = mind.getFValues().getLastId();
//                                            master.get(i).getF().setResult(slave.get(i).getValue());
//                                            if (mind.getCalculator().calculate(master.get(i).getF(), logging)
//                                                    && id < mind.getFValues().getLastId()) {
//                                                result = true;
//                                                List<TValue> list = new ArrayList<>();
//                                                for (TValue v : mind.getTValues()) {
//                                                    if (v.getId() <= id) {
//                                                        break;
//                                                    } else {
//                                                        list.add(v);
//                                                    }
//                                                }
//                                                substMaster[i] = list;
//                                                slave.setUsed();
//                                                master.setUsed();
//                                                applied = true;
//                                            }
//                                        }
//
//                                        if (slave.get(i).isFSet()
//                                                && slave.get(i).getF().isCalculable()
//                                                && slave.get(i).getF().isEmpty()
//                                                && !master.get(i).isEmpty()) {
//
//                                            long id = mind.getFValues().getLastId();
//                                            slave.get(i).getF().setResult(master.get(i).getValue());
//                                            if (mind.getCalculator().calculate(slave.get(i).getF(), logging)
//                                                    && id < mind.getFValues().getLastId()) {
//                                                result = true;
//                                                List<TValue> list = new ArrayList<>();
//                                                for (TValue v : mind.getTValues()) {
//                                                    if (v.getId() <= id) {
//                                                        break;
//                                                    } else {
//                                                        list.add(v);
//                                                    }
//                                                }
//                                                substSlave[i] = list;
//                                                slave.setUsed();
//                                                master.setUsed();
//                                                applied = true;
//                                            }
//                                        }

                                        if (!applied) {
                                            if (master.get(i).isEmpty() || slave.get(i).isEmpty()
                                                    || master.get(i).getValue().getId() != slave.get(i).getValue().getId()) {
                                                success = false;
                                                break;
                                            } else {
                                                substMaster[i] = null;
                                                substSlave[i] = null;
                                            }
                                        } else {
                                            master.getRight().setUsed();
                                        }

                                    }
                                }

                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();

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

                                    mind.getTValues().release();
                                    mind.getFValues().release();
                                }

                            }
                        }
                    }
                }

            }
        }

        return result;
    }

    private boolean markExcluded(Object[] subst, Domain master, Domain slave, Map<Right, Set<Cause>> causes, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right r = null;
        boolean occurrs = false;
        for (int i = 0; i < slave.getRange(); ++i) {
            if (subst[i] != null) {
                List<TValue> list = new ArrayList<>();
                if (subst[i] instanceof Collection) {
                    list.addAll((Collection<TValue>) subst[i]);
                } else {
                    list.add((TValue) subst[i]);
                }
                for (TValue v : list) {
                    boolean caused = false;
                    Cause s = new Cause(i, master, slave);
                    if (!v.getCauses().contains(s)) {
                        v.getCauses().add(s);
                        caused = true;
                    }
                    if (caused || !master.isExcluded(slave.getArguments())) {
                        r = v.getTVar().getRight();
                        if (caused) {
                            if (!causes.containsKey(r)) {
                                causes.put(r, new HashSet<>());
                            }
                            causes.get(r).add(s);
                            if (logging) {
                                mind.getLog().add(LogMode.ANALIZER, "Closed: " + v);
                            }
                            occurrs = true;
                        }
                    }
                }
            }
        }
        if (r != null) {
            master.setExcluded(slave.getArguments());
            if (occurrs && logging) {
                mind.pushDebugLevel();
                mind.setDebugLevel(mind.getDebugLevel() & ~(Enums.DEBUG_OPTION_VALUES | Enums.DEBUG_OPTION_STATUS));
                mind.getLog().add(LogMode.ANALIZER, "From right: " + r); //master.getRight());
                mind.getLog().add(LogMode.ANALIZER, "\tAcceptor: " + master);
                mind.popDebugLevel();
                mind.getLog().add(LogMode.ANALIZER, "\tDonor   : " + slave);
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return r != null;
    }


    private boolean linkDatabase(List<Domain> tree, Map<Right, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {

        boolean result = false;
        boolean occurs = false;


        long tag = mind.getTValues().incTag();

//        for (Domain d : tree) {
//            d.recalculate(true);
//        }

        if (checkSystem(tree, logging)) {

//            for (Domain d : tree) {
//                d.recalculate(true);
//            }


            List<TValue> solve = new ArrayList<>();
            for (TVariable t : tvars) {
                if (!t.isEmpty()) {
                    solve.add(t.getCurrent());
                }
            }


            Set<Domain> excluded = new HashSet<>();
            Set<Domain> calculated = new HashSet<>();
            Set<Domain> candidates = new HashSet<>();
            Set<Domain> assumed = new HashSet<>();
            Set<Domain> stored = new HashSet<>();

            for (Domain d : tree) {
//                if(!d.isComplete()) {
//                    continue;
//                }

//                for(Function f : d.getArguments().getFunctions()) {
//                    f.clear();
//                    mind.getCalculator().calculate(f, logging);
//                }

                for (Domain master : mind.getDomains().getWaiters()) {
                    if (master.getPredicateId() == d.getPredicateId() && master.isAntc() != d.isAntc() && d.isComplete()) {
                        boolean success = true;
                        for (int i = 0; i < d.getRange(); ++i) {
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
//                if(!d.isComplete()) {
//                    continue;
//                }

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
                        mind.getLog().add(LogMode.STORAGE, "DB assumed record: " + d);
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
                        d.setTag(tag = mind.getTValues().incTag());
                        d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve);
                        if (logging) {
                            mind.getLog().add(LogMode.STORAGE, "DB assumed record (x): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
                //TODO: Сомнительно, но вроде работает с ?$x $y index(qwerty) -> index(x), y : x;
            } //else
            if (!calculated.isEmpty() && candidates.isEmpty() /*&& tree.size() - excluded.size() == calculated.size()*/) {
                occurs = true;
                for (Domain d : calculated) {
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored() /*|| d.isQuery()*/) {
                        result = true;
                        d.setProduced();
                        d.setTag(tag = mind.getTValues().incTag());
                        d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve);
                        if (logging) {
                            mind.getLog().add(LogMode.STORAGE, "DB assumed record (c): " + d);
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
                            mind.getLog().add(LogMode.STORAGE, "DB assumed record (a): " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            }
            if (result) {
                if (logging) {
                    mind.getLog().add(LogMode.STORAGE, "-------------------------------------------");
                }
            }

        }

        return result;
    }

    private void logCauses(LogMode mode, Domain d) throws Exception {
        boolean rightShowed = false;
        if (d.getCauses() != null) {
            for (Cause c : d.getCauses()) {
                if (!rightShowed) {
                    mind.getLog().add(mode, "\tFrom right: " + c.getDst().getRight());
                    rightShowed = true;
                }
                mind.getLog().add(mode, "\t\tUsing: " + c.getSrc().toString(c.getArguments()));
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
//        if(!mind.getProducedDomains().isEmpty()) {
//            for (Map.Entry<Domain, List<ArgList>> master : mind.getProducedDomains().entrySet()) {
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
//                        for (Right right : mind.getRights().getDatabase()) {
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
//            for(Right master : mind.getRights().getDatabase()) {
//                for (Right right : mind.getRights().getDatabase(master.getId())) {
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

    private boolean updateDatabase(boolean logging) throws Exception {
        boolean result = false;
        for (Map.Entry<Domain, List<List<Term>>> e : mind.getProducedDomains().entrySet()) {
            Domain d = e.getKey();
            for (List<Term> args : e.getValue()) {
                result = true;
                d.getArguments().applyStamp(args);
                if (d.isComplete()) {

//                for(Function f : d.getArguments().getFunctions()) {
//                    f.clear();
//                    mind.getCalculator().calculate(f, logging);
//                }


//                d.recalculate(true);

//                for (int i = 0; i < args.size(); /*d.getPredicate().getRange();*/ ++i) {
//                    if (d.getArguments().get(i).isTSet()) {
//                        if (d.getArguments().get(i).getT().find(args.get(i).getValue()) != null) {
//                            d.getArguments().get(i).getT().setValue(args.get(i).getValue());
//                        }
//                    } else if (d.getArguments().get(i).isFSet()) {
//                        //TODO: Добавить обработку функций
//                    }
//                }

                    Right x;
//                if (d.getArguments().getTVariables(true).isEmpty()) {
//                    x = d.setStored();
//                    if (logging) {
//                        mind.getLog().add(LogMode.ANALIZER, "DB set record: " + d);
//                    }
//                } else {
                    x = d.createStored();
                    if (d.isUsed()) {
                        x.getDomain().setUsed();
                    }
                    if (logging) {
                        mind.getLog().add(LogMode.STORAGE, "DB add record: " + d + " -> " + x);
                    }
//                }

                    if (d.isCalculated()) {
                        x.getDomain().setCalculated();
                    }
                    if (d.getCauses() != null) {
                        x.getCauses().clear();
                        x.getCauses().addAll(d.getCauses());

//                    for(Cause c : x.getCauses()) {
//                        if(!c.getDst().isStored()) {
//                            c.getDst().createStored();
//                        }
//                    }
                    }
                    if (d.getSolves() != null) {
                        x.getSolves().clear();
                        x.getSolves().addAll(d.getSolves());
                    }
                }

            }
        }

        if (result && logging) {
            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }


    public boolean calcFunctions(List<Domain> master, Map<Right, Set<Cause>> causes, boolean logging) throws Exception {
        boolean result = false;

        for (Domain d : master) {
            for (Function f : d.getArguments().getFunctions()) {
                if (f.isCalculable() && f.isEmpty()) {
                    f.clear();
                    if (mind.getCalculator().calculate(f, logging)) {
                        result = true;
                    }
                }
            }
        }

//        if (checkSystem(master, logging)) {
//            for (Domain d : master) {
//                for (Function f : d.getArguments().getFunctions()) {
//                    if (f.isCalculable() && f.isEmpty()) {
//                        if (mind.getCalculator().calculate(f, logging)) {
//                            result = true;
//                        }
//                    }
//                }
//            }
//        }

        if (result && logging) {
            mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
        }
        return result;
    }

    public boolean checkSystem(List<Domain> tree, boolean logging) throws Exception {
        boolean block = false;
        boolean success = false;
        for (Domain d : tree) {
            if (d.isSystem()) {

//                d.pushValues();

//                List<TValue> list = new ArrayList<>();
//                for (TVariable t : d.getArguments().getTVariables(true)) {
//                    list.add(t.getCurrent());
//                }

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
                        success = true;
                    } else {
                        block = true;
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setCalculated();
                        success = true;
                    } else {
                        block = true;
                    }
                }

                if (block && logging) {
                    mind.getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
                }
//                d.popValues();

//                List<TVariable> ts = d.getArguments().getTVariables(true);
//                for (int i = 0; i < ts.size(); ++i) {
//                    if (list.get(i) != null) {
//                        ts.get(i).setCurrent(list.get(i));
//                    }
//                }
            }
        }

        if (success && !block) {
            for (Domain d : tree) {
                if (d.isSystem() && !d.isCalculated()) {
                    block = true;
                }
            }
            if (block) {
                for (Domain d : tree) {
                    if (d.isCalculated()) {
                        d.unCalculated();
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
