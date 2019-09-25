package kanger.interfaces;

import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {
    void add(Identifiable one) throws Exception;

    void add(long id, Object one) throws Exception;

    Object get(long id) throws Exception;

    int size() throws Exception;

    boolean isEmpty() throws Exception;

    Set<Long> find(int h) throws Exception;

    void clear() throws Exception;

    void mark() throws Exception;

    long commit() throws Exception;

    long release() throws Exception;

    boolean containsKey(long id) throws Exception;

    @Override
    Iterator<Object> iterator();

    Iterator<Object> iterator(boolean backward, long fromId);
}
