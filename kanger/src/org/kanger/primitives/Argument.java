package org.kanger.primitives;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.units.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument implements Externalizable {

    private static final long serialVersionUID = -7113328096110690461L; //196402070011L;

    private IUnit o = null;

    private transient long id = -1;
    private transient ArgumentType type = ArgumentType.EMPTY;
    private transient IUser user = null;

    public Argument() {
    }

    public Argument(IUnit d) {
        o = d;
        if (o != null) {
            id = o.getId();
            type = getObjectType();
            user = d.getUser();
        }
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        type = ArgumentType.values()[dis.readInt()];
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeInt(type.ordinal());
    }

    private void load(IUser user) throws IOException, ClassNotFoundException {
        switch (type) {
            case CVARIABLE:
            case TERM:
                o = user.getMind().getTerms().load(id);
                break;
            case TVARIABLE:
                o = user.getMind().getTVars().load(id);
                break;
            case TVALUE:
                o = user.getMind().getTValues().load(id);
                break;
            case FUNCTION:
                o = user.getMind().getFunctions().load(id);
                break;
            case FVALUE:
                o = user.getMind().getFValues().load(id);
                break;
            default:
                o = null;
        }
    }


    private ArgumentType getObjectType() {
        if (o instanceof Term) {
            if (((Term) o).isCVariable()) {
                return ArgumentType.CVARIABLE;
            } else {
                return ArgumentType.TERM;
            }
        } else if (o instanceof TVariable) {
            return ArgumentType.TVARIABLE;
        } else if (o instanceof TValue) {
            return ArgumentType.TVALUE;
        } else if (o instanceof FValue) {
            return ArgumentType.FVALUE;
        } else if (o instanceof Function) {
            return ArgumentType.FUNCTION;
        } else {
            return ArgumentType.EMPTY;
        }
    }

    public Term getValue() throws IOException, ClassNotFoundException {
        switch (type) {
            case CVARIABLE:
            case TERM:
                return (Term) getO();
            case TVARIABLE:
                return ((TVariable) getO()).getValue();
            case TVALUE:
                return ((TValue) getO()).getValue();
            case FVALUE:
                return ((FValue) getO()).getValue();
            case FUNCTION:
                return ((Function) getO()).getValue();
            default:
                return null;
        }
    }

    public TValue addValue(Term t) throws IOException, ClassNotFoundException {
        switch (type) {
            case TVARIABLE:
                TVariable tv = (TVariable) getO();
                user = tv.getUser();
                TValue s = user.getMind().getTValues().find(tv, t);
                if (s == null) {
                    s = user.getMind().getTValues().add(tv, t);
                } else {
                    s = null;
                }
                return s;
            default:
                return null;
        }
    }

    public boolean setValue(Term t) throws Exception {
        switch (type) {
            case EMPTY:
                o = t;
                id = o.getId();
                type = t.isCVariable() ? ArgumentType.CVARIABLE : ArgumentType.TERM;
                return true;
            case CVARIABLE:
                return false;
            case TERM:
                o = t;
                id = o.getId();
                return true;
            case TVARIABLE:
                TVariable tv = (TVariable) getO();
                user = tv.getUser();
                tv.setValue(t);
//                TValue s = user.getMind().getTValues().find(tv, t);
//                if (s == null) {
//                    s = user.getMind().getTValues().add(tv, t);
//                }
//                if (tv.getCurrent() == null) {
//                    tv.setCurrent(s);
//                }
                return true;
            case FUNCTION:
                Function f = (Function) getO();
                user = f.getUser();
                if (f.isCalculable()) {
                    f.setResult(t);
                    user.getMind().getCalculator().calculate(f, user.getMind().isLogging());
                }
                return true;
            default:
                return false;
        }
    }

    private IUnit getO() throws IOException, ClassNotFoundException {
        if (o == null && id != -1 && type != ArgumentType.EMPTY) {
            load(user);
        }
        return o;
    }

    public TVariable getT() throws IOException, ClassNotFoundException {
        return type == ArgumentType.TVARIABLE ? (TVariable) getO() : null;
    }

    public TValue getV() throws IOException, ClassNotFoundException {
        return type == ArgumentType.TVALUE ? (TValue) getO() : null;
    }

    public Function getF() throws IOException, ClassNotFoundException {
        return type == ArgumentType.FUNCTION ? (Function) getO() : null;
    }

    public FValue getR() throws IOException, ClassNotFoundException {
        return type == ArgumentType.FVALUE ? (FValue) getO() : null;
    }

    public void clear() {
        o = null;
        type = ArgumentType.EMPTY;
        id = -1;
    }

    public boolean isEmpty() {
        try {
            return getValue() == null;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
    }

    public boolean isTSet() {
        return type == ArgumentType.TVARIABLE;
    }

    public boolean isVSet() {
        return type == ArgumentType.TVALUE;
    }

    public boolean isRSet() {
        return type == ArgumentType.FVALUE;
    }

    public boolean isFSet() {
        return type == ArgumentType.FUNCTION;
    }

    @Override
    public String toString() {
        try {
            Object val = getValue();
            if (val != null) {
                return val.toString();
            } else {
                return "null";
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }


    public boolean isDefined() throws Exception {
        Term t = getValue();
        return t != null && type != ArgumentType.CVARIABLE;
    }


    public boolean isCVar() {
        return type == ArgumentType.CVARIABLE; //!isEmpty() && getValue().isCVariable();
    }

    public IUser getUser() {
        return user;
    }

    public void setUser(IUser user) {
        this.user = user;
    }

    public long getId() {
        return id;
    }

    public ArgumentType getType() {
        return type;
    }

}
