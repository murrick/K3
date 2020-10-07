package org.kanger.interfaces;

import org.kanger.Mind;

public interface IUser {

//    Mind getMind();

//    void setMind(Mind mind);

    boolean isClosed();

    IBase getStorage(String schema);

    void clear(Mind mind) throws Exception;

    String getStorageName();

    void close() throws Exception;

    Mind use(Mind mind, String name) throws Exception;

    void remove() throws Exception;

    void reindex(IReactor iReactor) throws Exception;

    long getUsedCacheSize();

    long getMaxCacheSize();

    void clearCache();

    long lastId(String context);

    long nextId(String context);

    void clearCounters(String schema);

    long lastId();

    long nextId();

    void flush() throws Exception;

}
