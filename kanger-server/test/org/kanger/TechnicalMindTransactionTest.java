/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Direct lifecycle qualification for {@link TechnicalMindTransaction}. */
class TechnicalMindTransactionTest {

    @Test
    void closeRollsBackUnsettledTechnicalChild() throws Exception {
        Fixture fixture = fixture("close-rollback");
        try {
            assertEquals(0, counter(fixture.root));
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(fixture.root)) {
                assertEquals(1, counter(fixture.root));
                tx.mind().getLog().add(org.kanger.enums.LogMode.ANALYZER, "technical-scope-probe");
            }
            assertEquals(0, counter(fixture.root));
        } finally {
            fixture.close();
        }
    }

    @Test
    void explicitRollbackSettlesExactlyOnce() throws Exception {
        Fixture fixture = fixture("rollback-once");
        try {
            TechnicalMindTransaction tx = TechnicalMindTransaction.begin(fixture.root);
            assertEquals(1, counter(fixture.root));
            tx.rollback();
            assertEquals(0, counter(fixture.root));
            tx.close();
            assertEquals(0, counter(fixture.root));
            assertThrows(IllegalStateException.class, tx::rollback);
            assertEquals(0, counter(fixture.root));
        } finally {
            fixture.close();
        }
    }

    @Test
    void explicitCommitSettlesExactlyOnce() throws Exception {
        Fixture fixture = fixture("commit-once");
        try {
            TechnicalMindTransaction tx = TechnicalMindTransaction.begin(fixture.root);
            assertEquals(1, counter(fixture.root));
            tx.commit();
            assertEquals(0, counter(fixture.root));
            tx.close();
            assertEquals(0, counter(fixture.root));
            assertThrows(IllegalStateException.class, tx::commit);
            assertEquals(0, counter(fixture.root));
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "technical-mind-tx-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;

        private Fixture(IUser user, Mind root) {
            this.user = user;
            this.root = root;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
