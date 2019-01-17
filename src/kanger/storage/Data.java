package kanger.storage;

import kanger.interfaces.Identifiable;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.*;

public class Data implements Closeable, Iterable<Identifiable> {
    private static final short VERSION = 0x0301;
    private static final int CACHE_SIZE = 512;

    private int version = VERSION;
    private int headerSize = 0;
    private boolean changed = false;
    private File file = null;
    private RandomAccessFile ras = null;

    private long currentOffset = -1;
    private long blockSize = 0;
    private long dataSize = 0;
    private Identifiable data = null;
    private byte[] buffer = null;
    private int cacheSise = CACHE_SIZE;

    private int readCounter = 0;
    private int writeCounter = 0;

    private List<DataOne> cache = new ArrayList<>();
    private Map<Long, DataOne> cacheIndex = new HashMap<>();

    public void open(String fileName) throws IOException {
        open(new File(fileName));
    }

    public void open(File file) throws IOException {
        this.file = file;
        try {
            ras = new RandomAccessFile(file, "r");
            ras.seek(0);
            version = ras.readShort();
            headerSize = ras.readInt();
            changed = false;
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

    public void clear() throws IOException {
        try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
            ras.seek(0);
            ras.setLength(0);
            ras.writeShort(version);
            headerSize = (int) (ras.getFilePointer() + Integer.BYTES);
            ras.writeInt(headerSize);
            changed = false;
        }
    }

    public void flush() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;

            if (cacheIndex.containsKey(currentOffset)) {
                DataOne one = cacheIndex.get(currentOffset);
                cache.remove(one);
                cacheIndex.remove(currentOffset);
            }
            addToCache();
        }
    }

    public Identifiable get(long offset) throws IOException, ClassNotFoundException {
        if (offset != currentOffset) {
            if (changed) {
                saveCurrentBlock();
                changed = false;
            }

            if (cacheIndex.containsKey(offset)) {

                DataOne one = cacheIndex.get(offset);
                blockSize = one.getBlockSize();
                dataSize = one.getDataSize();
                data = one.getData();
                buffer = one.getBuffer();
                currentOffset = offset;

                cache.remove(one);
                cache.add(one);

            } else if (offset < ras.length()) {

                ras.seek(offset);
                blockSize = ras.readLong();
                dataSize = ras.readLong();
                if (dataSize == 0) {
                    data = null;
                } else {
                    buffer = new byte[(int) dataSize];
                    ras.read(buffer);
                    ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
                    ObjectInputStream ois = new ObjectInputStream(bis);
                    data = (Identifiable) ois.readObject();
                    ++readCounter;
                }
                currentOffset = offset;
                addToCache();

            } else {
                data = null;
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

        cache.add(one);
        cacheIndex.put(currentOffset, one);

        while (cache.size() > cacheSise) {
            one = cache.get(0);
            cache.remove(0);
            cacheIndex.remove(one.getOffset());
        }
    }


    public long add(Identifiable o) throws IOException {
        return set(-1, o);
    }

    public long set(long offset, Identifiable o) throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(o);
        byte[] tmp = bos.toByteArray();

        if (offset != currentOffset || buffer == null || !Arrays.equals(tmp, buffer)) {
            currentOffset = offset;
            data = o;
            buffer = tmp;
            dataSize = buffer.length;
            changed = true;

            flush();
        } else {
            DataOne one = cacheIndex.get(currentOffset);
            cache.remove(one);
            cache.add(one);
        }
        return currentOffset;
    }

    public void remove(long offset) throws IOException {
        try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
            ras.seek(offset + 8);
            if (ras.getFilePointer() == offset + 8) {
                long size = ras.readLong();
                if (size != 0) {
                    ras.seek(offset + 8);
                    ras.writeLong(0L);
                }
                if (cacheIndex.containsKey(offset)) {
                    DataOne one = cacheIndex.get(offset);
                    cache.remove(one);
                    cacheIndex.remove(offset);
                }
            }
        }
    }

    private void saveCurrentBlock() throws IOException {
        if (buffer != null && data != null) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
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

    public int getVersion() {
        return version;
    }

    public boolean isChanged() {
        return changed;
    }

    public long getDataSize() {
        return dataSize;
    }

    public Identifiable getData() {
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


    public void setCacheSise(int cacheSise) {
        this.cacheSise = cacheSise;
    }

    public int getCacheSise() {
        return cacheSise;
    }


    @Override
    public Iterator<Identifiable> iterator() {
        try {
            flush();
            currentOffset = -1;
        } catch (IOException e) {
            return null;
        }

        return new Iterator<Identifiable>() {

            @Override
            public void remove() {
                // TODO: Implement this method
            }

            @Override
            public boolean hasNext() {
                try {
                    if (currentOffset == -1) {
                        if (ras.length() >= headerSize + Long.BYTES * 2) {
                            ras.seek(headerSize);
                            long blockSize = ras.readLong();
                            return ras.length() >= headerSize + Long.BYTES * 2 + blockSize;
                        } else {
                            return false;
                        }
                    } else {
                        return ras.length() >= currentOffset + Long.BYTES * 4 + blockSize;
                    }
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public Identifiable next() {
                try {
                    if (currentOffset == -1) {
                        return get(headerSize);
                    } else {
                        return get(currentOffset + blockSize + Long.BYTES * 2);
                    }
                } catch (ClassNotFoundException | IOException e) {
                    return null;
                }
            }
        };
    }

    public class DataOne {
        private long offset = -1;
        private long blockSize = 0;
        private long dataSize = 0;
        private Identifiable data = null;
        private byte[] buffer = null;


        public void setOffset(long offset) {
            this.offset = offset;
        }

        public long getOffset() {
            return offset;
        }

        public void setBlockSize(long blockSize) {
            this.blockSize = blockSize;
        }

        public long getBlockSize() {
            return blockSize;
        }

        public void setDataSize(long dataSize) {
            this.dataSize = dataSize;
        }

        public long getDataSize() {
            return dataSize;
        }

        public void setData(Identifiable data) {
            this.data = data;
        }

        public Identifiable getData() {
            return data;
        }

        public void setBuffer(byte[] buffer) {
            this.buffer = buffer;
        }

        public byte[] getBuffer() {
            return buffer;
        }
    }

}
