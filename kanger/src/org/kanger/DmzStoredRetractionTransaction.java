/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Rollback-only native overlay with narrowly qualified unit-application restrictions.
 * Recursive continuation accepts signed unary integer facts and queries.
 */
final class DmzStoredRetractionTransaction implements AutoCloseable {
    private final TechnicalMindTransaction transaction;
    private final DmzTerminalRestriction restriction;
    private final boolean continuationOnly;
    private final List<Rule> retracted;
    private final Mind authorityTarget;
    private final String authorityState;
    private final DmzReplayProvenance.SourceCheckpoint authoritySources;
    private int cacheAdmissions;
    private boolean closed;
    private DmzStoredRetractionTransaction(TechnicalMindTransaction transaction, DmzTerminalRestriction restriction,
            boolean continuationOnly, List<Rule> retracted, Mind authorityTarget,
            String authorityState, DmzReplayProvenance.SourceCheckpoint authoritySources) {
        this.transaction = transaction; this.restriction = restriction;
        this.continuationOnly = continuationOnly;
        this.retracted = new ArrayList<Rule>(retracted);
        this.authorityTarget = authorityTarget;
        this.authorityState = authorityState;
        this.authoritySources = authoritySources;
    }
    static DmzStoredRetractionTransaction begin(DmzAcceptanceProofGuard guard, DmzStoredProof proof,
            DmzProofWitnesses.Witness blocked, Mind target, int budget) throws Exception {
        return begin(guard, proof, blocked, target, budget, false);
    }
    static DmzStoredRetractionTransaction beginContinuation(DmzAcceptanceProofGuard guard, DmzStoredProof proof,
            DmzProofWitnesses.Witness blocked, Mind target, int budget) throws Exception {
        return begin(guard, proof, blocked, target, budget, true);
    }
    private static DmzStoredRetractionTransaction begin(DmzAcceptanceProofGuard guard, DmzStoredProof proof,
            DmzProofWitnesses.Witness blocked, Mind target, int budget, boolean continuationOnly) throws Exception {
        DmzStoredRestrictionProbe projection = DmzStoredRestrictionProbe.project(guard, proof, blocked, target, budget);
        if (!projection.eligible) throw new IllegalStateException("Current fully audited restriction projection required");
        if (continuationOnly) DmzTerminalRestriction.validateContinuation(blocked, target);
        else DmzTerminalRestriction.validate(blocked, target);
        List<Rule> removed = new ArrayList<Rule>();
        for (DmzStoredRestrictionProbe.Node node : projection.nodes) if (node.retracted()) {
            TerminalSupportCapture.Ground expected = proof.graph.observed.nodes.get(node.index).ground;
            boolean found = false;
            for (IRule candidate : target.getRules()) {
                if (candidate.isDeleted(target) || candidate.isQuery()) continue;
                Rule rule = (Rule) candidate;
                if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
                Domain domain = rule.getTree().get(0).get(0);
                TerminalSupportCapture.Ground actual = TerminalSupportCapture.Ground.capture(new Solve(
                        domain.getPredicate(), domain.isAntc(), domain.getArguments().convertBase(target)), target);
                if (!expected.equivalent(actual)) continue;
                if (!target.getRules().isGenerated(rule)) throw new IllegalStateException("Primary rule cannot be retracted");
                removed.add(rule); found = true;
            }
            if (!found) throw new IllegalStateException("Retracted generated atom must be live");
        }
        // Revalidate immediately before opening the native child overlay.
        if (!guard.isCurrent(proof, blocked, target)) throw new IllegalStateException("Restriction boundary changed");
        String authorityState = DmzObservationStateFingerprint.capture(target);
        DmzReplayProvenance.SourceCheckpoint authoritySources = DmzReplayProvenance.sourceCheckpoint(target);
        TechnicalMindTransaction transaction = TechnicalMindTransaction.beginIsolated(target);
        try {
            for (Rule rule : removed) rule.setDeleted(true, transaction.mind());
            return new DmzStoredRetractionTransaction(transaction, continuationOnly
                    ? DmzTerminalRestriction.beginContinuation(transaction.mind(), blocked)
                    : DmzTerminalRestriction.begin(transaction.mind(), blocked), continuationOnly, removed, target, authorityState, authoritySources);
        } catch (Exception failure) {
            transaction.close(); throw failure;
        }
    }
    /** Read-only eligibility view; deliberately does not expose the Mind for inference. */
    boolean hasLiveRule(long nativeRule) {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        Mind child = transaction.mind();
        for (IRule rule : child.getRules()) if (rule.getId() == nativeRule && !rule.isDeleted(child)) return true;
        return false;
    }
    boolean hasLiveGround(TerminalSupportCapture.Ground expected) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        Mind child = transaction.mind();
        for (IRule candidate : child.getRules()) {
            if (candidate.isDeleted(child) || candidate.isQuery()) continue;
            Rule rule = (Rule) candidate;
            if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
            Domain literal = rule.getTree().get(0).get(0);
            if (expected.equivalent(TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                    literal.isAntc(), literal.getArguments().convertBase(child)), child))) return true;
        }
        return false;
    }
    enum InputKind { FRESH, EXISTING_PRIMARY, SUPPORTED_DERIVED, CONFLICT, INCONSISTENT }
    enum CandidateDisposition { REJECT_Q, NEEDS_BRANCHING, REUSE_PRIMARY, PROBE_NEW_INPUT }
    static final class CandidateAssessment {
        final InputKind branch, authority;
        final CandidateDisposition disposition;
        private CandidateAssessment(InputKind branch, InputKind authority) {
            this.branch = branch; this.authority = authority;
            disposition = authority == InputKind.CONFLICT || authority == InputKind.INCONSISTENT
                    ? CandidateDisposition.REJECT_Q
                    : branch == InputKind.CONFLICT || branch == InputKind.INCONSISTENT
                    ? CandidateDisposition.NEEDS_BRANCHING
                    : branch == InputKind.EXISTING_PRIMARY ? CandidateDisposition.REUSE_PRIMARY
                    : CandidateDisposition.PROBE_NEW_INPUT;
        }
    }
    /** Detached checked-boundary diagnostic, not a permission to accept or reuse a source. */
    CandidateAssessment assessCandidate(String statement, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        Mind child = transaction.mind();
        String state = DmzObservationStateFingerprint.capture(child);
        DmzReplayProvenance.SourceCheckpoint sources = DmzReplayProvenance.sourceCheckpoint(child);
        InputKind authority = classifyAuthorityInput(statement, budget);
        InputKind branch = classifyInput(statement, budget);
        requireAuthorityCurrent();
        if (!state.equals(DmzObservationStateFingerprint.capture(child)) || !sources.isCurrent(child))
            throw new IllegalStateException("Candidate assessment branch boundary changed");
        return new CandidateAssessment(branch, authority);
    }
    static final class ConflictSupport {
        final DmzProofWitnesses.Witness witness;
        final List<DmzReplayProvenance.Binding> accepted, pending;
        private ConflictSupport(DmzProofWitnesses.Witness witness, List<DmzReplayProvenance.Binding> accepted,
                List<DmzReplayProvenance.Binding> pending) {
            this.witness = witness;
            this.accepted = java.util.Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(accepted));
            this.pending = java.util.Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(pending));
        }
    }
    static final class ConflictEvidence {
        final CandidateAssessment assessment;
        final List<ConflictSupport> supports;
        final TerminalSupportCapture.Ground candidate;
        private final DmzStoredRetractionTransaction owner;
        private final String state;
        private final DmzReplayProvenance.SourceCheckpoint sources;
        private ConflictEvidence(DmzStoredRetractionTransaction owner, CandidateAssessment assessment,
                List<ConflictSupport> supports, String state, DmzReplayProvenance.SourceCheckpoint sources) {
            this.owner = owner; this.assessment = assessment; this.state = state; this.sources = sources;
            this.supports = java.util.Collections.unmodifiableList(new ArrayList<ConflictSupport>(supports));
            DmzProofWitnesses.Witness first = supports.get(0).witness;
            TerminalSupportCapture.Ground opposing = first.graph.observed.nodes.get(first.node).ground;
            this.candidate = new TerminalSupportCapture.Ground(opposing.predicate, !opposing.sign, opposing.arguments);
        }
        boolean isCurrent() throws Exception {
            return !owner.closed && owner.authorityState.equals(DmzObservationStateFingerprint.capture(owner.authorityTarget))
                    && owner.authoritySources.isCurrent(owner.authorityTarget)
                    && state.equals(DmzObservationStateFingerprint.capture(owner.transaction.mind()))
                    && sources.isCurrent(owner.transaction.mind());
        }
    }
    /** Exact opposing branch witnesses, never a cut or a pending-evidence no-good. */
    ConflictEvidence conflictEvidence(String candidate, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        Mind child = transaction.mind();
        String state = DmzObservationStateFingerprint.capture(child);
        DmzReplayProvenance.SourceCheckpoint sources = DmzReplayProvenance.sourceCheckpoint(child);
        CandidateAssessment assessment = assessCandidate(candidate, budget);
        if (assessment.disposition != CandidateDisposition.NEEDS_BRANCHING || assessment.branch != InputKind.CONFLICT)
            throw new IllegalArgumentException("Unambiguous branch-only conflict required");
        DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child, budget, true);
        if (!inventory.eligible) throw new IllegalStateException("Conflict inventory unavailable: " + inventory.gaps);
        int open = candidate.indexOf('(');
        boolean sign = !candidate.startsWith("!~");
        String predicate = candidate.substring(sign ? 1 : 2, open);
        java.math.BigDecimal value = new java.math.BigDecimal(candidate.substring(open + 1, candidate.length() - 2));
        List<ConflictSupport> supports = new ArrayList<ConflictSupport>();
        int remaining = budget;
        int[] sourceBudget = new int[] {budget};
        for (DmzObservedProofGraph.Node node : inventory.graph.observed.nodes) {
            if (remaining-- == 0) throw new IllegalStateException("Conflict ground scan budget exceeded");
            TerminalSupportCapture.Ground ground = node.ground;
            if (ground.sign == sign || !ground.predicate.equals(predicate) || ground.arguments.size() != 1) continue;
            Object actual = ground.arguments.get(0).materialize().getValue();
            if (!(actual instanceof Number) || value.compareTo(new java.math.BigDecimal(actual.toString())) != 0) continue;
            DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(ground, DmzTerminalRestriction.activeNoGoods(child), budget);
            if (proofs.truncated) throw new IllegalStateException("Conflict witness budget exceeded");
            for (DmzProofWitnesses.Witness witness : proofs.witnesses) {
                if (supports.size() >= budget) throw new IllegalStateException("Conflict support budget exceeded");
                List<DmzReplayProvenance.Binding> accepted = new ArrayList<DmzReplayProvenance.Binding>();
                List<DmzReplayProvenance.Binding> pending = new ArrayList<DmzReplayProvenance.Binding>();
                collectConflictSources(child, witness, accepted, pending,
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<DmzReplayProvenance.Binding, Boolean>()),
                        sourceBudget);
                if (pending.isEmpty()) throw new IllegalStateException("Branch conflict must have exact pending support");
                supports.add(new ConflictSupport(witness, accepted, pending));
            }
        }
        if (supports.isEmpty() || !inventory.isCurrent(child)) throw new IllegalStateException("Current opposing support required");
        ConflictEvidence result = new ConflictEvidence(this, assessment, supports, state, sources);
        if (!result.isCurrent()) throw new IllegalStateException("Conflict evidence boundary changed");
        return result;
    }
    private static void collectConflictSources(Mind child, DmzProofWitnesses.Witness witness,
            List<DmzReplayProvenance.Binding> accepted, List<DmzReplayProvenance.Binding> pending,
            java.util.Set<DmzReplayProvenance.Binding> seen, int[] remaining) throws Exception {
        if (remaining[0]-- == 0) throw new IllegalStateException("Conflict source traversal budget exceeded");
        if (seen.add(witness.source)) {
            boolean found = false;
            for (DmzReplayProvenance.SourceObservation observation : DmzReplayProvenance.observedSources(child, witness.source.nativeRule)) {
                if (remaining[0]-- == 0) throw new IllegalStateException("Conflict source lookup budget exceeded");
                if (observation.binding != witness.source) continue;
                if (observation.outcome == DmzReplayProvenance.Outcome.ACCEPTED) accepted.add(witness.source);
                else if (observation.outcome == DmzReplayProvenance.Outcome.PENDING) pending.add(witness.source);
                else throw new IllegalStateException("Live conflict source outcome required");
                found = true; break;
            }
            if (!found) throw new IllegalStateException("Exact conflict source observation required");
        }
        for (DmzProofWitnesses.Witness premise : witness.premises)
            collectConflictSources(child, premise, accepted, pending, seen, remaining);
    }
    /** Read-only current-proof diagnostic; never an admission or durable permission. */
    InputKind classifyInput(String statement, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (!continuationOnly || statement == null
                || !statement.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer continuation classification is qualified");
        if (budget < 1 || budget > 10000) throw new IllegalArgumentException("Classification budget must be between 1 and 10000");
        return classifyCurrent(transaction.mind(), statement, budget, true,
                DmzTerminalRestriction.activeNoGoods(transaction.mind()));
    }
    /** Settled Q proof diagnostic at the pinned authority boundary, independent of branch no-goods. */
    InputKind classifyAuthorityInput(String statement, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (!continuationOnly || statement == null
                || !statement.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer authority classification is qualified");
        if (budget < 1 || budget > 10000) throw new IllegalArgumentException("Classification budget must be between 1 and 10000");
        requireAuthorityCurrent();
        InputKind result = classifyCurrent(authorityTarget, statement, budget, false,
                java.util.Collections.<DmzProofWitnesses.NoGood>emptyList());
        requireAuthorityCurrent();
        return result;
    }
    private void requireAuthorityCurrent() throws Exception {
        if (!authorityState.equals(DmzObservationStateFingerprint.capture(authorityTarget))
                || !authoritySources.isCurrent(authorityTarget))
            throw new IllegalStateException("Pinned Q authority boundary changed");
    }
    private static InputKind classifyCurrent(Mind child, String statement, int budget, boolean allowPending,
            List<DmzProofWitnesses.NoGood> noGoods) throws Exception {
        DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child, budget, allowPending);
        if (!inventory.eligible) throw new IllegalStateException("Classification inventory unavailable: " + inventory.gaps);
        int open = statement.indexOf('(');
        boolean positive = !statement.startsWith("!~");
        String predicate = statement.substring(positive ? 1 : 2, open);
        java.math.BigDecimal value = new java.math.BigDecimal(statement.substring(open + 1, statement.length() - 2));
        boolean same = false, opposite = false, primary = false;
        int remaining = budget;
        for (DmzObservedProofGraph.Node node : inventory.graph.observed.nodes) {
            if (remaining-- == 0) throw new IllegalStateException("Classification ground scan budget exceeded");
            TerminalSupportCapture.Ground ground = node.ground;
            if (!ground.predicate.equals(predicate) || ground.arguments.size() != 1) continue;
            Object actual = ground.arguments.get(0).materialize().getValue();
            if (!(actual instanceof Number) || value.compareTo(new java.math.BigDecimal(actual.toString())) != 0) continue;
            DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(ground, noGoods, budget);
            if (proofs.truncated) throw new IllegalStateException("Classification witness budget exceeded");
            if (proofs.witnesses.isEmpty()) continue;
            if (ground.sign == positive) {
                same = true;
                for (DmzProofWitnesses.Witness witness : proofs.witnesses) if (witness.step == -1) primary = true;
            } else opposite = true;
        }
        if (!inventory.isCurrent(child)) throw new IllegalStateException("Classification boundary changed");
        return same && opposite ? InputKind.INCONSISTENT : opposite ? InputKind.CONFLICT
                : primary ? InputKind.EXISTING_PRIMARY : same ? InputKind.SUPPORTED_DERIVED : InputKind.FRESH;
    }
    Boolean accept(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            DmzReplayProvenance.Authority authority, String statement) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (statement == null || !statement.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only explicit unary integer-fact continuation is qualified");
        try {
            Boolean accepted = DmzReplayProvenance.acceptRule(transaction.mind(), source, sourceRule, authority, statement);
            if (continuationOnly && Boolean.TRUE.equals(accepted)) reconcileCache();
            return accepted;
        } catch (Exception failure) {
            close(); throw failure;
        }
    }
    private void reconcileCache() throws Exception {
        Mind child = transaction.mind();
        DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child, 10000, true);
        if (!inventory.eligible) throw new IllegalStateException("Continuation cache audit unavailable: " + inventory.gaps);
        List<Rule> admitted = new ArrayList<Rule>();
        for (Rule rule : retracted) {
            if (!rule.isDeleted(child)) continue;
            Domain literal = rule.getDomain();
            TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                    literal.isAntc(), literal.getArguments().convertBase(child)), child);
            DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(ground, DmzTerminalRestriction.activeNoGoods(child), 10000);
            if (proofs.truncated) throw new IllegalStateException("Continuation cache proof budget exceeded");
            if (!proofs.witnesses.isEmpty()) admitted.add(rule);
        }
        if (!inventory.isCurrent(child)) throw new IllegalStateException("Cache admission boundary changed");
        for (Rule rule : admitted) { rule.setDeleted(false, child); ++cacheAdmissions; }
    }
    int deniedCount() { return restriction.deniedCount(); }
    int inventoryCheckCount() { return restriction.inventoryCheckCount(); }
    int cacheAdmissionCount() { return cacheAdmissions; }
    DmzCurrentUnaryProofInventory.Proofs proofs(TerminalSupportCapture.Ground ground, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        Mind child = transaction.mind();
        return DmzCurrentUnaryProofInventory.capture(child, 10000, true).proofs(ground,
                DmzTerminalRestriction.activeNoGoods(child), budget);
    }
    Boolean query(String statement) throws Exception {
        if (continuationOnly) throw new IllegalArgumentException("Recursive continuation query path is not qualified");
        if (closed || statement == null || !statement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only open unary integer-fact queries are qualified");
        return transaction.mind().query(statement, null, false);
    }
    int queryDeniedCount() { return restriction.queryDeniedCount(); }
    /** Detached diagnostic hypotheses, never accepted proof inputs. */
    static final class QueryResult {
        enum CandidateMode { NONE, NEW_INPUT, EXISTING_PRIMARY }
        final CandidateMode candidateMode;
        final Boolean value;
        final List<String> hypotheses;
        final List<String> hypothesisAssertions;
        private QueryResult(Boolean value, List<String> hypotheses, List<String> assertions) {
            this(value, hypotheses, assertions, CandidateMode.NONE);
        }
        private QueryResult(Boolean value, List<String> hypotheses, List<String> assertions, CandidateMode mode) {
            this.value = value;
            this.candidateMode = mode;
            this.hypotheses = java.util.Collections.unmodifiableList(new ArrayList<String>(hypotheses));
            this.hypothesisAssertions = java.util.Collections.unmodifiableList(new ArrayList<String>(assertions));
        }
    }
    Boolean queryContinuation(String statement) throws Exception { return queryContinuation(statement, 10000); }
    Boolean queryContinuation(String statement, int auditBudget) throws Exception {
        return queryContinuationResult(statement, auditBudget).value;
    }
    QueryResult queryContinuationResult(String statement, int auditBudget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (!continuationOnly || statement == null || !statement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer continuation queries are qualified");
        if (auditBudget < 1 || auditBudget > 10000) throw new IllegalArgumentException("Query audit budget must be between 1 and 10000");
        Mind child = transaction.mind();
        String state = DmzObservationStateFingerprint.capture(child);
        List<String> previousHypotheses = hypothesisText(child);
        try {
            QueryResult result;
            try (DmzTerminalRestriction.QueryScope audit = DmzTerminalRestriction.prepareQuery(child, auditBudget);
                TechnicalMindTransaction query = TechnicalMindTransaction.beginIsolated(child)) {
                Boolean value = query.mind().query(statement, null, false);
                result = querySnapshot(value, query.mind());
            }
            if (!state.equals(DmzObservationStateFingerprint.capture(child)))
                throw new IllegalStateException("Continuation query changed its branch boundary");
            if (!previousHypotheses.equals(hypothesisText(child)))
                throw new IllegalStateException("Continuation query changed branch hypotheses");
            return result;
        } catch (Exception failure) { close(); throw failure; }
    }
    /** Conditional relevance probe; the candidate and its consequences always roll back. */
    QueryResult probeCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            String candidate, String queryStatement, int auditBudget) throws Exception {
        return probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, 10000);
    }
    QueryResult probeCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            String candidate, String queryStatement, int auditBudget, int lookupBudget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        validateCandidate(source, sourceRule, candidate, queryStatement, auditBudget, lookupBudget);
        Mind child = transaction.mind();
        String state = DmzObservationStateFingerprint.capture(child);
        List<String> hypotheses = hypothesisText(child);
        try {
            QueryResult result;
            try (TechnicalMindTransaction probe = TechnicalMindTransaction.beginIsolated(child)) {
                QueryResult.CandidateMode mode = existingPrimary(child, candidate, lookupBudget)
                        ? QueryResult.CandidateMode.EXISTING_PRIMARY : QueryResult.CandidateMode.NEW_INPUT;
                if (mode == QueryResult.CandidateMode.NEW_INPUT
                        && !Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(probe.mind(), source, sourceRule,
                                DmzReplayProvenance.Authority.EXTERNAL, candidate)))
                    throw new IllegalStateException("Candidate was not accepted in isolated probe");
                try (DmzTerminalRestriction.QueryScope audit = DmzTerminalRestriction.prepareQuery(probe.mind(), auditBudget)) {
                    Boolean value = probe.mind().query(queryStatement, null, false);
                    QueryResult snapshot = querySnapshot(value, probe.mind());
                    result = new QueryResult(snapshot.value, snapshot.hypotheses, snapshot.hypothesisAssertions, mode);
                }
            }
            if (!state.equals(DmzObservationStateFingerprint.capture(child)) || !hypotheses.equals(hypothesisText(child)))
                throw new IllegalStateException("Candidate probe changed its branch boundary");
            return result;
        } catch (Exception failure) { close(); throw failure; }
    }
    private void validateCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            String candidate, String queryStatement, int auditBudget, int lookupBudget) {
        if (!continuationOnly || candidate == null || !candidate.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer candidates are qualified");
        if (queryStatement == null || !queryStatement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer candidate queries are qualified");
        if (auditBudget < 1 || auditBudget > 10000) throw new IllegalArgumentException("Query audit budget must be between 1 and 10000");
        if (source == null || sourceRule < 0 || source.getCommune() != null || !source.getCommuneMembers().isEmpty())
            throw new IllegalArgumentException("Exact atomic candidate source required");
        if (lookupBudget < 1 || lookupBudget > 10000)
            throw new IllegalArgumentException("Candidate lookup budget must be between 1 and 10000");
    }
    enum CandidateStatus { DECLINED_Q, BRANCHING_REQUIRED, REUSED_PRIMARY, PROBED_NEW_INPUT }
    static final class CandidateEvaluation {
        final CandidateAssessment assessment;
        final CandidateStatus status;
        final QueryResult query; // absent for declined/deferred candidates, never an unknown truth result
        private CandidateEvaluation(CandidateAssessment assessment, CandidateStatus status, QueryResult query) {
            this.assessment = assessment; this.status = status; this.query = query;
        }
    }
    /** Checked workflow; rejected/deferred candidates never reach native admission. */
    CandidateEvaluation evaluateCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            String candidate, String queryStatement, int auditBudget, int lookupBudget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        validateCandidate(source, sourceRule, candidate, queryStatement, auditBudget, lookupBudget);
        CandidateAssessment assessment = assessCandidate(candidate, auditBudget);
        if (assessment.disposition == CandidateDisposition.REJECT_Q)
            return new CandidateEvaluation(assessment, CandidateStatus.DECLINED_Q, null);
        if (assessment.disposition == CandidateDisposition.NEEDS_BRANCHING)
            return new CandidateEvaluation(assessment, CandidateStatus.BRANCHING_REQUIRED, null);
        QueryResult result;
        CandidateStatus status;
        if (assessment.disposition == CandidateDisposition.REUSE_PRIMARY) {
            QueryResult snapshot = queryContinuationResult(queryStatement, auditBudget);
            result = new QueryResult(snapshot.value, snapshot.hypotheses, snapshot.hypothesisAssertions,
                    QueryResult.CandidateMode.EXISTING_PRIMARY);
            status = CandidateStatus.REUSED_PRIMARY;
        } else {
            result = probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, lookupBudget);
            status = CandidateStatus.PROBED_NEW_INPUT;
        }
        requireAuthorityCurrent();
        return new CandidateEvaluation(assessment, status, result);
    }
    private static boolean existingPrimary(Mind mind, String statement, int budget) throws Exception {
        int open = statement.indexOf('(');
        boolean positive = !statement.startsWith("!~");
        String predicate = statement.substring(positive ? 1 : 2, open);
        java.math.BigDecimal value = new java.math.BigDecimal(statement.substring(open + 1, statement.length() - 2));
        for (IRule candidate : mind.getRules()) {
            if (budget-- == 0) throw new IllegalStateException("Candidate primary lookup budget exceeded");
            if (candidate.isDeleted(mind) || candidate.isQuery() || mind.getRules().isGenerated(candidate)) continue;
            Rule rule = (Rule) candidate;
            if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
            Domain literal = rule.getDomain();
            if (literal.isAntc() != positive || !literal.getPredicate().getName(mind).equals(predicate)
                    || literal.getArguments().size() != 1) continue;
            org.kanger.interfaces.ITerm term = literal.getArguments().get(0).getValue(mind);
            Object actual = term == null ? null : term.getValue();
            if (actual instanceof Number && value.compareTo(new java.math.BigDecimal(actual.toString())) == 0) return true;
        }
        return false;
    }
    private static QueryResult querySnapshot(Boolean value, Mind mind) throws Exception {
        List<String> assertions = new ArrayList<String>();
        for (org.kanger.interfaces.IHypothesis hypothesis : mind.getHypothesis())
            assertions.add(((org.kanger.primitives.Hypothesis) hypothesis).toAssertionString(mind));
        return new QueryResult(value, hypothesisText(mind), assertions);
    }
    private static List<String> hypothesisText(Mind mind) throws Exception {
        List<String> result = new ArrayList<String>();
        for (org.kanger.interfaces.IHypothesis hypothesis : mind.getHypothesis())
            result.add(((org.kanger.primitives.Hypothesis) hypothesis).toString(mind));
        return result;
    }
    @Override public void close() throws Exception {
        if (!closed) { restriction.close(); closed = true; transaction.close(); }
    }
}
