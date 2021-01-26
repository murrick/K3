package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.HashSet;

/**
 * Created by Dmitry G. Qusnetsov on 13.12.16.
 */
public class TValue implements Comparable<TValue>, IUnit<TValue> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                   // Идентификатор значения переменной
    private long mindId = -1;                                   // id транзакции
    private Term value = null;
    private TVariable tVar = null;
//    private long tag = 0;
//    private Set<Cause> causes = new HashSet<>();

//    private boolean cVariable = false;        // Родительская c-переменная
//    private long parentId = -1;               // Родительская c-переменная

    //    private TValue next = null;          // Следующая переменная
    private transient long valueId = -1;
    private transient long tVarId = -1;
    private transient Mind mind = null;

    private transient boolean deleted = false;
    private transient boolean calculated = false;

    public TValue() {
    }

    public TValue(TVariable var, Term val) {
        tVar = var;
        value = val;
        tVarId = tVar.getId();
        valueId = value.getId();
//        cVariable = val.isCVariable();
    }

    public TValue(Mind mind) {
        this.mind = mind;
    }

    public TValue(TVariable tv, Term t, Mind mind) {
        this.mind = mind;
        this.tVar = tv;
        this.value = t;
        tVarId = tVar.getId();
        valueId = value.getId();
//        cVariable = t.isCVariable();
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0)
                .putLong(valueId)
                .putLong(tVarId);
//                .putByte(cVariable ? 1 : 0);
//                .putLong(parentId);

//                .putInt(causes.size());
//        for (Cause c : causes) {
//            packet.append(c.pack());
//        }
        return packet.createMarked();
    }

    public TValue apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        valueId = packet.getLong();
        tVarId = packet.getLong();
//        cVariable = packet.getByte() != 0;
//        parentId = packet.getLong();
//        int count = packet.getInt();
//        while (count-- > 0) {
//            try {
//                packet.mark();
//                Cause c = new Cause().apply(packet);
////                c.setUser(user);
//                causes.add(c);
//            } finally {
//                packet.release();
//            }
//        }
        return this;
    }

    public Term getValue() throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().load(valueId);
        }
        return value;
    }

    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
//        cVariable = value.isCVariable();
    }

//    public Set<Cause> getCauses() {
//        return causes;
//    }


    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public TVariable getTVar() throws Exception {
        if (tVar == null && tVarId != -1) {
            tVar = mind.getTVars().load(tVarId);
        }
        return tVar;
    }

    public void setTVar(TVariable tVar) {
        this.tVar = tVar;
        this.tVarId = tVar.getId();
    }

//    public long getTag() {
//        return tag;
//    }
//
//    public void setTag(long tag) {
//        this.tag = tag;
//    }

    @Override
    public String toString() {
        try {
            return ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? getTVar().getVarName() + "=" : "") + getValue().toString();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    public void setQuery(Mind mind) throws Exception {
        if (!mind.getQueryValues().containsKey(getTVar())) {
            mind.getQueryValues().put(getTVar(), new HashSet<>());
        }
        mind.getQueryValues().get(getTVar()).add(this);
    }

    //    public void setBlocked() {
//        if (!mind.getBlockedValues().containsKey(tVar.getId())) {
//            mind.getBlockedValues().put(tVar.getId(), new HashSet<>());
//        }
//        mind.getBlockedValues().get(tVar.getId()).add(id);
//    }
//
//    public boolean isBlocked() {
//        return mind.getBlockedValues().containsKey(tVar.getId()) && mind.getBlockedValues().get(tVar.getId()).contains(id);
//    }
//
    //    public boolean isRelativeFor(TValue slave) {
//        for (Domain m : dstSolves) {
//            if (slave.getSrcSolves().contains(m)) {
//                return true;
//            }
//        }
//        for (Domain m : srcSolves) {
//            if (slave.getDstSolves().contains(m)) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        hash = 47 * hash + (int) (tVarId ^ (tVarId >>> 32));
        return hash;
    }

    @Override
    public boolean equalsTo(TValue to) {
        return to.getTVarId() == tVarId && to.getValueId() == valueId;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TValue setMind(Mind mind) {
        this.mind = mind;
//        for (Cause c : causes) {
//            c.setUser(user);
//        }
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
//        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TValue && ((TValue) obj).getId() == id;
    }

    @Override
    public int compareTo(TValue o) {
        return (int) (tVarId == o.getTVarId() ? id - o.getId() : tVarId - o.getTVarId());
    }

    public long getValueId() {
        return valueId;
    }

    public long getTVarId() {
        return tVarId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        deleted = true;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVALUE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

//    public TValue commit(Mind m) throws Exception {
//        setMind(m);
//        setValue(value.commit(m));
////        for (Cause c : causes) {
////            c.commit(mind, m);
////        }
//        return this;
//    }


    //    public long getParentId() {
//        return parentId;
//    }
//
//    public void setParentId(long parentId) {
//        this.parentId = parentId;
//    }
//
//    public boolean isCVariable() {
//        return cVariable;
//    }
//
//    public void setCVariable(boolean cVariable) {
//        this.cVariable = cVariable;
//    }

    @Override
    public boolean isLoaded() {
        return value != null && valueId == value.getId();
    }

    public boolean isCalculated() {
        return calculated;
    }

    public void setCalculated(boolean calculated) {
        this.calculated = calculated;
    }

}
