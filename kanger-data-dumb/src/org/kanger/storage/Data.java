package org.kanger.storage;

import org.kanger.Version;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;

import java.io.*;
import java.util.*;

public class Data implements Closeable, Iterable<IStep> {
    private static final long MAX_CACHE_SIZE = 1024L * 1024L;

    private final Object locker = new Object();
    private int version = Version.VERSION_CODE;
    private int headerSize = 0;
    private boolean changed = false;
    private File file = null;
    private RandomAccessFile ras = null;
    private long currentOffset = -1;
    private long blockSize = 0;
    private long dataSize = 0;
    private IStep data = null;
    private byte[] buffer = null;
    private final Queue<Long> timing = new LinkedList<>();
    private int readCounter = 0;
    private int writeCounter = 0;
    private final Map<Long, DataOne> cache = new HashMap<>();
    private long maxCacheSize = MAX_CACHE_SIZE;
    private volatile long cacheSize = 0L;

    private IBase base = null;


    public Data(IBase base) {
        this.base = base;
    }

    public void open(String fileName) throws IOException {
        open(new File(fileName));
    }

    public void open(File file) throws IOException {
        this.file = file;
        synchronized (locker) {
            try {
                ras = new RandomAccessFile(file.getAbsoluteFile(), "r");
                ras.seek(0);
                version = ras.readShort();
                headerSize = ras.readInt();
                changed = false;
            } catch (FileNotFoundException ex) {
                clear();
                ras = new RandomAccessFile(file.getAbsoluteFile(), "r");
            }
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        synchronized (locker) {
            ras.close();
        }
        ras = null;
    }

    public void clear() throws IOException {
        String path = file.getAbsolutePath();
        path = path.substring(0, path.length() - file.getName().length());
        new File(path).mkdirs();
        synchronized (locker) {
            try (RandomAccessFile ras = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {
                ras.seek(0);
                ras.setLength(0);
                ras.writeShort(version);
                headerSize = (int) (ras.getFilePointer() + Integer.BYTES);
                ras.writeInt(headerSize);
                changed = false;
            }
        }
    }

    public void flush() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;

            synchronized (cache) {
                if (cache.containsKey(currentOffset)) {
                    timing.remove(currentOffset);
                    DataOne o = cache.remove(currentOffset);
                    cacheSize -= o.blockSize;
                }
            }
            addToCache();
        }
    }

    public IStep get(long offset) throws Exception {
        if (offset != currentOffset) {
            if (changed) {
                saveCurrentBlock();
                changed = false;
            }

            data = null;
            synchronized (cache) {
                if (cache.containsKey(offset)) {
                    DataOne one = cache.get(offset);
                    blockSize = one.getBlockSize();
                    dataSize = one.getDataSize();
                    data = one.getData();
                    buffer = one.getBuffer();
                    currentOffset = offset;

                    timing.remove(offset);
                    timing.add(offset);

                }
            }

            if (data == null) {
                synchronized (locker) {

                    if (offset < ras.length()) {

                        ras.seek(offset);
                        blockSize = ras.readLong();
                        dataSize = ras.readLong();
                        if (dataSize == 0) {
                            data = null;
                        } else {
                            buffer = new byte[(int) dataSize];
                            ras.read(buffer);
                            ByteBuffer packet = new ByteBuffer(buffer);
                            try {
                                packet.mark();
                                data = new Sapato(base);
//                        data.setBase(this);
                                data.apply(packet);
                            } finally {
                                packet.release();
                            }

//                    ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
//                    ObjectInputStream ois = new ObjectInputStream(bis);
//                    data = (IStep) ois.readObject();
                            ++readCounter;
                        }
                        currentOffset = offset;
                        addToCache();

                    }
                }
            }
        }

        return data;
    }

    private void addToCache() {
        DataOne one = new DataOne();
        one.setBlockSize(blockSize);
        one.setDataSize(dataSize);
        one.setData(data);
        one.setBuffer(buffer);
        one.setOffset(currentOffset);

        synchronized (cache) {
            if (!cache.containsKey(currentOffset)) {
                cache.put(currentOffset, one);
                timing.add(currentOffset);
                cacheSize += one.blockSize;
                while (cacheSize > maxCacheSize) {
                    long offset = timing.poll();
                    one = cache.remove(offset);
                    cacheSize -= one.blockSize;
                }
            }
        }
    }


    public long add(IStep o) throws IOException {
        return set(-1, o);
    }

    public long set(long offset, IStep o) throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }

//        ByteArrayOutputStream bos = new ByteArrayOutputStream();
//        ObjectOutputStream out = new ObjectOutputStream(bos);
//        out.writeObject(o);
        byte[] tmp = o.pack().getBuffer();

        if (offset != currentOffset || buffer == null || !Arrays.equals(tmp, buffer)) {
            currentOffset = offset;
            data = o;
            buffer = tmp;
            dataSize = buffer.length;
            changed = true;
            flush();
        } else {
            synchronized (cache) {
                if (cache.containsKey(currentOffset)) {
                    timing.remove(currentOffset);
                    timing.add(currentOffset);
                }
            }
        }
        return currentOffset;
    }

    public void remove(long offset) throws IOException {
        synchronized (locker) {
            try (RandomAccessFile ras = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {
                ras.seek(offset + 8);
                if (ras.getFilePointer() == offset + 8) {
                    long size = ras.readLong();
                    if (size != 0) {
                        ras.seek(offset + 8);
                        ras.writeLong(0L);
                    }

                    synchronized (cache) {
                        if (cache.containsKey(offset)) {
                            timing.remove(offset);
                            DataOne one = cache.remove(offset);
                            cacheSize -= one.blockSize;
                        }
                    }
                }
            }
        }
    }

    private void saveCurrentBlock() throws IOException {
        if (buffer != null && data != null) {
            synchronized (locker) {
                try (RandomAccessFile ras = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {
                    if (currentOffset != -1) {
                        ras.seek(currentOffset);
                        blockSize = ras.readLong();
                        if (blockSize >= dataSize) {
                            ras.writeLong(dataSize);
                            ras.write(buffer);
                        } else if (currentOffset + blockSize >= ras.length()) {
                            blockSize = dataSize;
                            ras.seek(currentOffset);
                            ras.writeLong(blockSize);
                            ras.writeLong(dataSize);
                            ras.write(buffer);
                        } else {
                            ras.writeLong(0L);
                            currentOffset = -1;
                        }
                    }
                    if (currentOffset == -1) {
                        ras.seek(ras.length());
                        currentOffset = ras.getFilePointer();
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

    public int getVersion() {
        return version;
    }

    public boolean isChanged() {
        return changed;
    }

    public long getDataSize() {
        return dataSize;
    }

    public IStep getData() {
        return data;
    }

    public byte[] getBuffer() {
        return buffer;
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

    public long getCurrentOffset() {
        return currentOffset;
    }

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
            currentOffset = -1;
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
                    if (currentOffset == -1) {
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
                        synchronized (locker) {
                            return ras.length() >= currentOffset + Long.BYTES * 4 + blockSize;
                        }
                    }
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public IStep next() {
                try {
                    if (currentOffset == -1) {
                        return get(headerSize);
                    } else {
                        return get(currentOffset + blockSize + Long.BYTES * 2);
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
    }

}
