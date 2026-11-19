/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.command.CommandParser;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Whole-family qualification through the actual explicitly enabled DUMB2 provider. */
public final class ContextOrdinaryFamilyIngressWitness {
    private static final String RULE = "!@x left(x)&&right(x)->p(x);";

    public static void main(String[] args) throws Exception {
        Path data = Files.createDirectory(Paths.get(args[0]).toAbsolutePath());
        seed(data, "A", "!left(1); !p(1);");
        seed(data, "B", "!right(1); !~p(1);");
        for (boolean reverse : new boolean[] {false, true}) {
            DB db = new DB();
            Mind root = ContextPrivatePublicationWitness.open(db, data, reverse ? "Reverse" : "Forward");
            User user = (User) root.getUser();
            try {
                require(root.compile("!anchor(1);", null, false), "Source seed");
                db.connectContext(reverse ? "B" : "A");
                db.connectContext(reverse ? "A" : "B");
                long revision = db.getRevision();
                int ruleCount = root.getRules().size();
                expectConflict(() -> root.query(RULE, null, false));
                require(db.getRevision() == revision && root.getRules().size() == ruleCount,
                        "Refused ordinary write changed Source");
                require(user.getCurrentMind() == root, "Ordinary refusal changed the current Mind");

                command(user, "transaction start");
                Mind child = (Mind) user.getCurrentMind();
                require(Boolean.TRUE.equals(child.query(RULE, null, false)), "Stage user rule");
                expectConflict(() -> command(user, "transaction commit"));
                require(user.getCurrentMind() == child, "Refusal lost the open transaction");
                root.requirePendingPhysicalChild(child);
                require(db.getRevision() == revision, "Refused transaction changed Source");
                expectConflict(() -> db.publishContext(child, "Masked publication"));
                require(user.getCurrentMind() == child, "Publication refusal lost the open transaction");
                root.requirePendingPhysicalChild(child);
                require(db.getRevision() == revision && root.getRules().size() == ruleCount,
                        "Refused publication changed Source");
                command(user, "transaction rollback");
                require(user.getCurrentMind() == root, "Rollback did not return to Source");

                require(Boolean.TRUE.equals(root.query("!unrelated(1);", null, false)),
                        "Safe rule refused because siblings disagree");
                require(db.getRevision() == revision + 1, "Safe write did not publish");
                command(user, "transaction start");
                command(user, "transaction start");
                Mind safe = (Mind) user.getCurrentMind();
                require(Boolean.TRUE.equals(safe.query("!localPublished(9);", null, false)), "Stage safe nested rule");
                Mind published = (Mind) db.publishContext(safe, "Safe nested publication");
                require(db.getRevision() == revision + 2, "Safe publication did not advance once");
                require(Boolean.TRUE.equals(published.query("?localPublished(9);", null, false)),
                        "Safe publication lost the nested rule");
                require(db.federationSnapshot().getConnections().size() == 2,
                        "Publication did not retain both pins");
            } finally {
                ((Mind) user.getCurrentMind()).closeStorage();
            }
        }
        seed(data, "Arithmetic", "!@x fun(x)->next(x+1);");
        DB unsupported = new DB();
        Mind root = ContextPrivatePublicationWitness.open(unsupported, data, "Unsupported");
        try {
            require(root.compile("!anchor(1);", null, false), "Unsupported case seed");
            unsupported.connectContext("Arithmetic");
            long revision = unsupported.getRevision();
            expectFailure(() -> root.query("!ordinary(1);", null, false),
                    StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE);
            require(unsupported.getRevision() == revision, "Incomplete qualification published");
        } finally {
            root.closeStorage();
        }
        System.out.println("CONTEXT_ORDINARY_FAMILY_INGRESS_PASS");
    }

    private static void seed(Path data, String name, String rules) throws Exception {
        Mind mind = ContextPrivatePublicationWitness.open(new DB(), data, name);
        try {
            require(mind.compile(rules, null, false), "Seed " + name);
        } finally {
            mind.closeStorage();
        }
    }

    private static void command(User user, String text) throws Exception {
        require(new CanonicalCommandProcessor().execute(new CommandParser().parse(text), user).isSuccess(),
                "Console command failed: " + text);
    }

    private static void expectConflict(Attempt attempt) throws Exception {
        expectFailure(attempt, StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT);
    }

    private static void expectFailure(Attempt attempt, StorageLifecycleErrorCode code) throws Exception {
        try {
            attempt.run();
        } catch (StorageLifecycleException failure) {
            require(failure.getErrorCode() == code,
                    "Wrong refusal code: " + failure.getCode());
            return;
        }
        throw new AssertionError("Expected refusal: " + code);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Attempt {
        void run() throws Exception;
    }
}
