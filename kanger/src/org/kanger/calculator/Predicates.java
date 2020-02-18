package org.kanger.calculator;

import org.kanger.Mind;
import org.kanger.enums.DataType;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
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


    private final Mind mind;
    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {

        /// Системные предикаты
        {

            put("_eq(2)", new SysOp(LibMode.PREDICATE, "_eq", 2, new IReactor() {

                @Override
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgList arg = ((Domain) o).getArguments();

//                    if (arg.get(0).isFSet() /*&& arg.get(0).getF().isCalculable() && !arg.get(0).getF().getResult().isEmpty(mind) && arg.get(0).getF().isEmpty(mind)*/) {
//                        mind.getCalculator().calculate(arg.get(0).getF(), mind.isLogging());
//                    }
//
//                    if (arg.get(1).isFSet() /*&& arg.get(1).getF().isCalculable() && !arg.get(1).getF().getResult().isEmpty(mind) && arg.get(1).getF().isEmpty(mind)*/) {
//                        mind.getCalculator().calculate(arg.get(1).getF(), mind.isLogging());
//                    }

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (arg.get(1).setValue(mind, arg.get(0).getValue(mind))) {
                            i = 1;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (arg.get(0).setValue(mind, arg.get(1).getValue(mind))) {
                            i = 1;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind)) == 0) {
                            i = 1;
                        } else { //if ((arg.createCVar(0).getValue(mind).isCVariable() && arg.createCVar(1).getValue(mind).isCVariable()) || (!arg.createCVar(0).getValue(mind).isCVariable() && !arg.createCVar(1).getValue(mind).isCVariable())) {

                            Term v0 = arg.get(0).getValue(mind);
                            Term v1 = arg.get(1).getValue(mind);


                            if (arg.get(0).isTSet() && !arg.get(1).isEmpty(mind)) {
                                TValue v = arg.get(0).addValue(mind, arg.get(1).getValue(mind));
                                if (mind.isLogging() && v != null) {
                                    mind.getLog().add(LogMode.ANALIZER, "Added: " + v);
                                    mind.getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
                                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                                }
                            }
                            if (arg.get(1).isTSet() && !arg.get(0).isEmpty(mind)) {
                                TValue v = arg.get(1).addValue(mind, arg.get(0).getValue(mind));
                                if (mind.isLogging() && v != null) {
                                    mind.getLog().add(LogMode.ANALIZER, "Added: " + v);
                                    mind.getLog().add(LogMode.ANALIZER, "\tFrom: " + o);
                                    mind.getLog().add(LogMode.ANALIZER, "-------------------------------------------");
                                }
                            }

                            if (arg.get(0).isFSet() && arg.get(0).getF(mind).isCalculable()) {
                                arg.get(0).getF(mind).setResult(v1);
                                mind.getCalculator().calculate(arg.get(0).getF(mind), mind.isLogging());
                            }
                            if (arg.get(1).isFSet() && arg.get(1).getF(mind).isCalculable()) {
                                arg.get(1).getF(mind).setResult(v0);
                                mind.getCalculator().calculate(arg.get(1).getF(mind), mind.isLogging());
                            }

                            i = 0;
                        }
//                        else //if(!arg.createCVar(0).getValue(mind).isCVariable() && !arg.createCVar(1).getValue(mind).isCVariable())
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
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind)) {
                        int rc = arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind));
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
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        int rc = arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind));
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
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        int rc = arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind));
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
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        int rc = arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind));
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
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        int rc = arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind));
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
                    if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING) {
                            Term top = null;
                            for (Term cur : expand(arg.get(1).getValue(mind), null)) {
                                if (top == null) top = cur;
                                arg.get(0).addValue(mind, cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            arg.get(0).setValue(mind, top);
                        }
                    } else if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING) {
                            i = _in(arg.get(0).getValue(mind), arg.get(1).getValue(mind), null) ? 1 : 0;
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
                    if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING) {
                            Term top = null;
                            for (Term cur : expand(arg.get(1).getValue(mind), arg.get(2).getValue(mind))) {
                                if (top == null) top = cur;
                                arg.get(0).addValue(mind, cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            arg.get(0).setValue(mind, top);
                        }
                    } else if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING) {
                            i = _in(arg.get(0).getValue(mind), arg.get(1).getValue(mind), arg.get(2).getValue(mind)) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

    };

    public Predicates(Mind mind) {
        this.mind = mind;
    }

    public Map<String, SysOp> getSysOps() {
        return sysOps;
    }

    private boolean cmp(int rc, int rcmin, int rcmax, Term cur, Term step) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        boolean res = false;
        if (rc < 0 ? (rcmin >= 0 && rcmax <= 0) : (rcmin <= 0 && rcmax >= 0)) {
            if (cur.getType() == DataType.NUMERIC && step == null) {
                step = mind.getTerms().add(1);
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


    //TODO: ОШИБКА! ?$x x : "123123123123";

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
                        Term t = mind.getTerms().add(mt.group(k + 1) + "");
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
                            ? mind.getCalculator().getFunctions()._add(cur, step)
                            : mind.getCalculator().getFunctions()._sub(cur, step);
                } else {
                    next = rc < 0
                            ? mind.getCalculator().getFunctions()._inc(cur)
                            : mind.getCalculator().getFunctions()._dec(cur);
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
                    Term x = mind.getTerms().add(interval.getValue().toString().charAt(k) + "");
                    list.add(x);
                }
            } else {
                Pattern pt = Pattern.compile(step.getValue().toString());
                Matcher mt = pt.matcher(interval.getValue().toString());
                if (mt.find()) {
                    for (int k = 0; k < mt.groupCount(); ++k) {
                        Term t = mind.getTerms().add(mt.group(k + 1) + "");
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

