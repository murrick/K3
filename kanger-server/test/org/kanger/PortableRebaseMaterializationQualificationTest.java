/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification that portable semantic visibility state becomes native again
 * when the referenced declaration reappears on a later storage baseline.
 */
class PortableRebaseMaterializationQualificationTest {

    @Test
    void portableRuleDeletionMaterializesAgainstReplacementStorageId()
            throws Exception {
        String identity = "portable-materialization-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            new DB().init(user);
            IMind current = new Mind(user);
            user.setCurrentMind(current);

            current = createStorage(user, current,
                    "portable-material-a", "shared_fact");
            current = createStorage(user, current,
                    "portable-material-b", "b_only");
            current = createStorage(user, current,
                    "portable-material-c", "shared_fact");
            current = open(user, current, "portable-material-a");

            Mind u1 = new Mind(current);
            user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("-shared_fact;")));

            Mind onB = (Mind) u1.useStorage("portable-material-b");
            user.setCurrentMind(onB);
            Mind onC = (Mind) onB.useStorage("portable-material-c");
            user.setCurrentMind(onC);

            assertEquals(1, onC.getTransactionLevel());
            Rule cRule = findRule(onC, "!shared_fact;");
            assertNotNull(cRule, "C/U0 Rule was not reconstructed for U1 visibility state");
            Set<Long> deleted = onC.getDeleted().get(UnitType.RULE);
            assertNotNull(deleted,
                    "portable deletion did not materialize into native RULE deletion state");
            assertTrue(deleted.contains(cRule.getId()),
                    "portable deletion retained an old storage id instead of C's canonical id");
            assertTrue(cRule.isDeleted(onC));
            assertFalse(Boolean.TRUE.equals(onC.query("?shared_fact;")));

            Mind cRoot = (Mind) onC.getNext();
            cRoot.release(onC);
            user.setCurrentMind(cRoot);
            assertTrue(Boolean.TRUE.equals(cRoot.query("?shared_fact;")),
                    "rollback did not reveal the untouched C/U0 declaration");
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private IMind createStorage(IUser user, IMind mind, String name, String fact)
            throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query("!" + fact + ";")));
        user.checkpoint(mind);
        mind = mind.closeStorage();
        user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(IUser user, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        user.setCurrentMind(mind);
        return mind;
    }

    private Rule findRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        return null;
    }
}
