/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the physical reservation shape of an explicit user stack.
 *
 * <p>At a command boundary the published top level owns no hidden child. Every
 * ancestor below it owns exactly one reservation: the next explicit child in
 * the published U-chain. Merely testing {@code transactionCounter > 0} is too
 * weak because an unreachable sibling can make a corrupted chain look valid.</p>
 */
class TransactionReservationInvariantTest {

    @Test
    void storageRebaseRejectsExtraSiblingReservationBeforeMutatingStack()
            throws Exception {
        Fixture fixture = fixture("extra-sibling");
        Mind explicit = null;
        Mind stray = null;
        IMind observedCurrent = null;
        String observedStorage = null;
        Exception observedFailure = null;
        try {
            IMind root = createStorage(fixture, "reservation-a", "a_fact");
            createStorage(fixture, "reservation-b", "b_fact");
            root = open(fixture, root, "reservation-a");

            explicit = new Mind(root);
            fixture.user.setCurrentMind(explicit);
            assertTrue(Boolean.TRUE.equals(explicit.query("!u1_fact;")));

            stray = new Mind(root);
            assertEquals(2, counter((Mind) root),
                    "fixture did not establish the ambiguous parent reservation");
            assertEquals(0, counter(explicit));

            final Mind published = explicit;
            observedFailure = assertThrows(Exception.class,
                    () -> published.useStorage("reservation-b"));
            observedCurrent = fixture.user.getCurrentMind();
            observedStorage = observedCurrent.getStorageName();
        } finally {
            IMind current = fixture.user.getCurrentMind();
            if (current != null && current.getNext() != null) {
                Mind parent = (Mind) current.getNext();
                parent.release(current);
                fixture.user.setCurrentMind(parent);
            }
            if (stray != null) {
                Mind root = (Mind) stray.getNext();
                if (counter(root) > 0) {
                    root.release(stray);
                }
            }
            fixture.close();
        }

        assertTrue(observedFailure instanceof IllegalStateException,
                "ambiguous U-chain was not rejected by the ownership invariant: "
                        + observedFailure);
        assertTrue(observedFailure.getMessage() != null
                        && observedFailure.getMessage().contains("exactly one"),
                "failure did not identify the exact-reservation invariant: "
                        + observedFailure);
        assertSame(explicit, observedCurrent,
                "rebase mutated/reconstructed the published U1 before detecting the stray reservation");
        assertEquals("reservation-a", observedStorage,
                "rejected ownership state changed the active storage");
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private IMind createStorage(Fixture fixture, String name, String fact)
            throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query("!" + fact + ";")));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "reservation-invariant-" + purpose + "-"
                + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user);
    }

    private static final class Fixture {
        private final IUser user;

        private Fixture(IUser user) {
            this.user = user;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
