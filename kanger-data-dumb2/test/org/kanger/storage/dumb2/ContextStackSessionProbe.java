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

/** Repeated squash, storage rebase and rejected publication with live checkpoints. */
public final class ContextStackSessionProbe {
    private ContextStackSessionProbe() { }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Mind current(User user) { return (Mind) user.getCurrentMind(); }
    private static void command(User user, String source) throws Exception {
        require(new CanonicalCommandProcessor().execute(new CommandParser().parse(source), user).isSuccess(),
                "command rejected: " + source);
    }
    private static void accept(User user, String source) throws Exception {
        require(Boolean.TRUE.equals(current(user).query(source, null, false)), "authoring rejected: " + source);
    }
    private static void ask(User user, String source, Boolean expected) throws Exception {
        require(java.util.Objects.equals(expected, current(user).query(source, null, false)),
                "unexpected truth: " + source);
    }
    private static void clean(User user, DB data) throws Exception {
        ask(user, "?q(John);", Boolean.TRUE);
        require(ContextLongSessionProbe.retained(data) == 0, "retired layers after settlement");
        require(ContextLongSessionProbe.communes(data) == 1, "obsolete communes after settlement");
    }
    public static void main(String[] args) throws Exception {
        int cycles = args.length == 0 ? 10 : Integer.parseInt(args[0]);
        require(cycles > 0, "positive cycle count required");
        Path directory = Files.createTempDirectory("kanger-stack-session-");
        System.out.println("Test database: " + directory);
        User user = new User(); user.setDatabaseDir(directory + File.separator);
        DB data = new DB(); data.init(user); user.setCurrentMind(new Mind(user));
        command(user, "use N");
        accept(user, "!p(John);"); accept(user, "!@x p(x) -> q(x);");
        user.setCurrentMind(current(user).closeStorage());
        for (String name : new String[] { "X", "Y" }) {
            command(user, "use " + name);
            accept(user, "!anchor(" + name + ");");
            data.connectContext("N", "own"); data.saveConnections(current(user));
            user.setCurrentMind(current(user).closeStorage());
        }
        command(user, "use X");
        try {
            for (int cycle = 1; cycle <= cycles; cycle++) {
                long revision = data.getRevision();
                command(user, "transaction start"); command(user, "ctx ask N !p(Tom);");
                command(user, "transaction start"); command(user, "ctx ask N !p(Mary);");
                command(user, "transaction squash");
                require(current(user).getTransactionLevel() == 1, "squash did not produce U1");
                ask(user, "?q(Tom);", Boolean.TRUE); ask(user, "?q(Mary);", Boolean.TRUE);
                command(user, "rollback");
                ask(user, "?q(Tom);", null); ask(user, "?q(Mary);", null);
                require(data.getRevision() == revision, "squash/rollback published"); clean(user, data);
            }
            System.out.println("SQUASH_ROLLBACK_PASS " + cycles);
            for (int cycle = 1; cycle <= cycles; cycle++) {
                String destination = cycle % 2 == 1 ? "Y" : "X";
                command(user, "transaction start"); accept(user, "!outerMark(Tom);");
                command(user, "transaction start"); accept(user, "!innerMark(Mary);");
                command(user, "use " + destination);
                require(current(user).getTransactionLevel() == 2, "rebase lost transaction depth");
                ask(user, "?anchor(" + destination + ");", Boolean.TRUE);
                ask(user, "?outerMark(Tom);", Boolean.TRUE); ask(user, "?innerMark(Mary);", Boolean.TRUE);
                command(user, "rollback"); ask(user, "?innerMark(Mary);", null);
                ask(user, "?outerMark(Tom);", Boolean.TRUE);
                command(user, "rollback"); ask(user, "?outerMark(Tom);", null); clean(user, data);
            }
            System.out.println("STORAGE_REBASE_ROLLBACK_PASS " + cycles);
            for (int cycle = 1; cycle <= cycles; cycle++) {
                long revision = data.getRevision();
                long pinned = data.federationSnapshot().getConnections().get(0).getPinnedRevision();
                command(user, "transaction start"); accept(user, "!~p(John);");
                command(user, "transaction start"); accept(user, "!publicationMark(Tom);");
                Mind before = current(user);
                boolean rejected = false;
                try { data.publishContext(before, "Rejected publication " + cycle); }
                catch (org.kanger.exception.CommandErrorException | org.kanger.exception.StorageLifecycleException expected) {
                    rejected = true;
                }
                require(rejected, "incompatible publication accepted");
                require(current(user) == before && before.getTransactionLevel() == 2,
                        "rejection replaced live stack");
                require(data.getRevision() == revision, "rejection advanced CURRENT");
                require(data.federationSnapshot().getConnections().get(0).getPinnedRevision() == pinned,
                        "rejection advanced pin");
                command(user, "rollback"); command(user, "rollback");
                ask(user, "?publicationMark(Tom);", null); clean(user, data);
            }
            System.out.println("REJECTED_PUBLICATION_ROLLBACK_PASS " + cycles);
        } finally {
            while (current(user).getNext() != null) {
                Mind child = current(user); Mind parent = (Mind) child.getNext();
                parent.release(child); user.setCurrentMind(parent);
            }
            user.setCurrentMind(current(user).closeStorage());
        }
    }
}
