/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.factory.CommentFactory;
import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.DomainFactory;
import org.kanger.factory.FValueFactory;
import org.kanger.factory.FunctionFactory;
import org.kanger.factory.LibraryFactory;
import org.kanger.factory.PredicateFactory;
import org.kanger.factory.RuleFactory;
import org.kanger.factory.TValueFactory;
import org.kanger.factory.TVariableFactory;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.internal.IBase;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.TVariable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qualification for reindex failure containment on a physically valid DUMB
 * generation with a dangling semantic TVariable reference.
 */
class DumbCorruptedReindexContainmentTest {

    private static final String SOURCE = "!@x a(x) -> b(x);";
    private static final String HEALTHY_FACT = "healthy_recovery";
    private static final String[] CORE_SUFFIXES = {
            ".index", ".store", ".integrity"
    };

    @Test
    void corruptedOfflineReindexPreservesGenerationAndSessionCanRecover()
            throws Exception {
        CorruptGeneration generation = createCorruptGeneration();
        String healthyStorage = "healthy-recovery";
        createHealthyGeneration(
                generation.root, generation.databaseDir, healthyStorage);
        Map<String, String> before = hashGeneration(
                generation.databaseDir, generation.storageName);

        Runtime victim = newRuntime(
                generation.root.resolve("victim-home"), generation.databaseDir);
        Mind original = victim.mind();
        String token = UserFactory.addUser(victim.user);
        try {
            assertFalse(original.isStorageUsed(),
                    "reindex fixture must begin from an offline workspace");

            assertThrows(Exception.class,
                    () -> victim.user.reindex(
                            null, original, generation.storageName),
                    "corrupted generation was silently accepted by reindex");

            assertTrue(victim.user.getCurrentMind() == original,
                    "failed named reindex displaced the published offline Mind");
            assertFalse(original.isStorageUsed(),
                    "failed named reindex attached storage to the offline Mind");
            assertEquals(before,
                    hashGeneration(generation.databaseDir, generation.storageName),
                    "failed reindex mutated the live corrupted generation");

            victim.open(healthyStorage);
            assertTrue(victim.mind().isStorageUsed(),
                    "same session could not attach a healthy storage after failed reindex");
            assertTrue(Boolean.TRUE.equals(
                            victim.mind().query("?" + HEALTHY_FACT + ";")),
                    "same session could not hydrate/query healthy storage after failed reindex");
            victim.user.setCurrentMind(victim.mind().closeStorage());
            assertTrue(victim.db.isClosed(),
                    "same-session healthy close retained physical DUMB handles");
            assertEquals(before,
                    hashGeneration(generation.databaseDir, generation.storageName),
                    "same-session recovery mutated the corrupted source generation");

            JSONObject response = quit(token);
            assertEquals("OK", response.optString("result"), response.toString());
            assertThrows(AuthenticationErrorException.class,
                    () -> UserFactory.getUser(token),
                    "quit after same-session recovery retained the session token");
            assertTrue(victim.db.isClosed(),
                    "quit after same-session recovery retained physical DUMB handles");
            assertEquals(before,
                    hashGeneration(generation.databaseDir, generation.storageName),
                    "quit cleanup mutated the failed reindex source generation");
        } finally {
            if (!victim.db.isClosed()) {
                try {
                    victim.db.close();
                } catch (Exception ignored) {
                    // Preserve the qualification failure as primary.
                }
            }
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Expected after successful quit.
            }
        }
    }

    private CorruptGeneration createCorruptGeneration() throws Exception {
        Path root = Files.createTempDirectory("kanger-corrupt-reindex-");
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);
        String storageName = "dangling-reindex";

        Runtime creator = newRuntime(root.resolve("creator-home"), databaseDir);
        long tvarId;
        try {
            creator.open(storageName);
            creator.mind().query(SOURCE);

            Iterator variables = creator.mind().getTVars().iterator();
            assertTrue(variables.hasNext(),
                    "fixture source did not create a TVariable");
            tvarId = ((TVariable) variables.next()).getId();

            creator.user.setCurrentMind(
                    creator.user.checkpoint(creator.mind()));
            creator.user.setCurrentMind(creator.mind().closeStorage());
            assertTrue(creator.db.isClosed(),
                    "fixture creator failed to close clean generation");
        } finally {
            if (!creator.db.isClosed()) {
                creator.db.close();
            }
        }

        User corruptorUser = new User();
        corruptorUser.setDatabaseDir(withSeparator(databaseDir));
        DB corruptor = new DB();
        corruptor.init(corruptorUser);
        try {
            IBase variables = acquireCanonicalBases(corruptor, storageName);
            assertTrue(variables.containsKey(tvarId),
                    "clean generation lacks persisted TVariable " + tvarId);
            variables.delete(tvarId);
            corruptor.flush();
            assertFalse(variables.containsKey(tvarId),
                    "failed to create dangling TVariable fixture");
        } finally {
            corruptor.close();
        }

        return new CorruptGeneration(root, databaseDir, storageName);
    }

    private void createHealthyGeneration(Path root,
                                         Path databaseDir,
                                         String storageName) throws Exception {
        Runtime creator = newRuntime(root.resolve("healthy-home"), databaseDir);
        try {
            creator.open(storageName);
            creator.mind().query("!" + HEALTHY_FACT + ";");
            creator.user.setCurrentMind(
                    creator.user.checkpoint(creator.mind()));
            creator.user.setCurrentMind(creator.mind().closeStorage());
            assertTrue(creator.db.isClosed(),
                    "healthy recovery fixture failed to close cleanly");
        } finally {
            if (!creator.db.isClosed()) {
                creator.db.close();
            }
        }
    }

    private Runtime newRuntime(Path home, Path databaseDir) throws Exception {
        Files.createDirectories(home);
        Files.createDirectories(databaseDir);

        User user = new User();
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user.setId(id == 0L ? 1L : id);
        user.setUserDir(withSeparator(home));
        user.setDatabaseDir(withSeparator(databaseDir));
        new UDF().init(user);
        DB db = new DB();
        db.init(user);
        user.setCurrentMind(new Mind(user));
        return new Runtime(user, db);
    }

    private IBase acquireCanonicalBases(DB db, String storageName)
            throws Exception {
        db.use(storageName);
        db.getBase(DictionaryFactory.SCHEMA);
        db.getBase(DomainFactory.SCHEMA);
        db.getBase(FunctionFactory.SCHEMA);
        db.getBase(PredicateFactory.SCHEMA);
        db.getBase(RuleFactory.SCHEMA);
        IBase variables = db.getBase(TVariableFactory.SCHEMA);
        db.getBase(LibraryFactory.SCHEMA);
        db.getBase(TValueFactory.SCHEMA);
        db.getBase(FValueFactory.SCHEMA);
        db.getBase(CommentFactory.SCHEMA);
        return variables;
    }

    private Map<String, String> hashGeneration(Path databaseDir,
                                                String storageName)
            throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Path base = databaseDir.resolve(storageName);
        for (String suffix : CORE_SUFFIXES) {
            Path file = new File(base.toString() + suffix).toPath();
            assertTrue(Files.isRegularFile(file),
                    "generation core file is missing: " + file);
            result.put(suffix, sha256(Files.readAllBytes(file)));
        }
        return result;
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte one : digest) {
            result.append(String.format("%02x", one & 0xff));
        }
        return result.toString();
    }

    private JSONObject quit(String token) throws Exception {
        IReactor<JSONObject> reactor = new MindLifecycleReactor(
                new QueryProcessor());
        Object response = reactor.run(new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("quit", ""))));
        assertTrue(response instanceof JSONObject,
                "quit response is not JSON: " + response);
        return (JSONObject) response;
    }

    private static String withSeparator(Path path) {
        return path.toAbsolutePath().toString() + File.separator;
    }

    private static final class Runtime {
        private final User user;
        private final DB db;

        private Runtime(User user, DB db) {
            this.user = user;
            this.db = db;
        }

        private Mind mind() {
            return (Mind) user.getCurrentMind();
        }

        private void open(String storageName) throws Exception {
            user.setCurrentMind(mind().useStorage(storageName));
        }
    }

    private static final class CorruptGeneration {
        private final Path root;
        private final Path databaseDir;
        private final String storageName;

        private CorruptGeneration(Path root,
                                  Path databaseDir,
                                  String storageName) {
            this.root = root;
            this.databaseDir = databaseDir;
            this.storageName = storageName;
        }
    }
}
