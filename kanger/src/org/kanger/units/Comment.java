package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

public class Comment implements IUnit<Comment> {

    private long id = -1;
    private String comment = "";
    private long mindId = -1;
    private Mind mind;
//    private boolean deleted = false;

    public Comment() {
    }

    public Comment(long id, String comment, Mind mind) {
        this.id = id;
        this.comment = comment;
        this.mind = mind;
        this.mindId = mind.getId();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long id) {
        mindId = id;
    }

    @Override
    public int getHash() throws Exception {
        return comment.hashCode();
    }

    @Override
    public boolean equalsTo(Comment to) throws Exception {
        return id == to.id && comment.equals(to.comment);
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public Comment setMind(Mind mind) throws Exception {
        this.mind = mind;
        return this;
    }

    @Override
    public boolean isDeleted(Mind mind) {
        return mind.isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putString(comment);
        return packet.createMarked();
    }

    @Override
    public Comment apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        comment = packet.getString();
        return this;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.COMMENT;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return comment;
    }
}
