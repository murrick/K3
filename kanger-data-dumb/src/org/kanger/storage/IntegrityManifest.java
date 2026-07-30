/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage;

import org.kanger.exception.DatabaseErrorException;
import org.kanger.interfaces.internal.IStep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Checksummed manifest for one physical DUMB index/store pair.
 *
 * <p>Several logical {@link Base} instances with different base codes share
 * the same physical files. Each instance owns only its local entry subset, but
 * every manifest publication reloads and merges the complete global file under
 * the storage-wide locker.</p>
 */
final class IntegrityManifest {

    private static final int MAGIC = 0x4B444932; // KDI2
    private static final short VERSION = 2;
    private static final int HEADER_SIZE = Integer.BYTES + Short.BYTES
            + Integer.BYTES;
    private static final int ENTRY_SIZE = Integer.BYTES + Long.BYTES
            + Integer.BYTES + Integer.BYTES;
    private static final int FOOTER_SIZE = Integer.BYTES;
    private static final int STORE_HEADER_SIZE = Short.BYTES + Integer.BYTES;
    private static final int STORE_BLOCK_HEADER_SIZE = Long.BYTES * 2;

    private final File file;
    private final int baseCode;
    private final Object locker;
    private final Map<Long, Entry> entries = new LinkedHashMap<Long, Entry>();
    private boolean changed;

    IntegrityManifest(String fileName, int baseCode, Object locker) {
        this.file = new File(fileName);
        this.baseCode = baseCode;
        this.locker = locker;
    }

    void openOrBootstrap(Index index, Data data) throws Exception {
        synchronized (locker) {
            if (file.exists()) {
                Map<Key, Entry> global = loadGlobal();
                selectLocal(global);
                if (entries.isEmpty() && !index.isEmpty()) {
                    bootstrap(index, data);
                    mergeAndWrite(global);
                } else {
                    validate(index, data);
                }
            } else {
                bootstrap(index, data);
                Map<Key, Entry> global = new TreeMap<Key, Entry>();
                addLocal(global);
                writeGlobal(global);
                changed = false;
            }
        }
    }

    void put(IStep step) throws Exception {
        if (step == null || step.getId() < 0L) {
            throw corruption("cannot register null or negative-id record");
        }
        synchronized (locker) {
            Entry next = Entry.fromStep(step);
            Entry previous = entries.put(Long.valueOf(step.getId()), next);
            changed = !next.equals(previous) || changed;
        }
    }

    void remove(long id) {
        synchronized (locker) {
            if (entries.remove(Long.valueOf(id)) != null) {
                changed = true;
            }
        }
    }

    void clear() {
        synchronized (locker) {
            entries.clear();
            changed = true;
        }
    }

    void flush() throws Exception {
        synchronized (locker) {
            if (!file.exists()) {
                throw corruption("integrity manifest disappeared after initialization");
            }
            if (!changed) {
                return;
            }
            mergeAndWrite(loadGlobal());
        }
    }

