package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.Cause;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 13.12.16.
 */
public class TSolve implements Comparable<TSolve>, IUnit<TSolve> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                   // Идентификатор значения переменной
    private long mindId = -1;                                   // id транзакции
    private List<TValue> solve = new ArrayList<>();
    private long tag = 0;
    private Set<Cause> causes = new HashSet<>();

    //    private TValue next = null;          // Следующая переменная
    private transient List<Long> solveIds = new ArrayList<>();
    private transient Mind mind = null;

    private transient boolean deleted = false;

    public TSolve() {
    }

    public TSolve(List<TValue> list, Mind mind) {
        this.solve = list;
        this.mind = mind;
        for (TValue v : list) {
            solveIds.add(v.getId());
        }
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0);
        packet.putInt(solve.size());
        for (TValue v : solve) {
            packet.putLong(v.getId());
        }
        packet.putInt(causes.size());
        for (Cause c : causes) {
            packet.append(c.pack());
        }
        return packet.createMarked();
    }

    public TSolve apply(ByteBuffer packet) throws OutOfBufferException {
        solveIds.clear();
        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        int count = packet.getInt();
        while (count-- > 0) {
            solveIds.add(packet.getLong());
        }
        count = packet.getInt();
        while (count-- > 0) {
            try {
                packet.mark();
                Cause c = new Cause().apply(packet);
                causes.add(c);
            } finally {
                packet.release();
            }
        }
        return this;
    }

    public List<TValue> getSolve() throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        if (solve.isEmpty() && !solveIds.isEmpty()) {
            for (long id : solveIds) {
                TValue v = mind.getTValues().load(id);
                solve.add(v);
            }
        }
        return solve;
    }

    public void setSolve(List<TValue> solve) {
        this.solve = solve;
        solveIds.clear();
        for (TValue v : solve) {
            solveIds.add(v.getId());
        }
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

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

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
        } catch (IOException | ClassNotFoundException | OutOfBufferException | RuntimeErrorException e) {
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

//    public TSolve commit(Mind m) throws Exception {
//        setMind(m);
//        setValue(value.commit(m));
//        for (Cause c : causes) {
//            c.commit(mind, m);
//        }
//        return this;
//    }
}
