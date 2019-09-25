package kanger.interfaces;

import kanger.User;
import kanger.exception.RuntimeErrorException;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash();

    boolean equalsTo(T to);

    void linkExternal(User user) throws RuntimeErrorException, Exception;
}
