package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.exception.RuntimeErrorException;

import java.util.Collection;

public interface IUser {

//    Mind getMind();

//    void setMind(Mind mind);

    boolean isClosed();

    IBase getStorage(String schema);

    IBase connect(String schema) throws Exception;

    Mind clear(Mind mind) throws Exception;

    String getStorageName();

    Collection<String> getStoragesList();

    Mind close(Mind mind) throws Exception;

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

    Object getLocker();

    String getProperty(String key, String defaultValue);

    boolean containsKey(String s);

    IData getData() throws RuntimeErrorException;

    void setData(IData db);

    String getUserDir();

    String getDatabaseDir();

    String getSourceDir();
}
