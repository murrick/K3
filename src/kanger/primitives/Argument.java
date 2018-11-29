package kanger.primitives;

import kanger.User;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument {

    private Object o = null;

    public Argument() {
    }

    public Argument(Object d) {
        o = d;
    }

    public Argument(DataInputStream dis, User user) throws IOException {
        int flags = dis.readInt();
        if (flags == 1) {
            long id = dis.readLong();
            o = user.getMind().getTerms().get(id);
        } else if (flags == 2) {
            long id = dis.readLong();
            o = user.getMind().getTVars().get(id);
        } else if (flags == 3) {
            o = new Function(dis, user);
        }
    }

    public Term getDirtyValue() {
        if (o instanceof Term) {
            return (Term) o;
        } else if (o instanceof TVariable) {
            return ((TVariable) o).getValue();
        } else if (o instanceof TValue) {
            return ((TValue) o).getValue();
        } else if (o instanceof Function) {
            if (isEmpty() && getF().getArguments().size() > getF().getRange()) {
                return getF().getArguments().get(getF().getRange()).getDirtyValue();
            } else {
                return ((Function) o).getValue();
            }
        } else {
            return null;
        }
    }

    public Term getValue() {
        if (o instanceof Term) {
            return (Term) o;
        } else if (o instanceof TVariable) {
            return ((TVariable) o).getValue();
        } else if (o instanceof TValue) {
            return ((TValue) o).getValue();
        } else if (o instanceof Function) {
            return ((Function) o).getValue();
        } else {
            return null;
        }
    }

//    public boolean setValue(Function f, Term t) {
//        return setValue(f.getOwner(), t);
//    }
//

    //TODO: Тут разобраться. Для IValue
    public boolean setValue(Term t) {
        if (o == null) {
            o = t;
        } else if (o instanceof TVariable) {
            ((TVariable) o).setValue(t).setClosed();
        } else if (o instanceof Function) {
            ((Function) o).setValue(t);
        }
        return true;
    }

    public void delValue() {
        if (o == null || o instanceof Term || o instanceof TValue) {
            o = null;
        } else if (o instanceof TVariable) {
            ((TVariable) o).clear();
        } else if (o instanceof Function) {
            ((Function) o).clear();
        }
    }

    public TVariable getT() {
        return isTSet() ? (TVariable) o : null;
    }

    public TValue getV() {
        return isVSet() ? (TValue) o : null;
    }

    public Function getF() {
        return isFSet() ? (Function) o : null;
    }


    public boolean isEmpty() {
        return getValue() == null;
    }

    public boolean isTSet() {
        return o instanceof TVariable;
    }

    public boolean isVSet() {
        return o instanceof TValue;
    }

    public boolean isFSet() {
        return o instanceof Function;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        if (o instanceof Term) {
            dos.writeInt(1);
            dos.writeLong(((Term) o).getId());
        } else if (o instanceof TVariable) {
            dos.writeInt(2);
            dos.writeLong(((TVariable) o).getId());
        } else if (o instanceof Function) {
            dos.writeInt(3);
            ((Function) o).writeCompiledData(dos);
        }
    }


//    @Override
//    public boolean equals(Object x) {
//        if (x == null || !(x instanceof Argument)) {
//            return false;
//        } else {
//            Argument a = (Argument) x;
//            if ((o instanceof Term)
//                    && ((o == null && a.o == null)
//                    || (o != null && a.o != null && ((!((Term) o).isCVariable() && a.o.equals(o))
//                    || (((Term) o).isCVariable() && ((Term) a.o).isCVariable()))))) {
//                return true;
//
//            } else if ((o instanceof Function)
//                    && ((o == null && a.o == null) || (o != null && a.o != null && ((Function) a.o).equals(o)))) {
//                return true;
//
//            } else if (o instanceof TVariable
//                    && ((o == null && a.o == null) || (o != null && a.o != null))) {
//                return true;
//
//
//            }
//            return false;
//        }
//    }

//    public boolean isDestFor(Domain d) {
////        return isTVariable() && getTVariable().isDestFor(d);
//        if (isTVariable())
//            return getTVariable().isDestFor(d);
//        else if (isFunction()) {
//            for (TVariable t : getFunction().getTVariables()) {
//                if (t.isDestFor(d)) {
//                    return true;
//                }
//            }
//            return false;
//        } else {
//            return false;
//        }
//    }

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

    public boolean isCalculated() {
        if (isTSet()) {
            return !getT().isEmpty(); //.isComplete();
        } else if (isFSet()) {
//            Term t = getFunction().getResult();
//			if(getFunction().isCalculable()) {
            return getF().isCalculated();
//			} else {
//            return t != null && !"$$".equals(t.toString());
//			}
        } else {
            return !isEmpty() /*&& !getValue().isCVariable()*/;
        }
    }

    public boolean isCVar() {
        return !isEmpty() && getValue().isCVariable();
    }

}
