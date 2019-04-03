package kanger.data;

import java.io.IOException;
import java.io.RandomAccessFile;

public class IndexPage extends IndexCore {

    protected int size;
    protected long offset;
    protected long next;

    public IndexPage() {
        super();
        setHeader();
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getNext() {
        return next;
    }

    public void setNext(long next) {
        this.next = next;
    }

    public void writeTo(RandomAccessFile out) throws IOException {
        out.writeShort(flags);
        out.writeLong(id);
        out.writeInt(size);
        out.writeLong(offset);
    }

    public IndexPage readFrom(RandomAccessFile in) throws IOException {
        flags = in.readShort();
        id = in.readLong();
        size = in.readInt();
        offset = in.readLong();
        return this;
    }

}
