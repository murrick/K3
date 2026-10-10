/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

public final class DmzAcceptanceProofGuardRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-input-guard-").toString());
        for (String mode : new String[]{"duplicate-before", "state-before", "duplicate-after", "state-after", "scope", "late"}) run(mode);
        System.out.println("DMZ_ACCEPTANCE_PROOF_GUARD_PASS checks=" + checks);
    }
    private static void run(String mode) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision rules = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision fact = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            DmzReplayProvenance.acceptRule(q, rules, 10, DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            if (mode.endsWith("before")) {
                if (mode.startsWith("duplicate")) DmzReplayProvenance.replayRule(q, rules, 11,
                        DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> derived(x);");
                else q.query("!anchor(2);", null, false);
                boolean stale = false;
                try { guard.accept(fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);"); }
                catch (IllegalStateException expected) { stale = true; }
                require(stale, "changed baseline rejected before executing incoming input");
                require(capture.storedSnapshot().isEmpty(), "stale ticket produces no stored consequence");
                return;
            }
            require(Boolean.TRUE.equals(guard.accept(fact, 20, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "native acceptance");
            TerminalSupportCapture.Materialization stored = capture.storedSnapshot().get(0).materialization;
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, stored, 1000);
            DmzProofWitnesses.Witness witness = proof.witnesses.get(0);
            require(guard.isCurrent(proof, witness, q), "pre-input lineage matches stored proof");
            require(!guard.isCurrent(proof, witness, new Mind(user)), "foreign target rejected");
            DmzStoredProof other = DmzStoredProof.build(capture, journal, stored, 1000);
            require(!guard.isCurrent(proof, other.witnesses.get(0), q), "foreign witness graph rejected");
            if (mode.equals("duplicate-after")) {
                DmzReplayProvenance.replayRule(q, fact, 21, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);");
                require(!guard.isCurrent(proof, witness, q), "late source occurrence invalidates ticket");
            } else if (mode.equals("state-after")) {
                q.query("!anchor(2);", null, false);
                require(!guard.isCurrent(proof, witness, q), "later native mutation invalidates ticket");
            } else if (mode.equals("scope")) {
                try (DmzReplayProvenance inactive = DmzReplayProvenance.begin()) {
                    require(!guard.isCurrent(proof, witness, q), "inactive journal rejected");
                }
                try (TerminalSupportCapture inactive = TerminalSupportCapture.begin(q)) {
                    require(!guard.isCurrent(proof, witness, q), "inactive capture rejected");
                }
                require(guard.isCurrent(proof, witness, q), "owning scopes restored");
            } else {
                DmzAcceptanceProofGuard late = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
                require(Boolean.TRUE.equals(late.accept(fact, 22, DmzReplayProvenance.Authority.EXTERNAL, "!anchor(3);")), "later acceptance");
                require(!late.isCurrent(proof, witness, q), "old materialization cannot use new input baseline");
                require(!guard.isCurrent(proof, witness, q), "old ticket invalidated by later acceptance");
            }
        }
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
