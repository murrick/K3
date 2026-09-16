/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for the existing Dictionary/Escalera boundary as a
 * cross-context verbal projection primitive.
 */
public final class KangerCrossContextTermProjectionTest {

    @Test
    void foreignTermResolvesOnlyToExistingTargetVerbalTwin() throws Exception {
        String suffix = Long.toString(System.nanoTime());

        User userA = (User) UserFactory.createUser(
                "term-projection-a-" + suffix,
                "term-projection-a-" + suffix);
        new UDF().init(userA);
        new DB().init(userA);
        Mind a = (Mind) new Mind(userA).clearWorkspace();

        User userB = (User) UserFactory.createUser(
                "term-projection-b-" + suffix,
                "term-projection-b-" + suffix);
        new UDF().init(userB);
        new DB().init(userB);
        Mind b = (Mind) new Mind(userB).clearWorkspace();

        Term aJohn = (Term) a.getTerms().add("John");

        // Deliberately shift B's local operational allocation before John.
        b.getTerms().add("Peter");
        b.getTerms().add("Mary");

        assertNull(b.getTerms().find(aJohn),
                "foreign John must not resolve when B has no verbal John");
        assertNull(b.getTerms().find("John"),
                "a foreign lookup miss must not verbalize John in B");

        Term bJohn = (Term) b.getTerms().add("John");
        assertNotEquals(aJohn.getId(), bJohn.getId(),
                "fixture must use different context-local operational IDs");
        assertEquals(aJohn.getHash(), bJohn.getHash(),
                "equal verbal Terms must have context-independent hashes");
        assertTrue(aJohn.equalsTo(bJohn),
                "equal verbal Terms must compare semantically across contexts");

        Term projectedJohn = b.getTerms().find(aJohn);
        assertNotNull(projectedJohn,
                "B must resolve an existing verbal twin using A's Term");
        assertEquals(bJohn.getId(), projectedJohn.getId(),
                "projection must return B-local operational identity");
    }

    @Test
    void setProjectionIgnoresContextLocalMemberIdsAndOrder() throws Exception {
        String suffix = Long.toString(System.nanoTime());

        User userA = (User) UserFactory.createUser(
                "set-projection-a-" + suffix,
                "set-projection-a-" + suffix);
        new UDF().init(userA);
        new DB().init(userA);
        Mind a = (Mind) new Mind(userA).clearWorkspace();

        User userB = (User) UserFactory.createUser(
                "set-projection-b-" + suffix,
                "set-projection-b-" + suffix);
        new UDF().init(userB);
        new DB().init(userB);
        Mind b = (Mind) new Mind(userB).clearWorkspace();

        // Different creation order guarantees different member-local IDs.
        Term aJohn = (Term) a.getTerms().add("John");
        Term aMary = (Term) a.getTerms().add("Mary");

        Term bMary = (Term) b.getTerms().add("Mary");
        b.getTerms().add("Peter");
        Term bJohn = (Term) b.getTerms().add("John");

        assertNotEquals(aJohn.getId(), bJohn.getId(),
                "John must have different context-local IDs");
        assertNotEquals(aMary.getId(), bMary.getId(),
                "Mary must have different context-local IDs");

        Term aSet = (Term) a.getTerms().add(new Object[]{"John", "Mary"});
        Term bSet = (Term) b.getTerms().add(new Object[]{"Mary", "John"});

        assertEquals(aSet.getHash(), bSet.getHash(),
                "SET hash must depend on semantic members, not local IDs or order");
        assertTrue(aSet.equalsTo(bSet),
                "SET equality must compare semantic members across contexts");

        Term projectedSet = b.getTerms().find(aSet);
        assertNotNull(projectedSet,
                "B must resolve an existing semantically equal SET from A");
        assertEquals(bSet.getId(), projectedSet.getId(),
                "SET projection must return B-local canonical identity");
    }
}
