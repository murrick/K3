package org.kanger.interfaces;

import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {

    void add(IUnit one) throws Exception;

//    void add(long id, Object one) throws Exception;

    Object get(long id) throws Exception;

    void delete(long id) throws Exception;

    int size();

    boolean isEmpty();

    Set<Long> find(int h) throws Exception;

    void clear() throws Exception;

    long mark();

    long commit();

    long release() throws Exception;

    boolean containsKey(long id) throws Exception;

    Iterator<Object> iterator(long fromId);

    IStep getRoot();

    void setRoot(IStep root);

    @Override
    Iterator<Object> iterator();

    boolean update() throws Exception;

}
