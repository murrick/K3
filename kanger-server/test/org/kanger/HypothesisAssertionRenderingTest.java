/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterizes assertion-ready presentation of hypotheses without changing
 * their historical/internal polarity representation or optimizer semantics.
 */
class HypothesisAssertionRenderingTest {

    @Test
    void succedentCVariableHypothesisMaterializesAsUniversalNegativeAssertion()
            throws Exception {
        Fixture fixture = fixture("variable");
        try {
            Mind mind = new Mind(fixture.user);
            fixture.user.setCurrentMind(mind);
            Rule source = (Rule) mind.compileLine("!$y son(John,y);", false, null);
            Hypothesis hypothesis = new Hypothesis(source, mind);

            assertEquals("?$y son(John,y);", hypothesis.toString(mind));
            assertEquals("!@y ~son(John,y);", hypothesis.toAssertionString(mind));
        } finally {
            fixture.close();
        }
    }

    @Test
    void groundSuccedentHypothesisMaterializesAsNegativeAssertion()
            throws Exception {
        Fixture fixture = fixture("ground");
        try {
            Mind mind = new Mind(fixture.user);
            fixture.user.setCurrentMind(mind);
            Rule source = (Rule) mind.compileLine("!female(Tom);", false, null);
            Hypothesis hypothesis = new Hypothesis(source, mind);

            assertEquals("?female(Tom);", hypothesis.toString(mind));
            assertEquals("!~female(Tom);", hypothesis.toAssertionString(mind));
        } finally {
            fixture.close();
        }
    }

    private static Fixture fixture(String purpose) throws Exception {
        String identity = "hypothesis-rendering-" + purpose + "-" + UUID.randomUUID();
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
