package kanger.primitives;

import kanger.enums.ArgumentType;
import kanger.interfaces.Identifiable;
import kanger.units.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by murray on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument implements Externalizable {

    private ArgumentType type = ArgumentType.EMPTY;
    private long objectId = -1;

    public Argument() {
    }

    public Argument(Identifiable o) {
        type = detectType(o);
        objectId = type == ArgumentType.EMPTY ? -1 : o.getId();
    }

    public static ArgumentType detectType(Object o) {
        if (o instanceof Term) {
            return ArgumentType.TERM;
        } else if (o instanceof TVariable) {
            return ArgumentType.TVRIABLE;
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

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        type = ArgumentType.values()[dis.readInt()];
        objectId = dis.readLong();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeInt(type.ordinal());
        dos.writeLong(objectId);
    }


    public ArgumentType getType() {
        return type;
    }

    public long getValueId() {
        switch (type) {
            case TERM:
                return objectId;
            case TVRIABLE:
                return ((TVariable) o).getValue();
            case TVALUE:
                return ((TValue) o).getValue();
            case FVALUE:
                Term v = user.
                return ((FValue) o).getValue();
            case FUNCTION:
                return ((Function) o).getValue();
            default:
                return null;
        }
    }

    public boolean setValueId(long id) {
        switch (getType()) {
            case EMPTY:
                o = t;
                return true;
            case TERM:
                if(!((Term)o).isCVariable()) {
                    o = t;
                    return true;
                } else {
                    return false;
                }
            case TVRIABLE:
                ((TVariable) o).setValue(t);
                return true;
            case FUNCTION:
                ((Function) o).setValue(t);
                return true;
            default:
                return false;
        }
    }

    public TVariable getT() {
        return getType() == ArgumentType.TVRIABLE ? (TVariable) o : null;
    }

    public TValue getV() {
        return getType() == ArgumentType.TVALUE ? (TValue) o : null;
    }

    public Function getF() {
        return getType() == ArgumentType.FUNCTION ? (Function) o : null;
    }

    public FValue getR() {
        return getType() == ArgumentType.FVALUE ? (FValue) o : null;
    }

    public boolean isEmpty() {
        return getValue() == null;
    }

    public boolean isTSet() {
        return getType() == ArgumentType.TVRIABLE;
    }

    public boolean isVSet() {
        return getType() == ArgumentType.TVALUE;
    }

    public boolean isRSet() {
        return getType() == ArgumentType.FVALUE;
    }

    public boolean isFSet() {
        return getType() == ArgumentType.FUNCTION;
    }

    @Override
    public String toString() {
        Object val = getValue();
        if (val != null) {
            return val.toString();
        } else {
            return "null";
        }
    }


    public boolean isDefined() {
        Term t = getValue();
        return t != null && !t.isCVariable();
    }


    public boolean isCVar() {
        return !isEmpty() && getValue().isCVariable();
    }

}
