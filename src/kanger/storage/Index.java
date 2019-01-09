package kanger.storage;

import java.io.*;
import java.util.*;

public class Index implements Closeable, Iterable<Index.IndexOne> {

    private static final int DELETED = 0x01;
    private static final int BLOCK_MARK = 0x10;

    private static final int BLOCK_SIZE = 5; //1000;
    private static final short VERSION = 0x0301;

    private int version = VERSION;

    private NavigableMap<Long, IndexOne> baseIndex = new TreeMap<>();
    private NavigableMap<Long, IndexOne> currentBlock = new TreeMap<>();

    private File file = null;
    private RandomAccessFile ras = null;
    private boolean changed = false;
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
            do {
                IndexOne one = new IndexOne().readFrom(ras);
                if (!one.isDeleted() && one.isBlockMark()) {
                    baseIndex.put(one.getId(), one);
                    ras.seek(ras.getFilePointer() + one.getSize());
                } else if (ras.length() > ras.getFilePointer()) {
                    ras.seek(ras.getFilePointer() + one.getSize());
                } else {
                    break;
                }
            } while (ras.length() > ras.getFilePointer());
        } catch (FileNotFoundException ex) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(0);
                ras.writeShort(version);
                changed = true;
                IndexOne one = new IndexOne();
                one.setBlockMark(true);
                one.setId(0);
                one.setSize(0);
                one.getData().add(ras.getFilePointer());
                one.getData().add(0L);
                one.writeTo(ras);
                baseIndex.put(one.getId(), one);
            }
            ras = new RandomAccessFile(file, "r");
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        ras.close();
        file = null;
        ras = null;
        baseIndex.clear();
        currentBlock.clear();
    }

    public void flush() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
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
            if (io == null || io.isDeleted()) {
                return null;
            } else {
                return io;
            }
        } else {
            IndexOne head = getHead(id);
            loadBlock(head);
            IndexOne io = currentBlock.get(id);
            if (io == null || io.isDeleted()) {
                return null;
            } else {
                return io;
            }
        }
    }

    private int getBlockLength(Collection<IndexOne> block) {
        int size = 0;
        for (IndexOne one : block) {
            size += one.getRecordSize();
        }
        return size;
    }

    private void saveCurrentBlock() throws IOException {
        IndexOne head = currentBlock.isEmpty() ? null : baseIndex.get(currentBlock.firstKey());
        if (head != null) {
            IndexOne empty = new IndexOne();
            empty.setDeleted(true);
            empty.getData().add(0L);
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                ras.seek(head.getData().get(0));
                long blockLength = head.getData().get(1);
                if (currentBlock.size() > BLOCK_SIZE || (blockLength > 0 && blockLength < getBlockLength(currentBlock.values()))) {
                    TreeMap<Long, IndexOne> blockOne = new TreeMap<>();
                    TreeMap<Long, IndexOne> blockTwo = new TreeMap<>();
                    int current = 0;
                    for (IndexOne one : currentBlock.values()) {
                        if (current < blockLength / 2) {
                            blockOne.put(one.getId(), one);
                        } else {
                            blockTwo.put(one.getId(), one);
                        }
                        current += one.getRecordSize();
                    }
                    while (getBlockLength(blockOne.values()) > blockLength) {
                        IndexOne one = blockOne.lastEntry().getValue();
                        blockOne.remove(one.getId());
                        blockTwo.put(one.getId(), one);
                    }

                    head.getData().clear();
                    head.getData().add(ras.getFilePointer());
                    head.getData().add(blockLength);
                    head.setSize(blockOne.size());
                    head.setId(blockOne.firstKey());
                    head.writeTo(ras);
                    for (IndexOne io : blockOne.values()) {
                        io.writeTo(ras);
                    }
                    if (blockLength > getBlockLength(blockOne.values())) {
                        byte[] temp = new byte[(int) (blockLength - getBlockLength(blockOne.values()))];
                        ras.write(temp);
                    }
                    currentBlock.clear();
                    currentBlock.putAll(blockOne);

                    ras.seek(ras.length());
                    IndexOne tail = new IndexOne();
                    tail.getData().add(ras.getFilePointer());
                    tail.getData().add(getBlockLength(currentBlock.values()) * 2L);
                    tail.setBlockMark(true);
                    tail.setId(blockTwo.firstKey());
                    tail.setSize(blockTwo.size());
                    baseIndex.put(tail.getId(), tail);

                    tail.writeTo(ras);
                    for (IndexOne io : blockTwo.values()) {
                        io.writeTo(ras);
                    }
                    if (tail.getSize() > getBlockLength(blockTwo.values())) {
                        byte[] temp = new byte[tail.getSize() - getBlockLength(blockTwo.values())];
                        ras.write(temp);
                    }
                } else {
                    if (blockLength == 0) {
                        blockLength = getBlockLength(currentBlock.values());
                    }
                    head.getData().clear();
                    head.getData().add(ras.getFilePointer());
                    head.getData().add(blockLength);
                    head.setSize(currentBlock.size());
                    head.writeTo(ras);
                    for (IndexOne io : currentBlock.values()) {
                        io.writeTo(ras);
                    }
                    if (head.getSize() > getBlockLength(currentBlock.values())) {
                        byte[] temp = new byte[head.getSize() - getBlockLength(currentBlock.values())];
                        ras.write(temp);
                    }
                }
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
            ras.seek(head.getData().get(0) + head.getRecordSize());
            for (int i = 0; i < head.getSize(); ++i) {
                IndexOne one = new IndexOne().readFrom(ras);
                if (!one.isDeleted()) {
                    currentBlock.put(one.getId(), one);
                }
            }
            if (head.getSize() != currentBlock.size()) {
                head.setSize(currentBlock.size());
            }
            ++readCounter;
        }
    }

    public void set(long id, long offset) throws IOException {
        List<Long> list = new ArrayList<Long>() {{
            add(offset);
        }};
        set(id, list);
    }

    public void set(long id, List<Long> offset) throws IOException {
        IndexOne io = getOne(id);
        if (io == null) {
            IndexOne top = getHead(id);
            loadBlock(top);
            io = new IndexOne();
            io.setId(id);
            io.getData().addAll(offset);
            io.setSize(offset.size());
            currentBlock.put(io.getId(), io);
            baseIndex.remove(top.getId());
            top.setId(currentBlock.firstKey());
            top.setSize(currentBlock.size());
            top.getData().remove(1);
            top.getData().add((long) getBlockLength(currentBlock.values()));
            baseIndex.put(top.getId(), top);
            changed = true;
            if (currentBlock.size() > BLOCK_SIZE) {
                saveCurrentBlock();
                changed = false;
            }
        } else if (!io.getData().equals(offset)) {
            io.getData().clear();
            io.getData().addAll(offset);
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
        private byte flags = 0;
        private long id = -1;
        private int size = 0;
        private List<Long> data = new ArrayList<>();

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

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public List<Long> getData() {
            return data;
        }

        public void setData(List<Long> data) {
            this.data = data;
        }

        public boolean isDeleted() {
            return (flags & DELETED) != 0;
        }

        public void setDeleted(boolean on) {
            if (on) {
                flags |= DELETED;
            } else {
                flags &= ~DELETED;
            }
        }

        public boolean isBlockMark() {
            return (flags & BLOCK_MARK) != 0;
        }

        public void setBlockMark(boolean on) {
            if (on) {
                flags |= BLOCK_MARK;
            } else {
                flags &= ~BLOCK_MARK;
            }
        }

        public void writeTo(RandomAccessFile out) throws IOException {
            out.writeByte(flags);
            out.writeInt(size);
            out.writeLong(id);
            out.writeInt(data.size());
            for (long x : data) {
                out.writeLong(x);
            }
        }

        public IndexOne readFrom(RandomAccessFile in) throws IOException {
            flags = in.readByte();
            size = in.readInt();
            id = in.readLong();
            int cnt = in.readInt();
            data.clear();
            while (cnt-- > 0) {
                data.add(in.readLong());
            }
            return this;
        }

        public int getRecordSize() {
            return Byte.BYTES
                    + Long.BYTES
                    + Integer.BYTES
                    + Integer.BYTES
                    + Long.BYTES * data.size();
        }

        public String toString() {
            return id + "=" + data;
        }
    }

}
