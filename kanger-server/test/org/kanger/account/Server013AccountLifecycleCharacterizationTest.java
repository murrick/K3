/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger.account;

import org.junit.jupiter.api.Test;
import org.kanger.UserFactory;
import org.kanger.interfaces.IUser;
import org.kanger.security.CredentialStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Records the Server 0.13 account-lifecycle surface before it is replaced by
 * the Server 0.14 lifecycle service.
 *
 * <p>These assertions describe the migration starting point, not the target
 * architecture. A later stage may replace this test with target-state gates
 * when the legacy entrypoints are deliberately removed.</p>
 */
class Server013AccountLifecycleCharacterizationTest {

    @Test
    void userFactoryCombinesCredentialCreationAndUserResolution() throws Exception {
        assertEquals(IUser.class,
                UserFactory.class
                        .getMethod("createUser", String.class, String.class)
                        .getReturnType());
        assertEquals(String.class,
                UserFactory.class
                        .getMethod("token", String.class, String.class)
                        .getReturnType());
        assertEquals(IUser.class,
                UserFactory.class
                        .getMethod("getUserByToken", String.class)
                        .getReturnType());
    }

    @Test
    void credentialStoreHasNoAccountDeletionContract() {
        assertThrows(NoSuchMethodException.class,
                () -> CredentialStore.class.getMethod("delete", long.class));
        assertThrows(NoSuchMethodException.class,
                () -> CredentialStore.class.getMethod("delete", String.class));
    }

    @Test
    void dropUserIsRuntimeClosureRatherThanAccountDeletion() throws Exception {
        assertEquals(Void.TYPE,
                UserFactory.class.getMethod("dropUser", IUser.class).getReturnType());
        assertEquals(Void.TYPE,
                UserFactory.class.getMethod("dropUser", Long.class).getReturnType());
    }
}
