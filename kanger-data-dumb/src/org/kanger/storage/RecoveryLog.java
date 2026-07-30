/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage;

import org.kanger.exception.DatabaseErrorException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Per-logical-base undo journal for one physical DUMB storage pair.
 *
 * <p>Only the first before-image of an ID after a completed flush is needed:
 * rollback always restores the transaction-entry state. The journal file is
 * therefore retained as one append session until checkpoint instead of being
 * reopened for every physical mutation.</p>
 */
final class RecoveryLog {

    private static final int MAGIC = 0x4B445531; // KDU1
    private static final short VERSION = 1;
    private static final int HEADER_SIZE = Integer.BYTES + Short.BYTES + Integer.BYTES;
    private static final int MAX_RECORD_SIZE = 64 * 1024 * 1024;

    private static final byte TYPE_UPSERT = 1;
    private static final byte TYPE_DELETE = 2;

    private static final int STORE_HEADER_SIZE = Short.BYTES + Integer.BYTES;
    private static final int STORE_BLOCK_HEADER_SIZE = Long.BYTES * 2;

    private final File file;
    private final int baseCode;
    private final Object locker;
    private final Set<Long> journaledIds = new HashSet<Long>();
    private RandomAccessFile output;

    RecoveryLog(String storageName, int baseCode, Object locker) {
        this.file = new File(storageName + ".wal." + baseCode);
        this.baseCode = baseCode;
        this.locker = locker;
    }

    boolean hasPending() {
        synchronized (locker) {
            return file.exists() && file.length() > 0L;
        }
    }

    void prepareUpsert(long id, Index index, Data data) throws Exception {
        synchronized (locker) {
            Long key = Long.valueOf(id);
            if (journaledIds.contains(key)) {
                return;
            }
            BeforeImage before = capture(id, index, data);
            append(new Record(TYPE_UPSERT,
                    Collections.singletonList(before)));
            journaledIds.add(key);
        }
    }

    void prepareDelete(Collection<Long> ids, Index index, Data data) throws Exception {
        synchronized (locker) {
            List<BeforeImage> before = new ArrayList<BeforeImage>();
            List<Long> accepted = new ArrayList<Long>();
            for (Long id : ids) {
                if (id == null || journaledIds.contains(id)) {
                    continue;
                }
                BeforeImage image = capture(id.longValue(), index, data);
                if (image.existed) {
                    before.add(image);
                    accepted.add(id);
                }
            }
            if (!before.isEmpty()) {
                append(new Record(TYPE_DELETE, before));
                journaledIds.addAll(accepted);
            }
        }
    }

    void rollback(Index index, Data data) throws Exception {
        synchronized (locker) {
            closeOutput();
            List<Record> records = load();
            for (int recordIndex = records.size() - 1; recordIndex >= 0; --recordIndex) {
                List<BeforeImage> images = records.get(recordIndex).images;
                for (int imageIndex = images.size() - 1; imageIndex >= 0; --imageIndex) {
                    BeforeImage image = images.get(imageIndex);
                    if (image.existed) {
                        restore(image, index, data);
                    } else {
                        removeCurrent(image.id, index, data);
                    }
                }
            }
        }
    }

    void checkpoint() throws IOException {
        synchronized (locker) {
            closeOutput();
            Files.deleteIfExists(file.toPath());
            journaledIds.clear();
        }
    }

    private BeforeImage capture(long id, Index index, Data data) throws Exception {
        // -1 is the Index traversal sentinel. Other signed IDs are valid
        // application/service keys; CommentFactory uses -2/-3 for header/footer.
        if (id == -1L) {
            throw corruption("cannot journal reserved id=-1");
        }
        Index.IndexOne current = index.getOne(id);
        if (current == null) {
            return new BeforeImage(id, false, new byte[0]);
        }
        return new BeforeImage(id, true,
                readPacked(data.getFile(), current.getLong()));
    }

