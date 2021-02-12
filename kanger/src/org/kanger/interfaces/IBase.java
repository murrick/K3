package org.kanger.interfaces;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public interface IBase {

    void add(IStep one) throws Exception;

    void update(IStep one) throws Exception;

    IStep get(long id) throws Exception;

//    int size() throws Exception;

    void clearCache();

    boolean isEmpty();

    void delete(long id) throws Exception;

    void clear() throws Exception;

    boolean containsKey(long id) throws Exception;

    IStep getRoot();

    IStep getTop();

    String getName();

    long getUsedCacheSize();

    long getMaxCacheSize();

    long lastId();

    long nextId();

    void flush() throws Exception;

    void close() throws Exception;
}
