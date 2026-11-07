/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Actual topology ingress with an X rule already published before attachment. */
public final class ContextOrdinaryGroundAttachmentRunner {
    private static final String RULE = "!left(1)&&right(1)->p(1);";

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("ordinary-ground-attachment-");
        System.setProperty("user.home", home.toString());
        ContextSevenPremiseNativeWriteRunner.create(home, "A", "!left(1); !p(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "B", "!right(1); !~p(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "Safe", "!unrelated(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "TA", "!left(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "TB", "!right(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "N", "!~p(1);");
        List<String> failures = new ArrayList<>();
        for (boolean reverse : new boolean[]{false, true}) {
            for (boolean universal : new boolean[]{false, true}) {
                verify(home, reverse, universal, failures);
                verifyTrust(home, reverse, universal, failures);
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("CONTEXT_ORDINARY_GROUND_ATTACHMENT_FAIL failures=" + failures);
            System.exit(1);
        }
        System.out.println("CONTEXT_ORDINARY_GROUND_ATTACHMENT_PASS");
        System.exit(0);
    }

    private static void verifyTrust(Path home, boolean reverse, boolean universal, List<String> failures) throws Exception {
        String first = reverse ? "TB" : "TA";
        String second = reverse ? "TA" : "TB";
        String name = "TrustAttach" + first + universal;
        List<RevisionRef> settled;
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile(universal ? "!@x left(x)&&right(x)->p(x);" : RULE, null, false), "trust X rule");
            f.db.connectContext("N");
            f.db.connectContext(first, "team");
            f.db.saveConnections(f.root);
            long revision = f.db.getRevision();
            List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
            String fingerprint = org.kanger.ContextProductionInventory.inspect(f.root, f.db.getContextId(), f.db.getRevision(), true, java.util.Collections.emptyList(), 1000).fingerprint;
            boolean rejected = false;
            try {
                f.db.connectContext(second, "team");
            } catch (StorageLifecycleException conflict) {
                if (conflict.getErrorCode() != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT) throw conflict;
                rejected = true;
            }
            System.out.println("universal=" + universal + " trust " + first + " -> " + second + " rejected=" + rejected);
            if (!rejected) failures.add("trust " + first + " -> " + second + " accepted");
            else ContextSevenPremiseNativeWriteRunner.require(f.db.getRevision() == revision
                    && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                    && fingerprint.equals(org.kanger.ContextProductionInventory.inspect(f.root, f.db.getContextId(), f.db.getRevision(), true, java.util.Collections.emptyList(), 1000).fingerprint),
                    "trust refusal preserves revision, pins and X source");
            f.db.connectContext("Safe", "team");
            f.db.saveConnections(f.root);
            settled = ContextSevenPremiseNativeWriteRunner.pins(f);
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(
                    settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)), "trust attachment reopens");
        }
    }

    private static void verify(Path home, boolean reverse, boolean universal, List<String> failures) throws Exception {
        String first = reverse ? "B" : "A";
        String second = reverse ? "A" : "B";
        String name = "Attach" + first + universal;
        List<RevisionRef> settledPins;
        try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(f.root.compile(universal ? "!@x left(x)&&right(x)->p(x);" : RULE, null, false), "standalone X rule");
            f.db.connectContext(first);
            f.db.saveConnections(f.root);
            long revision = f.db.getRevision();
            List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
            String fingerprint = org.kanger.ContextProductionInventory.inspect(f.root, f.db.getContextId(), f.db.getRevision(), true, java.util.Collections.emptyList(), 1000).fingerprint;

            boolean rejected = false;
            try {
                f.db.connectContext(second);
            } catch (StorageLifecycleException conflict) {
                if (conflict.getErrorCode() != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT) throw conflict;
                rejected = true;
            }
            System.out.println("universal=" + universal + " " + first + " -> " + second + " rejected=" + rejected);
            if (rejected) {
                ContextSevenPremiseNativeWriteRunner.require(f.db.getRevision() == revision
                        && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                        && fingerprint.equals(org.kanger.ContextProductionInventory.inspect(f.root, f.db.getContextId(), f.db.getRevision(), true, java.util.Collections.emptyList(), 1000).fingerprint),
                        "refusal preserves revision, working pins and source");
            } else {
                failures.add(first + " -> " + second + " accepted");
            }
            f.db.connectContext("Safe");
            f.db.saveConnections(f.root);
            settledPins = ContextSevenPremiseNativeWriteRunner.pins(f);
            ContextSevenPremiseNativeWriteRunner.require(settledPins.size() == (rejected ? 2 : 3),
                    "safe attachment adds exactly one pin");
        }
        try (ContextSevenPremiseNativeWriteRunner.Fixture reopened =
                     new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
            ContextSevenPremiseNativeWriteRunner.require(
                    settledPins.equals(ContextSevenPremiseNativeWriteRunner.pins(reopened)),
                    "reopen preserves the actual settled topology");
        }
    }
}
