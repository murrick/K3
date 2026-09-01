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
import java.util.Iterator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression for a physically valid DUMB generation whose semantic
 * graph contains a dangling TVariable reference. The fixture is created only
 * through normal KANGER persistence and IBase.delete(id); no file bytes are
 * edited by the test.
 */
class DumbDanglingTVariableContainmentTest {

    private static final String SOURCE = "!@x a(x) -> b(x);";

    @Test
    void poisonedRealDumbGenerationStillAllowsQuitAndPhysicalRelease()
            throws Exception {
        CorruptGeneration generation = createCorruptGeneration("quit");
        Runtime victim = openRuntime(generation, "victim-quit");
        String token = UserFactory.addUser(victim.user);
        try {
            victim.open(generation.storageName);

            /*
             * Do not pre-hydrate the corrupted Rule here. The normal logout
             * path must encounter the corruption naturally while attempting
             * its Mind-aware checkpoint/close, then fall back to emergency
             * physical cleanup of the already detached runtime.
             */
            JSONObject response = quit(token);
            assertEquals("OK", response.optString("result"), response.toString());
            assertThrows(AuthenticationErrorException.class,
                    () -> UserFactory.getUser(token),
                    "quit returned success but retained the corrupted session");
            assertTrue(victim.db.isClosed(),
                    "quit detached the token but retained physical DUMB handles");
        } finally {
            if (!victim.db.isClosed()) {
                try {
                    victim.db.close();
                } catch (Exception ignored) {
                    // Preserve the regression failure as the primary result.
                }
            }
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Expected after successful quit.
            }
        }
    }

    @Test
    void danglingSemanticReferenceFailsFastDuringRuleTraversal()
            throws Exception {
        CorruptGeneration generation = createCorruptGeneration("hydration");
        Runtime victim = openRuntime(generation, "victim-hydration");
        try {
            victim.open(generation.storageName);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> {
                        Iterator rules = victim.mind().getRules().iterator();
                        assertTrue(rules.hasNext(),
                                "corruption fixture lost the persisted Rule itself");
                        rules.next();
                    },
                    "persistent hydration failure was silently converted to semantic null");

            Throwable cause = failure.getCause();
            assertNotNull(cause,
                    "iterator fail-fast bridge discarded the hydration cause");
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof NullPointerException,
                    "unexpected root hydration failure: " + cause);
        } finally {
            try {
                MindRuntimeLifecycle.close(victim.user);
            } catch (Exception ignored) {
                // This test deliberately exercises a corrupted generation.
            }
            if (!victim.db.isClosed()) {
                try {
                    victim.db.close();
                } catch (Exception ignored) {
                    // best effort fixture cleanup
                }
            }
        }
    }

    private CorruptGeneration createCorruptGeneration(String purpose)
            throws Exception {
        Path root = Files.createTempDirectory("kanger-dangling-tvar-" + purpose + "-");
        Path databaseDir = root.resolve("database");
        Files.createDirectories(databaseDir);
        String storageName = "dangling-" + purpose;

        Runtime creator = newRuntime(root.resolve("creator-home"), databaseDir);
        long tvarId;
        try {
            creator.open(storageName);
            creator.mind().query(SOURCE);

            Iterator variables = creator.mind().getTVars().iterator();
            assertTrue(variables.hasNext(),
                    "fixture source did not create a TVariable: " + SOURCE);
            TVariable variable = (TVariable) variables.next();
            tvarId = variable.getId();

            creator.user.setCurrentMind(
                    creator.user.checkpoint(creator.mind()));
            creator.user.setCurrentMind(creator.mind().closeStorage());
            assertTrue(creator.db.isClosed(),
                    "fixture creator failed to close the clean generation");
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
                    "clean generation does not contain persisted TVariable " + tvarId);
            variables.delete(tvarId);
            corruptor.flush();
            assertFalse(variables.containsKey(tvarId),
                    "IBase.delete did not remove TVariable " + tvarId);
        } finally {
            corruptor.close();
        }

        return new CorruptGeneration(root, databaseDir, storageName, tvarId);
    }

    private Runtime openRuntime(CorruptGeneration generation, String purpose)
            throws Exception {
        return newRuntime(generation.root.resolve(purpose + "-home"),
                generation.databaseDir);
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
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
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
        private final long tvarId;

        private CorruptGeneration(Path root,
                                  Path databaseDir,
                                  String storageName,
                                  long tvarId) {
            this.root = root;
            this.databaseDir = databaseDir;
            this.storageName = storageName;
            this.tvarId = tvarId;
        }
    }
}
