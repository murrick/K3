/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Permission;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Focused invariant gate for exception-atomic DUMB reindex publication. */
public final class DBReindexSwapAtomicitySafetyRunner {

    private DBReindexSwapAtomicitySafetyRunner() {
    }

    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("kanger-dumb-reindex-swap-")
                .toFile();
        SecurityManager previous = System.getSecurityManager();
        try {
            String directoryName = directory.getAbsolutePath() + File.separator;
            String generation = "generation";
            String dbPath = directoryName + generation;
            String temporaryPath = dbPath + "-temporary";

            byte[] indexBefore = "original-index".getBytes(StandardCharsets.UTF_8);
            byte[] storeBefore = "original-store".getBytes(StandardCharsets.UTF_8);
            byte[] integrityBefore = "original-integrity"
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(new File(dbPath + ".index").toPath(), indexBefore);
            Files.write(new File(dbPath + ".store").toPath(), storeBefore);
            Files.write(new File(dbPath + ".integrity").toPath(), integrityBefore);

            User user = new User();
            user.setDatabaseDir(directoryName);
            Mind mind = new Mind(user);
            DB db = new DB();
            db.init(user);
            db.use(generation);

            Map<String, IBase> registry = new LinkedHashMap<String, IBase>();
            registry.put("dictionary", new ProbeBase("dictionary"));
            setRegistry(db, registry);

            OneShotRenameFailure injector = new OneShotRenameFailure(
                    new File(dbPath + ".index"));
            System.setSecurityManager(injector);

            SecurityException observed = null;
            try {
                db.reindex(null, mind);
            } catch (SecurityException expected) {
                observed = expected;
            } finally {
                System.setSecurityManager(previous);
            }

            require(observed != null,
                    "The injected reindex publication failure must propagate");
            require(injector.wasTriggered(),
                    "The failure must occur while publishing the temporary index");
            requireFileEquals(new File(dbPath + ".index"), indexBefore,
                    "Original index must survive a failed publication");
            requireFileEquals(new File(dbPath + ".store"), storeBefore,
                    "Original store must survive a failed publication");
            requireFileEquals(new File(dbPath + ".integrity"), integrityBefore,
                    "Original integrity manifest must survive a failed publication");
            require(new File(temporaryPath + ".index").isFile(),
                    "Temporary index must remain available after rollback");
            require(new File(temporaryPath + ".store").isFile(),
                    "Temporary store must remain available after rollback");
            require(new File(temporaryPath + ".integrity").isFile(),
                    "Temporary integrity manifest must remain available after rollback");

            System.out.println("DUMB reindex swap atomicity invariant: success");
        } finally {
            if (System.getSecurityManager() != previous) {
                System.setSecurityManager(previous);
            }
            deleteRecursively(directory);
        }
    }

    private static void requireFileEquals(File file, byte[] expected,
                                          String message) throws Exception {
        require(file.isFile(), message + " (file missing)");
        byte[] actual = Files.readAllBytes(file.toPath());
        require(java.util.Arrays.equals(expected, actual),
                message + " (content changed)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void setRegistry(DB db, Map<String, IBase> registry)
            throws Exception {
        Field field = DB.class.getDeclaredField("bases");
        field.setAccessible(true);
        field.set(db, registry);
    }

    private static final class OneShotRenameFailure extends SecurityManager {
        private final String liveIndex;
        private boolean liveIndexDeleted;
        private boolean triggered;

        private OneShotRenameFailure(File liveIndex) {
            this.liveIndex = liveIndex.getAbsolutePath();
        }

        private boolean wasTriggered() {
            return triggered;
        }

        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }

        @Override
        public void checkDelete(String file) {
            if (liveIndex.equals(new File(file).getAbsolutePath())) {
                liveIndexDeleted = true;
            }
        }

        @Override
        public void checkWrite(String file) {
            if (liveIndexDeleted && !triggered
                    && liveIndex.equals(new File(file).getAbsolutePath())) {
                triggered = true;
                throw new SecurityException("injected reindex rename failure");
            }
        }
    }

    private static final class ProbeBase implements IBase {
        private final String name;

        private ProbeBase(String name) {
            this.name = name;
        }

        @Override
        public void add(IStep one) {
        }

        @Override
        public void update(IStep one) {
        }

        @Override
        public IStep get(long id) {
            return null;
        }

        @Override
        public void clearCache() {
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public void deleteAll(Collection<Long> ids) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void reindex(IBase to, IMind mind) {
        }

        @Override
        public boolean containsKey(long id) {
            return false;
        }

        @Override
        public IStep getRoot() {
            return null;
        }

        @Override
        public IStep getTop() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getUsedCacheSize() {
            return 0L;
        }

        @Override
        public long getMaxCacheSize() {
            return 0L;
        }

        @Override
        public long lastId() {
            return 0L;
        }

        @Override
        public long nextId() {
            return 0L;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public Class getUdf() {
            return null;
        }
    }
}
