package kanger.interfaces;

import kanger.User;

import java.io.IOException;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash() throws IOException, ClassNotFoundException;

    boolean equalsTo(T to) throws IOException, ClassNotFoundException;

    User getUser();

    void setUser(User user) throws IOException, ClassNotFoundException;

//    void linkExternal(User user) throws RuntimeErrorException, Exception;
}
