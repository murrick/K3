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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Логическая база DUMB, занимающая один {@code baseCode} в общих физических
 * файлах поколения.
 *
 * <p><strong>Архитектурная роль.</strong> {@code Base} реализует {@link IBase}
 * для одной схемы KANGER и связывает четыре физических механизма: ID-to-offset
 * {@link Index}, block store {@link Data}, undo-журнал {@link RecoveryLog} и
 * контроль целостности {@link IntegrityManifest}. Он предоставляет hydration,
 * mutation, delete, flush, endpoint reconstruction и schema-local ID allocation,
 * но не владеет всем поколением файлов — container lifecycle принадлежит
 * {@link DB}.</p>
 *
 * <p><strong>Открытие и recovery.</strong> Сначала открываются index и store.
 * Если WAL содержит незавершённый checkpoint, before-images откатываются в оба
 * файла, восстановленный subset принудительно flush-ится, его integrity-запись
 * пересобирается, и только затем WAL checkpoint удаляет журнал. После этого
 * manifest открывается либо явно bootstrap-ится для legacy generation, а
 * linked endpoints проверяются по persisted {@code next}-ссылкам.</p>
 *
 * <p><strong>Порядок цепочки.</strong> Корень и хвост нельзя выводить из
 * минимального или максимального ID: параллельные транзакции способны выделять
 * идентификаторы в одном порядке, а публиковаться в другом. Авторитетен только
 * граф {@link Sapato#getNextId()}. При восстановлении требуется ровно один не
 * referenced root, ровно один tail с sentinel {@code -1}, отсутствие dangling
 * links, duplicate IDs и чужих записей по index offset. Нарушение является
 * corruption, а не поводом перейти к сортировке по ID.</p>
 *
 * <p><strong>Mutation protocol.</strong> До изменения записи WAL сохраняет её
 * первое before-image. Затем обновляется или размещается packed block в
 * {@link Data}, при relocation корректируется {@link Index}, после чего
 * integrity delta получает новое semantic содержимое. Удаление аналогично
 * проходит WAL, index, block tombstone и integrity delta. {@link #flush()}
 * фиксирует index, затем store, затем integrity и лишь после этого завершает
 * recovery checkpoint.</p>
 *
 * <p><strong>Кэши.</strong> Верхний access-ordered LRU хранит hydrated
 * persistent {@link IStep} по semantic ID. Внутри {@link Data} существует
 * независимый LRU packed blocks по physical offset. Оба являются только
 * физическими ускорителями: они не определяют identity, chain membership или
 * committed state. Mutation инвалидирует hydrated entry; sequential reindex и
 * endpoint scans используют uncached чтение и не вытесняют hot blocks.</p>
 *
 * <p><strong>ID и reindex.</strong> {@link #nextId()} выделяет идентификаторы
 * только внутри этой схемы. Начальное значение восстанавливается по максимальному
 * indexed ID, но это не превращает ID order в chain order. {@link #reindex(IBase,
 * IMind)} гидратирует каждую живую запись и переносит её в destination base;
 * publication нового поколения координирует {@link DB}.</p>
 *
 * <p><strong>Закрытие.</strong> {@link #close()} сначала выполняет полный
 * flush, затем compaction integrity manifest, очищает hydrated cache и закрывает
 * index/store handles. Ошибка flush/compact не маскируется успешным закрытием и
 * должна быть обработана container-level retry protocol.</p>
 *
 * <p><strong>Concurrency.</strong> Index, store, WAL, integrity и endpoint
 * reconstruction сериализуются общим locker поколения. Hydrated LRU имеет
 * отдельный monitor. Код не должен удерживать cache monitor во время semantic
 * hydration или пользовательских callback; canonical semantic ownership
 * остаётся за фабриками и {@link org.kanger.storage.Escalera}.</p>
 *
 * @see DB
 * @see Data
 * @see IBase
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

    /*
     * Chain position is semantic storage metadata, not numeric ID order.
     * Parallel sibling transactions can allocate IDs in one order and commit
     * them in another, so index.firstKey()/lastKey() are not chain endpoints.
     */
    private Long rootId = null;
    private Long topId = null;

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
                DumbFaultInjector.hit("recovery-after-rollback");
                index.flush();
                DumbFaultInjector.hit("recovery-after-index");
                data.flush();
                DumbFaultInjector.hit("recovery-after-data");
                IntegrityManifest.recoverSubset(name + ".integrity", baseCode,
                        index, data, locker);
                DumbFaultInjector.hit("recovery-after-integrity");
                recovery.checkpoint();
                DumbFaultInjector.hit("recovery-after-checkpoint");
            }

            integrity = new IntegrityManifest(name + ".integrity", baseCode, locker);
            integrity.openOrBootstrap(index, data);
            resolveEndpoints();
        } catch (Exception failure) {
            closeOpenedFilesAfterFailure();
            throw failure;
        }

        if (!index.isEmpty()) {
            long maximumId = index.lastKey();
            lastId = maximumId < 0L ? 0L : maximumId + 1L;
        } else {
            lastId = 0L;
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

    private void invalidateEndpoints() {
        rootId = null;
        topId = null;
    }

    /**
     * Reconstruct the linked-chain endpoints from persisted next references.
     * Exactly one record is not referenced by another record (root), and
     * exactly one record points to -1 (oldest/top). Any other shape is a
     * structural storage error rather than an ID-order fallback.
     */
    private void resolveEndpoints() throws Exception {
        synchronized (locker) {
            if (index.isEmpty()) {
                rootId = null;
                topId = null;
                return;
            }
            if (rootId != null && topId != null
                    && index.getOne(rootId.longValue()) != null
                    && index.getOne(topId.longValue()) != null) {
                return;
            }

            Set<Long> ids = new HashSet<Long>();
            Set<Long> referenced = new HashSet<Long>();
            Long tail = null;
            Iterator<Index.IndexOne> iterator = index.iterator(false);
            if (iterator == null) {
                throw new DatabaseErrorException(
                        "DUMB storage corruption: cannot enumerate base=" + baseCode);
            }
            while (iterator.hasNext()) {
                Index.IndexOne one = iterator.next();
                if (one == null) {
                    throw new DatabaseErrorException(
                            "DUMB storage corruption: null index record base=" + baseCode);
                }
                IStep stored = data.getUncached(one.getLong());
                if (!(stored instanceof Sapato) || stored.getId() != one.getId()) {
                    throw new DatabaseErrorException(
                            "DUMB storage corruption: invalid chain record base="
                                    + baseCode + " id=" + one.getId());
                }
                long id = stored.getId();
                if (!ids.add(Long.valueOf(id))) {
                    throw new DatabaseErrorException(
                            "DUMB storage corruption: duplicate chain id base="
                                    + baseCode + " id=" + id);
                }
                long nextId = ((Sapato) stored).getNextId();
                if (nextId == -1L) {
                    if (tail != null && tail.longValue() != id) {
                        throw new DatabaseErrorException(
                                "DUMB storage corruption: multiple chain tails base="
                                        + baseCode + " ids=" + tail + "," + id);
                    }
                    tail = Long.valueOf(id);
                } else {
                    referenced.add(Long.valueOf(nextId));
                }
            }

            if (!ids.containsAll(referenced)) {
                Set<Long> missing = new HashSet<Long>(referenced);
                missing.removeAll(ids);
                throw new DatabaseErrorException(
                        "DUMB storage corruption: dangling chain links base="
                                + baseCode + " missing=" + missing);
            }
            Set<Long> roots = new HashSet<Long>(ids);
            roots.removeAll(referenced);
            if (roots.size() != 1 || tail == null) {
                throw new DatabaseErrorException(
                        "DUMB storage corruption: invalid chain endpoints base="
                                + baseCode + " roots=" + roots + " tail=" + tail);
            }
            rootId = roots.iterator().next();
            topId = tail;
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
            integrity.compact();
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
        invalidateEndpoints();
    }

    @Override
    public void add(IStep one) throws Exception {
        ++writeCount;
        synchronized (locker) {
            recovery.prepareUpsert(one.getId(), index, data);
            DumbFaultInjector.hit("upsert-after-wal");
            Index.IndexOne current = index.getOne(one.getId());
            if (current != null) {
                long currentOffset = current.getLong();
                long newOffset = data.set(currentOffset, one);
                DumbFaultInjector.hit("upsert-after-data");
                if (newOffset != currentOffset) {
                    index.set(one.getId(), newOffset);
                }
            } else {
                long offset = data.add(one);
                DumbFaultInjector.hit("upsert-after-data");
                index.set(one.getId(), offset);
            }
            DumbFaultInjector.hit("upsert-after-index");
            integrity.put(one);
            DumbFaultInjector.hit("upsert-after-integrity");
            invalidateEndpoints();
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
            DumbFaultInjector.hit("flush-after-index");
            data.flush();
            DumbFaultInjector.hit("flush-after-data");
            integrity.flush();
            DumbFaultInjector.hit("flush-after-integrity");
            recovery.checkpoint();
            DumbFaultInjector.hit("flush-after-checkpoint");
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
                flush();
                data.clear();
                index.clear();
                integrity.clear();
                flush();
                clearCache();
                lastId = 0;
            }
            invalidateEndpoints();
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
            resolveEndpoints();
            return rootId == null ? null : get(rootId.longValue());
        } catch (Exception e) {
            throw endpointFailure("root", e);
        }
    }

    @Override
    public IStep getTop() {
        try {
            resolveEndpoints();
            return topId == null ? null : get(topId.longValue());
        } catch (Exception e) {
            throw endpointFailure("top", e);
        }
    }

    private IllegalStateException endpointFailure(String endpoint, Exception cause) {
        return new IllegalStateException(
                "DUMB storage " + endpoint + " endpoint resolution failed for "
                        + name + " base=" + baseCode,
                cause);
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
            DumbFaultInjector.hit("delete-after-wal");
            boolean removed = false;
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                Index.IndexOne current = index.getOne(id);
                if (current != null) {
                    index.remove(id);
                    DumbFaultInjector.hit("delete-after-index");
                    data.remove(current.getLong());
                    DumbFaultInjector.hit("delete-after-data");
                    integrity.remove(id.longValue());
                    DumbFaultInjector.hit("delete-after-integrity");
                    removed = true;
                }
            }
            if (removed) {
                invalidateEndpoints();
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
            return iterator != null && iterator.hasNext();
        }

        @Override
        public IStep next() {
            Index.IndexOne one = iterator == null ? null : (Index.IndexOne) iterator.next();
            if (one == null) {
                return null;
            }
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
