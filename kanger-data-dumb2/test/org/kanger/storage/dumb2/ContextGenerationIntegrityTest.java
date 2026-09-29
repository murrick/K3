package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.Step;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-generation physical integrity qualification for DUMB2.
 */
public class ContextGenerationIntegrityTest {

    @TempDir
    Path root;

    @Test
    void openRejectsUnknownTypeCodeInAnOtherwiseValidUnopenedBase()
            throws Exception {
        Path location = root.resolve("unknown-record-type");

        ContextStore created = ContextStore.create(location);
        try {
            IBase first = created.getBase("first");
            first.add(step(0L, 11, Long.valueOf(1L), null));

            IBase hidden = created.getBase("hidden");
            hidden.add(step(0L, 12, Long.valueOf(2L), null));

            assertEquals(1L, created.flush());
        } finally {
            created.close();
        }

        Path hiddenSnapshot = ContextStore.generationPath(location, 1L)
                .resolve("hidden.base");
        rewriteFirstRecordTypeCode(hiddenSnapshot, Integer.MAX_VALUE);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void corruptedPublishedSourceCannotBePromotedIntoNextGeneration()
            throws Exception {
        Path location = root.resolve("corrupted-source");

        ContextStore context = ContextStore.create(location);
        Path untouchedSnapshot = null;
        byte[] preservedUntouched = null;
        try {
            IBase changed = context.getBase("changed");
            changed.add(step(0L, 51, Long.valueOf(500L), null));

            IBase untouched = context.getBase("untouched");
            untouched.add(step(0L, 52, Long.valueOf(600L), null));

            assertEquals(1L, context.flush());

            untouchedSnapshot = ContextStore.generationPath(location, 1L)
                    .resolve("untouched.base");
            preservedUntouched = Files.readAllBytes(untouchedSnapshot);

            // The Context is still exclusively open, but simulate external
            // disk damage after R was published.
            byte[] damaged = preservedUntouched.clone();
            damaged[damaged.length / 2] ^= 0x01;
            Files.write(untouchedSnapshot, damaged);

            Step previous = new Step();
            previous.setId(0L);
            previous.setHash(51);
            previous.setData(Long.valueOf(500L));
            changed.add(step(1L, 53, Long.valueOf(501L), previous));

            StorageLifecycleException failure = assertThrows(
                    StorageLifecycleException.class, context::flush);
            assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                    failure.getErrorCode());
            assertEquals(1L, context.getRevision());
            assertEquals(1L, RevisionStore.read(
                    ContextStore.revisionPath(location)));
            assertFalse(Files.exists(
                    ContextStore.generationPath(location, 2L)));

            // Restore the already-published source only so close/retry can
            // complete and release lifecycle resources cleanly.
            Files.write(untouchedSnapshot, preservedUntouched);
            assertEquals(2L, context.flush());
        } finally {
            if (untouchedSnapshot != null && preservedUntouched != null
                    && Files.exists(untouchedSnapshot)) {
                // harmless if already restored; protects cleanup on assertion failure
                Files.write(untouchedSnapshot, preservedUntouched);
            }
            context.close();
        }
    }

    @Test
    void unpublishedNextGenerationIsInvisibleAndRebuiltOnRetry()
            throws Exception {
        Path location = root.resolve("orphan-next-generation");

        ContextStore created = ContextStore.create(location);
        try {
            IBase base = created.getBase("index");
            base.add(step(0L, 21, Long.valueOf(10L), null));
            assertEquals(1L, created.flush());
        } finally {
            created.close();
        }

        Path orphan = ContextStore.generationPath(location, 2L);
        Files.createDirectories(orphan);
        Path poison = orphan.resolve("poison");
        Files.write(poison, new byte[]{1, 2, 3});

        // revision marker still names R=1, therefore orphan R+1 is invisible.
        ContextStore reopened = ContextStore.open(location);
        try {
            assertEquals(1L, reopened.getRevision());
            IBase base = reopened.getBase("index");
            assertEquals(Long.valueOf(10L), base.get(0L).getData());

            Step previous = new Step();
            previous.setId(0L);
            previous.setHash(21);
            previous.setData(Long.valueOf(10L));

            Step next = step(1L, 22, Long.valueOf(20L), previous);
            base.add(next);

            assertEquals(2L, reopened.flush());
            assertFalse(Files.exists(poison),
                    "retry must replace, not adopt, an unpublished generation");
        } finally {
            reopened.close();
        }

        assertEquals(2L, RevisionStore.read(
                ContextStore.revisionPath(location)));

        ContextStore finalOpen = ContextStore.open(location);
        try {
            IBase base = finalOpen.getBase("index");
            assertEquals(Long.valueOf(10L), base.get(0L).getData());
            assertEquals(Long.valueOf(20L), base.get(1L).getData());
            assertEquals(1L, base.getRoot().getId());
            assertEquals(0L, base.getTop().getId());
        } finally {
            finalOpen.close();
        }
    }

