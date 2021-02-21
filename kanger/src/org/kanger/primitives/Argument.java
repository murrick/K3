package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.*;

import java.util.ArrayList;
import java.util.List;

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

    private transient int varOrder = -1;

//    private transient IUser user = null;

    public Argument() {
    }

    public Argument(ITerm d) {
        this((IUnit) d);
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
                .putInt(type.ordinal())
                .putInt(varOrder);
        return packet.createMarked();
    }

    public Argument apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        type = ArgumentType.values()[packet.getInt()];
        varOrder = packet.getInt();
        return this;
    }

    private void load(Mind mind) throws Exception {
        switch (type) {
            case TERM:
                o = mind.getTerms().get(id);
                break;
            case TVARIABLE:
                o = mind.getTVars().get(id);
                break;
            case TVALUE:
                o = mind.getTValues().get(id);
                break;
            case FUNCTION:
                o = mind.getFunctions().get(id);
                break;
            case FVALUE:
                o = mind.getFValues().get(id);
                break;
            default:
                o = null;
        }
    }


    private ArgumentType getObjectType() {
        if (o instanceof Term) {
            return ArgumentType.TERM;
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

    public ITerm getValue(IMind mind) throws Exception {
        switch (type) {
            case TERM:
                return (Term) getO((Mind) mind);
            case TVARIABLE:
                return ((TVariable) getO((Mind) mind)).getValue();
            case TVALUE:
                return ((TValue) getO((Mind) mind)).getValue();
            case FVALUE:
                return ((FValue) getO((Mind) mind)).getValue();
            case FUNCTION:
                return ((Function) getO((Mind) mind)).getValue();
            default:
                return null;
        }
    }

    public TValue addValue(Mind mind, ITerm t) throws Exception {
//        Mind mind = t.getUser().getMind();
        switch (type) {
            case TVARIABLE:
                TVariable tv = (TVariable) getO(mind);
                TValue s = mind.getTValues().find(tv, t);
                if (s == null) {
                    s = mind.getTValues().add(tv, t);

                    List<TValue> list = new ArrayList<>();
                    list.add(s);
                    mind.addTSolve(list);

                } else {
                    s = null;
                }
                return s;
            default:
                return null;
        }
    }

    public boolean setValue(Mind mind, ITerm t) throws Exception {
//        Mind mind = t.getUser().getMind();
        switch (type) {
            case EMPTY:
                o = (IUnit) t;
                id = o.getId();
                type = ArgumentType.TERM;
                return true;
            case TERM:
                o = (IUnit) t;
                id = o.getId();
                return true;
            case TVARIABLE:
                TVariable tv = (TVariable) getO((Mind) mind);
                TValue s = tv.setValue(t);
//                mind.addTSolve(s);


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

    public IUnit getO(IMind mind) throws Exception {
        if (o == null && id != -1 && type != ArgumentType.EMPTY) {
            load((Mind) mind);
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

    public TVariable getT(IMind mind) throws Exception {
        return type == ArgumentType.TVARIABLE ? (TVariable) getO(mind) : null;
    }

    public TValue getV(IMind mind) throws Exception {
        return type == ArgumentType.TVALUE ? (TValue) getO(mind) : null;
    }

    public Function getF(IMind mind) throws Exception {
        return type == ArgumentType.FUNCTION ? (Function) getO(mind) : null;
    }

    public FValue getR(IMind mind) throws Exception {
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
        Term t = (Term) getValue(mind);
        return t != null && !t.isCVariable(); //isCVar(mind); //type != ArgumentType.CVARIABLE;
    }


//    public boolean isCVar(Mind mind) throws Exception {
//        return type == ArgumentType.CVARIABLE; //
//        return !isEmpty(mind) && getValue(mind).isCVariable();
//    }

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

    public int getVarOrder() {
        return varOrder;
    }

    public void setVarOrder(int varOrder) {
        this.varOrder = varOrder;
    }
}
