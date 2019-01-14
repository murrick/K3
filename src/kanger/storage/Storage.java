package kanger.storage;

import kanger.interfaces.Identifiable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Storage implements Closeable, Iterable<Identifiable> {

    private Index index = null;
    private Index hash = null;
    private Data data = null;

    private String name = "";

    public Storage open(String name) throws IOException {
        this.name = name;

        index = new Index();
        index.open(name + ".index");

        hash = new Index();
        hash.open(name + ".hash");

        data = new Data();
        data.open(name + ".data");

        return this;
    }

    @Override
    public void close() throws IOException {
        if (index != null && !index.isClosed()) {
            index.close();
        }
        if (hash != null && !hash.isClosed()) {
            hash.close();
        }
        if (data != null && !data.isClosed()) {
            data.close();
        }
    }

    public void flush() throws IOException {
        index.flush();
        hash.flush();
        data.flush();
    }

    public void add(Identifiable one) throws IOException, ClassNotFoundException {
        Index.IndexOne current = index.getOne(one.getId());
        if (current != null) {
            long currentOffset = current.getData().get(0);
            long currentHash = data.get(currentOffset).getHash();
            long newHash = one.getHash();
            long newOffset = data.set(currentOffset, one);
            if (newOffset != currentOffset) {
                index.set(one.getId(), newOffset);
            }
            if (newHash != currentHash) {
                Index.IndexOne hashOne = hash.getOne(currentHash);
                List<Long> list = new ArrayList<>();
                list.addAll(hashOne.getData());
                list.remove(currentOffset);
                if (list.isEmpty()) {
                    hash.remove(currentHash);
                } else {
                    hash.set(currentHash, list);
                }
            }
            hash.add(newHash, newOffset);
        } else {
            long offset = data.add(one);
            index.set(one.getId(), offset);
            hash.add(one.getHash(), offset);
        }
    }

    public Identifiable get(long id) throws IOException, ClassNotFoundException {
        Index.IndexOne x = index.getOne(id);
        if (x != null) {
            return data.get(x.getData().get(0));
        } else {
            return null;
        }
    }

    public List<Identifiable> find(long h) throws IOException, ClassNotFoundException {
        List<Identifiable> list = new ArrayList<>();
        Index.IndexOne x = hash.getOne(h);
        if (x != null) {
            for (long offset : x.getData()) {
                Identifiable o = data.get(offset);
                if (o != null) {
                    list.add(o);
                }
            }
        }
        return list;
    }

    public boolean isClosed() {
        return data.isClosed();
    }

    public void reindex() throws IOException {
        if (!isClosed()) {
            File tempFile = new File(name + ".data.temp");
            Data tempData = new Data();
            tempData.open(tempFile);

            index.clear();
            hash.clear();

            for (Identifiable one : data) {
                if (one != null) {
                    long offset = tempData.add(one);
                    index.set(one.getId(), offset);
                    hash.add(one.getHash(), offset);
                }
            }

            tempData.close();
            data.close();
            data.getFile().delete();
            tempFile.renameTo(data.getFile());
            data.open(data.getFile());
            flush();
        }
    }

    @Override
    public Iterator<Identifiable> iterator() {
        return new StorageIterator();
    }

    public class StorageIterator implements Iterator<Identifiable> {

        Iterator iterator = index.iterator(true);

        @Override
        public void remove() {

        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Identifiable next() {
            Index.IndexOne one = (Index.IndexOne) iterator.next();
            try {
                return data.get(one.getData().get(0));
            } catch (IOException e) {
                return null;
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
