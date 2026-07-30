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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Rebuilds one logical-base subset of the shared integrity manifest after undo. */
final class IntegrityRecovery {

    private static final int MAGIC = 0x4B444932; // KDI2
    private static final short VERSION = 2;
    private static final int HEADER_SIZE = Integer.BYTES + Short.BYTES
            + Integer.BYTES;
    private static final int ENTRY_SIZE = Integer.BYTES + Long.BYTES
            + Integer.BYTES + Integer.BYTES;
    private static final int FOOTER_SIZE = Integer.BYTES;
    private static final int STORE_HEADER_SIZE = Short.BYTES + Integer.BYTES;
    private static final int STORE_BLOCK_HEADER_SIZE = Long.BYTES * 2;

    private IntegrityRecovery() {
    }

    static void rebuild(String fileName, int baseCode, Index index,
                        Data data, Object locker) throws Exception {
        synchronized (locker) {
            File file = new File(fileName);
            if (!file.exists()) {
                throw corruption("integrity manifest is missing during recovery");
            }
            Map<Key, Entry> global = load(file);
            Iterator<Key> keys = global.keySet().iterator();
            while (keys.hasNext()) {
                if (keys.next().baseCode == baseCode) {
                    keys.remove();
                }
            }

            if (!index.isEmpty()) {
                Iterator<Index.IndexOne> iterator = index.iterator();
                if (iterator == null) {
                    throw corruption("cannot enumerate index after recovery");
                }
                while (iterator.hasNext()) {
                    Index.IndexOne one = iterator.next();
                    if (one == null) {
                        throw corruption("null index record after recovery");
                    }
                    Key key = new Key(baseCode, one.getId());
                    if (global.put(key,
                            Entry.fromStored(data.getFile(), one.getLong())) != null) {
                        throw corruption("duplicate recovered key base=" + baseCode
                                + " id=" + one.getId());
                    }
                }
            }
            write(file, global);
        }
    }

    private static Map<Key, Entry> load(File file) throws Exception {
        byte[] packet = Files.readAllBytes(file.toPath());
        if (packet.length < HEADER_SIZE + FOOTER_SIZE) {
            throw corruption("integrity manifest is truncated during recovery");
        }

        int bodyLength = packet.length - FOOTER_SIZE;
        CRC32 crc = new CRC32();
        crc.update(packet, 0, bodyLength);
        int storedCrc = java.nio.ByteBuffer.wrap(packet, bodyLength, FOOTER_SIZE)
                .getInt();
        if (((int) crc.getValue()) != storedCrc) {
            throw corruption("integrity manifest checksum mismatch during recovery");
        }

        Map<Key, Entry> global = new TreeMap<Key, Entry>();
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength))) {
            if (input.readInt() != MAGIC) {
                throw corruption("invalid integrity manifest magic during recovery");
            }
            if (input.readShort() != VERSION) {
                throw corruption("unsupported integrity manifest version during recovery");
            }
            int count = input.readInt();
            int maximum = Math.max(0, (bodyLength - HEADER_SIZE) / ENTRY_SIZE);
            if (count < 0 || count > maximum) {
                throw corruption("invalid integrity entry count=" + count);
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
                throw corruption("unexpected bytes in integrity manifest during recovery");
            }
        } catch (IOException malformed) {
            throw corruption("cannot parse integrity manifest during recovery: "
                    + malformed);
        }
        return global;
    }

    private static void write(File file, Map<Key, Entry> global) throws Exception {
        byte[] packet = pack(global);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Path directory = parent == null
                ? new File(".").toPath().toAbsolutePath()
                : parent.toPath();
        Path temporary = Files.createTempFile(directory,
                file.getName() + ".recovery.", ".tmp");
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

    private static byte[] pack(Map<Key, Entry> global) throws Exception {
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
        return new DatabaseErrorException("DUMB recovery error: " + detail);
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
    }

    private static final class Entry {
        private final int length;
        private final int crc32;

        private Entry(int length, int crc32) {
            this.length = length;
            this.crc32 = crc32;
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
                    throw corruption("invalid recovered store block offset=" + offset
                            + " blockSize=" + blockSize + " dataSize=" + dataSize
                            + " available=" + available);
                }
                byte[] packed = new byte[(int) dataSize];
                input.readFully(packed);
                CRC32 crc = new CRC32();
                crc.update(packed);
                return new Entry(packed.length, (int) crc.getValue());
            }
        }
    }
}
