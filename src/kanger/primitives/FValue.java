package kanger.primitives;

import java.io.*;
import java.util.*;
import kanger.*;
import kanger.interfaces.*;

public class FValue implements IValue {
    private long id = -1;
    private Term value = null;
    //    private Map<Long, Long> condition = new HashMap<>();
    private List<Long> condition = new ArrayList<>();
    private Function function = null;

    private FValue next = null;
    private User user = null;

    public FValue(Function f, User user) {
        function = f;
        value = f.getArguments().get(f.getRange()).getValue(); //.getResult();
//        for(Argument a : f.getArguments()){
//            condition.createTVar(a.getValue().getId());
//        }
//        for (TVariable t : f.getTVariables()) {
//            condition.put(t.getId(), t.getCurrent().getId());
//        }
        for (Argument a : f.getArguments()) {
            condition.add(a.getDirtyValue().getId());
        }
        this.user = user;
    }

    public FValue(DataInputStream dis, User user) throws IOException {
        id = dis.readLong();
        function = user.getMind().getFunctions().get(dis.readLong());
        value = user.getMind().getTerms().get(dis.readLong());
        int count = dis.readInt();
        while (--count >= 0) {
//            condition.createTVar(dis.readLong());
            condition.add(dis.readLong());
        }
        this.user = user;
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

    @Override
    public boolean isCalculated() {
        return !isEmpty();
    }

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

    @Override
    public Term getDirtyValue() {
        return getValue();
    }

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

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(function.getId());
        dos.writeLong(value == null ? -1 : value.getId());
        dos.writeInt(condition.size());
//        for(long id : condition){
//            dos.writeLong(id);
//        }
        for (Long e : condition) {
            dos.writeLong(e);
        }
    }

    public boolean isActual(Function f) {
        for (int i = 0; i < function.getRange(); ++i) {
            if (function.getArguments().get(i).getDirtyValue() == null
                    || function.getArguments().get(i).getDirtyValue().getId() != condition.get(i)) {
                return false;
            }
        }
//        for (Map.Entry<Long, Long> e : condition.entrySet()) {
//            TVariable tv = mind.getTVars().get(e.getKey());
//            if (tv == null || tv.isEmpty() || tv.getCurrent().getId() != e.getValue()) {
//                return false;
//            }
//        }
        return true;
    }

    public long getCondition(int index) {
        return condition.get(index);
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

//    @Override
//    public String toString() {
//        return function.toString(this);
//    }
}
