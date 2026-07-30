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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Checksummed integrity state for one physical DUMB index/store pair.
 *
 * <p>The KDI2 snapshot remains the compact baseline. Completed flushes append
 * small checksummed KDD1 delta frames instead of rewriting the complete global
 * manifest. Normal close compacts the snapshot and removes the delta file.
 * Several logical bases may safely append independent frames because all of
 * them share the storage-wide locker.</p>
 */
final class IntegrityManifest {

    static final String BOOTSTRAP_PROPERTY = "kanger.dumb.integrity.bootstrap";

    private static final int SNAPSHOT_MAGIC = 0x4B444932; // KDI2
    private static final short SNAPSHOT_VERSION = 2;
    private static final int SNAPSHOT_HEADER_SIZE = Integer.BYTES + Short.BYTES
            + Integer.BYTES;
    private static final int SNAPSHOT_ENTRY_SIZE = Integer.BYTES + Long.BYTES
            + Integer.BYTES + Integer.BYTES;
    private static final int SNAPSHOT_FOOTER_SIZE = Integer.BYTES;

    private static final int DELTA_MAGIC = 0x4B444431; // KDD1
    private static final short DELTA_VERSION = 1;
    private static final int DELTA_HEADER_SIZE = Integer.BYTES + Short.BYTES
            + Integer.BYTES;
    private static final int DELTA_BODY_HEADER_SIZE = Integer.BYTES + Integer.BYTES;
    private static final int DELTA_CHANGE_SIZE = Byte.BYTES + Long.BYTES
            + Integer.BYTES + Integer.BYTES;
    private static final int DELTA_FOOTER_SIZE = Integer.BYTES;
    private static final int MAX_DELTA_BODY_SIZE = 64 * 1024 * 1024;
    private static final byte PUT = 1;
    private static final byte DELETE = 2;

    private static final int STORE_HEADER_SIZE = Short.BYTES + Integer.BYTES;
    private static final int STORE_BLOCK_HEADER_SIZE = Long.BYTES * 2;

    private final File file;
    private final File deltaFile;
    private final int baseCode;
    private final Object locker;
    private final Map<Long, Entry> entries = new LinkedHashMap<Long, Entry>();
    private final Map<Long, Change> pending = new LinkedHashMap<Long, Change>();

    IntegrityManifest(String fileName, int baseCode, Object locker) {
        this.file = new File(fileName);
        this.deltaFile = new File(fileName + ".delta");
        this.baseCode = baseCode;
        this.locker = locker;
    }

    void openOrBootstrap(Index index, Data data) throws Exception {
        synchronized (locker) {
            if (file.exists()) {
                Map<Key, Entry> global = loadGlobal(false);
                selectLocal(global);
                if (entries.isEmpty() && !index.isEmpty()) {
                    requireExplicitBootstrap("manifest has no protected subset for base="
                            + baseCode);
                    bootstrap(index, data);
                    replaceLocal(global);
                    writeSnapshot(global);
                    Files.deleteIfExists(deltaFile.toPath());
                } else {
                    validate(index, data);
                }
            } else {
                if (deltaFile.exists()) {
                    throw corruption("integrity delta exists without snapshot");
                }
                if (!index.isEmpty()) {
                    requireExplicitBootstrap(
                            "integrity manifest is missing for non-empty storage");
                }
                bootstrap(index, data);
                Map<Key, Entry> global = new TreeMap<Key, Entry>();
                addLocal(global);
                writeSnapshot(global);
            }
            pending.clear();
        }
    }

    void put(IStep step) throws Exception {
        if (step == null || step.getId() < 0L) {
            throw corruption("cannot register null or negative-id record");
        }
        synchronized (locker) {
            Long id = Long.valueOf(step.getId());
            Entry next = Entry.fromStep(step);
            Entry previous = entries.put(id, next);
            if (!next.equals(previous)) {
                pending.put(id, Change.put(next));
            }
        }
    }

    void remove(long id) {
        synchronized (locker) {
            Long key = Long.valueOf(id);
            if (entries.remove(key) != null) {
                pending.put(key, Change.delete());
            }
        }
    }

