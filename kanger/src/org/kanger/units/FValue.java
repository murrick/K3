package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.Operation;
import org.kanger.compiler.Parser;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.ByteBuffer;

import java.util.ArrayList;
import java.util.List;

public class FValue implements IUnit<FValue> {

    private static final long serialVersionUID = 196402070003L;

    private long id = -1;
    private long mindId = -1;                                   // id транзакции
    private Function function = null;
    private ITerm value = null;
    private ArgumentsList condition = new ArgumentsList();
    private List<Long> stamp = new ArrayList<>();

    //    private FValue next = null;
    private Mind mind = null;

    private transient long functionId = -1;
    private transient long valueId = -1;

//    private transient boolean deleted = false;

    public FValue() {
    }

    public FValue(Mind mind) {
        this.mind = mind;
    }

    public FValue(Function f, Mind mind) throws Exception {
        function = f;
        value = f.getArguments().get(f.getRange()).getValue(mind);
        functionId = function.getId();
        if (value != null) {
            valueId = value.getId();
        }
//        condition.setUser(user);
        for (Argument a : f.getArguments()) {
            if (a.isTSet()) {
                condition.add(new Argument(a.getT(mind).getCurrent()));
            } else if (a.isFSet()) {
                condition.add(new Argument(a.getF(mind).getCurrent()));
            } else {
                condition.add(new Argument(a.getValue(mind)));
            }
        }
        for (TVariable t : f.getArguments().getTVariables(mind)) {
            if (t.isEmpty()) {
                stamp.add(0L);
            } else {
                stamp.add(t.getCurrent().getValue().getId());
            }
        }
        this.mind = mind;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(functionId)
                .putLong(valueId);

        packet.putInt(stamp.size());
        for (long id : stamp) {
            packet.putLong(id);
        }
        packet.append(condition.pack());
        return packet.createMarked();
    }

    public FValue apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        functionId = packet.getLong();
        valueId = packet.getLong();
        int cnt = packet.getInt();
        while (cnt-- > 0) {
            stamp.add(packet.getLong());
        }
        try {
            packet.mark();
            condition = new ArgumentsList().apply(packet);
//            condition.setUser(user);
        } finally {
            packet.release();
        }
        return this;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
    }

    public ITerm getValue() throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().get(valueId);
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

    public Function getFunction() throws Exception {
        if (function == null) {
            function = mind.getFunctions().get(functionId);
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

    public ArgumentsList getCondition() {
        return condition;
    }

    private String formatParam(Argument t) throws Exception {
        Operation op = Parser.getOp(getFunction().getName().toString(), getFunction().getRange());
        boolean isOp = op != null && op.getRange() == getFunction().getRange();
        String s = "";
        if (t.isFSet()) {
            s += (isOp ? "(" : "") + t.getF(mind).toString() + (isOp ? ")" : "");
        } else if (t.isRSet()) {
            s += (isOp ? "(" : "") + t.getR(mind).toString() + (isOp ? ")" : "");
        } else if (t.isTSet()) {
            s += t.getT(mind).toString();
        } else if (t.isVSet()) {
            s += t.getV(mind).toString();
        } else if (!t.isEmpty(mind)) {
            s += t.getValue(mind).toString();
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
    public boolean equalsTo(FValue f) throws Exception {
        return equalsTo(f.getFunction());
    }

    public boolean equalsTo(Function f) {
        try {
            if (f.getId() == getFunction().getId()
                    && !f.getResult().isEmpty(mind)
                    && valueId == f.getResult().getValue(mind).getId()) {
                boolean complete = true;
                List<TVariable> list = f.getArguments().getTVariables(mind);
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
    public Mind getMind() {
        return mind;
    }

    @Override
    public FValue setMind(Mind mind) {
        this.mind = mind;
//        this.condition.setUser(user);
        return this;
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;

//        return ("" + id).hashCode();
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
                    if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0) {
                        //                if (getResult() != null) {
                        if (getValue() != null) {
                            res = " {= " + getValue() + "}";
                        } else if (condition.size() > function.getRange() && !condition.get(function.getRange()).isEmpty(mind)) {
                            res = " [= " + condition.get(function.getRange()).getValue(mind) + "]";
                        }
                    }
                    //Argument r = range < arguments.size() ? arguments.createCVar(range) : null;
                    return s + res;
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return "";
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.FVALUE;
    }

//    @Override
//    public FValue commit(Mind m) throws Exception {
//        setValue(value.commit(m));
//        for (Argument a : condition) {
//            a.setO((IUnit) a.getO(mind).commit(m));
//        }
//        List<Long> temp = new ArrayList<>();
//        for (long id : stamp) {
//            Term x = mind.getTerms().load(id);
//            if (x != null) {
//                temp.add(m.getTerms().add(x).getId());
//            } else {
//                temp.add(0L);
//            }
//        }
//        stamp = temp;
//        setMind(m);
//        return this;
//    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    public long getFunctionId() {
        return functionId;
    }

    @Override
    public boolean isLoaded() {
        return function != null && functionId == function.getId();
    }

}
