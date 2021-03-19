/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.calculator;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.LibMode;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Dmitry G. Quznetsov on 18.01.17.
 */
public class Predicates {


    private final transient Mind mind;
    private final Map<String, Operation> sysOps = new HashMap<String, Operation>() {

        /// Системные предикаты
        {

            put("_eq(2)", new Operation(LibMode.PREDICATE, "_eq", 2, new IReactor() {

                @Override
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();

//                    if (arg.get(0).isFSet() /*&& arg.get(0).getF().isCalculable() && !arg.get(0).getF().getResult().isEmpty(mind) && arg.get(0).getF().isEmpty(mind)*/) {
//                        mind.getCalculator().calculate(arg.get(0).getF(), mind.isLogging());
//                    }
//
//                    if (arg.get(1).isFSet() /*&& arg.get(1).getF().isCalculable() && !arg.get(1).getF().getResult().isEmpty(mind) && arg.get(1).getF().isEmpty(mind)*/) {
//                        mind.getCalculator().calculate(arg.get(1).getF(), mind.isLogging());
//                    }

                    if (mind.getCalculator().getFunctions().isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (((Argument) arg.get(1)).setValue(mind, arg.get(0).getValue(mind))) {
                            i = 1;
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                List<TValue> list = new ArrayList<>();
                                list.add(((TVariable) arg.get(1).getObject(mind)).getCurrent());
                                mind.addTSolve(list);
                            }
                        }
                    } else if (arg.get(0).isEmpty(mind) && mind.getCalculator().getFunctions().isDefined(arg.get(1))) {
                        if (((Argument) arg.get(0)).setValue(mind, arg.get(1).getValue(mind))) {
                            i = 1;
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                List<TValue> list = new ArrayList<>();
                                list.add(((TVariable) arg.get(0).getObject(mind)).getCurrent());
                                mind.addTSolve(list);
                            }
                        }
                    } else if (mind.getCalculator().getFunctions().isDefined(arg.get(0)) && mind.getCalculator().getFunctions().isDefined(arg.get(1))) {
                        if (arg.get(0).getValue(mind).equalsTo(arg.get(1).getValue(mind))) {
                            i = 1;
                        } else { //if ((arg.createCVar(0).getValue(mind).isCVariable() && arg.createCVar(1).getValue(mind).isCVariable()) || (!arg.createCVar(0).getValue(mind).isCVariable() && !arg.createCVar(1).getValue(mind).isCVariable())) {

                            Term v0 = (Term) arg.get(0).getValue(mind);
                            Term v1 = (Term) arg.get(1).getValue(mind);


                            if (arg.get(0).getType() == ArgumentType.TVARIABLE && !arg.get(1).isEmpty(mind)) {
                                TValue v = mind.getCalculator().getFunctions().addTValue(arg.get(0), arg.get(1).getValue(mind));
                                if (mind.isLogging() && v != null) {
                                    mind.getLog().add(LogMode.ANALYZER, "Added: " + v);
                                    mind.getLog().add(LogMode.ANALYZER, "\tFrom: " + o);
                                    mind.getLog().add(LogMode.ANALYZER, "-------------------------------------------");
                                }
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE && !arg.get(0).isEmpty(mind)) {
                                TValue v = mind.getCalculator().getFunctions().addTValue(arg.get(1), arg.get(0).getValue(mind));
                                if (mind.isLogging() && v != null) {
                                    mind.getLog().add(LogMode.ANALYZER, "Added: " + v);
                                    mind.getLog().add(LogMode.ANALYZER, "\tFrom: " + o);
                                    mind.getLog().add(LogMode.ANALYZER, "-------------------------------------------");
                                }
                            }

                            if (arg.get(0).getType() == ArgumentType.FUNCTION
                                    && ((Function) arg.get(0).getObject(mind)).isCalculable()) {
                                ((Function) arg.get(0).getObject(mind)).setResult(v1);
                                mind.getCalculator().calculate((Function) arg.get(0).getObject(mind), mind.isLogging());
                            }
                            if (arg.get(1).getType() == ArgumentType.FUNCTION
                                    && ((Function) arg.get(1).getObject(mind)).isCalculable()) {
                                ((Function) arg.get(1).getObject(mind)).setResult(v0);
                                mind.getCalculator().calculate((Function) arg.get(1).getObject(mind), mind.isLogging());
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
            put("_ne(2)", new Operation(LibMode.PREDICATE, "_ne", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
                    if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind)) {
                        boolean rc = arg.get(0).getValue(mind).equalsTo(arg.get(1).getValue(mind));
                        if (!rc) {
                            i = 1;
                        } else {
                            i = 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_gr(2)", new Operation(LibMode.PREDICATE, "_gr", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
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
            put("_ge(2)", new Operation(LibMode.PREDICATE, "_ge", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
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
            put("_lr(2)", new Operation(LibMode.PREDICATE, "_lr", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
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
            put("_le(2)", new Operation(LibMode.PREDICATE, "_le", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
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
            put("_in(2)", new Operation(LibMode.PREDICATE, "_in", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
//                    if (arg.get(1).isFSet()) {
//                        mind.getCalculator().calculate(arg.get(1).getF(mind), mind.isLogging());
//                    }

                    if (arg.get(0).isEmpty(mind) && mind.getCalculator().getFunctions().isDefined(arg.get(1))) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING
                                || arg.get(1).getValue(mind).getType() == DataType.BLOB) {
                            ITerm top = null;
                            for (ITerm cur : mind.getCalculator().expand(arg.get(1).getValue(mind), null, true)) {
                                if (top == null) top = cur;
                                mind.getCalculator().getFunctions().addTValue(arg.get(0), cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            ((Argument) arg.get(0)).setValue(mind, top);
                        }
                    } else if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING
                                || arg.get(1).getValue(mind).getType() == DataType.BLOB) {
                            i = _in(arg.get(0).getValue(mind), arg.get(1).getValue(mind), null) ? 1 : 0;
                        }
                    }
                    return i;
                }
            }));
        }

        {
            put("_in(3)", new Operation(LibMode.PREDICATE, "_in", 3, new IReactor() {
                public Object run(Object o) throws Exception {
                    int i = -1;
                    ArgumentsList arg = ((Domain) o).getArguments();
//                    if (arg.get(1).isFSet()) {
//                        mind.getCalculator().calculate(arg.get(1).getF(mind), mind.isLogging());
//                    }
                    if (arg.get(0).isEmpty(mind) && mind.getCalculator().getFunctions().isDefined(arg.get(1))) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING
                                || arg.get(1).getValue(mind).getType() == DataType.BLOB) {
//                            if (arg.get(1).isFSet()) {
//                                mind.getCalculator().calculate(arg.get(1).getF(mind), mind.isLogging());
//                            }
                            ITerm top = null;
                            for (ITerm cur : mind.getCalculator().expand(arg.get(1).getValue(mind), arg.get(2).getValue(mind), true)) {
                                if (top == null) top = cur;
                                mind.getCalculator().getFunctions().addTValue(arg.get(0), cur);
                                i = 1;
                            }
                        }
                        Term top = null;
                        if (top != null) {
                            ((Argument) arg.get(0)).setValue(mind, top);
                        }
                    } else if (!arg.get(0).isEmpty(mind) && !arg.get(1).isEmpty(mind) && !arg.get(0).getValue(mind).isCVariable() && !arg.get(1).getValue(mind).isCVariable()) {
                        if (arg.get(1).getValue(mind).getType() == DataType.INTERVAL
                                || arg.get(1).getValue(mind).getType() == DataType.SET
                                || arg.get(1).getValue(mind).getType() == DataType.STRING
                                || arg.get(1).getValue(mind).getType() == DataType.BLOB) {
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

    public Map<String, Operation> getSysOps() {
        return sysOps;
    }

    private boolean cmp(int rc, int rcmin, int rcmax, ITerm cmin, ITerm cur, ITerm step) throws Exception {
        boolean res = false;
        if (rc < 0 ? (rcmin >= 0 && rcmax <= 0) : (rcmin <= 0 && rcmax >= 0)) {
            if (cur.getType() == DataType.NUMERIC && step == null) {
                step = mind.getTerms().add(1);
            }
            if (cur.getType() == DataType.NUMERIC && step.getType() == DataType.NUMERIC
                    && Math.abs((double) step.getValue()) > Term.FLT_EPSILON
                    && Math.abs(((double) cur.getValue() - (double) cmin.getValue()) % (double) step.getValue()) > Term.FLT_EPSILON) {
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

    public boolean _in(ITerm cur, ITerm interval, ITerm step) throws Exception {
        boolean res = false;
        if (interval.getType() == DataType.INTERVAL) {

            int rcmin = -2;
            int rcmax = -2;
            int i = -1;
            ITerm min = ((List<Term>) interval.getValue()).get(0);
            ITerm max = ((List<Term>) interval.getValue()).get(1);
            int rc = min.compareTo(max);
//            Term cmin = rc < 0 ? min : max;

            if (cur.getType() == DataType.INTERVAL) {

                ITerm xmin = (Term) ((Collection) cur.getValue()).toArray()[0];
                ITerm xmax = (Term) ((Collection) cur.getValue()).toArray()[1];
                int xrc = xmin.compareTo(xmax);
                rcmin = rc == xrc ? xmin.compareTo(min) : xmin.compareTo(max);
                rcmax = rc == xrc ? xmax.compareTo(max) : xmax.compareTo(min);
                res = cmp(rc, rcmin, rcmax, min, cur, step);

            } else if (cur.getType() == DataType.SET) {

                for (ITerm t : mind.getCalculator().expand(cur, step, false)) {
                    rcmin = t.compareTo(min);
                    rcmax = t.compareTo(max);
                    if (cmp(rc, rcmin, rcmax, min, cur, step)) {
                        res = true;
                    } else {
                        res = false;
                        break;
                    }
                }

            } else {
                rcmin = cur.compareTo(min);
                rcmax = cur.compareTo(max);
                res = cmp(rc, rcmin, rcmax, min, cur, step);
            }
        } else if (interval.getType() == DataType.SET) {

            for (ITerm c : mind.getCalculator().expand(cur, step, false)) {
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
            String scur = cur.getValue().toString();
            if (cur.getType() == DataType.NUMERIC) {
                scur = ((Double) cur.getValue()).longValue() + "";
            }
            if (step == null) {
                res = interval.getValue().toString().contains(scur)
                        || Pattern.matches(scur, interval.getValue().toString());
            } else {
                Pattern pt = Pattern.compile(step.getValue().toString());
                Matcher mt = pt.matcher(interval.getValue().toString());
                while (mt.find()) {
                    for (int k = 0; k < mt.groupCount(); ++k) {
                        ITerm t = mind.getTerms().add(mt.group(k + 1) + "");
                        if (cur.getId() == t.getId()) {
                            res = true;
                            break;
                        }
                    }
                }
            }
        } else if (interval.getType() == DataType.BLOB) {
            res = indexOf((byte[]) interval.getValue(), (byte[]) cur.getValue()) != -1;
        }
        return res;
    }

    public int indexOf(byte[] source, byte[] sample) {
        int pos = 0;
        while (pos < source.length) {
            boolean complete = true;
            for (int i = 0; i < sample.length; ++i) {
                if (source.length <= pos + i) {
                    break;
                } else if (sample[i] != source[pos + i]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return pos;
            } else {
                ++pos;
            }
        }
        return -1;
    }


}