    private void restore(BeforeImage image, Index index, Data data)
            throws Exception {
        Index.IndexOne current = index.getOne(image.id);
        long currentOffset = current == null ? -1L : current.getLong();
        long restoredOffset = writePacked(data.getFile(), currentOffset, image.packed);
        index.set(image.id, restoredOffset);
    }

    private void removeCurrent(long id, Index index, Data data) throws Exception {
        Index.IndexOne current = index.getOne(id);
        if (current != null) {
            index.remove(id);
            invalidate(data.getFile(), current.getLong());
        }
    }

    private void append(Record record) throws Exception {
        byte[] body = packRecord(record);
        CRC32 crc = new CRC32();
        crc.update(body);

        RandomAccessFile journal = openOutput();
        long start = journal.length();
        try {
            journal.seek(start);
            journal.writeInt(body.length);
            journal.write(body);
            journal.writeInt((int) crc.getValue());
        } catch (IOException failure) {
            try {
                journal.setLength(start);
            } catch (IOException ignored) {
            }
            throw failure;
        }
    }

    private RandomAccessFile openOutput() throws Exception {
        if (output != null) {
            return output;
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        output = new RandomAccessFile(file, "rw");
        if (output.length() == 0L) {
            output.writeInt(MAGIC);
            output.writeShort(VERSION);
            output.writeInt(baseCode);
        } else {
            validateHeader(output);
        }
        return output;
    }

    private void closeOutput() throws IOException {
        if (output != null) {
            output.close();
            output = null;
        }
    }

    private List<Record> load() throws Exception {
        List<Record> records = new ArrayList<Record>();
        if (!file.exists() || file.length() == 0L) {
            return records;
        }

        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.length() < HEADER_SIZE) {
                return records;
            }
            validateHeader(input);
            input.seek(HEADER_SIZE);
            while (input.getFilePointer() < input.length()) {
                long recordStart = input.getFilePointer();
                long remaining = input.length() - recordStart;
                if (remaining < Integer.BYTES) {
                    break;
                }

                int length = input.readInt();
                if (length <= 0 || length > MAX_RECORD_SIZE) {
                    throw corruption("invalid undo record length=" + length
                            + " offset=" + recordStart);
                }
                if (input.length() - input.getFilePointer()
                        < (long) length + Integer.BYTES) {
                    break;
                }

                byte[] body = new byte[length];
                input.readFully(body);
                int storedCrc = input.readInt();
                CRC32 crc = new CRC32();
                crc.update(body);
                if (((int) crc.getValue()) != storedCrc) {
                    if (input.getFilePointer() == input.length()) {
                        break;
                    }
                    throw corruption("undo record checksum mismatch offset="
                            + recordStart);
                }
                records.add(unpackRecord(body));
            }
        }
        return records;
    }

    private void validateHeader(RandomAccessFile input) throws Exception {
        input.seek(0L);
        if (input.readInt() != MAGIC) {
            throw corruption("invalid undo log magic");
        }
        if (input.readShort() != VERSION) {
            throw corruption("unsupported undo log version");
        }
        int storedBaseCode = input.readInt();
        if (storedBaseCode != baseCode) {
            throw corruption("undo log base mismatch expected=" + baseCode
                    + " actual=" + storedBaseCode);
        }
    }

    private byte[] packRecord(Record record) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(record.type);
            output.writeInt(record.images.size());
            for (BeforeImage image : record.images) {
                output.writeLong(image.id);
                output.writeBoolean(image.existed);
                output.writeInt(image.packed.length);
                output.write(image.packed);
            }
        }
        byte[] body = bytes.toByteArray();
        if (body.length <= 0 || body.length > MAX_RECORD_SIZE) {
            throw new IOException("DUMB undo record is too large: " + body.length);
        }
        return body;
    }

    private Record unpackRecord(byte[] body) throws Exception {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(body))) {
            byte type = input.readByte();
            if (type != TYPE_UPSERT && type != TYPE_DELETE) {
                throw corruption("invalid undo record type=" + type);
            }
            int count = input.readInt();
            if (count <= 0) {
                throw corruption("invalid undo before-image count=" + count);
            }
            List<BeforeImage> images = new ArrayList<BeforeImage>(count);
            for (int i = 0; i < count; ++i) {
                long id = input.readLong();
                boolean existed = input.readBoolean();
                int length = input.readInt();
                if (id == -1L || length < 0 || length > input.available()
                        || (existed && length == 0)
                        || (!existed && length != 0)) {
                    throw corruption("invalid undo before-image id=" + id
                            + " existed=" + existed + " length=" + length);
                }
                byte[] packed = new byte[length];
                input.readFully(packed);
                images.add(new BeforeImage(id, existed, packed));
            }
            if (input.available() != 0) {
                throw corruption("unexpected bytes in undo record");
            }
            return new Record(type, images);
        } catch (EOFException malformed) {
            throw corruption("truncated undo record");
        } catch (IOException malformed) {
            throw corruption("cannot parse undo record: " + malformed);
        }
    }

    private static byte[] readPacked(File store, long offset) throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(store, "r")) {
            long fileLength = input.length();
            if (offset < STORE_HEADER_SIZE
                    || offset > fileLength - STORE_BLOCK_HEADER_SIZE) {
                throw corruption("invalid store offset=" + offset
                        + " fileLength=" + fileLength);
            }
            input.seek(offset);
            long blockSize = input.readLong();
            long dataSize = input.readLong();
            long available = fileLength - offset - STORE_BLOCK_HEADER_SIZE;
            if (blockSize <= 0L || dataSize <= 0L
                    || dataSize > blockSize || dataSize > Integer.MAX_VALUE
                    || blockSize > available) {
                throw corruption("invalid store block offset=" + offset
                        + " blockSize=" + blockSize + " dataSize=" + dataSize
                        + " available=" + available);
            }
            byte[] packed = new byte[(int) dataSize];
            input.readFully(packed);
            return packed;
        }
    }

    private static long writePacked(File store, long offset, byte[] packed)
            throws Exception {
        if (packed == null || packed.length == 0) {
            throw corruption("cannot restore empty store record");
        }
        try (RandomAccessFile output = new RandomAccessFile(store, "rw")) {
            if (offset >= STORE_HEADER_SIZE
                    && offset <= output.length() - STORE_BLOCK_HEADER_SIZE) {
                output.seek(offset);
                long blockSize = output.readLong();
                if (blockSize >= packed.length
                        && offset + STORE_BLOCK_HEADER_SIZE + blockSize
                        <= output.length()) {
                    output.writeLong(packed.length);
                    output.write(packed);
                    return offset;
                }
                output.seek(offset + Long.BYTES);
                output.writeLong(0L);
            }

            output.seek(output.length());
            long restoredOffset = output.getFilePointer();
            output.writeLong(packed.length);
            output.writeLong(packed.length);
            output.write(packed);
            return restoredOffset;
        }
    }

    private static void invalidate(File store, long offset) throws Exception {
        try (RandomAccessFile output = new RandomAccessFile(store, "rw")) {
            if (offset < STORE_HEADER_SIZE
                    || offset > output.length() - STORE_BLOCK_HEADER_SIZE) {
                throw corruption("cannot invalidate store offset=" + offset);
            }
            output.seek(offset + Long.BYTES);
            output.writeLong(0L);
        }
    }

    private static DatabaseErrorException corruption(String detail) {
        return new DatabaseErrorException("DUMB recovery log error: " + detail);
    }

    private static final class Record {
        private final byte type;
        private final List<BeforeImage> images;

        private Record(byte type, List<BeforeImage> images) {
            this.type = type;
            this.images = images;
        }
    }

    private static final class BeforeImage {
        private final long id;
        private final boolean existed;
        private final byte[] packed;

        private BeforeImage(long id, boolean existed, byte[] packed) {
            this.id = id;
            this.existed = existed;
            this.packed = packed;
        }
    }
}
