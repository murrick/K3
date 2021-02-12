package org.kanger.storage;

import org.kanger.Global;
import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.units.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class Sapato implements IStep {

    private static final long serialVersionUID = 196402071117L;

    private Object data = null;
    private long next = -1;
    private long id = -1;
    private int hash = 0;

    private IBase base = null;
    private long size = 0;

    public Sapato(IBase base) {
        this.base = base;
    }

    public Sapato(IBase base, IStep c) {
        this.base = base;
        this.data = c.getData();
        this.next = c.getNext() == null ? -1 : c.getNext().getId();
        this.id = c.getId();
        this.hash = c.getHash();
    }

    @Override
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putInt(hash)
                .putLong(next);
        if (data instanceof IUnit) {
            packet.putInt(((IUnit) data).getUnitType().ordinal())
                    .append(((IUnit) data).pack());
        } else if (data instanceof Long) {
            packet.putInt(UnitType.LONG.ordinal())
                    .putLong((Long) data);
        } else if (data instanceof Collection) {
            packet.putInt(UnitType.LONGS.ordinal())
                    .putInt(((Collection<Long>) data).size());
            for (long id : (Collection<Long>) data) {
                packet.putLong(id);
            }
        }
        return packet.createMarked();
    }

    @Override
    public IStep apply(ByteBuffer packet) throws Exception {
        id = packet.getLong();
        hash = packet.getInt();
        next = packet.getLong();
        UnitType type = UnitType.values()[packet.getInt()];
        switch (type) {

            case LONG:
                data = packet.getLong();
                break;

            case LONGS:
                int cnt = packet.getInt();
                data = new ArrayList<Long>();
                while (cnt-- > 0) {
                    ((List<Long>) data).add(packet.getLong());
                }
                break;

            default:
                try {
                    packet.mark();
                    data = newInstance(type).apply(packet);
                    assert (data != null);
                } finally {
                    packet.release();
                }
        }
        return null;
    }


    @Override
    public Object getData(Mind mind) throws Exception {
        if (data != null && data instanceof IUnit) {
            ((IUnit) data).setMind(mind);
        }
        return data;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public IStep getNext() {
        try {
            return base.get(next);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public void setNext(IStep next) {
        this.next = next == null ? -1 : next.getId();
    }

//    @Override
//    public IStep getPrev() {
//        try {
//            return base.get(prev);
//        } catch (Exception e) {
//            e.printStackTrace(System.err);
//            return null;
//        }
//    }
//
//    @Override
//    public void setPrev(IStep prev) {
//        this.prev = prev == null ? -1 : prev.getId();
//    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public int getHash() {
        return hash;
    }

    @Override
    public void setHash(int hash) {
        this.hash = hash;
    }

    @Override
    public void update() throws Exception {
        base.update(this);
    }

    @Override
    public void append() throws Exception {
        base.add(this);
    }


//    @Override
//    public IBase getBase() {
//        return base;
//    }
//
//    @Override
//    public void setBase(IBase base) {
//        this.base = base;
//    }

//    @Override
//    public void delete() throws IOException {
//        if (getPrev() != null) {
//            getPrev().setNext(getNext());
//            getPrev().update();
//        }
//        if (getNext() != null) {
//            getNext().setPrev(getPrev());
//            getNext().update();
//        }
//        base.delete(id);
//    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long sz) {
        size = sz;
    }

    private IUnit newInstance(UnitType type) throws RuntimeErrorException {
        switch (type) {
            case TERM:
                return new Term();
            case RULE:
                return new Rule();
            case COMMENT:
                return new Comment();
            case DOMAIN:
                return new Domain();
            case FVALUE:
                return new FValue();
            case TVALUE:
                return new TValue();
            case FUNCTION:
                return new Function();
            case PREDICATE:
                return new Predicate();
            case TVARIABLE:
                return new TVariable();
            case SYSOP:
                return Global.getUdf();

            case HYPOTHESE:
            case ARGUMENT:
            case ARGLIST:
            case CAUSE:
                return null;

            default:
                return null;
        }
    }

}
