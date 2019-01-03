package kanger.units;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;

import java.io.*;

public class FValue implements Externalizable, Identifiable {
    private long id = -1;
    private Function function = null;
    private Term value = null;
    private ArgList condition = new ArgList();

    private FValue next = null;
    private User user = null;

    public FValue(User user) {
        this.user = user;
    }

    public FValue(Function f, User user) {
        function = f;
        value = f.getArguments().get(f.getRange()).getValue();
        for (Argument a : f.getArguments()) {
            if (a.isTSet()) {
                condition.add(new Argument(a.getT().getCurrent()));
            } else if (a.isFSet()) {
                condition.add(new Argument(a.getF().getCurrent()));
            } else {
                condition.add(new Argument(a.getValue()));
            }
        }
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        function = (Function) dis.readObject();
        value = (Term) dis.readObject();
        condition = (ArgList) dis.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeObject(function);
        dos.writeObject(value);
        dos.writeObject(condition);
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }

    public Term setValue(Term value) {
        this.value = value;
        return value;
    }


    public Term getValue() {
        return value;
    }

//    @Override
//    public Term getDirtyValue() {
//        return getValue();
//    }

//    public TValue getValue(TVariable t) {
//        if (condition.containsKey(t.getId())) {
//            return mind.getTValues().get(condition.get(t.getId()));
//        } else {
//            return null;
//        }
//    }

//    public void setCondition(Map<Long, Long> condition) {
//        this.condition = condition;
//    }
//
//    public Map<Long, Long> getCondition() {
//        return condition;
//    }

    public void setFunction(Function function) {
        this.function = function;
    }

    public Function getFunc() {
        return function;
    }

    public void setNext(FValue next) {
        this.next = next;
    }

    public FValue getNext() {
        return next;
    }


//    public boolean isActual(Function f) {
//        for (int i = 0; i < function.getRange(); ++i) {
//            if (function.getArguments().get(i).getDirtyValue() == null
//                    || condition.get(i).getValue() == null
//                    || function.getArguments().get(i).getDirtyValue().getId() != condition.get(i).getValue().getId()) {
//                return false;
//            }
//        }
////        for (Map.Entry<Long, Long> e : condition.entrySet()) {
////            TVariable tv = mind.getTVars().get(e.getKey());
////            if (tv == null || tv.isEmpty() || tv.getCurrent().getId() != e.getValue()) {
////                return false;
////            }
////        }
//        return true;
//    }

    public Argument getCondition(int index) {
        return condition.get(index);
    }

    public ArgList getCondition() {
        return condition;
    }

//    public boolean isClosed() {
//        for (long id : condition.values()) {
//            if (mind.getTValues().get(id) == null || !mind.getTValues().get(id).isClosed()) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    public boolean isBlocked() {
//        for (long id : condition.values()) {
//            if (mind.getTValues().get(id) != null && mind.getTValues().get(id).isBlocked()) {
//                return true;
//            }
//        }
//        return false;
//    }

    private String formatParam(Argument t) {
        Operation op = Parser.getOp(function.getName().toString(), function.getRange());
        boolean isOp = op != null && op.getRange() == function.getRange();
        String s = "";
        if (t.isFSet()) {
            s += (isOp ? "(" : "") + t.getF().toString() + (isOp ? ")" : "");
        } else if (t.isRSet()) {
            s += (isOp ? "(" : "") + t.getR().toString() + (isOp ? ")" : "");
        } else if (t.isTSet()) {
            s += t.getT().toString();
        } else if (t.isVSet()) {
            s += t.getV().toString();
        } else if (!t.isEmpty()) {
            s += t.getValue().toString();
        } else {
            s += "_";
        }
        return s;
    }
   
    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(function.getId());
        buffer.append(value.getId());
        buffer.append(condition.hashCode());
        return buffer.toString().hashCode();
    }
    
    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }
        
    @Override
    public String toString() {
        if (!function.isCalculable() && getValue() != null) {
            return getValue().toString();
        } else {
            Operation op = Parser.getOp(function.getName().toString(), function.getRange());
            String s = "";
            if (op == null || op.getRange() != function.getRange()) {
                s = String.format("%s(", function.getName().toString());
                for (int i = 0; i < function.getRange(); ++i) {
                    s += formatParam(condition.get(i));
                    if (i + 1 < function.getRange()) {
                        s += (char) Enums.COMMA;
                    }
                }
                s += ")";
            } else if (op.getRange() == 1) {
                if (op.isPost()) {
                    s = formatParam(condition.get(0)) + op.getName();
                } else {
                    s = op.getName() + formatParam(condition.get(0));
                }
            } else {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(condition.get(i));
                    if (i + 1 < op.getRange()) {
                        s += " " + op.getName() + " ";
                    }
                }
            }

            String res = "";
            if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
//                if (getResult() != null) {
                if (getValue() != null) {
                    res = " {= " + getValue() + "}";
                } else if (condition.size() > function.getRange() && !condition.get(function.getRange()).isEmpty()) {
                    res = " [= " + condition.get(function.getRange()).getValue() + "]";
                }
            }
            //Argument r = range < arguments.size() ? arguments.createCVar(range) : null;
            return s + res;
        }
    }
}
