package org.kanger.interfaces;

import org.kanger.exception.RuntimeErrorException;

import java.io.IOException;

public interface IData {
    void init();

    void use(String name) throws IOException;

    void close() throws IOException;

    void flush() throws IOException;

    boolean isClosed();

    String getStorageName();

    IBase getBase(String context) throws IOException, RuntimeErrorException;
}
