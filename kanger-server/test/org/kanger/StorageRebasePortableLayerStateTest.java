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
import org.kanger.units.Operation;
import org.kanger.units.Rule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED characterization for exact portable authorial state of explicit U-levels.
 *
 * <p>A storage rebase may recanonicalize storage-local identities, but it must
 * preserve each U-level's own semantic structure and provenance. Effective
 * visible difference alone is insufficient because hidden local objects,
 * deletion/restoration markers and COW overrides remain meaningful to later
 * rollback, restore and subsequent storage switches.</p>
 */
class StorageRebasePortableLayerStateTest {

    @Test
    void ruleDeletionSurvivesBaselineThatLacksRuleAcrossSecondRebase()
            throws Exception {
        Fixture fixture = fixture("rule-delete-multihop");
        try {
            IMind root = createStorage(fixture, "portable-rule-a", "shared_fact");
            createStorage(fixture, "portable-rule-b", "b_only");
            createStorage(fixture, "portable-rule-c", "shared_fact");
            root = open(fixture, root, "portable-rule-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            assertFalse(Boolean.TRUE.equals(u1.query("?shared_fact;")));

            IMind onB = u1.useStorage("portable-rule-b");
            fixture.user.setCurrentMind(onB);
            assertEquals(1, onB.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(onB.query("?shared_fact;")));

            IMind onC = onB.useStorage("portable-rule-c");
            fixture.user.setCurrentMind(onC);
            assertEquals(1, onC.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(onC.query("?shared_fact;")),
                    "U1 deletion vanished while an intermediate U0 lacked the Rule");

            Mind rebasedU1 = (Mind) onC;
            Mind cRoot = (Mind) rebasedU1.getNext();
            cRoot.release(rebasedU1);
            fixture.user.setCurrentMind(cRoot);
            assertTrue(Boolean.TRUE.equals(cRoot.query("?shared_fact;")),
                    "rollback did not reveal C/U0 after the preserved U1 deletion");
        } finally {
            fixture.close();
        }
    }

    @Test
    void inheritedRestoreDoesNotBecomeAdditionWhenReplacementBaseLacksRule()
            throws Exception {
        Fixture fixture = fixture("restore-not-add");
        try {
            IMind root = createStorage(fixture, "portable-restore-a", "shared_fact");
            createStorage(fixture, "portable-restore-b", "b_only");
            root = open(fixture, root, "portable-restore-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!shared_fact;")));
            Rule restored = findAnyRule(u2, "!shared_fact;");
            assertNotNull(restored);
            assertTrue(restored.isRestored(u2),
                    "fixture did not create an explicit restoration marker");
            fixture.user.setCurrentMind(u2);

            IMind rebased = u2.useStorage("portable-restore-b");
            fixture.user.setCurrentMind(rebased);

            assertEquals(2, rebased.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(rebased.query("?shared_fact;")),
                    "RESTORE of a U0-owned Rule was replayed as ADD over a base where the Rule does not exist");
        } finally {
            fixture.close();
        }
    }

    @Test
    void hiddenLocallyOwnedRuleRemainsOwnedAtItsHistoricalBoundary()
            throws Exception {
        Fixture fixture = fixture("hidden-owned-rule");
        try {
            IMind root = createStorage(fixture, "portable-owned-a", "a_only");
            createStorage(fixture, "portable-owned-b", "b_only");
            root = open(fixture, root, "portable-owned-a");

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("!local_fact;")));
            Rule local = findAnyRule(u1, "!local_fact;");
            assertEquals(u1.getId(), local.getMindId(),
                    "fixture Rule is not locally owned by U1");
            assertTrue(Boolean.TRUE.equals(u1.query("-local_fact;")));
            assertTrue(local.isDeleted(u1));

            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!local_fact;")));
            assertTrue(local.isRestored(u2));
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-owned-b");
            fixture.user.setCurrentMind(rebasedU2);
            assertTrue(Boolean.TRUE.equals(rebasedU2.query("?local_fact;")));

            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            Rule hidden = findAnyRule(rebasedU1, "!local_fact;");
            assertNotNull(hidden,
                    "hidden U1-owned Rule was lost because U1 effective view was empty");
            assertEquals(rebasedU1.getId(), hidden.getMindId(),
                    "hidden Rule provenance moved out of its original explicit boundary");
            assertTrue(hidden.isDeleted(rebasedU1),
                    "hidden U1-owned Rule lost its U1 deletion marker");
        } finally {
            fixture.close();
        }
    }

    @Test
    void hiddenUdfOverrideSurvivesDeleteAndRestoreAtOriginalBoundary()
            throws Exception {
        Fixture fixture = fixture("hidden-udf-override");
        try {
            IMind root = createStorageWithUdf(
                    fixture, "portable-udf-a", "a_only",
                    "=txfn(a){return a;};");
            createStorageWithUdf(
                    fixture, "portable-udf-b", "b_only",
                    "=txfn(a){return 1;};");
            root = open(fixture, root, "portable-udf-a");

            Mind u1 = new Mind(root);
            String overrideSource = "=txfn(a){return 2;};";
            assertTrue(Boolean.TRUE.equals(u1.query(overrideSource)));
            Operation override = u1.getLibrary().find("txfn(1)");
            assertNotNull(override);
            assertTrue(override.asString().contains("return 2"),
                    "fixture did not create the U1 UDF override");
            override.setDeleted(true, u1);
            assertTrue(override.isDeleted(u1));

            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query(overrideSource)));
            Operation restored = u2.getLibrary().find("txfn(1)");
            assertNotNull(restored);
            assertFalse(restored.isDeleted(u2));
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-udf-b");
            fixture.user.setCurrentMind(rebasedU2);
            Operation current = rebasedU2.getLibrary().find("txfn(1)");
            assertNotNull(current);
            assertTrue(current.asString().contains("return 2"));
            assertFalse(current.isDeleted(rebasedU2));

            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            Operation hiddenOverride = rebasedU1.getLibrary().find("txfn(1)");
            assertNotNull(hiddenOverride);
            assertTrue(hiddenOverride.isDeleted(rebasedU1));
            assertTrue(hiddenOverride.asString().contains("return 2"),
                    "hidden U1 UDF override collapsed to the replacement U0 definition");
        } finally {
            fixture.close();
        }
    }

    @Test
    void hiddenRuleCommentOverrideSurvivesDeletionAtOriginalBoundary()
            throws Exception {
        Fixture fixture = fixture("hidden-comment-override");
        try {
            IMind root = createStorage(fixture, "portable-comment-a", "comment_fact");
            Mind aRoot = (Mind) open(fixture, root, "portable-comment-a");
            Rule aRule = findActivePrimaryRule(aRoot, "!comment_fact;");
            aRoot.getComments().add(aRule.getId(), "a-comment");
            fixture.user.checkpoint(aRoot);
            root = aRoot.closeStorage();
            fixture.user.setCurrentMind(root);

            createStorage(fixture, "portable-comment-b", "comment_fact");
            Mind bRoot = (Mind) open(fixture, fixture.user.getCurrentMind(), "portable-comment-b");
            Rule bRule = findActivePrimaryRule(bRoot, "!comment_fact;");
            bRoot.getComments().add(bRule.getId(), "b-comment");
            fixture.user.checkpoint(bRoot);
            root = bRoot.closeStorage();
            fixture.user.setCurrentMind(root);

            root = open(fixture, root, "portable-comment-a");
            Mind u1 = new Mind(root);
            Rule inherited = findActivePrimaryRule(u1, "!comment_fact;");
            u1.getComments().add(inherited.getId(), "u1-hidden-comment");
            assertTrue(Boolean.TRUE.equals(u1.query("-comment_fact;")));

            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!comment_fact;")));
            Rule visible = findActivePrimaryRule(u2, "!comment_fact;");
            assertEquals("u1-hidden-comment",
                    u2.getComments().get(visible.getId()).getComment());
            fixture.user.setCurrentMind(u2);

            Mind rebasedU2 = (Mind) u2.useStorage("portable-comment-b");
            fixture.user.setCurrentMind(rebasedU2);
            Mind rebasedU1 = (Mind) rebasedU2.getNext();
            Rule hiddenRule = findAnyRule(rebasedU1, "!comment_fact;");
            assertNotNull(hiddenRule);
            assertTrue(hiddenRule.isDeleted(rebasedU1));
            assertEquals("u1-hidden-comment",
                    rebasedU1.getComments().get(hiddenRule.getId()).getComment(),
                    "comment COW override was lost while its Rule was hidden");
        } finally {
            fixture.close();
        }
    }

    @Test
    void offlineAuthorialRootAssimilatesIntoStorageU0WithoutCreatingU1()
            throws Exception {
        Fixture fixture = fixture("offline-root-attach");
        try {
            Mind offline = (Mind) fixture.user.getCurrentMind();
            assertTrue(Boolean.TRUE.equals(offline.query("!offline_root_fact;")));
            assertEquals(0, offline.getTransactionLevel());

            IMind attached = offline.useStorage("portable-offline-root");
            fixture.user.setCurrentMind(attached);

            assertEquals(0, attached.getTransactionLevel(),
                    "nonempty offline U0 was shifted into an artificial U1");
            assertTrue(attached.isStorageUsed());
            assertTrue(Boolean.TRUE.equals(attached.query("?offline_root_fact;")));

            IMind reopened = attached.closeStorage();
            fixture.user.setCurrentMind(reopened);
            reopened = reopened.useStorage("portable-offline-root");
            fixture.user.setCurrentMind(reopened);

            assertEquals(0, reopened.getTransactionLevel(),
                    "reopen changed the assimilated U0 transaction depth");
            assertTrue(Boolean.TRUE.equals(reopened.query("?offline_root_fact;")),
                    "assimilated offline U0 authorial state did not persist across reopen");
        } finally {
            fixture.close();
        }
    }

    @Test
    void offlineRootAttachmentPreservesOnlyExistingExplicitBoundaries()
            throws Exception {
        Fixture fixture = fixture("offline-stack-attach");
        try {
            Mind offlineRoot = (Mind) fixture.user.getCurrentMind();
            assertTrue(Boolean.TRUE.equals(offlineRoot.query("!offline_root_fact;")));
            Mind u1 = new Mind(offlineRoot);
            assertTrue(Boolean.TRUE.equals(u1.query("!offline_u1_fact;")));
            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!offline_u2_fact;")));
            fixture.user.setCurrentMind(u2);
            assertEquals(2, u2.getTransactionLevel());

            IMind attached = u2.useStorage("portable-offline-stack");
            fixture.user.setCurrentMind(attached);

            assertEquals(2, attached.getTransactionLevel(),
                    "offline U0 attachment inserted an extra explicit boundary");
            assertTrue(Boolean.TRUE.equals(attached.query("?offline_root_fact;")));
            assertTrue(Boolean.TRUE.equals(attached.query("?offline_u1_fact;")));
            assertTrue(Boolean.TRUE.equals(attached.query("?offline_u2_fact;")));
        } finally {
            fixture.close();
        }
    }

    private Rule findActivePrimaryRule(Mind mind, String origin) throws Exception {
        Rule rule = findAnyRule(mind, origin);
        if (rule == null || rule.isGenerated() || rule.isDeleted(mind)) {
            throw new AssertionError("Active primary Rule not found: " + origin);
        }
        return rule;
    }

    private Rule findAnyRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        return null;
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

    private IMind createStorageWithUdf(Fixture fixture,
                                       String name,
                                       String fact,
                                       String udf) throws Exception {
        IMind mind = createStorage(fixture, name, fact);
        mind = open(fixture, mind, name);
        assertTrue(Boolean.TRUE.equals(mind.query(udf)));
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
        String identity = "storage-portable-layer-" + purpose + "-"
                + UUID.randomUUID();
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
