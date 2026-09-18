/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.Sapato;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Schema-local physical address space owned by one DUMB 2.0 Context.
 *
 * <p>The base deliberately reuses the core {@link Sapato} codec and the
 * existing {@link IBase} contract. Mutations change only the Context-owned
 * working image. They become durable only when {@link ContextStore#flush()}
 * publishes a complete next Context revision.</p>
 *
 * <p>One immutable snapshot file contains the whole schema image for a
 * revision. This correctness-first representation removes the old acquisition
 * order {@code baseCode}; the stable schema descriptor selects the physical
 * namespace directly. A later optimization may replace the full-image file
 * without changing the Context publication contract.</p>
 */
final class ContextBase implements IBase {

    private static final int MAGIC = 0x4B334232; // K3B2
    private static final int VERSION = 1;
    private static final int CRC_SIZE = 4;

    private final ContextStore owner;
    private final String schema;
    private final TreeMap<Long, byte[]> records = new TreeMap<Long, byte[]>();

    private long nextId;
    private boolean dirty;
    private boolean closed;

    ContextBase(ContextStore owner, String schema) throws Exception {
        this.owner = owner;
        this.schema = schema;
        load(owner.schemaPath(schema));
    }

    boolean isDirty() {
        return dirty;
    }

    void markPublished() {
        dirty = false;
    }

    void closeFromOwner() {
        closed = true;
        records.clear();
    }

    void validateWorkingState() throws Exception {
        resolveEndpoints();
    }

    void writeSnapshot(Path generation) throws Exception {
        requireOpen();
        long[] endpoints = resolveEndpoints();

        byte[] schemaBytes = schema.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        payload.writeInt(MAGIC);
        payload.writeInt(VERSION);
        payload.writeInt(schemaBytes.length);
        payload.write(schemaBytes);
        payload.writeLong(endpoints[0]);
        payload.writeLong(endpoints[1]);
        payload.writeInt(records.size());
        for (Map.Entry<Long, byte[]> entry : records.entrySet()) {
            payload.writeLong(entry.getKey().longValue());
            payload.writeInt(entry.getValue().length);
            payload.write(entry.getValue());
        }
        payload.flush();

        byte[] body = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body);

        Path path = owner.schemaPath(generation, schema);
        Files.createDirectories(path.getParent());
        try (FileOutputStream file = new FileOutputStream(path.toFile());
             DataOutputStream output = new DataOutputStream(file)) {
            output.write(body);
            output.writeInt((int) crc.getValue());
            output.flush();
            file.getFD().sync();
        }
    }

    private void load(Path path) throws Exception {
        if (!Files.exists(path)) {
            nextId = 0L;
            dirty = false;
            return;
        }

        byte[] packet = Files.readAllBytes(path);
        if (packet.length < 4 * 5 + 8 * 2 + CRC_SIZE) {
            throw corruption("DUMB2 schema snapshot is truncated: " + path);
        }

        int bodyLength = packet.length - CRC_SIZE;
        CRC32 crc = new CRC32();
        crc.update(packet, 0, bodyLength);
        int storedCrc = java.nio.ByteBuffer.wrap(packet, bodyLength, CRC_SIZE).getInt();
        if (((int) crc.getValue()) != storedCrc) {
            throw corruption("DUMB2 schema snapshot checksum mismatch: " + path);
        }

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength))) {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != MAGIC || version != VERSION) {
                throw new StorageLifecycleException(
                        StorageLifecycleErrorCode.STORAGE_FORMAT_INCOMPATIBLE,
                        "Unsupported DUMB2 schema snapshot format at " + path);
            }

            int schemaLength = input.readInt();
            if (schemaLength < 0 || schemaLength > input.available()) {
                throw corruption("Invalid DUMB2 schema descriptor length at " + path);
            }
            byte[] schemaBytes = new byte[schemaLength];
            input.readFully(schemaBytes);
            String storedSchema = new String(schemaBytes, StandardCharsets.UTF_8);
            if (!schema.equals(storedSchema)) {
                throw corruption("DUMB2 schema descriptor mismatch at " + path
                        + ": expected=" + schema + " actual=" + storedSchema);
            }

            long storedRoot = input.readLong();
            long storedTop = input.readLong();
            int count = input.readInt();
            if (count < 0) {
                throw corruption("Negative DUMB2 schema record count at " + path);
            }

            long maximumId = -1L;
            for (int i = 0; i < count; ++i) {
                long id = input.readLong();
                int length = input.readInt();
                if (id < 0L || length <= 0 || length > input.available()) {
                    throw corruption("Invalid DUMB2 schema record at " + path);
                }
                byte[] packed = new byte[length];
                input.readFully(packed);
                StepHeader header = header(packed);
                if (header.id != id) {
                    throw corruption("DUMB2 schema record id mismatch at " + path);
                }
                if (records.put(Long.valueOf(id), packed) != null) {
                    throw corruption("Duplicate DUMB2 schema record id " + id + " at " + path);
                }
                maximumId = Math.max(maximumId, id);
            }
            if (input.available() != 0) {
                throw corruption("Trailing bytes in DUMB2 schema snapshot at " + path);
            }

            long[] endpoints = resolveEndpoints();
            if (endpoints[0] != storedRoot || endpoints[1] != storedTop) {
                throw corruption("DUMB2 schema endpoint mismatch at " + path);
            }
            nextId = maximumId < 0L ? 0L : maximumId + 1L;
            dirty = false;
        } catch (StorageLifecycleException failure) {
            throw failure;
        } catch (Exception failure) {
            StorageLifecycleException wrapped = corruption(
                    "Cannot decode DUMB2 schema snapshot at " + path);
            wrapped.addSuppressed(failure);
            throw wrapped;
        }
    }

    @Override
    public synchronized void add(IStep one) throws Exception {
        requireOpen();
        put(one);
    }

    @Override
    public synchronized void update(IStep one) throws Exception {
        requireOpen();
        put(one);
    }

    private void put(IStep one) throws Exception {
        if (one == null || one.getId() < 0L || one.pack() == null) {
            throw new IllegalArgumentException("DUMB2 persistent step must have a non-negative id");
        }
        byte[] packed = one.pack().getBuffer();
        StepHeader header = header(packed);
        if (header.id != one.getId()) {
            throw corruption("DUMB2 packed step id differs from its operational id");
        }
        records.put(Long.valueOf(one.getId()), packed);
        nextId = Math.max(nextId, one.getId() + 1L);
        dirty = true;
    }

    @Override
    public synchronized IStep get(long id) throws Exception {
        requireOpen();
        byte[] packed = records.get(Long.valueOf(id));
        return packed == null ? null : decode(packed);
    }

    private IStep decode(byte[] packed) throws Exception {
        org.kanger.storage.ByteBuffer packet = new org.kanger.storage.ByteBuffer(packed);
        packet.mark();
        Sapato step = new Sapato(this);
        step.apply(packet);
        step.setSize(packed.length);
        return step;
    }

    @Override
    public void clearCache() {
        // Full-image M1 representation has no hydration cache.
    }

    @Override
    public synchronized boolean isEmpty() {
        return records.isEmpty();
    }

    @Override
    public synchronized void delete(long id) {
        if (records.remove(Long.valueOf(id)) != null) {
            dirty = true;
        }
    }

    @Override
    public synchronized void deleteAll(Collection<Long> ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id != null && records.remove(id) != null) {
                dirty = true;
            }
        }
    }

    @Override
    public synchronized void clear() {
        if (!records.isEmpty()) {
            records.clear();
            dirty = true;
        }
        nextId = 0L;
    }

    @Override
    public synchronized void reindex(IBase to, IMind mind) throws Exception {
        requireOpen();
        for (Long id : records.keySet()) {
            IStep stored = get(id.longValue());
            stored.getData((Mind) mind);
            to.add(stored);
        }
    }

    @Override
    public synchronized boolean containsKey(long id) {
        return records.containsKey(Long.valueOf(id));
    }

    @Override
    public synchronized IStep getRoot() {
        try {
            long root = resolveEndpoints()[0];
            return root < 0L ? null : get(root);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot resolve DUMB2 schema root " + schema, failure);
        }
    }

    @Override
    public synchronized IStep getTop() {
        try {
            long top = resolveEndpoints()[1];
            return top < 0L ? null : get(top);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot resolve DUMB2 schema top " + schema, failure);
        }
    }

    private long[] resolveEndpoints() throws Exception {
        if (records.isEmpty()) {
            return new long[]{-1L, -1L};
        }

        Set<Long> referenced = new HashSet<Long>();
        TreeMap<Long, Long> nextById = new TreeMap<Long, Long>();
        for (Map.Entry<Long, byte[]> entry : records.entrySet()) {
            StepHeader header = header(entry.getValue());
            if (header.id != entry.getKey().longValue()) {
                throw corruption("DUMB2 schema key/id mismatch in " + schema);
            }
            nextById.put(entry.getKey(), Long.valueOf(header.nextId));
            if (header.nextId >= 0L) {
                Long next = Long.valueOf(header.nextId);
                if (!records.containsKey(next)) {
                    throw corruption("DUMB2 schema " + schema
                            + " references missing next id " + header.nextId);
                }
                if (!referenced.add(next)) {
                    throw corruption("DUMB2 schema " + schema
                            + " contains multiple predecessors for id " + header.nextId);
                }
            }
        }

        Long root = null;
        for (Long id : records.keySet()) {
            if (!referenced.contains(id)) {
                if (root != null) {
                    throw corruption("DUMB2 schema " + schema + " contains multiple roots");
                }
                root = id;
            }
        }
        if (root == null) {
            throw corruption("DUMB2 schema " + schema + " contains a cycle");
        }

        Set<Long> visited = new HashSet<Long>();
        Long current = root;
        Long top = null;
        while (current != null && current.longValue() >= 0L) {
            if (!visited.add(current)) {
                throw corruption("DUMB2 schema " + schema + " contains a cycle");
            }
            top = current;
            Long next = nextById.get(current);
            current = next == null || next.longValue() < 0L ? null : next;
        }
        if (visited.size() != records.size()) {
            throw corruption("DUMB2 schema " + schema + " contains disconnected records");
        }
        return new long[]{root.longValue(), top.longValue()};
    }

    private static StepHeader header(byte[] packed) throws Exception {
        org.kanger.storage.ByteBuffer packet = new org.kanger.storage.ByteBuffer(packed);
        packet.mark();
        long id = packet.getLong();
        packet.getInt();
        long next = packet.getLong();
        return new StepHeader(id, next);
    }

    @Override
    public String getName() {
        return schema;
    }

    @Override
    public long getUsedCacheSize() {
        long bytes = 0L;
        for (byte[] packed : records.values()) {
            bytes += packed.length;
        }
        return bytes;
    }

    @Override
    public long getMaxCacheSize() {
        return 0L;
    }

    @Override
    public synchronized long lastId() {
        return nextId;
    }

    @Override
    public synchronized long nextId() {
        return nextId++;
    }

    @Override
    public void flush() throws Exception {
        owner.flush();
    }

    @Override
    public void close() throws Exception {
        owner.close();
    }

    @Override
    public Class getUdf() {
        return null;
    }

    private void requireOpen() {
        if (closed || owner.isClosed()) {
            throw new IllegalStateException("DUMB2 schema base is closed: " + schema);
        }
    }

    private static StorageLifecycleException corruption(String message) {
        return new StorageLifecycleException(
                StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION, message);
    }

    private static final class StepHeader {
        private final long id;
        private final long nextId;

        private StepHeader(long id, long nextId) {
            this.id = id;
            this.nextId = nextId;
        }
    }
}
