package org.kanger.interfaces;

import org.kanger.User;

import java.io.IOException;

public interface IData {
    void init();

    void use(String name) throws Exception;

    void close() throws Exception;

    void flush() throws IOException;

    boolean isClosed();

    String getStorageName();

    IBase counstructBase(User user, String context) throws IOException;
}
