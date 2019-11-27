package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.*;

import java.io.IOException;

/**
 * Created by Dmitry G. Qusnetsov on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument {

    private static final long serialVersionUID = -7113328096110690461L; //196402070011L;

    private IUnit o = null;

    private transient long id = -1;
    private transient ArgumentType type = ArgumentType.EMPTY;
//    private transient IUser user = null;

    public Argument() {
    }

    public Argument(IUnit d) {
        o = d;
        if (o != null) {
            id = o.getId();
            type = getObjectType();
//            user = d.getUser();
        }
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putInt(type.ordinal());
        return packet.createMarked();
    }

    public Argument apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        type = ArgumentType.values()[packet.getInt()];
        return this;
    }

    private void load(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        switch (type) {
            case CVARIABLE:
            case TERM:
                o = mind.getTerms().load(id);
                break;
            case TVARIABLE:
                o = mind.getTVars().load(id);
                break;
            case TVALUE:
                o = mind.getTValues().load(id);
                break;
            case FUNCTION:
                o = mind.getFunctions().load(id);
                break;
            case FVALUE:
                o = mind.getFValues().load(id);
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

    public Term getValue(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        switch (type) {
            case CVARIABLE:
            case TERM:
                return (Term) getO(mind);
            case TVARIABLE:
                return ((TVariable) getO(mind)).getValue();
            case TVALUE:
                return ((TValue) getO(mind)).getValue();
            case FVALUE:
                return ((FValue) getO(mind)).getValue();
            case FUNCTION:
                return ((Function) getO(mind)).getValue();
            default:
                return null;
        }
    }

    public TValue addValue(Mind mind, Term t) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
//        Mind mind = t.getUser().getMind();
        switch (type) {
            case TVARIABLE:
                TVariable tv = (TVariable) getO(mind);
                TValue s = mind.getTValues().find(tv, t);
                if (s == null) {
                    s = mind.getTValues().add(tv, t);
                } else {
                    s = null;
                }
                return s;
            default:
                return null;
        }
    }

    public boolean setValue(Mind mind, Term t) throws Exception {
//        Mind mind = t.getUser().getMind();
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
                TVariable tv = (TVariable) getO(mind);
                tv.setValue(t);
//                TValue s = mind.getTValues().find(tv, t);
//                if (s == null) {
//                    s = mind.getTValues().add(tv, t);
//                }
//                if (tv.getCurrent() == null) {
//                    tv.setCurrent(s);
//                }
                return true;
            case FUNCTION:
                Function f = (Function) getO(mind);
                if (f.isCalculable()) {
                    f.setResult(t);
                    mind.getCalculator().calculate(f, mind.isLogging());
                }
                return true;
            default:
                return false;
        }
    }

    public IUnit getO(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (o == null && id != -1 && type != ArgumentType.EMPTY) {
            load(mind);
        }
        return o;
    }

    public void setO(IUnit o) {
        this.o = o;
        type = getObjectType();
        if (o != null) {
            id = o.getId();
        } else {
            id = -1;
        }
    }

    public TVariable getT(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return type == ArgumentType.TVARIABLE ? (TVariable) getO(mind) : null;
    }

    public TValue getV(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return type == ArgumentType.TVALUE ? (TValue) getO(mind) : null;
    }

    public Function getF(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return type == ArgumentType.FUNCTION ? (Function) getO(mind) : null;
    }

    public FValue getR(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        return type == ArgumentType.FVALUE ? (FValue) getO(mind) : null;
    }

    public void clear() {
        o = null;
        type = ArgumentType.EMPTY;
        id = -1;
    }

    public boolean isEmpty(Mind mind) {
        try {
            return getValue(mind) == null;
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

    public String asString(Mind mind) {
        try {
            Object val = getValue(mind);
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


    public boolean isDefined(Mind mind) throws Exception {
        Term t = getValue(mind);
        return t != null && type != ArgumentType.CVARIABLE;
    }


    public boolean isCVar() {
        return type == ArgumentType.CVARIABLE; //!isEmpty() && getValue().isCVariable();
    }

    //    public IUser getUser() {
//        return user;
//    }
//
//    public void setUser(IUser user) {
//        this.user = user;
//    }
//
    public long getId() {
        return id;
    }

    public ArgumentType getType() {
        return type;
    }

    public UnitType getUnitType() {
        return UnitType.ARGUMENT;
    }

}
