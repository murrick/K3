package org.kanger.storage.dumb2;

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.util.Collection;

/**
 * Runtime-facing base for one immutable Context snapshot.
 *
 * <p>Persistent reads are delegated to the pinned snapshot. ID allocation is
 * deliberately runtime-local: query compilation may need temporary unit IDs,
 * but allocating them must not mutate the published Context revision.</p>
 */
final class SnapshotRuntimeBase implements IBase {

    private final IBase source;
    private final String name;
    private long nextId;
    private boolean closed;

    SnapshotRuntimeBase(IBase source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.source = source;
        this.name = source.getName();
        this.nextId = source.lastId();
    }

    @Override
    public void add(IStep one) {
        rejectMutation();
    }

    @Override
    public void update(IStep one) {
        rejectMutation();
    }

    @Override
    public IStep get(long id) throws Exception {
        requireOpen();
        return source.get(id);
    }

    @Override
    public void clearCache() {
        requireOpen();
        source.clearCache();
    }

    @Override
    public boolean isEmpty() {
        requireOpen();
        return source.isEmpty();
    }

    @Override
    public void delete(long id) {
        rejectMutation();
    }

    @Override
    public void deleteAll(Collection<Long> ids) {
        rejectMutation();
    }

    @Override
    public void clear() {
        rejectMutation();
    }

    @Override
    public void reindex(IBase to, IMind mind) {
        rejectMutation();
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        requireOpen();
        return source.containsKey(id);
    }

    @Override
    public IStep getRoot() {
        requireOpen();
        return source.getRoot();
    }

    @Override
    public IStep getTop() {
        requireOpen();
        return source.getTop();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getUsedCacheSize() {
        requireOpen();
        return source.getUsedCacheSize();
    }

    @Override
    public long getMaxCacheSize() {
        requireOpen();
        return source.getMaxCacheSize();
    }

    @Override
    public synchronized long lastId() {
        requireOpen();
        return nextId;
    }

    @Override
    public synchronized long nextId() {
        requireOpen();
        return nextId++;
    }

    @Override
    public void flush() {
        requireOpen();
        // Runtime-local allocation and query overlays have no physical flush.
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    @Override
    public Class getUdf() {
        requireOpen();
        return source.getUdf();
    }

    private void rejectMutation() {
        requireOpen();
        throw new UnsupportedOperationException(
                "DUMB2 Context snapshot runtime is read-only: " + name);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "DUMB2 Context snapshot runtime base is closed: " + name);
        }
    }
}
