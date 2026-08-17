/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes explicit transaction lifecycle after storage-independent
 * rebase state has become temporarily unresolved on a replacement U0.
 */
class PortableRebaseTransactionLifecycleTest {

    @Test
    void unresolvedChildDeleteCommitPropagatesExactlyOneLevel() throws Exception {
        Fixture fixture = fixture("child-delete-commit");
        try {
            IMind root = createStorage(fixture, "portable-life-a", "shared_fact");
            createStorage(fixture, "portable-life-b", "b_only");
            createStorage(fixture, "portable-life-c", "shared_fact");
            root = open(fixture, root, "portable-life-a");

            Mind u1 = new Mind(root);
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("-shared_fact;")));
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-life-b");
            fixture.user.setCurrentMind(rebasedU2);
            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            assertTrue(rebasedU1.commit(rebasedU2),
                    "U2 -> U1 commit rejected on the baseline where X is absent");
            fixture.user.setCurrentMind(rebasedU1);

            IMind onC = rebasedU1.useStorage("portable-life-c");
            fixture.user.setCurrentMind(onC);
            assertFalse(Boolean.TRUE.equals(onC.query("?shared_fact;")),
                    "committed unresolved U2 deletion did not become U1 state");
        } finally {
            fixture.close();
        }
    }

    @Test
    void rollbackDiscardsChildRestoreButPreservesParentDelete() throws Exception {
        Fixture fixture = fixture("child-restore-rollback");
        try {
            IMind root = createStorage(fixture, "portable-life-rb-a", "shared_fact");
            createStorage(fixture, "portable-life-rb-b", "b_only");
            createStorage(fixture, "portable-life-rb-c", "shared_fact");
            root = open(fixture, root, "portable-life-rb-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!shared_fact;")));
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-life-rb-b");
            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            rebasedU1.release(rebasedU2);
            fixture.user.setCurrentMind(rebasedU1);

            IMind onC = rebasedU1.useStorage("portable-life-rb-c");
            fixture.user.setCurrentMind(onC);
            assertFalse(Boolean.TRUE.equals(onC.query("?shared_fact;")),
                    "rollback discarded or corrupted the parent unresolved deletion");
        } finally {
            fixture.close();
        }
    }

    @Test
    void childRestoreCommitSupersedesParentUnresolvedDelete() throws Exception {
        Fixture fixture = fixture("child-restore-commit");
        try {
            IMind root = createStorage(fixture, "portable-life-rc-a", "shared_fact");
            createStorage(fixture, "portable-life-rc-b", "b_only");
            createStorage(fixture, "portable-life-rc-c", "shared_fact");
            root = open(fixture, root, "portable-life-rc-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!shared_fact;")));
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-life-rc-b");
            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            assertTrue(rebasedU1.commit(rebasedU2),
                    "U2 restore -> U1 commit rejected while X is absent");
            fixture.user.setCurrentMind(rebasedU1);

            IMind onC = rebasedU1.useStorage("portable-life-rc-c");
            fixture.user.setCurrentMind(onC);
            assertTrue(Boolean.TRUE.equals(onC.query("?shared_fact;")),
                    "committed child restore failed to supersede parent delete residue");
        } finally {
            fixture.close();
        }
    }

    @Test
    void rootCommitOfAbsentDeletionCollapsesResidueHarmlessly() throws Exception {
        Fixture fixture = fixture("root-collapse");
        try {
            IMind root = createStorage(fixture, "portable-life-root-a", "shared_fact");
            createStorage(fixture, "portable-life-root-b", "b_only");
            createStorage(fixture, "portable-life-root-c", "shared_fact");
            root = open(fixture, root, "portable-life-root-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            fixture.user.setCurrentMind(u1);

            Mind rebasedU1 = (Mind) u1.useStorage("portable-life-root-b");
            Mind bRoot = (Mind) rebasedU1.getNext();
            assertTrue(bRoot.commit(rebasedU1),
                    "U1 -> U0 commit rejected although B has no X to delete");
            fixture.user.setCurrentMind(bRoot);

            IMind cRoot = bRoot.useStorage("portable-life-root-c");
            fixture.user.setCurrentMind(cRoot);
            assertTrue(Boolean.TRUE.equals(cRoot.query("?shared_fact;")),
                    "deletion committed against absent B/U0 leaked into later C/U0");
        } finally {
            fixture.close();
        }
    }

    @Test
    void portableDeletedRestoredPairKeepsRestoredFirstVisibility() throws Exception {
        Fixture fixture = fixture("paired-markers");
        try {
            IMind root = createStorage(fixture, "portable-life-pair-a", "shared_fact");
            createStorage(fixture, "portable-life-pair-b", "b_only");
            createStorage(fixture, "portable-life-pair-c", "shared_fact");
            root = open(fixture, root, "portable-life-pair-a");

            Mind u1 = new Mind(root);
            Rule shared = findRule(u1, "!shared_fact;");
            u1.getDeleted().computeIfAbsent(org.kanger.enums.UnitType.RULE,
                    key -> new java.util.HashSet<Long>()).add(shared.getId());
            u1.getRestored().computeIfAbsent(org.kanger.enums.UnitType.RULE,
                    key -> new java.util.HashSet<Long>()).add(shared.getId());
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("?shared_fact;")),
                    "fixture pair did not honor restored-first visibility");

            IMind onB = u1.useStorage("portable-life-pair-b");
            fixture.user.setCurrentMind(onB);
            assertFalse(Boolean.TRUE.equals(onB.query("?shared_fact;")),
                    "paired marker created an absent declaration on B");

            Mind onC = (Mind) onB.useStorage("portable-life-pair-c");
            fixture.user.setCurrentMind(onC);
            Rule cShared = findRule(onC, "!shared_fact;");
            assertTrue(onC.getDeleted().get(org.kanger.enums.UnitType.RULE)
                            .contains(cShared.getId()),
                    "portable pair lost its deleted marker");
            assertTrue(onC.getRestored().get(org.kanger.enums.UnitType.RULE)
                            .contains(cShared.getId()),
                    "portable pair lost its restored marker");
            assertTrue(Boolean.TRUE.equals(onC.query("?shared_fact;")),
                    "restored-first visibility changed after portable rebase");
        } finally {
            fixture.close();
        }
    }

    private Rule findRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        throw new AssertionError("Rule not found: " + origin);
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
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "portable-rebase-life-" + purpose + "-" + UUID.randomUUID();
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
