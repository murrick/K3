package kanger.data;

import java.io.IOException;
import java.io.RandomAccessFile;

public class IndexOne extends IndexCore {

    public static final int SIZE = IndexCore.SIZE + Long.SIZE;

    protected long value;

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public void writeTo(RandomAccessFile out) throws IOException {
        out.writeShort(flags);
        out.writeLong(id);
        out.writeLong(value);
    }

    public IndexOne readFrom(RandomAccessFile in) throws IOException {
        flags = in.readShort();
        id = in.readLong();
        value = in.readLong();
        return this;
    }

}
