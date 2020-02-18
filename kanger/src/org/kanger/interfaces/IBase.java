package org.kanger.interfaces;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;

public interface IBase {

    void add(IStep one) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void update(IStep one) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    IStep get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    int size() throws IOException;

    void clearCache();

    boolean isEmpty();

    void delete(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    void clear() throws IOException, OutOfBufferException;

    boolean containsKey(long id) throws IOException;

    IStep getRoot();

    IStep getTop();

    String getName();

    long getUsedCacheSize();

    long getMaxCacheSize();

    long lastId();

    long nextId();
}
