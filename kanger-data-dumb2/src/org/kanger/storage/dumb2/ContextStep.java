package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.ByteBuffer;
import org.kanger.storage.Sapato;

/**
 * Persistent DUMB2 chain node.
 *
 * <p>The record envelope is materialized immediately, so id/hash/next/typeCode
 * are available without semantic hydration. KANGER data is decoded only from
 * {@link #getData(Mind)}. Neutral LONG/LONGS payloads may be attached eagerly
 * because they require no semantic factory or Mind.</p>
 *
 * <p>This class extends {@link Sapato} only to preserve the existing
 * Escalera persistent-step boundary. Its wire format is DUMB2
 * {@link PersistentRecordCodec}, never the legacy Sapato codec.</p>
 */
final class ContextStep extends Sapato {

    private final ContextBase base;
    private PersistentRecord record;
    private Object data;
    private long size;

    ContextStep(ContextBase base, PersistentRecord record) throws Exception {
        super(base);
        if (base == null || record == null) {
            throw new NullPointerException();
        }
        this.base = base;
        this.record = record;
        this.size = PersistentRecordCodec.encode(record).length;
        if (base.isNeutralPhysicalRecord(record)) {
            this.data = base.materialize(record, null);
        }
    }

    PersistentRecord getPersistentRecord() {
        return record;
    }

    @Override
    public long getNextId() {
        return record.getNextId();
    }

    boolean hasMaterializedData() {
        return data != null;
    }

    byte[] rawBytes() throws Exception {
        return PersistentRecordCodec.encode(record);
    }

    void replaceRecord(PersistentRecord next) throws Exception {
        if (next == null) {
            throw new NullPointerException("next");
        }
        this.record = next;
        this.size = PersistentRecordCodec.encode(next).length;
    }

    @Override
    public ByteBuffer pack() {
        try {
            return new ByteBuffer(rawBytes());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot encode DUMB2 Context step", failure);
        }
    }

    @Override
    public IStep apply(ByteBuffer packet) throws Exception {
        if (packet == null) {
            throw new NullPointerException("packet");
        }
        PersistentRecord restored =
                PersistentRecordCodec.decode(packet.getBytes(packet.rest()));
        replaceRecord(restored);
        data = base.isNeutralPhysicalRecord(restored)
                ? base.materialize(restored, null)
                : null;
        return this;
    }

    @Override
    public Object getData(Mind mind) throws Exception {
        if (data == null) {
            data = base.materialize(record, mind);
        }
        return data;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public IStep getNext() {
        if (record.getNextId() < 0L) {
            return null;
        }
        try {
            return base.get(record.getNextId());
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Cannot resolve DUMB2 next step " + record.getNextId(), failure);
        }
    }

    @Override
    public void setNext(IStep next) {
        record = new PersistentRecord(
                record.getId(),
                record.getHash(),
                next == null ? -1L : next.getId(),
                record.getTypeCode(),
                record.getPayload());
    }

    @Override
    public long getId() {
        return record.getId();
    }

    @Override
    public void setId(long id) {
        record = new PersistentRecord(
                id,
                record.getHash(),
                record.getNextId(),
                record.getTypeCode(),
                record.getPayload());
    }

    @Override
    public int getHash() {
        return record.getHash();
    }

    @Override
    public void setHash(int hash) {
        record = new PersistentRecord(
                record.getId(),
                hash,
                record.getNextId(),
                record.getTypeCode(),
                record.getPayload());
    }

    @Override
    public void update() throws Exception {
        base.update(this);
    }

    @Override
    public void append() throws Exception {
        base.add(this);
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void setSize(long size) {
        this.size = size;
    }
}
