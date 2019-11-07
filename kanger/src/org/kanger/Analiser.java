package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUser;
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


    private final IUser user;

    public Analiser(IUser user) {
        this.user = user;
    }


    public boolean analise(Right right, boolean logging) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        boolean result = false;
        int counter = 0;

        long start = System.currentTimeMillis();

        if (logging) {
            user.getMind().getLog().add(LogMode.ANALIZER, "============= ANALISER ====================");
        }

        user.getMind().getSolutions().clear();
        user.getMind().getValues().clear();

        result = checkDatabase(right, logging);

        if (!result) {

            boolean occurs = false;
            for (long id : user.getMind().getRights().getDatabase(-1)) {
                Right r = user.getMind().getRights().get(id);
                if (!r.isDeleted()) {
                    if (r.getMindId() < user.getMind().getId()) {
                        break;
                    }
                    Domain d = r.getDomain();
                    for (Argument a : d.getArguments()) {
                        if (a.isEmpty() || (a.getType() == ArgumentType.CVARIABLE && a.getValue().getMindId() == user.getMind().getId())) {
                            d = null;
                            break;
                        }
                    }
                    if (d != null && !d.isQuery()
                            && user.getMind().getHypotesisStore().find(!d.isAntc(), d.getPredicate(), d.getArguments()) == null) {
                        Hypotese h = user.getMind().getHypotesisStore().add(!d.isAntc(), d.isQuery(), d.getPredicate(), d.getArguments());
                        occurs = true;
                        if (logging) {
                            user.getMind().getLog().add(LogMode.ANALIZER, "Hypotesis assumed: " + d.toString());
                        }
                    }
                }
            }

            if (occurs && logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
            }
        }

        if (logging) {
            user.getMind().getLog().add(LogMode.TIMING, "* Analising time \t" + ((System.currentTimeMillis() - start) / 1000.0) + " sec");
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
                user.getMind().getValues().add(p.getSolves());
            }

            if (logging) {
                user.getMind().getLog().add(LogMode.ANALIZER, "Calculated coincidence: ");
                user.getMind().getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
            }
            result = true;
        } else {
            for (long iq : user.getMind().getRights().getDatabase(p.getId())) {
                Right q = user.getMind().getRights().get(iq);
                if (!q.isDeleted()
                        && p.getDomain().equalsBase(q.getDomain())
                        && p.getDomain().isAntc() != q.getDomain().isAntc()) {

                    if (p.getDomain().isQuery() && p.getDomain().getArguments().getCVariables(true).isEmpty()) {
                        user.getMind().getSolutions().add(q);
                        user.getMind().getValues().add(p.getSolves());
                    } else if (q.getDomain().isQuery() && q.getDomain().getArguments().getCVariables(true).isEmpty()) {
                        user.getMind().getSolutions().add(p);
                        user.getMind().getValues().add(q.getSolves());
                    }

                    if (logging) {
                        user.getMind().getLog().add(LogMode.ANALIZER, "Database coincidence: ");
                        user.getMind().getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                        user.getMind().getLog().add(LogMode.ANALIZER, "\t" + q.toString());
                        user.getMind().getLog().add(LogMode.ANALIZER, "===========================================");
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
        Set<Right> orfans = new HashSet<>();

        for (long id : user.getMind().getRights().getDatabase(-1)) {
            Right p = user.getMind().getRights().get(id);
            if (!p.isDeleted() && checkRight(p, orfans, logging)) {
                result = true;
            }
        }

        // Контроль закрытия всех веток запроса
        if (!orfans.isEmpty()) {
            result = false;
            if (logging) {
                for (Right r : orfans) {
                    user.getMind().getLog().add(LogMode.ANALIZER, "Unresolved: \t" + r.getDomain().toString());
                }
                user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
            }
        }
        return result;
    }
}
