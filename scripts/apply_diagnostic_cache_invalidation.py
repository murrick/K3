from pathlib import Path

path = Path("kanger-data-dumb/src/org/kanger/storage/Base.java")
source = path.read_text(encoding="utf-8")

old = '''    @Override
    public void add(IStep one) throws Exception {
        ++writeCount;
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
'''
new = '''    @Override
    public void add(IStep one) throws Exception {
        ++writeCount;
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

        // A write may replace the serialized state of an existing ID. Keeping
        // the old hydrated IStep makes subsequent reads observe stale Rule,
        // TVariable and lifecycle flags.
        if (CACHE_ENABLE) {
            synchronized (cache) {
                IStep stale = cache.remove(one.getId());
                timing.remove(one.getId());
                if (stale != null) {
                    cacheSize -= stale.getSize();
                    if (cacheSize < 0L) {
                        cacheSize = 0L;
                    }
                }
            }
        }
    }
'''

if old not in source:
    if "A write may replace the serialized state" in source:
        raise SystemExit(0)
    raise RuntimeError("Base.add diagnostic invalidation anchor not found")

path.write_text(source.replace(old, new, 1), encoding="utf-8")
