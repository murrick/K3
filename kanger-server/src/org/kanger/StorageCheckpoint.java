/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;

/**
 * Executes the existing root transaction-finalization path as a durable,
 * storage-preserving checkpoint.
 *
 * <p>The core already publishes a root generation when its last child is
 * committed. A deliberately empty child therefore provides the same
 * pack/update/flush ordering without introducing a storage close, manifest
 * compaction or runtime-context reset.</p>
 */
final class StorageCheckpoint {

    private StorageCheckpoint() {
    }

    static void checkpoint(IMind active) throws Exception {
        if (active == null) {
            throw new IllegalStateException(
                    "Cannot checkpoint storage without an active Mind");
        }
        if (active.getTransactionLevel() != 0) {
            throw new IllegalStateException(
                    "Cannot checkpoint storage while transaction level "
                            + active.getTransactionLevel() + " is active");
        }

        IMind root = active.getTop();
        if (!root.isStorageUsed()) {
            throw new IllegalStateException(
                    "Cannot checkpoint storage because no database is open");
        }

        Mind checkpoint = new Mind(root);
        boolean applied = root.commit(checkpoint);
        if (!applied) {
            throw new IllegalStateException(
                    "Empty root checkpoint was rejected");
        }
    }
}
