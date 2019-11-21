package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;

public interface IUser {

//    Mind getMind();

//    void setMind(Mind mind);

    boolean isClosed();

    IBase getStorage(String schema);

    void clear(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    String getStorageName();

    void close() throws IOException;

    Mind use(Mind mind, String name) throws RuntimeErrorException, IOException, OutOfBufferException, ClassNotFoundException;

    void remove() throws IOException, RuntimeErrorException;

    void reindex(IReactor iReactor) throws IOException, RuntimeErrorException;

    long getUsedCacheSize();

    long getMaxCacheSize();

    void clearCache();

    long lastId(String context);

    long nextId(String context);

    void clearCounters(String schema);

    long lastId();

    long nextId();

    void flush() throws IOException;

}
