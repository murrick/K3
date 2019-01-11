package kanger.storage;

import java.io.*;
import java.util.*;

public class Index implements Closeable, Iterable<Index.IndexOne> {

    private static final int DELETED = 0x01;
    private static final int BLOCK_MARK = 0x10;

    private static final int BLOCK_SIZE = 512;
    private static final short VERSION = 0x0301;

    private int version = VERSION;

    private NavigableMap<Long, IndexOne> baseIndex = new TreeMap<>();
    private NavigableMap<Long, IndexOne> currentBlock = new TreeMap<>();

    private File file = null;
    private RandomAccessFile ras = null;
    private boolean changed = false;
    private int blockSize = BLOCK_SIZE;

    private volatile int readCounter = 0;
    private volatile int writeCounter = 0;

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
            version = ras.readShort();
            blockSize = ras.readInt();
            do {
                IndexOne one = new IndexOne().readFrom(ras);
                if (!one.isDeleted() && one.isBlockMark() && one.getSize() > 0) {
                    baseIndex.put(one.getId(), one);
                    ras.seek(one.getData().get(0) + one.getData().get(1) + one.getRecordSize());
                } else if (ras.length() > ras.getFilePointer()) {
                    if(one.isBlockMark()) {
                        ras.seek(one.getData().get(0) + one.getData().get(1) + one.getRecordSize());
                    } else {
                        ras.seek(one.getData().get(0) + one.getSize() * Long.BYTES + one.getRecordSize());
                    }
                } else {
                    break;
                }
            } while (ras.length() > ras.getFilePointer());
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
        baseIndex.clear();
        currentBlock.clear();
    }

    public void flush() throws IOException {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }
    }

    public void clear() throws IOException {
        try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
            baseIndex.clear();
            currentBlock.clear();
            ras.seek(0);
            ras.setLength(0);
            ras.writeShort(version);
            ras.writeInt(blockSize);

            IndexOne one = new IndexOne();
            one.setBlockMark(true);
            one.setId(0);
            one.setSize(0);
            one.getData().add(ras.getFilePointer());
            one.getData().add(0L);
            one.writeTo(ras);
            baseIndex.put(one.getId(), one);
            changed = true;
        }
    }

    private long getNext(long id, NavigableMap<Long, IndexOne> block) throws IOException {
        IndexOne head = getHead(id);
        loadBlock(head, block);
        Long next = currentBlock.higherKey(id);
        if (next != null) {
            return next;
        } else if (baseIndex.higherKey(head.getId()) != null) {
            head = baseIndex.higherEntry(head.getId()).getValue();
            loadBlock(head, block);
            return currentBlock.firstKey();
        } else {
            return -1;
        }
    }

    private long getPrevious(long id, NavigableMap<Long, IndexOne> block) throws IOException {
        IndexOne head = getTail(id);
        loadBlock(head, block);
        Long next = id == -1 ? currentBlock.lastKey() : currentBlock.lowerKey(id);
        if (next != null) {
            return next;
        } else if (baseIndex.lowerKey(head.getId()) != null) {
            head = baseIndex.lowerEntry(head.getId()).getValue();
            loadBlock(head, block);
            return currentBlock.lastKey();
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

    private IndexOne getTail(long id) {
        IndexOne head;
        if (baseIndex.size() == 1) {
            head = baseIndex.get(baseIndex.firstKey());
        } else if (baseIndex.containsKey(id)) {
            head = baseIndex.get(id);
        } else if (!baseIndex.tailMap(id).isEmpty()) {
            head = baseIndex.get(baseIndex.tailMap(id).firstKey());
        } else {
            head = baseIndex.get(baseIndex.tailMap(id).lastKey());
        }
        return head;
    }


    public IndexOne getOne(long id) throws IOException {
        return getOne(id, currentBlock);
    }

    private IndexOne getOne(long id, NavigableMap<Long, IndexOne> block) throws IOException {
        if (block.containsKey(id)) {
            IndexOne io = block.get(id);
            if (io == null || io.isDeleted()) {
                return null;
            } else {
                return io;
            }
        } else {
            IndexOne head = getHead(id);
            loadBlock(head, block);
            IndexOne io = block.get(id);
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
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                boolean isLast = head.getData().get(1) == 0 || head.getData().get(0) + head.getRecordSize() + head.getData().get(1) >= ras.length();
                long blockLength = head.getData().get(1);
                if (currentBlock.size() > BLOCK_SIZE || (!isLast && blockLength > 0 && blockLength < getBlockLength(currentBlock.values()))) {
                    TreeMap<Long, IndexOne> blockOne = new TreeMap<>();
                    TreeMap<Long, IndexOne> blockTwo = new TreeMap<>();
                    int current = 0;
                    if (blockLength == 0) {
                        blockLength = getBlockLength(currentBlock.values());
                        head.getData().remove(1);
                        head.getData().add(blockLength);
                    }
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

//                    head.getData().remove(1);
//                    head.getData().add(blockLength);
                    if (blockOne.size() > 0) {
                        head.setSize(blockOne.size());
                        head.setId(blockOne.firstKey());
                        ras.seek(head.getData().get(0));
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
                    } else {
                        currentBlock.clear();
                        head.setDeleted(true);
                        head.setSize(0);
                        ras.seek(head.getData().get(0));
                        head.writeTo(ras);
                        baseIndex.remove(head.getId());
                    }

                    ras.seek(ras.length());
                    if (blockLength < getBlockLength(blockTwo.values())) {
                        blockLength = getBlockLength(blockTwo.values());
                    }
                    IndexOne tail = new IndexOne();
                    tail.getData().add(ras.getFilePointer());
                    tail.getData().add(blockLength);
                    tail.setBlockMark(true);
                    tail.setId(blockTwo.firstKey());
                    tail.setSize(blockTwo.size());
                    baseIndex.put(tail.getId(), tail);

                    tail.writeTo(ras);
                    for (IndexOne io : blockTwo.values()) {
                        io.writeTo(ras);
                    }
                    if (blockLength > getBlockLength(blockTwo.values())) {
                        byte[] temp = new byte[(int) (blockLength - getBlockLength(blockTwo.values()))];
                        ras.write(temp);
                    }
                    if (currentBlock.isEmpty()) {
                        currentBlock.putAll(blockTwo);
                    }

                } else {
                    if (blockLength == 0 || (isLast && blockLength < getBlockLength(currentBlock.values()))) {
                        blockLength = getBlockLength(currentBlock.values());
                    }
                    head.getData().remove(1);
                    head.getData().add(blockLength);
                    head.setSize(currentBlock.size());
                    ras.seek(head.getData().get(0));
                    head.writeTo(ras);
                    for (IndexOne io : currentBlock.values()) {
                        io.writeTo(ras);
                    }
                    if (blockLength > getBlockLength(currentBlock.values())) {
                        byte[] temp = new byte[(int) (blockLength - getBlockLength(currentBlock.values()))];
                        ras.write(temp);
                    }
                }
                ++writeCounter;
            }
        }
    }

    private void loadBlock(IndexOne head, NavigableMap<Long, IndexOne> block) throws IOException {
        if (block.isEmpty() || head.getId() != block.firstKey()) {
            if (block == currentBlock) {
                flush();
            }
            block.clear();
            ras.seek(head.getData().get(0) + head.getRecordSize());
            for (int i = 0; i < head.getSize(); ++i) {
                IndexOne one = new IndexOne().readFrom(ras);
                if (!one.isDeleted()) {
                    block.put(one.getId(), one);
                }
            }
            if (head.getSize() != block.size()) {
                head.setSize(block.size());
            }
            ++readCounter;
        }
    }


    public void add(long id, long offset) throws IOException {
        IndexOne io = getOne(id, currentBlock);
        if (io == null) {
            set(id, offset);
        } else if (!io.getData().contains(offset)) {
            io.getData().add(offset);
            io.setSize(io.getData().size());
            changed = true;
        }
    }

    public void set(long id, final long offset) throws IOException {
        List<Long> list = new ArrayList<Long>() {{
            add(offset);
        }};
        set(id, list);
    }

    public void set(long id, List<Long> offset) throws IOException {
        IndexOne io = getOne(id, currentBlock);
        if (io == null) {
            IndexOne top = getHead(id);
            loadBlock(top, currentBlock);
            io = new IndexOne();
            io.setId(id);
            io.getData().addAll(offset);
            io.setSize(offset.size());
            currentBlock.put(io.getId(), io);
            baseIndex.remove(top.getId());
            top.setId(currentBlock.firstKey());
            top.setSize(currentBlock.size());
            baseIndex.put(top.getId(), top);
            changed = true;
            if (currentBlock.size() > BLOCK_SIZE) {
                flush();
            }
        } else if (!io.getData().equals(offset)) {
            io.getData().clear();
            io.getData().addAll(offset);
            io.setSize(offset.size());
            changed = true;
        }
    }

    public void remove(long id) throws IOException {
        IndexOne head = getHead(id);
        loadBlock(head, currentBlock);
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

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
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
            return new IndexIterator();
        } catch (IOException e) {
            return null;
        }
    }

    public Iterator<IndexOne> iterator(boolean backward) {
        try {
            return new IndexIterator(backward);
        } catch (IOException e) {
            return null;
        }
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
            for (long x : data) {
                out.writeLong(x);
            }
        }

        public IndexOne readFrom(RandomAccessFile in) throws IOException {
            flags = in.readByte();
            size = in.readInt();
            id = in.readLong();
            int cnt = isBlockMark() ? 2 : size;
            data.clear();
            while (cnt-- > 0) {
                data.add(in.readLong());
            }
            return this;
        }

        public int getRecordSize() {
            return Byte.BYTES
                    + Integer.BYTES
                    + Long.BYTES
                    + Long.BYTES * data.size();
        }

        public String toString() {
            return id + "=" + data;
        }
    }

    public class IndexIterator implements Iterator<Index.IndexOne> {

        private NavigableMap<Long, Index.IndexOne> block = new TreeMap<>();

        private long currentId = 0;
        private long blockId = 0;
        private boolean backward = false;

        public IndexIterator() throws IOException {
            flush();
            currentId = -1;
            blockId = backward ? baseIndex.lastKey() : baseIndex.firstKey();
            loadBlock(baseIndex.get(blockId), block);
        }

        public IndexIterator(boolean backward) throws IOException {
            this();
            this.backward = backward;
        }

        @Override
        public void remove() {

        }

        @Override
        public boolean hasNext() {
            try {
                if(backward) {
                    return getPrevious(currentId, block) != -1;
                } else {
                    return getNext(currentId, block) != -1;
                }
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public Index.IndexOne next() {
            try {
                if(backward) {
                    currentId = getPrevious(currentId, block);
                } else {
                    currentId = getNext(currentId, block);
                }
                if (currentId != -1) {
                    return getOne(currentId, block);
                } else {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }
    }

}
