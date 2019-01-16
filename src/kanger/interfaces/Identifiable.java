package kanger.interfaces;

public interface Identifiable<T> {

    long getId();

    void setId(long id);

    int getHash();

    boolean equalsTo(T to);

    //TODO: getNext() Удалить после отладки!
    Identifiable getNext();

    void linkExternal();
}
