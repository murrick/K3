/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Required proposed-revision qualification through actual ordinary provider ingress. */
public final class ContextOrdinaryRevisionSwitchRunner {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("ordinary-revision-switch-");
        System.setProperty("user.home", home.toString());
        ContextSevenPremiseNativeWriteRunner.create(home, "A", "!left(1); !p(1);");
        List<String> failures = new ArrayList<>();
        for (boolean reverse : new boolean[]{false, true}) {
            String target = "B" + reverse;
            String name = "Switch" + reverse;
            ContextSevenPremiseNativeWriteRunner.create(home, target, "!safe(1);");
            List<RevisionRef> settled;
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(
                        f.root.compile("!left(1)&&right(1)->p(1);", null, false), "X rule");
                f.db.connectContext(reverse ? target : "A");
                f.db.connectContext(reverse ? "A" : target);
                f.db.saveConnections(f.root);
                UUID safeId;
                long safeRevision;
                try (ContextSevenPremiseNativeWriteRunner.Fixture b =
                             new ContextSevenPremiseNativeWriteRunner.Fixture(home, target)) {
                    ContextSevenPremiseNativeWriteRunner.require(
                            b.root.compile("!also_safe(1);", null, false), "safe target revision");
                    safeId = b.db.getContextId();
                    safeRevision = b.db.getRevision();
                }
                f.db.switchContextRevision(safeId, safeRevision);
                ContextSevenPremiseNativeWriteRunner.require(
                        ContextSevenPremiseNativeWriteRunner.pins(f).contains(new RevisionRef(safeId, safeRevision)),
                        "safe revision switch updates the exact target pin");
                f.db.saveConnections(f.root);
                UUID id;
                long conflictRevision;
                try (ContextSevenPremiseNativeWriteRunner.Fixture b =
                             new ContextSevenPremiseNativeWriteRunner.Fixture(home, target)) {
                    ContextSevenPremiseNativeWriteRunner.require(
                            b.root.compile("!right(1); !~p(1);", null, false), "new target theory");
                    id = b.db.getContextId();
                    conflictRevision = b.db.getRevision();
                }
                long revision = f.db.getRevision();
                List<RevisionRef> before = ContextSevenPremiseNativeWriteRunner.pins(f);
                String fingerprint = ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint;
                boolean rejected = false;
                try {
                    f.db.switchContextRevision(id, conflictRevision);
                } catch (StorageLifecycleException conflict) {
                    if (conflict.getErrorCode() != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT) throw conflict;
                    rejected = true;
                }
                System.out.println("reverse=" + reverse + " revision switch rejected=" + rejected);
                if (!rejected) failures.add("conflicting revision accepted reverse=" + reverse);
                else ContextSevenPremiseNativeWriteRunner.require(f.db.getRevision() == revision
                        && before.equals(ContextSevenPremiseNativeWriteRunner.pins(f))
                        && fingerprint.equals(ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint),
                        "switch refusal preserves source and pins");
                f.db.saveConnections(f.root);
                settled = ContextSevenPremiseNativeWriteRunner.pins(f);
            }
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(
                        settled.equals(ContextSevenPremiseNativeWriteRunner.pins(f)), "settled pins reopen");
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("CONTEXT_ORDINARY_REVISION_SWITCH_FAIL " + failures);
            System.exit(1);
        }
        System.out.println("CONTEXT_ORDINARY_REVISION_SWITCH_PASS");
        System.exit(0);
    }
}
