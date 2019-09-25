package kanger.primitives;

import kanger.User;
import kanger.enums.ArgumentType;
import kanger.interfaces.Identifiable;
import kanger.units.*;

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

    private Identifiable o = null;

    private transient long id = -1;
    private transient ArgumentType type = ArgumentType.EMPTY;

    public Argument() {
    }

    public Argument(Identifiable d) {
        o = d;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException {
        id = dis.readLong();
        type = ArgumentType.values()[dis.readInt()];
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(o.getId());
        dos.writeInt(getType().ordinal());
    }

    public void linkExternal(User user) throws Exception {
        if (o == null && type != ArgumentType.EMPTY) {
            switch (type) {
                case TERM:
                    o = user.getMind().getTerms().get(id);
                    o.linkExternal(user);
                    break;
                case TVRIABLE:
                    o = user.getMind().getTVars().get(id);
                    o.linkExternal(user);
                    break;
                case TVALUE:
                    o = user.getMind().getTValues().get(id);
                    o.linkExternal(user);
                    break;
                case FUNCTION:
                    o = user.getMind().getFunctions().get(id);
                    o.linkExternal(user);
                    break;
                case FVALUE:
                    o = user.getMind().getFValues().get(id);
                    o.linkExternal(user);
                    break;
                default:
                    o = null;
            }
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

    public Term getValue() throws Exception {
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

    public boolean setValue(Term t) throws Exception {
        switch (getType()) {
            case EMPTY:
                o = t;
                return true;
            case TERM:
                if (!((Term) o).isCVariable()) {
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
        try {
            return getValue() == null;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
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
        return t != null && !t.isCVariable();
    }


    public boolean isCVar() throws Exception {
        return !isEmpty() && getValue().isCVariable();
    }

}
