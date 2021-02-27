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
import org.kanger.enums.*;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 18.01.17.
 */
public class Functions {

    private final transient Mind mind;
    private final Map<String, Operation> sysOps = new HashMap<String, Operation>() {


        /// Арифметика
        {
            put("_inc(1)", new Operation(LibMode.FUNCTION, "_inc", 1, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _inc(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _dec(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_inc(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _dec(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }

                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_dec(1)", new Operation(LibMode.FUNCTION, "_dec", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _dec(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _inc(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_dec(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _inc(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitnot(1)", new Operation(LibMode.FUNCTION, "_bitnot", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _bitnot(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!o.setParameter(0, _bitnot(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_bitnot(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _bitnot(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_neg(1)", new Operation(LibMode.FUNCTION, "_neg", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _neg(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!o.setParameter(0, _neg(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_neg(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _neg(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_val(1)", new Operation(LibMode.FUNCTION, "_val", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, arg.get(0).getValue(mind))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!o.setParameter(0, arg.get(1).getValue(mind))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (arg.get(0).getValue(mind).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), arg.get(1).getValue(mind));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_add(2)", new Operation(LibMode.FUNCTION, "_add", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _add(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _sub(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                        if (!o.setParameter(1, _sub(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_add(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _sub(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(1), _sub(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_sub(2)", new Operation(LibMode.FUNCTION, "_sub", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _sub(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _add(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                        if (!o.setParameter(1, _sub(arg.get(0).getValue(mind), arg.get(2).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_sub(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _add(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(1), _sub(arg.get(0).getValue(mind), arg.get(2).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_mul(2)", new Operation(LibMode.FUNCTION, "_mul", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _mul(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!o.setParameter(0, _div(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && (double) arg.get(0).getValue(mind).getValue() != 0) {
                        if (!o.setParameter(1, _div(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_mul(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE && (double) arg.get(1).getValue(mind).getValue() != 0) {
                                TValue v = addTValue(arg.get(0), _div(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE && (double) arg.get(0).getValue(mind).getValue() != 0) {
                                TValue v = addTValue(arg.get(1), _div(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_div(2)", new Operation(LibMode.FUNCTION, "_div", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!o.setParameter(2, _div(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _mul(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && (double) arg.get(2).getValue(mind).getValue() != 0) {
                        if (!o.setParameter(1, _div(arg.get(0).getValue(mind), arg.get(2).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_div(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _mul(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE && (double) arg.get(2).getValue(mind).getValue() != 0) {
                                TValue v = addTValue(arg.get(0), _div(arg.get(0).getValue(mind), arg.get(2).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_rem(2)", new Operation(LibMode.FUNCTION, "_rem", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!o.setParameter(2, _rem(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_rem(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_iv(2)", new Operation(LibMode.FUNCTION, "_iv", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, mind.getTerms().add(new ITerm[]{arg.get(0).getValue(mind), arg.get(1).getValue(mind)}))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL && arg.get(1).getValue(mind).equalsTo(((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                        if (!o.setParameter(0, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(0))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL && arg.get(0).getValue(mind).equalsTo(((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                        if (!o.setParameter(1, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL) {
                        if (!o.setParameter(0, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(0)) && !o.setParameter(1, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL
                            && mind.getTerms().add(new ITerm[]{arg.get(0).getValue(mind), arg.get(1).getValue(mind)}).equalsTo(arg.get(2).getValue(mind))) {
                        ret = 2;
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_is(0)", new Operation(LibMode.FUNCTION, "_is", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();

                    ArgumentsList tmp = new ArgumentsList();
                    for (int i = 0; i < arg.size() - 1; ++i) {
                        if (arg.get(i).isEmpty(mind)) {
                            ret = 0;
                            break;
                        }
                        tmp.add(arg.get(i));
                    }

                    if (ret != 0 && arg.get(arg.size() - 1).isEmpty(mind)) {
                        if (!o.setParameter(arg.size() - 1, mind.getTerms().add(tmp))) {
                            ret = 0;
                        }
                    } else if (ret != 0) {
                        //TODO: Сравнить два массива сетов
                        ret = 0;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitleft(2)", new Operation(LibMode.FUNCTION, "_bitleft", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _bitleft(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _bitright(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_bitleft(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _bitright(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitright(2)", new Operation(LibMode.FUNCTION, "_bitright", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _bitright(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _bitleft(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_bitright(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _bitleft(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitxor(2)", new Operation(LibMode.FUNCTION, "_bitxor", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _bitxor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _bitxor(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_bitxor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _bitxor(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitand(2)", new Operation(LibMode.FUNCTION, "_bitand", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _bitand(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_bitand(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitor(2)", new Operation(LibMode.FUNCTION, "_bitor", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _bitor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _bitandnot(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                        if (!o.setParameter(1, _bitandnot(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_bitor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _bitandnot(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(1), _bitandnot(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("log(1)", new Operation(LibMode.FUNCTION, "log", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _log(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _exp(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_log(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _exp(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("exp(1)", new Operation(LibMode.FUNCTION, "exp", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _exp(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _log(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_exp(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _log(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("log10(1)", new Operation(LibMode.FUNCTION, "log10", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _log10(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _exp10(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_log10(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _exp10(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("exp10(1)", new Operation(LibMode.FUNCTION, "exp10", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _exp10(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _log10(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_exp10(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _log10(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pi(0)", new Operation(LibMode.FUNCTION, "pi", 0, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    if (!o.setParameter(0, _pi())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sin(1)", new Operation(LibMode.FUNCTION, "sin", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _sin(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _asin(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_sin(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _asin(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("asin(1)", new Operation(LibMode.FUNCTION, "asin", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _asin(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _sin(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_asin(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _sin(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("cos(1)", new Operation(LibMode.FUNCTION, "cos", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _cos(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _acos(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_cos(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _acos(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("acos(1)", new Operation(LibMode.FUNCTION, "acos", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _acos(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _cos(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_acos(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _cos(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("tan(1)", new Operation(LibMode.FUNCTION, "tan", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _tan(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _atan(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_tan(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _atan(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("atan(1)", new Operation(LibMode.FUNCTION, "atan", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _atan(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _tan(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_atan(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _tan(arg.get(1).getValue(mind)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("int(1)", new Operation(LibMode.FUNCTION, "int", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _int(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_int(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("int(2)", new Operation(LibMode.FUNCTION, "int", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _int(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_int(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("blob(1)", new Operation(LibMode.FUNCTION, "blob", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _blob(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_blob(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("blob(2)", new Operation(LibMode.FUNCTION, "blob", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _blob(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_blob(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("date(1)", new Operation(LibMode.FUNCTION, "date", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _date(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_date(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("date(2)", new Operation(LibMode.FUNCTION, "date", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _date(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_date(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("string(1)", new Operation(LibMode.FUNCTION, "string", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _string(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_string(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("string(2)", new Operation(LibMode.FUNCTION, "string", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _string(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_string(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("abs(1)", new Operation(LibMode.FUNCTION, "abs", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _abs(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_abs(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(2)", new Operation(LibMode.FUNCTION, "round", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _round(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_round(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(1)", new Operation(LibMode.FUNCTION, "round", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _round(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_round(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sqrt(1)", new Operation(LibMode.FUNCTION, "sqrt", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _sqrt(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, _pow(arg.get(1).getValue(mind), mind.getTerms().add(2)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (_sqrt(arg.get(0).getValue(mind)).equalsTo(arg.get(1).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _pow(arg.get(1).getValue(mind), mind.getTerms().add(2)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pow(2)", new Operation(LibMode.FUNCTION, "pow", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _pow(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _root(arg.get(2).getValue(mind), mind.getTerms().add(1)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_pow(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _root(arg.get(2).getValue(mind), mind.getTerms().add(1)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("root(2)", new Operation(LibMode.FUNCTION, "root", 2, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                        if (!o.setParameter(2, _root(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(2))) {
                        if (!o.setParameter(0, _pow(arg.get(2).getValue(mind), mind.getTerms().add(1)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                        if (_root(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), _pow(arg.get(2).getValue(mind), mind.getTerms().add(1)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(o.getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        // String functions
        /// Строковые функции
        {
            put("length(1)", new Operation(LibMode.FUNCTION, "length", 1, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                            if (!o.setParameter(1, _length(arg.get(0).getValue(mind), null))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                            if (_length(arg.get(0).getValue(mind), null).equalsTo(arg.get(1).getValue(mind))) {
                                ret = 2;
                            } else {
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("length(2)", new Operation(LibMode.FUNCTION, "length", 2, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                            if (!o.setParameter(2, _length(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                            if (!o.setParameter(1, _step(arg.get(0).getValue(mind), arg.get(2).getValue(mind)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                            if (_length(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).equalsTo(arg.get(2).getValue(mind))) {
                                ret = 2;
                            } else {
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("mid(2)", new Operation(LibMode.FUNCTION, "mid", 2, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                            if (!o.setParameter(2, mind.getTerms().add(_substring(src, pos.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                            if (!o.setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                            if (_equals(result, _substring(src, pos.intValue(), 0))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(1), mind.getTerms().add(_indexOf(src, result)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("mid(3)", new Operation(LibMode.FUNCTION, "mid", 3, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Double len = arg.get(2).isEmpty(mind) ? null : (Double) arg.get(2).getValue(mind).getValue();
                        Object result = arg.get(3).isEmpty(mind) ? null : arg.get(3).getValue(mind).getValue();

                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && arg.get(3).isEmpty(mind)) {
                            if (!o.setParameter(3, mind.getTerms().add(_substring(src, pos.intValue(), len.intValue())))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && isDefined(arg.get(3))) {
                            if (!o.setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind) && isDefined(arg.get(3)) && _indexOf(src, result) != -1) {
                            if (!o.setParameter(2, mind.getTerms().add(_indexOf(src, result) + __length(result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && arg.get(2).isEmpty(mind) && isDefined(arg.get(3)) && _indexOf(src, result) != -1) {
                            if (!o.setParameter(2, mind.getTerms().add(_indexOf(src, result) + __length(result)))
                                    || !o.setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && isDefined(arg.get(3))) {
                            if (_equals(result, _substring(src, pos.intValue(), len.intValue()))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(1), mind.getTerms().add(_indexOf(src, result)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                if (arg.get(2).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(2), mind.getTerms().add(_indexOf(src, result) + __length(result)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("left(2)", new Operation(LibMode.FUNCTION, "left", 2, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                            if (!o.setParameter(2, mind.getTerms().add(_substring(src, 0, pos.intValue())))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && _startsWith(src, result)) {
                            if (!o.setParameter(1, mind.getTerms().add(__length(result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                            if (_equals(result, _substring(src, 0, pos.intValue()))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(1), mind.getTerms().add(__length(result)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("right(2)", new Operation(LibMode.FUNCTION, "right", 2, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                            if (!o.setParameter(2, mind.getTerms().add(_substring(src, __length(src) - pos.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2)) && _endsWith(src, result)) {
                            if (!o.setParameter(1, mind.getTerms().add(__length(result)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                            if (_equals(result, _substring(src, __length(src) - pos.intValue(), 0))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(1), mind.getTerms().add(__length(result)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("trim(1)", new Operation(LibMode.FUNCTION, "trim", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add(src.trim()))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (result.equals(src.trim())) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("uc(1)", new Operation(LibMode.FUNCTION, "uc", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add(src.toUpperCase()))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (result.equals(src.toUpperCase())) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("lc(1)", new Operation(LibMode.FUNCTION, "lc", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add(src.toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (result.equals(src.toLowerCase())) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("at(2)", new Operation(LibMode.FUNCTION, "at", 2, new IReactor<Function>() {
                public Object run(Function o) {
                    int ret = 1;
                    try {
                        ArgumentsList arg = o.getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Object sample = arg.get(1).isEmpty(mind) ? null : arg.get(1).getValue(mind).getValue();
                        Double result = arg.get(2).isEmpty(mind) ? null : (Double) arg.get(2).getValue(mind).getValue();

                        if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && arg.get(2).isEmpty(mind)) {
                            if (!o.setParameter(2, mind.getTerms().add((_indexOf(src, sample))))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind) && isDefined(arg.get(2))) {
                            if (!o.setParameter(1, mind.getTerms().add(_substring(src, result.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2))) {
                            if (result == _indexOf(src, sample)) {
                                ret = 2;
                            } else {
                                if (arg.get(1).getType() == ArgumentType.TVARIABLE) {
                                    TValue v = addTValue(arg.get(1), mind.getTerms().add(_substring(src, result.intValue(), 0)));
                                    Operation.showLog((IUnit) o, v);
                                }
                                ret = 0;
                            }
                        } else {
                            ret = 0;
                        }
                    } catch (Exception ex) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("replace(3)", new Operation(LibMode.FUNCTION, "replace", 3, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                    Object target = arg.get(1).isEmpty(mind) ? null : arg.get(1).getValue(mind).getValue();
                    Object replacement = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();
                    Object result = arg.get(3).isEmpty(mind) ? null : arg.get(3).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && arg.get(3).isEmpty(mind)) {
                        if (!o.setParameter(3, mind.getTerms().add(_replaceAll(src, target, replacement)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && isDefined(arg.get(3))) {
                        if (!o.setParameter(0, mind.getTerms().add(_replaceAll(result, replacement, target)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1)) && isDefined(arg.get(2)) && isDefined(arg.get(3))) {
                        if (_equals(result, _replaceAll(src, target, replacement))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), mind.getTerms().add(_replaceAll(result, replacement, target)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("chr(1)", new Operation(LibMode.FUNCTION, "chr", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    Double src = arg.get(0).isEmpty(mind) ? null : (Double) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add(String.format("%c", src.intValue())))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, mind.getTerms().add((int) result.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (result.equals(String.format("%c", src.intValue()))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), mind.getTerms().add((int) result.charAt(0)));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("asc(1)", new Operation(LibMode.FUNCTION, "asc", 1, new IReactor<Function>() {
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    Double result = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add((int) src.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && isDefined(arg.get(1))) {
                        if (!o.setParameter(0, mind.getTerms().add(String.format("%c", result.intValue())))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (result == src.charAt(0)) {
                            ret = 2;
                        } else {
                            if (arg.get(0).getType() == ArgumentType.TVARIABLE) {
                                TValue v = addTValue(arg.get(0), mind.getTerms().add(String.format("%c", result.intValue())));
                                Operation.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        ////////// дата и время
        {
            put("now(0)", new Operation(LibMode.FUNCTION, "now", 0, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    if (!o.setParameter(0, _now())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("tz(0)", new Operation(LibMode.FUNCTION, "tz", 0, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    if (!o.setParameter(0, _tz())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        ////////// разное
        {
            put("type(1)", new Operation(LibMode.FUNCTION, "type", 1, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, mind.getTerms().add(arg.get(0).getValue(mind).getType().name().toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (arg.get(0).getValue(mind).getType().name().toLowerCase().equals(arg.get(1).getValue(mind).toString().toLowerCase())) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("md5(1)", new Operation(LibMode.FUNCTION, "md5", 1, new IReactor<Function>() {
                @Override
                public Object run(Function o) throws Exception {
                    int ret = 1;
                    ArgumentsList arg = o.getArguments();

                    if (isDefined(arg.get(0)) && arg.get(1).isEmpty(mind)) {
                        if (!o.setParameter(1, _md5(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (isDefined(arg.get(0)) && isDefined(arg.get(1))) {
                        if (arg.get(1).getValue(mind).equals(_md5(arg.get(0).getValue(mind)))) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

    };

    public Functions(Mind mind) {
        this.mind = mind;
    }

    public Map<String, Operation> getSysOps() {
        return sysOps;
    }

    public boolean isDefined(IArgument a) throws Exception {
        Term t = (Term) a.getValue(mind);
        return t != null && !t.isCVariable();
    }

    public TValue addTValue(IArgument a, ITerm t) throws Exception {
        TValue s = null;
        if (a.getType() == ArgumentType.TVARIABLE) {
            TVariable tv = (TVariable) ((Argument) a).getObject(mind);
            s = mind.getTValues().find(tv, t);
            if (s == null) {
                s = mind.getTValues().add(tv, t);

                List<TValue> list = new ArrayList<>();
                list.add(s);
                mind.addTSolve(list);

            } else {
                s = null;
            }
        }
        return s;

    }

    protected boolean _startsWith(Object a, Object b) {
        if (a instanceof String && b instanceof String) {
            return ((String) a).startsWith((String) b);
        } else if (a instanceof byte[] && b instanceof byte[]) {
            if (((byte[]) a).length < ((byte[]) b).length) {
                return false;
            }
            for (int i = 0; i < ((byte[]) b).length; ++i) {
                if (((byte[]) a)[i] != ((byte[]) b)[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    protected boolean _endsWith(Object a, Object b) {
        if (a instanceof String && b instanceof String) {
            return ((String) a).endsWith((String) b);
        } else if (a instanceof byte[] && b instanceof byte[]) {
            if (((byte[]) a).length < ((byte[]) b).length) {
                return false;
            }
            int offset = ((byte[]) a).length - ((byte[]) b).length;
            for (int i = 0; i < ((byte[]) b).length; ++i) {
                if (((byte[]) a)[offset + i] != ((byte[]) b)[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    protected int __length(Object src) {
        if (src instanceof String) {
            return ((String) src).length();
        } else if (src instanceof byte[]) {
            return ((byte[]) src).length;
        } else {
            return 0;
        }
    }

    protected boolean _equals(Object a, Object b) {
        if (a instanceof String && b instanceof String) {
            return a.equals(b);
        } else if (a instanceof byte[] && b instanceof byte[]) {
            if (((byte[]) a).length != ((byte[]) b).length) {
                return false;
            }
            for (int i = 0; i < ((byte[]) a).length; ++i) {
                if (((byte[]) a)[i] != ((byte[]) b)[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    protected Object _substring(Object src, int start, int length) {
        if (src instanceof String) {
            if (length > 0) {
                return ((String) src).substring(start, start + length);
            } else {
                return ((String) src).substring(start);
            }
        } else if (src instanceof byte[]) {
            int len = length > 0 ? length : ((byte[]) src).length - start;
            if (len > 0) {
                byte[] buffer = new byte[len];
                System.arraycopy(src, start, buffer, 0, len);
                return buffer;
            } else {
                return src;
            }
        } else {
            return src;
        }
    }

    protected Object _replaceAll(Object src, Object target, Object replacement) {
        if (src instanceof String) {
            return ((String) src).replaceAll((String) replacement, (String) target);
        } else if (src instanceof byte[]) {
            int pos;
            while ((pos = _indexOf(src, target)) != -1) {
                int len = ((byte[]) src).length - ((byte[]) target).length + ((byte[]) replacement).length;
                if (len > 0) {
                    byte[] buffer = new byte[len];
                    if (pos > 0) {
                        System.arraycopy(src, 0, buffer, 0, pos);
                    }
                    System.arraycopy(replacement, 0, buffer, pos, ((byte[]) replacement).length);
                    if (pos + ((byte[]) target).length < ((byte[]) src).length) {
                        System.arraycopy(src, pos + ((byte[]) target).length, buffer, pos + ((byte[]) replacement).length,
                                ((byte[]) src).length - pos - ((byte[]) target).length);
                    }
                    src = buffer;
                } else {
                    return src;
                }
            }
            return src;
        } else {
            return src;
        }
    }

    protected int _indexOf(Object src, Object sample) {
        if (src instanceof String && sample instanceof String) {
            return ((String) src).indexOf((String) sample);
        } else if (src instanceof byte[] && sample instanceof byte[]) {
            return mind.getCalculator().getPredicates().indexOf((byte[]) src, (byte[]) sample);
        } else {
            return -1;
        }
    }

    private ITerm _min(ITerm a, ITerm b) {
        if (a.compareTo(b) > 0) {
            return b;
        } else {
            return a;
        }
    }

    private ITerm _max(ITerm a, ITerm b) {
        if (a.compareTo(b) > 0) {
            return a;
        } else {
            return b;
        }
    }

    protected ITerm _add(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() + (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.PERIOD) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), 1);
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), (String) a.getValue(), 1);
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.NUMERIC) {
            res = Tools.dateAdd((Date) a.getValue(), Tools.timeToInterval(((Double) b.getValue()).longValue()), 1);
        } else if (a.getType() == DataType.NUMERIC && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), Tools.timeToInterval(((Double) a.getValue()).longValue()), 1);
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.PERIOD) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) + Tools.intervalToTime((String) b.getValue()));
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.NUMERIC) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) + ((Double) b.getValue()).longValue());
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.PERIOD) {
            res = Tools.timeToInterval(((Double) a.getValue()).longValue() + Tools.intervalToTime((String) b.getValue()));
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.INTERVAL) {
            ITerm[] list = new Term[2];
            List<ITerm> aa = (List<ITerm>) a.getValue();
            List<ITerm> bb = (List<ITerm>) b.getValue();
            boolean backward = aa.get(0).compareTo(aa.get(1)) > 0 && bb.get(0).compareTo(bb.get(1)) > 0;
            list[0] = _min(_min(aa.get(0), aa.get(1)), _min(bb.get(0), bb.get(1)));
            list[1] = _max(_max(aa.get(0), aa.get(1)), _max(bb.get(0), bb.get(1)));
            if (backward) {
                ITerm tmp = list[0];
                list[0] = list[1];
                list[1] = tmp;
            }
            res = list;
        } else if (a.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            for (Term t : (Collection<Term>) a.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(n, mind)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (ITerm t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                if (!list.contains(t, mind)) {
                    list.add(new Argument(t));
                }
            }
            res = list;
        } else if (b.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            for (Term t : (Collection<Term>) b.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(n, mind)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (ITerm t : mind.getCalculator().getPredicates().expand(a, null, false)) {
                if (!list.contains(t, mind)) {
                    list.add(new Argument(t));
                }
            }
            res = list;
        } else if (a.getType() == DataType.INTERVAL) {
            if (b.getType() == ((List<Term>) a.getValue()).get(0).getType()) {
                ITerm[] list = new Term[2];
                List<ITerm> aa = (List<ITerm>) a.getValue();
                list[0] = _min(_min(aa.get(0), aa.get(1)), b);
                list[1] = _max(_max(aa.get(0), aa.get(1)), b);
                boolean backward = aa.get(0).compareTo(aa.get(1)) > 0;
                if (backward) {
                    ITerm tmp = list[0];
                    list[0] = list[1];
                    list[1] = tmp;
                }
                res = list;
            } else {
                ArgumentsList list = new ArgumentsList();
                for (ITerm t : mind.getCalculator().getPredicates().expand(a, null, false)) {
                    if (!list.contains(t, mind)) {
                        list.add(new Argument(t));
                    }
                }
                list.add(new Argument(b));
                res = list;
            }
        } else if (b.getType() == DataType.INTERVAL) {
            if (a.getType() == ((List<Term>) b.getValue()).get(0).getType()) {
                ITerm[] list = new Term[2];
                List<ITerm> bb = (List<ITerm>) b.getValue();
                list[0] = _min(_min(bb.get(0), bb.get(1)), a);
                list[1] = _max(_max(bb.get(0), bb.get(1)), a);
                boolean backward = bb.get(0).compareTo(bb.get(1)) > 0;
                if (backward) {
                    ITerm tmp = list[0];
                    list[0] = list[1];
                    list[1] = tmp;
                }
                res = list;
            } else {
                ArgumentsList list = new ArgumentsList();
                for (ITerm t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                    if (!list.contains(t, mind)) {
                        list.add(new Argument(t));
                    }
                }
                list.add(new Argument(a));
                res = list;
            }
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            byte[] buffer = new byte[((byte[]) a.getValue()).length + ((byte[]) b.getValue()).length];
            System.arraycopy(a.getValue(), 0, buffer, 0, ((byte[]) a.getValue()).length);
            System.arraycopy(b.getValue(), 0, buffer, ((byte[]) a.getValue()).length, ((byte[]) b.getValue()).length);
            res = buffer;
        } else if (a.getType() == DataType.STRING || b.getType() == DataType.STRING) {
            res = a.getValue().toString() + b.getValue().toString();
        } else {
            throw new RuntimeErrorException("Incompatible types for addition: " + a.getType() + " + " + b.getType());
        }
        return mind.getTerms().add(res);
    }

    protected ITerm _inc(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() + 1;
        } else if (a.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) a.getValue(), "1 day", 1);
        } else if (a.getType() == DataType.PERIOD) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) + Tools.intervalToTime("1 day"));
        } else if (a.getType() == DataType.STRING && a.getValue().toString().length() == 1) {
            res = String.format("%c", a.getValue().toString().charAt(0) + 1);
        } else {
            res = a.getValue();
        }
        return mind.getTerms().add(res);
    }

    protected ITerm _dec(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() - 1;
        } else if (a.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) a.getValue(), "1 day", -1);
        } else if (a.getType() == DataType.PERIOD) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) - Tools.intervalToTime("1 day"));
        } else if (a.getType() == DataType.STRING && a.getValue().toString().length() == 1) {
            res = String.format("%c", a.getValue().toString().charAt(0) - 1);
        } else {
            res = a.getValue();
        }
        return mind.getTerms().add(res);
    }

    protected ITerm _sub(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() - (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.PERIOD) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), -1);
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.NUMERIC) {
            res = Tools.dateAdd((Date) a.getValue(), Tools.timeToInterval(((Double) b.getValue()).longValue()), -1);
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.NUMERIC) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) - ((Double) b.getValue()).longValue());
        } else if (a.getType() == DataType.NUMERIC && b.getType() == DataType.PERIOD) {
            res = ((Double) b.getValue()).longValue() - Tools.intervalToTime((String) a.getValue());
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.PERIOD) {
            res = Tools.timeToInterval(Tools.intervalToTime((String) a.getValue()) - Tools.intervalToTime((String) b.getValue()));
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.DATE) {
            res = Tools.dateDiff((Date) b.getValue(), (Date) a.getValue());
        } else if (a.getType() == DataType.INTERVAL && b.getType() == ((List<Term>) a.getValue()).get(0).getType()) {
            ArgumentsList list = new ArgumentsList();
            for (ITerm n : mind.getCalculator().getPredicates().expand(a, null, false)) {
                list.add(new Argument(n));
            }
            list.remove(b, mind);
            res = list;
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.INTERVAL) {
            ArgumentsList list = new ArgumentsList();
            for (ITerm n : mind.getCalculator().getPredicates().expand(a, null, false)) {
                list.add(new Argument(n));
            }
            for (ITerm n : mind.getCalculator().getPredicates().expand(b, null, false)) {
                list.remove(n, mind);
            }
            res = list;
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            for (ITerm n : mind.getCalculator().getPredicates().expand(a, null, false)) {
                list.add(new Argument(n));
            }
            for (Term t : (Collection<Term>) b.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    list.remove(n, mind);
                }
            }
            res = list;
        } else if (a.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            for (Term t : (Collection<Term>) a.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(n, mind)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (ITerm t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                list.remove(t, mind);
            }
            res = list;
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            int pos = mind.getCalculator().getPredicates().indexOf((byte[]) a.getValue(), (byte[]) b.getValue());
            if (pos != -1) {
                int size = ((byte[]) a.getValue()).length - ((byte[]) b.getValue()).length;
                if (size > 0) {
                    byte[] buffer = new byte[size];
                    if (pos > 0) {
                        System.arraycopy(a.getValue(), 0, buffer, 0, pos);
                    }
                    if (pos + ((byte[]) b.getValue()).length < ((byte[]) a.getValue()).length) {
                        System.arraycopy(a.getValue(), pos + ((byte[]) b.getValue()).length, buffer, pos, size - pos);
                    }
                    res = buffer;
                } else {
                    res = a.getValue();
                }
            } else {
                res = a.getValue();
            }
        } else if (a.getType() == DataType.STRING) {
            res = a.getValue().toString().replace(b.getValue().toString(), "");
        } else {
            throw new RuntimeErrorException("Incompatible types for subtraction: " + a.getType() + " - " + b.getType());
        }
        return mind.getTerms().add(res);
    }

    private ITerm _mul(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() * (double) b.getValue();
        } else if (a.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            ArgumentsList result = new ArgumentsList();
            for (ITerm t : (Collection<Term>) a.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(n, mind)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (ITerm t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                if (list.contains(t, mind)) {
                    result.add(new Argument(t));
                }
            }
            res = result;
        } else if (b.getType() == DataType.SET) {
            ArgumentsList list = new ArgumentsList();
            ArgumentsList result = new ArgumentsList();
            for (ITerm t : (Collection<Term>) b.getValue()) {
                for (ITerm n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(n, mind)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (ITerm t : mind.getCalculator().getPredicates().expand(a, null, false)) {
                if (list.contains(t, mind)) {
                    result.add(new Argument(t));
                }
            }
            res = result;
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _div(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() / (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _rem(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC || b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() % (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _neg(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = -(double) a.getValue();
        } else if (a.getType() == DataType.INTERVAL) {
            Term[] list = new Term[2];
            list[0] = ((List<Term>) a.getValue()).get(1);
            list[1] = ((List<Term>) a.getValue()).get(0);
            res = list;
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitnot(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Long.valueOf(~(long) a.getValue()).doubleValue();
        } else if (a.getType() == DataType.BLOB) {
            if (((byte[]) a.getValue()).length > 0) {
                byte[] buffer = new byte[((byte[]) a.getValue()).length];
                for (int i = 0; i < ((byte[]) a.getValue()).length; ++i) {
                    buffer[i] = (byte) ((~((byte[]) a.getValue())[i]) & 0xFF);
                }
                res = buffer;
            } else {
                res = a.getValue();
            }
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitleft(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() << (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitright(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() >> (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitxor(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() ^ (long) b.getValue()).doubleValue();
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            if (((byte[]) a.getValue()).length > 0 && ((byte[]) b.getValue()).length > 0) {
                byte[] buffer = new byte[((byte[]) a.getValue()).length];
                int k = 0;
                for (int i = 0; i < ((byte[]) a.getValue()).length; ++i) {
                    buffer[i] = (byte) ((((byte[]) a.getValue())[i] ^ (((byte[]) b.getValue())[k++])) & 0xFF);
                    if (k >= ((byte[]) b.getValue()).length) {
                        k = 0;
                    }
                }
                res = buffer;
            } else {
                res = a.getValue();
            }
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitand(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() & (long) b.getValue()).doubleValue();
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            if (((byte[]) a.getValue()).length > 0 && ((byte[]) b.getValue()).length > 0) {
                byte[] buffer = new byte[((byte[]) a.getValue()).length];
                int k = 0;
                for (int i = 0; i < ((byte[]) a.getValue()).length; ++i) {
                    buffer[i] = (byte) ((((byte[]) a.getValue())[i] & (((byte[]) b.getValue())[k++])) & 0xFF);
                    if (k >= ((byte[]) b.getValue()).length) {
                        k = 0;
                    }
                }
                res = buffer;
            } else {
                res = a.getValue();
            }
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitor(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() | (long) b.getValue()).doubleValue();
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            if (((byte[]) a.getValue()).length > 0 && ((byte[]) b.getValue()).length > 0) {
                byte[] buffer = new byte[((byte[]) a.getValue()).length];
                int k = 0;
                for (int i = 0; i < ((byte[]) a.getValue()).length; ++i) {
                    buffer[i] = (byte) ((((byte[]) a.getValue())[i] | (((byte[]) b.getValue())[k++])) & 0xFF);
                    if (k >= ((byte[]) b.getValue()).length) {
                        k = 0;
                    }
                }
                res = buffer;
            } else {
                res = a.getValue();
            }
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _bitandnot(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() & ~(long) b.getValue()).doubleValue();
        } else if (a.getType() == DataType.BLOB && b.getType() == DataType.BLOB) {
            if (((byte[]) a.getValue()).length > 0 && ((byte[]) b.getValue()).length > 0) {
                byte[] buffer = new byte[((byte[]) a.getValue()).length];
                int k = 0;
                for (int i = 0; i < ((byte[]) a.getValue()).length; ++i) {
                    buffer[i] = (byte) ((((byte[]) a.getValue())[i] & ~(((byte[]) b.getValue())[k++])) & 0xFF);
                    if (k >= ((byte[]) b.getValue()).length) {
                        k = 0;
                    }
                }
                res = buffer;
            } else {
                res = a.getValue();
            }
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _log(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.log((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _exp(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.exp((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _log10(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.log10((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _exp10(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.pow(10.0, (double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _pi() throws Exception {
        Object res = Math.PI;
        return mind.getTerms().add(res);
    }

    private ITerm _sin(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _asin(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.asin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _cos(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.cos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _acos(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.acos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _tan(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.tan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _atan(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.atan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _abs(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.abs((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    /**
     * @param a
     * @param param NUMERIC, игнорируются, STRING - 10,16 или 8, BLOB - 0 - little-endian, 1-big-endian, DATE - 0-unixtime, 1-год, 2-месяц, 3-день, 4-час, 5-минута, 6-секунда, 7-мс
     * @return
     * @throws Exception
     */
    private ITerm _int(ITerm a, ITerm param) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = ((Double) a.getValue()).longValue();
        } else if (a.getType() == DataType.STRING) {
            if (((String) a.getValue()).contains(".")) {
                res = Double.valueOf((String) a.getValue()).longValue();
            } else if (param != null && param.getType() == DataType.NUMERIC) {
                res = Long.valueOf((String) a.getValue(), ((Double) param.getValue()).intValue());
            } else {
                try {
                    res = Long.valueOf((String) a.getValue());
                } catch (Exception ex) {
                    TimeZone tz = TimeZone.getTimeZone((String) a.getValue());
                    res = tz.getOffset(System.currentTimeMillis());
                }
            }
        } else if (a.getType() == DataType.BLOB) {
            long val = 0;
            int pos = 0;
            for (byte b : (byte[]) a.getValue()) {
                if (param == null || ((Double) param.getValue()).intValue() == 0) {
                    val |= ((long) b & 0xFF) << (pos * Byte.SIZE);
                } else {
                    val <<= Byte.SIZE;
                    val |= (b & 0xFF);
                }
                if (++pos >= Long.BYTES) {
                    break;
                }
            }
            res = Long.valueOf(val);
        } else if (a.getType() == DataType.DATE) {
            if (param == null || param.getType() != DataType.STRING || !Enums.INTERVALS.containsKey(((String) param.getValue()).toLowerCase())) {
                res = ((Date) a.getValue()).getTime();
            } else {
                switch (Enums.INTERVALS.get(((String) param.getValue()).toLowerCase()).intValue()) {
                    case (int) Enums.INTERVAL_YEAR:
                        res = Tools.getYear(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_MONTH:
                        res = Tools.getMonth(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_DAY:
                        res = Tools.getDay(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_HOUR:
                        res = Tools.getHour(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_MINUTE:
                        res = Tools.getMinute(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_SECOND:
                        res = Tools.getSecond(((Date) a.getValue()));
                        break;
                    case (int) Enums.INTERVAL_MILLISECOND:
                        res = Tools.getMillisecond(((Date) a.getValue()));
                        break;
                    default:
                        res = ((Date) a.getValue()).getTime();
                }
            }
        } else if (a.getType() == DataType.PERIOD) {
            res = Tools.intervalToTime((String) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _blob(ITerm a, ITerm param) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC || a.getType() == DataType.DATE) {
            byte[] buffer = new byte[Long.BYTES];
            long val = a.getType() == DataType.NUMERIC ? ((Double) a.getValue()).longValue() : ((Date) a.getValue()).getTime();
            for (int pos = 0; pos < Long.BYTES; ++pos) {
                if (param == null || ((Double) param.getValue()).longValue() == 0) {
                    buffer[pos] = (byte) ((val >> (pos * Byte.SIZE)) & 0xFF);
                } else {
                    buffer[Long.BYTES - pos - 1] = (byte) ((val >> (pos * Byte.SIZE)) & 0xFF);
                }
            }
            res = buffer;
        } else if (a.getType() == DataType.STRING) {
            if (param != null && param.getType() == DataType.STRING) {
                res = ((String) a.getValue()).getBytes((String) param.getValue());
            } else {
                res = ((String) a.getValue()).getBytes();
            }
        } else {
            res = new byte[]{};
        }
        return mind.getTerms().add(res);
    }

    private ITerm _string(ITerm a, ITerm param) throws Exception {
        Object res;
        if (a.getType() == DataType.BLOB) {
            if (param != null && param.getType() == DataType.STRING) {
                res = new String((byte[]) a.getValue(), (String) param.getValue());
            } else {
                res = new String((byte[]) a.getValue());
            }
        } else if (a.getType() == DataType.NUMERIC) {
            if (param != null && param.getType() == DataType.NUMERIC) {
                res = Long.toString(((Double) a.getValue()).longValue(), ((Double) param.getValue()).intValue());
            } else if (((Double) a.getValue()).longValue() == (Double) a.getValue()) {
                res = "\"" + ((Double) a.getValue()).longValue() + "\"";
            } else {
                res = "\"" + a.getValue().toString() + "\"";
            }
        } else {
            res = a.getValue().toString();
        }
        return mind.getTerms().add(res);
    }

    private ITerm _date(ITerm a, ITerm param) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = new Date(((Double) a.getValue()).longValue());
        } else if (a.getType() == DataType.BLOB) {
            res = new Date(((Double) _int(a, param).getValue()).longValue());
        } else if (a.getType() == DataType.DATE) {
            res = a;
        } else {
            res = new Date(0);
        }
        return mind.getTerms().add(res);
    }

    private ITerm _round(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            Double val;
            if (b != null) {
                val = (double) a.getValue() * Math.pow(10, (double) b.getValue());
                Long r = Math.round(val);
                val = r / Math.pow(10, (double) b.getValue());
            } else {
                val = (double) Math.round((double) a.getValue());
            }
            res = val;
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _sqrt(ITerm a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sqrt((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _pow(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _root(ITerm a, ITerm b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), 1.0 / (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private ITerm _now() throws Exception {
        Object res = new Date(System.currentTimeMillis());
        return mind.getTerms().add(res);
    }

    private ITerm _tz() throws Exception {
        Object res = TimeZone.getDefault().getID();
        return mind.getTerms().add(res);
    }

    private ITerm _md5(ITerm a) throws Exception {
        ITerm res = null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(a.getType() == DataType.BLOB ? (byte[]) a.getValue() : a.toString().getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100), 1, 3);
            }
            res = mind.getTerms().add(sb.toString());
        } catch (java.security.NoSuchAlgorithmException ex) {
            ex.printStackTrace(System.err);
        }
        return res;
    }

    private ITerm _length(ITerm a, ITerm step) throws Exception {
        Object res = null;
        if (a.getType() == DataType.STRING) {
            res = a.getValue().toString().length();
        } else if (a.getType() == DataType.BLOB) {
            res = ((byte[]) a.getValue()).length;
        } else if (a.getType() == DataType.INTERVAL) {
            res = mind.getCalculator().getPredicates().expand(a, step, false).size();
        } else if (a.getType() == DataType.SET) {
            res = mind.getCalculator().getPredicates().expand(a, step, false).size();
        }
        return mind.getTerms().add(res);
    }

    private ITerm _step(ITerm a, ITerm size) throws Exception {
        Object res = null;
        if (a.getType() == DataType.INTERVAL) {
            List<ITerm> list = mind.getCalculator().getPredicates().expand(a, null, false);
            if (list.size() > 1) {
                ITerm dif;
                if (list.get(0).compareTo(list.get(list.size() - 1)) > 0) {
                    dif = _sub(list.get(0), _dec(list.get(list.size() - 1)));
                } else {
                    dif = _sub(list.get(list.size() - 1), _dec(list.get(0)));
                }
                res = _div(dif, size);
            }
        }
        return mind.getTerms().add(res);
    }
}
