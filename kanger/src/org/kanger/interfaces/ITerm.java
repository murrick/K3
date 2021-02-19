package org.kanger.interfaces;

import org.kanger.enums.DataType;

public interface ITerm extends Comparable<Object> {

    DataType getType();

    long getId();

    Object getValue();

    boolean isEmpty();

    boolean isDeleted(IMind mind);

    long getMindId();

    boolean isCVariable();

    int getIndex();

    String toString();

}
