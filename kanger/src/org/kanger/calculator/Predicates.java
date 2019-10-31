package org.kanger.calculator;

import org.kanger.enums.DataType;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgList;
import org.kanger.units.Domain;
import org.kanger.units.SysOp;
import org.kanger.units.TValue;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Dmitry G. Qusnetsov on 18.01.17.
 */
public class Predicates {


    private IUser user = null;
    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {

        /// Системные предикаты
        {

            put("_eq(2)", new SysOp(LibMode.PREDICATE, "_eq", 2, new IReactor() {

                @Override
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();

//                    if (arg.get(0).isFSet() /*&& arg.get(0).getF().isCalculable() && !arg.get(0).getF().getResult().isEmpty() && arg.get(0).getF().isEmpty()*/) {
//                        user.getMind().getCalculator().calculate(arg.get(0).getF(), user.getMind().isLogging());
//                    }
//
//                    if (arg.get(1).isFSet() /*&& arg.get(1).getF().isCalculable() && !arg.get(1).getF().getResult().isEmpty() && arg.get(1).getF().isEmpty()*/) {
//                        user.getMind().getCalculator().calculate(arg.get(1).getF(), user.getMind().isLogging());
//                    }

                    if (arg.get(0).isDefined() && arg.get(1).isEmpty()) {
                        if (arg.get(1).setValue(arg.get(0).getValue())) {
                            i = 1;
                        }
                    } else if (arg.get(0).isEmpty() && arg.get(1).isDefined()) {
                        if (arg.get(0).setValue(arg.get(1).getValue())) {
                            i = 1;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (arg.get(0).getValue().compareTo(arg.get(1).getValue()) == 0) {
                            i = 1;
                        } else { //if ((arg.createCVar(0).getValue().isCVariable() && arg.createCVar(1).getValue().isCVariable()) || (!arg.createCVar(0).getValue().isCVariable() && !arg.createCVar(1).getValue().isCVariable())) {

                            Term v0 = arg.get(0).getValue();
                            Term v1 = arg.get(1).getValue();


                            if (arg.get(0).isTSet() && !arg.get(1).isEmpty()) {
                                TValue v = arg.get(0).addValue(arg.get(1).getValue());
                                if (user.getMind().isLogging() && v != null) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "Added: " + v);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                                }
                            }
                            if (arg.get(1).isTSet() && !arg.get(0).isEmpty()) {
                                TValue v = arg.get(1).addValue(arg.get(0).getValue());
                                if (user.getMind().isLogging() && v != null) {
                                    user.getMind().getLog().add(LogMode.ANALIZER, "Added: " + v);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
                                    user.getMind().getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                                }
                            }

                            if (arg.get(0).isFSet() && arg.get(0).getF().isCalculable()) {
                                arg.get(0).getF().setResult(v1);
                                user.getMind().getCalculator().calculate(arg.get(0).getF(), user.getMind().isLogging());
                            }
                            if (arg.get(1).isFSet() && arg.get(1).getF().isCalculable()) {
                                arg.get(1).getF().setResult(v0);
                                user.getMind().getCalculator().calculate(arg.get(1).getF(), user.getMind().isLogging());
                            }

                            i = 0;
                        }
//                        else //if(!arg.createCVar(0).getValue().isCVariable() && !arg.createCVar(1).getValue().isCVariable())
//                            i = 0;
                    }
                    return i;
                }
            }));
        }


        {
            put("_ne(2)", new SysOp(LibMode.PREDICATE, "_ne", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
                        if (rc != 0) {
                            i = 1;
                        } else if (rc == 0) {
                            i = 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_gr(2)", new SysOp(LibMode.PREDICATE, "_gr", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
//                        if (rc != -2) {
                        i = rc > 0 ? 1 : 0;
//                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_ge(2)", new SysOp(LibMode.PREDICATE, "_ge", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
//                        if (rc != -2) {
                        i = rc >= 0 ? 1 : 0;
//                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_lr(2)", new SysOp(LibMode.PREDICATE, "_lr", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
//                        if (rc != -2) {
                        i = rc < 0 ? 1 : 0;
//                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_le(2)", new SysOp(LibMode.PREDICATE, "_le", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        int rc = arg.get(0).getValue().compareTo(arg.get(1).getValue());
//                        if (rc != -2) {
                        i = rc <= 0 ? 1 : 0;
//                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_in(2)", new SysOp(LibMode.PREDICATE, "_in", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (arg.get(0).isEmpty() && arg.get(1).isDefined()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                || arg.get(1).getValue().getType() == DataType.SET
                                || arg.get(1).getValue().getType() == DataType.STRING) {
                            Term top = null;
                            for (Term cur : expand(arg.get(1).getValue(), null)) {
                                if (top == null) top = cur;
                                arg.get(0).addValue(cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            arg.get(0).setValue(top);
                        }
                    } else if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                || arg.get(1).getValue().getType() == DataType.SET
                                || arg.get(1).getValue().getType() == DataType.STRING) {
                            i = _in(arg.get(0).getValue(), arg.get(1).getValue(), null) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_in(3)", new SysOp(LibMode.PREDICATE, "_in", 3, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();
                    if (arg.get(0).isEmpty() && arg.get(1).isDefined()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                || arg.get(1).getValue().getType() == DataType.SET
                                || arg.get(1).getValue().getType() == DataType.STRING) {
                            Term top = null;
                            for (Term cur : expand(arg.get(1).getValue(), arg.get(2).getValue())) {
                                if (top == null) top = cur;
                                arg.get(0).addValue(cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            arg.get(0).setValue(top);
                        }
                    } else if (!arg.get(0).isEmpty() && !arg.get(1).isEmpty() && !arg.get(0).getValue().isCVariable() && !arg.get(1).getValue().isCVariable()) {
                        if (arg.get(1).getValue().getType() == DataType.INTERVAL
                                || arg.get(1).getValue().getType() == DataType.SET
                                || arg.get(1).getValue().getType() == DataType.STRING) {
                            i = _in(arg.get(0).getValue(), arg.get(1).getValue(), arg.get(2).getValue()) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

    };

    public Predicates(IUser user) {
        this.user = user;
    }

    public Map<String, SysOp> getSysOps() {
        return sysOps;
    }

    private boolean cmp(int rc, int rcmin, int rcmax, Term cur, Term step) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        boolean res = false;
        if (rc < 0 ? (rcmin >= 0 && rcmax <= 0) : (rcmin <= 0 && rcmax >= 0)) {
            if (cur.getType() == DataType.NUMERIC && step == null) {
                step = user.getMind().getTerms().add(1);
            }
            if (cur.getType() == DataType.NUMERIC && step.getType() == DataType.NUMERIC
                    && Math.abs((double) step.getValue()) > Term.FLT_EPSILON
                    && Math.abs((double) cur.getValue() % (double) step.getValue()) > Term.FLT_EPSILON) {
                res = false;
            } else {
                res = true;
            }
        } else {
            res = false;
        }
        return res;
    }

    public boolean _in(Term cur, Term interval, Term step) throws Exception {
        boolean res = false;
        if (interval.getType() == DataType.INTERVAL
                && interval.getValue() instanceof Collection
                && ((Collection) interval.getValue()).size() == 2) {

            int rcmin = -2;
            int rcmax = -2;
            int i = -1;
            Term min = ((List<Term>) interval.getValue()).get(0);
            Term max = ((List<Term>) interval.getValue()).get(1);
            int rc = min.compareTo(max);

            if (cur.getType() == DataType.INTERVAL
                    && cur.getValue() instanceof Collection
                    && ((Collection) cur.getValue()).size() == 2) {

                Term xmin = (Term) ((Collection) cur.getValue()).toArray()[0];
                Term xmax = (Term) ((Collection) cur.getValue()).toArray()[1];
                int xrc = xmin.compareTo(xmax);
                rcmin = rc == xrc ? xmin.compareTo(min) : xmin.compareTo(max);
                rcmax = rc == xrc ? xmax.compareTo(max) : xmax.compareTo(min);
                res = cmp(rc, rcmin, rcmax, cur, step);

            } else if (cur.getType() == DataType.SET
                    && cur.getValue() instanceof Collection
                    && ((Collection) cur.getValue()).size() > 0) {

                for (Term t : expand(cur, step)) {
                    rcmin = t.compareTo(min);
                    rcmax = t.compareTo(max);
                    if (cmp(rc, rcmin, rcmax, cur, step)) {
                        res = true;
                    } else {
                        res = false;
                        break;
                    }
                }

            } else {
                rcmin = cur.compareTo(min);
                rcmax = cur.compareTo(max);
                res = cmp(rc, rcmin, rcmax, cur, step);
            }
        } else if (interval.getType() == DataType.SET
                && interval.getValue() instanceof Collection
                && ((Collection) interval.getValue()).size() > 0) {

            for (Term c : expand(cur, step)) {
                res = false;
                for (Term t : (Collection<Term>) interval.getValue()) {
                    if (t.getType() == DataType.INTERVAL || t.getType() == DataType.SET) {
                        if (_in(c, t, step)) {
                            res = true;
                            break;
                        }
                    } else if (c.getId() == t.getId()) {
                        res = true;
                        break;
                    }
                }
                if (!res) {
                    break;
                }
            }
        } else if (interval.getType() == DataType.STRING) {
            if (step == null) {
                res = interval.getValue().toString().contains(cur.getValue().toString())
                        || Pattern.matches((String) cur.getValue(), (String) interval.getValue());
            } else {
                Pattern pt = Pattern.compile(step.getValue().toString());
                Matcher mt = pt.matcher(interval.getValue().toString());
                if (mt.find()) {
                    for (int k = 0; k < mt.groupCount(); ++k) {
                        Term t = user.getMind().getTerms().add(mt.group(k + 1) + "");
                        if (cur.getId() == t.getId()) {
                            res = true;
                            break;
                        }
                    }
                }
            }
        }
        return res;
    }


    public List<Term> expand(Term interval, Term step) throws Exception {
        List<Term> list = new ArrayList<>();
        Term top = null;
        if (interval.getType() == DataType.INTERVAL
                && interval.getValue() instanceof Collection
                && ((Collection) interval.getValue()).size() == 2) {

            Term min = (Term) ((Collection) interval.getValue()).toArray()[0];
            Term max = (Term) ((Collection) interval.getValue()).toArray()[1];
            Term cur = min;
            int rc = min.compareTo(max);
            while (true) {
                list.add(cur);
                if (top == null) top = cur;
                if (rc == 0) {
                    break;
                }
                Term next;
                if (step != null) {
                    next = rc < 0
                            ? user.getMind().getCalculator().getFunctions()._add(cur, step)
                            : user.getMind().getCalculator().getFunctions()._sub(cur, step);
                } else {
                    next = rc < 0
                            ? user.getMind().getCalculator().getFunctions()._inc(cur)
                            : user.getMind().getCalculator().getFunctions()._dec(cur);
                }
                if (next.getId() == cur.getId()) {
                    list.add(max);
                    break;
                } else if (rc < 0 && next.compareTo(max) > 0) {
                    break;
                } else if (rc > 0 && next.compareTo(max) < 0) {
                    break;
                } else {
                    cur = next;
                }
            }
        } else if (interval.getType() == DataType.SET
                && interval.getValue() instanceof Collection
                && ((Collection) interval.getValue()).size() > 0) {

            for (Term t : (Collection<Term>) interval.getValue()) {
                if (t.getType() == DataType.INTERVAL || t.getType() == DataType.SET) {
                    list.addAll(expand(t, null));
                } else {
                    list.add(t);
                }
            }
        } else if (interval.getType() == DataType.STRING) {
            if (step == null) {
                for (int k = 0; k < interval.getValue().toString().length(); ++k) {
                    Term x = user.getMind().getTerms().add(interval.getValue().toString().charAt(k) + "");
                    list.add(x);
                }
            } else {
                Pattern pt = Pattern.compile(step.getValue().toString());
                Matcher mt = pt.matcher(interval.getValue().toString());
                if (mt.find()) {
                    for (int k = 0; k < mt.groupCount(); ++k) {
                        Term t = user.getMind().getTerms().add(mt.group(k + 1) + "");
                        list.add(t);
                    }
                }
            }
        } else {
            list.add(interval);
        }

        return list;
    }


}

