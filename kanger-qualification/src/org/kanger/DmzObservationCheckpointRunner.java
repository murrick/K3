/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.Files;

/** Scope-local checkpoint qualification; not cross-context proof certification. */
public final class DmzObservationCheckpointRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-checkpoint-").toString());
        sourceMetadata();
        Mind q = root();
        TerminalSupportCapture capture = TerminalSupportCapture.begin(q);
        TerminalSupportCapture.Checkpoint first;
        try {
            require(q.compile("!@x a(x) -> male(x); !@x male(x) -> good(x); !a(John);", null, false), "initial program");
            first = capture.checkpoint(q);
            require(!first.graph.nodes.isEmpty(), "latest operation graph captured");
            require(capture.isCurrent(first, q), "fresh checkpoint current");
            require(Boolean.TRUE.equals(q.query("?good(John);", null, false)), "native query");
            require(capture.isCurrent(first, q), "read-only query preserves checkpoint");
            require(!q.compile("!~good(John);", null, false), "rejected change");
            require(capture.isCurrent(first, q), "rejected change preserves checkpoint");
            require(Boolean.TRUE.equals(q.query("-a(John);", null, false)), "delete support");
            require(!capture.isCurrent(first, q), "deletion invalidates checkpoint");
            TerminalSupportCapture.Checkpoint deleted = capture.checkpoint(q);
            require(capture.isCurrent(deleted, q), "new deletion checkpoint current");
            for (DmzObservedProofGraph.Node node : deleted.graph.nodes)
                require(!node.atom.equals("!good(John);") || !node.grounded, "older good proof excluded");
            require(q.compile("!a(John);", null, false), "restore support");
            require(!capture.isCurrent(first, q) && !capture.isCurrent(deleted, q), "restoration never revives old checkpoint");
            TerminalSupportCapture.Checkpoint restored = capture.checkpoint(q);
            require(capture.isCurrent(restored, q), "restored operation fresh checkpoint");
            try (TerminalSupportCapture other = TerminalSupportCapture.begin(q)) {
                require(!other.isCurrent(restored, q), "different capture cannot reuse ticket");
            }
            require(capture.isCurrent(restored, q), "outer scope restored");
        } finally { capture.close(); }
        require(!capture.isCurrent(first, q), "closed scope ticket unusable");
        System.out.println("DMZ_OBSERVATION_CHECKPOINT_PASS checks=" + checks);
    }
    private static void sourceMetadata() throws Exception {
        java.util.UUID q = java.util.UUID.randomUUID(), a = java.util.UUID.randomUUID();
        org.kanger.interfaces.internal.IContextFederation.Connection pin = pin(a, 1L);
        org.kanger.interfaces.internal.IContextFederation.Snapshot first =
                new org.kanger.interfaces.internal.IContextFederation.Snapshot("Q", q, 1L, java.util.Arrays.asList(pin));
        String signature = DmzObservationStateFingerprint.sourceSignature(first);
        require(signature.equals(DmzObservationStateFingerprint.sourceSignature(first)), "source signature repeatable");
        require(!signature.equals(DmzObservationStateFingerprint.sourceSignature(
                new org.kanger.interfaces.internal.IContextFederation.Snapshot("Q", q, 2L, java.util.Arrays.asList(pin)))), "own revision invalidates signature");
        require(!signature.equals(DmzObservationStateFingerprint.sourceSignature(
                new org.kanger.interfaces.internal.IContextFederation.Snapshot("Q", q, 1L, java.util.Arrays.asList(pin(a, 2L))))), "source pin invalidates signature");
        require(!signature.equals(DmzObservationStateFingerprint.sourceSignature(
                new org.kanger.interfaces.internal.IContextFederation.Snapshot("Q", java.util.UUID.randomUUID(), 1L, java.util.Arrays.asList(pin)))), "context identity invalidates signature");
    }
    private static org.kanger.interfaces.internal.IContextFederation.Connection pin(java.util.UUID context, long revision) {
        return new org.kanger.interfaces.internal.IContextFederation.Connection("A", context, revision, revision,
                org.kanger.interfaces.internal.IContextFederation.PinPolicy.EXACT_REVISION,
                org.kanger.interfaces.internal.IContextFederation.CompatibilityStatus.QUALIFIED, "3.8.0");
    }

    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
