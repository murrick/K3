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
        run(false); run(true); run(false, 0); run(false, 1); run(false, -1, true); run(true, -1, true); run(false, 0, true); run(false, 1, true);
        for (boolean negative : new boolean[] { false, true }) for (boolean deeper : new boolean[] { false, true })
            for (int variant = 1; variant < 8; ++variant) run(negative, -1, deeper, variant);
        run(false, -1, true, 0, true); run(true, -1, true, 0, true);
        run(false, -1, true, 7, true); run(true, -1, true, 7, true);
        System.out.println("DMZ_REPLAY_QUERY_CASCADE_PASS checks=" + checks);
        System.exit(0);
    }
    private static void run(boolean negative) throws Exception { run(negative, -1); }
    private static void run(boolean negative, int staleMode) throws Exception { run(negative, staleMode, false); }
    private static void run(boolean negative, int staleMode, boolean deeper) throws Exception { run(negative, staleMode, deeper, 0); }
    private static void run(boolean negative, int staleMode, boolean deeper, int variant) throws Exception { run(negative, staleMode, deeper, variant, false); }
    private static void run(boolean negative, int staleMode, boolean deeper, int variant, boolean fourth) throws Exception {
        boolean productionAliases = (variant & 1) != 0, primaryAliases = (variant & 2) != 0, independent = (variant & 4) != 0;
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision source = new IContextResults.Revision(UUID.randomUUID(), 1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 1,
                    DmzReplayProvenance.Authority.EXTERNAL, fourth ? "!@x source(x) -> hop(x);" : deeper ? "!@x source(x) -> bridge(x);" : "!@x source(x) -> middle(x);")), "first production");
            if (fourth) require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 204,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x hop(x) -> bridge(x);")), "fourth production");
            if (deeper) require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 203,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x bridge(x) -> middle(x);")), "intermediate production");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 2,
                    DmzReplayProvenance.Authority.EXTERNAL, negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);")), "second production");
            if (productionAliases) {
                DmzReplayProvenance.replayRule(q, source, 211, DmzReplayProvenance.Authority.EXTERNAL,
                        fourth ? "!@x source(x) -> hop(x);" : deeper ? "!@x source(x) -> bridge(x);" : "!@x source(x) -> middle(x);");
                DmzReplayProvenance.replayRule(q, source, 212, DmzReplayProvenance.Authority.EXTERNAL,
                        negative ? "!@x middle(x) -> ~derived(x);" : "!@x middle(x) -> derived(x);");
            }
            if (independent) require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, source, 219,
                    DmzReplayProvenance.Authority.EXTERNAL, "!@x independent(x) -> middle(x);")), "independent production");
            DmzAcceptanceProofGuard guard = DmzAcceptanceProofGuard.beforeInput(capture, journal, q);
            require(Boolean.TRUE.equals(guard.acceptAliases(source, primaryAliases ? new long[] { 3, 213 } : new long[] { 3 },
                    DmzReplayProvenance.Authority.EXTERNAL, "!source(1);")), "Q primary");
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
                if (independent) require(Boolean.TRUE.equals(overlay.accept(source, 220,
                        DmzReplayProvenance.Authority.EXTERNAL, "!independent(1);")), "independent pending root");
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
                        DmzStoredRetractionTransaction.ReplayPathExtension genericOne = choice.restoreRecordedPath(path, source, 150,
                                candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 1, 1000000);
                        require(genericOne.paths.size() == 1 && genericOne.paths.get(0).steps.size() == 1
                                && genericOne.applications.get(0).disposition == child.disposition && genericOne.traversalWork > 0,
                                "generic operator restores one-step terminal under shared work limit");
                        boolean tinyShared = false;
                        try { choice.restoreRecordedPath(path, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 1, 1); }
                        catch (DmzStoredRetractionTransaction.ReplayPathBudgetFailure expected) { tinyShared = true; }
                        require(tinyShared && projection.isCurrent(), "shared pre-admission budget refuses without changing parent");

                        if (child.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.QUERIED) {
                            boolean resolvedRefused = false;
                            try { choice.extendRecordedPath(path, 0, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                            catch (IllegalArgumentException expected) { resolvedRefused = true; }
                            require(resolvedRefused && projection.isCurrent(), "generic extension refuses already resolved path");
                            DmzStoredRetractionTransaction.ReplayTraversalResult resolvedWalk = choice.traverseRecordedBranches(path,
                                    source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 1, 1, 1000000);
                            require(resolvedWalk.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE
                                    && resolvedWalk.generatedNodes == 1 && resolvedWalk.opinions.size() == 1
                                    && resolvedWalk.frontier.isEmpty() && Boolean.TRUE.equals(resolvedWalk.opinions.get(0).application.query.value),
                                    "resolved seed yields one freshly queried conditional opinion");
                        }
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
                    DmzStoredRetractionTransaction.ReplayTraversalResult walked = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 64, 1000000);
                    require(walked.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE
                            && walked.frontier.isEmpty() && walked.declined.isEmpty() && walked.opinions.size() >= 2
                            && walked.generatedNodes % 2 == 1 && walked.generatedNodes <= 64 && walked.traversalWork > 0
                            && walked.policy == DmzStoredRetractionTransaction.ReplayTraversalPolicy.PACKET_FIRST_BREADTH_FIRST
                            && walked.maximumDepth == 4 && walked.maximumNodes == 64 && walked.workLimit == 1000000
                            && walked.candidate.equals(candidate) && walked.queryStatement.equals("?source(1);")
                            && projection.isCurrent(), "bounded packet-first traversal collects complete conditional family");
                    for (DmzStoredRetractionTransaction.ReplayOpinion opinion : walked.opinions) {
                        require(opinion.path.steps.size() <= 4 && opinion.application.query != null
                                && Boolean.TRUE.equals(opinion.application.query.value)
                                && opinion.path.steps.get(0).side == deferredPath.steps.get(0).side,
                                "each opinion retains its selected prefix and independent authoritative truth");
                        require(Boolean.TRUE.equals(choice.restoreRecordedPath(opinion.path, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 4, 1000000).applications.get(0).query.value)
                                && projection.isCurrent(), "collected detached opinion can be recognized and queried again");
                    }
                    boolean immutableOpinions = false, immutableFrontier = false;
                    try { walked.opinions.clear(); } catch (UnsupportedOperationException expected) { immutableOpinions = true; }
                    DmzStoredRetractionTransaction.ReplayTraversalResult depthStop = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 1, 64, 1000000);
                    try { depthStop.frontier.clear(); } catch (UnsupportedOperationException expected) { immutableFrontier = true; }
                    require(immutableOpinions && immutableFrontier && depthStop.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.DEPTH_LIMIT
                            && depthStop.generatedNodes == 1 && depthStop.opinions.isEmpty() && depthStop.frontier.size() == 1
                            && depthStop.frontier.get(0).path.remaining != deferredPath.remaining && projection.isCurrent(),
                            "depth ceiling keeps fresh unresolved frontier rather than inventing opinion");
                    DmzStoredRetractionTransaction.ReplayTraversalResult nodeStop = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 2, 1000000);
                    require(nodeStop.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.NODE_LIMIT
                            && nodeStop.generatedNodes == 1 && nodeStop.frontier.size() == 1 && nodeStop.opinions.isEmpty(),
                            "node ceiling reserves both symmetric children and keeps whole pending prefix");
                    DmzStoredRetractionTransaction.ReplayTraversalResult partial = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 2, 64, 1000000);
                    require(partial.generatedNodes == 3 && !partial.opinions.isEmpty()
                            && partial.opinions.size() + partial.declined.size() + partial.frontier.size() == 2,
                            "first expansion keeps every terminal or deferred sibling without deduplicating equal answers");
                    if (!partial.frontier.isEmpty()) require(partial.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.DEPTH_LIMIT
                            && partial.frontier.get(0).reason == DmzStoredRetractionTransaction.ReplayTraversalOutcome.DEPTH_LIMIT
                            && partial.frontier.get(0).recognizedInTraversal,
                            "partial collection keeps successful opinions alongside individually explained depth frontier");
                    if (deeper && !fourth && !negative && variant == 0 && staleMode < 0) {
                        DmzStoredRetractionTransaction.ReplayTraversalResult oneExpansion = choice.traverseRecordedBranches(deferredPath,
                                source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 3, 1000000);
                        require(oneExpansion.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.NODE_LIMIT
                                && !oneExpansion.opinions.isEmpty() && !oneExpansion.frontier.isEmpty(),
                                "node stop retains earlier complete opinions and unresolved sibling");
                        DmzStoredRetractionTransaction.ReplayTraversalResult partialWork = choice.traverseRecordedBranches(deferredPath,
                                source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 64, oneExpansion.traversalWork + 10);
                        require(partialWork.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.WORK_LIMIT
                                && !partialWork.opinions.isEmpty() && !partialWork.frontier.isEmpty() && partialWork.generatedNodes == 3
                                && partialWork.frontier.get(0).reason == DmzStoredRetractionTransaction.ReplayTraversalOutcome.WORK_LIMIT
                                && projection.isCurrent(), "later work exhaustion preserves previously completed opinions and whole failed prefix");
                    }
                    boolean invalidNodes = false, traversalPhase = false;
                    try { choice.traverseRecordedBranches(deferredPath, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 10000, 4, 0, 1000000); }
                    catch (IllegalArgumentException expected) { invalidNodes = true; }
                    try { choice.traverseRecordedBranches(deferredPath, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 1, 4, 64, 1000000); }
                    catch (IllegalStateException expected) { traversalPhase = true; }
                    require(invalidNodes && traversalPhase && projection.isCurrent(),
                            "invalid traversal bounds and phase failures are refusals rather than partial opinions");
                    DmzStoredRetractionTransaction.ReplayTraversalResult workStop = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 64, 1);
                    require(workStop.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.WORK_LIMIT
                            && workStop.generatedNodes == 0 && workStop.traversalWork == 1 && workStop.opinions.isEmpty()
                            && workStop.frontier.size() == 1 && workStop.frontier.get(0).path == deferredPath && !workStop.frontier.get(0).recognizedInTraversal && projection.isCurrent(),
                            "pre-recognition exhaustion preserves historical unverified frontier");
                    DmzStoredRetractionTransaction.ReplayPathExtension meteredSeed = choice.restoreRecordedPath(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 1, 1000000);
                    DmzStoredRetractionTransaction.ReplayTraversalResult nestedWorkStop = choice.traverseRecordedBranches(deferredPath,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 64, meteredSeed.traversalWork + 1);
                    require(nestedWorkStop.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.WORK_LIMIT
                            && nestedWorkStop.frontier.size() == 1 && nestedWorkStop.generatedNodes <= 1
                            && nestedWorkStop.opinions.isEmpty() && projection.isCurrent(),
                            "traversal does not reset shared allowance between restored seed and expansion");
                    DmzStoredRetractionTransaction.ReplayTraversalResult resumed = choice.traverseRecordedBranches(depthStop.frontier.get(0).path,
                            source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 4, 64, 1000000);
                    require(resumed.outcome == DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE
                            && resumed.opinions.size() == walked.opinions.size() && projection.isCurrent(),
                            "retained frontier resumes by fresh recognition under explicit new limits");

                    boolean nestedBudget = false;
                    try { choice.extendReplayPath(deferredPath, nextIndex, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 10000, 1); }
                    catch (DmzStoredRetractionTransaction.SupportChoiceBudgetFailure expected) { nestedBudget = true; }
                    require(nestedBudget && projection.isCurrent(), "second-level mask budget rolls back restored prefix");
                    DmzStoredRetractionTransaction.ReplayPath retainedTwoStep = null;
                    boolean twoStepBudgetFailure = false;
                    boolean deferredTwoStep = false;
                    boolean deferredThreeStep = false;
                    DmzStoredRetractionTransaction.ReplayPath retainedThreeStep = null;
                    DmzStoredRetractionTransaction.ReplayPath thirdPrefix = null;
                    String[] queries = independent ? new String[] { "?source(1);", "?middle(1);", negative ? "?~derived(1);" : "?derived(1);", "?middle(2);", "?source(2);", "?~source(2);" }
                            : variant != 0 ? new String[] { "?source(1);", negative ? "?~derived(1);" : "?derived(1);", "?middle(2);", "?source(2);", "?~source(2);" } : staleMode < 0 ? new String[] { "?source(1);", "?middle(2);", "?source(2);", "?~source(2);" }
                            : new String[] { "?source(1);" };
                    for (String statement : queries) {
                        DmzStoredRetractionTransaction.ReplayPathExtension extension = choice.extendReplayPath(
                                deferredPath, nextIndex, source, 150, candidate, statement,
                                10000, 10000, 10000, 10000, 10000, 10000);
                        DmzStoredRetractionTransaction.ReplayPathExtension genericExtension = choice.extendRecordedPath(
                                deferredPath, nextIndex, source, 150, candidate, statement,
                                10000, 10000, 10000, 10000, 10000, 2, 1000000);
                        compareExtension(extension, genericExtension);
                        if (statement.equals("?source(1);")) {
                            boolean depthRefused = false, indexRefused = false, labelRefused = false, phaseRefused = false;
                            try { choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, statement,
                                    10000, 10000, 10000, 10000, 1, 2, 1000000); }
                            catch (IllegalStateException expected) { phaseRefused = true; }
                            require(phaseRefused && projection.isCurrent(), "generic extension phase failure restores prefix and scopes");
                            try { choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, statement,
                                    10000, 10000, 10000, 10000, 10000, 1, 1000000); }
                            catch (IllegalArgumentException expected) { depthRefused = true; }
                            try { choice.extendRecordedPath(deferredPath, deferredPath.remaining.conflicts.size(), source, 150, candidate, statement,
                                    10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                            catch (IllegalArgumentException expected) { indexRefused = true; }
                            try { choice.extendRecordedPath(deferredPath, nextIndex, source, 151, candidate, statement,
                                    10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                            catch (IllegalArgumentException expected) { labelRefused = true; }
                            require(depthRefused && indexRefused && labelRefused && projection.isCurrent(),
                                    "generic extension rejects depth, index and candidate mismatch without publication");
                            if (!negative && variant == 0 && !deeper && staleMode < 0) {
                                boolean siblingBudget = false;
                                int low = 1, high = genericExtension.traversalWork * 2;
                                for (int attempt = 0; attempt < 24 && low <= high && !siblingBudget; ++attempt) {
                                    int allowance = low + (high - low) / 2;
                                    try { choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, statement,
                                            10000, 10000, 10000, 10000, 10000, 2, allowance); high = allowance - 1; }
                                    catch (DmzStoredRetractionTransaction.ReplayPathBudgetFailure expected) {
                                        siblingBudget = expected.completedBranches == 1;
                                        low = allowance + 1;
                                    }
                                    require(projection.isCurrent(), "budget search restores parent after each symmetric extension attempt");
                                }
                                require(siblingBudget, "shared allowance can expire in second sibling after first has rolled back");
                                compareExtension(extension, choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 2, 1000000));
                            }
                        }
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
                            if (variant != 0) {
                                int expectedDerived = (productionAliases ? 4 : 1) * (primaryAliases ? 2 : 1) - 1
                                        + (independent ? (productionAliases ? 2 : 1) : 0);
                                require(groundOneProofs(path.steps.get(0).grounds, "derived", !negative) == expectedDerived
                                        && groundOneProofs(path.steps.get(1).grounds, "derived", !negative) == expectedDerived,
                                        "exact original cut removes one derivation and retains all alternate root proofs");
                                int expectedMiddle = (productionAliases ? 2 : 1) * (primaryAliases ? 2 : 1) + (independent ? 1 : 0);
                                require(middleOneProofs(path.steps.get(0).grounds) == expectedMiddle
                                        && middleOneProofs(path.steps.get(1).grounds) == expectedMiddle,
                                        "both choices preserve all independent and aliased middle(1) proofs");
                            }
                            if (!unresolved && statement.equals(negative ? "?~derived(1);" : "?derived(1);"))
                                require(Boolean.TRUE.equals(applied.query.value), "alternate support keeps the original signed root queryable");
                            if (!unresolved && statement.equals("?middle(1);"))
                                require(Boolean.TRUE.equals(applied.query.value), "independent alternative stays natively queryable");

                            if (unresolved && statement.equals("?source(1);")) {
                                thirdPrefix = path;
                                int thirdIndex = -1;
                                for (int j = 0; j < path.remaining.conflicts.size(); ++j)
                                    if (path.remaining.conflicts.get(j).ground.predicate.equals("source")) thirdIndex = j;
                                require(thirdIndex >= 0, "third source conflict selected from deferred two-step packet");
                                boolean wrongThirdLabel = false;
                                try { choice.extendTwoStepPath(path, thirdIndex, source, 151, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { wrongThirdLabel = true; }
                                require(wrongThirdLabel && projection.isCurrent(), "third extension rejects different candidate label before admission");
                                boolean invalidThird = false;
                                try { choice.extendTwoStepPath(path, path.remaining.conflicts.size(), source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { invalidThird = true; }
                                require(invalidThird && projection.isCurrent(), "third extension rejects nonexistent historical conflict");
                                boolean thirdBudget = false;
                                try { choice.extendTwoStepPath(path, thirdIndex, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000, 1); }
                                catch (DmzStoredRetractionTransaction.SupportChoiceBudgetFailure expected) { thirdBudget = true; }
                                require(thirdBudget && projection.isCurrent(), "third mask budget failure rolls back both restored prefix levels");
                                boolean thirdRestoreBudget = false;
                                String[] thirdQueries = { "?source(1);", "?middle(2);", "?bridge(2);", "?source(2);", "?~source(2);" };
                                for (String thirdQuery : thirdQueries) {
                                    DmzStoredRetractionTransaction.ReplayPathExtension third = choice.extendTwoStepPath(path, thirdIndex,
                                            source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 10000, 10000);
                                    compareExtension(third, choice.extendRecordedPath(path, thirdIndex, source, 150, candidate, thirdQuery,
                                            10000, 10000, 10000, 10000, 10000, 3, 1000000));
                                    require(third.paths.size() == 2 && third.applications.size() == 2 && projection.isCurrent(),
                                            "both third variants complete and roll back to unchanged parent");
                                    for (int j = 0; j < 2; ++j) {
                                        DmzStoredRetractionTransaction.ReplayPath full = third.paths.get(j);
                                        DmzStoredRetractionTransaction.ReplayAppliedChoice terminal = third.applications.get(j);
                                        require(full.steps.size() == 3 && full.steps.get(0).side == path.steps.get(0).side
                                                && full.steps.get(1).side == path.steps.get(1).side && full.steps.get(2).side == terminal.side
                                                && full.steps.get(2).conflict.predicate.equals("source"), "ordered three-step descriptor preserves prefix and third choice");
                                        boolean thirdUnresolved = terminal.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.NEEDS_BRANCHING;
                                        if (thirdUnresolved) {
                                            require(fourth && terminal.query == null && terminal.conflict != null,
                                                    "four-production terminal retains remaining conflict without query");
                                            deferredThreeStep = true;
                                            int fourthIndex = -1;
                                            for (int k = 0; k < full.remaining.conflicts.size(); ++k)
                                                if (full.remaining.conflicts.get(k).ground.predicate.equals("hop")) fourthIndex = k;
                                            require(fourthIndex >= 0, "fourth live hop conflict available");
                                            DmzStoredRetractionTransaction.ReplayPathExtension fourthFamily = choice.extendRecordedPath(full, fourthIndex,
                                                    source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 4, 1000000);
                                            require(fourthFamily.paths.size() == 2 && fourthFamily.traversalWork > 0 && projection.isCurrent(),
                                                    "generic extension produces both fourth choices with checked rollback");
                                            for (int k = 0; k < 2; ++k) {
                                                DmzStoredRetractionTransaction.ReplayPath fourthPath = fourthFamily.paths.get(k);
                                                DmzStoredRetractionTransaction.ReplayAppliedChoice fourthTerminal = fourthFamily.applications.get(k);
                                                require(fourthPath.steps.size() == 4 && fourthPath.steps.get(3).side == fourthTerminal.side
                                                        && fourthTerminal.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.QUERIED
                                                        && fourthTerminal.query != null && fourthTerminal.conflict == null,
                                                        "fourth choices resolve all internal conflicts before native query");
                                                for (int level = 0; level < 3; ++level)
                                                    require(fourthPath.steps.get(level).side == full.steps.get(level).side
                                                            && fourthPath.steps.get(level).evidence != full.steps.get(level).evidence,
                                                            "fourth choice keeps freshly recognized selected prefix");
                                                if (thirdQuery.equals("?source(1);")) require(Boolean.TRUE.equals(fourthTerminal.query.value),
                                                        "Q authority survives fourth symmetric choices");
                                                if (thirdQuery.equals("?middle(2);") || thirdQuery.equals("?bridge(2);"))
                                                    require(!Boolean.TRUE.equals(fourthTerminal.query.value), "fourth choice inherits earlier rejected chain");
                                                DmzStoredRetractionTransaction.ReplayPathExtension fourthRestored = choice.restoreRecordedPath(fourthPath,
                                                        source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 4, 1000000);
                                                require(fourthRestored.paths.get(0).steps.size() == 4
                                                        && java.util.Objects.equals(fourthRestored.applications.get(0).query.value, fourthTerminal.query.value)
                                                        && projection.isCurrent(), "detached four-step records restore through generic operator");
                                            }
                                        } else require(terminal.disposition == DmzStoredRetractionTransaction.ReplayQueryDisposition.QUERIED
                                                && terminal.query != null && terminal.conflict == null, "third choice resolves source conflict before query");
                                        if (!thirdUnresolved && thirdQuery.equals("?source(1);")) require(Boolean.TRUE.equals(terminal.query.value), "Q source survives third choice");
                                        if (!thirdUnresolved && (thirdQuery.equals("?middle(2);") || thirdQuery.equals("?bridge(2);")))
                                            require(!Boolean.TRUE.equals(terminal.query.value), "both earlier cuts inherited at third level");
                                        if (!thirdUnresolved && ((thirdQuery.equals("?source(2);") && terminal.side == DmzStoredRetractionTransaction.ReplaySide.OPPOSING)
                                                || (thirdQuery.equals("?~source(2);") && terminal.side == DmzStoredRetractionTransaction.ReplaySide.SUPPORTING)))
                                            require(!Boolean.TRUE.equals(terminal.query.value), "third cut rejects selected signed source root");
                                        if (variant != 0) require(groundOneProofs(full.steps.get(2).grounds, "derived", !negative)
                                                == groundOneProofs(path.steps.get(1).grounds, "derived", !negative), "third choice preserves independent and aliased original root proofs");
                                        boolean threeImmutable = false;
                                        try { full.steps.clear(); } catch (UnsupportedOperationException expected) { threeImmutable = true; }
                                        require(threeImmutable, "three-step descriptor immutable");
                                        retainedThreeStep = full;
                                        if (thirdQuery.equals("?source(1);")) {
                                            boolean wrongThreeLabel = false;
                                            try { choice.replayThreeStepPath(full, source, 151, candidate, thirdQuery,
                                                    10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                            catch (IllegalArgumentException expected) { wrongThreeLabel = true; }
                                            require(wrongThreeLabel && projection.isCurrent(), "three-step restore rejects different candidate label");
                                            try { choice.replayThreeStepPath(full, source, 150, candidate, thirdQuery,
                                                    10000, 10000, 10000, 10000, 10000, 10000, 1); }
                                            catch (DmzStoredRetractionTransaction.SupportChoiceBudgetFailure expected) { thirdRestoreBudget = true; }
                                        }
                                        DmzStoredRetractionTransaction.ReplayPathExtension restoredThree = choice.replayThreeStepPath(full,
                                                source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 10000, 10000);
                                        DmzStoredRetractionTransaction.ReplayPathExtension genericThree = choice.restoreRecordedPath(full,
                                                source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 3, 1000000);
                                        DmzStoredRetractionTransaction.ReplayAppliedChoice genericTerminal = genericThree.applications.get(0);
                                        require(genericThree.paths.get(0).steps.size() == 3 && genericTerminal.disposition == terminal.disposition
                                                && (thirdUnresolved ? genericTerminal.query == null && genericTerminal.conflict != null
                                                    : genericTerminal.query != null && java.util.Objects.equals(genericTerminal.query.value, terminal.query.value))
                                                && projection.isCurrent(), "generic three-step restore preserves terminal evidence or fresh query");
                                        if (!negative && variant == 0 && !fourth && thirdQuery.equals("?source(1);")) {
                                            DmzStoredRetractionTransaction.ReplayPathExtension onePrefix = choice.restoreRecordedPath(deferredPath,
                                                    source, 150, candidate, thirdQuery, 10000, 10000, 10000, 10000, 10000, 1, 1000000);
                                            boolean nestedShared = false;
                                            try { choice.restoreRecordedPath(full, source, 150, candidate, thirdQuery,
                                                    10000, 10000, 10000, 10000, 10000, 3, onePrefix.traversalWork + 2); }
                                            catch (DmzStoredRetractionTransaction.ReplayPathBudgetFailure expected) { nestedShared = expected.completedLevels >= 1; }
                                            require(nestedShared && projection.isCurrent(), "shared budget exhaustion after live prefix rolls back all scopes");
                                        }
                                        require(choice.restoreRecordedPath(genericThree.paths.get(0), source, 150, candidate, thirdQuery,
                                                10000, 10000, 10000, 10000, 10000, 3, 1000000)
                                                .applications.get(0).disposition == terminal.disposition,
                                                "fresh descriptor permits repeated bounded restoration");
                                        require(restoredThree.paths.size() == 1 && restoredThree.applications.size() == 1 && projection.isCurrent(),
                                                "only selected three-step chain restored and completely rolled back");
                                        DmzStoredRetractionTransaction.ReplayPath freshThree = restoredThree.paths.get(0);
                                        for (int level = 0; level < 3; ++level)
                                            require(freshThree.steps.get(level).side == full.steps.get(level).side
                                                    && freshThree.steps.get(level).evidence != full.steps.get(level).evidence,
                                                    "each selected level recognized under fresh candidate evidence");
                                        DmzStoredRetractionTransaction.ReplayAppliedChoice restoredTerminal = restoredThree.applications.get(0);
                                        require(restoredTerminal.disposition == terminal.disposition
                                                && (thirdUnresolved ? restoredTerminal.query == null && restoredTerminal.conflict != null
                                                        && restoredTerminal.conflict != full.remaining
                                                    : restoredTerminal.query != null && java.util.Objects.equals(restoredTerminal.query.value, terminal.query.value)),
                                                "fresh three-step terminal recognizes remaining evidence or evaluates native query");
                                        DmzStoredRetractionTransaction.ReplayAppliedChoice reusedThree = choice.replayThreeStepPath(freshThree,
                                                source, 150, candidate, "?source(1);", 10000, 10000, 10000, 10000, 10000, 10000, 10000)
                                                .applications.get(0);
                                        require(thirdUnresolved ? reusedThree.query == null && reusedThree.disposition == terminal.disposition
                                                : reusedThree.query != null && Boolean.TRUE.equals(reusedThree.query.value),
                                                "fresh three-step descriptor reusable with another query");

                                        if (thirdQuery.equals("?source(1);")) {
                                            boolean unsupportedRestore = false;
                                            try { choice.replayTwoStepPath(full, source, 150, candidate, thirdQuery,
                                                    10000, 10000, 10000, 10000, 10000, 10000); }
                                            catch (IllegalArgumentException expected) { unsupportedRestore = true; }
                                            require(unsupportedRestore && projection.isCurrent(), "two-step restore refuses three-step descriptor before admission");
                                        }

                                    }
                                }
                                require(thirdRestoreBudget && projection.isCurrent(), "third-level restore budget failure permits complete retry");
                                require(choice.replayTwoStepPath(path, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000).applications.get(0).disposition
                                        == DmzStoredRetractionTransaction.ReplayQueryDisposition.NEEDS_BRANCHING,
                                        "two-step prefix reusable after complete third extensions");
                            }
                            if (!unresolved && statement.equals("?source(1);")) {
                                boolean completedThird = false;
                                try { choice.extendTwoStepPath(path, 0, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { completedThird = true; }
                                require(completedThird && projection.isCurrent(), "completed two-step path has no third conflict to extend");
                            }
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
                            if (statement.equals("?source(1);")) {
                                boolean depthRefused = false;
                                try { choice.restoreRecordedPath(path, source, 150, candidate, statement,
                                        10000, 10000, 10000, 10000, 10000, 1, 1000000); }
                                catch (IllegalArgumentException expected) { depthRefused = true; }
                                require(depthRefused && projection.isCurrent(), "declared maximum depth refuses longer path before admission");
                                DmzStoredRetractionTransaction.ReplayPathExtension genericTwo = choice.restoreRecordedPath(path, source, 150,
                                        candidate, statement, 10000, 10000, 10000, 10000, 10000, 2, 1000000);
                                require(genericTwo.paths.get(0).steps.size() == 2 && genericTwo.applications.get(0).disposition == applied.disposition
                                        && projection.isCurrent(), "generic operator restores selected two-step path");
                            }
                            require(restored.paths.size() == 1 && restored.applications.size() == 1 && projection.isCurrent(),
                                    "only selected two-step path restored and completely rolled back");
                            DmzStoredRetractionTransaction.ReplayPath freshPath = restored.paths.get(0);
                            DmzStoredRetractionTransaction.ReplayAppliedChoice fresh = restored.applications.get(0);
                            require(freshPath.steps.get(0).side == path.steps.get(0).side
                                    && freshPath.steps.get(1).side == path.steps.get(1).side
                                    && freshPath.steps.get(0).evidence != path.steps.get(0).evidence
                                    && freshPath.steps.get(1).evidence != path.steps.get(1).evidence,
                                    "both exact choices recognized under fresh candidate evidence");
                            if (variant != 0) require(groundOneProofs(freshPath.steps.get(0).grounds, "derived", !negative) == groundOneProofs(path.steps.get(0).grounds, "derived", !negative)
                                    && groundOneProofs(freshPath.steps.get(1).grounds, "derived", !negative) == groundOneProofs(path.steps.get(1).grounds, "derived", !negative)
                                    && middleOneProofs(freshPath.steps.get(0).grounds) == middleOneProofs(path.steps.get(0).grounds)
                                    && middleOneProofs(freshPath.steps.get(1).grounds) == middleOneProofs(path.steps.get(1).grounds),
                                    "restoration retains exact alternative and alias multiplicity");
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
                    if (fourth) require(deferredThreeStep, "deferred three-step terminal qualified under four-production cascade");
                    if (deeper) require(deferredTwoStep, "deeper cascade qualifies remaining conflict after two restored choices");
                    require(twoStepBudgetFailure && projection.isCurrent(), "second-step replay budget failure permits complete retry");
                    require(choice.replayPath(deferredPath, source, 150, candidate, "?source(1);",
                            10000, 10000, 10000, 10000, 10000).applications.size() == 1,
                            "nested extensions preserve reusable historical prefix");
                    require(!Boolean.TRUE.equals(choice.query("?" + candidate.substring(1), 10000).value), "candidate not retained after siblings");
                    if (staleMode >= 0) {
                        String beforeMutation = DmzObservationStateFingerprint.capture(q);
                        if (staleMode == 1) DmzReplayProvenance.replayRule(q, source, 201,
                                DmzReplayProvenance.Authority.EXTERNAL, fourth ? "!@x source(x) -> hop(x);" : deeper ? "!@x source(x) -> bridge(x);" : "!@x source(x) -> middle(x);");
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
                        if (thirdPrefix != null) {
                            boolean staleThird = false;
                            try { choice.extendTwoStepPath(thirdPrefix, 0, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalStateException expected) { staleThird = expected.getMessage().contains("boundary changed"); }
                            require(staleThird, "third extension refuses stale Q pins before admission");
                        }
                        if (retainedThreeStep != null) {
                            boolean staleGeneric = false;
                            try { choice.restoreRecordedPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 3, 1000000); }
                            catch (IllegalStateException expected) { staleGeneric = expected.getMessage().contains("boundary changed"); }
                            require(staleGeneric, "generic restore refuses stale Q pins");
                            boolean staleThree = false;
                            try { choice.replayThreeStepPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalStateException expected) { staleThree = expected.getMessage().contains("boundary changed"); }
                            require(staleThree, "three-step restore refuses stale native or source-only Q pins");
                        }
                        boolean staleExtension = false;
                        try { choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                        catch (IllegalStateException expected) { staleExtension = expected.getMessage().contains("boundary changed"); }
                        require(staleExtension, "generic extension refuses native and source-only Q staleness");
                        boolean staleTraversal = false;
                        try { choice.traverseRecordedBranches(deferredPath, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 4, 64, 1000000); }
                        catch (IllegalStateException expected) { staleTraversal = expected.getMessage().contains("boundary changed"); }
                        require(staleTraversal, "traversal refuses stale Q rather than reporting budget frontier");
                        choice.close();
                        require(qState.equals(DmzObservationStateFingerprint.capture(q)), "stale choice close preserves external Q mutation");
                    } else {
                        choice.close();
                        boolean closedExtension = false;
                        try { choice.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                        catch (IllegalStateException expected) { closedExtension = true; }
                        require(closedExtension && projection.isCurrent(), "generic extension refuses closed owner");
                        boolean closedTraversal = false;
                        try { choice.traverseRecordedBranches(deferredPath, source, 150, candidate, "?source(1);",
                                10000, 10000, 10000, 10000, 10000, 4, 64, 1000000); }
                        catch (IllegalStateException expected) { closedTraversal = true; }
                        require(closedTraversal && projection.isCurrent(), "traversal refuses closed owner");
                        if (thirdPrefix != null) {
                            boolean closedThird = false;
                            try { choice.extendTwoStepPath(thirdPrefix, 0, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalStateException expected) { closedThird = true; }
                            require(closedThird && projection.isCurrent(), "closed choice refuses third extension");
                        }
                        if (retainedThreeStep != null) {
                            boolean closedGeneric = false;
                            try { choice.restoreRecordedPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 3, 1000000); }
                            catch (IllegalStateException expected) { closedGeneric = true; }
                            require(closedGeneric && projection.isCurrent(), "closed choice refuses generic restore");
                            boolean closedThree = false;
                            try { choice.replayThreeStepPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                            catch (IllegalStateException expected) { closedThree = true; }
                            require(closedThree && projection.isCurrent(), "closed choice refuses three-step restore");
                        }
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
                            boolean foreignExtension = false;
                            try { foreign.extendRecordedPath(deferredPath, nextIndex, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 2, 1000000); }
                            catch (IllegalArgumentException expected) { foreignExtension = true; }
                            require(foreignExtension && projection.isCurrent(), "generic extension refuses foreign ownership");
                            boolean foreignTraversal = false;
                            try { foreign.traverseRecordedBranches(deferredPath, source, 150, candidate, "?source(1);",
                                    10000, 10000, 10000, 10000, 10000, 4, 64, 1000000); }
                            catch (IllegalArgumentException expected) { foreignTraversal = true; }
                            require(foreignTraversal && projection.isCurrent(), "traversal cannot adopt foreign seed");
                            if (thirdPrefix != null) {
                                boolean foreignThird = false;
                                try { foreign.extendTwoStepPath(thirdPrefix, 0, source, 150, candidate, "?source(1);",
                                        10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { foreignThird = true; }
                                require(foreignThird && projection.isCurrent(), "third extension refuses foreign path ownership");
                            }
                            if (retainedThreeStep != null) {
                                boolean foreignGeneric = false;
                                try { foreign.restoreRecordedPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                        10000, 10000, 10000, 10000, 10000, 3, 1000000); }
                                catch (IllegalArgumentException expected) { foreignGeneric = true; }
                                require(foreignGeneric && projection.isCurrent(), "generic path ownership cannot transfer to another choice");
                                boolean foreignThree = false;
                                try { foreign.replayThreeStepPath(retainedThreeStep, source, 150, candidate, "?source(1);",
                                        10000, 10000, 10000, 10000, 10000, 10000, 10000); }
                                catch (IllegalArgumentException expected) { foreignThree = true; }
                                require(foreignThree && projection.isCurrent(), "three-step ownership cannot transfer to identical choice");
                            }
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
    private static void compareExtension(DmzStoredRetractionTransaction.ReplayPathExtension expected,
            DmzStoredRetractionTransaction.ReplayPathExtension actual) {
        require(actual.paths.size() == 2 && actual.applications.size() == 2 && actual.traversalWork > 0,
                "generic extension returns complete metered symmetric family");
        for (int i = 0; i < 2; ++i) {
            DmzStoredRetractionTransaction.ReplayPath a = actual.paths.get(i), e = expected.paths.get(i);
            DmzStoredRetractionTransaction.ReplayAppliedChoice x = actual.applications.get(i), y = expected.applications.get(i);
            require(a.steps.size() == e.steps.size() && x.side == y.side && x.disposition == y.disposition
                    && (x.query == null ? y.query == null && x.conflict != null : y.query != null
                        && java.util.Objects.equals(x.query.value, y.query.value)), "generic terminal matches fixed-length reference");
            for (int level = 0; level < a.steps.size(); ++level) {
                DmzStoredRetractionTransaction.ReplayPathStep fresh = a.steps.get(level), old = e.steps.get(level);
                require(fresh.side == old.side && fresh.conflict.equivalent(old.conflict) && fresh.evidence != old.evidence
                        && fresh.grounds.size() == old.grounds.size(), "generic extension retains fresh ordered prefix and ground coverage");
                for (DmzStoredRetractionTransaction.ReplayAppliedGround row : old.grounds) {
                    boolean matched = false;
                    for (DmzStoredRetractionTransaction.ReplayAppliedGround now : fresh.grounds)
                        if (now.ground.equivalent(row.ground) && now.proofs == row.proofs && now.visible == row.visible) matched = true;
                    require(matched, "generic extension preserves exact residual multiplicity and visibility");
                }
            }
        }
    }
    private static int middleOneProofs(java.util.List<DmzStoredRetractionTransaction.ReplayAppliedGround> rows) {
        return groundOneProofs(rows, "middle", true);
    }
    private static int groundOneProofs(java.util.List<DmzStoredRetractionTransaction.ReplayAppliedGround> rows, String predicate, boolean sign) {
        for (DmzStoredRetractionTransaction.ReplayAppliedGround row : rows)
            if (row.ground.sign == sign && row.ground.predicate.equals(predicate)
                    && new java.math.BigDecimal(row.ground.arguments.get(0).materialize().getValue().toString()).intValueExact() == 1)
                return row.proofs;
        return -1;
    }
    private static void require(boolean value, String message) { ++checks; if (!value) throw new AssertionError(message); }
}