    void clear() {
        synchronized (locker) {
            for (Long id : entries.keySet()) {
                pending.put(id, Change.delete());
            }
            entries.clear();
        }
    }

    void flush() throws Exception {
        synchronized (locker) {
            if (!file.exists()) {
                throw corruption("integrity manifest disappeared after initialization");
            }
            if (pending.isEmpty()) {
                return;
            }
            appendDelta(baseCode, pending);
            pending.clear();
        }
    }

    /** Compact all committed frames into one atomic KDI2 snapshot. */
    void compact() throws Exception {
        synchronized (locker) {
            flush();
            Map<Key, Entry> global = loadGlobal(false);
            writeSnapshot(global);
            Files.deleteIfExists(deltaFile.toPath());
        }
    }

    /**
     * Replace one logical-base subset after UNDO recovery and compact the
     * shared integrity state. A truncated final delta frame is safe here:
     * recovery is entered only while the WAL still proves that the last flush
     * was not committed.
     */
    static void recoverSubset(String fileName, int baseCode, Index index,
                              Data data, Object locker) throws Exception {
        synchronized (locker) {
            IntegrityManifest manifest = new IntegrityManifest(
                    fileName, baseCode, locker);
            if (!manifest.file.exists()) {
                throw corruption("integrity manifest is missing during recovery");
            }
            Map<Key, Entry> global = manifest.loadGlobal(true);
            manifest.removeBase(global);
            if (!index.isEmpty()) {
                Iterator<Index.IndexOne> iterator = index.iterator();
                if (iterator == null) {
                    throw corruption("cannot enumerate index during recovery");
                }
                while (iterator.hasNext()) {
                    Index.IndexOne one = iterator.next();
                    if (one == null) {
                        throw corruption("null index record during recovery");
                    }
                    Key key = new Key(baseCode, one.getId());
                    Entry entry = Entry.fromStored(data.getFile(), one.getLong());
                    manifest.readExpected(data, one.getId(), one.getLong());
                    if (global.put(key, entry) != null) {
                        throw corruption("duplicate recovered key base=" + baseCode
                                + " id=" + one.getId());
                    }
                }
            }
            manifest.writeSnapshot(global);
            Files.deleteIfExists(manifest.deltaFile.toPath());
        }
    }

    private void requireExplicitBootstrap(String detail)
            throws DatabaseErrorException {
        if (!Boolean.getBoolean(BOOTSTRAP_PROPERTY)) {
            throw corruption(detail + "; explicit one-time migration requires -D"
                    + BOOTSTRAP_PROPERTY + "=true");
        }
    }

    private void replaceLocal(Map<Key, Entry> global) {
        removeBase(global);
        addLocal(global);
    }

