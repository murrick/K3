package kanger.interfaces;

import kanger.prototypes.EntryX;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 05.12.16.
 */
public interface IFactory {
    void mark();

    void release();

    void apply();

    void reset();

    IEntry getRoot();

    IEntry getApplied();

    IEntry append(IEntry entry);

    IEntry register(IEntry entry);

    IEntry find(IEntry IEntry);

    void remove(IEntry entry);

    long counter();

    long getLastID();

    long getCounter();

    IEntry getEntry(long id);

    int size();

    void writeCompiledData(DataOutputStream dos) throws IOException;

    public void readCompiledData(DataInputStream dis, Class baseClass) throws IOException;

}
