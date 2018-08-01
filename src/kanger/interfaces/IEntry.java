package kanger.interfaces;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 05.12.16.
 */
public interface IEntry {
    IEntry getNext();

    void setNext(IEntry next);

    long getId();

    void setId(long id);

    void writeCompiledData(DataOutputStream dos) throws IOException;

    public void readCompiledData(DataInputStream dis) throws IOException;

}
