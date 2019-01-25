package kanger.storage;

import kanger.interfaces.Identifiable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
            Identifiable one = data.get(x.getData().get(0));
            return one;
        } else {
            return null;
        }
    }

    public List<Identifiable> find(long h) {
        List<Identifiable> list = new ArrayList<>();
        try {
            Index.IndexOne x = hash.getOne(h);
            if (x != null) {
                for (long offset : x.getData()) {
                    Identifiable o = data.get(offset);
                    if (o != null) {
                        list.add(o);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return list;
    }

    public long firstKey() {
        return index.firstKey();
    }

    public long lastKey() throws IOException {
        return index.lastKey();
    }

    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    public String getName() {
        return name;
    }

    public void clear() throws IOException {
        if(!isClosed()) {
            data.clear();
            index.clear();
            hash.clear();
            flush();
        }
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
            data.getFile().getAbsoluteFile().delete();
            tempFile.renameTo(data.getFile().getAbsoluteFile());
            data.open(data.getFile());
            flush();
        }
    }

    public int size() {
        if (!isClosed()) {
            return index.size();
        } else {
            return 0;
        }
    }

    public void remove() throws IOException {
        boolean wasOpened = false;
        if (index != null && !index.isClosed()) {
            index.close();
            wasOpened = true;
        }
        if (hash != null && !hash.isClosed()) {
            hash.close();
            wasOpened = true;
        }
        if (data != null && !data.isClosed()) {
            data.close();
            wasOpened = true;
        }

        if(wasOpened) {
            index.getFile().getAbsoluteFile().delete();
            hash.getFile().getAbsoluteFile().delete();
            data.getFile().getAbsoluteFile().delete();
        }
    }


    @Override
    public Iterator<Identifiable> iterator() {
        return new StorageIterator(true);
    }

    public Iterator<Identifiable> iterator(boolean backward) {
        return new StorageIterator(backward);
    }

    public class StorageIterator implements Iterator<Identifiable> {

        Iterator iterator;

        public StorageIterator(boolean backward) {
            iterator = index.iterator(backward);
        }

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