    private void mergeAndWrite(Map<Key, Entry> global) throws Exception {
        Iterator<Key> iterator = global.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().baseCode == baseCode) {
                iterator.remove();
            }
        }
        addLocal(global);
        writeGlobal(global);
        changed = false;
    }

    private void addLocal(Map<Key, Entry> global) {
        for (Map.Entry<Long, Entry> one : entries.entrySet()) {
            global.put(new Key(baseCode, one.getKey().longValue()), one.getValue());
        }
    }

    private void selectLocal(Map<Key, Entry> global) {
        entries.clear();
        for (Map.Entry<Key, Entry> one : global.entrySet()) {
            if (one.getKey().baseCode == baseCode) {
                entries.put(Long.valueOf(one.getKey().id), one.getValue());
            }
        }
        changed = false;
    }

    private void bootstrap(Index index, Data data) throws Exception {
        entries.clear();
        if (!index.isEmpty()) {
            Iterator<Index.IndexOne> iterator = index.iterator();
            if (iterator == null) {
                throw corruption("cannot enumerate index during manifest bootstrap");
            }
            while (iterator.hasNext()) {
                Index.IndexOne one = iterator.next();
                if (one == null) {
                    throw corruption("null index record during manifest bootstrap");
                }
                readExpected(data, one.getId(), one.getLong());
                Entry stored = Entry.fromStored(data.getFile(), one.getLong());
                if (entries.put(Long.valueOf(one.getId()), stored) != null) {
                    throw corruption("duplicate index id=" + one.getId()
                            + " base=" + baseCode);
                }
            }
        }
        changed = true;
    }

    private void validate(Index index, Data data) throws Exception {
        for (Map.Entry<Long, Entry> expected : entries.entrySet()) {
            long id = expected.getKey().longValue();
            Index.IndexOne one = index.getOne(id);
            if (one == null) {
                throw corruption("manifest id is absent from index base="
                        + baseCode + " id=" + id);
            }
            readExpected(data, id, one.getLong());
            Entry actual = Entry.fromStored(data.getFile(), one.getLong());
            if (!expected.getValue().equals(actual)) {
                throw corruption("record checksum mismatch base="
                        + baseCode + " id=" + id);
            }
        }

        int seen = 0;
        if (!index.isEmpty()) {
            Iterator<Index.IndexOne> iterator = index.iterator();
            if (iterator == null) {
                throw corruption("cannot enumerate index during validation");
            }
            while (iterator.hasNext()) {
                Index.IndexOne one = iterator.next();
                if (one == null) {
                    throw corruption("null index record during validation");
                }
                if (!entries.containsKey(Long.valueOf(one.getId()))) {
                    throw corruption("index id is absent from manifest base="
                            + baseCode + " id=" + one.getId());
                }
                ++seen;
            }
        }
        if (seen != entries.size()) {
            throw corruption("index/manifest cardinality mismatch base=" + baseCode
                    + " index=" + seen + " manifest=" + entries.size());
        }
    }

    private IStep readExpected(Data data, long expectedId, long offset) throws Exception {
        IStep step = data.getUncached(offset);
        if (step == null) {
            throw corruption("index points to missing store record base=" + baseCode
                    + " id=" + expectedId + " offset=" + offset);
        }
        if (step.getId() != expectedId) {
            throw corruption("index/store id mismatch base=" + baseCode
                    + " expected=" + expectedId + " actual=" + step.getId()
                    + " offset=" + offset);
        }
        return step;
    }

    private Map<Key, Entry> loadGlobal() throws Exception {
        byte[] packet = Files.readAllBytes(file.toPath());
        if (packet.length < HEADER_SIZE + FOOTER_SIZE) {
            throw corruption("integrity manifest is truncated");
        }

        int bodyLength = packet.length - FOOTER_SIZE;
        CRC32 crc = new CRC32();
        crc.update(packet, 0, bodyLength);
        int storedCrc = java.nio.ByteBuffer.wrap(packet, bodyLength, FOOTER_SIZE)
                .getInt();
        if (((int) crc.getValue()) != storedCrc) {
            throw corruption("integrity manifest checksum mismatch");
        }

        Map<Key, Entry> global = new TreeMap<Key, Entry>();
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength))) {
            if (input.readInt() != MAGIC) {
                throw corruption("invalid integrity manifest magic");
            }
            if (input.readShort() != VERSION) {
                throw corruption("unsupported integrity manifest version");
            }
            int count = input.readInt();
            int maximum = Math.max(0, (bodyLength - HEADER_SIZE) / ENTRY_SIZE);
            if (count < 0 || count > maximum) {
                throw corruption("invalid integrity manifest entry count=" + count);
            }

            Key previous = null;
            for (int i = 0; i < count; ++i) {
                int entryBaseCode = input.readInt();
                long id = input.readLong();
                int length = input.readInt();
                int recordCrc = input.readInt();
                Key key = new Key(entryBaseCode, id);
                if (entryBaseCode <= 0 || entryBaseCode > 0x0F
                        || id < 0L || length <= 0
                        || (previous != null && key.compareTo(previous) <= 0)) {
                    throw corruption("invalid integrity entry base=" + entryBaseCode
                            + " id=" + id + " length=" + length);
                }
                global.put(key, new Entry(length, recordCrc));
                previous = key;
            }
            if (input.available() != 0) {
                throw corruption("unexpected bytes in integrity manifest");
            }
        } catch (IOException malformed) {
            throw corruption("cannot parse integrity manifest: " + malformed);
        }
        return global;
    }

    private void writeGlobal(Map<Key, Entry> global) throws Exception {
        byte[] packet = pack(global);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Path directory = parent == null
                ? new File(".").toPath().toAbsolutePath()
                : parent.toPath();
        Path temporary = Files.createTempFile(directory,
                file.getName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(packet);
                output.flush();
                output.getFD().sync();
            }
            try {
                Files.move(temporary, file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private byte[] pack(Map<Key, Entry> global) throws Exception {
        TreeMap<Key, Entry> ordered = new TreeMap<Key, Entry>(global);
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        try (DataOutputStream body = new DataOutputStream(bodyBytes)) {
            body.writeInt(MAGIC);
            body.writeShort(VERSION);
            body.writeInt(ordered.size());
            for (Map.Entry<Key, Entry> one : ordered.entrySet()) {
                body.writeInt(one.getKey().baseCode);
                body.writeLong(one.getKey().id);
                body.writeInt(one.getValue().length);
                body.writeInt(one.getValue().crc32);
            }
        }

        byte[] payload = bodyBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteArrayOutputStream packetBytes = new ByteArrayOutputStream(
                payload.length + FOOTER_SIZE);
        packetBytes.write(payload);
        try (DataOutputStream packet = new DataOutputStream(packetBytes)) {
            packet.writeInt((int) crc.getValue());
        }
        return packetBytes.toByteArray();
    }

    private static DatabaseErrorException corruption(String detail) {
        return new DatabaseErrorException("DUMB storage corruption: " + detail);
    }

    private static final class Key implements Comparable<Key> {
        private final int baseCode;
        private final long id;

        private Key(int baseCode, long id) {
            this.baseCode = baseCode;
            this.id = id;
        }

        @Override
        public int compareTo(Key other) {
            int baseComparison = Integer.compare(baseCode, other.baseCode);
            return baseComparison != 0 ? baseComparison : Long.compare(id, other.id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return baseCode == key.baseCode && id == key.id;
        }

        @Override
        public int hashCode() {
            return 31 * baseCode + (int) (id ^ (id >>> 32));
        }
    }

    private static final class Entry {
        private final int length;
        private final int crc32;

        private Entry(int length, int crc32) {
            this.length = length;
            this.crc32 = crc32;
        }

        private static Entry fromStep(IStep step) throws Exception {
            byte[] packed = step.pack().getBuffer();
            return fromBytes(packed);
        }

        private static Entry fromStored(File store, long offset) throws Exception {
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
                        || dataSize > blockSize
                        || dataSize > Integer.MAX_VALUE
                        || blockSize > available) {
                    throw corruption("invalid store block offset=" + offset
                            + " blockSize=" + blockSize + " dataSize=" + dataSize
                            + " available=" + available);
                }
                byte[] packed = new byte[(int) dataSize];
                input.readFully(packed);
                return fromBytes(packed);
            }
        }

        private static Entry fromBytes(byte[] packed) {
            CRC32 crc = new CRC32();
            crc.update(packed);
            return new Entry(packed.length, (int) crc.getValue());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Entry)) {
                return false;
            }
            Entry entry = (Entry) other;
            return length == entry.length && crc32 == entry.crc32;
        }

        @Override
        public int hashCode() {
            return 31 * length + crc32;
        }
    }
}
