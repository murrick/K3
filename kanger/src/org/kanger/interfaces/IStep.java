package org.kanger.interfaces;


import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;

public interface IStep {

    ByteBuffer pack();

    IStep apply(ByteBuffer packet) throws OutOfBufferException, RuntimeErrorException, IOException, ClassNotFoundException;

//    Object getData(IUser user) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException;

    Object getData(Mind mind) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException;

    Object getData();

    void setData(Object data);

    IStep getNext();

    void setNext(IStep next);

    IStep getPrev();

    void setPrev(IStep prev);

    long getId();

    void setId(long id);

    int getHash();

    void setHash(int hash);

    void update() throws IOException;

    void append() throws IOException;

    IBase getBase();

    void setBase(IBase base);

    void delete() throws IOException;

    long getSize();

    void setSize(long sz);

}
