/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.kanger.ContextQualification;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IContextResults;

/** Ground clause reduction, provenance and full exact-revision compatibility. */
public final class GroundDisjunctionQualificationRunner {
    private static int checks;
    private static Path root;

    public static void main(String[] args) {
        try {
            root = Files.createTempDirectory("ground-disjunction-");
            System.setProperty("user.home", root.toString());
            for (String value : new String[] {"1", "R"}) {
                for (boolean quantified : new boolean[] {false, true}) {
                    for (boolean reverse : new boolean[] {false, true})
                        contradiction(value, quantified, reverse);
                }
            }
            concreteMismatchAndRetraction();
            concreteConjunction();
            nullaryClause();
            transactionIsolation();
            exactPairAndConnections();
            System.out.println("GROUND_DISJUNCTION_QUALIFICATION_PASS checks=" + checks);
            System.exit(0);
        } catch (Throwable failure) {
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

    private static void contradiction(String value, boolean quantified, boolean reverse) throws Exception {
        Mind mind = memory();
        String clause = quantified ? "!@x p(x) || q(x);" : "!(p(" + value + ") || q(" + value + "));";
        String negativeP = "!~p(" + value + ");", negativeQ = "!~q(" + value + ");";
        if (reverse) {
            require(mind.compile(negativeP + negativeQ, null, false), "negative fixture");
            require(!mind.compile(clause, null, false), "reject clause contradicting both existing negatives");
            require(Boolean.TRUE.equals(mind.query("?~p(" + value + ");", null, false)), "refusal preserves p");
            require(Boolean.TRUE.equals(mind.query("?~q(" + value + ");", null, false)), "refusal preserves q");
        } else {
            require(mind.compile(clause, null, false), "clause accepted");
            require(mind.compile(negativeP, null, false), "first negative accepted");
            require(Boolean.TRUE.equals(mind.query("?q(" + value + ");", null, false)), "remaining alternative proved");
            require(!mind.compile(negativeQ, null, false), "reject opposite of remaining alternative");
            require(Boolean.TRUE.equals(mind.query("?q(" + value + ");", null, false)), "rejected input preserves proof");
        }
        require(ContextQualification.inspect(mind, false).isValid(), "retained context remains consistent");
    }

    private static void concreteMismatchAndRetraction() throws Exception {
        Mind mind = memory();
        require(mind.compile("!(p(1) || q(1)); !~p(2);", null, false), "different substitution is compatible");
        require(mind.query("?q(1);", null, false) == null, "mismatched ground must not close clause");
        require(mind.compile("!~p(1);", null, false), "matching ground accepted");
        require(Boolean.TRUE.equals(mind.query("?q(1);", null, false)), "matching ground closes clause");
        require(Boolean.TRUE.equals(mind.query("-~p(1);", null, false)), "remove matching supporting ground");
        require(mind.query("?q(1);", null, false) == null, "derived alternative loses removed support");
        require(Boolean.TRUE.equals(mind.query("?~p(2);", null, false)), "unrelated ground preserved");
    }

    private static void nullaryClause() throws Exception {
        Mind mind = memory();
        require(mind.compile("!(left() || right()); !~left();", null, false), "nullary clause setup");
        require(Boolean.TRUE.equals(mind.query("?right();", null, false)), "zero-position exact match closes clause");
        require(!mind.compile("!~right();", null, false), "nullary contradiction rejected");
    }

    private static void concreteConjunction() throws Exception {
        Mind mind = memory();
        require(mind.compile("!alpha(1); !beta(1);", null, false), "concrete conjunction fixture");
        require(Boolean.TRUE.equals(mind.query("?alpha(1) && beta(1);", null, false)),
                "independently proved ground conjuncts close the complete query");
    }

    private static void transactionIsolation() throws Exception {
        Mind root = memory();
        require(root.compile("!(p(1) || q(1));", null, false), "transaction fixture");
        Mind child = new Mind(root);
        ((User) root.getUser()).setCurrentMind(child);
        try {
            require(child.compile("!~p(1);", null, false), "child ground accepted");
            require(Boolean.TRUE.equals(child.query("?q(1);", null, false)), "child derives its own alternative");
        } finally {
            root.release(child);
            ((User) root.getUser()).setCurrentMind(root);
        }
        require(root.query("?q(1);", null, false) == null, "rollback removes derived alternative");
        require(root.query("?~p(1);", null, false) == null, "rollback removes added ground");
    }

    private static void exactPairAndConnections() throws Exception {
        IContextResults.Revision left = create("Disjunction", "!(p(1) || q(1));");
        IContextResults.Revision right = create("Negatives", "!~p(1); !~q(1);");
        PairQualification.Result pair = PairQualification.qualify(root.resolve("Disjunction"), left.getRevision(),
                root.resolve("Negatives"), right.getRevision());
        require(!pair.isCompatible() && !pair.isLeftOverRightValid() && !pair.isRightOverLeftValid(),
                "full pair rejects indirect contradiction in both directions");
        require(pair.getCertificate() == null && !pair.getCollisions().isEmpty(), "no certificate and actual collision witnesses");
        try (Fixture x = new Fixture("Disjunction")) {
            rejectConnect(x, "Negatives", null);
            require(x.db.federationSnapshot().getConnections().isEmpty(), "pair refusal leaves topology unchanged");
            require(x.db.getRevision() == left.getRevision(), "pair refusal leaves source revision unchanged");
        }
        try (Fixture x = new Fixture("Trust")) {
            x.db.connectContext("Disjunction", "joint");
            rejectConnect(x, "Negatives", "joint");
            require(x.db.federationSnapshot().getConnections().size() == 1, "commune refusal preserves previous member");
        }
        try (ContextSnapshot l = ContextSnapshot.open(root.resolve("Disjunction"));
             ContextSnapshot r = ContextSnapshot.open(root.resolve("Negatives"))) {
            require(l.getRevision() == left.getRevision() && r.getRevision() == right.getRevision(),
                    "qualification preserves both exact source revisions");
        }
    }

    private static void rejectConnect(Fixture x, String target, String trust) throws Exception {
        try {
            x.db.connectContext(target, trust);
            throw new AssertionError("Incompatible connection accepted");
        } catch (org.kanger.exception.StorageLifecycleException expected) {
            require(!expected.getCollisions().isEmpty(), "connection refusal carries collision witnesses");
        } catch (org.kanger.exception.CommandErrorException expected) {
            require(trust != null && expected.toString().contains("Trust commune contains incompatible knowledge"),
                    "trust refusal identifies incompatible full knowledge");
        }
    }

    private static IContextResults.Revision create(String name, String source) throws Exception {
        try (Fixture f = new Fixture(name)) { require(f.mind.compile(source, null, false), "persistent fixture " + name); }
        try (ContextSnapshot snapshot = ContextSnapshot.open(root.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(), snapshot.getRevision());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final User user;
        final DB db;
        final Mind mind;
        Fixture(String name) throws Exception {
            user = new User(); user.setDatabaseDir(root.toString() + File.separator);
            db = new DB(); db.init(user);
            Mind initial = new Mind(user); user.setCurrentMind(initial);
            mind = (Mind) initial.useStorage(name); user.setCurrentMind(mind);
        }
        public void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    }

    private static void require(boolean success, String message) {
        ++checks;
        if (!success) throw new AssertionError(message);
    }
}
