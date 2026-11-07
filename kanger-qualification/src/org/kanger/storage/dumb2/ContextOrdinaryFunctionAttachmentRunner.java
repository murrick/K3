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
        if (!failures.isEmpty()) {
            System.err.println("CONTEXT_ORDINARY_FUNCTION_ATTACHMENT_FAIL " + failures);
            System.exit(1);
        }
        System.out.println("CONTEXT_ORDINARY_FUNCTION_ATTACHMENT_PASS");
        System.exit(0);
    }
}
