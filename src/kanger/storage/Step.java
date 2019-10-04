package kanger.storage;

import kanger.interfaces.IStep;

public class Step implements IStep {
    private Object data = null;
    private IStep next = null;
    private IStep prev = null;
    private long id = -1;
    private int hash = 0;

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
        return next;
    }

    @Override
    public void setNext(IStep next) {
        this.next = next;
    }

    @Override
    public IStep getPrev() {
        return prev;
    }

    @Override
    public void setPrev(IStep prev) {
        this.prev = prev;
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
    public void update() {

    }

    @Override
    public void append() {

    }

    @Override
    public Object getBase() {
        return null;
    }

    @Override
    public void setBase(Object base) {

    }
}
