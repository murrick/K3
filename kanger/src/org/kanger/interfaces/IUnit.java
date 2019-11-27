package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;

public interface IUnit<T> {

    long getId();

    void setId(long id);

    long getMindId();

    void setMindId(long id);

    int getHash() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    boolean equalsTo(T to) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    Mind getMind();

    T setMind(Mind mind) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException;

    boolean isDeleted();

    void setDeleted();

    ByteBuffer pack();

    T apply(ByteBuffer packet) throws OutOfBufferException;

    UnitType getUnitType();

    T commit(Mind m) throws Exception;
}
