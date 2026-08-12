/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RED characterization for the explicit-user-stack command-boundary model.
 *
 * <p>A storage switch changes U0 but must preserve each explicit U1..Un
 * semantic boundary. Operation-local technical transactions are not part of
 * this stack and must already be balanced when these assertions execute.</p>
 */
class CommandBoundaryTransactionModelTest {

    @Test
    void storageUseRebasesBaseAndPreservesTwoExplicitBoundaries() throws Exception {
        Fixture fixture = fixture("two-level-rebase");
        try {
            IMind root = createStorage(fixture, "rebase-a", "a_base");
            createStorage(fixture, "rebase-b", "b_base");
            root = open(fixture, root, "rebase-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_fact;")));

            Mind u2 = new Mind(u1);
            fixture.user.setCurrentMind(u2);
            assertTrue(Boolean.TRUE.equals(u2.query("!u2_fact;")));
            assertEquals(2, u2.getTransactionLevel());

            IMind rebased = u2.useStorage("rebase-b");
            fixture.user.setCurrentMind(rebased);

            assertEquals(2, rebased.getTransactionLevel(),
                    "use collapsed explicit user transaction boundaries");
            assertTrue(Boolean.TRUE.equals(rebased.query("?b_base;")));
            assertTrue(Boolean.TRUE.equals(rebased.query("?u1_fact;")));
            assertTrue(Boolean.TRUE.equals(rebased.query("?u2_fact;")));
            assertFalse(Boolean.TRUE.equals(rebased.query("?a_base;")),
                    "old U0 leaked through the rebased transaction stack");

            Mind levelTwo = (Mind) rebased;
            Mind levelOne = (Mind) levelTwo.getNext();
            levelOne.release(levelTwo);
            fixture.user.setCurrentMind(levelOne);

            assertEquals(1, levelOne.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(levelOne.query("?b_base;")));
            assertTrue(Boolean.TRUE.equals(levelOne.query("?u1_fact;")));
            assertFalse(Boolean.TRUE.equals(levelOne.query("?u2_fact;")));

            Mind newRoot = (Mind) levelOne.getNext();
            newRoot.release(levelOne);
            fixture.user.setCurrentMind(newRoot);

            assertEquals(0, newRoot.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(newRoot.query("?b_base;")));
            assertFalse(Boolean.TRUE.equals(newRoot.query("?u1_fact;")));
            assertFalse(Boolean.TRUE.equals(newRoot.query("?u2_fact;")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void storageUseReplaysInheritedDeletionAtSameExplicitBoundary() throws Exception {
        Fixture fixture = fixture("deletion-rebase");
        try {
            IMind root = createStorage(fixture, "delete-a", "shared_fact");
            createStorage(fixture, "delete-b", "shared_fact");
            root = open(fixture, root, "delete-a");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            Boolean deleted = u1.query("-shared_fact;");
            assertTrue(Boolean.TRUE.equals(deleted),
                    "test fixture did not establish an inherited deletion");
            assertFalse(Boolean.TRUE.equals(u1.query("?shared_fact;")));

            IMind rebased = u1.useStorage("delete-b");
            fixture.user.setCurrentMind(rebased);

            assertEquals(1, rebased.getTransactionLevel());
            assertFalse(Boolean.TRUE.equals(rebased.query("?shared_fact;")),
                    "U1 deletion was lost while rebasing onto the new U0");

            Mind rebasedU1 = (Mind) rebased;
            Mind newRoot = (Mind) rebasedU1.getNext();
            newRoot.release(rebasedU1);
            fixture.user.setCurrentMind(newRoot);

            assertEquals(0, newRoot.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(newRoot.query("?shared_fact;")),
                    "rollback did not reveal the target base state");
        } finally {
            fixture.close();
        }
    }

    @Test
    void successfulGetCommitsIntoCurrentUserLevelWithoutCreatingAnotherLevel()
            throws Exception {
        Fixture fixture = fixture("get-boundary");
        try {
            IMind root = createStorage(fixture, "get-base", "base_fact");
            root = open(fixture, root, "get-base");

            Mind u1 = new Mind(root);
            fixture.user.setCurrentMind(u1);
            assertTrue(Boolean.TRUE.equals(u1.query("!u1_before_get;")));
            assertEquals(1, u1.getTransactionLevel());

            String token = UserFactory.addUser(fixture.user);
            String source = "!get_fact;";
            Path file = Paths.get(fixture.user.getSourceDir()).resolve("tx-get.k");
            Files.createDirectories(file.getParent());
            Files.write(file, source.getBytes(StandardCharsets.UTF_8));

            GetSourceBoundaryReactor reactor = new GetSourceBoundaryReactor(
                    new QueryProcessor());
            JSONObject response = invokeGet(reactor, token, "tx-get.k");

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals(1, response.optInt("transaction", -1),
                    "get exposed its operation-local child as a user transaction");

            IMind current = fixture.user.getCurrentMind();
            assertEquals(1, current.getTransactionLevel(),
                    "get changed explicit user transaction depth");
            assertTrue(Boolean.TRUE.equals(current.query("?base_fact;")));
            assertTrue(Boolean.TRUE.equals(current.query("?u1_before_get;")));
            assertTrue(Boolean.TRUE.equals(current.query("?get_fact;")));

            Mind currentU1 = (Mind) current;
            Mind currentRoot = (Mind) currentU1.getNext();
            currentRoot.release(currentU1);
            fixture.user.setCurrentMind(currentRoot);

            assertEquals(0, currentRoot.getTransactionLevel());
            assertTrue(Boolean.TRUE.equals(currentRoot.query("?base_fact;")));
            assertFalse(Boolean.TRUE.equals(currentRoot.query("?u1_before_get;")));
            assertFalse(Boolean.TRUE.equals(currentRoot.query("?get_fact;")),
                    "get delta escaped the explicit U1 boundary");
        } finally {
            fixture.close();
        }
    }

    private JSONObject invokeGet(GetSourceBoundaryReactor reactor,
                                 String token,
                                 String fileName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("get", fileName)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "get response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IMind createStorage(Fixture fixture, String name, String fact) throws Exception {
        IMind mind = fixture.user.getCurrentMind();
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
            fixture.user.setCurrentMind(mind);
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        assertTrue(Boolean.TRUE.equals(mind.query("!" + fact + ";")));
        fixture.user.checkpoint(mind);
        mind = mind.closeStorage();
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private IMind open(Fixture fixture, IMind mind, String name) throws Exception {
        if (mind.isStorageUsed()) {
            mind = mind.closeStorage();
        }
        mind = mind.useStorage(name);
        fixture.user.setCurrentMind(mind);
        return mind;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "command-tx-model-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user);
    }

    private static final class Fixture {
        private final IUser user;

        private Fixture(IUser user) {
            this.user = user;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
