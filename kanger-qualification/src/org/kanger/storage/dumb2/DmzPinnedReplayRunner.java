/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import org.kanger.DmzReplayProvenance;
import org.kanger.Mind;
import org.kanger.User;

/** Exercises real persistent connection pins through CommuneRuntime replay. */
public final class DmzPinnedReplayRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("dmz-pinned-replay-");
        System.setProperty("user.home", directory.toString());
        create(directory, "A", "!@x a(x) -> male(x); !a(John); !family([]);");
        create(directory, "B", "!@x a(x) -> male(x); !a(John); !family([]);");
        create(directory, "Q", "!anchor(Q);");
        ContextConnection a = ConnectionManager.qualifyConnect(directory.resolve("Q"), directory.resolve("A"));
        ContextConnection b = ConnectionManager.qualifyConnect(directory.resolve("Q"), directory.resolve("B"));
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                CommuneRuntime runtime = CommuneRuntime.prepare(Arrays.asList(a, b))) {
            List<DmzReplayProvenance.Binding> bindings = journal.snapshot();
            require(bindings.size() == 6, "three primary inputs from each persistent source");
            int duplicates = 0;
            for (DmzReplayProvenance.Binding binding : bindings) {
                ContextConnection source = binding.context.equals(a.getTarget().getContextId()) ? a : b;
                require(binding.context.equals(source.getTarget().getContextId()) &&
                        binding.revision == source.getTarget().getRevision(), "actual connection pin");
                require(binding.authority == DmzReplayProvenance.Authority.EXTERNAL, "external authority");
                boolean found = false;
                for (org.kanger.interfaces.IRule rule : source.layer().getRules())
                    if (rule.getId() == binding.sourceRule && !rule.isDeleted(source.layer())) found = true;
                require(found, "source-local rule exists in pinned layer");
                if (binding.duplicate) ++duplicates;
            }
            require(duplicates == 3, "second source retains duplicate occurrences");
            Mind work = Mind.ephemeralChild(runtime.mind());
            try {
                require(work.queryCanonical("?male(John);", new LinkedList<>(), false), "cross-source native inference");
                require(work.queryCanonical("?family([]);", new LinkedList<>(), false), "empty set survives persistent replay");
            } finally { runtime.mind().discardEphemeral(work); }
            require(journal.snapshot().size() == 6, "queries add no replay bindings");
            require(journal.settlementSnapshot().accepted.isEmpty()
                    && journal.settlementSnapshot().untracked == 6,
                    "untracked runtime layer is not a global acceptance certificate");
        } finally { a.closeLayer(); b.closeLayer(); }
        create(directory, "C", "!p(John);");
        create(directory, "D", "!~p(John);");
        ContextConnection c = ConnectionManager.qualifyConnect(directory.resolve("Q"), directory.resolve("C"));
        ContextConnection d = ConnectionManager.qualifyConnect(directory.resolve("Q"), directory.resolve("D"));
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            boolean rejected = false;
            try (CommuneRuntime candidate = CommuneRuntime.prepare(Arrays.asList(c, d))) {
                throw new AssertionError("contradictory runtime accepted");
            } catch (org.kanger.exception.CommandErrorException expected) { rejected = true; }
            require(rejected, "whole provider preparation rejected");
            require(!journal.snapshot().isEmpty(), "failed preparation retains raw diagnostics");
            require(journal.settlementSnapshot().accepted.isEmpty(), "failed provider preparation cannot certify replay");
        } finally { c.closeLayer(); d.closeLayer(); }
        System.out.println("DMZ_PINNED_REPLAY_PASS checks=" + checks);
    }
    private static void create(Path directory, String name, String source) throws Exception {
        User user = new User(); user.setDatabaseDir(directory + File.separator);
        DB data = new DB(); data.init(user);
        Mind mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
        try { require(mind.compile(source, null, false), "persistent source " + name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
