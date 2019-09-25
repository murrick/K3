package kanger.units;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class FValue implements Externalizable, Identifiable<Function> {

    private static final long serialVersionUID = 196402070003L;

    private long id = -1;
    private Function function = null;
    private Term value = null;
    private ArgList condition = new ArgList();

    //    private FValue next = null;
    private User user = null;

    private transient long functionId = -1;
    private transient long valueId = -1;

    public FValue() {
    }

    public FValue(User user) {
        this.user = user;
    }

    public FValue(Function f, User user) throws Exception {
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
        functionId = dis.readLong();
        valueId = dis.readLong();
        condition = (ArgList) dis.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(function.getId());
        dos.writeLong(value.getId());
        dos.writeObject(condition);
    }

    public void linkExternal(User user) throws Exception {
        this.user = user;
        if (function == null && functionId != -1) {
            function = user.getMind().getFunctions().get(functionId);
            function.linkExternal(user);
        }
        if (value == null && valueId != -1) {
            value = user.getMind().getTerms().get(valueId);
            value.linkExternal(user);
        }
        condition.linkExternal(user);
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

    public Argument getCondition(int index) {
        return condition.get(index);
    }

    public ArgList getCondition() {
        return condition;
    }

    private String formatParam(Argument t) throws Exception {
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
        buffer.append(value == null ? 0 : value.getId());
        buffer.append(condition.hashCode());
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Function f) {
        try {
            if (f.getId() == getFunc().getId()
                    && (f.getArguments().get(f.getRange()).isEmpty()
                    || getValue().getId() == f.getArguments().get(f.getRange()).getValue().getId())) {
                boolean complete = true;
                for (int i = 0; i < f.getRange(); ++i) {
                    if (!f.getArguments().get(i).isEmpty() && f.getArguments().get(i).getValue().getId() != getCondition().get(i).getValue().getId()) {
                        complete = false;
                        break;
                    }
                }
                return complete;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
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
            try {
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
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return "";
            }
        }
    }

}
