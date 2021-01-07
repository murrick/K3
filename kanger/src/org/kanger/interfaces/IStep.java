package org.kanger.interfaces;


import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.ByteBuffer;

public interface IStep {

    ByteBuffer pack();

    IStep apply(ByteBuffer packet) throws OutOfBufferException, RuntimeErrorException, Exception;

    Object getData(Mind mind) throws Exception;

    Object getData();

    void setData(Object data);

    IStep getNext();

    void setNext(IStep next);

    long getId();

    void setId(long id);

    int getHash();

    void setHash(int hash);

    void update() throws Exception;

    void append() throws Exception;

//    IBase getBase();
//
//    void setBase(IBase base);

//    void delete() throws IOException;

    long getSize();

    void setSize(long sz);

}
