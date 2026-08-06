/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.Enums;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression qualification for the explicit transaction/storage lifecycle.
 */
class ExplicitStorageLifecycleTest {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @Test
    void rootCommitIsRepeatableCheckpointAndLeavesStorageOpen() throws Exception {
        Fixture fixture = fixture("root-commit");
        try {
            use(fixture, "root.commit");
            IMind root = fixture.user.getCurrentMind();
            mutate(fixture, "root_commit_fact");

            JSONObject first = transaction(fixture, "commit");
            assertEquals("OK", first.optString("result"), first.toString());
            assertEquals("Storage checkpoint completed",
                    first.optString("description"));
            assertSame(root, fixture.user.getCurrentMind());
            assertTrue(root.isStorageUsed());
            assertEquals(0, root.getTransactionLevel());
            assertEquals("yes", ask(fixture, "root_commit_fact")
                    .optString("response"));

            JSONObject second = transaction(fixture, "commit");
            assertEquals("OK", second.optString("result"), second.toString());
            assertSame(root, fixture.user.getCurrentMind());
            assertTrue(root.isStorageUsed());

            mutate(fixture, "after_root_commit");
            close(fixture);
            assertFalse(fixture.user.getCurrentMind().isStorageUsed());

            JSONObject reopen = use(fixture, "root.commit");
            assertEquals("OK", reopen.optString("result"), reopen.toString());
            assertEquals("yes", ask(fixture, "root_commit_fact")
                    .optString("response"));
            assertEquals("yes", ask(fixture, "after_root_commit")
                    .optString("response"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void nestedCommitExplicitCloseReopenPassesManifestValidation() throws Exception {
        Fixture fixture = fixture("nested-reopen");
        try {
            use(fixture, "nested.reopen");
            assertEquals(1, transaction(fixture, "create")
                    .optInt("transaction", -1));
            assertEquals(2, transaction(fixture, "create")
                    .optInt("transaction", -1));
            mutate(fixture, "nested_reopen_fact");

            assertEquals(1, transaction(fixture, "commit")
                    .optInt("transaction", -1));
            JSONObject rootCommit = transaction(fixture, "commit");
            assertEquals("OK", rootCommit.optString("result"),
                    rootCommit.toString());
            assertEquals(0, rootCommit.optInt("transaction", -1));
            assertTrue(fixture.user.getCurrentMind().isStorageUsed());

            close(fixture);
            JSONObject reopen = use(fixture, "nested.reopen");
            assertEquals("OK", reopen.optString("result"), reopen.toString());
            assertEquals("yes", ask(fixture, "nested_reopen_fact")
                    .optString("response"));

            for (int cycle = 0; cycle < 3; ++cycle) {
                close(fixture);
                reopen = use(fixture, "nested.reopen");
                assertEquals("OK", reopen.optString("result"),
                        "reopen cycle " + cycle + ": " + reopen);
                assertEquals("yes", ask(fixture, "nested_reopen_fact")
                        .optString("response"));
            }
        } finally {
            fixture.close();
        }
    }

    @Test
    void useWhileOpenRejectsBeforeTargetOpenOrValidation() throws Exception {
        Fixture fixture = fixture("use-reject");
        Path corruptStore = null;
        try {
            use(fixture, "use.reject.current");
            IMind active = fixture.user.getCurrentMind();
            String source = active.getSourceCode();
            int level = active.getTransactionLevel();
            Map<String, String> currentBefore = hashGeneration(
                    fixture.user, active.getStorageName());

            String corruptName = "use" + Enums.FILE_SEPARATOR + "reject-target";
            Path corruptBase = Paths.get(fixture.user.getDatabaseDir())
                    .resolve(corruptName);
            Files.createDirectories(corruptBase.getParent());
            corruptStore = Paths.get(corruptBase.toString() + ".store");
            byte[] sentinel = new byte[]{0x41, 0x42, 0x43, 0x44};
            Files.write(corruptStore, sentinel);
            String corruptBefore = sha256(sentinel);

            JSONObject same = use(fixture, "use.reject.current");
            assertEquals("error", same.optString("result"), same.toString());
            assertEquals("storage_already_open", same.optString("code"));

            JSONObject other = use(fixture, "use.reject-target");
            assertEquals("error", other.optString("result"), other.toString());
            assertEquals("storage_already_open", other.optString("code"));

            assertSame(active, fixture.user.getCurrentMind());
            assertEquals(level, active.getTransactionLevel());
            assertEquals(source, active.getSourceCode());
            assertEquals(currentBefore,
                    hashGeneration(fixture.user, active.getStorageName()));
            assertEquals(corruptBefore,
                    sha256(Files.readAllBytes(corruptStore)));
            assertFalse(Files.exists(Paths.get(corruptBase + ".integrity")),
                    "Rejected use validated the target generation");
            assertFalse(Files.exists(Paths.get(corruptBase + ".index")),
                    "Rejected use opened the target generation");
        } finally {
            if (corruptStore != null) {
                Files.deleteIfExists(corruptStore);
            }
            fixture.close();
        }
    }

    @Test
    void closeRejectsActiveLevelsAndPreservesTransientRules() throws Exception {
        Fixture fixture = fixture("close-reject");
        try {
            use(fixture, "close.reject");
            String storageName = fixture.user.getCurrentMind().getStorageName();
            Map<String, String> durableBefore = hashGeneration(
                    fixture.user, storageName);

            transaction(fixture, "create");
            mutate(fixture, "transient_level_one");
            IMind levelOne = fixture.user.getCurrentMind();
            JSONObject rejectedOne = closeResponse(fixture);
            assertEquals("error", rejectedOne.optString("result"),
                    rejectedOne.toString());
            assertEquals("transaction_open", rejectedOne.optString("code"));
            assertSame(levelOne, fixture.user.getCurrentMind());
            assertEquals(1, levelOne.getTransactionLevel());
            assertTrue(levelOne.isStorageUsed());
            assertEquals("yes", ask(fixture, "transient_level_one")
                    .optString("response"));
            assertEquals(durableBefore, hashGeneration(fixture.user, storageName));

            transaction(fixture, "create");
            mutate(fixture, "transient_level_two");
            IMind levelTwo = fixture.user.getCurrentMind();
            JSONObject rejectedTwo = closeResponse(fixture);
            assertEquals("error", rejectedTwo.optString("result"),
                    rejectedTwo.toString());
            assertEquals("transaction_open", rejectedTwo.optString("code"));
            assertSame(levelTwo, fixture.user.getCurrentMind());
            assertEquals(2, levelTwo.getTransactionLevel());
            assertTrue(levelTwo.isStorageUsed());
            assertEquals("yes", ask(fixture, "transient_level_two")
                    .optString("response"));
            assertEquals(durableBefore, hashGeneration(fixture.user, storageName));

            transaction(fixture, "rollback");
            transaction(fixture, "rollback");
            close(fixture);
            use(fixture, "close.reject");
            assertNotEquals("yes", ask(fixture, "transient_level_one")
                    .optString("response"));
            assertNotEquals("yes", ask(fixture, "transient_level_two")
                    .optString("response"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void rollbackCloseReopenAndCrossStorageCyclesRemainExplicit() throws Exception {
        Fixture fixture = fixture("rollback-cycles");
        try {
            use(fixture, "cycles.a");
            mutate(fixture, "cycle_a_baseline");
            transaction(fixture, "create");
            mutate(fixture, "cycle_a_rolled_back");
            transaction(fixture, "rollback");
            close(fixture);

            use(fixture, "cycles.a");
            assertEquals("yes", ask(fixture, "cycle_a_baseline")
                    .optString("response"));
            assertNotEquals("yes", ask(fixture, "cycle_a_rolled_back")
                    .optString("response"));
            close(fixture);

            use(fixture, "cycles.b");
            mutate(fixture, "cycle_b_baseline");
            close(fixture);

            use(fixture, "cycles.a");
            assertEquals("yes", ask(fixture, "cycle_a_baseline")
                    .optString("response"));
            assertNotEquals("yes", ask(fixture, "cycle_b_baseline")
                    .optString("response"));
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "explicit-storage-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        IReactor<JSONObject> reactor = new ExplicitStorageLifecycleReactor(
                new DestructiveStopLossReactor(
                        new MindLifecycleReactor(new QueryProcessor())));
        return new Fixture(user, token, reactor);
    }

    private JSONObject use(Fixture fixture, String name) throws Exception {
        return invoke(fixture, "command", new JSONObject()
                .put("token", fixture.token)
                .put("use", name));
    }

    private void close(Fixture fixture) throws Exception {
        JSONObject response = closeResponse(fixture);
        assertEquals("OK", response.optString("result"), response.toString());
    }

    private JSONObject closeResponse(Fixture fixture) throws Exception {
        return invoke(fixture, "command", new JSONObject()
                .put("token", fixture.token)
                .put("close", ""));
    }

    private JSONObject transaction(Fixture fixture, String action)
            throws Exception {
        return invoke(fixture, "query", new JSONObject()
                .put("token", fixture.token)
                .put("transaction", action));
    }

    private void mutate(Fixture fixture, String fact) throws Exception {
        JSONObject response = query(fixture, "!" + fact + ";");
        assertEquals("OK", response.optString("result"), response.toString());
    }

    private JSONObject ask(Fixture fixture, String fact) throws Exception {
        return query(fixture, "?" + fact + ";");
    }

    private JSONObject query(Fixture fixture, String source) throws Exception {
        return invoke(fixture, "query", new JSONObject()
                .put("token", fixture.token)
                .put("request", URLEncoder.encode(source, "UTF-8")));
    }

    private JSONObject invoke(Fixture fixture,
                              String context,
                              JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = fixture.reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Lifecycle response is not JSON: " + response);
        return (JSONObject) response;
    }

    private Map<String, String> hashGeneration(IUser user, String storageName)
            throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            Path file = Paths.get(base.toString() + suffix);
            if (Files.exists(file)) {
                result.put(suffix, sha256(Files.readAllBytes(file)));
            }
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

    private static final class Fixture {
        private final IUser user;
        private final String token;
        private final IReactor<JSONObject> reactor;

        private Fixture(IUser user,
                        String token,
                        IReactor<JSONObject> reactor) {
            this.user = user;
            this.token = token;
            this.reactor = reactor;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // A completed test may already have closed the session.
            }
        }
    }
}
