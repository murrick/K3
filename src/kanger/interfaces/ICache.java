package kanger.interfaces;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {

    void add(Identifiable one) throws IOException, ClassNotFoundException;

    void add(long id, Object one) throws IOException;

    Object get(long id) throws IOException, ClassNotFoundException;

    void delete(long id) throws IOException, ClassNotFoundException;

    int size();

    boolean isEmpty();

    Set<Long> find(int h) throws IOException, ClassNotFoundException;

    void clear() throws IOException, ClassNotFoundException;

    void mark();

    long commit();

    long release() throws IOException, ClassNotFoundException;

    boolean containsKey(long id) throws IOException, ClassNotFoundException;

    void unlink() throws IOException;

    IStep getRoot();

    void setRoot(IStep root);

    IStep getTop();

    void setTop(IStep top);

    @Override
    Iterator<Object> iterator();

    Iterator<Object> iterator(boolean backward, long fromId);

    boolean update() throws IOException;

}
