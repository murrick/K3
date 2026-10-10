/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** A selected middle conflict leaves a source conflict in one symmetric child. */
public final class DmzReplayQueryCascadeRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-replay-query-cascade-").toString());
        run(false); run(true);
        System.out.println("DMZ_REPLAY_QUERY_CASCADE_PASS checks=" + checks);
        System.exit(0);
    }
    private static void run(boolean negative) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 1,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> middle(x);")), "first production");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 2,
                    DmzReplayProvenance.Authority.EXTERNAL, negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);")), "second production");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.accept(source, 3, DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "Q primary");
            TerminalSupportCapture.Materialization materialized = null;
            for (TerminalSupportCapture.StoredObservation observed : capture.storedSnapshot())
                if (observed.materialization.causes.nodes.get(observed.materialization.causes.root).ground.predicate.equals("derived"))
                    materialized = observed.materialization;
            require(materialized != null, "Q generated root");
            DmzStoredProof proof = DmzStoredProof.build(capture, journal, materialized, 10000);
            String qState = DmzObservationStateFingerprint.capture(q);
            try (DmzStoredRetractionTransaction overlay = DmzStoredRetractionTransaction.beginContinuation(
                    guard, proof, proof.witnesses.get(0), q, 10000)) {
                require(Boolean.TRUE.equals(overlay.accept(source, 4, DmzReplayProvenance.Authority.EXTERNAL, "!source(2);")), "pending fresh route");
                String candidate = negative ? "!derived(2);" : "!~derived(2);";
                DmzStoredRetractionTransaction.ConflictEvidence evidence = overlay.conflictEvidence(candidate, 10000);
                DmzStoredRetractionTransaction.SymmetricProjection projection = overlay.projectSymmetric(evidence, 10000);
                try (DmzStoredRetractionTransaction.NativeSupportChoice choice = overlay.beginSupportChoice(projection,
                        DmzStoredRetractionTransaction.SupportChoice.KEEP_CANDIDATE, 10000)) {
                    DmzStoredRetractionTransaction.ConsequenceAuditFailure packet = null;
                    try { choice.probeCandidate(source, 150, candidate, "?source(1);", 10000); }
                    catch (DmzStoredRetractionTransaction.ConsequenceAuditFailure conflict) { packet = conflict; }
                    require(packet != null && packet.kind == DmzStoredRetractionTransaction.ConsequenceConflictKind.INTERNAL, "detached internal cascade");
                    int middle = -1;
                    for (int i = 0; i < packet.conflicts.size(); ++i)
                        if (packet.conflicts.get(i).ground.predicate.equals("middle")) middle = i;
                    require(middle >= 0, "middle conflict selection");
                    DmzStoredRetractionTransaction.ReplaySymmetricProjection result = choice.queryReplayChoices(
                            packet, middle, source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000);
                    boolean deferred = false, queried = false;
                    for (DmzStoredRetractionTransaction.ReplayAppliedChoice child : result.applications) {
                        if (child.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.NEEDS_BRANCHING) {
                            require(child.query == null && child.conflict != null, "no query result for unresolved child");
                            boolean sourceConflict = false;
                            for (DmzStoredRetractionTransaction.ConsequenceConflict conflict : child.conflict.conflicts)
                                if (conflict.ground.predicate.equals("source") && !conflict.supporting.isEmpty() && !conflict.opposing.isEmpty()) sourceConflict = true;
                            require(sourceConflict, "remaining source contradiction carries both exact witness sides");
                            deferred = true;
                        } else {
                            require(child.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.QUERIED
                                    && child.conflict == null && child.query != null && Boolean.TRUE.equals(child.query.value),
                                    "consistent sibling can query independent authoritative source");
                            queried = true;
                        }
                    }
                    require(deferred && queried && projection.isCurrent(), "deferred and queried siblings roll back to current parent evidence");
                    require(!Boolean.TRUE.equals(choice.query("?" + candidate.substring(1), 10000).value), "candidate not retained after siblings");
                }
            }
            require(qState.equals(DmzObservationStateFingerprint.capture(q)), "Q native state restored");
            require(Boolean.TRUE.equals(q.query("?source(1);", null, false)), "Q remains authoritative");
        }
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
