package org.kanger.units;

import org.kanger.enums.Enums;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 13.12.16.
 */
public class TValue implements Comparable<TValue>, Externalizable, IUnit<TValue> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private long tag = 0;
    private Set<Cause> causes = new HashSet<>();

    //    private TValue next = null;          // Следующая переменная
    private transient long valueId = -1;
    private transient long tVarId = -1;
    private transient IUser user = null;

    private transient boolean deleted = false;

    public TValue() {
    }

    public TValue(TVariable var, Term val) {
        tVar = var;
        value = val;
        tVarId = tVar.getId();
        valueId = value.getId();
    }

    public TValue(IUser user) {
        this.user = user;
    }

    public TValue(TVariable tv, Term t, IUser user) {
        this.user = user;
        this.tVar = tv;
        this.value = t;
        tVarId = tVar.getId();
        valueId = value.getId();

    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        deleted = dis.readBoolean();
        valueId = dis.readLong();
        tVarId = dis.readLong();
        int count = dis.readInt();
        causes.clear();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            c.setUser(user);
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeBoolean(deleted);
        dos.writeLong(valueId);
        dos.writeLong(tVarId);
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }

//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        this.user = user;
//        tVar = user.getMind().getTVars().load(tVarId);
//        value = user.getMind().getTerms().load(valueId);
//        for (Cause c : causes) {
////            c.linkExternal(user);
//        }
//    }

    public Term getValue() throws IOException, ClassNotFoundException {
        if (value == null && valueId != -1) {
            value = user.getMind().getTerms().load(valueId);
        }
        return value;
    }

//    public Object setValue(Term value) {
//        this.value = value;
//        return value;
//    }

    public Set<Cause> getCauses() {
        return causes;
    }


    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public TVariable getTVar() throws IOException, ClassNotFoundException {
        if (tVar == null && tVarId != -1) {
            tVar = user.getMind().getTVars().load(tVarId);
        }
        return tVar;
    }

    public void setTVar(TVariable tVar) {
        this.tVar = tVar;
        this.tVarId = tVar.getId();
    }

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

    @Override
    public String toString() {
        try {
            return ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? getTVar().getVarName() + "=" : "") + getValue().toString();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    public void setQuery() throws IOException, ClassNotFoundException {
        if (!user.getMind().getQueryValues().containsKey(getTVar())) {
            user.getMind().getQueryValues().put(getTVar(), new HashSet<>());
        }
        user.getMind().getQueryValues().get(getTVar()).add(this);
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
    public IUser getUser() {
        return user;
    }

    @Override
    public void setUser(IUser user) {
        this.user = user;
        for (Cause c : causes) {
            c.setUser(user);
        }
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
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
}
