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

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class TransactionCompatibilityRegistryTest {

    @Test
    void peekReportsUnqualifiedWithoutMaterializingRecord() throws Exception {
        Mind mind = newMind("transaction-registry-peek");
        Map<Mind, TransactionCompatibilityRegistry.Record> records = records();
        records.remove(mind);

        try {
            assertFalse(records.containsKey(mind),
                    "fresh Mind must not already have compatibility metadata");

            TransactionCompatibilityRegistry.Record observed =
                    TransactionCompatibilityRegistry.peek(mind);

            assertEquals(TransactionCompatibilityRegistry.Compatibility.UNQUALIFIED,
                    observed.getCompatibility(),
                    "unknown compatibility must be observed as UNQUALIFIED");
            assertEquals(TransactionCompatibilityRegistry.storage(mind),
                    observed.getStorage(),
                    "observation must retain the current logical storage name");
            assertEquals(0, observed.getCollisions().size(),
                    "unknown compatibility must not invent collision witnesses");
            assertFalse(records.containsKey(mind),
                    "peek must not materialize compatibility metadata");
        } finally {
            records.remove(mind);
        }
    }

    @Test
    void peekReturnsExistingRecordWithoutReplacingIt() throws Exception {
        Mind mind = newMind("transaction-registry-existing");
        Map<Mind, TransactionCompatibilityRegistry.Record> records = records();
        records.remove(mind);

        try {
            TransactionCompatibilityRegistry.markUnqualified(mind);
            TransactionCompatibilityRegistry.Record existing = records.get(mind);
            assertNotNull(existing, "markUnqualified must materialize the test fixture");

            TransactionCompatibilityRegistry.Record observed =
                    TransactionCompatibilityRegistry.peek(mind);

            assertSame(existing, observed,
                    "peek must return already-recorded compatibility verbatim");
            assertSame(existing, records.get(mind),
                    "peek must not replace an existing compatibility record");
        } finally {
            records.remove(mind);
        }
    }

    private static Mind newMind(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }

    @SuppressWarnings("unchecked")
    private static Map<Mind, TransactionCompatibilityRegistry.Record> records()
            throws Exception {
        Field field = TransactionCompatibilityRegistry.class.getDeclaredField("RECORDS");
        field.setAccessible(true);
        return (Map<Mind, TransactionCompatibilityRegistry.Record>) field.get(null);
    }
}
