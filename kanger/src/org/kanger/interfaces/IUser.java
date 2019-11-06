package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;

public interface IUser {

    Mind getMind();

    void setMind(Mind mind);

    boolean isClosed();

    IBase getStorage(String schema);

    void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException;

    String getStorageName();

    void close() throws IOException;

    void use(String name) throws RuntimeErrorException, IOException;

    void remove() throws IOException, RuntimeErrorException;

    void reindex(IReactor iReactor) throws IOException, RuntimeErrorException;

    long getUsedCacheSize();

    long getMaxCacheSize();

    void clearCache();

    long lastId(String context);

    long nextId(String context);
}
