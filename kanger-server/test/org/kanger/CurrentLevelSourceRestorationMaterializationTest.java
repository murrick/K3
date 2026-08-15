/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;
import org.kanger.units.Operation;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for source-representable restoration delta. */
public class CurrentLevelSourceRestorationMaterializationTest {

    @Test
    public void restoredInheritedRuleAndUdfRemainVisibleInCurrentLevelProjection()
            throws Exception {
        String identity = "source-restoration-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            assertTrue(Boolean.TRUE.equals(root.query("!base;")));
            assertTrue(Boolean.TRUE.equals(root.query("!unchanged;")));
            assertTrue(Boolean.TRUE.equals(root.query("=txfn(a){return a;};")));

            Mind u1 = new Mind(root);
            assertTrue(Boolean.TRUE.equals(u1.query("-base;")));
            Operation deletedUdf = u1.getLibrary().find("txfn(1)");
            assertNotNull(deletedUdf);
            deletedUdf.setDeleted(true, u1);

            Mind u2 = new Mind(u1);
            assertTrue(Boolean.TRUE.equals(u2.query("!base;")));
            assertTrue(Boolean.TRUE.equals(u2.query("=txfn(a){return a;};")));

            IRule restoredBase = null;
            for (IRule candidate : u2.getRules()) {
                if ("!base;".equals(candidate.getOrigin())) {
                    restoredBase = candidate;
                    break;
                }
            }
            assertNotNull(restoredBase, "Restored canonical Rule disappeared from U2");
            assertTrue(restoredBase.isRestored(u2),
                    "Restored canonical Rule lost its U2 restoration marker");

            String projected = SourceContextMaterializer.materializeCurrentLevel(u2);
            assertTrue(projected.contains("!base;"),
                    "Restored inherited Rule disappeared from U2 source projection");
            assertTrue(projected.contains("=txfn(a){return a;};"),
                    "Restored inherited UDF disappeared from U2 source projection");
            assertFalse(projected.contains("!unchanged;"),
                    "Unchanged parent Rule leaked into U2 source projection");

            u1.release(u2);
            root.release(u1);
        } finally {
            UserFactory.dropUser(user);
        }
    }
}
