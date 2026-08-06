/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;

/**
 * Server-owned {@link User} with an explicit boundary between transaction and
 * physical storage lifecycles.
 */
final class ExplicitLifecycleUser extends User {

    @Override
    public IMind use(IMind mind, String name) throws Exception {
        if (!isClosed()) {
            throw new RuntimeErrorException(
                    "A database is already open; explicit close is required before use");
        }
        return super.use(mind, name);
    }

    @Override
    public IMind close(IMind mind) throws Exception {
        if (mind != null && mind.getTransactionLevel() > 0) {
            throw new RuntimeErrorException(
                    "Cannot close database while transaction level "
                            + mind.getTransactionLevel()
                            + " is active; commit or rollback first");
        }
        if (isClosed()) {
            return mind;
        }
        if (mind == null) {
            throw new IllegalStateException(
                    "Cannot close an open database without an active Mind");
        }

        StorageCheckpoint.checkpoint(mind);
        return super.close(mind);
    }
}
