package kanger.primitives;

import kanger.Mind;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FValue {
    private long id = -1;
    private Term value = null;
    private Map<Long, Long> condition = new HashMap<>();
    //    private List<Long> condition = new ArrayList<>();
    private Function function = null;

    private FValue next = null;
    private Mind mind = null;

    public FValue(Function f, Mind mind) {
        function = f;
        value = f.getArguments().get(f.getRange()).getValue(); //.getResult();
//        for(Argument a : f.getArguments()){
//            condition.createTVar(a.getValue().getId());
//        }
        for (TVariable t : f.getTVariables()) {
            condition.put(t.getId(), t.getCurrent().getId());
        }
        this.mind = mind;
    }

    public FValue(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        function = mind.getFunctions().get(dis.readLong());
        value = mind.getTerms().get(dis.readLong());
        int count = dis.readInt();
        while (--count >= 0) {
//            condition.createTVar(dis.readLong());
            condition.put(dis.readLong(), dis.readLong());
        }
        this.mind = mind;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setValue(Term value) {
        this.value = value;
    }

    public Term getValue() {
        return value;
    }

    public TValue getValue(TVariable t) {
        if (condition.containsKey(t.getId())) {
            return mind.getTValues().get(condition.get(t.getId()));
        } else {
            return null;
        }
    }

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

    public Function getFunction() {
        return function;
    }

    public void setNext(FValue next) {
        this.next = next;
    }

    public FValue getNext() {
        return next;
    }

    public void setMind(Mind mind) {
        this.mind = mind;
    }

    public Mind getMind() {
        return mind;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(function.getId());
        dos.writeLong(value == null ? -1 : value.getId());
        dos.writeInt(condition.size());
//        for(long id : condition){
//            dos.writeLong(id);
//        }
        for (Map.Entry<Long, Long> e : condition.entrySet()) {
            dos.writeLong(e.getKey());
            dos.writeLong(e.getValue());
        }
    }

    public boolean isActual() {
//        for(int i=0; i<function.getRange(); ++i){
//            if(function.getArguments().createCVar(i).isEmpty()
//            || function.getArguments().createCVar(i).getValue().getId() != condition.createCVar(i)){
//                return false;
//            }
//        }
        for (Map.Entry<Long, Long> e : condition.entrySet()) {
            TVariable tv = mind.getTVars().get(e.getKey());
            if (tv == null || tv.isEmpty() || tv.getCurrent().getId() != e.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean isClosed() {
        for (long id : condition.values()) {
            if (mind.getTValues().get(id) == null || !mind.getTValues().get(id).isClosed()) {
                return false;
            }
        }
        return true;
    }

    public boolean isBlocked() {
        for (long id : condition.values()) {
            if (mind.getTValues().get(id) != null && mind.getTValues().get(id).isBlocked()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return function.toString(this);
    }
}
