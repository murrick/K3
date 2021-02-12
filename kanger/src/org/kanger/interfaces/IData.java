package org.kanger.interfaces;

import java.util.Collection;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public interface IData {
    void init(IUser user);

    void use(String name) throws Exception;

    void close() throws Exception;

    void flush() throws Exception;

    void remove(String name) throws Exception;

    boolean isClosed();

    String getStorageName();

    IBase getBase(String context) throws Exception;

    IBase connect(String context) throws Exception;

    String getDescription();

    Collection<String> list();
}
