/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runtime-only compatibility memory for explicit user transaction levels.
 *
 * <p>This metadata is observational. It is never persisted into KANGER storage,
 * never participates in inference and never qualifies a context merely because
 * status was requested. Unknown historical compatibility is therefore reported
 * honestly as {@link Compatibility#UNQUALIFIED} until an existing semantic
 * boundary proves it valid or incompatible.</p>
 */
final class TransactionCompatibilityRegistry {

    enum Compatibility {
        VALID,
        UNQUALIFIED,
        INCOMPATIBLE
    }

    static final class Witness {
        private final String left;
        private final String right;

        Witness(String left, String right) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
        }

        String getLeft() {
            return left;
        }

        String getRight() {
            return right;
        }
    }

    static final class Record {
        private final Compatibility compatibility;
        private final String storage;
        private final List<Witness> collisions;

        Record(Compatibility compatibility,
               String storage,
               List<Witness> collisions) {
            this.compatibility = compatibility;
            this.storage = storage;
            this.collisions = Collections.unmodifiableList(
                    new ArrayList<Witness>(collisions));
        }

        Compatibility getCompatibility() {
            return compatibility;
        }

        String getStorage() {
            return storage;
        }

        List<Witness> getCollisions() {
            return collisions;
        }
    }

    private static final Map<Mind, Record> RECORDS =
            Collections.synchronizedMap(new WeakHashMap<Mind, Record>());

    private TransactionCompatibilityRegistry() {
    }

    static Record status(Mind mind) throws Exception {
        Record existing = RECORDS.get(mind);
        if (existing != null) {
            return existing;
        }
        Record created = new Record(
                Compatibility.VALID, storage(mind), Collections.<Witness>emptyList());
        RECORDS.put(mind, created);
        return created;
    }

    static Record capture(Mind mind) throws Exception {
        Record current = status(mind);
        return new Record(current.getCompatibility(), current.getStorage(),
                current.getCollisions());
    }

    static void restore(Mind mind, Record record) throws Exception {
        if (record == null) {
            markValid(mind);
            return;
        }
        RECORDS.put(mind, new Record(record.getCompatibility(),
                record.getStorage(), record.getCollisions()));
    }

    static void markValid(Mind mind) throws Exception {
        RECORDS.put(mind, new Record(
                Compatibility.VALID, storage(mind), Collections.<Witness>emptyList()));
    }

    static void markUnqualified(Mind mind) throws Exception {
        RECORDS.put(mind, new Record(
                Compatibility.UNQUALIFIED, storage(mind), Collections.<Witness>emptyList()));
    }

    static void markIncompatible(Mind mind,
                                 ContextQualification qualification) throws Exception {
        List<Witness> collisions = new ArrayList<Witness>();
        if (qualification != null) {
            for (ContextQualification.CollisionWitness witness
                    : qualification.getCollisions()) {
                collisions.add(new Witness(witness.getLeft(), witness.getRight()));
            }
        }
        RECORDS.put(mind, new Record(
                Compatibility.INCOMPATIBLE, storage(mind), collisions));
    }

    static void markRebasedStack(Mind top) throws Exception {
        List<Mind> lineage = UserTransactionStackSnapshot.lineage(top);
        for (int i = 0; i < lineage.size(); ++i) {
            Mind level = lineage.get(i);
            if (i == 0 || i == lineage.size() - 1) {
                markValid(level);
            } else {
                markUnqualified(level);
            }
        }
    }

    static String storage(Mind mind) throws Exception {
        return mind != null && mind.isStorageUsed() ? mind.getStorageName() : null;
    }
}
