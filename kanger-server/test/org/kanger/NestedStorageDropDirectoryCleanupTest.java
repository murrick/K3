/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for physical parent-directory cleanup after nested DUMB drop. */
class NestedStorageDropDirectoryCleanupTest {

    @Test
    void droppingLastNestedStoragePrunesEmptyParentsButKeepsDatabaseRoot()
            throws Exception {
        IUser user = user("last-nested");
        DB db = new DB();
        db.init(user);
        try {
            String physical = physical("zz.error.smoke");
            createStorage(db, physical);

            File root = new File(user.getDatabaseDir()).getAbsoluteFile();
            File zz = new File(root, "zz");
            File error = new File(zz, "error");
            assertTrue(error.isDirectory(), error.getPath());

            db.remove(physical);

            assertFalse(error.exists(), error.getPath());
            assertFalse(zz.exists(), zz.getPath());
            assertTrue(root.isDirectory(), root.getPath());
        } finally {
            UserFactory.dropUser(user);
        }
    }

    @Test
    void droppingNestedStorageKeepsSharedParentsWhileSiblingExists()
            throws Exception {
        IUser user = user("nested-sibling");
        DB db = new DB();
        db.init(user);
        try {
            String smoke = physical("zz.error.smoke");
            String keep = physical("zz.error.keep");
            createStorage(db, smoke);
            createStorage(db, keep);

            File root = new File(user.getDatabaseDir()).getAbsoluteFile();
            File zz = new File(root, "zz");
            File error = new File(zz, "error");
            File keepStore = new File(root, keep + ".store");
            assertTrue(keepStore.isFile(), keepStore.getPath());

            db.remove(smoke);

            assertTrue(error.isDirectory(), error.getPath());
            assertTrue(zz.isDirectory(), zz.getPath());
            assertTrue(keepStore.isFile(), keepStore.getPath());

            db.remove(keep);
            assertFalse(error.exists(), error.getPath());
            assertFalse(zz.exists(), zz.getPath());
            assertTrue(root.isDirectory(), root.getPath());
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private IUser user(String purpose) throws Exception {
        String identity = "nested-storage-drop-cleanup-" + purpose + "-"
                + UUID.randomUUID();
        return UserFactory.createUser(identity, identity);
    }

    private void createStorage(DB db, String physical) throws Exception {
        db.use(physical);
        db.getBase("cleanup");
        db.close();
    }

    private String physical(String logical) {
        return logical.replace(".", Enums.FILE_SEPARATOR);
    }
}
