package kanger.primitives;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.interfaces.IValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FValue implements IValue {
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

    public FValue readCompiledData(DataInputStream dis) throws IOException {
        this.user = user;
        id = dis.readLong();
        function = user.getMind().getFunctions().get(dis.readLong());
        long valueId = dis.readLong();
        if(valueId != -1) {
            value = user.getMind().getTerms().get(valueId);
        }
        condition = new ArgList(dis, user);
        return this;
    }

    public void writeCompiledData(DataOutputStream dos, User user) throws IOException {
        dos.writeLong(id);
        dos.writeLong(function.getId());
        dos.writeLong(value == null ? -1 : value.getId());
        condition.writeCompiledData(dos, user);
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public Term setValue(Term value) {
        this.value = value;
        return value;
    }

    @Override
    public void clear() {
        value = null;
    }

    @Override
    public boolean isTVariable() {
        return false;
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isTValue() {
        return false;
    }

    @Override
    public boolean isTerm() {
        return false;
    }

    @Override
    public boolean isFValue() {
        return true;
    }

    @Override
    public boolean isCVariable() {
        return !isEmpty() && getValue().isCVariable();
    }

    @Override
    public boolean isDefined() {
        Term t = getValue();
        return t != null && !t.isCVariable();
    }

//    @Override
//    public boolean isCalculated() {
//        return !isEmpty();
//    }

    @Override
    public TVariable getTVariable() {
        return null;
    }

    @Override
    public Function getFunction() {
        return null;
    }

    @Override
    public TValue getTValue() {
        return null;
    }

    @Override
    public FValue getFValue() {
        return this;
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
