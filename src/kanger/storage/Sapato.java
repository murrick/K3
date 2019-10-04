package kanger.storage;

import kanger.interfaces.IStep;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Sapato implements IStep, Externalizable {

    private static final long serialVersionUID = 196402071117L;

    private Object data = null;
    private long next = -1;
    private long prev = -1;
    private long id = -1;
    private int hash = 0;

    private Base base = null;

    public Sapato() {
    }

    public Sapato(Base base, IStep c) {
        this.base = base;
        this.data = c.getData();
        this.next = c.getNext() == null ? -1 : c.getNext().getId();
        this.prev = c.getPrev() == null ? -1 : c.getPrev().getId();
        this.id = c.getId();
        this.hash = c.getHash();
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        hash = dis.readInt();
        prev = dis.readLong();
        next = dis.readLong();
        data = dis.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeInt(hash);
        dos.writeLong(prev);
        dos.writeLong(next);
        dos.writeObject(data);
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

    @Override
    public IStep getPrev() {
        try {
            return base.get(prev);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public void setPrev(IStep prev) {
        this.prev = prev == null ? -1 : prev.getId();
    }

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
    public void update() throws IOException {
        base.update(this);
    }

    @Override
    public void append() throws IOException {
        base.add(this);
    }


    @Override
    public Base getBase() {
        return base;
    }

    @Override
    public void setBase(Object base) {
        this.base = (Base) base;
    }
}
