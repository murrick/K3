/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.kanger.ContextProductionInventory;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Required arithmetic function topology gate, including safe and refuting attachments. */
public final class ContextOrdinaryFunctionAttachmentRunner {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("ordinary-function-attachment-");
        System.setProperty("user.home", home.toString());
        ContextSevenPremiseNativeWriteRunner.create(home, "FX", "!@x fun(x)->next(x+1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Safe", "!unrelated(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Producer", "!@x seed(x)->fun(x);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Deny", "!~next(2);");
        ContextSevenPremiseNativeWriteRunner.create(home, "FunFact", "!fun(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Guard", "!@x next(x)->~blocked(x);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Blocker", "!blocked(2);");
        ContextSevenPremiseNativeWriteRunner.create(home, "BodyFX", "!@x next(x+1)&&seed(x)->done(x+2);");
        ContextSevenPremiseNativeWriteRunner.create(home, "BodyDeny", "!~done(3);");
        List<String> failures = new ArrayList<>();
        for (int mode = 0; mode < 3; mode++) {
            String name = "Function" + mode;
            List<RevisionRef> settled;
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                String source = mode == 0 ? "!anchor(1);" : mode == 1 ? "!fun(1);" : "!fun(1); !~next(2);";
                ContextSevenPremiseNativeWriteRunner.require(f.root.compile(source, null, false), "X function fixture");
                long revision = f.db.getRevision();
                String fingerprint = ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                        revision, true, Collections.emptyList(), 1000).fingerprint;
                List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
                StorageLifecycleErrorCode error = null;
                boolean rejected = false;
                try {
                    f.db.connectContext("FX");
                } catch (StorageLifecycleException refusal) {
                    rejected = true;
                    error = refusal.getErrorCode();
                } catch (IllegalStateException nativeFailure) {
                    rejected = true;
                    failures.add("untyped native pair failure mode=" + mode);
                    System.out.println("function mode=" + mode + " native pair failure=" + nativeFailure.getMessage());
                }
                System.out.println("function mode=" + mode + " status=" + (error == null ? (rejected ? "untyped failure" : "accepted") : error));
                if (rejected) {
                    ContextSevenPremiseNativeWriteRunner.require(revision == f.db.getRevision()
                            && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                            && fingerprint.equals(ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                                    revision, true, Collections.emptyList(), 1000).fingerprint),
                            "function refusal preserves source and pins");
                }
                if (mode < 2 && rejected) failures.add("safe function mode=" + mode + " refused " + error);
                if (mode == 2 && error != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT)
                    failures.add("function collision not proved: " + error);
                f.db.connectContext("Safe");
                f.db.saveConnections(f.root);
                settled = ContextSevenPremiseNativeWriteRunner.pins(f);
            }
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(
                        settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)), "function topology reopens");
            }
        }
        verifyMixedDomainSafe(home);
        for (boolean reverse : new boolean[]{false, true}) {
            verifyBoundBody(home, reverse);
            verifyCrossCut(home, reverse);
            verifyConstraintCut(home, reverse);
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, "BackwardFunction")) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile("!~next(2);", null, false), "negative X premise");
            f.db.connectContext("FX");
            boolean rejected = false;
            try { f.db.connectContext("FunFact"); }
            catch (StorageLifecycleException conflict) {
                if (conflict.getErrorCode() != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT) throw conflict;
                rejected = true;
            }
            System.out.println("backward arithmetic cross-cut rejected=" + rejected);
            if (!rejected) failures.add("backward arithmetic cross-cut conflict accepted");
        }
        if (!failures.isEmpty()) {
            System.err.println("CONTEXT_ORDINARY_FUNCTION_ATTACHMENT_FAIL " + failures);
            System.exit(1);
        }
        System.out.println("CONTEXT_ORDINARY_FUNCTION_ATTACHMENT_PASS");
        System.exit(0);
    }
    private static void verifyMixedDomainSafe(Path home) throws Exception {
        for (int mode = 0; mode < 2; mode++) {
            String name = "MixedDomainSafe" + mode;
            List<RevisionRef> settled;
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                String source = mode == 0 ? "!tag(\"outside\");" : "!seed(1); !next(3); !tag(\"outside\");";
                ContextSevenPremiseNativeWriteRunner.require(f.root.compile(source, null, false), "mixed-domain safe X");
                f.db.connectContext("BodyFX");
                f.db.connectContext("BodyDeny");
                f.db.saveConnections(f.root);
                settled = ContextSevenPremiseNativeWriteRunner.pins(f);
            }
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)),
                        "safe mixed-domain arithmetic topology reopens");
            }
            System.out.println("mixed domain mode=" + mode + " accepted and reopened");
        }
    }

    private static void verifyBoundBody(Path home, boolean reverse) throws Exception {
        String name = "BoundBody" + reverse;
        List<RevisionRef> settled;
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile("!seed(1); !next(2); !tag(\"outside\");", null, false), "bound body X");
            f.db.connectContext(reverse ? "BodyFX" : "BodyDeny");
            f.db.saveConnections(f.root);
            long revision = f.db.getRevision();
            List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
            String fingerprint = ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                    revision, true, Collections.emptyList(), 1000).fingerprint;
            StorageLifecycleException conflict = null;
            try { f.db.connectContext(reverse ? "BodyDeny" : "BodyFX"); }
            catch (StorageLifecycleException refusal) { conflict = refusal; }
            ContextSevenPremiseNativeWriteRunner.require(conflict != null
                    && conflict.getErrorCode() == StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                    && !conflict.getCollisions().isEmpty(), "computed body conflict carries witnesses");
            ContextSevenPremiseNativeWriteRunner.require(revision == f.db.getRevision()
                    && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                    && fingerprint.equals(ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                            revision, true, Collections.emptyList(), 1000).fingerprint), "computed body rejection purity");
            f.db.connectContext("Safe");
            f.db.saveConnections(f.root);
            settled = ContextSevenPremiseNativeWriteRunner.pins(f);
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)),
                    "computed body settled topology reopens");
        }
        System.out.println("bound body reverse=" + reverse + " rejected with witnesses");
    }

    private static void verifyConstraintCut(Path home, boolean reverse) throws Exception {
        String name = "ConstraintFunction" + reverse;
        List<RevisionRef> settled;
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile("!seed(1);", null, false), "constraint X seed");
            f.db.connectContext("Producer");
            f.db.connectContext("FX");
            f.db.connectContext(reverse ? "Blocker" : "Guard");
            f.db.saveConnections(f.root);
            long revision = f.db.getRevision();
            List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
            String fingerprint = ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                    revision, true, Collections.emptyList(), 1000).fingerprint;
            StorageLifecycleException conflict = null;
            try { f.db.connectContext(reverse ? "Guard" : "Blocker"); }
            catch (StorageLifecycleException refusal) { conflict = refusal; }
            ContextSevenPremiseNativeWriteRunner.require(conflict != null
                    && conflict.getErrorCode() == StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                    && !conflict.getCollisions().isEmpty(), "constraint residual conflicts in the receiving private context");
            ContextSevenPremiseNativeWriteRunner.require(revision == f.db.getRevision()
                    && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                    && fingerprint.equals(ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                            revision, true, Collections.emptyList(), 1000).fingerprint), "constraint attachment refusal purity");
            f.db.connectContext("Safe");
            f.db.saveConnections(f.root);
            settled = ContextSevenPremiseNativeWriteRunner.pins(f);
            System.out.println("constraint arithmetic reverse=" + reverse + " rejected with witnesses");
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(
                    settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)), "constraint settled pins reopen");
        }
    }

    private static void verifyCrossCut(Path home, boolean reverse) throws Exception {
        String name = "CrossFunction" + reverse;
        List<RevisionRef> settled;
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile("!seed(1);", null, false), "cross-cut X seed");
            f.db.connectContext("Producer");
            f.db.connectContext(reverse ? "FX" : "Deny");
            f.db.saveConnections(f.root);
            long revision = f.db.getRevision();
            List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
            String fingerprint = ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                    revision, true, Collections.emptyList(), 1000).fingerprint;
            StorageLifecycleException conflict = null;
            try { f.db.connectContext(reverse ? "Deny" : "FX"); }
            catch (StorageLifecycleException refusal) { conflict = refusal; }
            ContextSevenPremiseNativeWriteRunner.require(conflict != null
                    && conflict.getErrorCode() == StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                    && !conflict.getCollisions().isEmpty(), "cross-cut arithmetic conflict carries witnesses");
            ContextSevenPremiseNativeWriteRunner.require(revision == f.db.getRevision()
                    && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                    && fingerprint.equals(ContextProductionInventory.inspect(f.root, f.db.getContextId(),
                            revision, true, Collections.emptyList(), 1000).fingerprint), "cross-cut arithmetic refusal purity");
            f.db.connectContext("Safe");
            f.db.saveConnections(f.root);
            settled = ContextSevenPremiseNativeWriteRunner.pins(f);
            System.out.println("cross-cut arithmetic reverse=" + reverse + " rejected with witnesses");
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(
                    settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)), "cross-cut arithmetic settled pins reopen");
        }
    }

}
