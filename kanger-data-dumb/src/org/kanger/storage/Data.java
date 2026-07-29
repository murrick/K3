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

import org.kanger.exception.DatabaseErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialized block store and its bounded hydrated-block cache.
 */
public class Data implements Closeable, Iterable<IStep> {

    private static final long DEFAULT_MAX_CACHE_SIZE = 1024L * 1024L;
    private static final int VERSION_CODE = 0x0103;

    private int version = VERSION_CODE;

    /** Access-ordered LRU. Keys are physical block offsets. */
    private final LinkedHashMap<Long, DataOne> cache =
            new LinkedHashMap<Long, DataOne>(16, 0.75f, true);
    private final Object locker = new Object();

    private File file = null;
    private int headerSize = 0;
    private RandomAccessFile ras = null;
    private DataOne currentOne = null;
    private long maxCacheSize = DEFAULT_MAX_CACHE_SIZE;

    private boolean readonly = false;
    private int readCounter = 0;
    private int writeCounter = 0;
    private long cacheSize = 0L;
    private long cacheHits = 0L;
    private long cacheMisses = 0L;
    private long cacheEvictions = 0L;

    private final IBase base;

    public Data(IBase base, IUser user) throws Exception {
        this.base = base;
        this.maxCacheSize = Math.max(0L, Long.parseLong(
                user.getProperty("cache.data.size", DEFAULT_MAX_CACHE_SIZE + "")));
    }

    public void open(String fileName, boolean readonly) throws Exception {
        open(new File(fileName), readonly);
    }

    public void open(File file, boolean readonly) throws Exception {
        this.readonly = readonly;
        this.file = file;
        try {
            ras = new RandomAccessFile(file, "r");
            ras.seek(0);
            version = ras.readShort();
            if (version != VERSION_CODE) {
                throw new DatabaseErrorException("Incompatible DB version");
            }
            headerSize = ras.readInt();
            clearCacheState();
        } catch (FileNotFoundException ex) {
            clear();
            ras = new RandomAccessFile(file, "r");
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        clearCacheState();
        ras.close();
        ras = null;
    }

    public void clear() throws Exception {
        if (readonly) {
            throw new RuntimeException("Database is readonly");
        }
        String path = file.getPath();
        path = path.substring(0, path.length() - file.getName().length());
        new File(path).mkdirs();
        synchronized (locker) {
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                output.seek(0);
                output.setLength(0);
                output.writeShort(version);
                headerSize = (int) (output.getFilePointer() + Integer.BYTES);
                output.writeInt(headerSize);
            }
        }
        clearCacheState();
    }

    private void clearCacheState() {
        synchronized (cache) {
            cache.clear();
            cacheSize = 0L;
        }
    }

    public void flush() throws IOException {
        synchronized (cache) {
            for (DataOne one : cache.values()) {
                if (one.isChanged()) {
                    one.saveBlock();
                    one.setChanged(false);
                }
            }
        }
    }

    private long cacheWeight(DataOne one) {
        return one == null ? 0L : Math.max(1L, one.getDataSize());
    }

    private DataOne readOne(long offset) throws Exception {
        if (offset <= 0) {
            return null;
        }
        synchronized (locker) {
            if (offset >= ras.length()) {
                return null;
            }

            ras.seek(offset);
            long blockSize = ras.readLong();
            long dataSize = ras.readLong();
            if (dataSize <= 0) {
                return null;
            }

            byte[] buffer = new byte[(int) dataSize];
            ras.read(buffer);
            ByteBuffer packet = new ByteBuffer(buffer);
            try {
                packet.mark();
                IStep step = new Sapato(base);
                step.apply(packet);
                // dataSize is known without reserializing a semantic object.
                step.setSize(Math.max(1L, dataSize));

                DataOne one = new DataOne();
                one.setBlockSize(blockSize);
                one.setDataSize(dataSize);
                one.setData(step);
                one.setBuffer(buffer);
                one.setOffset(offset);
                ++readCounter;
                return one;
            } finally {
                packet.release();
            }
        }
    }

    private DataOne getOne(long offset) throws Exception {
        if (maxCacheSize > 0L) {
            synchronized (cache) {
                DataOne cached = cache.get(offset);
                if (cached != null) {
                    ++cacheHits;
                    return cached;
                }
                ++cacheMisses;
            }
        }

        DataOne loaded = readOne(offset);
        if (loaded != null) {
            addToCache(loaded);
        }
        return loaded;
    }

    private void addToCache(DataOne one) throws IOException {
        if (one == null) {
            return;
        }
        if (maxCacheSize <= 0L) {
            if (one.isChanged()) {
                one.saveBlock();
                one.setChanged(false);
            }
            return;
        }

        synchronized (cache) {
            DataOne previous = cache.remove(one.getOffset());
            if (previous != null) {
                cacheSize -= cacheWeight(previous);
            }

            cache.put(one.getOffset(), one);
            cacheSize += cacheWeight(one);

            Iterator<Map.Entry<Long, DataOne>> iterator = cache.entrySet().iterator();
            while (cacheSize > maxCacheSize && iterator.hasNext()) {
                Map.Entry<Long, DataOne> eldest = iterator.next();
                DataOne evicted = eldest.getValue();
                if (evicted.isChanged()) {
                    evicted.saveBlock();
                    evicted.setChanged(false);
                }
                cacheSize -= cacheWeight(evicted);
                iterator.remove();
                ++cacheEvictions;
            }
            if (cacheSize < 0L) {
                cacheSize = 0L;
            }
        }
    }

    public IStep get(long offset) throws Exception {
        DataOne one = getOne(offset);
        return one == null ? null : one.getData();
    }

