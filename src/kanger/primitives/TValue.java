package kanger.primitives;

import kanger.User;
import kanger.interfaces.IValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by murray on 13.12.16.
 */
public class TValue implements IValue {


    private long id = -1;                   // Идентификатор значения переменной
    private Term value = null;
    private TVariable tVar = null;
    private Set<TValue.Solve> solves = new HashSet<>();
//    private List<Domain> srcSolves = new ArrayList<>();
//    private List<Domain> dstSolves = new ArrayList<>();
//    private List<Integer> posSolves = new ArrayList<>();

    private long commitId = 0;
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

    public Object setValue(Term value) {
        this.value = value;
        return value;
    }

    public Set<Solve> getSolves() {
        return solves;
    }

//    public Set<Domain> getSrcSolves() {
//        Set<Domain> set = new HashSet<>();
//        for(Solve s : solves) {
//            set.add(s.getSrc());
//        }
//        return set;
//    }
//
//    public Set<Domain> getDstSolves() {
//        Set<Domain> set = new HashSet<>();
//        for(Solve s : solves) {
//            set.add(s.getDst());
//        }
//        return set;
//    }


    public boolean addSolve(int index, Domain dst, Domain src) {
        TValue.Solve s = new TValue.Solve(index, dst, src);
        if (solves.contains(s)) {
            return false;
        } else {
            solves.add(s);
            return true;
        }
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
        return tVar.getVarName() + ":" + value.toString() + (commitId > 0 ? " " + commitId : "");
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

    public long getCommitId() {
        return commitId;
    }

    public void setCommitId(long commitId) {
        this.commitId = commitId;
    }

    public class Solve {
        private Domain src = null;
        private Domain dst = null;
        private int index = -1;

        public Solve(int index, Domain dst, Domain src) {
            this.index = index;
            this.dst = dst;
            this.src = src;
        }

        public Domain getSrc() {
            return src;
        }

        public void setSrc(Domain src) {
            this.src = src;
        }

        public Domain getDst() {
            return dst;
        }

        public void setDst(Domain dst) {
            this.dst = dst;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }


        @Override
        public int hashCode() {
            StringBuffer buffer = new StringBuffer();
            buffer.append(this.src.getId());
            buffer.append(this.dst.getId());
            buffer.append(this.index);
            return buffer.toString().hashCode();
        }

//        @Override
//        public int hashCode(){
//            return toString().hashCode();
//        }

        @Override
        public boolean equals(Object o) {
            return o != null
                    && o instanceof Solve
                    && src != null && dst != null
                    && ((Solve) o).getSrc() != null && ((Solve) o).getDst() != null
                    && src.getId() == ((Solve) o).getSrc().getId()
                    && dst.getId() == ((Solve) o).getDst().getId()
                    && index == ((Solve) o).getIndex();
        }
    }

}
