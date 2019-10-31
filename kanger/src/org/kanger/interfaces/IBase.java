package org.kanger.interfaces;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.Sapato;

import java.io.IOException;

public interface IBase {

    void add(Sapato one) throws IOException;

    void update(Sapato one) throws IOException;

    IStep get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    int size() throws IOException;

    void clearCache();

    boolean isEmpty();

    void delete(long id) throws IOException;

    void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    boolean containsKey(long id) throws IOException;

    IStep getRoot();

    IStep getTop();

    String getName();

    long getUsedCacheSize();

    long getMaxCacheSize();
}
