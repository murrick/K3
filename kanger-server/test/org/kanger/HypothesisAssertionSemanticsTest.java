/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes WHEN hypotheses as assertion-ready solutions of the original
 * query rather than arbitrary assumptions that merely make the query
 * determinate.
 */
class HypothesisAssertionSemanticsTest {

    private static final String SOURCE =
            "!@x $y parent(y,x);" +
            "!@x ~parent(x,x);" +
            "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
            "!@x @y daughter(x,y) -> female(x), child(x,y);" +
            "!@x @y son(x,y) -> male(x), child(x,y);" +
            "!@x @y father(x,y) -> male(x), parent(x,y);" +
            "!@x @y mother(x,y) -> female(x), parent(x,y);" +
            "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
            "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
            "!@x @y ~(parent(x,y), parent(y,x));" +
            "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y);" +
            "!@x @y ~(sibling(x,y), parent(x,y));" +
            "!@x @y sibling(x,y) -> sibling(y,x);" +
            "!@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);" +
            "!father(John, Tom);" +
            "!daughter(Sarah, John);" +
            "!mother(Mary,Sarah);" +
            "!child(Tom,Mary);" +
            "!age(John, 37);" +
            "!age(Tom, 12);" +
            "!age(Sarah, 4);";

    @Test
    void succedentCVariableHypothesisRendersAsUniversalNegativeAssertion()
            throws Exception {
        Fixture fixture = fixture("render");
        try {
            Mind mind = new Mind(fixture.user);
            fixture.user.setCurrentMind(mind);
            Rule source = (Rule) mind.compileLine("!$y son(John,y);", false, null);
            Hypothesis hypothesis = new Hypothesis(source, mind);

            assertEquals("?$y son(John,y);", hypothesis.toInternalString(mind));
            assertEquals("!@y ~son(John,y);", hypothesis.toAssertionString(mind));
            assertEquals("!@y ~son(John,y);", hypothesis.toString(mind));
        } finally {
            fixture.close();
        }
    }

    @Test
    void optimizedHypothesesAreAssertionsThatMakeTheOriginalQueryTrue()
            throws Exception {
        Fixture fixture = fixture("true-only");
        try {
            Mind mind = prepared(fixture);
            String query = "?male(Tom);";
            assertNull(mind.query(query, null, false));

            mind.optimizeHypothesis();

            assertEquals(6, mind.getHypothesis().size(),
                    "counter-hypotheses that make the query FALSE remain visible");
            for (IHypothesis candidate : mind.getHypothesis()) {
                String assertion = ((Hypothesis) candidate).toAssertionString(mind);
                Mind child = new Mind(mind);
                try {
                    assertTrue(Boolean.TRUE.equals(child.query(assertion, null, false)),
                            "optimized hypothesis is not an admissible assertion: " + assertion);
                    assertTrue(Boolean.TRUE.equals(child.query(query, null, false)),
                            "optimized hypothesis does not solve the original query: " + assertion);
                } finally {
                    mind.release(child);
                }
            }
        } finally {
            fixture.close();
        }
    }

    @Test
    void daughterSaraHasNoViableSingleHypothesis() throws Exception {
        Fixture fixture = fixture("sara");
        try {
            Mind mind = prepared(fixture);
            assertNull(mind.query("?$x daughter(Sara,x);", null, false));

            mind.optimizeHypothesis();

            assertEquals(0, mind.getHypothesis().size(),
                    "WHEN exposed only counter-hypotheses for daughter(Sara,x)");
        } finally {
            fixture.close();
        }
    }

    private static Mind prepared(Fixture fixture) throws Exception {
        Mind mind = new Mind(fixture.user);
        fixture.user.setCurrentMind(mind);
        assertTrue(mind.compile(SOURCE));
        return mind;
    }

    private static Fixture fixture(String purpose) throws Exception {
        String identity = "hypothesis-assertion-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
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
