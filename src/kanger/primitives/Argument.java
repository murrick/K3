package kanger.primitives;

import kanger.Mind;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 26.05.15.
 * <p>
 * Решение для предиката
 */
public class Argument {

//    Term c = null;                                // Может быть ЛИБО т-переменная, либо
//    TVariable t = null;                                 // с-переменная
//    Function f = null;                                  // либо функция

    private Object o = null;

    public Argument() {
    }

    public Argument(Object d) {
        o = d;
    }

    public Argument(DataInputStream dis, Mind mind) throws IOException {
        int flags = dis.readInt();
        if (flags == 1) {
            long id = dis.readLong();
            o = mind.getTerms().get(id);
        } else if (flags == 2) {
            long id = dis.readLong();
            o = mind.getTVars().get(id);
        } else if (flags == 3) {
            o = new Function(dis, mind);
        }
    }

    public Term getDirtyValue() {
        if (o instanceof Term) {
            return (Term) o;
        } else if (o instanceof TVariable) {
            return ((TVariable) o).getValue();
        } else if (o instanceof Function) {
            if (isEmpty() && getF().getArguments().size() > getF().getRange()) {
                return getF().getArguments().get(getF().getRange()).getDirtyValue();
            } else {
                return ((Function) o).getResult();
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
        } else if (o instanceof Function) {
            return ((Function) o).getResult();
        } else {
            return null;
        }
    }

//    public boolean setValue(Function f, Term t) {
//        return setValue(f.getOwner(), t);
//    }
//

    public boolean setValue(Term t) {
        boolean result = true;
        if (o == null || o instanceof Term) {
            o = t;
        } else if (o instanceof TVariable) {
            TValue s = ((TVariable) o).setValue(t);
        } else if (o instanceof Function) {
            ((Function) o).setResult(t);
            result = true;
        }
        return result;
    }

    public void delValue() {
        if (o == null || o instanceof Term) {
            o = null;
        } else if (o instanceof TVariable) {
            ((TVariable) o).delValue();
        } else if (o instanceof Function) {
            ((Function) o).clearResult();
        }
    }

    public TVariable getT() {
        return isTSet() ? (TVariable) o : null;
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
//                    || (o != null && a.o != null && ((!((Term) o).isCVar() && a.o.equals(o))
//                    || (((Term) o).isCVar() && ((Term) a.o).isCVar()))))) {
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
////        return isTSet() && getT().isDestFor(d);
//        if (isTSet())
//            return getT().isDestFor(d);
//        else if (isFSet()) {
//            for (TVariable t : getF().getTVariables()) {
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
        if (isTSet()) {
            return !getT().isEmpty(); //.isComplete();
        } else if (isFSet()) {
            Term t = getF().getResult();
//			if(getF().isCalculable()) {
//				return getF().isCalculated();
//			} else {
            return t != null && !"$$".equals(t.toString());
//			}
        } else {
            return !isEmpty() && !getValue().isCVar();
        }
    }

    public boolean isCalculated() {
        if (isTSet()) {
            return !getT().isEmpty(); //.isComplete();
        } else if (isFSet()) {
//            Term t = getF().getResult();
//			if(getF().isCalculable()) {
            return getF().isCalculated();
//			} else {
//            return t != null && !"$$".equals(t.toString());
//			}
        } else {
            return !isEmpty() /*&& !getValue().isCVar()*/;
        }
    }

    public boolean isCVar() {
        return !isEmpty() && getValue().isCVar();
    }

}
