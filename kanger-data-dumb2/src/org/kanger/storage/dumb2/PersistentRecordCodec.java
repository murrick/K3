package org.kanger.storage.dumb2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * Canonical generic codec for the DUMB 2.0 record envelope.
 *
 * <pre>
 * K3PR | version | id | hash | nextId | typeCode | payloadLength | payload | CRC32
 * </pre>
 *
 * <p>All multi-byte fields are little-endian. Persistent type identity is the
 * Context-local {@code typeCode}; no Java enum ordinal or class metadata is
 * present in this format.</p>
 */
final class PersistentRecordCodec {

    private static final int MAGIC = 0x4B335052; // K3PR
    private static final int VERSION = 1;
    private static final int CRC_SIZE = 4;
    private static final int MAX_PAYLOAD = 64 * 1024 * 1024;

    private PersistentRecordCodec() {
    }

    static byte[] encode(PersistentRecord record) throws IOException {
        if (record == null) {
            throw new NullPointerException("record");
        }

        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        DataOutputStream body = new DataOutputStream(bodyBytes);
        body.writeByte('K');
        body.writeByte('3');
        body.writeByte('P');
        body.writeByte('R');
        writeInt(body, VERSION);
        writeLong(body, record.getId());
        writeInt(body, record.getHash());
        writeLong(body, record.getNextId());
        writeInt(body, record.getTypeCode());
        byte[] payload = record.getPayload();
        writeInt(body, payload.length);
        body.write(payload);
        body.flush();

        byte[] bytes = bodyBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(bytes);

        ByteArrayOutputStream resultBytes =
                new ByteArrayOutputStream(bytes.length + CRC_SIZE);
        resultBytes.write(bytes);
        DataOutputStream result = new DataOutputStream(resultBytes);
        writeInt(result, (int) crc.getValue());
        result.flush();
        return resultBytes.toByteArray();
    }

    static PersistentRecord decode(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length < 4 + 4 + 8 + 4 + 8 + 4 + 4 + CRC_SIZE) {
            throw new IOException("Truncated DUMB2 persistent record");
        }

        int bodyLength = bytes.length - CRC_SIZE;
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bodyLength);
        long expected = ((long) bytes[bodyLength] & 0xffL)
                | (((long) bytes[bodyLength + 1] & 0xffL) << 8)
                | (((long) bytes[bodyLength + 2] & 0xffL) << 16)
                | (((long) bytes[bodyLength + 3] & 0xffL) << 24);
        if (crc.getValue() != expected) {
            throw new IOException("DUMB2 persistent record checksum mismatch");
        }

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, bodyLength));
        try {
            int magic = (input.readUnsignedByte() << 24)
                    | (input.readUnsignedByte() << 16)
                    | (input.readUnsignedByte() << 8)
                    | input.readUnsignedByte();
            int version = readInt(input);
            if (magic != MAGIC || version != VERSION) {
                throw new IOException("Unsupported DUMB2 persistent record format");
            }

            long id = readLong(input);
            int hash = readInt(input);
            long nextId = readLong(input);
            int typeCode = readInt(input);
            int length = readInt(input);
            if (length < 0 || length > MAX_PAYLOAD || length > input.available()) {
                throw new IOException("Invalid DUMB2 persistent payload length " + length);
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            if (input.available() != 0) {
                throw new IOException("Trailing bytes in DUMB2 persistent record");
            }
            try {
                return new PersistentRecord(id, hash, nextId, typeCode, payload);
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid DUMB2 persistent record envelope", failure);
            }
        } catch (EOFException failure) {
            throw new IOException("Truncated DUMB2 persistent record", failure);
        }
    }

    private static void writeInt(DataOutputStream output, int value)
            throws IOException {
        output.writeByte(value);
        output.writeByte(value >>> 8);
        output.writeByte(value >>> 16);
        output.writeByte(value >>> 24);
    }

    private static int readInt(DataInputStream input) throws IOException {
        return input.readUnsignedByte()
                | (input.readUnsignedByte() << 8)
                | (input.readUnsignedByte() << 16)
                | (input.readUnsignedByte() << 24);
    }

    private static void writeLong(DataOutputStream output, long value)
            throws IOException {
        for (int i = 0; i < 8; ++i) {
            output.writeByte((int) (value >>> (8 * i)));
        }
    }

    private static long readLong(DataInputStream input) throws IOException {
        long value = 0L;
        for (int i = 0; i < 8; ++i) {
            value |= ((long) input.readUnsignedByte()) << (8 * i);
        }
        return value;
    }
}
