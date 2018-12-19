package kanger.primitives;

import kanger.User;
import kanger.enums.ArgumentType;
import kanger.interfaces.IEntry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument  {

    private Object o = null;

    public Argument() {
    }

    public Argument(Object d) {
        o = d;
    }

    public Argument(DataInputStream dis, User user) throws IOException {
        ArgumentType type = ArgumentType.values()[dis.readInt()];
        switch (type) {
            case TERM:
                o = user.getMind().getTermsLink().get(dis.readLong());
                break;
            case TVRIABLE:
                o = user.getMind().getTVariablesLink().get(dis.readLong());
                break;
            case TVALUE:
                o = user.getMind().getTValuesLink().get(dis.readLong());
                break;
            case FVALUE:
                o = user.getMind().getFValuesLink().get(dis.readLong());
                break;
            case FUNCTION:
                o = user.getMind().getFunctionsLink().get(dis.readLong());
                break;
        }
    }

    public ArgumentType getType() {
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

    public Term getValue() {
        switch (getType()) {
            case TERM:
                return (Term) o;
            case TVRIABLE:
                return ((TVariable) o).getValue();
            case TVALUE:
                return ((TValue) o).getValue();
            case FVALUE:
                return ((FValue) o).getValue();
            case FUNCTION:
                return ((Function) o).getValue();
            default:
                return null;
        }
    }

    public boolean setValue(Term t) {
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

    public void writeCompiledData(DataOutputStream dos, User user) throws IOException {
        ArgumentType type = getType();
        dos.writeInt(type.ordinal());
        switch (type) {
            case FUNCTION:
                dos.writeLong((Long) user.getMind().getFunctionsLink().get(getF()));
                break;
            case TVRIABLE:
                dos.writeLong((Long) user.getMind().getTVariablesLink().get(getT()));
                break;
            case TERM:
                dos.writeLong((Long) user.getMind().getTermsLink().get(getValue()));
                break;
            case FVALUE:
                dos.writeLong((Long) user.getMind().getFValuesLink().get(getR()));
                break;
            case TVALUE:
                dos.writeLong((Long) user.getMind().getTValuesLink().get(getV()));
                break;
        }
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
