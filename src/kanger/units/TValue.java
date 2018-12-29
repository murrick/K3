package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.*;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by murray on 13.12.16.
 */
public class TValue implements Comparable<TValue>, Externalizable, Identifiable {


    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private SortedSet<Cause> causes = new TreeSet<>();

    private TValue next = null;          // Следующая переменная
    private User user = null;


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
        value = (Term) dis.readObject();
        tVar = (TVariable) dis.readObject();
        int count = dis.readInt();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeObject(value);
        dos.writeObject(tVar);
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }


    public Term getValue() {
        return value;
    }

    public Object setValue(Term value) {
        this.value = value;
        return value;
    }

    public SortedSet<Cause> getCauses() {
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

    public TValue getNext() {
        return next;
    }

    public void setNext(TValue next) {
        this.next = next;
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
    public void setClosed() {
        if (!user.getMind().getClosedValues().containsKey(tVar)) {
            user.getMind().getClosedValues().put(tVar, new HashSet<>());
        }
        user.getMind().getClosedValues().get(tVar).add(this);
    }

    public boolean isClosed() {
        return user.getMind().getClosedValues().containsKey(tVar) && user.getMind().getClosedValues().get(tVar).contains(this);
    }

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
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(id);
//        buffer.append(value.getId());
//        buffer.append(tVar.getId());
        return buffer.toString().hashCode();
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
