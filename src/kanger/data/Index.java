package kanger.data;

import kanger.Version;

import java.io.*;
import java.util.NavigableMap;
import java.util.TreeMap;

public class Index implements Closeable {

    private static final int BLOCK_SIZE = 512 * IndexOne.SIZE;
    private static final long START_OFFSET = Short.SIZE + Long.SIZE + Integer.SIZE;

    private long id = 0;
    private File file;
    private RandomAccessFile ras;
    private boolean changed;
    private int blockSize;
    private long startOffset = START_OFFSET;
    private int version = Version.VERSION_CODE;

    private NavigableMap<Long, IndexPage> emptyPages = new TreeMap<>();
    private NavigableMap<Long, IndexPage> baseIndex = new TreeMap<>();
    private NavigableMap<Long, IndexOne> currentBlock = new TreeMap<>();

    public Index() {
        this.id = 0;
        this.file = null;
        this.ras = null;
        this.changed = false;
        if (System.getProperties().containsKey("index.page.size")) {
            this.blockSize = Integer.parseInt(System.getProperty("index.page.size")) * IndexOne.SIZE;
        } else {
            this.blockSize = BLOCK_SIZE;
        }
    }

    public Index open(String fileName) throws IOException {
        return open(new File(fileName));
    }

    public Index open(File file) throws IOException {
        this.file = file;
        this.baseIndex.clear();
        this.currentBlock.clear();

        try {
            ras = new RandomAccessFile(file.getAbsoluteFile(), "r");
            version = ras.readShort();
            startOffset = ras.readLong();
            blockSize = ras.readInt();

            open();
            if (baseIndex.isEmpty()) {
                reset();
            } else {
                ras.seek(startOffset);
                while (true) {
                    IndexPage page = new IndexPage().readFrom(ras);
                    if(page.getSize() == 0) {
                        emptyPages.put(page.getId(), page);
                    }
                    if (ras.getFilePointer() + blockSize < ras.length()) {
                        ras.seek(ras.getFilePointer() + blockSize);
                    } else {
                        break;
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            reset();
            ras = new RandomAccessFile(file.getAbsoluteFile(), "r");
        }
        return this;
    }

    private Index open() throws IOException {

        ras.seek(startOffset);
        while (true) {
            IndexPage page = new IndexPage().readFrom(ras);
            id = page.getId();
            baseIndex.put(page.getId(), page);
            if (page.getNext() > 0 && page.getNext() < ras.length()) {
                ras.seek(page.getNext());
            } else {
                break;
            }
        }
        return this;
    }

    public Index reset() throws IOException {
        String path = file.getAbsolutePath();
        path = path.substring(0, path.length() - file.getName().length());
        new File(path).mkdirs();
        try (RandomAccessFile ras = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {
            baseIndex.clear();
            currentBlock.clear();
            ras.seek(0);
            ras.setLength(0);

            ras.writeShort(version);
            ras.writeLong(startOffset);
            ras.writeInt(blockSize);

            IndexPage page = new IndexPage();
            page.setId(0);
            page.setOffset(startOffset);
            page.writeTo(ras);

            ras.seek(startOffset);
            ras.write(new byte[blockSize]);

            baseIndex.put(page.getId(), page);
            changed = true;
        }
        return this;
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

    private void saveCurrentBlock() throws IOException {
        IndexPage head = currentBlock.isEmpty() ? null : baseIndex.get(currentBlock.firstKey());
        if (head != null) {
            try (RandomAccessFile ras = new RandomAccessFile(file.getAbsoluteFile(), "rw")) {

                TreeMap<Long, IndexOne> blockOne = new TreeMap<>();
                if (currentBlock.size() > blockSize) {
                    TreeMap<Long, IndexOne> blockTwo = new TreeMap<>();

                    int current = 0;
                    for (IndexOne one : currentBlock.values()) {
                        if (current < blockSize / 2) {
                            blockOne.put(one.getId(), one);
                        } else {
                            blockTwo.put(one.getId(), one);
                        }
                        current += IndexOne.SIZE;
                    }

                    currentBlock.clear();
                    currentBlock.putAll(blockOne);

                    ras.seek(ras.length());

                    IndexPage tail = new IndexPage();
                    tail.setOffset(ras.getFilePointer());
                    tail.setId(blockTwo.firstKey());
                    tail.setSize(blockTwo.size());
                    baseIndex.put(tail.getId(), tail);


                    ras.seek(tail.getOffset());
                    ras.write(new byte[blockSize]);

                    ras.seek(tail.getOffset() + IndexPage.SIZE);
                    tail.writeTo(ras);
                    for (IndexOne io : blockTwo.values()) {
                        io.writeTo(ras);
                    }
                } else {
                    blockOne.putAll(currentBlock);
                }

                head.setSize(blockOne.size());
                head.setId(blockOne.firstKey());

                ras.seek(head.getOffset());
                head.writeTo(ras);
                for (IndexOne io : blockOne.values()) {
                    io.writeTo(ras);
                }
            }
        }
    }


    private void loadBlock(IndexPage head, NavigableMap<Long, IndexOne> block) throws IOException {
        if (block.isEmpty() || head.getId() != block.firstKey()) {
            if (block == currentBlock) {
                flush();
            }
            block.clear();
            ras.seek(head.getOffset() + IndexPage.SIZE);
            for (int i = 0; i < head.getSize(); ++i) {
                IndexOne one = new IndexOne().readFrom(ras);
                block.put(one.getId(), one);
            }
        }
    }

    public File getFile() {
        return file;
    }

    public Index setFile(File file) {
        this.file = file;
        return this;
    }

    public RandomAccessFile getRas() {
        return ras;
    }

    public Index setRas(RandomAccessFile ras) {
        this.ras = ras;
        return this;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public Index setBlockSize(int blockSize) {
        this.blockSize = blockSize;
        return this;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public Index setStartOffset(long startOffset) {
        this.startOffset = startOffset;
        return this;
    }
}
