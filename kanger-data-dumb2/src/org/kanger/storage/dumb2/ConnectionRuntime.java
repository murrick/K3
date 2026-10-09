/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.exception.CommandErrorException;

/** One private initialized layer over an immutable pinned Context. */
final class ConnectionRuntime implements AutoCloseable {
    private final SnapshotMindRuntime snapshot;
    private final ConnectionRuntime parent;
    private int references = 1;
    final Mind mind;
    boolean closed;
    ConnectionRuntime(ContextConnection connection) throws Exception {
        parent = null;
        snapshot = SnapshotMindRuntime.open(connection.getTargetLocation(), connection.getTarget(),
                "connection-" + connection.getTarget().getContextId());
        Mind work = null;
        try {
            work = Mind.contextConnectionLayer(snapshot.getMind());
            ((User) work.getUser()).setCurrentMind(work);
            for (String command : connection.getInitialization()) {
                apply(work, command);
            }
            mind = work;
        } catch (Exception failure) {
            if (work != null) snapshot.getMind().discardEphemeral(work);
            snapshot.close();
            throw failure;
        }
    }

    ConnectionRuntime(ConnectionRuntime parent, String command) throws Exception {
        this.parent = parent; this.snapshot = null;
        Mind work = Mind.contextConnectionLayer(parent.mind);
        boolean accepted = false;
        try {
            ((User) work.getUser()).setCurrentMind(work);
            apply(work, command);
            mind = work;
            parent.references++;
            accepted = true;
        } finally {
            if (!accepted) {
                parent.mind.discardEphemeral(work);
                ((User) parent.mind.getUser()).setCurrentMind(parent.mind);
            }
        }
    }

    public void close() throws Exception {
        if (closed) return;
        closed = true;
        release();
    }

    private void release() throws Exception {
        if (--references != 0) return;
        Mind root = parent == null ? snapshot.getMind() : parent.mind;
        root.discardEphemeral(mind);
        ((User) mind.getUser()).setCurrentMind(root);
        if (parent == null) snapshot.close();
        else parent.release();
    }

    private static void apply(Mind work, String command) throws Exception {
        Boolean result = work.query(command, null, false);
        // Native no-op operations still express useful intent when replaying
        // against a later pin (already present assertion or an empty deletion).
        if (Boolean.TRUE.equals(result) || work.wasLastCommandNoOp()) return;
        throw new CommandErrorException("Context initialization command rejected: " + command);
    }
}
