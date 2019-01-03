package kanger.storage;

import java.io.*;
import java.util.SortedMap;
import java.util.TreeMap;

public class Index implements Closeable {

    private static final int DELETED = 0x01;
    private static final int BLOCK_MARK = 0x10;

    private static final long RECORD_SIZE = 1L + 8L + 8L + 4L;
    private static final int BLOCK_SIZE = 1000;
    private static final short VERSION = 0x0301;

    private int version = VERSION;
    private int blockSize = BLOCK_SIZE;
    private boolean changed = false;
    private SortedMap<Long, IndexOne> baseIndex = new TreeMap<>();
    private SortedMap<Long, IndexOne> currentBlock = new TreeMap<>();
    private File file;

    public void open(String fileName) throws IOException {
        open(new File(fileName));
    }

    public void open(File file) throws IOException {
        this.file = file;
        baseIndex.clear();
        currentBlock.clear();
        RandomAccessFile ras = null;

        try {
            ras = new RandomAccessFile(file, "r");
            long pos = 0;
            ras.seek(pos);
            version = ras.readShort();
            blockSize = ras.readInt();
            do {
                int flags = ras.readByte();
                ras.seek(ras.getFilePointer() - 1);
                if ((flags & DELETED) == 0 && (flags & BLOCK_MARK) != 0) {
                    IndexOne one = new IndexOne().readFrom(ras);
                    baseIndex.put(one.getId(), one);
                    ras.seek(ras.getFilePointer() + (RECORD_SIZE * blockSize));
                } else if (ras.length() >= ras.getFilePointer() + RECORD_SIZE) {
                    ras.seek(ras.getFilePointer() + RECORD_SIZE);
                } else {
                    break;
                }
            } while (ras.length() >= ras.getFilePointer() + RECORD_SIZE);
        } catch (FileNotFoundException ex) {
            ras = new RandomAccessFile(file, "rw");
            IndexOne one = new IndexOne();
            one.setFlags((byte) BLOCK_MARK);
            one.setId(0);
            one.setOffset(0);
            one.setSize(0);
            baseIndex.put(one.getId(), one);
            ras.seek(0);
            ras.writeShort(version);
            ras.writeInt(blockSize);
            one.writeTo(ras);
        } finally {
            if (ras != null) {
                ras.close();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }
    }

    public long get(long id) throws IOException {
        IndexOne io = getOne(id);
        if (io != null) {
            return io.getOffset();
        } else {
            return -1L;
        }
    }

    private IndexOne getOne(long id) throws IOException {
        if (currentBlock.containsKey(id)) {
            IndexOne io = currentBlock.get(id);
            if ((io.getFlags() & DELETED) != 0) {
                return null;
            } else {
                return io;
            }
        } else {
            long top = baseIndex.headMap(id).lastKey();
            IndexOne head = baseIndex.get(top);
            if (head.getId() != currentBlock.firstKey()) {
                loadBlock(head);
                IndexOne io = currentBlock.get(id);
                if ((io.getFlags() & DELETED) != 0) {
                    return null;
                } else {
                    return io;
                }
            } else {
                return null;
            }
        }
    }

    private void saveCurrentBlock() throws IOException {
        IndexOne head = baseIndex.get(currentBlock.firstKey());
        if (head != null) {
            IndexOne empty = new IndexOne();
            empty.setFlags((byte) DELETED);
            try (RandomAccessFile ras = new RandomAccessFile(file, "wr")) {
                ras.seek(head.getOffset());
                if (head.getSize() > blockSize) {
                    SortedMap<Long, IndexOne> blockOne = new TreeMap<>();
                    SortedMap<Long, IndexOne> blockTwo = new TreeMap<>();
                    int current = 0;
                    for (IndexOne io : currentBlock.values()) {
                        if (current <= head.getSize() / 2) {
                            blockOne.put(io.getId(), io);
                        } else {
                            blockTwo.put(io.getId(), io);
                        }
                    }

                    head.setSize(blockOne.size());
                    head.setId(blockOne.firstKey());
                    head.writeTo(ras);
                    for (IndexOne io : blockOne.values()) {
                        io.writeTo(ras);
                    }
                    for (int i = (int) head.getSize(); i < blockSize; ++i) {
                        empty.writeTo(ras);
                    }

                    ras.seek(ras.length());
                    IndexOne tail = new IndexOne();
                    tail.setFlags((byte) BLOCK_MARK);
                    tail.setId(blockTwo.firstKey());
                    tail.setOffset(ras.getFilePointer());
                    tail.setSize(blockTwo.size());
                    baseIndex.put(tail.getId(), tail);

                    tail.writeTo(ras);
                    for (IndexOne io : blockTwo.values()) {
                        io.writeTo(ras);
                    }
                    for (int i = (int) tail.getSize(); i < blockSize; ++i) {
                        empty.writeTo(ras);
                    }
                } else {
                    head.writeTo(ras);
                    for (IndexOne io : currentBlock.values()) {
                        io.writeTo(ras);
                    }
                    for (int i = (int) head.getSize(); i < blockSize; ++i) {
                        empty.writeTo(ras);
                    }
                }
            }
        }
    }

    private void loadBlock(IndexOne head) throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }
        try (RandomAccessFile ras = new RandomAccessFile(file, "r")) {
            currentBlock.clear();
            ras.seek(head.getOffset());
            for (int i = 0; i < head.getSize(); ++i) {
                int flags = ras.readByte();
                ras.seek(ras.getFilePointer() - 1);
                if ((flags & DELETED) == 0 && (flags & BLOCK_MARK) == 0) {
                    IndexOne one = new IndexOne().readFrom(ras);
                    currentBlock.put(one.getId(), one);
                } else {
                    ras.seek(ras.getFilePointer() + RECORD_SIZE);
                }
            }
        }
    }

    public void set(long id, long offset) throws IOException {
        IndexOne io = getOne(id);
        if (io == null) {
            io = new IndexOne();

        } else if (io.getOffset() != offset) {

        }
    }


    public class IndexOne {
        private byte flags;
        private long id;
        private long offset;
        private long size;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public byte getFlags() {
            return flags;
        }

        public void setFlags(byte flags) {
            this.flags = flags;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getOffset() {
            return offset;
        }

        public void setOffset(long offset) {
            this.offset = offset;
        }

        public void writeTo(RandomAccessFile out) throws IOException {
            out.writeByte(flags);
            out.writeLong(id);
            out.writeLong(offset);
            out.writeLong(size);
        }

        public IndexOne readFrom(RandomAccessFile in) throws IOException {
            flags = in.readByte();
            id = in.readLong();
            offset = in.readLong();
            size = in.readLong();
            return this;
        }
    }

}
