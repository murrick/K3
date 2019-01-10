package kanger.storage;

import kanger.interfaces.Identifiable;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

public class Storage implements Closeable {

    private Index index = null;
    private Index hash = null;
    private Data data = null;

    private String name = "";

    public void open(String name) throws IOException {
        this.name = name;

        index = new Index();
        index.open(name + ".index");

        hash = new Index();
        hash.open(name + ".hash");

        data = new Data();
        data.open(name + ".data");
    }

    @Override
    public void close() throws IOException {
        if(index != null && !index.isClosed()) {
            index.close();
        }
        if(hash != null && !hash.isClosed()) {
            hash.close();
        }
        if(data != null && !data.isClosed()) {
            data.close();
        }
    }

    public void flush() throws IOException {
        index.flush();
        hash.flush();
        data.flush();
    }

    public void add(Identifiable one) throws IOException {
        long offset = data.add(one);
        index.set(one.getId(), offset);
        hash.add(one.getHash(), offset);
    }

    public Identifiable get(long id) throws IOException, ClassNotFoundException {
        Index.IndexOne x = index.getOne(id);
        if(x != null) {
            return data.get(x.getData().get(0));
        } else {
            return null;
        }
    }

    public List<Identifiable> getByHash(long hash) throws IOException, ClassNotFoundException {
        List<Identifiable> list = new ArrayList<>();
        Index.IndexOne x = index.getOne(hash);
        if(x != null) {
            for(long offset : x.getData()) {
                Identifiable o = data.get(offset);
                if(o != null) {
                    list.add(o);
                }
            }
        }
        return list;
    }
}
