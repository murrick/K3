/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.udf.UDF;
import java.nio.file.Files;

/** Technical transaction settlement qualification; not revision certification. */
public final class DmzTransactionObservationRunner {
    private static int checks;
    private static final String PROGRAM = "!@x a(x) -> male(x); !a(John);";
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-settlement-").toString());
        accepted(); rejected(); nested(false); nested(true); autoRollback(); lateScope(); branchBoundary();
        System.out.println("DMZ_TRANSACTION_OBSERVATION_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static void accepted() throws Exception {
        Mind q = root();
        TerminalSupportCapture.SettlementSnapshot snapshot;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(q.compile(PROGRAM, null, false), "accepted compile");
            snapshot = capture.settlementSnapshot();
            require(!snapshot.accepted.isEmpty(), "committed observations accepted");
            require(!DmzObservedProofGraph.build(snapshot.accepted).nodes.isEmpty(), "accepted graph build");
            require(snapshot.pending == 0 && snapshot.discarded == 0 && snapshot.untracked == 0, "all observations settled");
        }
        require(!snapshot.accepted.isEmpty(), "settlement snapshot detached");
        try { snapshot.accepted.clear(); throw new AssertionError("mutable accepted list"); }
        catch (UnsupportedOperationException expected) { ++checks; }
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "accepted native truth");
    }
    private static void rejected() throws Exception {
        Mind q = root();
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            require(!q.compile(PROGRAM + " !@x b(x) -> ~male(x); !b(John);", null, false), "rejected compile");
            require(!capture.applicationSnapshot().isEmpty(), "raw rejected observations retained");
            TerminalSupportCapture.SettlementSnapshot snapshot = capture.settlementSnapshot();
            require(snapshot.accepted.isEmpty() && snapshot.pending == 0, "rejected observations not accepted");
            require(snapshot.discarded == capture.applicationSnapshot().size(), "rejection discards entire lineage");
            require(DmzObservedProofGraph.build(snapshot.accepted).nodes.isEmpty(), "rejected lineage absent from settled graph");
        }
        System.out.println("DMZ_REJECTED_OBSERVATIONS_PASS");
    }
    private static void nested(boolean commit) throws Exception {
        Mind q = root();
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin();
             TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
            require(outer.mind().compile(PROGRAM, null, false), "inner committed compile");
            TerminalSupportCapture.SettlementSnapshot pending = capture.settlementSnapshot();
            require(pending.accepted.isEmpty() && pending.pending > 0, "outer pending prevents acceptance");
            if (commit) require(outer.commit(), "outer commit"); else outer.rollback();
            TerminalSupportCapture.SettlementSnapshot settled = capture.settlementSnapshot();
            require(settled.pending == 0, "outer settled");
            require(commit ? !settled.accepted.isEmpty() && settled.discarded == 0
                    : settled.accepted.isEmpty() && settled.discarded > 0, "ancestor outcome controls observations");
            require(pending.accepted.isEmpty() && pending.pending > 0, "earlier snapshot unchanged");
        }
        System.out.println("DMZ_NESTED_OBSERVATIONS_PASS commit=" + commit);
    }
    private static void autoRollback() throws Exception {
        Mind q = root();
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            try {
                try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
                    require(outer.mind().compile(PROGRAM, null, false), "pre-exception compile");
                    throw new IllegalArgumentException("qualification rollback");
                }
            } catch (IllegalArgumentException expected) { ++checks; }
            TerminalSupportCapture.SettlementSnapshot snapshot = capture.settlementSnapshot();
            require(snapshot.accepted.isEmpty() && snapshot.pending == 0 && snapshot.discarded > 0, "automatic rollback discards lineage");
        }
    }
    private static void lateScope() throws Exception {
        Mind q = root();
        try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q)) {
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
                require(outer.mind().compile(PROGRAM, null, false), "late-scope compile");
                // Child commit is observed, but its outer transaction predates the scope.
                // This must not be represented as an unqualified current-state certificate.
                require(capture.settlementSnapshot().accepted.isEmpty()
                        && capture.settlementSnapshot().untracked > 0, "unknown outer lineage fails closed");
            }
            outer.rollback();
        }
    }
    private static void branchBoundary() throws Exception {
        Mind q = root();
        try (TechnicalMindTransaction branch = TechnicalMindTransaction.beginIsolated(q)) {
            try (TerminalSupportCapture capture = TerminalSupportCapture.begin(branch.mind())) {
                require(branch.mind().compile(PROGRAM, null, false), "explicit branch boundary compile");
                TerminalSupportCapture.SettlementSnapshot snapshot = capture.settlementSnapshot();
                require(!snapshot.accepted.isEmpty() && snapshot.untracked == 0, "technical commit to explicit branch accepted");
                require(Boolean.TRUE.equals(branch.mind().query("?male(John);", null, false)), "branch native truth");
            }
            branch.rollback();
        }
        System.out.println("DMZ_EXPLICIT_BRANCH_BOUNDARY_PASS");
    }

    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
