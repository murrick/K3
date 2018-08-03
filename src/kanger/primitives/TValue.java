package kanger.primitives;

import kanger.Mind;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by murray on 13.12.16.
 */
public class TValue {

    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private Set<Long> srcSolve = new HashSet<>();
    private Set<Long> dstSolve = new HashSet<>();

    private Right right = null;             // Ссылка на правило
    private TValue next = null;          // Следующая переменная

    private Mind mind = null;


    public TValue(Mind mind) {
        this.mind = mind;
    }

    public TValue(TVariable tv, Term t, Mind mind) {
        this.mind = mind;
        this.tVar = tv;
        this.value = t;
    }

    public TValue(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        tVar = mind.getTVars().get(dis.readLong());
        value = mind.getTerms().get(dis.readLong());
        long sid = dis.readLong();
        if (sid != -1) {
//            srcSolve = mind.getDomains().get(sid);
        }
        sid = dis.readLong();
        if (sid != -1) {
//            dstSolve = mind.getDomains().get(sid);
        }
        this.mind = mind;
    }

    public Term getValue() {
        return value;
    }

    public void setValue(Term value) {
        this.value = value;
    }

    public Set<Long> getSrcSolve() {
        return srcSolve;
    }

    public void addSrcSolve(Domain srcSolve) {
        this.srcSolve.add(srcSolve.getId());
    }

    public Set<Long> getDstSolve() {
        return dstSolve;
    }

    public void addDstSolve(Domain dstSolve) {
        this.dstSolve.add(dstSolve.getId());
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
//        dos.writeLong(srcSolve == null ? -1 : srcSolve.getId());
//        dos.writeLong(dstSolve == null ? -1 : dstSolve.getId());
    }

    @Override
    public String toString() {
        return tVar.getVarName() + ":" + value.toString();
    }

    public void setQuery() {
        if (!mind.getQueryValues().containsKey(tVar.getId())) {
            mind.getQueryValues().put(tVar.getId(), new HashSet<>());
        }
        mind.getQueryValues().get(tVar.getId()).add(id);
    }

    public void setBlocked() {
        if (!mind.getBlockedValues().containsKey(tVar.getId())) {
            mind.getBlockedValues().put(tVar.getId(), new HashSet<>());
        }
        mind.getBlockedValues().get(tVar.getId()).add(id);
    }

    public boolean isBlocked() {
        return mind.getBlockedValues().containsKey(tVar.getId()) && mind.getBlockedValues().get(tVar.getId()).contains(id);
    }

    public void setClosed() {
        if (!mind.getClosedValues().containsKey(tVar.getId())) {
            mind.getClosedValues().put(tVar.getId(), new HashSet<>());
        }
        mind.getClosedValues().get(tVar.getId()).add(id);
    }

    public boolean isClosed() {
        return mind.getClosedValues().containsKey(tVar.getId()) && mind.getClosedValues().get(tVar.getId()).contains(id);
    }

}
