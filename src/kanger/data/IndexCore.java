package kanger.data;

import java.io.Serializable;

public class IndexCore implements Serializable {

    public static final int SIZE = Short.SIZE + Long.SIZE;

    protected static final int FLAG_SUBINDEX = 0x01;
    protected static final int FLAG_HEADER = 0x10;

    protected short flags;              // Тип заголовка
    protected long id;                  // Идентификатор заголовка

    public boolean isSubindex() {
        return (flags & FLAG_SUBINDEX) != 0;
    }

    public void setSubindex() {
        flags |= FLAG_SUBINDEX;
    }

    public boolean isHeader() {
        return (flags & FLAG_HEADER) != 0;
    }

    public void setHeader() {
        flags |= FLAG_HEADER;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


}
