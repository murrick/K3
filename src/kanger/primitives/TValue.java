package kanger.primitives;

import kanger.User;
import kanger.interfaces.IValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by murray on 13.12.16.
 */
public class TValue implements IValue, Comparable<TValue> {


    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private Right right = null;             // Ссылка на правило
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

    public TValue(DataInputStream dis, User user) throws IOException {
        this.user = user;
        user.getMind().getRightsLink().put(dis.readLong(), this);
        long valueId = dis.readLong();
        if(valueId != -1) {
            value = (Term) user.getMind().getTermsLink().get(valueId);
        }
        tVar = (TVariable) user.getMind().getTVariablesLink().get(dis.readLong());
        right = (Right) user.getMind().getRightsLink().get(dis.readLong());
        int count = dis.readInt();
        while(count-- > 0) {
            Cause c = new Cause(dis, user);
            causes.add(c);
        }
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(value == null ? -1 : value.getId());
        dos.writeLong(tVar.getId());
        dos.writeLong(right.getId());
        dos.writeInt(causes.size());
        for(Cause c : causes) {
            c.writeCompiledData(dos, user);
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


    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

    public long getId() {
        return id;
    }

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
//        return ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0 ? tVar.getVarName() + ":" : "") + value.toString();
        return tVar.getVarName() + "=" + value.toString();
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
        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TValue && ((TValue) obj).getId() == id;
    }

    @Override
    public boolean isEmpty() {
        return getValue() == null;
    }

    @Override
    public void clear() {
        setValue(null);
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
        return true;
    }

    @Override
    public boolean isTerm() {
        return false;
    }

    @Override
    public boolean isFValue() {
        return false;
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
        return this;
    }

    @Override
    public FValue getFValue() {
        return null;
    }

    //    public int getTag() {
//        return tag;
//    }
//
//    public void setTag(int tag) {
//        this.tag = tag;
//    }
//
    @Override
    public int compareTo(TValue o) {
        return (int) (tVar.getId() == o.getTVar().getId() ? id - o.getId() : tVar.getId() - o.getTVar().getId());
    }

}
