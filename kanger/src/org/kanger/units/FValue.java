package org.kanger.units;

import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class FValue implements Externalizable, IUnit<Function> {

    private static final long serialVersionUID = 196402070003L;

    private long id = -1;
    private Function function = null;
    private Term value = null;
    private ArgList condition = new ArgList();
    private List<Long> stamp = new ArrayList<>();

    //    private FValue next = null;
    private IUser user = null;

    private transient long functionId = -1;
    private transient long valueId = -1;

    private transient boolean deleted = false;

    public FValue() {
    }

//    public FValue(User user) {
//        this.user = user;
//    }

    public FValue(Function f, IUser user) throws IOException, ClassNotFoundException {
        function = f;
        value = f.getArguments().get(f.getRange()).getValue();
        functionId = function.getId();
        if (value != null) {
            valueId = value.getId();
        }
        condition.setUser(user);
        for (Argument a : f.getArguments()) {
            if (a.isTSet()) {
                condition.add(new Argument(a.getT().getCurrent()));
            } else if (a.isFSet()) {
                condition.add(new Argument(a.getF().getCurrent()));
            } else {
                condition.add(new Argument(a.getValue()));
            }
        }
        for (TVariable t : f.getArguments().getTVariables(true)) {
            if (t.isEmpty()) {
                stamp.add(0L);
            } else {
                stamp.add(t.getCurrent().getValue().getId());
            }
        }
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        deleted = dis.readBoolean();
        functionId = dis.readLong();
        valueId = dis.readLong();
        int cnt = dis.readInt();
        for (int i = 0; i < cnt; ++i) {
            stamp.add(dis.readLong());
        }
        condition = (ArgList) dis.readObject();
        condition.setUser(user);
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeBoolean(deleted);
        dos.writeLong(functionId);
        dos.writeLong(valueId);
        dos.writeInt(stamp.size());
        for (long id : stamp) {
            dos.writeLong(id);
        }
        dos.writeObject(condition);
    }

//    @Override
//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        function = user.getMind().getFunctions().load(functionId);
//        value = user.getMind().getTerms().load(valueId);
////        condition.linkExternal(user);
//    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

//    public Term setValue(Term value) {
//        this.value = value;
//        return value;
//    }

    public Term getValue() throws IOException, ClassNotFoundException {
        if (value == null && valueId != -1) {
            value = user.getMind().getTerms().load(valueId);
        }
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

    public Function getFunction() throws IOException, ClassNotFoundException {
        if (function == null) {
            function = user.getMind().getFunctions().load(functionId);
        }
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
        this.functionId = function.getId();
    }

//    public Argument getCondition(int index) {
//        return condition.get(index);
//    }

    public ArgList getCondition() {
        return condition;
    }

    private String formatParam(Argument t) throws Exception {
        Operation op = Parser.getOp(getFunction().getName().toString(), getFunction().getRange());
        boolean isOp = op != null && op.getRange() == getFunction().getRange();
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
        int hash = 3;
        hash = 47 * hash + (int) (functionId ^ (functionId >>> 32));
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        for (long id : stamp) {
            hash = 47 * hash + (int) (id ^ (id >>> 32));
        }
        return hash;
    }

    @Override
    public boolean equalsTo(Function f) {
        try {
            if (f.getId() == getFunction().getId()
                    && !f.getResult().isEmpty()
                    && valueId == f.getResult().getValue().getId()) {
                boolean complete = true;
                List<TVariable> list = f.getArguments().getTVariables(true);
                for (int i = 0; i < list.size(); ++i) {
                    if (list.get(i).isEmpty() || list.get(i).getValue().getId() != stamp.get(i)) {
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
    public IUser getUser() {
        return user;
    }

    @Override
    public void setUser(IUser user) {
        this.user = user;
        this.condition.setUser(user);
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted() {
        deleted = true;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    @Override
    public String toString() {
        try {
            if (!getFunction().isCalculable() && getValue() != null) {
                return getValue().toString();
            } else {
                try {
                    Operation op = Parser.getOp(getFunction().getName().toString(), getFunction().getRange());
                    String s = "";
                    if (op == null || op.getRange() != getFunction().getRange()) {
                        s = String.format("%s(", getFunction().getName().toString());
                        for (int i = 0; i < getFunction().getRange(); ++i) {
                            s += formatParam(condition.get(i));
                            if (i + 1 < getFunction().getRange()) {
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
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

}
