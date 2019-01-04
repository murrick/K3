package kanger.storage;

import java.io.*;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

public class Index implements Closeable, Iterable<Index.IndexOne> {

    private static final int DELETED = 0x01;
    private static final int BLOCK_MARK = 0x10;

    private static final long RECORD_SIZE = 1L + 8L + 8L + 8L;
    private static final long SIZE_OFFSET = 2L + 4L;
    private static final int BLOCK_SIZE = 1000;
    private static final short VERSION = 0x0301;

    private int version = VERSION;
    private int blockSize = BLOCK_SIZE;
    private long size = 0;
    private boolean changed = false;
    private NavigableMap<Long, IndexOne> baseIndex = new TreeMap<>();
    private NavigableMap<Long, IndexOne> currentBlock = new TreeMap<>();
    private File file = null;
    private RandomAccessFile ras = null;
    private long currentId = 0;
    private long blockId = 0;

    private int readCounter = 0;
    private int writeCounter = 0;

    public void open(String fileName) throws IOException {
        open(new File(fileName));
    }

    public void open(File file) throws IOException {
        this.file = file;
        this.ras = null;
        this.baseIndex.clear();
        this.currentBlock.clear();

        try {
            ras = new RandomAccessFile(file, "r");
            long pos = 0;
            ras.seek(pos);
            version = ras.readShort();
            blockSize = ras.readInt();
            size = ras.readLong();
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
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(0);
                ras.writeShort(version);
                ras.writeInt(blockSize);
                ras.writeLong(size);
                changed = true;
                IndexOne one = new IndexOne();
                one.setFlags((byte) BLOCK_MARK);
                one.setId(0);
                one.setOffset(ras.getFilePointer());
                one.setSize(0);
                baseIndex.put(one.getId(), one);
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
        this.baseIndex.clear();
        this.currentBlock.clear();
    }

    public void flush() throws IOException {
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

    private long getNext(long id) throws IOException {
        IndexOne head = getHead(id);
        loadBlock(head);
        Long next = currentBlock.higherKey(id);
        if (next != null) {
            return next;
        } else if (baseIndex.higherKey(head.getId()) != null) {
            head = baseIndex.higherEntry(head.getId()).getValue();
            loadBlock(head);
            return currentBlock.firstKey();
        } else {
            return -1;
        }
    }

    private IndexOne getHead(long id) {
        IndexOne head;
        if (baseIndex.size() == 1) {
            head = baseIndex.get(baseIndex.firstKey());
        } else if (baseIndex.containsKey(id)) {
            head = baseIndex.get(id);
        } else if (!baseIndex.headMap(id).isEmpty()) {
            head = baseIndex.get(baseIndex.headMap(id).lastKey());
        } else {
            head = baseIndex.get(baseIndex.tailMap(id).firstKey());
        }
        return head;
    }

    public IndexOne getOne(long id) throws IOException {
        if (currentBlock.containsKey(id)) {
            IndexOne io = currentBlock.get(id);
            if (io == null || (io.getFlags() & DELETED) != 0) {
                return null;
            } else {
                return io;
            }
        } else {
            IndexOne head = getHead(id);
            loadBlock(head);
            IndexOne io = currentBlock.get(id);
            if (io == null || (io.getFlags() & DELETED) != 0) {
                return null;
            } else {
                return io;
            }
        }
    }

    private void saveCurrentBlock() throws IOException {
        IndexOne head = currentBlock.isEmpty() ? null : baseIndex.get(currentBlock.firstKey());
        if (head != null) {
            IndexOne empty = new IndexOne();
            empty.setFlags((byte) DELETED);
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(head.getOffset());
                if (head.getSize() > blockSize) {
                    SortedMap<Long, IndexOne> blockOne = new TreeMap<>();
                    SortedMap<Long, IndexOne> blockTwo = new TreeMap<>();
                    int current = 0;
                    for (IndexOne io : currentBlock.values()) {
                        if (current++ < head.getSize() / 2) {
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
                    currentBlock.clear();
                    currentBlock.putAll(blockOne);

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
                ras.seek(SIZE_OFFSET);
                ras.writeLong(size);
                ++writeCounter;
            }
        }
    }

    private void loadBlock(IndexOne head) throws IOException {
        if (currentBlock.isEmpty() || head.getId() != currentBlock.firstKey()) {
            if (changed) {
                saveCurrentBlock();
                changed = false;
            }
            currentBlock.clear();
            ras.seek(head.getOffset() + RECORD_SIZE);
            for (int i = 0; i < blockSize && head.getSize() > currentBlock.size(); ++i) {
                int flags = ras.readByte();
                ras.seek(ras.getFilePointer() - 1);
                if ((flags & DELETED) == 0 && (flags & BLOCK_MARK) == 0) {
                    IndexOne one = new IndexOne().readFrom(ras);
                    currentBlock.put(one.getId(), one);
                } else {
                    ras.seek(ras.getFilePointer() + RECORD_SIZE);
                    --i;
                }
            }
            if (head.getSize() != currentBlock.size()) {
                head.setSize(currentBlock.size());
            }
            ++readCounter;
        }
    }

    public void set(long id, long offset, long size) throws IOException {
        IndexOne io = getOne(id);
        if (io == null) {
            IndexOne top = getHead(id);
            loadBlock(top);
            io = new IndexOne();
            io.setId(id);
            io.setOffset(offset);
            io.setSize(size);
            currentBlock.put(io.getId(), io);
            baseIndex.remove(top.getId());
            top.setId(currentBlock.firstKey());
            top.setSize(currentBlock.size());
            baseIndex.put(top.getId(), top);
            ++this.size;
            changed = true;
            if (currentBlock.size() > blockSize) {
                saveCurrentBlock();
                changed = false;
            }
        } else if (io.getOffset() != offset || io.getSize() != size) {
            io.setOffset(offset);
            io.setSize(size);
            changed = true;
        }
    }

    public void remove(long id) throws IOException {
        IndexOne head = getHead(id);
        loadBlock(head);
        if (currentBlock.containsKey(id)) {
            currentBlock.remove(id);
            head.setSize(head.getSize() - 1);
            changed = true;
        }
    }

    public boolean isClosed() {
        return ras == null;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        if (isClosed()) {
            this.blockSize = blockSize;
        }
    }

    public int getVersion() {
        return version;
    }

    public boolean isChanged() {
        return changed;
    }

    public File getFile() {
        return file;
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

    @Override
    public Iterator<IndexOne> iterator() {
        try {
            flush();
            currentId = -1;
            blockId = baseIndex.firstKey();
            loadBlock(baseIndex.get(blockId));
        } catch (IOException e) {
            return null;
        }

        return new Iterator<IndexOne>() {
            @Override
            public boolean hasNext() {
                try {
                    return getNext(currentId) != -1;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            public IndexOne next() {
                try {
                    currentId = getNext(currentId);
                    if (currentId != -1) {
                        return getOne(currentId);
                    } else {
                        return null;
                    }
                } catch (IOException e) {
                    return null;
                }
            }
        };
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

        public String toString() {
            return id + "=" + offset;
        }
    }

}
