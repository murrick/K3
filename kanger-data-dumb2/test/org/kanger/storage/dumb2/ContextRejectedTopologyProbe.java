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

/** Rejected commune joins and repins must reclaim candidates and preserve the pin. */
public final class ContextRejectedTopologyProbe {
    private ContextRejectedTopologyProbe() { }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Mind open(User user, String name) throws Exception {
        Mind mind = (Mind) user.getCurrentMind().useStorage(name);
        user.setCurrentMind(mind); return mind;
    }
    public static void main(String[] args) throws Exception {
        int cycles = args.length == 0 ? 20 : Integer.parseInt(args[0]);
        require(cycles > 0, "positive cycle count required");
        Path directory = Files.createTempDirectory("kanger-rejected-topology-");
        System.out.println("Test database: " + directory);
        User user = new User(); user.setDatabaseDir(directory + File.separator);
        DB data = new DB(); data.init(user); user.setCurrentMind(new Mind(user));
        Mind mind = open(user, "N");
        try {
            require(Boolean.TRUE.equals(mind.query("!p(John);", null, false)), "fact");
            require(Boolean.TRUE.equals(mind.query("!@x p(x) -> q(x);", null, false)), "rule");
        } finally { user.setCurrentMind(mind.closeStorage()); }
        mind = open(user, "B");
        try { require(Boolean.TRUE.equals(mind.query("!~p(John);", null, false)), "negative fact"); }
        finally { user.setCurrentMind(mind.closeStorage()); }
        mind = open(user, "X");
        try {
            require(Boolean.TRUE.equals(mind.query("!anchor(X);", null, false)), "anchor");
            data.connectContext("N", "own");
            long source = data.getRevision();
            long pinned = data.federationSnapshot().getConnections().get(0).getPinnedRevision();
            for (int cycle = 1; cycle <= cycles; cycle++) {
                boolean rejected = false;
                try { data.connectContext("B", "own"); }
                catch (org.kanger.exception.CommandErrorException | org.kanger.exception.StorageLifecycleException expected) {
                    rejected = true;
                }
                require(rejected, "incompatible commune join accepted");
                require(data.federationSnapshot().getConnections().size() == 1, "rejection changed topology");
                require(data.getRevision() == source, "rejection published");
                require(ContextLongSessionProbe.retained(data) == 0, "failed join retained candidate " + cycle);
                require(ContextLongSessionProbe.communes(data) == 1, "failed join retained commune " + cycle);
            }
            require(Boolean.TRUE.equals(mind.query("?q(John);", null, false)), "join rejection lost answer");
            new CanonicalCommandProcessor().execute(new CommandParser().parse("ctx ask N !p(Mary);"), user);
            require(Boolean.TRUE.equals(mind.query("?q(Mary);", null, false)), "private initialization");
            User writer = new User(); writer.setDatabaseDir(directory + File.separator);
            DB targetData = new DB(); targetData.init(writer); writer.setCurrentMind(new Mind(writer));
            Mind target = open(writer, "N");
            long newer;
            try {
                require(Boolean.TRUE.equals(target.query("!~p(Mary);", null, false)), "new target revision");
                newer = targetData.getRevision();
            } finally { writer.setCurrentMind(target.closeStorage()); }
            for (int cycle = 1; cycle <= cycles; cycle++) {
                boolean rejected = false;
                try { data.switchContextRevision("N", newer); }
                catch (org.kanger.exception.CommandErrorException | org.kanger.exception.StorageLifecycleException expected) {
                    rejected = true;
                }
                require(rejected, "incompatible repin accepted");
                require(data.federationSnapshot().getConnections().get(0).getPinnedRevision() == pinned,
                        "rejected repin changed pin");
                require(data.getRevision() == source, "rejected repin published");
                require(ContextLongSessionProbe.retained(data) == 0, "failed repin retained candidate " + cycle);
                require(ContextLongSessionProbe.communes(data) == 1, "failed repin retained commune " + cycle);
            }
            require(Boolean.TRUE.equals(mind.query("?q(Mary);", null, false)), "repin rejection lost private answer");
        } finally { user.setCurrentMind(mind.closeStorage()); }
        System.out.println("REJECTED_JOIN_AND_REPIN_PASS " + cycles);
    }
}
