/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandParser;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of physical directory cleanup after canonical storage drop. */
class StorageDropPhysicalPathPruningTest {

    @Test
    void dropPrunesEmptyParentsButPreservesDatabaseRoot() throws Exception {
        Fixture fixture = fixture("empty-parents");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String logical = "prune.empty.leaf";

            IMind active = processor.execute(
                    parser.parse("storage use " + logical), fixture.user).getMind();
            assertTrue(Boolean.TRUE.equals(active.query("!prune_empty_fact;")));

            Path root = databaseRoot(fixture.user);
            Path first = root.resolve("prune");
            Path parent = first.resolve("empty");
            assertTrue(Files.isDirectory(parent));

            processor.execute(parser.parse("storage drop " + logical), fixture.user);

            assertFalse(Files.exists(parent),
                    "Drop left the empty leaf parent directory behind");
            assertFalse(Files.exists(first),
                    "Drop left the empty storage namespace directory behind");
            assertTrue(Files.isDirectory(root),
                    "Storage pruning must never remove the database root");
        } finally {
            fixture.close();
        }
    }

    @Test
    void dropStopsAtSharedParentAndPreservesSiblingStorage() throws Exception {
        Fixture fixture = fixture("shared-parent");
        try {
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            String keep = "prune.shared.keep";
            String drop = "prune.shared.drop";

            IMind active = processor.execute(
                    parser.parse("storage use " + keep), fixture.user).getMind();
            assertTrue(Boolean.TRUE.equals(active.query("!keep_fact;")));

            active = processor.execute(
                    parser.parse("storage use " + drop), fixture.user).getMind();
            assertTrue(Boolean.TRUE.equals(active.query("!drop_fact;")));

            Path root = databaseRoot(fixture.user);
            Path shared = root.resolve("prune").resolve("shared");
            Path keepStore = Paths.get(shared.resolve("keep").toString() + ".store");
            assertTrue(Files.isRegularFile(keepStore));

            processor.execute(parser.parse("storage drop " + drop), fixture.user);

            assertTrue(Files.isDirectory(shared),
                    "Drop pruned a parent still owned by a sibling storage");
            assertTrue(Files.isRegularFile(keepStore),
                    "Drop removed a sibling storage artifact");

            processor.execute(parser.parse("storage drop " + keep), fixture.user);
            assertFalse(Files.exists(root.resolve("prune")),
                    "Final sibling drop did not prune the now-empty namespace");
            assertTrue(Files.isDirectory(root),
                    "Storage pruning must never remove the database root");
        } finally {
            fixture.close();
        }
    }

    private Path databaseRoot(IUser user) throws Exception {
        return Paths.get(user.getDatabaseDir()).toRealPath();
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "storage-drop-prune-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, token);
    }

    private static final class Fixture {
        private final IUser user;
        private final String token;

        private Fixture(IUser user, String token) {
            this.user = user;
            this.token = token;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Isolated test session may already be closed by a failed request path.
            }
        }
    }
}
