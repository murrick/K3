package org.kanger.storage;

import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Base implements IBase, Iterable<IStep> {

//    private static long MAX_CACHE_SIZE = 1024L * 1024L;

    private Index index = null;
    private Index hash = null;
    private Data data = null;
    private final Object locker = new Object();
    private boolean readonly = false;

    private String name = "";
    //    private final Map<Long, IStep> cache = new HashMap<>();
//    private final Queue<Long> timing = new LinkedList<>();
//    private volatile long cacheSize = 0L;
    private long lastId = -1;

    public Base(String name, boolean readonly) throws Exception {
        this.name = name;
        this.readonly = readonly;

        index = new Index();
        index.open(name + ".index", readonly);

        hash = new Index();
        hash.open(name + ".hash", readonly);

        data = new Data(this);
        data.open(name + ".data", readonly);

        IStep root = getRoot();
        if (root != null) {
            lastId = root.getId() + 1;
        } else {
            lastId = 0;
        }

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

    @Override
    public void add(IStep one) throws Exception {
        synchronized (locker) {
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
    }

    @Override
    public void update(IStep one) throws Exception {
        add(one);
    }


    public void flush() throws Exception {
        synchronized (locker) {
            index.flush();
            hash.flush();
            data.flush();
        }
    }

    @Override
    public IStep get(long id) throws Exception {
//        synchronized (cache) {
//            if (cache.containsKey(id)) {
//                timing.remove(id);
//                timing.add(id);
//                return cache.get(id);
//            }
//        }

//        synchronized (locker) {
        Index.IndexOne x = index.getOne(id);
        if (x != null) {
            IStep one = data.get(x.getData().get(0));
            if (one != null) {
//                one.setSize(data.getDataSize());

//                    synchronized (cache) {
//                        if (!cache.containsKey(id)) {
//                            cache.put(id, one);
//                            timing.add(id);
//                            cacheSize += one.getSize();
//                            while (cacheSize > MAX_CACHE_SIZE && timing.size() > 1) {
//                                long topId = timing.poll();
//                                IStep top = cache.remove(topId);
//                                cacheSize -= top.getSize();
//                            }
//                        }
//                    }
            }

            return one;
        } else {
            return null;
        }
//        }
    }

    public List<IStep> find(long h) {
        List<IStep> list = new ArrayList<>();
        try {
            Index.IndexOne x = hash.getOne(h);
            if (x != null) {
                for (long offset : x.getData()) {
                    IStep o = data.get(offset);
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

    public long lastKey() throws Exception {
        return index.lastKey();
    }

    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void clear() throws Exception {
        synchronized (locker) {
            data.clear();
            index.clear();
            hash.clear();
            flush();

            clearCache();
            lastId = 0;
        }
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        return index.getOne(id) != null;
    }

    @Override
    public IStep getRoot() {
        try {
            if (isEmpty()) {
                return null;
            } else {
                return get(index.lastKey());
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public IStep getTop() {
        try {
            if (isEmpty()) {
                return null;
            } else {
                return get(index.firstKey());
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    public long reindex() throws Exception {
        if (!isClosed()) {
            flush();
            long size = index.getFile().length()
                    + hash.getFile().length()
                    + data.getFile().length();
            File tempFile = new File(name + ".data.temp");
            Data tempData = new Data(this);
            tempData.open(tempFile, false);

            index.clear();
            hash.clear();

            for (IStep one : data) {
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
            data.open(data.getFile(), false);
            flush();

            return index.getFile().length()
                    + hash.getFile().length()
                    + data.getFile().length()
                    - size;

        } else {
            return 0;
        }
    }

    @Override
    public int size() {
        if (!isClosed()) {
            return index.size();
        } else {
            return 0;
        }
    }

    @Override
    public void clearCache() {
//        synchronized (cache) {
//            cache.clear();
//            timing.clear();
//            cacheSize = 0;
//        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return size() == 0;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
    }

    @Override
    public void delete(long id) throws Exception {
//        synchronized (cache) {
//            cache.remove(id);
//            timing.remove(id);
//        }
        synchronized (locker) {
            Index.IndexOne current = index.getOne(id);
            if (current != null) {
                index.remove(id);
                long currentOffset = current.getData().get(0);
                long currentHash = data.get(currentOffset).getHash();
                Index.IndexOne hashOne = hash.getOne(currentHash);
                List<Long> list = new ArrayList<>();
                list.addAll(hashOne.getData());
                list.remove(currentOffset);
                if (list.isEmpty()) {
                    hash.remove(currentHash);
                } else {
                    hash.set(currentHash, list);
                }
                data.remove(currentOffset);
            }
        }
    }

    @Override
    public long getUsedCacheSize() {
//        return cacheSize;
        return 0;
    }

    @Override
    public long getMaxCacheSize() {
        return 0; //MAX_CACHE_SIZE;
    }

    @Override
    public synchronized long lastId() {
        return lastId;
    }

    @Override
    public synchronized long nextId() {
        return lastId++;
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

        if (wasOpened) {
            index.getFile().getAbsoluteFile().delete();
            hash.getFile().getAbsoluteFile().delete();
            data.getFile().getAbsoluteFile().delete();
        }
    }


    @Override
    public Iterator<IStep> iterator() {
        return new StorageIterator(true);
    }

    public Iterator<IStep> iterator(boolean backward) {
        return new StorageIterator(backward);
    }

    public class StorageIterator implements Iterator<IStep> {

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
        public IStep next() {
            Index.IndexOne one = (Index.IndexOne) iterator.next();
            try {
                return data.get(one.getData().get(0));
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return null;
            }
        }
    }
}
