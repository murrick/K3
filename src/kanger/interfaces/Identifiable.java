package kanger.interfaces;

import kanger.User;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash();

    boolean equalsTo(T to);

    User getUser();

    void setUser(User user);

//    void linkExternal(User user) throws RuntimeErrorException, Exception;
}
