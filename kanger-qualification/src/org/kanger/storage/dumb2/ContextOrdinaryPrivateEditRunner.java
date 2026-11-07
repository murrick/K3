/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Required full-family check before replacing a private connection layer. */
public final class ContextOrdinaryPrivateEditRunner {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("ordinary-private-edit-");
        System.setProperty("user.home", home.toString());
        ContextSevenPremiseNativeWriteRunner.create(home, "A", "!left(1); !p(1);");
        ContextSevenPremiseNativeWriteRunner.create(home, "B", "!~p(1);");
        List<String> failures = new ArrayList<>();
        for (boolean reverse : new boolean[]{false, true}) {
            String name = "Private" + reverse;
            List<String> settled;
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(
                        f.root.compile("!left(1)&&right(1)->p(1);", null, false), "X rule");
                f.db.connectContext(reverse ? "B" : "A");
                f.db.connectContext(reverse ? "A" : "B");
                f.db.saveConnections(f.root);
                f.db.applyConnectionCommand(f.root, "B", "!unrelated(1);");
                f.db.saveConnections(f.root);
                List<String> before = topology(f);
                long revision = f.db.getRevision();
                String fingerprint = ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint;
                boolean rejected = false;
                try {
                    f.db.applyConnectionCommand(f.root, "B", "!right(1);");
                } catch (StorageLifecycleException conflict) {
                    if (conflict.getErrorCode() != StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT) throw conflict;
                    rejected = true;
                }
                System.out.println("reverse=" + reverse + " private edit rejected=" + rejected);
                if (!rejected) failures.add("conflicting private edit accepted reverse=" + reverse);
                else ContextSevenPremiseNativeWriteRunner.require(f.db.getRevision() == revision
                        && before.equals(topology(f))
                        && fingerprint.equals(ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint),
                        "private refusal preserves X and exact initialization commands");
                f.db.saveConnections(f.root);
                settled = topology(f);
            }
            try (ContextSevenPremiseNativeWriteRunner.Fixture f =
                         new ContextSevenPremiseNativeWriteRunner.Fixture(home, name)) {
                ContextSevenPremiseNativeWriteRunner.require(settled.equals(topology(f)), "private layer reopens");
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("CONTEXT_ORDINARY_PRIVATE_EDIT_FAIL " + failures);
            System.exit(1);
        }
        System.out.println("CONTEXT_ORDINARY_PRIVATE_EDIT_PASS");
        System.exit(0);
    }

    private static List<String> topology(ContextSevenPremiseNativeWriteRunner.Fixture f) {
        List<String> result = new ArrayList<>();
        for (ContextConnection connection : f.db.dmzTopology()) {
            result.add(connection.getTarget().toString() + "/" + connection.getTrustGroup()
                    + "/" + connection.getInitialization().toString());
        }
        return result;
    }
}
