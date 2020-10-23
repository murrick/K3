package org.kanger;

import org.kanger.enums.LogMode;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Hypotese;
import org.kanger.units.Domain;
import org.kanger.units.Right;
import org.kanger.units.TValue;

import java.util.HashSet;
import java.util.Set;

// !@x a(x) -> b(x), @y b(y) -> c(y), @z c(z) -> d(z);

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 */
public class Analiser {


    private final Mind mind;

    public Analiser(Mind mind) {
        this.mind = mind;
    }


    public boolean analise(Right right, boolean logging) throws Exception {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

        if (logging) {
            mind.getLog().add(LogMode.ANALIZER, "============= ANALISER ====================");
        }

        mind.getSolutions().clear();
        mind.getValues().clear();

        result = checkDatabase(null /*right*/, logging);

        if (!result) {

            boolean occurs = false;
            for (Right r : mind.getRights()) {
                if (r.isStored() && !r.isDeleted()) {
                    if (r.getMindId() != mind.getId()) {
                        break;
                    }
                    Domain d = r.getDomain();
                    for (Argument a : d.getArguments()) {
                        if (a.isEmpty(mind) || (a.getValue(mind).isCVariable() && a.getValue(mind).getMindId() != mind.getId())) {
                            d = null;
                            break;
                        }
                    }
                    if (d != null && !d.isQuery(mind)
                            && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                        Hypotese h = mind.getHypotesisStore().add(!d.isAntc(), d.isQuery(mind), d.getPredicate(), d.getArguments());
                        occurs = true;
                        if (logging) {
                            mind.getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
                        }
                    }
                }
            }

            if (occurs && logging) {
                mind.getLog().add(LogMode.ANALIZER, "===========================================");
            }
        }

        if (logging) {
            mind.getLog().add(LogMode.TIMING, "* Analising time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
        }
        return result;
    }


    private boolean checkRight(Right p, Set<Right> orfans, Set<Long> list, boolean logging) throws Exception {
        boolean result = false;
        if (p.getDomain().isCalculated(mind)) {

            boolean valid = p.getDomain().isQuery(mind);
            if (!valid) {
                for (TValue v : p.getSolves()) {
                    if (v.getTVar().isQuery(mind)) {
                        valid = true;
                        break;
                    }
                }
            }

            if (valid) {
                mind.getValues().add(p.getSolves());
            }

            if (logging) {
                mind.getLog().add(LogMode.ANALIZER, "Calculated coincidence: ");
                mind.getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                mind.getLog().add(LogMode.ANALIZER, "===========================================");
            }
            result = true;
        } else {
//            if (p.getDomain().getArguments().get(2).getValue(mind).getType() == DataType.NUMERIC &&
//                    (1011.0 == (double) p.getDomain().getArguments().get(2).getValue(mind).getValue())) {
//                System.err.println("!");
//            }

//            boolean trigger = false;
            for (Right q : mind.getRights()) {

//                if (p.getDomain().equalsBase(q.getDomain())
//                        && p.getDomain().isAntc() != q.getDomain().isAntc()) {
//                    System.err.println("!!");
//                }

                if (q.isDeleted() || !q.isStored() || (list == null && q.getId() > p.getId()) || (list != null && list.contains(q.getId()))) {
                    continue;
                }

//                System.err.println(p.getId() + " --- " + q.getId());
//                if(q.getId() == p.getId()) {
//                    trigger = true;
//                }

                if (p.getDomain().equalsBase(q.getDomain())
                        && p.getDomain().isAntc() != q.getDomain().isAntc()) {
                        //&& p.getDomain().getArguments().getCVariables(mind).size() != p.getDomain().getRange()) {

                    //TODO: Костыль
                    if (q.getMind() == null) {
                        q.setMind(mind);
                    }
                    if (p.getDomain().isQuery(mind) && p.getDomain().getArguments().getCVariables(mind).isEmpty()) {
                        mind.getSolutions().add(q);
                        mind.getValues().add(p.getSolves());
                    } else if (q.getDomain().isQuery(mind) && q.getDomain().getArguments().getCVariables(mind).isEmpty()) {
                        mind.getSolutions().add(p);
                        mind.getValues().add(q.getSolves());
                    }

                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Database coincidence: ");
                        mind.getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                        mind.getLog().add(LogMode.ANALIZER, "\t" + q.toString());
                        mind.getLog().add(LogMode.ANALIZER, "===========================================");
                    }
                    result = true;

                }
            }

            if (!result && p.getDomain().isQuery(mind) && !p.getDomain().isUsed(mind)) {
                orfans.add(p);
            }
        }
        return result;
    }

//    private boolean equalsCVars(Domain p, Domain q) throws Exception {
//        for (int i = 0; i < p.getArguments().size(); ++i) {
//            if(p.get(i).isCVar(mind) && q.get(i).isCVar(mind)) {
//                if(p.get(i).getValue(mind).getRightId() == q.get(i).getValue(mind).getRightId()) {
//
//                }
//            } else {
//                return true;
//            }
//        }
//    }

    public boolean checkDatabase(Set<Long> list, boolean logging) throws Exception {

        boolean result = false;
        boolean calculated = false;

        Set<Right> orfans = new HashSet<>();

        for (Right p : mind.getRights()) {
            if (!p.isDeleted() && p.isStored() && (list == null || list.contains(p.getId())) && checkRight(p, orfans, list, logging)) {
                if (p.getDomain().isCalculated(mind)) {
                    calculated = true;
                }
                result = true;
            }
        }

        // Контроль закрытия всех веток запроса
        if (!orfans.isEmpty() && !calculated) {
            result = false;
            if (logging) {
                for (Right r : orfans) {
                    mind.getLog().add(LogMode.ANALIZER, "Unresolved: \t" + r.getDomain().toString());
                }
                mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return result;
    }
}
