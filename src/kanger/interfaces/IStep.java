package kanger.interfaces;


import java.io.IOException;

public interface IStep {

    Object getData();

    void setData(Object data);

    IStep getNext();

    void setNext(IStep next);

    IStep getPrev();

    void setPrev(IStep prev);

    long getId();

    void setId(long id);

    int getHash();

    void setHash(int hash);

    void update() throws IOException;

    void append() throws IOException;

    IBase getBase();

    void setBase(IBase base);

    void delete() throws IOException;

}
