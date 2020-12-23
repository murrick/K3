
package org.kanger;

import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IReactor;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.primitives.TVariableSet;
import org.kanger.units.*;

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

//        right = null;

        mind.getExcludedDomains().clear();
        mind.getUsedDomains().clear();
        mind.getCalculatedDomains().clear();
        mind.getUsedRights().clear();

        mind.getRightSolves().clear();

        int passCounter = 0;

        solvedPasses = 0;
        dumpedPasses = 0;
        skipedPasses = 0;

        final Map<Right, Set<Cause>> causes = new HashMap<>();

        Right top = mind.getRights().getTop();
        long topId = top == null ? -1 : top.getId();

//        int sz = 0;
        do {

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, String.format("---------- LINKER PASS %03d ---------------", ++passCounter));
            }

//            sz = mind.getRightSolves().size();

            mind.getRights().dropAction();
            mind.getTValues().dropAction();
            mind.getFValues().dropAction();

            Set<Right> rightSet = new HashSet<>();
            if (right != null) {

                for (List<Domain> list : right.getTree()) {
                    for (Domain d : list) {
                        if ("rule(1)".equals(d.getPredicate().toString()) && d.get(0).isTSet()) {
                            for (Right r : mind.getRights()) {
                                if (!r.isDeleted() && r.getId() < d.getRightId()) {
                                    TValue s = null;
                                    TVariable t = d.get(0).getT(mind);
                                    Term tm = mind.getTerms().add(r.getId());
                                    s = mind.getTValues().find(t, tm);
                                    if (s == null) {
                                        s = mind.getTValues().add(t, tm);
                                    }
                                }
                            }
                        }
                    }
                }

                rightSet.add(right);
                rightSet.addAll(right.getNatives());
                for (Right r : mind.getRights()) {
                    if (!r.isDeleted()) {
                        if (r.isUsed(mind)) {
                            rightSet.add(r);
                            rightSet.addAll(r.getNatives());
                        } else if (r.isGenerated() && r.getId() > topId) {
                            rightSet.add(r);
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

//            System.err.println("--------------");
//            for(Right r : rightSet) {
//                System.err.println(r);
//            }
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

//            break;

        } while (mind.getRights().isAction()
                || mind.getTValues().isAction()
                || mind.getFValues().isAction()
//                || mind.getRightSolves().size() > sz
        );

        if (logging) {
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Solved passes: %03d", solvedPasses));
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Dumped passes: %03d", dumpedPasses));
            mind.getLog().add(LogMode.TIMING, String.format("* LINKER Skiped passes: %03d", skipedPasses));
        }

//        if (logging) {
//            if (mind.getUsedRights().containsKey(0L) && !mind.getUsedRights().get(0L).isEmpty()) {
//                mind.getLog().add(LogMode.ANALIZER, String.format("---------- LINKER USED RIGHTS -------------"));
//                for (Right r : mind.getUsedRights().get(0L)) {
//                    mind.getLog().add(LogMode.ANALIZER, r.toString());
//                }
//            }
//        }
    }

    private boolean rotator(Collection<Right> rightList, Map<Right, Set<Cause>> causes, boolean logging) throws Exception {

        boolean used = false;

//        mind.getRightSolves().clear();

//        final SortedSet<TVariable> tvars = new TreeSet<>();
//        for (Right r : rightList) {
//            for (List<Domain> tree : r.getTree()) {
//                for (Domain d : tree) {
//                    tvars.addAll(d.getArguments().getTVariables(mind, true));
//                }
//            }
//        }

        for (Right r : rightList) {

            //TODO: Костыль
//            r.setMind(mind);

            mind.getProducedDomains().clear();
            mind.getDomainSolves().clear();
            mind.getDomainCauses().clear();

            final SortedSet<TVariable> tvars = new TreeSet<>();
            for (List<Domain> tree : r.getTree()) {
                for (Domain d : tree) {
                    tvars.addAll(d.getArguments().getTVariables(mind));
                }
            }

            boolean wasUsed = r.isUsed(mind);

//            if (!mind.getRightSolves().isEmpty()) {
//                System.err.println("========================================");
//                for (Map.Entry<TVariableSet, List<TSolve>> e : mind.getRightSolves().entrySet()) {
//                    for (TSolve s : e.getValue()) {
//                        if (!s.getSolve().isEmpty()) {
//                            System.err.println("---- " + " : " + s);
//                        } else {
//                            //TODO: Убрать
//                            System.err.println("?");
//                        }
//                    }
//                }
//            }


            for (List<Domain> tree : r.getTree()) {

                final List<Domain> t = tree;

//                mind.getTSolves().forEach(new IReactor() {
//                    @Override
//                    public Object run(Object o) throws Exception {
//                        ((TSolve) o).getSolve().get(0).getTVar().getRight();
//                        System.err.println("---- " + ((TSolve) o).getSolve().get(0).getTVar().getRight() + " : " + o);
//                        return null;
//                    }
//                });

                rotateVariables(tvars, tvars, new IReactor() {

//                for(List<TSolve> list : mind.getRightSolves().values()) {
//                    for(TSolve s : list) {
//                        for(TValue v : s.getSolve()) {
//                            v.getTVar().setCurrent(null);
//                        }
//                    }
//                }
//
//                for (TVariable v : tvars) {
//                    v.setCurrent(null);
//                }

                    //                rotateRights(0, mind.getRightSolves(), new IReactor() {
                    @Override
                    public Object run(Object o) {
                        boolean result = false;
//                            boolean logging = (boolean) o;

                        try {

//                            for (List<Domain> tree : r.getTree()) {
//
//                                final List<Domain> t = tree;

//                            Map<Right, List<Object[]>> variants = new HashMap<>();


                            if (linkDomains(t, rightList, causes, logging)) {
                                result = true;
                            }
                            if (calcFunctions(t, causes, logging)) {
                                result = true;
                            }

                            if (linkDatabase(t, causes, /*(SortedSet<TVariable>) o*/ tvars, logging)) {
                                result = true;
                            }

//                            }

//                            if(variants.containsKey(r)) {


                        } catch (Exception e) {
                            e.printStackTrace(System.err);
                            result = false;
                        }

                        return result;
                    }
                });
            }

            updateDatabase(logging);

//            if (!mind.getRightSolves().isEmpty()) {
//                System.err.println("========================================");
//                for (Map.Entry<TVariableSet, List<TSolve>> e : mind.getRightSolves().entrySet()) {
//                    for (TSolve s : e.getValue()) {
//                        if (!s.getSolve().isEmpty()) {
//                            System.err.println("---- " + " : " + s);
//                        } else {
//                            //TODO: Убрать
//                            System.err.println("?");F
//                        }
//                    }
//                }
//            }

            if (!wasUsed && r.isUsed(mind)) {
                used = true;
            }
        }

        return used;
    }

//    private boolean rotateRights(int pos, Map<TVariableSet, List<TSolve>> solves, IReactor runnable) throws Exception {
//        boolean result = false;
//        if (pos >= solves.size()) {
//            result = (boolean) runnable.run(null);
//        } else {
//            TVariableSet key = new ArrayList<>(solves.keySet()).get(pos);
//            List<TSolve> tv = solves.get(key);
//            int i = -1;
//            while (++i < tv.size()) {
//                TSolve s = tv.get(i);
//                Set<TVariable> temp = s.activate();
//                if (rotateRights(++pos, solves, runnable)) {
//                    result = true;
//                }
//                if (temp != null) {
//                    if (temp != null) {
//                        for (TVariable t : temp) {
//                            t.setCurrent(null);
//                        }
//                    }
//                }
//            }
////            if (!result[1]) {
////                if (rotateRights(++pos, solves, runnable)) {
////                    result[0] = true;
////                }
////            }
//
//        }
//
//        return result;
//    }

//    private List<TValue> getTSolves(TValue v) throws Exception {
//        List<TValue> list = new ArrayList<>();
//        Right r = v.getTVar().getRight();
//        for(TSolve s : r.getTSolves()) {
//            if(s.getValue(v.getTVar()).getValue().getId() == v.getId()) {
//                list.addAll(s.getSolve());
//                break;
//            }
//        }
//
//    }

    private boolean rotateVariables(SortedSet<TVariable> tvars, SortedSet<TVariable> base, IReactor runnable) throws Exception {
        final boolean[] result = new boolean[]{false, false};
        if (tvars.isEmpty()) {
            result[0] = (boolean) runnable.run(tvars);
        } else {
            final TVariable t = tvars.last();
//            result[1] = true;

//            t.setCurrent(null);
//            if (rotateVariables(tvars.headSet(t), base, runnable)) {
//                result[0] = true;
//            }

            mind.getTValues().forEach(t, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
//                    if (!((TValue) o).getValue().isCVariable() || !((TValue) o).getValue().getSlaves().isEmpty()) {
                    result[1] = true;
                    t.setCurrent((TValue) o);
                    if (isValidFor(base.tailSet(t))) {
//                    mind.getTValues().set(t, (TValue) o);
                        if (rotateVariables(tvars.headSet(t), base, runnable)) {
                            result[0] = true;
                        }
                    }
//                    }
                    return true;
                }
            });


            if (!result[1]) {
//                if(isValidFor(base.tailSet(t))) {
                if (rotateVariables(tvars.headSet(t), base, runnable)) {
                    result[0] = true;
                }
//                }
            }
        }
        return result[0];
    }

