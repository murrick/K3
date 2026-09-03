/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class TransactionCompatibilityRegistryTest {

    @Test
    void peekReportsUnqualifiedWithoutMaterializingRecord() throws Exception {
        Mind mind = newMind("transaction-registry-peek");

        TransactionCompatibilityRegistry.Record first =
                TransactionCompatibilityRegistry.peek(mind);
        TransactionCompatibilityRegistry.Record second =
                TransactionCompatibilityRegistry.peek(mind);

        assertEquals(TransactionCompatibilityRegistry.Compatibility.UNQUALIFIED,
                first.getCompatibility(),
                "unknown compatibility must be observed as UNQUALIFIED");
        assertEquals(TransactionCompatibilityRegistry.storage(mind),
                first.getStorage(),
                "observation must retain the current logical storage name");
        assertEquals(0, first.getCollisions().size(),
                "unknown compatibility must not invent collision witnesses");
        assertEquals(TransactionCompatibilityRegistry.Compatibility.UNQUALIFIED,
                second.getCompatibility(),
                "repeated observation must remain UNQUALIFIED");
        assertNotSame(first, second,
                "peek must not retain the synthetic compatibility record");

        TransactionCompatibilityRegistry.Record materialized =
                TransactionCompatibilityRegistry.status(mind);
        assertEquals(TransactionCompatibilityRegistry.Compatibility.VALID,
                materialized.getCompatibility(),
                "historical status must still materialize VALID after observational peeks");
        assertNotSame(first, materialized,
                "first peek result must not become registry state");
        assertNotSame(second, materialized,
                "second peek result must not become registry state");
    }

    @Test
    void peekReturnsExistingRecordWithoutReplacingIt() throws Exception {
        Mind mind = newMind("transaction-registry-existing");

        TransactionCompatibilityRegistry.markUnqualified(mind);
        TransactionCompatibilityRegistry.Record existing =
                TransactionCompatibilityRegistry.status(mind);
        assertEquals(TransactionCompatibilityRegistry.Compatibility.UNQUALIFIED,
                existing.getCompatibility(),
                "fixture must contain explicit UNQUALIFIED compatibility");

        TransactionCompatibilityRegistry.Record observed =
                TransactionCompatibilityRegistry.peek(mind);

        assertSame(existing, observed,
                "peek must return already-recorded compatibility verbatim");
        assertSame(existing, TransactionCompatibilityRegistry.status(mind),
                "peek must not replace an existing compatibility record");
    }

    private static Mind newMind(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }
}
