package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.exception.RuntimeErrorException;

import java.util.Collection;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public interface IUser {

    boolean isClosed();

    IBase getStorage(String schema);

    IBase connect(String schema) throws Exception;

    Mind clear(Mind mind) throws Exception;

    String getStorageName();

    Collection<String> getStoragesList();

    Mind close(Mind mind) throws Exception;

    Mind use(Mind mind, String name) throws Exception;

    void remove(Mind mind, String name) throws Exception;

    void reindex(IReactor iReactor) throws Exception;

    long getUsedCacheSize();

    long getMaxCacheSize();

    long lastId(String context);

    long nextId(String context);

    void clearCounters(String schema);

    long lastId();

    long nextId();

    void flush() throws Exception;

    String getProperty(String key, String defaultValue);

    void setProperty(String key, String defaultValue);

    void loadProperties(String confName) throws Exception;

    boolean containsProperty(String key);

    IData getData() throws RuntimeErrorException;

    void setData(IData db);

    String getUserDir();

    String getDatabaseDir();

    String getSourceDir();

    long getId();

    void setId(long id);

}
