package kanger.interfaces;

import java.io.IOException;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash();

    boolean equalsTo(T to);

//    Identifiable getNext();

    void linkExternal() throws IOException, ClassNotFoundException;
}
