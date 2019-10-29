package org.kanger.interfaces;

import java.io.IOException;

public interface IData {
    void init();

    void use(String name) throws IOException;

    void close() throws IOException;

    void flush() throws IOException;

    boolean isClosed();

    String getStorageName();

    IBase counstructBase(IUser user, String context) throws IOException;
}
