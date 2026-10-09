/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Publication and historical fork while a connected target's CURRENT advances. */
public final class ContextPinnedSessionProbe {
    private ContextPinnedSessionProbe() { }

    private static final class Session implements AutoCloseable {
        final User user = new User();
        final DB data = new DB();
        Mind mind;
        Session(Path directory, String name) throws Exception {
            user.setDatabaseDir(directory + File.separator);
            data.init(user);
            mind = new Mind(user);
            user.setCurrentMind(mind);
            mind = (Mind) mind.useStorage(name);
            user.setCurrentMind(mind);
        }
        void publish(String description) throws Exception {
            mind = (Mind) data.publishContext(mind, description);
            user.setCurrentMind(mind);
        }
        @Override public void close() throws Exception {
            user.setCurrentMind(mind.closeStorage());
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void johnOnly(Session session) throws Exception {
        long revision = session.data.getRevision();
        require(Boolean.TRUE.equals(session.mind.query("?$x q(x);", null, false)), "pinned truth");
        Set<String> values = new HashSet<>();
        session.mind.getValues().forEach(row -> values.add(row.get("x").toString()));
        require(values.equals(java.util.Collections.singleton("John")), "pinned Values: " + values);
        require(session.data.getRevision() == revision, "query published a revision");
        require(ContextLongSessionProbe.retained(session.data) == 0,
                "publication/query retained obsolete layers");
        require(ContextLongSessionProbe.communes(session.data) == 1,
                "publication/query retained obsolete commune states");
    }

    public static void main(String[] args) throws Exception {
        int cycles = args.length == 0 ? 20 : Integer.parseInt(args[0]);
        require(cycles > 0, "positive cycle count required");
        Path directory = Files.createTempDirectory("kanger-pinned-session-");
        System.out.println("Test database: " + directory);
        try (Session target = new Session(directory, "N")) {
            require(Boolean.TRUE.equals(target.mind.query("!p(John);", null, false)), "target fact");
            require(Boolean.TRUE.equals(target.mind.query("!@x p(x) -> q(x);", null, false)), "target rule");
            long pinned = target.data.getRevision();
            try (Session reader = new Session(directory, "X")) {
                require(Boolean.TRUE.equals(reader.mind.query("!anchor(X);", null, false)), "reader anchor");
                reader.data.connectContext("N", "own");
                reader.publish("Initial pin");
                long historical = reader.data.getRevision();
                UUID sourceId = reader.data.getContextId();
                for (int cycle = 1; cycle <= cycles; cycle++) {
                    String person = "Person" + cycle;
                    require(Boolean.TRUE.equals(target.mind.query("!p(" + person + ");", null, false)),
                            "advance target " + cycle);
                    require(target.data.getRevision() > pinned, "CURRENT did not advance");
                    johnOnly(reader);
                    require(reader.mind.query("?q(" + person + ");", null, false) == null,
                            "CURRENT leaked into pin " + cycle);
                    reader.publish("Pinned publication " + cycle);
                    require(reader.data.federationSnapshot().getConnections().get(0).getPinnedRevision() == pinned,
                            "publication advanced pin " + cycle);
                    johnOnly(reader);
                    if (cycle % 5 == 0 || cycle == cycles) {
                        String forkName = "Fork" + cycle;
                        try (Session selected = new Session(directory, "X@" + historical)) {
                            johnOnly(selected);
                            selected.mind.forkContext(forkName);
                            require(selected.data.getRevision() == historical, "fork changed selection");
                        }
                        try (Session fork = new Session(directory, forkName)) {
                            require(!sourceId.equals(fork.data.getContextId()), "fork reused identity");
                            require(fork.data.federationSnapshot().getConnections().get(0).getPinnedRevision() == pinned,
                                    "fork advanced dependency pin");
                            try (ContextSnapshot snapshot = ContextSnapshot.open(
                                    directory.resolve(forkName), fork.data.getRevision())) {
                                require(sourceId.equals(snapshot.getOrigin().getContextId()), "fork origin identity");
                                require(snapshot.getOrigin().getRevision() == historical, "fork origin revision");
                            }
                            johnOnly(fork);
                            require(Boolean.TRUE.equals(fork.mind.query("!forkOnly(Tom);", null, false)),
                                    "fork authoring");
                            fork.publish("Independent fork publication");
                            johnOnly(fork);
                        }
                        require(reader.mind.query("?forkOnly(Tom);", null, false) == null, "fork mutated source");
                        require(target.mind.query("?forkOnly(Tom);", null, false) == null, "fork mutated target");
                    }
                    if (cycle == 1 || cycle % 5 == 0 || cycle == cycles)
                        System.out.println("PIN_CYCLE " + cycle + " pinned=" + pinned
                                + " targetCurrent=" + target.data.getRevision()
                                + " readerCurrent=" + reader.data.getRevision());
                }
            }
            try (Session reopened = new Session(directory, "X")) {
                johnOnly(reopened);
                require(reopened.data.federationSnapshot().getConnections().get(0).getPinnedRevision() == pinned,
                        "reopen advanced pin");
            }
        }
        System.out.println("PIN_PUBLICATION_FORK_PASS " + cycles);
    }
}
