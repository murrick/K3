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

/**
 * Records the Server 0.13 account-lifecycle entrypoints that remain present
 * during the staged Server 0.14 migration.
 *
 * <p>The immutable develop/server/0.14.1 shelf retains the original
 * characterization that CredentialStore had no deletion contract. The working
 * 0.14.2 branch intentionally supersedes that single fact while preserving the
 * historical UserFactory surface until PendingRegistration replaces it.</p>
 */
class Server013AccountLifecycleCharacterizationTest {

    @Test
    void userFactoryStillCombinesCredentialCreationAndUserResolution()
            throws Exception {
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
    void credentialStoreNowExposesLifecycleDeletionByExactUserId()
            throws Exception {
        assertEquals(Boolean.TYPE,
                CredentialStore.class
                        .getMethod("delete", long.class)
                        .getReturnType());
    }

    @Test
    void dropUserRemainsRuntimeClosureRatherThanAccountDeletion()
            throws Exception {
        assertEquals(Void.TYPE,
                UserFactory.class.getMethod("dropUser", IUser.class).getReturnType());
        assertEquals(Void.TYPE,
                UserFactory.class.getMethod("dropUser", Long.class).getReturnType());
    }
}
