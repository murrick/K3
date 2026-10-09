package org.kanger.storage.dumb2;

import java.util.Arrays;
import org.kanger.factory.CommentFactory;

/**
 * Generic DUMB 2.0 persistent record envelope.
 *
 * <p>The envelope is storage structure only. {@code typeCode} is Context-local
 * and can be interpreted only through the owning Context manifest. The payload
 * is encoded by the descriptor associated with that typeCode.</p>
 */
final class PersistentRecord {

    private final long id;
    private final int hash;
    private final long nextId;
    private final int typeCode;
    private final byte[] payload;

    PersistentRecord(long id, int hash, long nextId, int typeCode, byte[] payload) {
        if (!isRecordId(id)) {
            throw new IllegalArgumentException("record id must be non-negative or a reserved source comment id");
        }
        if (nextId != -1L && !isRecordId(nextId)) {
            throw new IllegalArgumentException("nextId must be -1 or a valid record id");
        }
        if (typeCode <= 0) {
            throw new IllegalArgumentException("typeCode must be positive");
        }
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        this.id = id;
        this.hash = hash;
        this.nextId = nextId;
        this.typeCode = typeCode;
        this.payload = payload.clone();
    }

    // Core uses -2/-3 for source header/footer; -1 alone is the null link.
    static boolean isRecordId(long id) {
        return id >= 0L
                || id == CommentFactory.HEADER_ID
                || id == CommentFactory.FOOTER_ID;
    }

    long getId() {
        return id;
    }

    int getHash() {
        return hash;
    }

    long getNextId() {
        return nextId;
    }

    int getTypeCode() {
        return typeCode;
    }

    byte[] getPayload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PersistentRecord)) return false;
        PersistentRecord that = (PersistentRecord) other;
        return id == that.id
                && hash == that.hash
                && nextId == that.nextId
                && typeCode == that.typeCode
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + hash;
        result = 31 * result + (int) (nextId ^ (nextId >>> 32));
        result = 31 * result + typeCode;
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
