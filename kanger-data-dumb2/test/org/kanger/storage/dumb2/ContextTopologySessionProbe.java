/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.command.CommandParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Topology churn without query-driven root settlement, plus a live rollback pin. */
public final class ContextTopologySessionProbe {
    private ContextTopologySessionProbe() { }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        int cycles = args.length == 0 ? 40 : Integer.parseInt(args[0]);
        require(cycles > 0, "positive cycle count required");
        Path directory = Files.createTempDirectory("kanger-topology-session-");
        System.out.println("Test database: " + directory);
        User user = new User(); user.setDatabaseDir(directory + File.separator);
        DB data = new DB(); data.init(user); Mind mind = new Mind(user);
        user.setCurrentMind(mind); mind = (Mind) mind.useStorage("N"); user.setCurrentMind(mind);
        try {
            require(Boolean.TRUE.equals(mind.query("!p(John);", null, false)), "fact");
            require(Boolean.TRUE.equals(mind.query("!@x p(x) -> q(x);", null, false)), "rule");
        } finally { user.setCurrentMind(mind.closeStorage()); }
        mind = (Mind) user.getCurrentMind().useStorage("X"); user.setCurrentMind(mind);
        try {
            require(Boolean.TRUE.equals(mind.query("!anchor(X);", null, false)), "anchor");
            long revision = data.getRevision();
            for (int cycle = 1; cycle <= cycles; cycle++) {
                data.connectContext("N", "own");
                data.connectContext("N", "own");
                require(ContextLongSessionProbe.communes(data) == 1, "active commune count " + cycle);
                data.disconnectContext("N");
                require(ContextLongSessionProbe.retained(data) == 0, "retired layers without query " + cycle);
                require(ContextLongSessionProbe.communes(data) == 0, "old communes without query " + cycle);
                require(data.getRevision() == revision, "topology churn published " + cycle);
            }
            data.connectContext("N", "own");
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
            CommandParser parser = new CommandParser();
            processor.execute(parser.parse("transaction start"), user);
            for (int cycle = 1; cycle <= cycles; cycle++) {
                data.disconnectContext("N"); data.connectContext("N", "own");
            }
            data.disconnectContext("N");
            processor.execute(parser.parse("rollback"), user);
            mind = (Mind) user.getCurrentMind();
            require(data.federationSnapshot().getConnections().size() == 1, "rollback lost connection");
            require(Boolean.TRUE.equals(mind.query("?q(John);", null, false)), "rollback lost runtime");
            require(ContextLongSessionProbe.retained(data) == 0, "retired layers after rollback");
            require(ContextLongSessionProbe.communes(data) == 1, "rollback lost commune");
            Mind hidden = new Mind(mind);
            hidden.checkpointUserConnections();
            try {
                data.disconnectContext("N");
                require(ContextLongSessionProbe.retained(data) > 0,
                        "visible U0 collected a hidden child's checkpoint");
            } finally {
                mind.release(hidden);
            }
            require(Boolean.TRUE.equals(mind.query("?q(John);", null, false)),
                    "hidden child rollback lost runtime");
            require(ContextLongSessionProbe.retained(data) == 0,
                    "hidden child settlement retained layers");
        } finally {
            Mind current = (Mind) user.getCurrentMind();
            while (current.getNext() != null) {
                Mind parent = (Mind) current.getNext(); parent.release(current);
                current = parent; user.setCurrentMind(current);
            }
            user.setCurrentMind(current.closeStorage());
        }
        System.out.println("TOPOLOGY_WITHOUT_QUERY_AND_CHECKPOINT_PASS " + cycles);
    }
}
