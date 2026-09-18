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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for the DUMB 2.0 Context lifecycle boundary. */
public class ContextStoreTest {

    @TempDir
    Path root;

    @Test
    void createCloseAndReopenPublishOneCoherentIdentityRevisionPair() throws Exception {
        Path location = root.resolve("reopen");

        ContextStore created = ContextStore.create(location);
        java.util.UUID contextId = created.getContextId();
        long revision = created.getRevision();
        created.close();

        ContextStore reopened = ContextStore.open(location);
        try {
            assertEquals(contextId, reopened.getContextId());
            assertEquals(RevisionStore.INITIAL_REVISION, revision);
            assertEquals(revision, reopened.getRevision());
            assertEquals(location, reopened.getLocation());
            assertTrue(Files.isRegularFile(ContextStore.contextPath(location)));
            assertTrue(Files.isRegularFile(ContextStore.revisionPath(location)));
        } finally {
            reopened.close();
        }
    }

    @Test
    void independentContextsReceiveDistinctIdentities() throws Exception {
        ContextStore first = ContextStore.create(root.resolve("first"));
        ContextStore second = ContextStore.create(root.resolve("second"));
        try {
            assertNotEquals(first.getContextId(), second.getContextId());
            assertEquals(0L, first.getRevision());
            assertEquals(0L, second.getRevision());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void movingCompleteContextPreservesSnapshot() throws Exception {
        Path source = root.resolve("source");
        Path target = root.resolve("target");
        ContextStore before = ContextStore.create(source);
        java.util.UUID contextId = before.getContextId();
        long revision = before.getRevision();
        before.close();

        Files.move(ContextStore.contextPath(source), ContextStore.contextPath(target),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(ContextStore.revisionPath(source), ContextStore.revisionPath(target),
                StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(ContextStore.stateRoot(source))) {
            Files.move(ContextStore.stateRoot(source), ContextStore.stateRoot(target),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        ContextStore after = ContextStore.open(target);
        try {
            assertEquals(contextId, after.getContextId());
            assertEquals(revision, after.getRevision());
        } finally {
            after.close();
        }
    }

    @Test
    void secondCreateNeverReplacesExistingPair() throws Exception {
        Path location = root.resolve("existing");
        ContextStore original = ContextStore.create(location);
        java.util.UUID contextId = original.getContextId();
        long revision = original.getRevision();

        assertThrows(FileAlreadyExistsException.class,
                () -> ContextStore.create(location));

        original.close();
        ContextStore reopened = ContextStore.open(location);
        try {
            assertEquals(contextId, reopened.getContextId());
            assertEquals(revision, reopened.getRevision());
        } finally {
            reopened.close();
        }
    }

    @Test
    void secondOpenIsRejectedUntilExplicitClose() throws Exception {
        Path location = root.resolve("locked");
        ContextStore first = ContextStore.create(location);

        StorageLifecycleException failure = assertThrows(
                StorageLifecycleException.class,
                () -> ContextStore.open(location));
        assertEquals(StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN,
                failure.getErrorCode());

        first.close();
        ContextStore second = ContextStore.open(location);
        second.close();
    }

    @Test
    void closeIsIdempotent() throws Exception {
        ContextStore context = ContextStore.create(root.resolve("close"));
        context.close();
        context.close();
        assertTrue(context.isClosed());
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
        ContextStore created = ContextStore.create(location);
        created.close();

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
