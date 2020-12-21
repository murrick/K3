package org.kanger.calculator;

import org.kanger.Mind;
import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.LibMode;
import org.kanger.enums.Tools;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.units.Function;
import org.kanger.units.SysOp;
import org.kanger.units.TValue;
import org.kanger.units.Term;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 18.01.17.
 */
public class Functions {

    private final Mind mind;
    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {


        /// Арифметика
        {
            put("_inc(1)", new SysOp(LibMode.FUNCTION, "_inc", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _inc(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _dec(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_inc(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _dec(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
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
            put("_dec(1)", new SysOp(LibMode.FUNCTION, "_dec", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _dec(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _inc(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_dec(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _inc(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitnot(1)", new SysOp(LibMode.FUNCTION, "_bitnot", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _bitnot(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!((Function) o).setParameter(0, _bitnot(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_bitnot(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _bitnot(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_neg(1)", new SysOp(LibMode.FUNCTION, "_neg", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _neg(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!((Function) o).setParameter(0, _neg(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_neg(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _neg(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_val(1)", new SysOp(LibMode.FUNCTION, "_val", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, arg.get(0).getValue(mind))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) /*&& !arg.get(0).isCVar(mind)*/) {
                        if (!((Function) o).setParameter(0, arg.get(1).getValue(mind))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (arg.get(0).getValue(mind).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, arg.get(1).getValue(mind));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_add(2)", new SysOp(LibMode.FUNCTION, "_add", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _add(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _sub(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(1, _sub(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_add(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _sub(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).isTSet()) {
                                TValue v = arg.get(1).addValue(mind, _sub(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
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
            put("_sub(2)", new SysOp(LibMode.FUNCTION, "_sub", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _sub(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _add(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(1, _sub(arg.get(0).getValue(mind), arg.get(2).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_sub(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _add(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).isTSet()) {
                                TValue v = arg.get(1).addValue(mind, _sub(arg.get(0).getValue(mind), arg.get(2).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
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
            put("_mul(2)", new SysOp(LibMode.FUNCTION, "_mul", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _mul(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!((Function) o).setParameter(0, _div(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && (double) arg.get(0).getValue(mind).getValue() != 0) {
                        if (!((Function) o).setParameter(1, _div(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_mul(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet() && (double) arg.get(1).getValue(mind).getValue() != 0) {
                                TValue v = arg.get(0).addValue(mind, _div(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).isTSet() && (double) arg.get(0).getValue(mind).getValue() != 0) {
                                TValue v = arg.get(1).addValue(mind, _div(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
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
            put("_div(2)", new SysOp(LibMode.FUNCTION, "_div", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!((Function) o).setParameter(2, _div(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _mul(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && (double) arg.get(2).getValue(mind).getValue() != 0) {
                        if (!((Function) o).setParameter(1, _div(arg.get(0).getValue(mind), arg.get(2).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_div(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _mul(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).isTSet() && (double) arg.get(2).getValue(mind).getValue() != 0) {
                                TValue v = arg.get(0).addValue(mind, _div(arg.get(0).getValue(mind), arg.get(2).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
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
            put("_rem(2)", new SysOp(LibMode.FUNCTION, "_rem", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind) && (double) arg.get(1).getValue(mind).getValue() != 0) {
                        if (!((Function) o).setParameter(2, _rem(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_rem(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_iv(2)", new SysOp(LibMode.FUNCTION, "_iv", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, mind.getTerms().add(new Term[]{arg.get(0).getValue(mind), arg.get(1).getValue(mind)}))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL && arg.get(1).getValue(mind).compareTo(((List<Term>) arg.get(2).getValue(mind).getValue()).get(1)) == 0) {
                        if (!((Function) o).setParameter(0, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(0))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL && arg.get(0).getValue(mind).compareTo(((List<Term>) arg.get(2).getValue(mind).getValue()).get(1)) == 0) {
                        if (!((Function) o).setParameter(1, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL) {
                        if (!((Function) o).setParameter(0, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(0)) && !((Function) o).setParameter(1, ((List<Term>) arg.get(2).getValue(mind).getValue()).get(1))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(2).getValue(mind).getType() == DataType.INTERVAL
                            && mind.getTerms().add(new Term[]{arg.get(0).getValue(mind), arg.get(1).getValue(mind)}).compareTo(arg.get(2).getValue(mind)) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_is(0)", new SysOp(LibMode.FUNCTION, "_is", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();

                    ArgList tmp = new ArgList();
                    for (int i = 0; i < arg.size() - 1; ++i) {
                        if (arg.get(i).isEmpty(mind)) {
                            ret = 0;
                            break;
                        }
                        tmp.add(arg.get(i));
                    }

                    if (ret != 0 && arg.get(arg.size() - 1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(arg.size() - 1, mind.getTerms().add(tmp))) {
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
            put("_bitleft(2)", new SysOp(LibMode.FUNCTION, "_bitleft", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _bitleft(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _bitright(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_bitleft(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _bitright(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitright(2)", new SysOp(LibMode.FUNCTION, "_bitright", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _bitright(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _bitleft(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_bitright(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _bitleft(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitxor(2)", new SysOp(LibMode.FUNCTION, "_bitxor", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _bitxor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _bitxor(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_bitxor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _bitxor(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitand(2)", new SysOp(LibMode.FUNCTION, "_bitand", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _bitand(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_bitand(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitor(2)", new SysOp(LibMode.FUNCTION, "_bitor", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _bitor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _bitandnot(arg.get(2).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(1, _bitandnot(arg.get(2).getValue(mind), arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_bitor(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _bitandnot(arg.get(2).getValue(mind), arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            if (arg.get(1).isTSet()) {
                                TValue v = arg.get(1).addValue(mind, _bitandnot(arg.get(2).getValue(mind), arg.get(0).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(2).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("log(1)", new SysOp(LibMode.FUNCTION, "log", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _log(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _exp(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_log(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _exp(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("exp(1)", new SysOp(LibMode.FUNCTION, "exp", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _exp(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _log(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_exp(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _log(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("log10(1)", new SysOp(LibMode.FUNCTION, "log10", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _log10(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _exp10(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_log10(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _exp10(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("exp10(1)", new SysOp(LibMode.FUNCTION, "exp10", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _exp10(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _log10(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_exp10(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _log10(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pi(0)", new SysOp(LibMode.FUNCTION, "pi", 0, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    if (!((Function) o).setParameter(0, _pi())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sin(1)", new SysOp(LibMode.FUNCTION, "sin", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _sin(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _asin(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_sin(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _asin(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("asin(1)", new SysOp(LibMode.FUNCTION, "asin", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _asin(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _sin(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_asin(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _sin(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("cos(1)", new SysOp(LibMode.FUNCTION, "cos", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _cos(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _acos(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_cos(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _acos(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("acos(1)", new SysOp(LibMode.FUNCTION, "acos", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _acos(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _cos(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_acos(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _cos(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("tan(1)", new SysOp(LibMode.FUNCTION, "tan", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _tan(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _atan(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_tan(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _atan(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("atan(1)", new SysOp(LibMode.FUNCTION, "atan", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _atan(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _tan(arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_atan(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _tan(arg.get(1).getValue(mind)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("int(1)", new SysOp(LibMode.FUNCTION, "int", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _int(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_int(arg.get(0).getValue(mind), null).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("int(2)", new SysOp(LibMode.FUNCTION, "int", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _int(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_int(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("blob(1)", new SysOp(LibMode.FUNCTION, "blob", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _blob(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_blob(arg.get(0).getValue(mind), null).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("blob(2)", new SysOp(LibMode.FUNCTION, "blob", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _blob(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_blob(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("date(1)", new SysOp(LibMode.FUNCTION, "date", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _date(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_date(arg.get(0).getValue(mind), null).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("date(2)", new SysOp(LibMode.FUNCTION, "date", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _date(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_date(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("string(1)", new SysOp(LibMode.FUNCTION, "string", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _string(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_string(arg.get(0).getValue(mind), null).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("string(2)", new SysOp(LibMode.FUNCTION, "string", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _string(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_string(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("abs(1)", new SysOp(LibMode.FUNCTION, "abs", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _abs(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_abs(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(2)", new SysOp(LibMode.FUNCTION, "round", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _round(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_round(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(1)", new SysOp(LibMode.FUNCTION, "round", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _round(arg.get(0).getValue(mind), null))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_round(arg.get(0).getValue(mind), null).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sqrt(1)", new SysOp(LibMode.FUNCTION, "sqrt", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _sqrt(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _pow(arg.get(1).getValue(mind), mind.getTerms().add(2)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (_sqrt(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _pow(arg.get(1).getValue(mind), mind.getTerms().add(2)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pow(2)", new SysOp(LibMode.FUNCTION, "pow", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _pow(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _root(arg.get(2).getValue(mind), mind.getTerms().add(1)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_pow(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _root(arg.get(2).getValue(mind), mind.getTerms().add(1)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("root(2)", new SysOp(LibMode.FUNCTION, "root", 2, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                        if (!((Function) o).setParameter(2, _root(arg.get(0).getValue(mind), arg.get(1).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, _pow(arg.get(2).getValue(mind), mind.getTerms().add(1)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                        if (_root(arg.get(0).getValue(mind), arg.get(1).getValue(mind)).compareTo(arg.get(2).getValue(mind)) == 0) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, _pow(arg.get(2).getValue(mind), mind.getTerms().add(1)));
                                SysOp.showLog((IUnit) o, v);
                            }
                            ret = 0;
                        }
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        // String functions
        /// Строковые функции
        {
            put("length(1)", new SysOp(LibMode.FUNCTION, "length", 1, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                            if (!((Function) o).setParameter(1, _length(arg.get(0).getValue(mind)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                            if (_length(arg.get(0).getValue(mind)).compareTo(arg.get(1).getValue(mind)) == 0) {
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
            put("mid(2)", new SysOp(LibMode.FUNCTION, "mid", 2, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add(_substring(src, pos.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                            if (!((Function) o).setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                            if (_equals(result, _substring(src, pos.intValue(), 0))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).isTSet()) {
                                    TValue v = arg.get(1).addValue(mind, mind.getTerms().add(_indexOf(src, result)));
                                    SysOp.showLog((IUnit) o, v);
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
            put("mid(3)", new SysOp(LibMode.FUNCTION, "mid", 3, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Double len = arg.get(2).isEmpty(mind) ? null : (Double) arg.get(2).getValue(mind).getValue();
                        Object result = arg.get(3).isEmpty(mind) ? null : arg.get(3).getValue(mind).getValue();

                        if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(3).isEmpty(mind)) {
                            if (!((Function) o).setParameter(3, mind.getTerms().add(_substring(src, pos.intValue(), len.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && arg.get(3).isDefined(mind)) {
                            if (!((Function) o).setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind) && arg.get(3).isDefined(mind) && _indexOf(src, result) != -1) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add(_indexOf(src, result) + __length(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isEmpty(mind) && arg.get(3).isDefined(mind) && _indexOf(src, result) != -1) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add(_indexOf(src, result) + __length(result)))
                                    || !((Function) o).setParameter(1, mind.getTerms().add(_indexOf(src, result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(3).isDefined(mind)) {
                            if (_equals(result, _substring(src, pos.intValue(), len.intValue()))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).isTSet()) {
                                    TValue v = arg.get(1).addValue(mind, mind.getTerms().add(_indexOf(src, result)));
                                    SysOp.showLog((IUnit) o, v);
                                }
                                if (arg.get(2).isTSet()) {
                                    TValue v = arg.get(2).addValue(mind, mind.getTerms().add(_indexOf(src, result) + __length(result)));
                                    SysOp.showLog((IUnit) o, v);
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
            put("left(2)", new SysOp(LibMode.FUNCTION, "left", 2, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add(_substring(src, 0, pos.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && _startsWith(src, result)) {
                            if (!((Function) o).setParameter(1, mind.getTerms().add(__length(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                            if (_equals(result, _substring(src, 0, pos.intValue()))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).isTSet()) {
                                    TValue v = arg.get(1).addValue(mind, mind.getTerms().add(__length(result)));
                                    SysOp.showLog((IUnit) o, v);
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
            put("right(2)", new SysOp(LibMode.FUNCTION, "right", 2, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Double pos = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();
                        Object result = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();

                        if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add(_substring(src, __length(src) - pos.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind) && _endsWith(src, result)) {
                            if (!((Function) o).setParameter(1, mind.getTerms().add(__length(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                            if (_equals(result, _substring(src, __length(src) - pos.intValue(), 0))) {
                                ret = 2;
                            } else {
                                if (arg.get(1).isTSet()) {
                                    TValue v = arg.get(1).addValue(mind, mind.getTerms().add(__length(result)));
                                    SysOp.showLog((IUnit) o, v);
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
            put("trim(1)", new SysOp(LibMode.FUNCTION, "trim", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add(src.trim()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
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
            put("uc(1)", new SysOp(LibMode.FUNCTION, "uc", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add(src.toUpperCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
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
            put("lc(1)", new SysOp(LibMode.FUNCTION, "lc", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add(src.toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
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
            put("at(2)", new SysOp(LibMode.FUNCTION, "at", 2, new IReactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                        Object sample = arg.get(1).isEmpty(mind) ? null : arg.get(1).getValue(mind).getValue();
                        Double result = arg.get(2).isEmpty(mind) ? null : (Double) arg.get(2).getValue(mind).getValue();

                        if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isEmpty(mind)) {
                            if (!((Function) o).setParameter(2, mind.getTerms().add((_indexOf(src, sample))))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind) && arg.get(2).isDefined(mind)) {
                            if (!((Function) o).setParameter(1, mind.getTerms().add(_substring(src, result.intValue(), 0)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind)) {
                            if (result == _indexOf(src, sample)) {
                                ret = 2;
                            } else {
                                if (arg.get(1).isTSet()) {
                                    TValue v = arg.get(1).addValue(mind, mind.getTerms().add(_substring(src, result.intValue(), 0)));
                                    SysOp.showLog((IUnit) o, v);
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
            put("replace(3)", new SysOp(LibMode.FUNCTION, "replace", 3, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    Object src = arg.get(0).isEmpty(mind) ? null : arg.get(0).getValue(mind).getValue();
                    Object target = arg.get(1).isEmpty(mind) ? null : arg.get(1).getValue(mind).getValue();
                    Object replacement = arg.get(2).isEmpty(mind) ? null : arg.get(2).getValue(mind).getValue();
                    Object result = arg.get(3).isEmpty(mind) ? null : arg.get(3).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(3).isEmpty(mind)) {
                        if (!((Function) o).setParameter(3, mind.getTerms().add(_replaceAll(src, target, replacement)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(3).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, mind.getTerms().add(_replaceAll(result, replacement, target)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind) && arg.get(2).isDefined(mind) && arg.get(3).isDefined(mind)) {
                        if (_equals(result, _replaceAll(src, target, replacement))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, mind.getTerms().add(_replaceAll(result, replacement, target)));
                                SysOp.showLog((IUnit) o, v);
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
            put("chr(1)", new SysOp(LibMode.FUNCTION, "chr", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    Double src = arg.get(0).isEmpty(mind) ? null : (Double) arg.get(0).getValue(mind).getValue();
                    String result = arg.get(1).isEmpty(mind) ? null : (String) arg.get(1).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add(String.format("%c", src.intValue())))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, mind.getTerms().add((int) result.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (result.equals(String.format("%c", src.intValue()))) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, mind.getTerms().add((int) result.charAt(0)));
                                SysOp.showLog((IUnit) o, v);
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
            put("asc(1)", new SysOp(LibMode.FUNCTION, "asc", 1, new IReactor() {
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty(mind) ? null : (String) arg.get(0).getValue(mind).getValue();
                    Double result = arg.get(1).isEmpty(mind) ? null : (Double) arg.get(1).getValue(mind).getValue();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add((int) src.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isEmpty(mind) && arg.get(1).isDefined(mind)) {
                        if (!((Function) o).setParameter(0, mind.getTerms().add(String.format("%c", result.intValue())))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
                        if (result == src.charAt(0)) {
                            ret = 2;
                        } else {
                            if (arg.get(0).isTSet()) {
                                TValue v = arg.get(0).addValue(mind, mind.getTerms().add(String.format("%c", result.intValue())));
                                SysOp.showLog((IUnit) o, v);
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
            put("now(0)", new SysOp(LibMode.FUNCTION, "now", 0, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    if (!((Function) o).setParameter(0, _now())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        ////////// разное
        {
            put("type(1)", new SysOp(LibMode.FUNCTION, "type", 1, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, mind.getTerms().add(arg.get(0).getValue(mind).getType().name().toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
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
            put("md5(1)", new SysOp(LibMode.FUNCTION, "md5", 1, new IReactor() {
                @Override
                public Object run(Object o) throws Exception {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();

                    if (arg.get(0).isDefined(mind) && arg.get(1).isEmpty(mind)) {
                        if (!((Function) o).setParameter(1, _md5(arg.get(0).getValue(mind)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined(mind) && arg.get(1).isDefined(mind)) {
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

    public Map<String, SysOp> getSysOps() {
        return sysOps;
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
            return ((String) src).substring(start, start + length);
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

    private Term _min(Term a, Term b) {
        if (a.compareTo(b) > 0) {
            return b;
        } else {
            return a;
        }
    }

    private Term _max(Term a, Term b) {
        if (a.compareTo(b) > 0) {
            return a;
        } else {
            return b;
        }
    }

    protected Term _add(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() + (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.PERIOD) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), 1);
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), (String) a.getValue(), 1);
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.INTERVAL) {
            Term[] list = new Term[2];
            List<Term> aa = (List<Term>) a.getValue();
            List<Term> bb = (List<Term>) b.getValue();
            boolean backward = aa.get(0).compareTo(aa.get(1)) > 0 && bb.get(0).compareTo(bb.get(1)) > 0;
            list[0] = _min(_min(aa.get(0), aa.get(1)), _min(bb.get(0), bb.get(1)));
            list[1] = _max(_max(aa.get(0), aa.get(1)), _max(bb.get(0), bb.get(1)));
            if (backward) {
                Term tmp = list[0];
                list[0] = list[1];
                list[1] = tmp;
            }
            res = list;
        } else if (a.getType() == DataType.INTERVAL && b.getType() == ((List<Term>) a.getValue()).get(0).getType()) {
            Term[] list = new Term[2];
            List<Term> aa = (List<Term>) a.getValue();
            list[0] = _min(_min(aa.get(0), aa.get(1)), b);
            list[1] = _max(_max(aa.get(0), aa.get(1)), b);
            boolean backward = aa.get(0).compareTo(aa.get(1)) > 0;
            if (backward) {
                Term tmp = list[0];
                list[0] = list[1];
                list[1] = tmp;
            }
            res = list;
        } else if (b.getType() == DataType.INTERVAL && a.getType() == ((List<Term>) b.getValue()).get(0).getType()) {
            Term[] list = new Term[2];
            List<Term> bb = (List<Term>) b.getValue();
            list[0] = _min(_min(bb.get(0), bb.get(1)), a);
            list[1] = _max(_max(bb.get(0), bb.get(1)), a);
            boolean backward = bb.get(0).compareTo(bb.get(1)) > 0;
            if (backward) {
                Term tmp = list[0];
                list[0] = list[1];
                list[1] = tmp;
            }
            res = list;
        } else if (a.getType() == DataType.SET) {
            ArgList list = new ArgList();
            for (Term t : (Collection<Term>) a.getValue()) {
                for (Term n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(mind, n)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (Term t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                if (!list.contains(mind, t)) {
                    list.add(new Argument(t));
                }
            }
            res = list;
        } else if (b.getType() == DataType.SET) {
            ArgList list = new ArgList();
            for (Term t : (Collection<Term>) b.getValue()) {
                for (Term n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(mind, n)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (Term t : mind.getCalculator().getPredicates().expand(a, null, false)) {
                if (!list.contains(mind, t)) {
                    list.add(new Argument(t));
                }
            }
            res = list;
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

    protected Term _inc(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() + 1;
        } else if (a.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) a.getValue(), "1 day", 1);
        } else if (a.getType() == DataType.STRING && a.getValue().toString().length() == 1) {
            res = String.format("%c", a.getValue().toString().charAt(0) + 1);
        } else {
            res = a.getValue();
        }
        return mind.getTerms().add(res);
    }

    protected Term _dec(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() - 1;
        } else if (a.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) a.getValue(), "1 day", -1);
        } else if (a.getType() == DataType.STRING && a.getValue().toString().length() == 1) {
            res = String.format("%c", a.getValue().toString().charAt(0) - 1);
        } else {
            res = a.getValue();
        }
        return mind.getTerms().add(res);
    }

    protected Term _sub(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() - (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.PERIOD) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), -1);
        } else if (a.getType() == DataType.PERIOD && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), (String) a.getValue(), -1);
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.DATE) {
            res = Tools.dateDiff((Date) b.getValue(), (Date) a.getValue());
        } else if (a.getType() == DataType.SET) {
            ArgList list = new ArgList();
            for (Term t : (Collection<Term>) a.getValue()) {
                for (Term n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(mind, n)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (Term t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                list.remove(mind, t);
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

    private Term _mul(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() * (double) b.getValue();
        } else if (a.getType() == DataType.SET) {
            ArgList list = new ArgList();
            ArgList result = new ArgList();
            for (Term t : (Collection<Term>) a.getValue()) {
                for (Term n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(mind, n)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (Term t : mind.getCalculator().getPredicates().expand(b, null, false)) {
                if (list.contains(mind, t)) {
                    result.add(new Argument(t));
                }
            }
            res = result;
        } else if (b.getType() == DataType.SET) {
            ArgList list = new ArgList();
            ArgList result = new ArgList();
            for (Term t : (Collection<Term>) b.getValue()) {
                for (Term n : mind.getCalculator().getPredicates().expand(t, null, false)) {
                    if (!list.contains(mind, n)) {
                        list.add(new Argument(n));
                    }
                }
            }
            for (Term t : mind.getCalculator().getPredicates().expand(a, null, false)) {
                if (list.contains(mind, t)) {
                    result.add(new Argument(t));
                }
            }
            res = result;
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _div(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() / (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _rem(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC || b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() % (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _neg(Term a) throws Exception {
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

    private Term _bitnot(Term a) throws Exception {
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

    private Term _bitleft(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() << (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _bitright(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() >> (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _bitxor(Term a, Term b) throws Exception {
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

    private Term _bitand(Term a, Term b) throws Exception {
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

    private Term _bitor(Term a, Term b) throws Exception {
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

    private Term _bitandnot(Term a, Term b) throws Exception {
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

    private Term _log(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.log((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _exp(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.exp((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _log10(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.log10((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _exp10(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.pow(10.0, (double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _pi() throws Exception {
        Object res = Math.PI;
        return mind.getTerms().add(res);
    }

    private Term _sin(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _asin(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.asin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _cos(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.cos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _acos(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.acos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _tan(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.tan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _atan(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.atan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _abs(Term a) throws Exception {
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
    private Term _int(Term a, Term param) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = ((Double) a.getValue()).longValue();
        } else if (a.getType() == DataType.STRING) {
            if (((String) a.getValue()).contains(".")) {
                res = Double.valueOf((String) a.getValue()).longValue();
            } else if (param != null && param.getType() == DataType.NUMERIC) {
                res = Long.valueOf((String) a.getValue(), ((Double) param.getValue()).intValue());
            } else {
                res = Long.valueOf((String) a.getValue());
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
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _blob(Term a, Term param) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC || a.getType() == DataType.DATE) {
            byte[] buffer = new byte[Long.BYTES];
            long val = a.getType() == DataType.NUMERIC ? ((Double) a.getValue()).longValue() : ((Date) a.getValue()).getTime();
            for (int pos = 0; pos < Long.BYTES; ++pos) {
                if (param == null || ((Double) param.getValue()).intValue() == 0) {
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

    private Term _string(Term a, Term param) throws Exception {
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

    private Term _date(Term a, Term param) throws Exception {
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

    private Term _round(Term a, Term b) throws Exception {
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

    private Term _sqrt(Term a) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sqrt((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _pow(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _root(Term a, Term b) throws Exception {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), 1.0 / (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return mind.getTerms().add(res);
    }

    private Term _now() throws Exception {
        Object res = new Date(System.currentTimeMillis());
        return mind.getTerms().add(res);
    }

    private Term _md5(Term a) throws Exception {
        Term res = null;
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

    private Term _length(Term a) throws Exception {
        Object res = null;
        if (a.getType() == DataType.STRING) {
            res = a.getValue().toString().length();
        } else if (a.getType() == DataType.BLOB) {
            res = ((byte[]) a.getValue()).length;
        } else if (a.getType() == DataType.INTERVAL) {
            res = mind.getCalculator().getPredicates().expand(a, null, false).size();
        } else if (a.getType() == DataType.SET) {
            res = mind.getCalculator().getPredicates().expand(a, null, false).size();
        }
        return mind.getTerms().add(res);
    }

}
