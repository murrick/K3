package org.kanger.storage;

import org.kanger.User;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class Base implements IBase, Iterable<IStep> {

    private static long MAX_CACHE_SIZE = 2048L * 2048L;
    private static boolean CACHE_ENABLE = true;

    private Index index = null;
    private Data data = null;
    private Class udf = null;
    private final Object locker = new Object();

    private String name = "";
    private final Map<Long, IStep> cache = new HashMap<>();
    private final Queue<Long> timing = new LinkedList<>();
    private volatile long cacheSize = 0L;

    private long lastId = -1;

    public Base(String name, int baseCode, Object locker, boolean readonly, IUser user) throws Exception {
        this.name = name;
        this.udf = ((User) user).getUdf().getClass();

        MAX_CACHE_SIZE = Long.parseLong(user.getProperty("cache.size", (2048L * 2048L) + ""));
        CACHE_ENABLE = Boolean.parseBoolean(user.getProperty("cache.enable", "true"));

        index = new Index(baseCode, locker, user);
        index.open(name + ".index", readonly);

        data = new Data(this, user);
        data.open(name + ".store", readonly);

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
        if (data != null && !data.isClosed()) {
            data.close();
        }
    }

    @Override
    public void add(IStep one) throws Exception {
        synchronized (locker) {
            Index.IndexOne current = index.getOne(one.getId());
            if (current != null) {
                long currentOffset = current.getLong();
                long newOffset = data.set(currentOffset, one);
                if (newOffset != currentOffset) {
                    index.set(one.getId(), newOffset);
                }
            } else {
                long offset = data.add(one);
                index.set(one.getId(), offset);
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
            data.flush();
        }
    }

    @Override
    public IStep get(long id) throws Exception {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                if (cache.containsKey(id)) {
                    timing.remove(id);
                    timing.add(id);
                    return cache.get(id);
                }
            }
        }

        synchronized (locker) {
            Index.IndexOne x = index.getOne(id);
            if (x != null) {
                IStep one = data.get(x.getLong());
                if (one != null && CACHE_ENABLE) {
                    synchronized (cache) {
                        if (!cache.containsKey(id)) {
                            cache.put(id, one);
                            timing.add(id);
                            cacheSize += one.getSize();
                            while (cacheSize > MAX_CACHE_SIZE && timing.size() > 1) {
                                long topId = timing.poll();
                                IStep top = cache.remove(topId);
                                cacheSize -= top.getSize();
                            }
                        }
                    }
                }

                return one;
            } else {
                return null;
            }
        }
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
            if (!index.isEmpty()) {
                data.clear();
                index.clear();
                flush();

                clearCache();
                lastId = 0;
            }
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

//    public long reindex() throws Exception {
//        if (!isClosed()) {
//            flush();
//            long size = index.getFile().length()
//                    + hash.getFile().length()
//                    + data.getFile().length();
//            File tempFile = new File(name + ".data.temp");
//            Data tempData = new Data(this);
//            tempData.open(tempFile, false);
//
//            index.clear();
//            hash.clear();
//
//            for (IStep one : data) {
//                if (one != null) {
//                    long offset = tempData.add(one);
//                    index.set(one.getId(), offset);
//                    hash.add(one.getHash(), offset);
//                }
//            }
//
//            tempData.close();
//            data.close();
//            data.getFile().getAbsoluteFile().delete();
//            tempFile.renameTo(data.getFile().getAbsoluteFile());
//            data.open(data.getFile(), false);
//            flush();
//
//            return index.getFile().length()
//                    + hash.getFile().length()
//                    + data.getFile().length()
//                    - size;
//
//        } else {
//            return 0;
//        }
//    }

//    @Override
//    public int size() throws Exception {
//        if (!isClosed()) {
//            return index.size();
//        } else {
//            return 0;
//        }
//    }

    @Override
    public void clearCache() {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                cache.clear();
                timing.clear();
                cacheSize = 0;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return index.isEmpty();
    }

    @Override
    public void delete(long id) throws Exception {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                if (cache.containsKey(id)) {
                    timing.remove(id);
                    IStep top = cache.remove(id);
                    cacheSize -= top.getSize();
                }
            }
        }
        synchronized (locker) {
            Index.IndexOne current = index.getOne(id);
            if (current != null) {
                index.remove(id);
                long currentOffset = current.getLong();
                data.remove(currentOffset);
            }
        }
    }

    @Override
    public long getUsedCacheSize() {
        return cacheSize;
    }

    @Override
    public long getMaxCacheSize() {
        return MAX_CACHE_SIZE;
    }

    @Override
    public synchronized long lastId() {
        return lastId;
    }

    @Override
    public synchronized long nextId() {
        return lastId++;
    }

    @Override
    public Class getUdf() {
        return udf;
    }

//    public void remove() throws IOException {
//        boolean wasOpened = false;
//        if (index != null && !index.isClosed()) {
//            index.close();
//            wasOpened = true;
//        }
//        if (hash != null && !hash.isClosed()) {
//            hash.close();
//            wasOpened = true;
//        }
//        if (data != null && !data.isClosed()) {
//            data.close();
//            wasOpened = true;
//        }
//
//        if (wasOpened) {
//            index.getFile().getAbsoluteFile().delete();
//            hash.getFile().getAbsoluteFile().delete();
//            data.getFile().getAbsoluteFile().delete();
//        }
//    }


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
                return data.get(one.getLong());
            } catch (Exception e) {
                e.printStackTrace(System.err);
                return null;
            }
        }
    }
}
