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
 * Checksummed manifest for the logical contents of one DUMB base.
 *
 * <p>The manifest deliberately remains separate from the historical index and
 * store formats. A completed flush writes index and store first and publishes
 * the manifest last through an atomic file replacement. Therefore an
 * interrupted write can leave either the previous complete manifest or the new
 * complete manifest, while a mismatch is rejected explicitly on reopen.</p>
 */
final class IntegrityManifest {

    private static final int MAGIC = 0x4B444931; // KDI1
    private static final short VERSION = 1;
    private static final int HEADER_SIZE = Integer.BYTES + Short.BYTES
            + Integer.BYTES + Integer.BYTES;
    private static final int ENTRY_SIZE = Long.BYTES + Integer.BYTES + Integer.BYTES;
    private static final int FOOTER_SIZE = Integer.BYTES;

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
                load();
                validate(index, data);
            } else {
                bootstrap(index, data);
                flush();
            }
        }
    }

    void put(IStep step) throws Exception {
        if (step == null || step.getId() < 0L) {
            throw corruption("cannot register null or negative-id record");
        }
        synchronized (locker) {
            Entry next = Entry.from(step);
            Entry previous = entries.put(step.getId(), next);
            changed = !next.equals(previous) || changed;
        }
    }

    void remove(long id) {
        synchronized (locker) {
            if (entries.remove(id) != null) {
                changed = true;
            }
        }
    }

    void clear() {
        synchronized (locker) {
            if (!entries.isEmpty()) {
                entries.clear();
            }
            changed = true;
        }
    }

    void flush() throws Exception {
        synchronized (locker) {
            if (!changed && file.exists()) {
                return;
            }

            byte[] packet = pack();
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
                changed = false;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temporary);
                }
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
                IStep step = readExpected(data, one.getId(), one.getLong());
                if (entries.put(one.getId(), Entry.from(step)) != null) {
                    throw corruption("duplicate index id=" + one.getId());
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
                throw corruption("manifest id is absent from index: " + id);
            }
            IStep step = readExpected(data, id, one.getLong());
            Entry actual = Entry.from(step);
            if (!expected.getValue().equals(actual)) {
                throw corruption("record checksum mismatch id=" + id);
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
                if (!entries.containsKey(one.getId())) {
                    throw corruption("index id is absent from manifest: " + one.getId());
                }
                ++seen;
            }
        }
        if (seen != entries.size()) {
            throw corruption("index/manifest cardinality mismatch index=" + seen
                    + " manifest=" + entries.size());
        }
    }

    private IStep readExpected(Data data, long expectedId, long offset) throws Exception {
        IStep step = data.getUncached(offset);
        if (step == null) {
            throw corruption("index points to missing store record id=" + expectedId
                    + " offset=" + offset);
        }
        if (step.getId() != expectedId) {
            throw corruption("index/store id mismatch expected=" + expectedId
                    + " actual=" + step.getId() + " offset=" + offset);
        }
        return step;
    }

    private byte[] pack() throws Exception {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        try (DataOutputStream body = new DataOutputStream(bodyBytes)) {
            body.writeInt(MAGIC);
            body.writeShort(VERSION);
            body.writeInt(baseCode);
            TreeMap<Long, Entry> ordered = new TreeMap<Long, Entry>(entries);
            body.writeInt(ordered.size());
            for (Map.Entry<Long, Entry> one : ordered.entrySet()) {
                body.writeLong(one.getKey().longValue());
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

    private void load() throws Exception {
        byte[] packet = Files.readAllBytes(file.toPath());
        if (packet.length < HEADER_SIZE + FOOTER_SIZE) {
            throw corruption("integrity manifest is truncated");
        }

        int bodyLength = packet.length - FOOTER_SIZE;
        CRC32 crc = new CRC32();
        crc.update(packet, 0, bodyLength);
        int storedCrc = java.nio.ByteBuffer.wrap(packet, bodyLength, FOOTER_SIZE).getInt();
        if (((int) crc.getValue()) != storedCrc) {
            throw corruption("integrity manifest checksum mismatch");
        }

        entries.clear();
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength))) {
            if (input.readInt() != MAGIC) {
                throw corruption("invalid integrity manifest magic");
            }
            if (input.readShort() != VERSION) {
                throw corruption("unsupported integrity manifest version");
            }
            int storedBaseCode = input.readInt();
            if (storedBaseCode != baseCode) {
                throw corruption("integrity manifest base mismatch expected="
                        + baseCode + " actual=" + storedBaseCode);
            }
            int count = input.readInt();
            int maximum = Math.max(0, (bodyLength - HEADER_SIZE) / ENTRY_SIZE);
            if (count < 0 || count > maximum) {
                throw corruption("invalid integrity manifest entry count=" + count);
            }
            long previous = -1L;
            for (int i = 0; i < count; ++i) {
                long id = input.readLong();
                int length = input.readInt();
                int recordCrc = input.readInt();
                if (id < 0L || id <= previous || length <= 0) {
                    throw corruption("invalid integrity entry id=" + id
                            + " length=" + length);
                }
                entries.put(Long.valueOf(id), new Entry(length, recordCrc));
                previous = id;
            }
            if (input.available() != 0) {
                throw corruption("unexpected bytes in integrity manifest");
            }
        } catch (IOException malformed) {
            throw corruption("cannot parse integrity manifest: " + malformed);
        }
        changed = false;
    }

    private static DatabaseErrorException corruption(String detail) {
        return new DatabaseErrorException("DUMB storage corruption: " + detail);
    }

    private static final class Entry {
        private final int length;
        private final int crc32;

        private Entry(int length, int crc32) {
            this.length = length;
            this.crc32 = crc32;
        }

        private static Entry from(IStep step) throws Exception {
            byte[] packed = step.pack().getBuffer();
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
