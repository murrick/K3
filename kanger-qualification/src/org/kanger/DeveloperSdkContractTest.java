/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the minimum standalone contracts that the KANGER 3.7.0
 * Developer Distribution must expose without Console account plumbing,
 * Server, or Browser/UI participation.
 */
public class DeveloperSdkContractTest {

    @Test
    public void standaloneUserMindTransactionAndStorageLifecycle() throws Exception {
        Path home = Files.createTempDirectory("kanger-sdk-contract-");
        IUser user = new User();
        IMind mind = null;
        try {
            Path sources = home.resolve("SRC");
            Path databases = home.resolve("DB");
            Files.createDirectories(sources);
            Files.createDirectories(databases);

            user.setUserDir(directory(home));
            user.setSourceDir(directory(sources));
            user.setDatabaseDir(directory(databases));

            // Canonical standalone SDK entry: no UserFactory and no Server/UI.
            mind = new Mind(user);
            assertTrue(Boolean.TRUE.equals(mind.query("!sdk_root;")));
            assertTrue(Boolean.TRUE.equals(mind.query("?sdk_root;")));

            // Canonical explicit transaction: child Mind, then parent settles it.
            IMind transaction = new Mind(mind);
            assertTrue(Boolean.TRUE.equals(transaction.query("!sdk_transaction;")));
            assertTrue(mind.commit(transaction));
            assertTrue(Boolean.TRUE.equals(mind.query("?sdk_transaction;")));

            // DUMB is a runtime provider. Storage lifecycle authority is IUser.
            new DB().init(user);
            mind = user.use(mind, "sdk-contract");
            assertTrue(mind.isStorageUsed());
            assertTrue(Boolean.TRUE.equals(mind.query("!sdk_persistent;")));

            mind = user.checkpoint(mind);
            mind = user.close(mind);
            assertFalse(mind.isStorageUsed());

            mind = user.use(mind, "sdk-contract");
            assertTrue(Boolean.TRUE.equals(mind.query("?sdk_persistent;")));
            mind = user.close(mind);
            assertFalse(mind.isStorageUsed());
        } finally {
            if (mind != null && mind.isStorageUsed()) {
                try {
                    user.close(mind);
                } catch (Exception ignored) {
                    // Preserve the original test failure; temporary home cleanup
                    // below is best-effort in the same failure path.
                }
            }
            deleteTree(home.toFile());
        }
    }

    private static String directory(Path path) {
        String value = path.toAbsolutePath().toString();
        return value.endsWith(File.separator) ? value : value + File.separator;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        file.delete();
    }
}
