package org.kanger.interfaces;


import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;

public interface IStep {

    ByteBuffer pack();

    IStep apply(ByteBuffer packet) throws OutOfBufferException, RuntimeErrorException;

    Object getData(Mind mind) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException;

    Object getData();

    void setData(Object data);

    IStep getNext();

    void setNext(IStep next);

    long getId();

    void setId(long id);

    int getHash();

    void setHash(int hash);

    void update() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void append() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

//    IBase getBase();
//
//    void setBase(IBase base);

//    void delete() throws IOException;

    long getSize();

    void setSize(long sz);

}
