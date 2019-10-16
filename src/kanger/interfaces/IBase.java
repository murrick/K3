package kanger.interfaces;

import kanger.storage.Sapato;

import java.io.IOException;

public interface IBase {

    void add(Sapato one) throws IOException;

    void update(Sapato one) throws IOException;

    IStep get(long id) throws IOException, ClassNotFoundException;

    int size() throws IOException;

    void clearCache();

    boolean isEmpty();

    void delete(long id) throws IOException;

    void clear() throws IOException, ClassNotFoundException;

    boolean containsKey(long id) throws IOException;

    IStep getRoot();

    IStep getTop();
}
