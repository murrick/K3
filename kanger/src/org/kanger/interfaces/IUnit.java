package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.storage.ByteBuffer;

public interface IUnit<T> {

    long getId();

    void setId(long id);

    long getMindId();

    void setMindId(long id);

    int getHash() throws Exception;

    boolean equalsTo(T to) throws Exception;

    Mind getMind();

    T setMind(Mind mind) throws Exception;

    boolean isDeleted(Mind mind);

    void setDeleted(boolean on, Mind mind) throws Exception;

    ByteBuffer pack();

    T apply(ByteBuffer packet) throws Exception;

    UnitType getUnitType();

    boolean isLoaded();

//    T commit(Mind m) throws Exception;
}
