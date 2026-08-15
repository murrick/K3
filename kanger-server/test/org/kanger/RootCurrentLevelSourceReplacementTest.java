package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Operation;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RootCurrentLevelSourceReplacementTest {
    @Test
    public void rootReplacementIsAtomicAndReplacesSourceProjection() throws Exception {
        String id = "root-source-replace-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        try {
            new UDF().init(user);
            Mind root = new Mind(user);
            user.setCurrentMind(root);
            assertTrue(Boolean.TRUE.equals(root.query("!old;")));
            assertTrue(Boolean.TRUE.equals(root.query("=txfn(a){return a;};")));
            root.getComments().add(CommentFactory.HEADER_ID, "old-header");

            RootCurrentLevelSourceReplacement.Outcome accepted =
                    RootCurrentLevelSourceReplacement.replace(
                            user, "=txfn(a){return a+1;};\n!new;");
            assertTrue(accepted.isAccepted(), accepted.getDescription());
            assertSame(root, user.getCurrentMind());
            assertEquals(0, root.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(root.query("?old;")));
            assertTrue(Boolean.TRUE.equals(root.query("?new;")));

            Operation operation = root.getLibrary().find("txfn(1)");
            assertTrue(operation != null && !operation.isDeleted(root));
            assertTrue(operation.asString().contains("return a+1;"));

            String projected = SourceContextMaterializer.materializeCurrentLevel(root);
            assertTrue(projected.contains("!new;"));
            assertTrue(projected.contains("return a+1;"));
            assertFalse(projected.contains("!old;"));
            assertFalse(projected.contains("old-header"));

            Mind beforeReject = root;
            String before = projected;
            RootCurrentLevelSourceReplacement.Outcome rejected =
                    RootCurrentLevelSourceReplacement.replace(user, "?new;");
            assertFalse(rejected.isAccepted());
            assertSame(beforeReject, user.getCurrentMind());
            assertEquals(before,
                    SourceContextMaterializer.materializeCurrentLevel(root));

            RootCurrentLevelSourceReplacement.Outcome empty =
                    RootCurrentLevelSourceReplacement.replace(user, "");
            assertTrue(empty.isAccepted());
            assertEquals("", SourceContextMaterializer.materializeCurrentLevel(root));
        } finally {
            UserFactory.dropUser(user);
        }
    }

    @Test
    public void rootReplacementPreservesStorageAttachmentAndPersistsOnCheckpoint()
            throws Exception {
        String id = "root-source-storage-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(id, id);
        try {
            new UDF().init(user);
            new DB().init(user);
            IMind mind = new Mind(user);
            user.setCurrentMind(mind);
            mind = mind.useStorage("root-source-storage");
            user.setCurrentMind(mind);
            assertTrue(Boolean.TRUE.equals(mind.query("!stored_old;")));
            user.checkpoint(mind);

            Mind root = (Mind) mind;
            String storageName = root.getStorageName();
            RootCurrentLevelSourceReplacement.Outcome accepted =
                    RootCurrentLevelSourceReplacement.replace(user, "!stored_new;");
            assertTrue(accepted.isAccepted(), accepted.getDescription());
            assertSame(root, user.getCurrentMind());
            assertTrue(root.isStorageUsed());
            assertEquals(storageName, root.getStorageName());
            assertFalse(Boolean.TRUE.equals(root.query("?stored_old;")));
            assertTrue(Boolean.TRUE.equals(root.query("?stored_new;")));

            user.checkpoint(root);
            IMind reopened = root.closeStorage();
            user.setCurrentMind(reopened);
            reopened = reopened.useStorage("root-source-storage");
            user.setCurrentMind(reopened);
            assertFalse(Boolean.TRUE.equals(reopened.query("?stored_old;")),
                    "Root replacement resurrected deleted persistent source after reopen");
            assertTrue(Boolean.TRUE.equals(reopened.query("?stored_new;")),
                    "Replacement persistent source disappeared after reopen");
        } finally {
            UserFactory.dropUser(user);
        }
    }
}
