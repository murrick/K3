package org.kanger.interfaces;

import java.io.IOException;

public interface IUnit<T> {

    long getId();

    void setId(long id);

    int getHash() throws IOException, ClassNotFoundException;

    boolean equalsTo(T to) throws IOException, ClassNotFoundException;

    IUser getUser();

    void setUser(IUser user) throws IOException, ClassNotFoundException;

    boolean isDeleted();

    void setDeleted();
}
