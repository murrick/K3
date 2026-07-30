/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.exception.DatabaseErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Base implements IBase, Iterable<IStep> {

    private static final long DEFAULT_MAX_CACHE_SIZE = 2048L * 2048L;

    private Index index = null;
    private Data data = null;
    private IntegrityManifest integrity = null;
    private RecoveryLog recovery = null;
    private Class udf = null;
    private final Object locker;

    private String name = "";
    private int baseCode = -1;

    /** Bounded access-ordered cache of hydrated persistent records. */
    private final LinkedHashMap<Long, IStep> cache =
            new LinkedHashMap<Long, IStep>(16, 0.75f, true);
    private final long maxCacheSize;
    private final boolean cacheEnabled;
    private long cacheSize = 0L;
    private long cacheHits = 0L;
    private long cacheMisses = 0L;
    private long cacheEvictions = 0L;

    private volatile long readRequestCount = 0L;
    private volatile long cacheHitCount = 0L;
    private volatile long cacheMissCount = 0L;
    private volatile long storageReadCount = 0L;
    private volatile long writeCount = 0L;
    private volatile long deleteCount = 0L;
    private volatile long flushCount = 0L;

    private long lastId = -1;

    public Base(String name, int baseCode, Object locker, boolean readonly, IUser user) throws Exception {
        this.name = name;
        this.baseCode = baseCode;
        this.locker = locker;
        try {
            ((User) user).getUdf();
            this.udf = ((User) user).getUdf().getClass();
        } catch (RuntimeErrorException e) {
            // UDF module is optional.
        }

        maxCacheSize = Math.max(0L, Long.parseLong(
                user.getProperty("cache.size", DEFAULT_MAX_CACHE_SIZE + "")));
        cacheEnabled = Boolean.parseBoolean(
                user.getProperty("cache.enable", "true")) && maxCacheSize > 0L;

        try {
            index = new Index(baseCode, locker, user);
            index.open(name + ".index", readonly);

            data = new Data(this, user);
            data.open(name + ".store", readonly);

            recovery = new RecoveryLog(name, baseCode, locker);
            if (recovery.hasPending()) {
                recovery.rollback(index, data);
                index.flush();
                data.flush();
                IntegrityRecovery.rebuild(name + ".integrity", baseCode,
                        index, data, locker);
                recovery.checkpoint();
            }

            integrity = new IntegrityManifest(name + ".integrity", baseCode, locker);
            integrity.openOrBootstrap(index, data);
        } catch (Exception failure) {
            closeOpenedFilesAfterFailure();
            throw failure;
        }

        IStep root = getRoot();
        if (root != null) {
            lastId = root.getId() + 1;
        } else {
            lastId = 0;
        }
    }

    private void closeOpenedFilesAfterFailure() {
        try {
            if (index != null && !index.isClosed()) {
                index.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (data != null && !data.isClosed()) {
                data.close();
            }
        } catch (Exception ignored) {
        }
    }

    private long cacheWeight(IStep one) {
        return one == null ? 0L : Math.max(1L, one.getSize());
    }

    private void removeCached(long id, boolean eviction) {
        IStep removed = cache.remove(id);
        if (removed != null) {
            cacheSize -= cacheWeight(removed);
            if (cacheSize < 0L) {
                cacheSize = 0L;
            }
            if (eviction) {
                ++cacheEvictions;
            }
        }
    }

    private void cacheRecord(long id, IStep one) {
        if (!cacheEnabled || one == null) {
            return;
        }
        synchronized (cache) {
            removeCached(id, false);
            cache.put(id, one);
            cacheSize += cacheWeight(one);

            Iterator<Map.Entry<Long, IStep>> iterator = cache.entrySet().iterator();
            while (cacheSize > maxCacheSize && iterator.hasNext()) {
                Map.Entry<Long, IStep> eldest = iterator.next();
                cacheSize -= cacheWeight(eldest.getValue());
                iterator.remove();
                ++cacheEvictions;
            }
            if (cacheSize < 0L) {
                cacheSize = 0L;
            }
        }
    }

    private void invalidateCached(long id) {
        synchronized (cache) {
            removeCached(id, false);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } catch (Exception e) {
            throw new IOException(e.toString());
        }
        clearCache();
        if (index != null && !index.isClosed()) {
            index.close();
        }
        if (data != null && !data.isClosed()) {
            data.close();
        }
    }

    @Override
    public void add(IStep one) throws Exception {
        ++writeCount;
        synchronized (locker) {
            recovery.prepareUpsert(one.getId(), index, data);
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
            integrity.put(one);
        }
        invalidateCached(one.getId());
    }

    @Override
    public void update(IStep one) throws Exception {
        add(one);
    }

    public void flush() throws Exception {
        ++flushCount;
        synchronized (locker) {
            index.flush();
            data.flush();
            integrity.flush();
            recovery.checkpoint();
        }
    }

    @Override
    public IStep get(long id) throws Exception {
        if (cacheEnabled) {
            synchronized (cache) {
                IStep cached = cache.get(id);
                if (cached != null) {
                    ++cacheHits;
                    return cached;
                }
                ++cacheMisses;
            }
        }

        ++cacheMissCount;
        synchronized (locker) {
            Index.IndexOne x = index.getOne(id);
            if (x != null) {
                ++storageReadCount;
                IStep one = data.get(x.getLong());
                if (one == null) {
                    throw new DatabaseErrorException(
                            "DUMB storage corruption: index points to missing record id=" + id);
                }
                if (one.getId() != id) {
                    throw new DatabaseErrorException(
                            "DUMB storage corruption: index/store id mismatch expected="
                                    + id + " actual=" + one.getId());
                }
                cacheRecord(id, one);
                return one;
            }
            return null;
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
                // Commit earlier journalled operations before entering the
                // historical whole-storage clear path. Clear itself is outside
                // the operation-level Q2 contract and is qualified separately.
                flush();
                data.clear();
                index.clear();
                integrity.clear();
                flush();
                clearCache();
                lastId = 0;
            }
        }
    }

    @Override
    public void reindex(IBase base, IMind mind) throws Exception {
        if (!index.isEmpty()) {
            for (Index.IndexOne one : index) {
                if (!one.isDeleted()) {
                    IStep stored = get(one.getId());
                    stored.getData((Mind) mind);
                    base.add(stored);
                }
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
            return isEmpty() ? null : get(index.lastKey());
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public IStep getTop() {
        try {
            return isEmpty() ? null : get(index.firstKey());
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
            cacheSize = 0L;
        }
    }

    @Override
    public boolean isEmpty() {
        return index.isEmpty();
    }

    @Override
    public void delete(long id) throws Exception {
        deleteAll(java.util.Collections.singleton(id));
    }

    @Override
    public void deleteAll(Collection<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            if (id != null) {
                invalidateCached(id);
            }
        }
        synchronized (locker) {
            recovery.prepareDelete(ids, index, data);
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                Index.IndexOne current = index.getOne(id);
                if (current != null) {
                    index.remove(id);
                    data.remove(current.getLong());
                    integrity.remove(id.longValue());
                }
            }
        }
    }

    @Override
    public long getUsedCacheSize() {
        synchronized (cache) {
            return cacheSize + data.getUsedCacheSize();
        }
    }

    @Override
    public long getMaxCacheSize() {
        return maxCacheSize + data.getMaxCacheSize();
    }

    @Override
    public long getCacheHits() {
        synchronized (cache) {
            return cacheHits + data.getCacheHits();
        }
    }

    @Override
    public long getCacheMisses() {
        synchronized (cache) {
            return cacheMisses + data.getCacheMisses();
        }
    }

    @Override
    public long getCacheEvictions() {
        synchronized (cache) {
            return cacheEvictions + data.getCacheEvictions();
        }
    }

    @Override
    public long getCachedEntryCount() {
        synchronized (cache) {
            return cache.size() + data.getCachedEntryCount();
        }
    }

    @Override
    public boolean isCacheEnabled() {
        return cacheEnabled || data.getMaxCacheSize() > 0L;
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

    @Override
    public Iterator<IStep> iterator() {
        return new StorageIterator(true);
    }

    public Iterator<IStep> iterator(boolean backward) {
        return new StorageIterator(backward);
    }

    public class StorageIterator implements Iterator<IStep> {
        private final Iterator iterator;

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
                return data.getUncached(one.getLong());
            } catch (Exception e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
                return null;
            }
        }
    }
}
