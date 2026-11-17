/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Path;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Stabilizes direct persisted references without acquiring or executing foreign dependencies. */
final class DurablePins {
    private DurablePins() { }

    static void stabilize(ConnectionVector dependencies) throws Exception {
        for (ContextConnection connection : dependencies.getConnections()) {
            Path location = connection.getTargetLocation();
            RevisionRef target = connection.getTarget();
            requireIdentity(location, target);
            // The owning writer can still be between marker replacement and its final ACK.
            // Pinning a visible revision must establish our own persistence evidence for it.
            DurableFiles.forceContextFrame(location, target.getRevision());
            RevisionStore.confirmDurable(ContextStore.revisionPath(location));
            // Legitimate writers are monotone and retain published generations/vectors.
            // A later marker can supersede this one; a durable predecessor >= pin suffices.
            requireIdentity(location, target);
        }
    }

    private static void requireIdentity(Path location, RevisionRef target) throws Exception {
        try (ContextSnapshot snapshot = ContextSnapshot.open(location, target.getRevision())) {
            if (!target.getContextId().equals(snapshot.getContextId()))
                throw new StorageLifecycleException(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,
                        "Pinned Context identity changed at " + location.getFileName() + "@" + target.getRevision());
        }
    }
}
