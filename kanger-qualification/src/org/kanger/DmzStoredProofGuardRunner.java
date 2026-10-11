/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Current-state tickets do not promote historical stored witnesses to completeness. */
public final class DmzStoredProofGuardRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-stored-guard-").toString());
        run("duplicate"); run("state"); run("delete"); run("scope"); run("child-only"); run("recursive");
        System.out.println("DMZ_STORED_PROOF_GUARD_PASS checks=" + checks);
    }
    private static void run(String mode) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision production = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        DmzStoredProofGuard guard;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            String first = mode.equals("recursive") ? "!@x source(x) -> middle(x);" : "!@x source(x) -> derived(x);";
            require(Boolean.TRUE.equals(q.query(first, null, false)), "native primary production");
            DmzReplayProvenance.replayRule(q, production, 10, DmzReplayProvenance.Authority.EXTERNAL, first);
            if (mode.equals("recursive")) {
                require(Boolean.TRUE.equals(q.query("!@x middle(x) -> derived(x);", null, false)), "native recursive production");
                DmzReplayProvenance.replayRule(q, production, 11, DmzReplayProvenance.Authority.EXTERNAL,
                        "!@x middle(x) -> derived(x);");
            }
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
                TerminalSupportCapture.Materialization stored = null;
                try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
                    require(Boolean.TRUE.equals(outer.mind().query("!source(1);", null, false)), "native primary fact");
                    for (TerminalSupportCapture.StoredObservation item : capture.storedSnapshot())
                        if (item.materialization.causes.nodes.get(item.materialization.causes.root).ground.predicate.equals("derived"))
                            stored = item.materialization;
                    if (mode.equals("child-only")) DmzReplayProvenance.replayRule(outer.mind(), fact, 20,
                            DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                    require(outer.commit(), "native stored result commits");
                }
                require(stored != null, "stored identity captured");
                if (!mode.equals("child-only")) DmzReplayProvenance.replayRule(q, fact, 20,
                        DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
                require(proof.witnesses.size() == 1 && !proof.complete, "one historical source witness");
                guard = DmzStoredProofGuard.atCurrentState(capture, q, proof, proof.witnesses.get(0));
                require(guard.isCurrent(q) != mode.equals("child-only"), "selected sources must be visible from exact target");
                require(!guard.isCurrent(new Mind(user)), "another target cannot reuse ticket");
                require(!DmzStoredProofGuard.atCurrentState(capture, q, proof, null).isCurrent(q), "missing selection rejected");
                DmzStoredProof otherProof = DmzStoredProof.build(capture, journal, stored, 1000);
                require(!DmzStoredProofGuard.atCurrentState(capture, q, proof, otherProof.witnesses.get(0)).isCurrent(q),
                        "witness from another detached graph cannot reuse proof ticket");
                DmzStoredProof bounded = DmzStoredProof.build(capture, journal, stored, 1);
                require(!DmzStoredProofGuard.atCurrentState(capture, q, bounded,
                        bounded.witnesses.isEmpty() ? null : bounded.witnesses.get(0)).isCurrent(q), "truncated proof rejected");
                if (mode.equals("duplicate")) {
                    TerminalSupportCapture.Checkpoint state = capture.baselineCheckpoint(q);
                    DmzReplayProvenance.replayRule(q, fact, 21, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                    require(capture.isCurrent(state, q), "canonical duplicate does not alter native state");
                    require(!guard.isCurrent(q), "new source occurrence invalidates provenance ticket");
                } else if (mode.equals("state")) {
                    q.compileLine("!anchor(2);", false, new java.util.LinkedList<org.kanger.interfaces.ITerm>());
                    require(!guard.isCurrent(q), "native state change invalidates ticket");
                } else if (mode.equals("delete")) {
                    q.query("-source(1);", null, false);
                    require(!guard.isCurrent(q), "deletion invalidates historical proof ticket");
                    require(!DmzStoredProofGuard.atCurrentState(capture, q, proof, proof.witnesses.get(0)).isCurrent(q),
                            "new ticket cannot revive deleted stored root or primary support");
                } else if (mode.equals("scope")) {
                    try (TerminalSupportCapture other = TerminalSupportCapture.begin(q)) {
                        require(!guard.isCurrent(q), "inactive capture rejected");
                    }
                    require(guard.isCurrent(q), "capture restored without mutation");
                    try (DmzReplayProvenance other = DmzReplayProvenance.begin()) {
                        require(!guard.isCurrent(q), "inactive replay journal rejected");
                    }
                    require(guard.isCurrent(q), "owning journal restored");
                }
            }
            require(!guard.isCurrent(q), "closed capture invalidates ticket");
        }
        require(!guard.isCurrent(q), "closed replay journal invalidates ticket");
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
