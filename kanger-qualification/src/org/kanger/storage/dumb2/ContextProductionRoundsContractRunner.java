/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kanger.ContextQualification;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.exception.StorageLifecycleException;

/** Accepted authoring contract. Expected RED until production rounds exist. */
public final class ContextProductionRoundsContractRunner {
    private static int checks;
    private static final List<String> failures = new ArrayList<String>();
    private static Path root;
    private static final String RULE = "!@x left(x) && right(x) -> p(x);";

    public static void main(String[] args) {
        try {
            root = Files.createTempDirectory("context-production-rounds-");
            System.setProperty("user.home", root.toString());
            nativeProductionControl();
            for (boolean reverse : new boolean[] {false, true}) {
                write("masked-" + reverse, reverse, true, RULE, false);
                write("unmasked-" + reverse, reverse, false, RULE, false);
                write("foreign-only-" + reverse, reverse, true, "!unrelated(1);", true);
            }
            if (!failures.isEmpty()) throw new AssertionError(failures.toString());
            System.out.println("CONTEXT_PRODUCTION_ROUNDS_CONTRACT_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) {
            System.out.println("CONTEXT_PRODUCTION_ROUNDS_CONTRACT_FAIL checks=" + checks
                    + " failures=" + failures.size());
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static Mind memory() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        return mind;
    }

    private static void nativeProductionControl() throws Exception {
        Mind b = memory();
        check(b.compile(RULE + " !right(1); !~p(1);", null, false), "B native setup");
        check(Boolean.FALSE.equals(b.query("?left(1);", null, false)), "B proves negative left");
        check(Boolean.TRUE.equals(b.query("?~left(1);", null, false)), "signed production control");
        Mind a = memory();
        check(a.compile(RULE + " !left(1); !p(1);", null, false), "A native setup");
        check(!a.compile("!~left(1);", null, false), "actual B production conflicts in A");
        check(ContextQualification.inspect(a, false).isValid(), "native refusal preserves A");
    }

    private static void create(String name, String source) throws Exception {
        try (Fixture fixture = new Fixture(name)) {
            if (!source.isEmpty()) check(fixture.mind.compile(source, null, false), name + " setup");
        }
    }

    private static void write(String name, boolean reverse, boolean masked,
                              String command, boolean shouldAccept) throws Exception {
        String a = name + "-A", b = name + "-B", x = name + "-X";
        create(a, "!left(1);" + (masked ? " !p(1);" : ""));
        create(b, "!right(1); !~p(1);");
        create(x, "");
        long aBefore, bBefore;
        try (ContextSnapshot source = ContextSnapshot.open(root.resolve(a))) { aBefore = source.getRevision(); }
        try (ContextSnapshot source = ContextSnapshot.open(root.resolve(b))) { bBefore = source.getRevision(); }
        long expectedRevision;
        try (Fixture fixture = new Fixture(x)) {
            fixture.db.connectContext(reverse ? b : a);
            fixture.db.connectContext(reverse ? a : b);
            long before = fixture.db.getRevision();
            boolean accepted;
            try { accepted = Boolean.TRUE.equals(fixture.mind.query(command, null, false)); }
            catch (StorageLifecycleException conflict) {
                accepted = false;
                check(!conflict.getCollisions().isEmpty(), name + " refusal has witnesses");
            }
            System.out.println(name + " accepted=" + accepted + " revision=" + before
                    + "->" + fixture.db.getRevision());
            check(accepted == shouldAccept, name + " expected accepted=" + shouldAccept);
            expectedRevision = accepted ? fixture.db.getRevision() : before;
            if (!accepted) {
                check(fixture.db.getRevision() == before, name + " refusal preserves revision");
                check(masked ? fixture.mind.query("?p(1);", null, false) == null
                                : Boolean.FALSE.equals(fixture.mind.query("?p(1);", null, false)),
                        name + " refusal preserves original external answer");
                check(fixture.mind.query("?~left(1);", null, false) == Boolean.FALSE,
                        name + " rejected production does not leak into X");
            }
            check(fixture.db.federationSnapshot().getConnections().size() == 2,
                    name + " topology unchanged");
        }
        try (ContextSnapshot source = ContextSnapshot.open(root.resolve(x))) {
            check(source.getRevision() == expectedRevision, name + " close/reopen revision");
        }
        try (ContextSnapshot source = ContextSnapshot.open(root.resolve(a))) {
            check(source.getRevision() == aBefore, name + " A unchanged");
        }
        try (ContextSnapshot source = ContextSnapshot.open(root.resolve(b))) {
            check(source.getRevision() == bBefore, name + " B unchanged");
        }
    }

    private static void check(boolean condition, String message) {
        ++checks;
        if (!condition) { failures.add(message); System.out.println("MISSING: " + message); }
    }

    private static final class Fixture implements AutoCloseable {
        final User user = new User();
        final DB db = new DB();
        final Mind mind;
        Fixture(String name) throws Exception {
            user.setDatabaseDir(root.toString() + File.separator);
            db.init(user);
            Mind initial = new Mind(user);
            user.setCurrentMind(initial);
            mind = (Mind) initial.useStorage(name);
            user.setCurrentMind(mind);
        }
        @Override public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }
}
