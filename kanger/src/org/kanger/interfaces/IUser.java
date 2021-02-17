package org.kanger.interfaces;

import org.kanger.Mind;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IReactor;

import java.util.Collection;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public interface IUser {

    /**
     * Проверка на использования базы данных
     *
     * @return true - не используется, false - используется
     */
    boolean isClosed();

    /**
     * Пролучение имени текузей базы данных. Если БД не используется возвращается пустая строка.
     *
     * @return Имя текущей базы данных.
     */
    String getStorageName();

    Collection<String> getStoragesList();

    Mind close(Mind mind) throws Exception;

    Mind use(Mind mind, String name) throws Exception;

    void remove(Mind mind, String name) throws Exception;

    void reindex(IReactor iReactor) throws Exception;

    long getUsedCacheSize();

    long getMaxCacheSize();

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

    void setUserDir(String dir);

    void setDatabaseDir(String dir);

    void setSourceDir(String dir);

    long getId();

    void setId(long id);

}
