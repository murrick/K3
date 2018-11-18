package kanger.primitives;

import java.io.*;
import java.util.*;
import kanger.*;
import kanger.interfaces.*;

/**
 * Created by murray on 13.12.16.
 */
public class TValue implements IValue {


    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private List<Domain> srcSolves = new ArrayList<>();
    private List<Domain> dstSolves = new ArrayList<>();
    private List<Integer> posSolves = new ArrayList<>();

    private Right right = null;             // Ссылка на правило
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
        id = dis.readLong();
        tVar = user.getMind().getTVars().get(dis.readLong());
        value = user.getMind().getTerms().get(dis.readLong());
        long sid = dis.readLong();
        if (sid != -1) {
//            srcSolves = mind.getDomains().get(sid);
        }
        sid = dis.readLong();
        if (sid != -1) {
//            dstSolves = mind.getDomains().get(sid);
        }
        this.user = user;
    }

    
    public Term getValue() {
        return value;
    }

    @Override
    public Term getDirtyValue() {
        return getValue();
    }

    public Object setValue(Term value) {
        this.value = value;
        return value;
    }

    public List<Domain> getSrcSolves() {
        return srcSolves;
    }

    public void addSolve(int index, Domain src, Domain dst) {
        boolean found = false;
        for (int i = 0; i < srcSolves.size(); ++i) {
            if (srcSolves.get(i).getId() == src.getId()
                    && dstSolves.get(i).getId() == dst.getId()
                    && posSolves.get(i) == index) {
                found = true;
                break;
            }
        }
        if (!found) {
            this.srcSolves.add(src);
            this.dstSolves.add(dst);
            this.posSolves.add(index);
        }
    }

    public List<Domain> getDstSolves() {
        return dstSolves;
    }

    public List<Integer> getPosSolves() {
        return posSolves;
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

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(tVar.getId());
        dos.writeLong(value == null ? -1 : value.getId());
//        dos.writeLong(srcSolves == null ? -1 : srcSolves.getId());
//        dos.writeLong(dstSolves == null ? -1 : dstSolves.getId());
    }

    @Override
    public String toString() {
        return tVar.getVarName() + ":" + value.toString();
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
        return this;
    }

    @Override
    public FValue getFValue() {
        return null;
    }


}
