/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.exception.ParseErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization gates for the GC/technical-transaction quiescence boundary.
 *
 * <p>The canonical Term/Predicate registries are swept when a Mind returns to
 * transaction quiescence. A technical child that survives an exceptional
 * command path therefore suppresses GC even when the sweep itself is correct.</p>
 */
class GcTransactionQuiescenceTest {

    @Test
    void balancedTechnicalChildTriggersCanonicalTermSweep() throws Exception {
        Fixture fixture = fixture("balanced-sweep");
        try {
            String orphan = unique("gc_balanced_orphan");
            fixture.root.getTerms().add(orphan);
            assertNotNull(fixture.root.getTerms().find(orphan));

            Mind child = new Mind(fixture.root);
            fixture.root.release(child);

            assertEquals(0, counter(fixture.root),
                    "Balanced technical child did not return the root to quiescence");
            assertNull(fixture.root.getTerms().find(orphan),
                    "Quiescent pack retained an unreachable canonical Term");
        } finally {
            fixture.close();
        }
    }

    @Test
    void malformedCompileRestoresRootTransactionQuiescence() throws Exception {
        Fixture fixture = fixture("parse-counter");
        try {
            assertEquals(0, counter(fixture.root));

            assertThrows(ParseErrorException.class,
                    () -> fixture.root.compile("!\"unterminated"));

            assertEquals(0, counter(fixture.root),
                    "Malformed compile leaked a technical child reservation");
        } finally {
            fixture.close();
        }
    }

    @Test
    void malformedCompileCannotSuppressCanonicalTermSweep() throws Exception {
        Fixture fixture = fixture("parse-gc");
        try {
            String orphan = unique("gc_parse_orphan");
            fixture.root.getTerms().add(orphan);
            assertNotNull(fixture.root.getTerms().find(orphan));

            assertThrows(ParseErrorException.class,
                    () -> fixture.root.compile("!\"unterminated"));

            assertNull(fixture.root.getTerms().find(orphan),
                    "Failed compile prevented quiescent GC of an unreachable Term");
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "gc-quiescence-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
    }

    private String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
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
