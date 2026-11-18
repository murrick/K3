/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.command.CommandParser;

/** Host witness for the canonical publication and private recipe cold-cut fixture. */
public final class ContextPrivatePublicationWitness {
    private static void need(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static Mind open(DB db, Path directory, String name) throws Exception {
        User user = new User();
        user.setDatabaseDir(directory + File.separator);
        db.init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        Mind current = (Mind) root.useStorage(name);
        user.setCurrentMind(current);
        return current;
    }

    private static void close(Mind mind) throws Exception {
        ((User) mind.getUser()).setCurrentMind(mind.closeStorage());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("A fresh output directory is required");
        Path directory = java.nio.file.Files.createDirectory(Paths.get(args[0]).toAbsolutePath());
        Mind target = open(new DB(), directory, "Rules");
        need(target.compile("!remote(7);", null, false), "Rules seed");
        close(target);

        DB source = new DB();
        Mind mind = open(source, directory, "Source");
        need(mind.compile("!anchor(1);", null, false), "Source seed");
        source.connectContext("Rules");
        need(source.saveConnections(mind) == 2, "Source R2");
        User user = (User) mind.getUser();
        CommandParser parser = new CommandParser();
        CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
        need(processor.execute(parser.parse("transaction start"), user).isSuccess(), "Begin transaction");
        mind = (Mind) user.getCurrentMind();
        need(mind.compile("!localNext(9);", null, false), "Pending local original");
        source.applyConnectionCommand(mind, "Rules", "!privateNext(8);");
        mind = (Mind) source.publishContext(mind, "Private publication witness");
        user.setCurrentMind(mind);
        need(source.getRevision() == 3, "Source R3");
        close(mind);

        DB reopened = new DB();
        Mind selected = open(reopened, directory, "Source");
        need(Boolean.TRUE.equals(selected.query("?localNext(9);")), "Published local rule");
        need(Boolean.TRUE.equals(selected.query("?privateNext(8);")), "Replayed private rule");
        close(selected);
        Mind original = open(new DB(), directory, "Rules");
        need(Boolean.TRUE.equals(original.query("?remote(7);")), "Original rule stays visible");
        need(original.query("?privateNext(8);") == null, "Private rule does not leak");
        close(original);

        Path path = directory.resolve("Source");
        for (long revision : new long[] {2, 3}) {
            try (ContextSnapshot snapshot = ContextSnapshot.open(path, revision)) {
                ConnectionVector vector = ConnectionStore.read(path,
                    new RevisionRef(snapshot.getContextId(), revision));
                need(vector.size() == 1, "One direct pin");
                ContextConnection connection = vector.getConnections().get(0);
                need(connection.getTarget().getRevision() == 1, "Same Rules pin");
                need(connection.getInitialization().equals(revision == 2
                    ? java.util.Collections.emptyList()
                    : java.util.Collections.singletonList("!privateNext(8);")), "Exact saved recipe");
                try (ContextSnapshot rules = ContextSnapshot.open(directory.resolve("Rules"), 1)) {
                    need(connection.getTargetLocation().equals(rules.getLocation()), "Same Rules locator");
                    need(connection.getTarget().getContextId().equals(rules.getContextId()), "Same Rules identity");
                }
            }
        }
        System.out.println("PRIVATE_PUBLICATION_HOST_WITNESS_OK");
    }
}
