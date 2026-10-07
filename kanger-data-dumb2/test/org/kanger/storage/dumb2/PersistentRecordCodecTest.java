package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification of the generic DUMB2 physical record envelope. */
public class PersistentRecordCodecTest {

    @Test
    void envelopeRoundTripsContextLocalTypeCodeAndPayload() throws Exception {
        PersistentRecord source = new PersistentRecord(
                17L, 0x12345678, 23L, 41, new byte[]{1, 2, 3, 4});

        byte[] bytes = PersistentRecordCodec.encode(source);
        PersistentRecord restored = PersistentRecordCodec.decode(bytes);

        assertEquals(17L, restored.getId());
        assertEquals(0x12345678, restored.getHash());
        assertEquals(23L, restored.getNextId());
        assertEquals(41, restored.getTypeCode());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, restored.getPayload());
    }

    @Test
    void reservedCommentIdsRoundTripButNullAndUnknownNegativeIdsReject() throws Exception {
        PersistentRecord record = new PersistentRecord(-2L, 1, -3L, 1, new byte[]{1});
        assertEquals(record, PersistentRecordCodec.decode(PersistentRecordCodec.encode(record)));
        assertThrows(IllegalArgumentException.class,
                () -> new PersistentRecord(-1L, 1, -1L, 1, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new PersistentRecord(-4L, 1, -1L, 1, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new PersistentRecord(0L, 1, -4L, 1, new byte[0]));
    }

    @Test
    void envelopeRejectsDamagedBytes() throws Exception {
        byte[] bytes = PersistentRecordCodec.encode(
                new PersistentRecord(1L, 2, -1L, 3, new byte[]{4, 5}));
        bytes[12] ^= 0x01;

        assertThrows(IOException.class,
                () -> PersistentRecordCodec.decode(bytes));
    }

    @Test
    void envelopeRejectsInvalidTypeCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersistentRecord(1L, 2, -1L, 0, new byte[0]));
    }
}
