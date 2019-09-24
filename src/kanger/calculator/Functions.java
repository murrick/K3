package kanger.calculator;

import kanger.User;
import kanger.compiler.SysOp;
import kanger.enums.DataType;
import kanger.enums.LibMode;
import kanger.enums.Tools;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Reactor;
import kanger.primitives.ArgList;
import kanger.units.Function;
import kanger.units.Term;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 18.01.17.
 */
public class Functions {

    private User user = null;

    public Functions(User user) {
        this.user = user;
    }

    private final Map<String, SysOp> sysOps = new HashMap<String, SysOp>() {


        /// Арифметика
        {
            put("_inc(1)", new SysOp(LibMode.FUNCTION, "_inc", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && _inc(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _inc(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _dec(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _inc(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {

//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_dec(1)", new SysOp(LibMode.FUNCTION, "_dec", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && _dec(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _dec(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _inc(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _dec(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_bitnot(1)", new SysOp(LibMode.FUNCTION, "_bitnot", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && _bitnot(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _bitnot(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(0).isCVar()) {
                        if (!((Function) o).setParameter(0, _bitnot(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _bitnot(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_neg(1)", new SysOp(LibMode.FUNCTION, "_neg", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && _neg(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _neg(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(0).isCVar()) {
                        if (!((Function) o).setParameter(0, _neg(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _neg(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_val(1)", new SysOp(LibMode.FUNCTION, "_val", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(0).getValue().compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, arg.get(0).getValue())) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(0).isCVar()) {
                        if (!((Function) o).setParameter(0, arg.get(1).getValue())) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(0).getValue().compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("_add(2)", new SysOp(LibMode.FUNCTION, "_add", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _add(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _add(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _sub(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(1, _sub(arg.get(2).getValue(), arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _add(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_sub(2)", new SysOp(LibMode.FUNCTION, "_sub", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _sub(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _sub(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _add(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(1, _sub(arg.get(0).getValue(), arg.get(2).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _sub(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_mul(2)", new SysOp(LibMode.FUNCTION, "_mul", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _mul(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _mul(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && (double) arg.get(1).getValue().getValue() != 0) {
                        if (!((Function) o).setParameter(0, _div(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined() && (double) arg.get(0).getValue().getValue() != 0) {
                        if (!((Function) o).setParameter(1, _div(arg.get(2).getValue(), arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _mul(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_div(2)", new SysOp(LibMode.FUNCTION, "_div", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined() && (double) arg.get(1).getValue().getValue() != 0)
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _div(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _div(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _mul(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined() && (double) arg.get(2).getValue().getValue() != 0) {
                        if (!((Function) o).setParameter(1, _div(arg.get(0).getValue(), arg.get(2).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _div(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_rem(2)", new SysOp(LibMode.FUNCTION, "_rem", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined() && (double) arg.get(1).getValue().getValue() != 0)
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _rem(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _rem(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _rem(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_bitleft(2)", new SysOp(LibMode.FUNCTION, "_bitleft", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _bitleft(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _bitleft(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _bitright(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _bitleft(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_bitright(2)", new SysOp(LibMode.FUNCTION, "_bitright", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _bitright(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _bitright(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _bitleft(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _bitright(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_bitxor(2)", new SysOp(LibMode.FUNCTION, "_bitxor", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _bitxor(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _bitxor(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _bitxor(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _bitxor(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_bitand(2)", new SysOp(LibMode.FUNCTION, "_bitand", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _bitand(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _bitand(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _bitand(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("_bitor(2)", new SysOp(LibMode.FUNCTION, "_bitor", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _bitor(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _bitor(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _bitandnot(arg.get(2).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(1, _bitandnot(arg.get(2).getValue(), arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _bitor(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
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
            put("log(1)", new SysOp(LibMode.FUNCTION, "log", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _log(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _log(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _exp(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _log(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("exp(1)", new SysOp(LibMode.FUNCTION, "exp", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _exp(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _exp(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _log(arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _exp(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pi(0)", new SysOp(LibMode.FUNCTION, "pi", 0, new Reactor() {
                @Override
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    if (!((Function) o).setParameter(0, _pi())) {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sin(1)", new SysOp(LibMode.FUNCTION, "sin", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _sin(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _sin(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _asin(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _sin(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("asin(1)", new SysOp(LibMode.FUNCTION, "asin", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _asin(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _asin(arg.get(0).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _sin(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _asin(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("cos(1)", new SysOp(LibMode.FUNCTION, "cos", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _cos(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _cos(arg.get(0).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _acos(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _cos(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("acos(1)", new SysOp(LibMode.FUNCTION, "acos", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _acos(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _acos(arg.get(0).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _cos(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _acos(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("tan(1)", new SysOp(LibMode.FUNCTION, "tan", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _tan(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _tan(arg.get(0).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _atan(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _tan(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("atan(1)", new SysOp(LibMode.FUNCTION, "atan", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _atan(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _atan(arg.get(0).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _tan(arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _atan(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("int(1)", new SysOp(LibMode.FUNCTION, "int", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _int(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _int(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _int(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(2)", new SysOp(LibMode.FUNCTION, "round", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _round(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _round(arg.get(0).getValue(), arg.get(1).getValue()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _round(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("round(1)", new SysOp(LibMode.FUNCTION, "round", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _round(arg.get(0).getValue(), null).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _round(arg.get(0).getValue(), null))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _round(arg.get(0).getValue(), null).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("sqrt(1)", new SysOp(LibMode.FUNCTION, "sqrt", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && _sqrt(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) != 0)) {
                        if (!((Function) o).setParameter(1, _sqrt(arg.get(0).getValue()))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, _pow(arg.get(1).getValue(), user.getMind().getTerms().add(2)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && _sqrt(arg.get(0).getValue()).compareTo(arg.get(1).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("pow(2)", new SysOp(LibMode.FUNCTION, "pow", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _pow(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _pow(arg.get(0).getValue(), arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _root(arg.get(2).getValue(), user.getMind().getTerms().add(1)))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _pow(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
                        ret = 2;
                    } else {
//                        arg.createCVar(1).delValue(((Function) o).getOwner());
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("root(2)", new SysOp(LibMode.FUNCTION, "root", 2, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                            && _root(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) != 0)) {
                        if (!((Function) o).setParameter(2, _root(arg.get(0).getValue(), arg.get(1).getValue()))) {

                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(2).isDefined()) {
                        if (!((Function) o).setParameter(0, _pow(arg.get(2).getValue(), user.getMind().getTerms().add(1)))) {

                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && _root(arg.get(0).getValue(), arg.get(1).getValue()).compareTo(arg.get(2).getValue()) == 0) {
                        ret = 2;
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
            put("strlen(1)", new SysOp(LibMode.FUNCTION, "strlen", 1, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined()
                                && user.getMind().getTerms().add(arg.get(0).getValue().toString().length()).compareTo(arg.get(1).getValue()) != 0)) {

                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(arg.get(0).getValue().toString().length()))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && user.getMind().getTerms().add(arg.get(0).getValue().toString().length()).compareTo(arg.get(1).getValue()) == 0) {
                            ret = 2;
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
            put("mid(2)", new SysOp(LibMode.FUNCTION, "mid", 2, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                        Double pos = arg.get(1).isEmpty() ? null : (Double) arg.get(1).getValue().getValue();
                        String result = arg.get(2).isEmpty() ? null : (String) arg.get(2).getValue().getValue();

                        if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && !result.equals(src.substring(pos.intValue())))) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add(src.substring(pos.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined()) {
                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.indexOf(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && result.equals(src.substring(pos.intValue()))) {
                            ret = 2;
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
            put("mid(3)", new SysOp(LibMode.FUNCTION, "mid", 3, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                        Double pos = arg.get(1).isEmpty() ? null : (Double) arg.get(1).getValue().getValue();
                        Double len = arg.get(2).isEmpty() ? null : (Double) arg.get(2).getValue().getValue();
                        String result = arg.get(3).isEmpty() ? null : (String) arg.get(3).getValue().getValue();

                        if ((arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && !arg.get(3).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()
                                && !result.equals(src.substring(pos.intValue(), len.intValue())))) {
                            if (!((Function) o).setParameter(3, user.getMind().getTerms().add(src.substring(pos.intValue(), len.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()) {
                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.indexOf(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined() && arg.get(3).isDefined() && src.indexOf(result) != -1) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add(src.indexOf(result) + result.length()))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && !arg.get(2).isDefined() && arg.get(3).isDefined() && src.indexOf(result) != -1) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add(src.indexOf(result) + result.length()))
                                    || !((Function) o).setParameter(1, user.getMind().getTerms().add(src.indexOf(result)))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()
                                && result.equals(src.substring(pos.intValue(), len.intValue()))) {
                            ret = 2;
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
            put("left(2)", new SysOp(LibMode.FUNCTION, "left", 2, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                        Double pos = arg.get(1).isEmpty() ? null : (Double) arg.get(1).getValue().getValue();
                        String result = arg.get(2).isEmpty() ? null : (String) arg.get(2).getValue().getValue();

                        if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && !result.equals(src.substring(0, pos.intValue())))) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add(src.substring(0, pos.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined() && src.startsWith(result)) {
                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(result.length()))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && result.equals(src.substring(0, pos.intValue()))) {
                            ret = 2;
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
            put("right(2)", new SysOp(LibMode.FUNCTION, "right", 2, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                        Double pos = arg.get(1).isEmpty() ? null : (Double) arg.get(1).getValue().getValue();
                        String result = arg.get(2).isEmpty() ? null : (String) arg.get(2).getValue().getValue();

                        if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && !result.equals(src.substring(src.length() - pos.intValue())))) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add(src.substring(src.length() - pos.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined() && src.endsWith(result)) {
                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(result.length()))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && result.equals(src.substring(src.length() - pos.intValue()))) {
                            ret = 2;
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
            put("trim(1)", new SysOp(LibMode.FUNCTION, "trim", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                    String result = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();

                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && !result.equals(src.trim()))) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.trim()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result.equals(src.trim())) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("uc(1)", new SysOp(LibMode.FUNCTION, "uc", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                    String result = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();

                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && !result.equals(src.toUpperCase()))) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.toUpperCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result.equals(src.toUpperCase())) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("lc(1)", new SysOp(LibMode.FUNCTION, "lc", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                    String result = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();

                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && !result.equals(src.toLowerCase()))) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result.equals(src.toLowerCase())) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("at(2)", new SysOp(LibMode.FUNCTION, "at", 2, new Reactor() {
                public Object run(Object o) {
                    int ret = 1;
                    try {
                        ArgList arg = ((Function) o).getArguments();
                        String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                        String sample = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();
                        Double result = arg.get(2).isEmpty() ? null : (Double) arg.get(2).getValue().getValue();

                        if ((arg.get(0).isDefined() && arg.get(1).isDefined() && !arg.get(2).isDefined())
                                || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && result != src.indexOf(sample))) {
                            if (!((Function) o).setParameter(2, user.getMind().getTerms().add((src.indexOf(sample))))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && !arg.get(1).isDefined() && arg.get(2).isDefined()) {
                            if (!((Function) o).setParameter(1, user.getMind().getTerms().add(src.substring(result.intValue())))) {
                                ret = 0;
                            }
                        } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined()
                                && result == src.indexOf(sample)) {
                            ret = 2;
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
            put("replace(3)", new SysOp(LibMode.FUNCTION, "replace", 3, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                    String target = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();
                    String replacement = arg.get(2).isEmpty() ? null : (String) arg.get(2).getValue().getValue();
                    String result = arg.get(3).isEmpty() ? null : (String) arg.get(3).getValue().getValue();

                    if ((arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && !arg.get(3).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()
                            && !result.equals(src.replaceAll(target, replacement)))) {
                        if (!((Function) o).setParameter(3, user.getMind().getTerms().add(src.replaceAll(target, replacement)))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()) {
                        if (!((Function) o).setParameter(0, user.getMind().getTerms().add(result.replaceAll(replacement, target)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(2).isDefined() && arg.get(3).isDefined()
                            && result.equals(src.replaceAll(target, replacement))) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("chr(1)", new SysOp(LibMode.FUNCTION, "chr", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    Double src = arg.get(0).isEmpty() ? null : (Double) arg.get(0).getValue().getValue();
                    String result = arg.get(1).isEmpty() ? null : (String) arg.get(1).getValue().getValue();

                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && !result.equals(String.format("%c", src.intValue())))) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add(String.format("%c", src.intValue())))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, user.getMind().getTerms().add((int) result.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result.equals(String.format("%c", src.intValue()))) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        {
            put("asc(1)", new SysOp(LibMode.FUNCTION, "asc", 1, new Reactor() {
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();
                    String src = arg.get(0).isEmpty() ? null : (String) arg.get(0).getValue().getValue();
                    Double result = arg.get(1).isEmpty() ? null : (Double) arg.get(1).getValue().getValue();

                    if ((arg.get(0).isDefined() && !arg.get(1).isDefined())
                            || (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result != src.charAt(0))) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add((int) src.charAt(0)))) {
                            ret = 0;
                        }
                    } else if (!arg.get(0).isDefined() && arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(0, user.getMind().getTerms().add(String.format("%c", result.intValue())))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined()
                            && result == src.charAt(0)) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

        ////////// дата и время
        {
            put("now(0)", new SysOp(LibMode.FUNCTION, "now", 0, new Reactor() {
                @Override
                public Object run(Object o) throws RuntimeErrorException {
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
            put("type(1)", new SysOp(LibMode.FUNCTION, "type", 1, new Reactor() {
                @Override
                public Object run(Object o) throws RuntimeErrorException {
                    int ret = 1;
                    ArgList arg = ((Function) o).getArguments();

                    if (arg.get(0).isDefined() && !arg.get(1).isDefined()) {
                        if (!((Function) o).setParameter(1, user.getMind().getTerms().add(arg.get(0).getValue().getType().name().toLowerCase()))) {
                            ret = 0;
                        }
                    } else if (arg.get(0).isDefined() && arg.get(1).isDefined() && arg.get(0).getValue().getType().name().toLowerCase().equals(arg.get(1).getValue().toString().toLowerCase())) {
                        ret = 2;
                    } else {
                        ret = 0;
                    }
                    return ret;
                }
            }));
        }

    };

    public Map<String, SysOp> getSysOps() {
        return sysOps;
    }

    protected Term _add(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() + (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.INTERVAL) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), 1);
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), (String) a.getValue(), 1);
        } else {
            res = a.getValue().toString() + b.getValue().toString();
        }
        return user.getMind().getTerms().add(res);
    }

    protected Term _inc(Term a) throws RuntimeErrorException {
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
        return user.getMind().getTerms().add(res);
    }

    protected Term _dec(Term a) throws RuntimeErrorException {
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
        return user.getMind().getTerms().add(res);
    }

    protected Term _sub(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() - (double) b.getValue();
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.INTERVAL) {
            res = Tools.dateAdd((Date) a.getValue(), (String) b.getValue(), -1);
        } else if (a.getType() == DataType.INTERVAL && b.getType() == DataType.DATE) {
            res = Tools.dateAdd((Date) b.getValue(), (String) a.getValue(), -1);
        } else if (a.getType() == DataType.DATE && b.getType() == DataType.DATE) {
            res = Tools.dateDiff((Date) b.getValue(), (Date) a.getValue());
        } else {
            res = a.getValue().toString().replace(b.getValue().toString(), "");
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _mul(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() * (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _div(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() / (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _rem(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC || b.getType() == DataType.NUMERIC) {
            res = (double) a.getValue() % (double) b.getValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _neg(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = -(double) a.getValue();
        } else if (a.getType() == DataType.INTERVAL && a.getValue() instanceof Collection && ((Collection) a.getValue()).size() == 2) {
            List<Term> list = new ArrayList<>();
            list.add(_neg((Term) ((Collection) a.getValue()).toArray()[0]));
            list.add((Term) ((Collection) a.getValue()).toArray()[0]);
            res = list;
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitnot(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitleft(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() << (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitright(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() >> (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitxor(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() ^ (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitand(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() & (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitor(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() | (long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _bitandnot(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Long.valueOf((long) a.getValue() & ~(long) b.getValue()).doubleValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _log(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.log((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _exp(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.exp((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _pi() throws RuntimeErrorException {
        Object res = Math.PI;
        return user.getMind().getTerms().add(res);
    }

    private Term _sin(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _asin(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.asin((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _cos(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.cos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _acos(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.acos((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _tan(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.tan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _atan(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.atan((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _abs(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.abs((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _int(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = (double) (long) (double) a.getValue();
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _round(Term a, Term b) throws RuntimeErrorException {
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
        return user.getMind().getTerms().add(res);
    }

    private Term _sqrt(Term a) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC) {
            res = Math.sqrt((double) a.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _pow(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _root(Term a, Term b) throws RuntimeErrorException {
        Object res;
        if (a.getType() == DataType.NUMERIC && b.getType() == DataType.NUMERIC) {
            res = Math.pow((double) a.getValue(), 1.0 / (double) b.getValue());
        } else {
            res = (double) 0;
        }
        return user.getMind().getTerms().add(res);
    }

    private Term _now() throws RuntimeErrorException {
        Object res = new Date(System.currentTimeMillis());
        return user.getMind().getTerms().add(res);
    }

}
