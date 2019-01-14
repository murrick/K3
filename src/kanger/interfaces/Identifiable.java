package kanger.interfaces;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash();

    boolean equalsTo(T to);

    Identifiable getNext();

    void linkExternal();
}