    private void removeBase(Map<Key, Entry> global) {
        Iterator<Key> iterator = global.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().baseCode == baseCode) {
                iterator.remove();
            }
        }
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
                Entry stored = Entry.fromStored(data.getFile(), one.getLong());
                readExpected(data, one.getId(), one.getLong());
                if (entries.put(Long.valueOf(one.getId()), stored) != null) {
                    throw corruption("duplicate index id=" + one.getId()
                            + " base=" + baseCode);
                }
            }
        }
    }

    private void validate(Index index, Data data) throws Exception {
        for (Map.Entry<Long, Entry> expected : entries.entrySet()) {
            long id = expected.getKey().longValue();
            Index.IndexOne one = index.getOne(id);
            if (one == null) {
                throw corruption("manifest id is absent from index base="
                        + baseCode + " id=" + id);
            }
            Entry actual = Entry.fromStored(data.getFile(), one.getLong());
            if (!expected.getValue().equals(actual)) {
                throw corruption("record checksum mismatch base="
                        + baseCode + " id=" + id);
            }
            readExpected(data, id, one.getLong());
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

    private IStep readExpected(Data data, long expectedId, long offset)
            throws Exception {
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

    private Map<Key, Entry> loadGlobal(boolean tolerateTruncatedDeltaTail)
            throws Exception {
        Map<Key, Entry> global = loadSnapshot();
        applyDelta(global, tolerateTruncatedDeltaTail);
        return global;
    }

    private Map<Key, Entry> loadSnapshot() throws Exception {
        byte[] packet = Files.readAllBytes(file.toPath());
        if (packet.length < SNAPSHOT_HEADER_SIZE + SNAPSHOT_FOOTER_SIZE) {
            throw corruption("integrity manifest is truncated");
        }

        int bodyLength = packet.length - SNAPSHOT_FOOTER_SIZE;
        CRC32 crc = new CRC32();
        crc.update(packet, 0, bodyLength);
        int storedCrc = java.nio.ByteBuffer.wrap(packet, bodyLength,
                SNAPSHOT_FOOTER_SIZE).getInt();
        if (((int) crc.getValue()) != storedCrc) {
            throw corruption("integrity manifest checksum mismatch");
        }

        Map<Key, Entry> global = new TreeMap<Key, Entry>();
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength))) {
            if (input.readInt() != SNAPSHOT_MAGIC) {
                throw corruption("invalid integrity manifest magic");
            }
            if (input.readShort() != SNAPSHOT_VERSION) {
                throw corruption("unsupported integrity manifest version");
            }
            int count = input.readInt();
            int maximum = Math.max(0,
                    (bodyLength - SNAPSHOT_HEADER_SIZE) / SNAPSHOT_ENTRY_SIZE);
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

    private void applyDelta(Map<Key, Entry> global,
                            boolean tolerateTruncatedTail) throws Exception {
        if (!deltaFile.exists() || deltaFile.length() == 0L) {
            return;
        }
        try (RandomAccessFile input = new RandomAccessFile(deltaFile, "r")) {
            while (input.getFilePointer() < input.length()) {
                long frameOffset = input.getFilePointer();
                long remaining = input.length() - frameOffset;
                if (remaining < DELTA_HEADER_SIZE) {
                    if (tolerateTruncatedTail) {
                        break;
                    }
                    throw corruption("truncated integrity delta header offset="
                            + frameOffset);
                }
                if (input.readInt() != DELTA_MAGIC) {
                    throw corruption("invalid integrity delta magic offset="
                            + frameOffset);
                }
                if (input.readShort() != DELTA_VERSION) {
                    throw corruption("unsupported integrity delta version offset="
                            + frameOffset);
                }
                int bodyLength = input.readInt();
                if (bodyLength < DELTA_BODY_HEADER_SIZE
                        || bodyLength > MAX_DELTA_BODY_SIZE) {
                    throw corruption("invalid integrity delta length=" + bodyLength
                            + " offset=" + frameOffset);
                }
                if (input.length() - input.getFilePointer()
                        < (long) bodyLength + DELTA_FOOTER_SIZE) {
                    if (tolerateTruncatedTail) {
                        break;
                    }
                    throw corruption("truncated integrity delta body offset="
                            + frameOffset);
                }
                byte[] body = new byte[bodyLength];
                input.readFully(body);
                int storedCrc = input.readInt();
                CRC32 crc = new CRC32();
                crc.update(body);
                if (((int) crc.getValue()) != storedCrc) {
                    throw corruption("integrity delta checksum mismatch offset="
                            + frameOffset);
                }
                applyDeltaBody(global, body, frameOffset);
            }
        }
    }

    private void applyDeltaBody(Map<Key, Entry> global, byte[] body,
                                long frameOffset) throws Exception {
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(body))) {
            int frameBaseCode = input.readInt();
            int count = input.readInt();
            int maximum = Math.max(0,
                    (body.length - DELTA_BODY_HEADER_SIZE) / DELTA_CHANGE_SIZE);
            if (frameBaseCode <= 0 || frameBaseCode > 0x0F
                    || count <= 0 || count > maximum) {
                throw corruption("invalid integrity delta frame base="
                        + frameBaseCode + " count=" + count
                        + " offset=" + frameOffset);
            }
            Set<Long> seen = new HashSet<Long>();
            for (int i = 0; i < count; ++i) {
                byte operation = input.readByte();
                long id = input.readLong();
                int length = input.readInt();
                int recordCrc = input.readInt();
                if (id < 0L || !seen.add(Long.valueOf(id))) {
                    throw corruption("invalid or duplicate integrity delta id=" + id
                            + " offset=" + frameOffset);
                }
                Key key = new Key(frameBaseCode, id);
                if (operation == PUT) {
                    if (length <= 0) {
                        throw corruption("invalid integrity delta put length="
                                + length + " id=" + id);
                    }
                    global.put(key, new Entry(length, recordCrc));
                } else if (operation == DELETE) {
                    if (length != 0 || recordCrc != 0) {
                        throw corruption("invalid integrity delta delete id=" + id);
                    }
                    global.remove(key);
                } else {
                    throw corruption("invalid integrity delta operation="
                            + operation + " id=" + id);
                }
            }
            if (input.available() != 0) {
                throw corruption("unexpected bytes in integrity delta frame offset="
                        + frameOffset);
            }
        } catch (IOException malformed) {
            throw corruption("cannot parse integrity delta frame: " + malformed);
        }
    }

    private void appendDelta(int frameBaseCode, Map<Long, Change> changes)
            throws Exception {
        byte[] body = packDeltaBody(frameBaseCode, changes);
        CRC32 crc = new CRC32();
        crc.update(body);
        File parent = deltaFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream stream = new FileOutputStream(deltaFile, true);
             DataOutputStream output = new DataOutputStream(stream)) {
            output.writeInt(DELTA_MAGIC);
            output.writeShort(DELTA_VERSION);
            output.writeInt(body.length);
            output.write(body);
            output.writeInt((int) crc.getValue());
            output.flush();
        }
    }

    private byte[] packDeltaBody(int frameBaseCode,
                                 Map<Long, Change> changes) throws Exception {
        TreeMap<Long, Change> ordered = new TreeMap<Long, Change>(changes);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(frameBaseCode);
            output.writeInt(ordered.size());
            for (Map.Entry<Long, Change> one : ordered.entrySet()) {
                output.writeByte(one.getValue().operation);
                output.writeLong(one.getKey().longValue());
                if (one.getValue().operation == PUT) {
                    output.writeInt(one.getValue().entry.length);
                    output.writeInt(one.getValue().entry.crc32);
                } else {
                    output.writeInt(0);
                    output.writeInt(0);
                }
            }
        }
        byte[] body = bytes.toByteArray();
        if (body.length > MAX_DELTA_BODY_SIZE) {
            throw corruption("integrity delta frame is too large: " + body.length);
        }
        return body;
    }

    private void writeSnapshot(Map<Key, Entry> global) throws Exception {
        byte[] packet = packSnapshot(global);
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

    private byte[] packSnapshot(Map<Key, Entry> global) throws Exception {
        TreeMap<Key, Entry> ordered = new TreeMap<Key, Entry>(global);
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        try (DataOutputStream body = new DataOutputStream(bodyBytes)) {
            body.writeInt(SNAPSHOT_MAGIC);
            body.writeShort(SNAPSHOT_VERSION);
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
                payload.length + SNAPSHOT_FOOTER_SIZE);
        packetBytes.write(payload);
        try (DataOutputStream packet = new DataOutputStream(packetBytes)) {
            packet.writeInt((int) crc.getValue());
        }
        return packetBytes.toByteArray();
    }

    private static DatabaseErrorException corruption(String detail) {
        return new DatabaseErrorException("DUMB storage corruption: " + detail);
    }

    private static final class Change {
        private final byte operation;
        private final Entry entry;

        private Change(byte operation, Entry entry) {
            this.operation = operation;
            this.entry = entry;
        }

        private static Change put(Entry entry) {
            return new Change(PUT, entry);
        }

        private static Change delete() {
            return new Change(DELETE, null);
        }
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
            return fromBytes(step.pack().getBuffer());
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
                        || dataSize > blockSize || dataSize > Integer.MAX_VALUE
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
