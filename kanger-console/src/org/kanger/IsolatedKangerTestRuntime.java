/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;
import org.kanger.storage.DB;
import org.kanger.test.KangerVisualTestRunner;
import org.kanger.udf.UDF;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Disposable runtime boundary for the interactive historical test command.
 *
 * <p>The live Console user, Mind, transaction stack and storage are never
 * borrowed by visual tests. Every invocation owns a fresh User and root Mind;
 * database mode additionally owns a private DUMB database rooted below a
 * temporary directory. The complete directory is retired after the run.</p>
 */
final class IsolatedKangerTestRuntime {

    private static final String DATABASE_NAME = "visual-test";

    private IsolatedKangerTestRuntime() {
    }

    static boolean run(String prefix, boolean database) throws Exception {
        Path root = Files.createTempDirectory("kanger-visual-test-");
        User user = new User();
        IMind mind = null;
        Throwable failure = null;
        try {
            Path sources = root.resolve("SRC");
            Path databases = root.resolve("DB");
            Files.createDirectories(sources);
            Files.createDirectories(databases);

            user.setUserDir(directory(root));
            user.setSourceDir(directory(sources));
            user.setDatabaseDir(directory(databases));
            new UDF().init(user);

            mind = new Mind(user);
            if (database) {
                new DB().init(user);
                mind = mind.useStorage(DATABASE_NAME);
            }
            mind = mind.clearWorkspace();
            user.setCurrentMind(mind);

            System.out.println("Visual test runtime: isolated "
                    + (database ? "database" : "offline"));
            return KangerVisualTestRunner.test(mind, "set_" + prefix);
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            Throwable cleanupFailure = null;
            IMind current = user.getCurrentMind();
            if (current == null) {
                current = mind;
            }
            if (current != null && current.isStorageUsed()) {
                try {
                    current.closeStorage();
                } catch (Throwable error) {
                    cleanupFailure = error;
                }
            }
            try {
                deleteTree(root.toFile());
            } catch (Throwable error) {
                if (cleanupFailure == null) {
                    cleanupFailure = error;
                } else if (error != cleanupFailure) {
                    cleanupFailure.addSuppressed(error);
                }
            }
            if (cleanupFailure != null) {
                if (failure != null) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                } else {
                    rethrow(cleanupFailure);
                }
            }
        }
    }

    private static String directory(Path path) {
        String value = path.toAbsolutePath().toString();
        return value.endsWith(File.separator) ? value : value + File.separator;
    }

    private static void deleteTree(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new Exception("Cannot inspect temporary test directory " + file);
            }
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            throw new Exception("Cannot delete temporary test artifact " + file);
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }
}
