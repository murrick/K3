package org.kanger.interfaces;

public interface IComment {

    long getId();

    boolean isDeleted(IMind mind);

    String getComment();

    long getMindId();

}
