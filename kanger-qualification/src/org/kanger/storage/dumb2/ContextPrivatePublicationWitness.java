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

    static Mind open(DB db, Path directory, String name) throws Exception {
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
        DB source = new DB();
        Mind mind = seed(source, directory);
        close(publish(source, mind));
        verifyRecovery(directory, 3);
        System.out.println("PRIVATE_PUBLICATION_HOST_WITNESS_OK");
    }

    static Mind seed(DB source, Path directory) throws Exception {
        Mind target = open(new DB(), directory, "Rules");
        try {
            need(target.compile("!remote(7);", null, false), "Rules seed");
        } finally {
            close(target);
        }
        Mind mind = open(source, directory, "Source");
        need(mind.compile("!anchor(1);", null, false), "Source seed");
        source.connectContext("Rules");
        need(source.saveConnections(mind) == 2, "Source R2");
        return mind;
    }

    static Mind publish(DB source, Mind mind) throws Exception {
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
        return mind;

    }

    static void verifyRecovery(Path directory, long selectedRevision) throws Exception {
        need(selectedRevision == 2 || selectedRevision == 3, "Source revision outside transition");
        verifySourceView(directory, 2);
        if (selectedRevision == 3) verifySourceView(directory, 3);

        Mind original = open(new DB(), directory, "Rules@1");
        need(Boolean.TRUE.equals(original.query("?remote(7);")), "Original rule stays visible");
        need(original.query("?privateNext(8);") == null, "Private rule does not leak");
        close(original);

        Path path = directory.resolve("Source");
        for (long revision : selectedRevision == 2 ? new long[] {2} : new long[] {2, 3}) {
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
    }

    private static void verifySourceView(Path directory, long revision) throws Exception {
        Mind selected = open(new DB(), directory, "Source@" + revision);
        try {
            need(Boolean.TRUE.equals(selected.query("?anchor(1);")), "Original Source rule");
            need(Boolean.TRUE.equals(selected.query("?remote(7);")), "Pinned Rules rule");
            boolean published = revision == 3;
            need(java.util.Objects.equals(published ? Boolean.TRUE : null, selected.query("?localNext(9);")),
                "Local rule and selected revision disagree");
            need(java.util.Objects.equals(published ? Boolean.TRUE : null, selected.query("?privateNext(8);")),
                "Private recipe and selected revision disagree");
        } finally {
            close(selected);
        }
    }
}
