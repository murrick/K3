package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Hypotese;
import org.kanger.units.Domain;
import org.kanger.units.Right;
import org.kanger.units.TValue;

import java.io.IOException;
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


    public boolean analise(Right right, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

        if (logging) {
            mind.getLog().add(LogMode.ANALIZER, "============= ANALISER ====================");
        }

        mind.getSolutions().clear();
        mind.getValues().clear();

        result = checkDatabase(right, logging);

        if (!result) {

            boolean occurs = false;
            for (long id : mind.getRights().getDatabase(-1)) {
                Right r = mind.getRights().get(id);
                if (!r.isDeleted()) {
                    if (r.getMindId() < mind.getId()) {
                        break;
                    }
                    Domain d = r.getDomain();
                    for (Argument a : d.getArguments()) {
                        if (a.isEmpty() || (a.getType() == ArgumentType.CVARIABLE && a.getValue().getMindId() == mind.getId())) {
                            d = null;
                            break;
                        }
                    }
                    if (d != null && !d.isQuery()
                            && mind.getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                        Hypotese h = mind.getHypotesisStore().add(!d.isAntc(), d.isQuery(), d.getPredicate(), d.getArguments());
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


    private boolean checkRight(Right p, Set<Right> orfans, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        boolean result = false;
        if (p.getDomain().isCalculated()) {

            boolean valid = p.getDomain().isQuery();
            if (!valid) {
                for (TValue v : p.getSolves()) {
                    if (v.getTVar().isQuery()) {
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
            for (long iq : mind.getRights().getDatabase(p.getId())) {
                Right q = mind.getRights().get(iq);
                if (!q.isDeleted()
                        && p.getDomain().equalsBase(q.getDomain())
                        && p.getDomain().isAntc() != q.getDomain().isAntc()) {

                    if (p.getDomain().isQuery() && p.getDomain().getArguments().getCVariables(true).isEmpty()) {
                        mind.getSolutions().add(q);
                        mind.getValues().add(p.getSolves());
                    } else if (q.getDomain().isQuery() && q.getDomain().getArguments().getCVariables(true).isEmpty()) {
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

            if (!result && p.getDomain().isQuery() && !p.getDomain().isUsed()) {
                orfans.add(p);
            }
        }
        return result;
    }

    public boolean checkDatabase(Right right, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {

        boolean result = false;
        boolean calculated = false;

        Set<Right> orfans = new HashSet<>();

        for (long id : mind.getRights().getDatabase(-1)) {
            Right p = mind.getRights().get(id);
            if (!p.isDeleted() && checkRight(p, orfans, logging)) {
                if (p.getDomain().isCalculated()) {
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
