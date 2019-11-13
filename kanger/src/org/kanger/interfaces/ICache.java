package org.kanger.interfaces;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public interface ICache extends Iterable {

    void add(IUnit one) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void add(long id, Object one) throws IOException;

    Object get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void delete(long id) throws IOException, ClassNotFoundException;

    int size();

    boolean isEmpty();

    Set<Long> find(int h) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    long mark();

    long commit();

    long release() throws IOException, ClassNotFoundException;

    boolean containsKey(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    Iterator<Object> iterator(long fromId);

    IStep getRoot();

    void setRoot(IStep root);

    @Override
    Iterator<Object> iterator();

    boolean update() throws IOException;

    void delete(IStep s);

}
