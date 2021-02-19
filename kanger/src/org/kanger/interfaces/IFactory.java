package org.kanger.interfaces;

public interface IFactory<T> extends Iterable<T> {

    T get(long id) throws Exception;

    int size() throws Exception;

    boolean isEmpty();
}
