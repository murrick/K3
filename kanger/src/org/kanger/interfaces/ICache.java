package org.kanger.interfaces;

import org.kanger.exception.OutOfBufferException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {

    void add(IUnit one) throws IOException, ClassNotFoundException, OutOfBufferException;

    void add(long id, Object one) throws IOException;

    Object get(long id) throws IOException, ClassNotFoundException, OutOfBufferException;

    void delete(long id) throws IOException, ClassNotFoundException;

    int size();

    boolean isEmpty();

    Set<Long> find(int h) throws IOException, ClassNotFoundException, OutOfBufferException;

    void clear() throws IOException, ClassNotFoundException, OutOfBufferException;

    long mark();

    long commit();

    long release() throws IOException, ClassNotFoundException;

    boolean containsKey(long id) throws IOException, ClassNotFoundException, OutOfBufferException;

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