    @Test
    void unopenedSchemaIsCopiedForwardByteForByteIntoNextGeneration()
            throws Exception {
        Path location = root.resolve("carry-forward");

        ContextStore created = ContextStore.create(location);
        try {
            IBase changed = created.getBase("changed");
            changed.add(step(0L, 41, Long.valueOf(100L), null));

            IBase untouched = created.getBase("untouched");
            untouched.add(step(0L, 42, Long.valueOf(200L), null));

            assertEquals(1L, created.flush());
        } finally {
            created.close();
        }

        Path revisionOneUntouched = ContextStore.generationPath(location, 1L)
                .resolve("untouched.base");
        byte[] originalUntouched = Files.readAllBytes(revisionOneUntouched);

        ContextStore second = ContextStore.open(location);
        try {
            // Intentionally never acquire the "untouched" base in this handle.
            IBase changed = second.getBase("changed");
            Step previous = new Step();
            previous.setId(0L);
            previous.setHash(41);
            previous.setData(Long.valueOf(100L));

            changed.add(step(1L, 43, Long.valueOf(101L), previous));
            assertEquals(2L, second.flush());
        } finally {
            second.close();
        }

        Path revisionTwoUntouched = ContextStore.generationPath(location, 2L)
                .resolve("untouched.base");
        assertTrue(Files.exists(revisionTwoUntouched));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                originalUntouched, Files.readAllBytes(revisionTwoUntouched),
                "unopened schema must be carried forward without reinterpretation");

        ContextStore finalOpen = ContextStore.open(location);
        try {
            assertEquals(Long.valueOf(200L),
                    finalOpen.getBase("untouched").get(0L).getData());
            assertEquals(Long.valueOf(101L),
                    finalOpen.getBase("changed").get(1L).getData());
        } finally {
            finalOpen.close();
        }
    }

    @Test
    void unexpectedEntryInsideVisibleGenerationIsCorruption()
            throws Exception {
        Path location = root.resolve("unexpected-visible-entry");

        ContextStore created = ContextStore.create(location);
        try {
            created.getBase("index").add(
                    step(0L, 31, Long.valueOf(30L), null));
            assertEquals(1L, created.flush());
        } finally {
            created.close();
        }

        Files.write(ContextStore.generationPath(location, 1L)
                .resolve("unexpected.tmp"), new byte[]{9});

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    private static Step step(long id, int hash, Object data, Step next) {
        Step step = new Step();
        step.setId(id);
        step.setHash(hash);
        step.setData(data);
        step.setNext(next);
        return step;
    }

    /**
     * Rewrites one PersistentRecord through its native codec, then rebuilds the
     * outer snapshot CRC. This keeps every framing/checksum valid except for the
     * deliberate manifest/typeCode inconsistency.
     */
    private static void rewriteFirstRecordTypeCode(Path snapshot, int typeCode)
            throws Exception {
        byte[] packet = Files.readAllBytes(snapshot);
        int bodyLength = packet.length - 4;

        DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(packet, 0, bodyLength));
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bodyBytes);

        output.writeInt(input.readInt()); // magic
        output.writeInt(input.readInt()); // version

        int schemaLength = input.readInt();
        output.writeInt(schemaLength);
        byte[] schema = new byte[schemaLength];
        input.readFully(schema);
        output.write(schema);

        output.writeLong(input.readLong()); // root
        output.writeLong(input.readLong()); // top

        int count = input.readInt();
        output.writeInt(count);

        boolean rewritten = false;
        for (int i = 0; i < count; ++i) {
            long id = input.readLong();
            int length = input.readInt();
            byte[] packed = new byte[length];
            input.readFully(packed);

            if (!rewritten) {
                PersistentRecord record =
                        PersistentRecordCodec.decode(packed);
                packed = PersistentRecordCodec.encode(
                        new PersistentRecord(
                                record.getId(),
                                record.getHash(),
                                record.getNextId(),
                                typeCode,
                                record.getPayload()));
                rewritten = true;
            }

            output.writeLong(id);
            output.writeInt(packed.length);
            output.write(packed);
        }
        output.flush();

        byte[] body = bodyBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body);

        ByteArrayOutputStream rewrittenBytes =
                new ByteArrayOutputStream(body.length + 4);
        rewrittenBytes.write(body);
        DataOutputStream finalOutput =
                new DataOutputStream(rewrittenBytes);
        finalOutput.writeInt((int) crc.getValue());
        finalOutput.flush();

        Files.write(snapshot, rewrittenBytes.toByteArray());
    }
}
