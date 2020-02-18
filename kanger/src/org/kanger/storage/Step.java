package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;

import java.io.IOException;

public class Step implements IStep {
    private Object data = null;
    private IStep next = null;
    private long id = -1;
    private int hash = 0;

    private long size = 0;

    @Override
    public ByteBuffer pack() {
        return null;
    }

    @Override
    public IStep apply(ByteBuffer packet) {
        return null;
    }

    @Override
    public Object getData(Mind mind) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
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
        return next;
    }

    @Override
    public void setNext(IStep next) {
        this.next = next;
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

//    @Override
//    public IBase getBase() {
//        return null;
//    }
//
//    @Override
//    public void setBase(IBase base) {
//
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
//    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }
}
