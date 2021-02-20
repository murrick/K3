package org.kanger.interfaces;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public interface IUser {

    long getId();

    void setId(long id);

    String getProperty(String key, String defaultValue);

    void setProperty(String key, String defaultValue);

    void loadProperties(String confName) throws Exception;

    boolean containsProperty(String key);

    String getUserDir();

    String getDatabaseDir();

    String getSourceDir();

    void setUserDir(String dir);

    void setDatabaseDir(String dir);

    void setSourceDir(String dir);

}
