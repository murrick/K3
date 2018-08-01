package kanger.prototypes;

import kanger.interfaces.IEntry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 02.12.16.
 */
public class EntryX implements IEntry {
    protected long id = -1;
    protected IEntry next;

    public IEntry getNext() {
        return next;
    }

    public void setNext(IEntry next) {
        this.next = next;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public void writeCompiledData(DataOutputStream dos) throws IOException {

    }

    @Override
    public void readCompiledData(DataInputStream dis) throws IOException {

    }

}
