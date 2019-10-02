package kanger.interfaces;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {

    void add(Identifiable one) throws Exception;

    void add(long id, Object one) throws Exception;

    Object get(long id) throws IOException, ClassNotFoundException;

    int size() throws Exception;

    boolean isEmpty();

    Set<Long> find(int h) throws IOException, ClassNotFoundException;

    void clear() throws Exception;

    void mark() throws Exception;

    long commit() throws Exception;

    long release() throws Exception;

    boolean containsKey(long id) throws Exception;

    void unlink() throws Exception;

    IStep getRoot();

    void setRoot(IStep root);

    IStep getTop();

    void setTop(IStep top);

    @Override
    Iterator<Object> iterator();

    Iterator<Object> iterator(boolean backward, long fromId);

    boolean update() throws Exception;

}