//    private boolean isValidFor(TValue[] subst) throws Exception {
//        boolean nulls = true;
//        for (TValue v : subst) {
//            if (v != null) {
//                nulls = false;
//                break;
//            }
//        }
//        if (subst.length > 1 && !nulls) {
//            for (TVariableSet key : mind.getRightSolves().keySet()) {
//                for (TSolve s : mind.getRightSolves().get(key)) {
//                    if (s.isValid(subst)) {
//                        return true;
//                    }
//                }
//            }
//            return false;
//        }
//        return true;
//    }

    private boolean isValidFor(SortedSet<TVariable> tail) {
        final TVariable t = tail.first();
        boolean found = false;
        boolean result = false;
        if (tail.size() > 1) {
            for (TVariableSet key : mind.getRightSolves().keySet()) {
                if (key.contains(t)) {
                    found = true;
                    boolean success = false;
                    for (TSolve s : mind.getRightSolves().get(key)) {
                        if (s.containsTValue(t.getCurrent())) {
                            if (s.size() > 1) {
                                boolean complete = true;
                                for (TVariable x : tail) {
                                    if (x.getId() != t.getId()) {
                                        if (s.containsTVar(x)) {
                                            if (!s.containsTValue(x.getCurrent())) {
                                                complete = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (complete) {
                                    success = true;
                                    break;
                                }
                            } else {
                                success = true;
                                break;
                            }
                        } else if (s.size() == 1) {
                            success = true;
                            break;
                        }
                    }
                    if (success) {
                        result = true;
                        break;
                    }
                }
            }
        }
//        if(!(!found || result)) {
//            System.err.println(t);
//        }
        return !found || result;
    }

    private boolean linkDomains(List<Domain> treeSlave, Collection<Right> rightList, Map<Right, Set<Cause>> causes, boolean logging) throws Exception {

        Map<Solve, List<Object[]>> variants = new HashMap<>();
        boolean result = false;

        if (treeSlave.size() == 1) {
            for (Domain slave : treeSlave) {
                for (Right right : rightList /*mind.getRights()*/) {
//                    if (right.isDeleted() || !right.getPredicates().contains(slave.getPredicateId())) {
//                        continue;
//                    }

//                    System.err.println("\t" + right.getId() + ": " + right);


//                                                        TValue s = mind.getTValues().find(slave.get(i).getT(mind), tm);


                    for (List<Domain> treeMaster : right.getTree()) {
                        for (Domain master : treeMaster) {

                            if (master.getPredicateId() == slave.getPredicateId() && master.isAntc() != slave.isAntc()) {

                                //TODO: Костыль
//                                master.setMind(mind);
//                                slave.setMind(mind);

                                TValue[] substMaster = new TValue[master.getRange()];
                                TValue[] substSlave = new TValue[slave.getRange()];

//                                master.recalculate(true);
//                                slave.recalculate(true);

                                mind.getTValues().mark();
                                mind.getFValues().mark();

                                boolean success = true;
                                boolean applied = false;

                                // Отсечение несовпадений по константам

                                boolean blockRight = false;
                                boolean blockLeft = false;

//                                boolean constFound = false;
//                                boolean cvarFound = false;
//                                boolean clear = false;

                                boolean acceptable = false;
                                for (int i = 0; i < master.getRange(); ++i) {
                                    if (master.get(i).isTSet() /*&& !blockByOrder(i, master, slave)*/) {
                                        acceptable = true;
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isXVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).isCVariable()) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId()) {
                                    } else {
//                                        if(slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                            cvarFound = true;
//                                        } else {
//                                            clear = true;
//                                        }
                                        blockRight = true;
//                                        break;
                                    }
//                                            || slave.get(i).isEmpty(mind)
////                                            || slave.get(i).getValue(mind).isCVariable()
//                                            || (!slave.get(i).isEmpty(mind)
//                                            && !master.get(i).isEmpty(mind)
//                                            && (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()
////                                            || (master.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId())
////                                            || (slave.get(i).getValue(mind).isXVariable() && slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId())
////                                            || master.get(i).getValue(mind).getChilds().contains(slave.get(i).getValue(mind).getId())
////                                            || slave.get(i).getValue(mind).getChilds().contains(master.get(i).getValue(mind).getId())
//                                    ))) {
////                                        if(slave.get(i).getType() == ArgumentType.TERM && !slave.get(i).getValue(mind).isCVariable()) {
////                                            constFound = true;
////                                        }
////                                        if(!slave.get(i).isEmpty(mind) && slave.get(i).getValue(mind).isCVariable()) {
////                                            cvarFound = true;
////                                        }
//                                    } else {
//                                        blockRight = true;
//                                        break;
//                                    }
                                }

                                if (acceptable) {
                                    blockRight = false;
                                }

//                                if(!blockRight && cvarFound && !constFound) {
//                                    blockRight = true;
//                                }

//                                constFound = false;
//                                cvarFound = false;
                                acceptable = false;
                                for (int i = 0; i < slave.getRange(); ++i) {
                                    if (slave.get(i).isTSet() /*&& !blockByOrder(i, slave, master)*/) {
                                        acceptable = true;
                                    } else if (slave.get(i).isEmpty(mind) || master.get(i).isEmpty(mind)) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isXVariable()) {
//                                    } else if (slave.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).isCVariable()) {
                                    } else if (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId()) {
//                                    } else if (slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId()) {
                                    } else {
//                                        if(slave.get(i).getValue(mind).isCVariable() && master.get(i).getValue(mind).isCVariable()) {
//                                            cvarFound = true;
//                                        } else {
//                                            clear = true;
//                                        }
                                        blockLeft = true;
//                                        break;
                                    }

//                                    if (slave.get(i).isTSet() //&& !blockByOrder(i, slave, master))
//                                            || master.get(i).isEmpty(mind)
////                                            || master.get(i).getValue(mind).isCVariable()
//                                            || (!slave.get(i).isEmpty(mind)
//                                            && !master.get(i).isEmpty(mind)
//                                            && (master.get(i).getValue(mind).getId() == slave.get(i).getValue(mind).getId()
////                                            || (slave.get(i).getValue(mind).isXVariable() && slave.get(i).getValue(mind).getParent().getId() == master.get(i).getValue(mind).getId())
////                                            || (master.get(i).getValue(mind).isXVariable() && master.get(i).getValue(mind).getParent().getId() == slave.get(i).getValue(mind).getId())
////                                            || master.get(i).getValue(mind).getChilds().contains(slave.get(i).getValue(mind).getId())
////                                            || slave.get(i).getValue(mind).getChilds().contains(master.get(i).getValue(mind).getId())
//                                    ))) {
////                                        if(master.get(i).getType() == ArgumentType.TERM && !master.get(i).getValue(mind).isCVariable()) {
////                                            constFound = true;
////                                        }
////                                        if(!master.get(i).isEmpty(mind) && master.get(i).getValue(mind).isCVariable()) {
////                                            cvarFound = true;
////                                        }
//                                    } else {
//                                        blockLeft = true;
//                                        break;
//                                    }
                                }

                                if (acceptable) {
                                    blockLeft = false;
                                }

//                                if(!blockLeft && cvarFound && !constFound) {
//                                    blockLeft = true;
//                                }

//                                    } !slave.get(i).isTSet()
//                                            && (!master.get(i).isFSet() || !master.get(i).getF(mind).isEmpty())
//                                            && (!slave.get(i).isFSet() || !slave.get(i).getF(mind).isEmpty())
//                                            && (!slave.get(i).isCVar(mind) || !slave.get(i).getValue(mind).getSlaves().isEmpty())
//                                            && (!master.get(i).isCVar(mind) || !master.get(i).getValue(mind).getSlaves().isEmpty())
//                                            && (master.get(i).isEmpty(mind)
//                                            || slave.get(i).isEmpty(mind)
//                                            || master.get(i).getValue(mind).getId() != slave.get(i).getValue(mind).getId())) {
//                                        success = false;
//                                        break;
//                                    }


//                                if(blockRight && blockLeft && cvarFound && !clear) {
//                                    System.err.println(master + " <- " + slave);
////                                    for (int i = 0; i < slave.getRange(); ++i) {
////                                        if (master.get(i).isTSet() && !slave.get(i).isEmpty(mind) && !slave.get(i).getValue(mind).isCVariable()) {
////                                            blockRight = false;
////                                        }
////                                        if (slave.get(i).isTSet() && !master.get(i).isEmpty(mind) && !master.get(i).getValue(mind).isCVariable()) {
////                                            blockLeft = false;
////                                        }
////                                    }
//
//                                }

                                Set<Right> usedRights = new HashSet<>();

                                if (success) {

//                                    boolean masterComplete = master.isComplete(); // && master.isQuery();
//                                    boolean slaveComplete = slave.isComplete(); // && slave.isQuery();

//                                    System.err.println(blockLeft + " " +master + " <- " + blockLeft + " " + slave);

                                    for (int i = 0; i < slave.getRange(); ++i) {

                                        // Подстановка снизу вверх
//                                        if(slaveComplete) {
                                        if (!blockRight) {
                                            if (master.get(i).isTSet() /*&& master.get(i).isEmpty(mind)*/) {
                                                if (!slave.get(i).isEmpty(mind)) {

//                                            if (master.getVarOrder(mind, i) < slave.getVarOrder(mind, i)) {
//                                                    if (blockByOrder(i, master, slave)) {
//                                                System.err.println(slave + " -> " + master);
//                                                    } else {
//                                            if(master.getRightId() == slave.get(i).getValue(mind).getRightId()) {
//                                                System.err.println("=== " + master.getVarOrder(i) + ":" + master + " <-- " + slave.getVarOrder(i) + ":" + slave);
//                                            }

//                                            long parentId = -1;

                                                    Term tm = slave.get(i).getValue(mind);
                                                    TVariable t = master.get(i).getT(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && slave.getRightId() == tm.getRightId() && tm.getSlaves().isEmpty() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        s = mind.getTValues().getXValue(t, tm);
                                                        if (s == null) {
                                                            tm = mind.getTerms().createXVar(tm);
                                                        }
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }
                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
                                                        usedRights.add(master.getRight());
//                                                s.setParentId(parentId);
                                                        result = true;
                                                    }

//                                                    if (master.get(i).isEmpty(mind)) {
//                                                        master.get(i).getT(mind).setCurrent(s);
//                                                    }

                                                    substMaster[i] = s;
                                                    slave.setUsed(mind);
                                                    master.setUsed(mind);
                                                    applied = true;
                                                }
//                                            } else if(slave.isQuery()){
//                                                success = false;
//                                                break;
                                            }
                                        }
//                                        }
//                                        }


                                        // Подстановка сверху вниз
//                                        if(masterComplete) {
                                        if (!blockLeft) {
                                            if (slave.get(i).isTSet() && slave.get(i).isEmpty(mind)) {
                                                if (!master.get(i).isEmpty(mind)) {

//                                            if (slave.getVarOrder(mind, i) < master.getVarOrder(mind, i)) {
//                                                    if (blockByOrder(i, slave, master)) {
//                                                System.err.println(master + " -> " + slave);
//                                                    } else {

//                                            if(slave.getRightId() == master.get(i).getValue(mind).getRightId()) {
//                                                System.err.println("=== " + slave.getVarOrder(i) + ":" + slave + " <-- " + master.getVarOrder(i) + ":" + master);
//                                            }

//                                            long parentId = -1;
                                                    Term tm = master.get(i).getValue(mind);
                                                    TVariable t = slave.get(i).getT(mind);
                                                    TValue s = null;
                                                    if (tm.isCVariable() && master.getRightId() == tm.getRightId() && tm.getSlaves().isEmpty() /*&& tm.getRight().isSubstitutable()*/ /*&& tm.getSlaves().contains(t.getId())*/) {
                                                        s = mind.getTValues().getXValue(t, tm);
                                                        if (s == null) {
                                                            tm = mind.getTerms().createXVar(tm);
                                                        }
                                                    } else {
                                                        s = mind.getTValues().find(t, tm);
                                                    }

//                                                        TValue s = mind.getTValues().find(slave.get(i).getT(mind), tm);
                                                    if (s == null) {
                                                        s = mind.getTValues().add(t, tm);
                                                        usedRights.add(slave.getRight());
//                                                s.setParentId(parentId);
                                                        result = true;
                                                    }

//                                                    if (slave.get(i).isEmpty(mind)) {
//                                                        slave.get(i).getT(mind).setCurrent(s);
//                                                    }

                                                    substSlave[i] = s;
                                                    master.setUsed(mind);
                                                    slave.setUsed(mind);
                                                    applied = true;
                                                }
//                                            } else if(master.isQuery()){
//                                                success = false;
//                                                break;
                                            }
                                        }
//                                        }
//                                        }

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
//                                            if (master.get(i).isEmpty(mind) || slave.get(i).isEmpty(mind)
//                                                    || master.get(i).getValue(mind).getId() != slave.get(i).getValue(mind).getId()) {
//                                                success = false;
//                                                break;
//                                            } else {
                                            substMaster[i] = null;
                                            substSlave[i] = null;
//                                            }
//                                        } else {
//                                            master.getRight().setUsed();
                                        }

                                    }
                                }

                                //TODO: Нужно исключить подстановки в занятые места
//                                if(success && (!isValidFor(substMaster) || !isValidFor(substSlave))) {
//                                    success = false;
//                                }

                                if (success) {
                                    if (result) {
                                        ++solvedPasses;
                                        mind.getTValues().commit();
                                        mind.getFValues().commit();
                                    } else {
                                        ++dumpedPasses;
                                    }
                                    markExcluded(result, substMaster, master, slave, causes, variants, logging);
                                    markExcluded(result, substSlave, slave, master, causes, variants, logging);

                                    master.getRight().setUsed(mind);
                                    slave.getRight().setUsed(mind);
//                                    for(Right r : usedRights) {
//                                        r.setUsed();
//                                    }
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

            for (Map.Entry<Solve, List<Object[]>> variantsList : variants.entrySet()) {
                for (Object[] subst : variantsList.getValue()) {
                    List<TValue> list = new ArrayList<>();
                    for (Object x : subst) {
                        if (x == null) {
                        } else if (x instanceof TValue) {
                            list.add((TValue) x);
                        }
                    }
                    TSolve s = mind.addTSolve(list);
                    s.setVariant(variantsList.getKey());
                }
            }

        }
        return result;
    }

//    private boolean blockByOrder(int pos, Domain master, Domain slave) throws Exception {
////        if (slave.get(pos).getValue(mind).isCVariable()) {
////            if (master.getRightId() == slave.get(pos).getValue(mind).getRightId()) {
////                return master.get(pos).getT(mind).getIndex() < slave.get(pos).getValue(mind).getIndex();
//////            } else if (master.isQuery()){
////            } else if (!slave.get(pos).isTSet()) {
//        master.calcVarOrders(mind);
//        return master.getVarOrder(mind, pos) < slave.getVarOrder(mind, pos);
////            }
////        }
////        return false;
//    }

//    private TSolve findTSolve(List<TValue> list) {
//        TSolve tmp = new TSolve(list, mind);
//            for (TSolve t : mind.getRightSolves()) {
//                if (tmp.equalsTo(t)) {
//                    return t;
//                }
//            }
//        return null;
//    }
//
//    public TSolve addTSolve(List<TValue> list) {
//        TSolve tmp = findTSolve(list);
//        if (tmp != null) {
//            return tmp;
//        } else {
//            tmp = new TSolve(list, mind);
//            mind.getRightSolves().add(tmp);
//            return tmp;
//        }
//    }

    private boolean markExcluded(boolean result, TValue[] subst, Domain master, Domain slave, Map<Right, Set<Cause>> causes, Map<Solve, List<Object[]>> variants, boolean logging) throws Exception {
        Right r = null;
        boolean occurrs = false;


        List<TValue> list = new ArrayList<>();
        for (int i = 0; i < slave.getRange(); ++i) {
            if (subst[i] != null) {
                if (subst[i] instanceof Collection) {
                    list.addAll((Collection<TValue>) subst[i]);
                } else {
                    list.add(subst[i]);
                }
            }
        }
        for (TValue v : list) {
//            if (!master.isExcluded(slave.getArguments())) {
            r = v.getTVar().getRight();
            if (logging && result) {
                mind.getLog().add(LogMode.ANALIZER, "Closed: " + v);
            }
            occurrs = true;
//            }
        }


//        boolean complete = true;
//        List<TValue> list = new ArrayList<>();
//        for (int i = 0; i < slave.getRange(); ++i) {
//            if (subst[i] != null) {
//                if (subst[i] instanceof Collection) {
//                    list.addAll((Collection<TValue>) subst[i]);
//                } else {
//                    list.add((TValue) subst[i]);
//                }
//            } else {
//                complete = false;
//            }
//        }


//        if (!list.isEmpty()) {
//
//            // Отсечение конфликтных подстановок
//            for (int i = 0; i < list.size(); ++i) {
//                for (int j = 0; j < list.size(); ++j) {
//                    if (list.get(i).getTVar().getId() == list.get(j).getTVar().getId() && list.get(i).getValue().getId() != list.get(j).getValue().getId()) {
//                        return false;
//                    }
//                }
//            }
//
////            boolean caused = false;
//            TSolve t = mind.findTSolve(list);
//            Cause s = new Cause(mind, master, slave);
////            if (!t.getCauses().contains(s)) {
////                t.getCauses().add(s);
////                caused = true;
////            }
//
//            if (t == null || !t.getCauses().contains(s) || !master.isExcluded(slave.getArguments())) {
//                r = master.getRight();
//                if (t == null) {
//                    t = mind.addTSolve(list);
//                }
//                if (!t.getCauses().contains(s)) {
//                    t.getCauses().add(s);
//                    if (!causes.containsKey(r)) {
//                        causes.put(r, new HashSet<>());
//                    }
//                    causes.get(r).add(s);
//                    if (logging) {
//                        mind.getLog().add(LogMode.ANALIZER, "Closed: " + t);
//                    }
//                    occurrs = true;
//                }
//            }
//        }
//            }
//        }
        if (r != null) {

            if (occurrs /*&& !result*/) {
                Cause s = new Cause(mind, master, slave);
                if (!causes.containsKey(r)) {
                    causes.put(r, new HashSet<>());
                }
                causes.get(r).add(s);
            }

//            mind.addTSolve(list);

            master.setExcluded(slave.getArguments(), mind);
            if (!variants.containsKey(master)) {
                variants.put(master, new ArrayList<>());
            }
            variants.get(master).add(subst);

            if (occurrs && result && logging) {
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


    private void logBranch(List<Domain> tree, boolean logging) {
        if (!tree.isEmpty() && logging) {
            mind.getLog().add(LogMode.ANALIZER, "With branch:");

            for (Domain d : tree) {
                mind.getLog().add(LogMode.ANALIZER, "\t" + d.toString());
            }
        }
    }

    private boolean linkDatabase(List<Domain> tree, Map<Right, Set<Cause>> causes, Set<TVariable> tvars, boolean logging) throws Exception {

        boolean result = false;
        boolean occurs = false;


//        long tag = mind.getTValues().incTag();

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
                            if (master.get(i).isTSet() /*&& master.getVarOrder(mind, i) >= d.getVarOrder(mind, i)*/) {
//                            } else if(master.get(i).isEmpty(mind) || d.get(i).isEmpty(mind)) {
                            } else if (master.get(i).getValue(mind).getId() == d.get(i).getValue(mind).getId()) {
                            } else {
//                                    || master.get(i).getValue(mind).isCVariable()) {
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

                if ("rule(1)".equals(d.getPredicate().toString()) && !d.get(0).isEmpty(mind) && d.get(0).getValue(mind).getType() == DataType.NUMERIC) {
                    d.setProduced(mind);
                    mind.getLog().add(LogMode.STORAGE, "DB assumed record (r): " + d);
                    occurs = true;
//                    calculated.add(d);
                } else if (d.isCalculated(mind)) {
                    calculated.add(d);
                } else if (d.isSystem() || !d.isComplete()) {
                    excluded.clear();
                    candidates.clear();
                    break;
                } else if (d.isExcluded(mind)) {
                    excluded.add(d);
                } else {
                    candidates.add(d);
                }
                if (d.isStored(mind)) {
                    stored.add(d);
                }
            }


            //TODO: Интересно. Сделать репликацию c-переменных
//            if (excluded.size() > 1) {
//                boolean cFound = false;
//                boolean xFound = false;
//                for (Domain d : excluded) {
//                    if (!d.getArguments().getCVariables(mind).isEmpty()) {
//                        cFound = true;
//                    } else {
//                        xFound = true;
//                    }
//                }
//                if (cFound && xFound) {
////                    excluded.clear();
//                }
//            }

            if (candidates.size() == 1) {
                Domain d = candidates.toArray(new Domain[]{})[0];
                occurs = true;
                if (!d.isStored(mind) && (d.setCauses(causes.get(d.getRight()), mind) || !calculated.isEmpty() || !excluded.isEmpty())) {

//                    System.err.println();
//                    System.err.println(d);
//                    System.err.println(d.getArguments().getTValues(mind, true));
//                    TVariableSet ts = new TVariableSet(d.getArguments().getTValues(mind, true));
////                    for(List<TSolve> lst : mind.getRightSolves().values()) {
//                        if(mind.getRightSolves().containsKey(ts)) {
//                            for (TSolve s : mind.getRightSolves().get(ts)) {
//                                System.err.println("---  " + s);
//                            }
//                        }
////                    }

                    if (isValid(d) && d.getArguments().getCVariables(mind).isEmpty()) {
                        result = true;
                        d.setProduced(mind);
//                        d.setTag(tag);
//                    d.setCauses(causes.get(d.getRight()));
                        d.setSolves(solve, mind);
                        if (logging) {
                            logBranch(tree, logging);
                            mind.getLog().add(LogMode.STORAGE, "DB assumed record: " + d);
                            logCauses(LogMode.STORAGE, d);
                        }
                    }
                }
            } else if (!excluded.isEmpty() && candidates.isEmpty() && stored.isEmpty()) {
                occurs = true;
                for (Domain d : excluded) {
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRight()), mind)) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag = mind.getTValues().incTag());
//                        d.setCauses(causes.get(d.getRight()));
                            d.setSolves(solve, mind);
                            if (logging) {
                                logBranch(tree, logging);
                                mind.getLog().add(LogMode.STORAGE, "DB assumed record (x): " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
                }
                //TODO: Сомнительно, но вроде работает с ?$x $y index(qwerty) -> index(x), y : x;
            } //else
            if (!calculated.isEmpty() && candidates.isEmpty() /*&& tree.size() - excluded.size() == calculated.size()*/) {
                occurs = true;
                for (Domain d : calculated) {
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored(mind) /*|| d.isQuery()*/) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag = mind.getTValues().incTag());
                            d.setCauses(causes.get(d.getRight()), mind);
                            d.setSolves(solve, mind);
                            if (logging) {
                                logBranch(tree, logging);
                                mind.getLog().add(LogMode.STORAGE, "DB assumed record (c): " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
                        }
                    }
                }
            }

            if (!occurs && !assumed.isEmpty() && tree.size() > 1) {
                candidates.clear();
                excluded.clear();
                for (Domain d : tree) {
                    if (d.isComplete() && !d.isCalculated(mind) && !d.isSystem() && !assumed.contains(d)) {
                        if (!d.isExcluded(mind)) {
                            candidates.add(d);
                        } else {
                            excluded.add(d);
                        }
                    }
                }
                if (candidates.size() == 1 && !excluded.isEmpty()) {
                    Domain d = candidates.toArray(new Domain[]{})[0];
//                    d.setCauses(causes.get(d.getRight()));
                    if (!d.isStored(mind) && d.setCauses(causes.get(d.getRight()), mind)) {
                        if (isValid(d)) {
                            result = true;
                            d.setProduced(mind);
//                            d.setTag(tag);
//                        d.setCauses(causes.get(d.getRight()));
                            d.setSolves(solve, mind);
                            if (logging) {
                                logBranch(tree, logging);
                                mind.getLog().add(LogMode.STORAGE, "DB assumed record (a): " + d);
                                logCauses(LogMode.STORAGE, d);
                            }
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
        if (d.getCauses(mind) != null) {
            for (Cause c : d.getCauses(mind)) {
                if (!rightShowed) {
                    mind.getLog().add(mode, "\tFrom right: " + c.getRight(mind));
                    rightShowed = true;
                }


                mind.getLog().add(mode, "\t\tUsing: " + c.getDonor().toString(mind));
//                mind.getLog().add(mode, "\t\tUsing: " + mind.getRights().find(c.getDonor()).getDomain().toString());
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
                d.getArguments().applyStamp(mind, args);
                for (int i = 0; i < d.getRange(); ++i) {
                    if (d.getArguments().get(i).isFSet() && d.getArguments().get(i).getF(mind).isCalculable() && d.getArguments().get(i).getF(mind).isEmpty()) {
                        d.getArguments().get(i).getF(mind).clear();
                        mind.getCalculator().calculate(d.getArguments().get(i).getF(mind), logging);
                    }
                }
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
//                    for(Term t : d.getArguments().getCVariables(mind)) {
//                        t.toCVariable();
//                    }

                    x = d.createStored(mind);
                    if (d.isUsed(mind)) {
                        x.getDomain().setUsed(mind);
                    }
                    if (logging) {
                        mind.getLog().add(LogMode.STORAGE, "DB add record: " + d + " -> " + x);
                    }
//                }

                    if (d.isCalculated(mind)) {
                        x.getDomain().setCalculated(mind);
                    }
                    if (d.getCauses(mind) != null) {
                        x.getCauses().clear();
                        x.getCauses().addAll(d.getCauses(mind));

                        //TODO: XPRMNT
//                        x.getDomain().setCauses(d.getCauses());

//                    for(Cause c : x.getCauses()) {
//                        if(!c.getDst().isStored()) {
//                            c.getDst().createStored();
//                        }
//                    }
                    }
                    if (d.getSolves(mind) != null) {
                        x.getSolves().clear();
                        x.getSolves().addAll(d.getSolves(mind));
                    }

//                    if(!rightList.contains(x)) {
//                        rightList.add(x);
//                    }
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
            for (Function f : d.getArguments().getFunctions(mind)) {
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
        List<List<TValue>> solves = new ArrayList<>();
        for (Domain d : tree) {
            if (d.isSystem()) {

//                d.pushValues();

//                List<TValue> list = new ArrayList<>();
//                for (TVariable t : d.getArguments().getTVariables(true)) {
//                    list.add(t.getCurrent());
//                }

                int res = d.execSystem(mind);
                for (Argument a : d.getArguments()) {
                    if (a.isEmpty(mind)) {
                        res = -2;
                        break;
                    }
                }

                if (res == 0) {
                    if (d.isAntc()) {
                        d.setCalculated(mind);
                        success = true;
                    } else {
                        block = true;
                    }
                } else if (res == 1) {
                    if (!d.isAntc()) {
                        d.setCalculated(mind);
                        success = true;
                    } else {
                        block = true;
                    }
                }

                if (block && logging) {
                    mind.getLog().add(LogMode.ANALIZER, "Blocker: " + d.toString());
                }
                if (!block & d.isComplete()) {
                    List<TValue> list = new ArrayList<>();
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        list.add(t.getCurrent());
                    }
//                    Right r = d.getRight();
                    if (!list.isEmpty()) {
                        solves.add(list);
                    }
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
                if (d.isSystem() && !d.isCalculated(mind)) {
                    block = true;
                }
            }
            if (block) {
                for (Domain d : tree) {
                    if (d.isCalculated(mind)) {
                        d.unCalculated(mind);
                    }
                }
            } else if (!solves.isEmpty()) {
                for (List<TValue> list : solves) {
                    mind.addTSolve(list);
                }
            }
        }

        return !block;
    }

    private boolean isValid(Domain d) throws Exception {
        return true;

//        List<TValue> list = d.getArguments().getTValues(mind, true);
//        TVariableSet ts = new TVariableSet(list);
////        System.err.println(d);
//        if(mind.getRightSolves().containsKey(ts)) {
//            for (TSolve s : mind.getRightSolves().get(ts)) {
////                System.err.println("== " + d);
//                if(s.contains(list)) {
//                    return true;
//                }
//            }
//        }
//
////        System.err.println("-- " + d);
//        return false;
    }
}

//TODO Запрсы +(insert), -(delete), =(update)
//TODO счетчик ссылок для сборки мусор
//TODO time для каждой записи в базе
//TODO id для каждой зависи в базе??
