package org.kanger.storage;

import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;

import java.io.IOException;

public class Step implements IStep {
    private Object data = null;
    private IStep next = null;
    private IStep prev = null;
    private long id = -1;
    private int hash = 0;

    private long size = 0;

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
    public IBase getBase() {
        return null;
    }

    @Override
    public void setBase(IBase base) {

    }

    @Override
    public void delete() throws IOException {
        if (getPrev() != null) {
            getPrev().setNext(getNext());
            getPrev().update();
        }
        if (getNext() != null) {
            getNext().setPrev(getPrev());
            getNext().update();
        }
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }
}
