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

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

public class Comment implements IUnit<Comment> {

    private long id = -1;
    private String comment = "";
    private long mindId = -1;
    private Mind mind;

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
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
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
            setDeleted(true, (Mind) mind);
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