    /** Sequential scans bypass the LRU and therefore cannot evict hot blocks. */
    public IStep getUncached(long offset) throws Exception {
        DataOne one = readOne(offset);
        return one == null ? null : one.getData();
    }

    public long add(IStep step) throws Exception {
        return set(-1, step);
    }

    public long set(long offset, IStep step) throws Exception {
        DataOne one = null;
        if (maxCacheSize > 0L) {
            synchronized (cache) {
                one = cache.get(offset);
            }
        }
        if (one == null) {
            one = new DataOne();
        }

        byte[] packed = step.pack().getBuffer();
        step.setSize(Math.max(1L, packed.length));

        if (!Arrays.equals(packed, one.getBuffer())) {
            one.setOffset(offset);
            one.setData(step);
            one.setBuffer(packed);
            one.setDataSize(packed.length);
            one.setChanged(true);

            // Write through so a relocated block offset is returned to Base and
            // its ID index before this record can ever be evicted.
            one.saveBlock();
            one.setChanged(false);
            addToCache(one);
        }
        return one.getOffset();
    }

    public void remove(long offset) throws IOException {
        if (readonly) {
            throw new RuntimeException("Database is readonly");
        }
        synchronized (cache) {
            DataOne removed = cache.remove(offset);
            if (removed != null) {
                cacheSize -= cacheWeight(removed);
                if (cacheSize < 0L) {
                    cacheSize = 0L;
                }
            }
        }

        synchronized (locker) {
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                output.seek(offset + Long.BYTES);
                if (output.getFilePointer() == offset + Long.BYTES) {
                    long size = output.readLong();
                    if (size != 0) {
                        output.seek(offset + Long.BYTES);
                        output.writeLong(0L);
                    }
                }
            }
        }
    }

    public int getVersion() {
        return version;
    }

    public int getReadCounter() {
        return readCounter;
    }

    public int getWriteCounter() {
        return writeCounter;
    }

    public void dropReadCounter() {
        readCounter = 0;
    }

    public void dropWriteCounter() {
        writeCounter = 0;
    }

    public boolean isClosed() {
        return ras == null;
    }

    public File getFile() {
        return file;
    }

    public long getUsedCacheSize() {
        synchronized (cache) {
            return cacheSize;
        }
    }

    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public long getCacheHits() {
        synchronized (cache) {
            return cacheHits;
        }
    }

    public long getCacheMisses() {
        synchronized (cache) {
            return cacheMisses;
        }
    }

    public long getCacheEvictions() {
        synchronized (cache) {
            return cacheEvictions;
        }
    }

    public long getCachedEntryCount() {
        synchronized (cache) {
            return cache.size();
        }
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = Math.max(0L, maxCacheSize);
    }

    @Override
    public Iterator<IStep> iterator() {
        try {
            flush();
            currentOne = null;
        } catch (IOException e) {
            return null;
        }

        return new Iterator<IStep>() {
            @Override
            public void remove() {
            }

            @Override
            public boolean hasNext() {
                try {
                    if (currentOne == null) {
                        synchronized (locker) {
                            if (ras.length() >= headerSize + Long.BYTES * 2) {
                                ras.seek(headerSize);
                                long blockSize = ras.readLong();
                                return ras.length() >= headerSize + Long.BYTES * 2 + blockSize;
                            }
                            return false;
                        }
                    }
                    return ras.length() >= currentOne.getOffset()
                            + Long.BYTES * 4 + currentOne.getBlockSize();
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public IStep next() {
                try {
                    long offset = currentOne == null
                            ? headerSize
                            : currentOne.getOffset() + currentOne.getBlockSize() + Long.BYTES * 2;
                    currentOne = readOne(offset);
                    return currentOne == null ? null : currentOne.getData();
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                    return null;
                }
            }
        };
    }

    public class DataOne {
        private long offset = -1;
        private long blockSize = 0;
        private long dataSize = 0;
        private boolean changed = false;
        private IStep data = null;
        private byte[] buffer = null;

        public long getOffset() {
            return offset;
        }

        public void setOffset(long offset) {
            this.offset = offset;
        }

        public long getBlockSize() {
            return blockSize;
        }

        public void setBlockSize(long blockSize) {
            this.blockSize = blockSize;
        }

        public long getDataSize() {
            return dataSize;
        }

        public void setDataSize(long dataSize) {
            this.dataSize = dataSize;
        }

        public IStep getData() {
            return data;
        }

        public void setData(IStep data) {
            this.data = data;
        }

        public byte[] getBuffer() {
            return buffer;
        }

        public void setBuffer(byte[] buffer) {
            this.buffer = buffer;
        }

        public boolean isChanged() {
            return changed;
        }

        public void setChanged(boolean changed) {
            this.changed = changed;
        }

        public void saveBlock() throws IOException {
            if (readonly) {
                throw new RuntimeException("Database is readonly");
            }
            if (buffer == null || data == null) {
                return;
            }
            synchronized (locker) {
                try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                    if (offset != -1) {
                        output.seek(offset);
                        blockSize = output.readLong();
                        if (blockSize >= dataSize) {
                            output.writeLong(dataSize);
                            output.write(buffer);
                        } else if (offset + blockSize >= output.length()) {
                            blockSize = dataSize;
                            output.seek(offset);
                            output.writeLong(blockSize);
                            output.writeLong(dataSize);
                            output.write(buffer);
                        } else {
                            output.writeLong(0L);
                            offset = -1;
                        }
                    }
                    if (offset == -1) {
                        output.seek(output.length());
                        offset = output.getFilePointer();
                        blockSize = dataSize;
                        output.writeLong(blockSize);
                        output.writeLong(dataSize);
                        output.write(buffer);
                    }
                    ++writeCounter;
                }
            }
        }
    }
}
