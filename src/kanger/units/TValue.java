package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 13.12.16.
 */
public class TValue implements Comparable<TValue>, Externalizable, Identifiable<TValue> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private long tag = 0;
    private Set<Cause> causes = new HashSet<>();

    //    private TValue next = null;          // Следующая переменная
    private User user = null;

    private transient long valueId = -1;
    private transient long tVarId = -1;

    public TValue() {
    }

    public TValue(TVariable var, Term val) {
        tVar = var;
        value = val;
    }

    public TValue(User user) {
        this.user = user;
    }

    public TValue(TVariable tv, Term t, User user) {
        this.user = user;
        this.tVar = tv;
        this.value = t;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        valueId = dis.readLong();
        tVarId = dis.readLong();
        int count = dis.readInt();
        causes.clear();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(value.getId());
        dos.writeLong(tVar.getId());
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }

    public void linkExternal(User user) throws IOException, ClassNotFoundException {
        this.user = user;
        tVar = user.getMind().getTVars().load(tVarId);
        value = user.getMind().getTerms().load(valueId);
        for (Cause c : causes) {
//            c.linkExternal(user);
        }
    }

    public Term getValue() {
        return value;
    }

    public Object setValue(Term value) {
        this.value = value;
        return value;
    }

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

    public TVariable getTVar() {
        return tVar;
    }

    public void setTVar(TVariable tVar) {
        this.tVar = tVar;
    }

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

    @Override
    public String toString() {
        return ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? tVar.getVarName() + "=" : "") + value.toString();
    }

    public void setQuery() {
        if (!user.getMind().getQueryValues().containsKey(tVar)) {
            user.getMind().getQueryValues().put(tVar, new HashSet<>());
        }
        user.getMind().getQueryValues().get(tVar).add(this);
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
        StringBuffer buffer = new StringBuffer();
//        buffer.append(id);
        buffer.append(value.getId());
        buffer.append(tVar.getId());
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(TValue to) {
        return to.getTVar().getId() == getTVar().getId() && getValue().getId() == to.getValue().getId();
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public void setUser(User user) {
        this.user = user;
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
        return (int) (tVar.getId() == o.getTVar().getId() ? id - o.getId() : tVar.getId() - o.getTVar().getId());
    }

}
