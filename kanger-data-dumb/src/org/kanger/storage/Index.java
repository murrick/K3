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
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUser;

import java.io.*;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Index implements Closeable, Iterable<Index.IndexOne> {

    private static final int BLOCK_SIZE = 512;
    private static final int VERSION_CODE = 0x0101;

    private Object locker;

    private File file = null;
    private RandomAccessFile rasRead = null;
    private int version = VERSION_CODE;
    private int baseCode = 0;

    private NavigableMap<Long, IndexOne> baseIndex = new ConcurrentSkipListMap<>();
    private NavigableMap<Long, IndexOne> emptyIndex = new ConcurrentSkipListMap<>();
    private NavigableMap<Long, IndexOne> currentBlock = new ConcurrentSkipListMap<>();
    private NavigableMap<Long, IndexOne> dirtyEmptyBlocks = new ConcurrentSkipListMap<>();

    private volatile int readCounter = 0;
    private volatile int writeCounter = 0;

    private boolean readonly = false;
    private int blockSize = BLOCK_SIZE;
    private boolean changed = false;

    public Index(int baseCode, Object locker, IUser user) throws Exception {
        this.file = null;
        this.rasRead = null;
        this.changed = false;

        this.baseCode = baseCode;
        this.locker = locker;
        this.blockSize = Integer.parseInt(user.getProperty("cache.index.size", BLOCK_SIZE + ""));
    }

    public void open(String fileName, boolean readonly) throws Exception {
        open(new File(fileName), readonly);
    }

    public void open(File file, boolean readonly) throws Exception {
        this.readonly = readonly;
        this.file = file;
        this.rasRead = null;
        this.baseIndex.clear();
        this.emptyIndex.clear();
        this.currentBlock.clear();
        this.dirtyEmptyBlocks.clear();
        changed = false;

        try {
            synchronized (locker) {
                rasRead = new RandomAccessFile(file, "r");
                rasRead.seek(0);
                version = rasRead.readShort();

                if (version != VERSION_CODE) {
                    throw new DatabaseErrorException("Incompatible DB version");
                }

                blockSize = rasRead.readInt();

                while (rasRead.length() > rasRead.getFilePointer()) {
                    IndexOne one = new IndexOne(baseCode).readFrom(rasRead);
                    if (!one.isDeleted() && one.getCode() == baseCode) {
                        baseIndex.put(one.getId(), one);
                    } else if (one.isDeleted()) {
                        emptyIndex.put(one.getId(), one);
                    }
                    rasRead.seek(rasRead.getFilePointer() + (blockSize - 1) * IndexOne.RECORD_SIZE);
                }

                if (!baseIndex.isEmpty()) {
                    loadBlock(baseIndex.firstEntry().getValue(), currentBlock);
                }
            }
        } catch (FileNotFoundException ex) {
            clear();
            rasRead = new RandomAccessFile(file, "r");
        }
    }

    @Override
    public void close() throws IOException {
        if (!readonly) {
            try {
                flush();
            } catch (Exception e) {
                throw new IOException(e.toString());
            }
        }
        rasRead.close();
        rasRead = null;
        baseIndex.clear();
        emptyIndex.clear();
        currentBlock.clear();
        dirtyEmptyBlocks.clear();
    }

    public void flush() throws Exception {
        if (changed) {
            saveCurrentBlock();
            changed = false;
        }
    }

    public void clear() throws Exception {
        if (readonly) {
            throw new DatabaseErrorException("Database is readonly");
        }
        String path = file.getPath();
        path = path.substring(0, path.length() - file.getName().length());
        new File(path).mkdirs();
        synchronized (locker) {
            try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                baseIndex.clear();
                emptyIndex.clear();
                currentBlock.clear();
                dirtyEmptyBlocks.clear();
                ras.seek(0);
                ras.setLength(0);
                ras.writeShort(version);
                ras.writeInt(blockSize);
                changed = false;
            }
        }
    }

    private long getNext(long id, NavigableMap<Long, IndexOne> block) throws Exception {
        IndexOne head = getHead(id);
        if (head != null) {
            loadBlock(head, block);
            Long next = block.higherKey(id);
            if (next != null) {
                return next;
            } else if (baseIndex.higherKey(head.getId()) != null) {
                head = baseIndex.higherEntry(head.getId()).getValue();
                loadBlock(head, block);
                return block.firstKey();
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    private long getPrevious(long id, NavigableMap<Long, IndexOne> block) throws Exception {
        IndexOne head = getTail(id);
        if (head != null) {
            loadBlock(head, block);
            Long next = id == -1 ? block.lastKey() : block.lowerKey(id);
            if (next != null) {
                return next;
            } else if (baseIndex.lowerKey(head.getId()) != null) {
                head = baseIndex.lowerEntry(head.getId()).getValue();
                loadBlock(head, block);
                return block.lastKey();
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    private IndexOne getHead(long id) {
        IndexOne head = null;
        if (baseIndex.size() == 1) {
            head = baseIndex.get(baseIndex.firstKey());
        } else if (baseIndex.containsKey(id)) {
            head = baseIndex.get(id);
        } else if (!baseIndex.headMap(id).isEmpty()) {
            head = baseIndex.get(baseIndex.headMap(id).lastKey());
        } else if (!baseIndex.isEmpty()) {
            head = baseIndex.get(baseIndex.tailMap(id).firstKey());
        }
        return head;
    }

    private IndexOne getTail(long id) {
        IndexOne head = null;
        if (baseIndex.size() == 1) {
            head = baseIndex.get(baseIndex.firstKey());
        } else if (baseIndex.containsKey(id)) {
            head = baseIndex.get(id);
        } else if (!baseIndex.tailMap(id).isEmpty()) {
            head = baseIndex.get(baseIndex.tailMap(id).firstKey());
        } else if (!baseIndex.isEmpty()) {
            head = baseIndex.get(baseIndex.tailMap(id).lastKey());
        }
        return head;
    }

    public IndexOne getOne(long id) throws Exception {
        return getOne(id, currentBlock);
    }

    private IndexOne getOne(long id, NavigableMap<Long, IndexOne> currentBlock) throws Exception {
        if (currentBlock.containsKey(id)) {
            IndexOne io = currentBlock.get(id);
            if (!io.isDeleted()) {
                return io;
            }
        } else {
            IndexOne head = getHead(id);
            if (head != null) {
                loadBlock(head, currentBlock);
                if (currentBlock.containsKey(id)) {
                    IndexOne io = currentBlock.get(id);
                    if (!io.isDeleted()) {
                        return io;
                    }
                }
            }
        }
        return null;
    }

    private void writeDumb(RandomAccessFile ras, int cnt) throws IOException {
        ByteBuffer packet = new ByteBuffer();
        for (int i = 0; i < cnt; ++i) {
            packet
                    .putByte(((baseCode & 0xFF) << 4) | IndexOne.DELETED)
                    .append(new byte[Long.BYTES * 2]);
        }
        ras.write(packet.getBuffer());
    }

    private void saveCurrentBlock() throws Exception {
        if (readonly) {
            throw new DatabaseErrorException("Database is readonly");
        }

        IndexOne head = currentBlock.isEmpty() ? null : baseIndex.get(currentBlock.firstKey());
        if (head != null || !dirtyEmptyBlocks.isEmpty()) {
            synchronized (locker) {
                try (RandomAccessFile ras = new RandomAccessFile(file, "rw")) {
                    boolean wrote = false;
                    for (IndexOne emptyHead : dirtyEmptyBlocks.values()) {
                        if (emptyHead.getOffset() > 0) {
                            ras.seek(emptyHead.getOffset());
                            writeDumb(ras, blockSize);
                            wrote = true;
                        }
                    }
                    dirtyEmptyBlocks.clear();

                    if (head != null) {
                        if (head.getOffset() == 0) {
                            head.setOffset(ras.length());
                        }

                        if (head.isDeleted()) {
                            currentBlock.clear();
                            ras.seek(head.getOffset());
                            writeDumb(ras, blockSize);
                            baseIndex.remove(head.getId());
                        } else if (currentBlock.size() > blockSize) {
                            TreeMap<Long, IndexOne> blockOne = new TreeMap<>();
                            TreeMap<Long, IndexOne> blockTwo = new TreeMap<>();
                            int current = 0;
                            for (IndexOne one : currentBlock.values()) {
                                if (current < currentBlock.size() / 2) {
                                    blockOne.put(one.getId(), one);
                                } else {
                                    blockTwo.put(one.getId(), one);
                                }
                                ++current;
                            }

                            if (!blockOne.isEmpty()) {
                                ras.seek(head.getOffset());
                                for (IndexOne io : blockOne.values()) {
                                    io.writeTo(ras);
                                }
                                writeDumb(ras, blockSize - blockOne.size());
                                currentBlock.clear();
                                currentBlock.putAll(blockOne);
                            } else {
                                currentBlock.clear();
                                ras.seek(head.getOffset());
                                writeDumb(ras, blockSize);
                                baseIndex.remove(head.getId());
                                emptyIndex.put(head.getId(), head);
                            }

                            if (!blockTwo.isEmpty()) {

                                ras.seek(ras.length());
                                for (IndexOne io : blockTwo.values()) {
                                    io.writeTo(ras);
                                }
                                IndexOne tail = blockTwo.firstEntry().getValue();
                                baseIndex.put(tail.getId(), tail);
                                writeDumb(ras, blockSize - blockTwo.size());
                                if (currentBlock.isEmpty()) {
                                    currentBlock.putAll(blockTwo);
                                }
                            }

                        } else {
                            ras.seek(head.getOffset());
                            for (IndexOne io : currentBlock.values()) {
                                io.writeTo(ras);
                            }
                            writeDumb(ras, blockSize - currentBlock.size());
                        }
                        wrote = true;
                    }
                    if (wrote) {
                        ++writeCounter;
                    }
                }
            }
        }
    }

    private void loadBlock(IndexOne head, NavigableMap<Long, IndexOne> block) throws Exception {
        if (block.isEmpty() || head.getId() != block.firstKey()) {
            if (block == currentBlock) {
                flush();
            }
            block.clear();
            boolean wasRead = false;
            synchronized (locker) {
                rasRead.seek(head.getOffset());
                for (int i = 0; i < blockSize; ++i) {
                    IndexOne one = new IndexOne(baseCode).readFrom(rasRead);
                    wasRead = true;
                    if (!one.isDeleted()) {
                        block.put(one.getId(), one);
                    } else {
                        break;
                    }
                }
            }
            if (wasRead) {
                ++readCounter;
            }
        }
    }

    public IndexOne set(long id, long data) throws Exception {
        synchronized (locker) {
            IndexOne io = getOne(id, currentBlock);
            if (io == null) {

                io = new IndexOne(baseCode);
                io.setId(id);
                io.setLong(data);

                IndexOne top = getHead(id);
                if (top != null) {
                    loadBlock(top, currentBlock);
                    baseIndex.remove(top.getId());
                    currentBlock.put(io.getId(), io);
                    currentBlock.firstEntry().getValue().setOffset(top.getOffset());
                    top = currentBlock.firstEntry().getValue();
                    baseIndex.put(top.getId(), top);
                } else {
                    currentBlock.clear();
                    currentBlock.put(io.getId(), io);
                    io.setOffset(0 /*rasRead.length()*/);
                    baseIndex.put(io.getId(), io);
                }
                changed = true;
                if (currentBlock.size() > blockSize) {
                    flush();
                }
            } else if (io.getLong() != data) {
                io.setLong(data);
                changed = true;
            }
            return io;
        }
    }

    public void remove(long id) throws Exception {
        synchronized (locker) {
            IndexOne head = getHead(id);
            if (head != null) {
                loadBlock(head, currentBlock);
                currentBlock.remove(id);
                if (!currentBlock.isEmpty()) {
                    baseIndex.remove(head.getId());
                    currentBlock.firstEntry().getValue().setOffset(head.getOffset());
                    head = currentBlock.firstEntry().getValue();
                    baseIndex.put(head.getId(), head);
                } else {
                    head.setDeleted(true);
                    baseIndex.remove(head.getId());
                    emptyIndex.put(head.getId(), head);
                    if (head.getOffset() > 0) {
                        dirtyEmptyBlocks.put(head.getId(), head);
                    }
                }
                changed = true;
            }
        }
    }


    public boolean isClosed() {
        return rasRead == null;
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

    public long firstKey() {
        if (baseIndex.firstKey() != null) {
            return baseIndex.firstKey();
        } else {
            return -1;
        }
    }

    public long lastKey() throws Exception {
        if (baseIndex.lastKey() != null) {
            if (!currentBlock.isEmpty() && baseIndex.lastKey() == currentBlock.firstKey()) {
                return currentBlock.lastKey();
            } else {
                NavigableMap<Long, IndexOne> block = new TreeMap<>();
                loadBlock(baseIndex.lastEntry().getValue(), block);
                return block.lastKey();
            }
        } else {
            return -1;
        }
    }

    @Override
    public Iterator<IndexOne> iterator() {
        try {
            return new IndexIterator(false);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create forward index iterator", e);
        }
    }

    public Iterator<IndexOne> iterator(boolean backward) {
        try {
            return new IndexIterator(backward);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create index iterator", e);
        }
    }

    public boolean isEmpty() {
        return baseIndex.isEmpty();
    }

    public class IndexOne implements Comparable<IndexOne> {
        public static final int RECORD_SIZE = Byte.BYTES + Long.BYTES + Long.BYTES;
        private static final int DELETED = 0x01;

        private byte flags = 0;
        private long id = -1;
        private byte[] data = null;

        private long offset = -1;

        public long getId() {
            return id;
        }

        public IndexOne(int baseCode) {
            setCode(baseCode);
        }

        public IndexOne setId(long id) {
            this.id = id;
            return this;
        }

        public long getLong() throws OutOfBufferException {
            return new ByteBuffer(data).getLong();
        }

        public IndexOne setLong(long data) {
            this.data = new ByteBuffer().putLong(data).getBuffer();
            return this;
        }

        public long getOffset() {
            return offset;
        }

        public IndexOne setOffset(long offset) {
            this.offset = offset;
            return this;
        }

        public boolean isDeleted() {
            return (flags & DELETED) != 0;
        }

        public IndexOne setDeleted(boolean on) {
            if (on) {
                flags |= DELETED;
            } else {
                flags &= ~DELETED;
            }
            return this;
        }

        public IndexOne writeTo(RandomAccessFile out) throws IOException {
            if (data == null) {
                data = new byte[Long.BYTES];
            }
            offset = out.getFilePointer();
            out.writeByte(flags);
            out.writeLong(id);
            out.write(data, 0, Long.BYTES);
            return this;
        }

        public IndexOne readFrom(RandomAccessFile in) throws IOException {
            if (data == null) {
                data = new byte[Long.BYTES];
            }
            offset = in.getFilePointer();
            flags = in.readByte();
            id = in.readLong();
            in.read(data, 0, Long.BYTES);
            return this;
        }

        public String toString() {
            return id + "=" + data;
        }

        public int getCode() {
            return (flags >> 4) & 0x0F;
        }

        public IndexOne setCode(int code) {
            flags &= ~0xF0;
            flags |= (code & 0x0F) << 4;
            return this;
        }

        @Override
        public int compareTo(IndexOne indexOne) {
            return Long.compare(id, indexOne.getId());
        }
    }

    public class IndexIterator implements Iterator<Index.IndexOne> {

        private NavigableMap<Long, Index.IndexOne> block = new TreeMap<>();

        private long currentId;
        private long blockId;
        private final boolean backward;

        public IndexIterator() throws Exception {
            this(false);
        }

        public IndexIterator(boolean backward) throws Exception {
            this.backward = backward;
            flush();
            currentId = backward ? -1L : Long.MIN_VALUE;
            if (baseIndex.isEmpty()) {
                blockId = -1L;
                return;
            }
            blockId = backward ? baseIndex.lastKey() : baseIndex.firstKey();
            loadBlock(baseIndex.get(blockId), block);
        }

        @Override
        public void remove() {
        }

        @Override
        public boolean hasNext() {
            try {
                if (isEmpty()) {
                    return false;
                }
                if (backward) {
                    return getPrevious(currentId, block) != -1;
                } else {
                    return getNext(currentId, block) != -1;
                }
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Index.IndexOne next() {
            try {
                if (backward) {
                    currentId = getPrevious(currentId, block);
                } else {
                    currentId = getNext(currentId, block);
                }
                if (currentId != -1) {
                    return getOne(currentId, block);
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
    }

}
