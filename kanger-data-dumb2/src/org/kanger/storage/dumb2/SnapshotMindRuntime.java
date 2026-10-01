/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;

import java.nio.file.Path;

/** One isolated Mind attached to one exact immutable DUMB2 Context revision. */
final class SnapshotMindRuntime implements AutoCloseable {

    private final User user;
    private final RevisionRef expected;
    private Mind mind;

    private SnapshotMindRuntime(User user,
                                RevisionRef expected,
                                Mind mind) {
        this.user = user;
        this.expected = expected;
        this.mind = mind;
    }

    static SnapshotMindRuntime open(
            Path location,
            RevisionRef expected,
            String logicalName) throws Exception {
        if (expected == null) {
            throw new NullPointerException("expected");
        }

        User user = new User();
        ContextSnapshotData data =
                new ContextSnapshotData(
                        location,
                        logicalName,
                        Long.valueOf(expected.getRevision()));
        data.init(user);

        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(logicalName);
        user.setCurrentMind(mind);

        if (!expected.getContextId().equals(data.getContextId())
                || expected.getRevision() != data.getRevision()) {
            mind = (Mind) mind.closeStorage();
            user.setCurrentMind(mind);
            throw new IllegalStateException(
                    "Exact Context runtime identity mismatch: expected "
                            + expected);
        }

        return new SnapshotMindRuntime(
                user, expected, mind);
    }

    Mind getMind() {
        if (mind == null) {
            throw new IllegalStateException(
                    "Snapshot Mind runtime is closed: " + expected);
        }
        return mind;
    }

    RevisionRef getRef() {
        return expected;
    }

    @Override
    public void close() throws Exception {
        if (mind != null) {
            mind = (Mind) mind.closeStorage();
            user.setCurrentMind(mind);
            mind = null;
        }
    }
}
