/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Predicate;
import org.kanger.units.Term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for Predicate projection through the existing
 * PredicateFactory/Escalera semantic lookup boundary.
 */
public final class KangerCrossContextPredicateProjectionTest {

    @Test
    void equalHashPredicateBucketStillRequiresSemanticNameEquality() throws Exception {
        String suffix = Long.toString(System.nanoTime());

        User userA = (User) UserFactory.createUser(
                "predicate-collision-a-" + suffix,
                "predicate-collision-a-" + suffix);
        new UDF().init(userA);
        new DB().init(userA);
        Mind a = (Mind) new Mind(userA).clearWorkspace();

        User userB = (User) UserFactory.createUser(
                "predicate-collision-b-" + suffix,
                "predicate-collision-b-" + suffix);
        new UDF().init(userB);
        new DB().init(userB);
        Mind b = (Mind) new Mind(userB).clearWorkspace();

        Term aAa = (Term) a.getTerms().add("Aa");
        Predicate aPredicate = a.getPredicates().add(aAa, 1);

        // "Aa" and "BB" have the same Java String hash. With identical range
        // they therefore deliberately share the Predicate candidate hash.
        Term bBb = (Term) b.getTerms().add("BB");
        Predicate bCollision = b.getPredicates().add(bBb, 1);
        assertEquals(aPredicate.getHash(), bCollision.getHash(),
                "fixture requires a real Predicate hash collision");
        assertTrue(!aPredicate.equalsTo(bCollision),
                "equal Predicate hash must not imply semantic identity");

        int before = b.getPredicates().size();
        assertNull(b.getPredicates().find(aAa, 1),
                "Predicate hash collision must not resolve the wrong definition");
        assertEquals(before, b.getPredicates().size(),
                "collision miss must remain read-only");

        Term bAa = (Term) b.getTerms().add("Aa");
        Predicate bTwin = b.getPredicates().add(bAa, 1);
        Predicate projected = b.getPredicates().find(aAa, 1);
        assertNotNull(projected);
        assertEquals(bTwin.getId(), projected.getId(),
                "semantic equalsTo must select the B-local Predicate twin");
    }

    @Test
    void foreignPredicateNameResolvesOnlyToExistingTargetDefinition() throws Exception {
        String suffix = Long.toString(System.nanoTime());

        User userA = (User) UserFactory.createUser(
                "predicate-projection-a-" + suffix,
                "predicate-projection-a-" + suffix);
        new UDF().init(userA);
        new DB().init(userA);
        Mind a = (Mind) new Mind(userA).clearWorkspace();

        User userB = (User) UserFactory.createUser(
                "predicate-projection-b-" + suffix,
                "predicate-projection-b-" + suffix);
        new UDF().init(userB);
        new DB().init(userB);
        Mind b = (Mind) new Mind(userB).clearWorkspace();

        Term aName = (Term) a.getTerms().add("p");
        Predicate aPredicate = a.getPredicates().add(aName, 1);

        // Deliberately shift B's local Term allocation before verbalizing p.
        b.getTerms().add("Peter");
        b.getTerms().add("Mary");
        Term bName = (Term) b.getTerms().add("p");

        assertNotEquals(aName.getId(), bName.getId(),
                "fixture must use different context-local name IDs");

        int predicateCountBeforeMiss = b.getPredicates().size();
        assertNull(b.getPredicates().find(aName, 1),
                "foreign p/1 must not resolve before B contains that Predicate");
        assertEquals(predicateCountBeforeMiss, b.getPredicates().size(),
                "a foreign Predicate lookup miss must not create a target definition");

        Predicate bPredicate = b.getPredicates().add(bName, 1);

        assertNotEquals(aPredicate.getNameId(), bPredicate.getNameId(),
                "equal Predicate definitions must retain context-local name locators");
        assertEquals(aPredicate.getHash(), bPredicate.getHash(),
                "equal Predicate definitions must have context-independent hashes");
        assertTrue(aPredicate.equalsTo(bPredicate),
                "equal Predicate definitions must compare semantically across contexts");

        Predicate projected = b.getPredicates().find(aName, 1);
        assertNotNull(projected,
                "B must resolve an existing p/1 using A's name Term");
        assertEquals(bPredicate.getId(), projected.getId(),
                "projection must return B-local Predicate identity");

        assertNull(b.getPredicates().find(aName, 2),
                "arity remains part of Predicate semantic identity");
    }
}
