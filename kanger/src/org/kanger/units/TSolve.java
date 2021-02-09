package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.Solve;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 13.12.16.
 */
public class TSolve implements Comparable<TSolve>, IUnit<TSolve> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                   // Идентификатор значения переменной
    private long mindId = -1;                                   // id транзакции
    private List<TValue> solve = new ArrayList<>();
    private Solve variant = null;
//    private long tag = 0;
//    private Set<Cause> causes = new HashSet<>();

    //    private TValue next = null;          // Следующая переменная
    private transient List<Long> solveIds = new ArrayList<>();
    private transient Mind mind = null;

//    private transient boolean deleted = false;

    public TSolve() {
    }

    public TSolve(List<TValue> list, Mind mind) {
        this.solve.addAll(list);
        for (TValue v : solve) {
            solveIds.add(v.getId());
        }
        this.mind = mind;
    }

    public TSolve(Domain d, Mind mind) throws Exception {
        this.solve.addAll(d.getArguments().getTValues(mind, true));
        for (TValue v : solve) {
            solveIds.add(v.getId());
        }
        this.mind = mind;
        variant = new Solve(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public TSolve(TValue vv, Mind mind) {
        solve.add(vv);
        solveIds.add(vv.getId());
        this.mind = mind;
    }

    public void add(TValue v) {
        if (!solveIds.contains(v.getId())) {
            solve.add(v);
            solveIds.add(v.getId());
        }
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0);
        packet.putInt(solve.size());
        for (TValue v : solve) {
            packet.putLong(v.getId());
        }
//        packet.putInt(causes.size());
//        for (Cause c : causes) {
//            packet.append(c.pack());
//        }
        return packet.createMarked();
    }

    public TSolve apply(ByteBuffer packet) throws OutOfBufferException {
        solveIds.clear();
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        int count = packet.getInt();
        while (count-- > 0) {
            solveIds.add(packet.getLong());
        }
//        count = packet.getInt();
//        while (count-- > 0) {
//            try {
//                packet.mark();
//                Cause c = new Cause().apply(packet);
//                causes.add(c);
//            } finally {
//                packet.release();
//            }
//        }
        return this;
    }

    public List<TValue> getSolve() throws Exception {
        if (solve.isEmpty() && !solveIds.isEmpty()) {
            for (long id : solveIds) {
                TValue v = mind.getTValues().load(id);
                solve.add(v);
            }
        }
        return solve;
    }

//    public void setSolve(List<TValue> solve) {
//        this.solve = solve;
//        solveIds.clear();
//        for (TValue v : solve) {
//            solveIds.add(v.getId());
//        }
//    }

//    public Set<Cause> getCauses() {
//        return causes;
//    }

    public TValue getValue(TVariable t) throws Exception {
        for (TValue v : solve) {
            if (t.getId() == v.getTVar().getId()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
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
            String str = "";
            for (TValue v : getSolve()) {
                if (!str.isEmpty()) {
                    str += ", ";
                }
                str += v.toString();
            }
            return str;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int getHash() {
        int hash = 3;
        for (long id : solveIds) {
            hash = 47 * hash + (int) (id ^ (id >>> 32));
        }
        return hash;
    }

    @Override
    public boolean equalsTo(TSolve to) {
        if (solveIds.size() != to.solveIds.size()) {
            return false;
        }
        for (long id : solveIds) {
            if (!to.solveIds.contains(id)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TSolve setMind(Mind mind) {
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
        return obj != null && obj instanceof TSolve && ((TSolve) obj).getId() == id;
    }


    @Override
    public int compareTo(TSolve o) {
        return (int) (id - o.getId());
    }

    @Override
    public boolean isDeleted(Mind mind) {
        return mind.isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
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

    public void clearCurrent() throws Exception {
        for (TValue v : solve) {
            v.getTVar().setCurrent(null);
        }
    }

    /**
     * @return 3 состояния. null, пустой список, полный список
     * @throws ClassNotFoundException
     * @throws RuntimeErrorException
     * @throws OutOfBufferException
     * @throws IOException
     */
    public Set<TVariable> activate() throws Exception {
        Set<TVariable> list = new HashSet<>();
        boolean fail = false;
        for (TValue v : solve) {
            if (!v.getTVar().isEmpty() && v.getTVar().getValue().getId() != v.getValueId()) {
                return null;
            }
        }
        if (!fail) {
            for (TValue v : solve) {
                if (v.getTVar().isEmpty()) {
                    v.getTVar().setCurrent(v);
                    list.add(v.getTVar());
                }
            }
        }
        return list;
    }

    public boolean contains(Collection<TValue> vals) throws Exception {
        for (TValue x : vals) {
            if (!containsTVar(x.getTVar())) {
                return false;
            }
            if (!containsTValue(x)) {
                return false;
            }
        }
        return true;
    }

    public boolean containsTVar(TVariable t) {
        for (TValue v : solve) {
            if (v.getTVarId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsTValue(TValue t) {
        for (TValue v : solve) {
            if (t != null && v.getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return solve.size();
    }

    public boolean isValid(TValue[] solve) throws Exception {
        for (TValue v : solve) {
            if (v != null) {
                TValue x = getValue(v.getTVar());
                if (x != null) {
                    if (v.getValue().getId() != x.getValue().getId()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public Solve getVariant() {
        return variant;
    }

    public void setVariant(Solve variant) {
        this.variant = variant;
    }

//    public TSolve commit(Mind m) throws Exception {
//        setMind(m);
//        setValue(value.commit(m));
//        for (Cause c : causes) {
//            c.commit(mind, m);
//        }
//        return this;
//    }

    @Override
    public boolean isLoaded() {
        return true;
    }


}
