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
        run(false); run(true); run(false, 0); run(false, 1); run(false, -1, true); run(true, -1, true);
        System.out.println("DMZ_REPLAY_QUERY_CASCADE_PASS checks=" + checks);
        System.exit(0);
    }
    private static void run(boolean negative) throws Exception { run(negative, -1); }
    private static void run(boolean negative, int staleMode) throws Exception { run(negative, staleMode, false); }
    private static void run(boolean negative, int staleMode, boolean deeper) throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 1,
                    DmzReplayProvenance.Authority.EXTERNAL, deeper ? "!@x source(x) -> bridge(x);" : "!@x source(x) -> middle(x);")), "first production");
            if (deeper) require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 203,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x bridge(x) -> middle(x);")), "intermediate production");
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
                    DmzStoredRetractionTransaction.ReplayPath deferredPath = null;
                    for (DmzStoredRetractionTransaction.ReplayAppliedChoice child : result.applications) {
                        DmzStoredRetractionTransaction.ReplayPath path = choice.recordReplayPath(result, child.side);
                        require(path.steps.size() == 1 && path.steps.get(0).side == child.side && path.disposition == child.disposition,
                                "historical path preserves selected conflict, side and terminal disposition");
                        boolean immutable = false;
                        try { path.steps.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                        require(immutable, "path step list is immutable");
                        boolean wrongLabel = false;
                        try { choice.replayPath(path, source, 151, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000); }
                        catch (IllegalArgumentException expected) { wrongLabel = true; }
                        require(wrongLabel && projection.isCurrent(), "path replay refuses another candidate label before admission");
                        boolean budgetFailure = false;
                        try { choice.replayPath(path, source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 1); }
                        catch (IllegalStateException expected) { budgetFailure = true; }
                        require(budgetFailure && projection.isCurrent(), "path child audit failure rolls back and permits retry");
                        DmzStoredRetractionTransaction.ReplaySymmetricProjection repeated = choice.replayPath(
                                path, source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000);
                        require(repeated.applications.size() == 1 && repeated.applications.get(0).side == child.side
                                && repeated.applications.get(0).disposition == child.disposition && projection.isCurrent(),
                                "fresh replay restores exactly the selected child and its checked terminal state");
                        if (child.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.NEEDS_BRANCHING) {
                            require(repeated.applications.get(0).conflict != path.remaining,
                                    "remaining conflict witnesses are recognized live under a fresh candidate binding");
                            deferredPath = path;
                        } else require(Boolean.TRUE.equals(repeated.applications.get(0).query.value), "queried path preserves independent native truth");
                    }
                    require(deferredPath != null, "deferred path available after rollback");
                    int nextIndex = -1;
                    for (int i = 0; i < deferredPath.remaining.conflicts.size(); ++i)
                        if (deferredPath.remaining.conflicts.get(i).ground.predicate.equals(deeper ? "bridge" : "source")) nextIndex = i;
                    require(nextIndex >= 0, "next source conflict selected");
                    boolean nestedBudget = false;
                    try { choice.extendReplayPath(deferredPath, nextIndex, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 10000, 1); }
                    catch (DmzStoredRetractionTransaction.SupportChoiceBudgetFailure expected) { nestedBudget = true; }
                    require(nestedBudget && projection.isCurrent(), "second-level mask budget rolls back restored prefix");
                    DmzStoredRetractionTransaction.ReplayPath retainedTwoStep = null;
                    boolean twoStepBudgetFailure = false;
                    boolean deferredTwoStep = false;
                    String[] queries = staleMode < 0 ? new String[] { "?source(1);", "?middle(2);", "?source(2);", "?~source(2);" }
                            : new String[] { "?source(1);" };
                    for (String statement : queries) {
                        DmzStoredRetractionTransaction.ReplayPathExtension extension = choice.extendReplayPath(
                                deferredPath, nextIndex, source, 150, candidate, statement,
                                10000, 10000, 10000, 10000, 10000, 10000);
                        require(extension.paths.size() == 2 && extension.applications.size() == 2 && projection.isCurrent(),
                                "both second choices complete and roll back");
                        for (int i = 0; i < 2; ++i) {
                            DmzStoredRetractionTransaction.ReplayPath path = extension.paths.get(i);
                            DmzStoredRetractionTransaction.ReplayAppliedChoice applied = extension.applications.get(i);
                            require(path.steps.size() == 2 && path.steps.get(0).side == deferredPath.steps.get(0).side
                                    && path.steps.get(1).side == applied.side
                                    && path.steps.get(1).conflict.predicate.equals(deeper ? "bridge" : "source")
                                    && path.steps.get(0).evidence == path.root
                                    && path.steps.get(1).evidence != deferredPath.remaining, "two-step path retains ordered choices");
                            boolean unresolved = applied.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.NEEDS_BRANCHING;
                            if (unresolved) {
                                require(deeper && applied.query == null && applied.conflict != null, "two-step terminal retains exact unresolved evidence without query");
                                deferredTwoStep = true;
                            } else require(applied.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.QUERIED
                                    && applied.query != null && applied.conflict == null, "second conflict resolves before query");
                            if (!unresolved && statement.equals("?source(1);")) require(Boolean.TRUE.equals(applied.query.value), "independent Q truth survives both choices");
                            if (!unresolved && statement.equals("?middle(2);")) require(!Boolean.TRUE.equals(applied.query.value), "first cut cannot resurrect in either grandchild");
                            if (!unresolved && statement.equals("?source(2);") && applied.side == DmzStoredRetractionTransaction.ReplaySide.OPPOSING)
                                require(!Boolean.TRUE.equals(applied.query.value), "second cut rejects positive source root");
                            if (!unresolved && statement.equals("?~source(2);") && applied.side == DmzStoredRetractionTransaction.ReplaySide.SUPPORTING)
                                require(!Boolean.TRUE.equals(applied.query.value), "second cut rejects negative source root");
                            boolean immutable = false;
                            try { path.steps.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                            require(immutable, "two-step descriptor immutable");
                            retainedTwoStep = path;
                            if (statement.equals("?source(1);")) {
                                boolean wrongLabel = false;
                                try { choice.replayTwoStepPath(path, source, 151, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { wrongLabel = true; }
                                require(wrongLabel && projection.isCurrent(), "two-step replay rejects different candidate label");
                                try { choice.replayTwoStepPath(path, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 1); }
                                catch (DmzStoredRetractionTransaction.SupportChoiceBudgetFailure expected) { twoStepBudgetFailure = true; }
                            }
                            DmzStoredRetractionTransaction.ReplayPathExtension restored = choice.replayTwoStepPath(path,
                                    source, 150, candidate, statement, 10000, 10000, 10000, 10000, 10000, 10000);
                            require(restored.paths.size() == 1 && restored.applications.size() == 1 && projection.isCurrent(),
                                    "only selected two-step path restored and completely rolled back");
                            DmzStoredRetractionTransaction.ReplayPath freshPath = restored.paths.get(0);
                            DmzStoredRetractionTransaction.ReplayAppliedChoice fresh = restored.applications.get(0);
                            require(freshPath.steps.get(0).side == path.steps.get(0).side
                                    && freshPath.steps.get(1).side == path.steps.get(1).side
                                    && freshPath.steps.get(0).evidence != path.steps.get(0).evidence
                                    && freshPath.steps.get(1).evidence != path.steps.get(1).evidence,
                                    "both exact choices recognized under fresh candidate evidence");
                            require(fresh.disposition == applied.disposition
                                    && (unresolved ? fresh.query == null && fresh.conflict != null && fresh.conflict != path.remaining
                                        : fresh.query != null && java.util.Objects.equals(fresh.query.value, applied.query.value)),
                                    "terminal evidence or isolated query recognized under cumulative cuts");
                            DmzStoredRetractionTransaction.ReplayAppliedChoice reused = choice.replayTwoStepPath(freshPath,
                                    source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 10000).applications.get(0);
                            require(unresolved ? reused.query == null && reused.disposition == fresh.disposition
                                    : reused.query != null && Boolean.TRUE.equals(reused.query.value),
                                    "fresh two-step descriptor reusable with another query");

                        }
                    }
                    if (deeper) require(deferredTwoStep, "deeper cascade qualifies remaining conflict after two restored choices");
                    require(twoStepBudgetFailure && projection.isCurrent(), "second-step replay budget failure permits complete retry");
                    require(choice.replayPath(deferredPath, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 10000).applications.size() == 1,
                            "nested extensions preserve reusable historical prefix");
                    require(!Boolean.TRUE.equals(choice.query("?" + candidate.substring(1), 10000).value), "candidate not retained after siblings");
                    if (staleMode >= 0) {
                        String beforeMutation = DmzObservationStateFingerprint.capture(q);
                        if (staleMode == 1) DmzReplayProvenance.replayRule(q, source, 201,
                                DmzReplayProvenance.Authority.EXTERNAL, "!@x source(x) -> middle(x);");
                        else {
                            boolean changedVisibility = false;
                            for (org.kanger.interfaces.IRule rule : q.getRules()) if (q.getRules().isGenerated(rule) && !rule.isDeleted(q)) {
                                ((org.kanger.units.Rule) rule).setDeleted(true, q); changedVisibility = true; break;
                            }
                            require(changedVisibility, "native Q cache visibility mutation");
                        }
                        qState = DmzObservationStateFingerprint.capture(q);
                        if (staleMode == 1) require(beforeMutation.equals(qState), "source-only Q mutation leaves native state unchanged");
                        boolean staleTwoStep = false;
                        try { choice.replayTwoStepPath(retainedTwoStep, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 10000); }
                        catch (IllegalStateException expected) { staleTwoStep = expected.getMessage().contains("boundary changed"); }
                        require(staleTwoStep && !projection.isCurrent(), "two-step replay refuses native or source-only Q mutation");
                        choice.close();
                        require(qState.equals(DmzObservationStateFingerprint.capture(q)), "stale choice close preserves external Q mutation");
                    } else {
                        choice.close();
                        boolean closedTwoStep = false;
                        try { choice.replayTwoStepPath(retainedTwoStep, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 10000); }
                        catch (IllegalStateException expected) { closedTwoStep = true; }
                        require(closedTwoStep && projection.isCurrent(), "closed choice refuses two-step replay");
                        boolean closedReplay = false;
                        try { choice.replayPath(deferredPath, source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000); }
                        catch (IllegalStateException expected) { closedReplay = true; }
                        require(closedReplay && projection.isCurrent(), "closed native choice refuses historical path replay");
                        try (DmzStoredRetractionTransaction.NativeSupportChoice foreign = overlay.beginSupportChoice(projection,
                                DmzStoredRetractionTransaction.SupportChoice.KEEP_CANDIDATE, 10000)) {
                            boolean wrongTwoStepOwner = false;
                            try { foreign.replayTwoStepPath(retainedTwoStep, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalArgumentException expected) { wrongTwoStepOwner = true; }
                            require(wrongTwoStepOwner && projection.isCurrent(), "two-step ownership cannot transfer to identical choice");
                            boolean wrongOwner = false;
                            try { foreign.replayPath(deferredPath, source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalArgumentException expected) { wrongOwner = true; }
                            require(wrongOwner && projection.isCurrent(), "identical candidate source does not transfer a path to another choice");
                        }
                    }
                }
            }
            require(qState.equals(DmzObservationStateFingerprint.capture(q)), "Q native state restored");
            require(Boolean.TRUE.equals(q.query("?source(1);", null, false)), "Q remains authoritative");
        }
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
