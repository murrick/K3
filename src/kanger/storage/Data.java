package kanger.storage;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;

public class Data implements Closeable, Iterable<Externalizable> {
    private static final int DELETED = 0x01;
    private static final short VERSION = 0x0301;
    private static final long HEADER_SIZE = 2L + 8L + 8L;
    private static final long SIZE_OFFSET = 2L + 8L;

    private int version = VERSION;
    private long headerSize = HEADER_SIZE;
    private boolean changed = false;
    private File file = null;
    private RandomAccessFile ras = null;
    private long size = 0;
    private long currentOffset = -1;
    private long blockSize = 0;
    private long dataSize = 0;
    private Externalizable data = null;
    private byte[] buffer = null;

    private int readCounter = 0;
    private int writeCounter = 0;

    public void open(String fileName) throws IOException {
        open(new File(fileName));
    }

    public void open(File file) throws IOException {
        this.file = file;
        try {
            ras = new RandomAccessFile(file, "r");
            ras.seek(0);
            version = ras.readShort();
            headerSize = ras.readLong();
            size = ras.readLong();
            changed = false;
        } catch (FileNotFoundException ex) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(0);
                ras.writeShort(version);
                ras.writeLong(headerSize);
                ras.writeLong(size);
                changed = true;
            }
            ras = new RandomAccessFile(file, "r");
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        this.file = null;
        this.ras = null;
        this.size = 0;
    }

    public void flush() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }
    }

    public Externalizable get(long offset) throws IOException, ClassNotFoundException {
        if (offset != currentOffset) {
            if (changed) {
                saveCurrentBlock();
                changed = false;
            }

            if (offset < ras.length()) {
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
                    data = (Externalizable) ois.readObject();
                    ++readCounter;
                }
                currentOffset = offset;
            } else {
                data = null;
            }
        }
        return data;
    }

    public void set(long offset, Externalizable o) throws IOException {
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
        }
    }

    public void remove(long offset) throws IOException {
        try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
            ras.seek(offset + 8);
            if (ras.getFilePointer() == offset + 8) {
                long size = ras.readLong();
                if (size != 0) {
                    ras.seek(offset + 8);
                    ras.writeLong(0L);
                    --size;
                    ras.seek(SIZE_OFFSET);
                    ras.writeLong(size);
                }
            }
        }
    }

    private void saveCurrentBlock() throws IOException {
        if (buffer != null && data != null) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                long oldSize = size;
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
                        --size;
                    }
                }
                if (currentOffset == -1) {
                    ras.seek(ras.length());
                    currentOffset = ras.getFilePointer();
                    blockSize = dataSize;
                    ras.writeLong(blockSize);
                    ras.writeLong(dataSize);
                    ras.write(buffer);
                    ++size;
                }
                ++writeCounter;
                if (oldSize != size) {
                    ras.seek(SIZE_OFFSET);
                    ras.writeLong(size);
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

    public long getSize() {
        return size;
    }

    public long getDataSize() {
        return dataSize;
    }

    public Externalizable getData() {
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


    @Override
    public Iterator<Externalizable> iterator() {
        try {
            flush();
            currentOffset = -1;
        } catch (IOException e) {
            return null;
        }

        return new Iterator<Externalizable>() {
            @Override
            public boolean hasNext() {
                try {
                    if (currentOffset == -1) {
                        if (ras.length() >= headerSize + 8 + 8) {
                            ras.seek(headerSize);
                            long blockSize = ras.readLong();
                            return ras.length() >= headerSize + 8 + 8 + blockSize;
                        } else {
                            return false;
                        }
                    } else {
                        return ras.length() >= currentOffset + 8 + 8 + 8 + 8 + blockSize;
                    }
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public Externalizable next() {
                try {
                    if (currentOffset == -1) {
                        return get(headerSize);
                    } else {
                        return get(currentOffset + blockSize + 8 + 8);
                    }
                } catch (ClassNotFoundException | IOException e) {
                    return null;
                }
            }
        };
    }
}