/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for the first DUMB 2.0 Context create/open boundary. */
public class ContextStoreTest {

    @TempDir
    Path root;

    @Test
    void createAndReopenPublishOneCoherentIdentityRevisionPair() throws Exception {
        Path location = root.resolve("reopen");

        ContextStore created = ContextStore.create(location);
        ContextStore reopened = ContextStore.open(location);

        assertEquals(created.getContextId(), reopened.getContextId());
        assertEquals(RevisionStore.INITIAL_REVISION, created.getRevision());
        assertEquals(created.getRevision(), reopened.getRevision());
        assertEquals(location, reopened.getLocation());
        assertTrue(Files.isRegularFile(ContextStore.contextPath(location)));
        assertTrue(Files.isRegularFile(ContextStore.revisionPath(location)));
    }

    @Test
    void independentContextsReceiveDistinctIdentities() throws Exception {
        ContextStore first = ContextStore.create(root.resolve("first"));
        ContextStore second = ContextStore.create(root.resolve("second"));

        assertNotEquals(first.getContextId(), second.getContextId());
        assertEquals(0L, first.getRevision());
        assertEquals(0L, second.getRevision());
    }

    @Test
    void movingCompleteMetadataPairPreservesSnapshot() throws Exception {
        Path source = root.resolve("source");
        Path target = root.resolve("target");
        ContextStore before = ContextStore.create(source);

        Files.move(ContextStore.contextPath(source), ContextStore.contextPath(target),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(ContextStore.revisionPath(source), ContextStore.revisionPath(target),
                StandardCopyOption.REPLACE_EXISTING);

        ContextStore after = ContextStore.open(target);
        assertEquals(before.getContextId(), after.getContextId());
        assertEquals(before.getRevision(), after.getRevision());
    }

    @Test
    void secondCreateNeverReplacesExistingPair() throws Exception {
        Path location = root.resolve("existing");
        ContextStore original = ContextStore.create(location);

        assertThrows(FileAlreadyExistsException.class,
                () -> ContextStore.create(location));

        ContextStore reopened = ContextStore.open(location);
        assertEquals(original.getContextId(), reopened.getContextId());
        assertEquals(original.getRevision(), reopened.getRevision());
    }

    @Test
    void absentMetadataIsStorageNotFound() {
        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(root.resolve("missing")));

        assertEquals(StorageLifecycleErrorCode.STORAGE_NOT_FOUND,
                failure.getErrorCode());
    }

    @Test
    void identityWithoutRevisionIsSemanticCorruption() throws Exception {
        Path location = root.resolve("identity-only");
        ContextIdStore.create(ContextStore.contextPath(location));

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void revisionWithoutIdentityIsSemanticCorruption() throws Exception {
        Path location = root.resolve("revision-only");
        RevisionStore.create(ContextStore.revisionPath(location));

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }

    @Test
    void failedCreateRollsBackOnlyFreshIdentitySidecar() throws Exception {
        Path location = root.resolve("occupied-revision");
        Path revisionPath = ContextStore.revisionPath(location);
        RevisionStore.create(revisionPath);
        long preservedRevision = RevisionStore.advance(revisionPath, 0L);

        assertThrows(FileAlreadyExistsException.class,
                () -> ContextStore.create(location));

        assertFalse(Files.exists(ContextStore.contextPath(location)));
        assertEquals(preservedRevision, RevisionStore.read(revisionPath));
    }

    @Test
    void codecCorruptionPropagatesThroughContextOpen() throws Exception {
        Path location = root.resolve("damaged");
        ContextStore.create(location);
        Path revisionPath = ContextStore.revisionPath(location);
        byte[] bytes = Files.readAllBytes(revisionPath);
        bytes[8] ^= 0x01;
        Files.write(revisionPath, bytes);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));

        assertEquals(StorageLifecycleErrorCode.STORAGE_SEMANTIC_CORRUPTION,
                failure.getErrorCode());
    }
}
