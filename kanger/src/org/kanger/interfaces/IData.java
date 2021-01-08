package org.kanger.interfaces;

public interface IData {
    void init(IUser user);

    void use(String name) throws Exception;

    void close() throws Exception;

    void flush() throws Exception;

    void remove() throws Exception;

    boolean isClosed();

    String getStorageName();

    IBase getBase(String context) throws Exception;

    IBase connect(String context) throws Exception;

    String getDescription();
}
