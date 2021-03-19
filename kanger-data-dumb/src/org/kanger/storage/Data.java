/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.storage;

import org.kanger.exception.DatabaseErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.*;
import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Data implements Closeable, Iterable<IStep> {

    private static long MAX_CACHE_SIZE = 1024L * 1024;
    private static final int VERSION_CODE = 0x0102;

    private int version = VERSION_CODE;

    private final Map<Long, DataOne> cache = new HashMap<>();
    private final Object locker = new Object();

    //    private IStep data = null;
//    private boolean changed = false;
//    private byte[] buffer = null;
    private File file = null;
    private int headerSize = 0;
    //    private long blockSize = 0;
//    private long dataSize = 0;
    private RandomAccessFile ras = null;
    private DataOne currentOne = null;
    private final Queue<Long> timing = new LinkedList<>();
    private long maxCacheSize = MAX_CACHE_SIZE;
//    private long currentOne = -1;

    private boolean readonly = false;
    private int readCounter = 0;
    private int writeCounter = 0;
    private volatile long cacheSize = 0L;

    private IBase base = null;


    public Data(IBase base, IUser user) throws Exception {
        this.base = base;
        MAX_CACHE_SIZE = Long.parseLong(user.getProperty("cache.data.size", (1024L * 1024) + ""));
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
//            changed = false;

            cache.clear();
            timing.clear();
            cacheSize = 0L;
        } catch (FileNotFoundException ex) {
            clear();
            ras = new RandomAccessFile(file, "r");
        }
    }

    @Override
    public void close() throws IOException {
        flush();
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
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(0);
                ras.setLength(0);
                ras.writeShort(version);
                headerSize = (int) (ras.getFilePointer() + Integer.BYTES);
                ras.writeInt(headerSize);
//                changed = false;
            }
        }
        synchronized (cache) {
            cache.clear();
            timing.clear();
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
//        if (changed) {
//            saveCurrentBlock();
//            changed = false;
//
//            synchronized (cache) {
//                if (cache.containsKey(currentOffset)) {
//                    timing.remove(currentOffset);
//                    DataOne o = cache.remove(currentOffset);
//                    cacheSize -= o.blockSize;
//                }
//            }
//            addToCache();
//        }
    }

    private DataOne getOne(long offset) throws Exception {
        DataOne one = null;
        synchronized (cache) {
            if (cache.containsKey(offset)) {
                one = cache.get(offset);

                timing.remove(offset);
                timing.add(offset);
            }
        }

        if (offset > 0 && one == null) {
            long blockSize = -1;
            long dataSize = -1;
            byte[] buffer = null;
            synchronized (locker) {
                if (offset < ras.length()) {

                    ras.seek(offset);
                    blockSize = ras.readLong();
                    dataSize = ras.readLong();
                    if (dataSize > 0) {
                        buffer = new byte[(int) dataSize];
                        ras.read(buffer);
                        ByteBuffer packet = new ByteBuffer(buffer);
                        try {
                            packet.mark();
                            IStep data = new Sapato(base);
                            data.apply(packet);
                            one = new DataOne();
                            one.setBlockSize(blockSize);
                            one.setDataSize(dataSize);
                            one.setData(data);
                            one.setBuffer(buffer);
                            one.setOffset(offset);
                        } finally {
                            packet.release();
                        }
                        ++readCounter;
                    }
                }
            }

            if (one != null) {
                addToCache(one);
            }
        }
        return one;
    }

    private void addToCache(DataOne one) throws IOException {
        synchronized (cache) {
            if (!cache.containsKey(one.getOffset())) {
                cache.put(one.getOffset(), one);
                timing.add(one.getOffset());
                cacheSize += one.getBlockSize();
            } else {
                cacheSize -= cache.get(one.getOffset()).getBlockSize();
                cache.put(one.getOffset(), one);
                timing.remove(one.getOffset());
                timing.add(one.getOffset());
                cacheSize += one.getBlockSize();
            }

            while (cacheSize > maxCacheSize) {
                long topOffset = timing.poll();
                one = cache.remove(topOffset);
                cacheSize -= one.getBlockSize();
                if (one.isChanged()) {
                    one.saveBlock();
                }
            }
        }
    }

    public IStep get(long offset) throws Exception {

        DataOne one = getOne(offset);
        if (one != null) {
            return one.getData();
        } else {
            return null;
        }
    }

    public long add(IStep o) throws Exception {
        return set(-1, o);
    }

    public long set(long offset, IStep o) throws Exception {
        DataOne one = cache.get(offset);
        if (one == null) {
            one = new DataOne();
        }

        byte[] tmp = o.pack().getBuffer();

        if (!Arrays.equals(tmp, one.getBuffer())) {
            one.setOffset(offset);
            one.setData(o);
            one.setBuffer(tmp);
            one.setDataSize(tmp.length);
            one.setChanged(true);

            if (offset == -1) {
                one.saveBlock();
            }

            addToCache(one);

            //flush();
        } else {
            synchronized (cache) {
                if (cache.containsKey(offset)) {
                    timing.remove(offset);
                    timing.add(offset);
                }
            }
        }
        return one.getOffset();
    }

    public void remove(long offset) throws IOException {
        if (readonly) {
            throw new RuntimeException("Database is readonly");
        }
        synchronized (cache) {
            if (cache.containsKey(offset)) {
                timing.remove(offset);
                DataOne one = cache.remove(offset);
                cacheSize -= one.blockSize;
            }
        }

        synchronized (locker) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(offset + Long.BYTES);
                if (ras.getFilePointer() == offset + Long.BYTES) {
                    long size = ras.readLong();
                    if (size != 0) {
                        ras.seek(offset + Long.BYTES);
                        ras.writeLong(0L);
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

//    public long getCurrentOffset() {
//        return currentOffset;
//    }

    public boolean isClosed() {
        return ras == null;
    }

    public File getFile() {
        return file;
    }

    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
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
                // TODO: Implement this method
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
                            } else {
                                return false;
                            }
                        }
                    } else {
                        return ras.length() >= currentOne.getOffset() + Long.BYTES * 4 + currentOne.getBlockSize();
                    }
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public IStep next() {
                try {
                    if (currentOne == null) {
                        currentOne = getOne(headerSize);
                        return currentOne.getData();
                    } else {
                        currentOne = getOne(currentOne.getOffset() + currentOne.getBlockSize() + Long.BYTES * 2);
                        return currentOne.getData();
                    }
                } catch (Exception e) {
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
            if (buffer != null && data != null) {
                synchronized (locker) {
                    try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                        if (offset != -1) {
                            ras.seek(offset);
                            blockSize = ras.readLong();
                            if (blockSize >= dataSize) {
                                ras.writeLong(dataSize);
                                ras.write(buffer);
                            } else if (offset + blockSize >= ras.length()) {
                                blockSize = dataSize;
                                ras.seek(offset);
                                ras.writeLong(blockSize);
                                ras.writeLong(dataSize);
                                ras.write(buffer);
                            } else {
                                ras.writeLong(0L);
                                offset = -1;
                            }
                        }
                        if (offset == -1) {
                            ras.seek(ras.length());
                            offset = ras.getFilePointer();
                            blockSize = dataSize;
                            ras.writeLong(blockSize);
                            ras.writeLong(dataSize);
                            ras.write(buffer);
                        }
                        ++writeCounter;
                    }
                }
            }
        }
    }

}
