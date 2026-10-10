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
    static final class ProjectedGround {
        final TerminalSupportCapture.Ground ground;
        final int keepExisting, keepCandidate;
        private ProjectedGround(TerminalSupportCapture.Ground ground, int before, int after) {
            this.ground = ground; keepExisting = before; keepCandidate = after;
        }
        boolean losesLastSupport() { return keepExisting > 0 && keepCandidate == 0; }
    }
    static final class SymmetricProjection {
        final ConflictEvidence evidence;
        final List<ProjectedGround> grounds;
        // KEEP_EXISTING excludes the incoming candidate; KEEP_CANDIDATE excludes these exact opposing chains.
        final List<ConflictSupport> candidateSideCuts;
        private SymmetricProjection(ConflictEvidence evidence, List<ProjectedGround> grounds) {
            this.evidence = evidence;
            this.grounds = java.util.Collections.unmodifiableList(new ArrayList<ProjectedGround>(grounds));
            candidateSideCuts = evidence.supports;
        }
        boolean isCurrent() throws Exception { return evidence.isCurrent(); }
    }
    /** Read-only inherited-proof projection; the proposed candidate is not inserted or inferred. */
    SymmetricProjection projectSymmetric(ConflictEvidence evidence, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (evidence == null || evidence.owner != this) throw new IllegalArgumentException("Owning branch conflict evidence required");
        if (budget < 1 || budget > 10000) throw new IllegalArgumentException("Projection budget must be between 1 and 10000");
        if (!evidence.isCurrent()) throw new IllegalStateException("Current conflict evidence required");
        Mind child = transaction.mind();
        DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child, budget, true);
        if (!inventory.eligible) throw new IllegalStateException("Symmetric projection inventory unavailable: " + inventory.gaps);
        List<ProjectedGround> grounds = new ArrayList<ProjectedGround>();
        int[] work = new int[] {budget};
        for (DmzObservedProofGraph.Node node : inventory.graph.observed.nodes) {
            projectionWork(work);
            DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(node.ground, DmzTerminalRestriction.activeNoGoods(child), budget);
            if (proofs.truncated) throw new IllegalStateException("Symmetric projection witness budget exceeded");
            int kept = 0;
            for (DmzProofWitnesses.Witness witness : proofs.witnesses) {
                boolean excluded = false;
                for (ConflictSupport cut : evidence.supports)
                    if (containsExactWitness(witness, cut.witness, work)) { excluded = true; break; }
                if (!excluded) ++kept;
            }
            grounds.add(new ProjectedGround(node.ground, proofs.witnesses.size(), kept));
        }
        if (!inventory.isCurrent(child) || !evidence.isCurrent()) throw new IllegalStateException("Symmetric projection boundary changed");
        return new SymmetricProjection(evidence, grounds);
    }
    enum SupportChoice { KEEP_EXISTING, KEEP_CANDIDATE }
    static final class SupportChoiceBudgetFailure extends IllegalStateException {
        final int hiddenRules;
        private SupportChoiceBudgetFailure(String phase, int hiddenRules) {
            super("Native support choice " + phase + " budget exceeded"); this.hiddenRules = hiddenRules;
        }
    }
    /** Inspection-only child: no inference, candidate admission, commit or raw Mind access. */
    enum ConsequenceConflictKind { AUTHORITY, INTERNAL }
    /** Detached witness alternatives, not a permission to apply cuts after the probe rolls back. */
    static final class ConsequenceConflict {
        final TerminalSupportCapture.Ground ground;
        final List<ConflictSupport> supporting, opposing;
        private ConsequenceConflict(TerminalSupportCapture.Ground ground,
                List<ConflictSupport> supporting, List<ConflictSupport> opposing) {
            this.ground = ground;
            this.supporting = java.util.Collections.unmodifiableList(new ArrayList<ConflictSupport>(supporting));
            this.opposing = java.util.Collections.unmodifiableList(new ArrayList<ConflictSupport>(opposing));
        }
    }
    /** Historical result of live recognition and symmetric support counting; never a cut permission. */
    static final class ReplayProjectedGround {
        final TerminalSupportCapture.Ground ground;
        final int before, keepSupporting, keepOpposing;
        private ReplayProjectedGround(TerminalSupportCapture.Ground ground, int before, int supporting, int opposing) {
            this.ground = ground; this.before = before; keepSupporting = supporting; keepOpposing = opposing;
        }
    }
    enum ReplaySide { SUPPORTING, OPPOSING }
    static final class ReplayAppliedGround {
        final TerminalSupportCapture.Ground ground;
        final int proofs;
        final boolean visible;
        private ReplayAppliedGround(TerminalSupportCapture.Ground ground, int proofs, boolean visible) {
            this.ground = ground; this.proofs = proofs; this.visible = visible;
        }
    }
    enum ReplayQueryDisposition { NOT_REQUESTED, QUERIED, NEEDS_BRANCHING, DECLINED_BY_Q }
    static final class ReplayPathStep {
        final ConsequenceAuditFailure evidence;
        final TerminalSupportCapture.Ground conflict;
        final ReplaySide side;
        final List<ReplayAppliedGround> grounds;
        private ReplayPathStep(ConsequenceAuditFailure evidence, TerminalSupportCapture.Ground conflict, ReplayAppliedChoice result) {
            this.evidence = evidence; this.conflict = conflict; side = result.side; grounds = result.grounds;
        }
    }
    /** Detached historical choices; no Mind, retained transaction or enforcement permission. */
    static final class ReplayPath {
        private final Object replayOwner;
        final ConsequenceAuditFailure root, remaining;
        final ReplayQueryDisposition disposition;
        final List<ReplayPathStep> steps;
        private ReplayPath(Object replayOwner, ConsequenceAuditFailure root, ReplayPathStep step, ReplayAppliedChoice result) {
            this.replayOwner = replayOwner; this.root = root; remaining = result.conflict; disposition = result.disposition;
            steps = java.util.Collections.singletonList(step);
        }
        private ReplayPath(Object replayOwner, ConsequenceAuditFailure root, List<ReplayPathStep> steps, ReplayAppliedChoice result) {
            this.replayOwner = replayOwner; this.root = root; remaining = result.conflict; disposition = result.disposition;
            this.steps = java.util.Collections.unmodifiableList(new ArrayList<ReplayPathStep>(steps));
        }
    }
    static final class ReplayPathExtension {
        final List<ReplayPath> paths;
        final List<ReplayAppliedChoice> applications;
        private ReplayPathExtension(List<ReplayPath> paths, List<ReplayAppliedChoice> applications) {
            this.paths = java.util.Collections.unmodifiableList(new ArrayList<ReplayPath>(paths));
            this.applications = java.util.Collections.unmodifiableList(new ArrayList<ReplayAppliedChoice>(applications));
        }
    }
    private static final class ReplayExtensionRequest {
        final TerminalSupportCapture.Ground conflict;
        final int projectionBudget, applicationBudget;
        ReplayPathExtension result;
        ReplayExtensionRequest(TerminalSupportCapture.Ground conflict, int projectionBudget, int applicationBudget) {
            this.conflict = conflict; this.projectionBudget = projectionBudget; this.applicationBudget = applicationBudget;
        }
    }
    private static final class ReplayQueryRequest {
        ReplayExtensionRequest extension;
        final String statement;
        final int budget;
        final ReplayPath path;
        final org.kanger.interfaces.IContextResults.Revision source;
        final long sourceRule;
        final int recognitionBudget;
        ReplayQueryRequest(String statement, int budget) { this(statement, budget, null, null, -1, 0); }
        ReplayQueryRequest(String statement, int budget, ReplayPath path,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule, int recognitionBudget) {
            this.statement = statement; this.budget = budget; this.path = path;
            this.source = source; this.sourceRule = sourceRule; this.recognitionBudget = recognitionBudget;
        }
    }
    static final class ReplayAppliedChoice {
        final ReplaySide side;
        final int hiddenRules;
        final List<ReplayAppliedGround> grounds;
        final ReplayQueryDisposition disposition;
        final QueryResult query;
        final ConsequenceAuditFailure conflict;
        private ReplayAppliedChoice(ReplaySide side, int hiddenRules, List<ReplayAppliedGround> grounds) {
            this(side, hiddenRules, grounds, ReplayQueryDisposition.NOT_REQUESTED, null, null);
        }
        private ReplayAppliedChoice(ReplaySide side, int hiddenRules, List<ReplayAppliedGround> grounds,
                ReplayQueryDisposition disposition, QueryResult query, ConsequenceAuditFailure conflict) {
            this.side = side; this.hiddenRules = hiddenRules;
            this.disposition = disposition; this.query = query; this.conflict = conflict;
            this.grounds = java.util.Collections.unmodifiableList(new ArrayList<ReplayAppliedGround>(grounds));
        }
    }
    static final class ReplaySymmetricProjection {
        final ConsequenceAuditFailure evidence;
        final int conflictIndex;
        final List<ReplayProjectedGround> grounds;
        final List<ReplayAppliedChoice> applications;
        private ReplaySymmetricProjection(ConsequenceAuditFailure evidence, int index, List<ReplayProjectedGround> grounds) {
            this(evidence, index, grounds, java.util.Collections.<ReplayAppliedChoice>emptyList());
        }
        private ReplaySymmetricProjection(ConsequenceAuditFailure evidence, int index, List<ReplayProjectedGround> grounds,
                List<ReplayAppliedChoice> applications) {
            this.evidence = evidence; conflictIndex = index;
            this.applications = java.util.Collections.unmodifiableList(new ArrayList<ReplayAppliedChoice>(applications));
            this.grounds = java.util.Collections.unmodifiableList(new ArrayList<ReplayProjectedGround>(grounds));
        }
    }
    static final class ConsequenceAuditFailure extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final Object replayOwner;
        final ConsequenceConflictKind kind;
        final List<ConsequenceConflict> conflicts;
        private ConsequenceAuditFailure(Object replayOwner, ConsequenceConflictKind kind, List<ConsequenceConflict> conflicts) {
            super(kind == ConsequenceConflictKind.AUTHORITY ? "Candidate consequences conflict with pinned Q"
                    : "Candidate consequences require further branching");
            this.replayOwner = replayOwner;
            this.kind = kind;
            this.conflicts = java.util.Collections.unmodifiableList(new ArrayList<ConsequenceConflict>(conflicts));
        }
    }
    private static List<ConflictSupport> snapshotConflictSupports(Mind target,
            List<DmzProofWitnesses.Witness> witnesses, int[] sourceBudget) throws Exception {
        List<ConflictSupport> result = new ArrayList<ConflictSupport>();
        for (DmzProofWitnesses.Witness witness : witnesses) {
            List<DmzReplayProvenance.Binding> accepted = new ArrayList<DmzReplayProvenance.Binding>();
            List<DmzReplayProvenance.Binding> pending = new ArrayList<DmzReplayProvenance.Binding>();
            collectConflictSources(target, witness, accepted, pending,
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<DmzReplayProvenance.Binding, Boolean>()), sourceBudget);
            result.add(new ConflictSupport(witness, accepted, pending));
        }
        return result;
    }
    static final class NativeSupportChoice implements AutoCloseable {
        final SupportChoice choice;
        final SymmetricProjection projection;
        final int hiddenRules;
        private final DmzStoredRetractionTransaction owner;
        private final TechnicalMindTransaction transaction;
        private final int budget;
        private final Object replayOwner = new Object();
        private boolean closed;
        private NativeSupportChoice(DmzStoredRetractionTransaction owner, SymmetricProjection projection,
                SupportChoice choice, TechnicalMindTransaction transaction, int budget, int hiddenRules) {
            this.owner = owner; this.projection = projection; this.choice = choice;
            this.transaction = transaction; this.budget = budget; this.hiddenRules = hiddenRules;
        }
        boolean hasLiveGround(TerminalSupportCapture.Ground expected) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (!projection.isCurrent()) throw new IllegalStateException("Native support choice boundary changed");
            int remaining = budget;
            Mind child = transaction.mind();
            for (IRule item : child.getRules()) {
                if (remaining-- == 0) throw new IllegalStateException("Native support choice inspection budget exceeded");
                if (item.isDeleted(child) || item.isQuery()) continue;
                Rule rule = (Rule) item;
                if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
                Domain literal = rule.getTree().get(0).get(0);
                TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                        literal.isAntc(), literal.getArguments().convertBase(child)), child);
                if (expected.equivalent(ground)) return true;
            }
            return false;
        }
        /** Detached current proofs with exact pending-source cuts; does not authorize native inference. */
        DmzCurrentUnaryProofInventory.Proofs proofs(TerminalSupportCapture.Ground ground, int proofBudget) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (ground == null || proofBudget < 1 || proofBudget > 10000)
                throw new IllegalArgumentException("Ground and bounded proof budget required");
            if (!projection.isCurrent()) throw new IllegalStateException("Native support choice boundary changed");
            Mind child = transaction.mind();
            DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child, proofBudget, true);
            if (!inventory.eligible) throw new IllegalStateException("Native support choice current inventory unavailable");
            DmzCurrentUnaryProofInventory.Proofs raw = inventory.proofs(ground,
                    DmzTerminalRestriction.activeNoGoods(child), proofBudget);
            if (raw.truncated) throw new IllegalStateException("Native support choice proof budget exceeded");
            List<DmzProofWitnesses.Witness> kept = new ArrayList<DmzProofWitnesses.Witness>();
            int[] work = {proofBudget};
            for (DmzProofWitnesses.Witness witness : raw.witnesses) {
                boolean excluded = false;
                if (choice == SupportChoice.KEEP_CANDIDATE)
                    for (ConflictSupport cut : projection.candidateSideCuts)
                        if (containsExactWitness(witness, cut.witness, work)) { excluded = true; break; }
                if (!excluded) kept.add(witness);
            }
            if (!inventory.isCurrent(child) || !projection.isCurrent())
                throw new IllegalStateException("Native support choice boundary changed");
            return new DmzCurrentUnaryProofInventory.Proofs(kept, false, raw.provisional);
        }
        /** Expand primary cuts through the pinned parent's complete eligible unary closure before query assumptions. */
        private List<DmzProofWitnesses.Witness> queryCuts(int auditBudget) throws Exception {
            List<DmzProofWitnesses.Witness> result = new ArrayList<DmzProofWitnesses.Witness>();
            if (choice != SupportChoice.KEEP_CANDIDATE) return result;
            boolean primary = false;
            for (ConflictSupport cut : projection.candidateSideCuts)
                if (cut.witness.step < 0) primary = true;
                else result.add(cut.witness);
            if (!primary) return result;
            Mind parent = owner.transaction.mind();
            DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(parent, auditBudget, true);
            if (!inventory.eligible) throw new IllegalStateException("Primary cut query inventory unavailable");
            int[] work = {auditBudget};
            for (DmzObservedProofGraph.Node node : inventory.graph.observed.nodes) {
                projectionWork(work);
                DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(node.ground,
                        DmzTerminalRestriction.activeNoGoods(parent), auditBudget);
                if (proofs.truncated) throw new IllegalStateException("Primary cut query proof budget exceeded");
                for (DmzProofWitnesses.Witness witness : proofs.witnesses) {
                    projectionWork(work);
                    if (witness.step < 0) continue;
                    for (ConflictSupport cut : projection.candidateSideCuts)
                        if (cut.witness.step < 0 && containsExactWitness(witness, cut.witness, work)) {
                            result.add(witness); break;
                        }
                }
            }
            if (!inventory.isCurrent(parent) || !projection.isCurrent())
                throw new IllegalStateException("Native support choice boundary changed");
            return result;
        }
        /** Rollback-only signed unary query with pre-assumption derived gates for exact primary or derived cuts. */
        QueryResult query(String statement, int auditBudget) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (statement == null || !statement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
                throw new IllegalArgumentException("Only signed unary integer choice queries are qualified");
            if (auditBudget < 1 || auditBudget > 10000) throw new IllegalArgumentException("Bounded query audit required");
            if (!projection.isCurrent()) throw new IllegalStateException("Native support choice boundary changed");
            List<DmzProofWitnesses.Witness> cuts = queryCuts(auditBudget);
            Mind child = transaction.mind();
            String state = DmzObservationStateFingerprint.capture(child);
            List<String> hypotheses = hypothesisText(child);
            List<DmzTerminalRestriction> gates = new ArrayList<DmzTerminalRestriction>();
            try {
                for (DmzProofWitnesses.Witness cut : cuts)
                    gates.add(DmzTerminalRestriction.beginPendingChoice(child, cut));
                QueryResult result;
                try (DmzTerminalRestriction.QueryScope audit = DmzTerminalRestriction.prepareQuery(child, auditBudget);
                        TechnicalMindTransaction query = TechnicalMindTransaction.beginIsolated(child)) {
                    result = querySnapshot(query.mind().query(statement, null, false), query.mind());
                }
                if (!state.equals(DmzObservationStateFingerprint.capture(child)) || !hypotheses.equals(hypothesisText(child))
                        || !projection.isCurrent()) throw new IllegalStateException("Native support choice query boundary changed");
                return result;
            } finally {
                for (int i = gates.size() - 1; i >= 0; --i) gates.get(i).close();
                if (!state.equals(DmzObservationStateFingerprint.capture(child)) || !hypotheses.equals(hypothesisText(child))
                        || !projection.isCurrent()) {
                    close();
                    throw new IllegalStateException("Native support choice query boundary changed");
                }
            }
        }
        /** Audit every supported current ground, including inherited inputs; Q keeps its own settled proofs. */
        private void auditConsequences(Mind probe, int consequenceBudget) throws Exception {
            owner.requireAuthorityCurrent();
            DmzCurrentUnaryProofInventory current = DmzCurrentUnaryProofInventory.capture(probe, consequenceBudget, true);
            DmzCurrentUnaryProofInventory authority = DmzCurrentUnaryProofInventory.capture(owner.authorityTarget, consequenceBudget);
            if (!current.eligible || !authority.eligible)
                throw new IllegalStateException("Candidate consequence inventory unavailable");
            List<DmzProofWitnesses.NoGood> exclusions = DmzTerminalRestriction.activeNoGoods(probe);
            int[] work = {consequenceBudget};
            int[] sourceBudget = {consequenceBudget};
            List<ConsequenceConflict> internal = new ArrayList<ConsequenceConflict>();
            for (DmzObservedProofGraph.Node node : current.graph.observed.nodes) {
                projectionWork(work);
                DmzCurrentUnaryProofInventory.Proofs supported = current.proofs(node.ground, exclusions, consequenceBudget);
                if (supported.truncated) throw new IllegalStateException("Candidate consequence proof budget exceeded");
                if (supported.witnesses.isEmpty()) continue;
                for (DmzProofWitnesses.Witness witness : supported.witnesses) projectionWork(work);
                TerminalSupportCapture.Ground opposite = new TerminalSupportCapture.Ground(node.ground.predicate,
                        !node.ground.sign, node.ground.arguments);
                DmzCurrentUnaryProofInventory.Proofs local = current.proofs(opposite, exclusions, consequenceBudget);
                DmzCurrentUnaryProofInventory.Proofs q = authority.proofs(opposite,
                        java.util.Collections.<DmzProofWitnesses.NoGood>emptyList(), consequenceBudget);
                if (local.truncated || q.truncated) throw new IllegalStateException("Candidate consequence proof budget exceeded");
                if (!q.witnesses.isEmpty()) {
                    ConsequenceConflict conflict = new ConsequenceConflict(node.ground,
                            snapshotConflictSupports(probe, supported.witnesses, sourceBudget),
                            snapshotConflictSupports(owner.authorityTarget, q.witnesses, sourceBudget));
                    if (!current.isCurrent(probe) || !authority.isCurrent(owner.authorityTarget) || !projection.isCurrent())
                        throw new IllegalStateException("Candidate consequence boundary changed");
                    owner.requireAuthorityCurrent();
                    throw new ConsequenceAuditFailure(replayOwner, ConsequenceConflictKind.AUTHORITY,
                            java.util.Collections.singletonList(conflict));
                }
                if (!local.witnesses.isEmpty() && node.ground.sign)
                    internal.add(new ConsequenceConflict(node.ground,
                            snapshotConflictSupports(probe, supported.witnesses, sourceBudget),
                            snapshotConflictSupports(probe, local.witnesses, sourceBudget)));
            }
            if (!current.isCurrent(probe) || !authority.isCurrent(owner.authorityTarget) || !projection.isCurrent())
                throw new IllegalStateException("Candidate consequence boundary changed");
            owner.requireAuthorityCurrent();
            if (!internal.isEmpty()) throw new ConsequenceAuditFailure(replayOwner, ConsequenceConflictKind.INTERNAL, internal);
        }
        /** Exact receipt candidate only; native input, consequences and query always roll back. */
        QueryResult probeCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget) throws Exception {
            return probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, auditBudget);
        }
        QueryResult probeCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int consequenceBudget) throws Exception {
            return probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, consequenceBudget, null, 10000, -1, 10000, null, 0, null);
        }
        /** Recognize historical chains while the replay is live; always rolls back and never applies cuts. */
        ConsequenceAuditFailure replayConflict(ConsequenceAuditFailure prior,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget) throws Exception {
            if (prior == null || prior.replayOwner != replayOwner || prior.kind != ConsequenceConflictKind.INTERNAL)
                throw new IllegalArgumentException("Owned internal conflict evidence required");
            if (recognitionBudget < 1 || recognitionBudget > 10000)
                throw new IllegalArgumentException("Bounded replay recognition required");
            candidateBinding(prior, source, sourceRule, new int[] { recognitionBudget });
            try { probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, auditBudget, prior, recognitionBudget, -1, 10000, null, 0, null); }
            catch (ConsequenceAuditFailure recognized) { return recognized; }
            throw new IllegalStateException("Historical conflict disappeared during replay");
        }
        ReplaySymmetricProjection projectReplayConflict(ConsequenceAuditFailure prior, int conflictIndex,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget, int projectionBudget) throws Exception {
            return projectReplayConflict(prior, conflictIndex, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, 0);
        }
        ReplaySymmetricProjection inspectReplayChoices(ConsequenceAuditFailure prior, int conflictIndex,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget) throws Exception {
            if (applicationBudget < 1 || applicationBudget > 10000)
                throw new IllegalArgumentException("Bounded replay choice application required");
            return projectReplayConflict(prior, conflictIndex, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, applicationBudget);
        }
        ReplayPathExtension extendReplayPath(ReplayPath path, int nextConflict,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget, int queryBudget, int nextApplicationBudget) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (path == null || path.replayOwner != replayOwner || path.steps.size() != 1
                    || path.disposition != ReplayQueryDisposition.NEEDS_BRANCHING || path.remaining == null
                    || nextConflict < 0 || nextConflict >= path.remaining.conflicts.size())
                throw new IllegalArgumentException("Owned deferred one-choice path and next conflict required");
            if (applicationBudget < 1 || applicationBudget > 10000 || queryBudget < 1 || queryBudget > 10000
                    || nextApplicationBudget < 1 || nextApplicationBudget > 10000)
                throw new IllegalArgumentException("Bounded nested replay work required");
            if (!projection.isCurrent()) throw new IllegalStateException("Replay path boundary changed");
            int rootIndex = -1;
            int[] work = { recognitionBudget };
            for (int i = 0; i < path.root.conflicts.size(); ++i) {
                projectionWork(work);
                if (path.steps.get(0).conflict.equivalent(path.root.conflicts.get(i).ground)) rootIndex = i;
            }
            if (rootIndex < 0) throw new IllegalStateException("Replay path root conflict missing");
            ReplayQueryRequest request = new ReplayQueryRequest(queryStatement, queryBudget, path, source, sourceRule, recognitionBudget);
            request.extension = new ReplayExtensionRequest(path.remaining.conflicts.get(nextConflict).ground,
                    projectionBudget, nextApplicationBudget);
            projectReplayConflict(path.root, rootIndex, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, applicationBudget, request);
            if (request.extension.result == null) throw new IllegalStateException("Complete nested replay required");
            return request.extension.result;
        }
        private ReplaySymmetricProjection projectReplayConflict(ConsequenceAuditFailure prior, int conflictIndex,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget) throws Exception {
            return projectReplayConflict(prior, conflictIndex, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, applicationBudget, null);
        }
        ReplaySymmetricProjection queryReplayChoices(ConsequenceAuditFailure prior, int conflictIndex,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget, int queryBudget) throws Exception {
            if (applicationBudget < 1 || applicationBudget > 10000 || queryBudget < 1 || queryBudget > 10000)
                throw new IllegalArgumentException("Bounded replay application and query required");
            return projectReplayConflict(prior, conflictIndex, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, applicationBudget,
                    new ReplayQueryRequest(queryStatement, queryBudget));
        }
        ReplayPath recordReplayPath(ReplaySymmetricProjection result, ReplaySide side) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (result == null || result.evidence.replayOwner != replayOwner || side == null)
                throw new IllegalArgumentException("Owned replay result and explicit side required");
            if (!projection.isCurrent()) throw new IllegalStateException("Replay path boundary changed");
            for (ReplayAppliedChoice application : result.applications) if (application.side == side) {
                if (application.disposition != ReplayQueryDisposition.QUERIED
                        && application.disposition != ReplayQueryDisposition.NEEDS_BRANCHING)
                    throw new IllegalArgumentException("Queried or internally deferred replay choice required");
                return new ReplayPath(replayOwner, result.evidence,
                        new ReplayPathStep(result.evidence, result.evidence.conflicts.get(result.conflictIndex).ground, application), application);
            }
            throw new IllegalArgumentException("Applied replay side required");
        }
        ReplaySymmetricProjection replayPath(ReplayPath path,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget, int queryBudget) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (path == null || path.replayOwner != replayOwner || path.steps.size() != 1)
                throw new IllegalArgumentException("Owned one-choice replay path required");
            if (applicationBudget < 1 || applicationBudget > 10000 || queryBudget < 1 || queryBudget > 10000
                    || recognitionBudget < 1 || recognitionBudget > 10000)
                throw new IllegalArgumentException("Bounded replay path work required");
            if (!projection.isCurrent()) throw new IllegalStateException("Replay path boundary changed");
            int index = -1;
            int[] work = { recognitionBudget };
            for (int i = 0; i < path.root.conflicts.size(); ++i) {
                projectionWork(work);
                if (path.steps.get(0).conflict.equivalent(path.root.conflicts.get(i).ground)) index = i;
            }
            if (index < 0) throw new IllegalStateException("Replay path root conflict missing");
            return projectReplayConflict(path.root, index, source, sourceRule, candidate, queryStatement,
                    auditBudget, recognitionBudget, projectionBudget, applicationBudget,
                    new ReplayQueryRequest(queryStatement, queryBudget, path, source, sourceRule, recognitionBudget));
        }
        private ReplaySymmetricProjection projectReplayConflict(ConsequenceAuditFailure prior, int conflictIndex,
                org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int recognitionBudget,
                int projectionBudget, int applicationBudget, ReplayQueryRequest query) throws Exception {
            if (prior == null || prior.replayOwner != replayOwner || prior.kind != ConsequenceConflictKind.INTERNAL
                    || conflictIndex < 0 || conflictIndex >= prior.conflicts.size())
                throw new IllegalArgumentException("Owned internal conflict selection required");
            if (recognitionBudget < 1 || recognitionBudget > 10000 || projectionBudget < 1 || projectionBudget > 10000)
                throw new IllegalArgumentException("Bounded replay recognition and projection required");
            candidateBinding(prior, source, sourceRule, new int[] { recognitionBudget });
            ReplaySymmetricProjection[] result = new ReplaySymmetricProjection[1];
            try { probeCandidate(source, sourceRule, candidate, queryStatement, auditBudget, auditBudget,
                    prior, recognitionBudget, conflictIndex, projectionBudget, result, applicationBudget, query); }
            catch (ConsequenceAuditFailure recognized) {
                if (result[0] == null || result[0].evidence != recognized)
                    throw new IllegalStateException("Complete live replay projection required");
                return result[0];
            }
            throw new IllegalStateException("Historical conflict disappeared during projection replay");
        }
        private QueryResult probeCandidate(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
                String candidate, String queryStatement, int auditBudget, int consequenceBudget,
                ConsequenceAuditFailure prior, int recognitionBudget, int conflictIndex, int projectionBudget,
                ReplaySymmetricProjection[] projectionResult, int applicationBudget, ReplayQueryRequest query) throws Exception {
            if (closed) throw new IllegalStateException("Native support choice closed");
            if (consequenceBudget < 1 || consequenceBudget > 10000)
                throw new IllegalArgumentException("Bounded consequence audit required");
            owner.validateCandidate(source, sourceRule, candidate, queryStatement, auditBudget, auditBudget);
            if (!projection.isCurrent()) throw new IllegalStateException("Native support choice boundary changed");
            if (choice != SupportChoice.KEEP_CANDIDATE)
                throw new IllegalStateException("Candidate admission requires candidate support choice");
            int open = candidate.indexOf('(');
            boolean sign = !candidate.startsWith("!~");
            String predicate = candidate.substring(sign ? 1 : 2, open);
            Object expected = projection.evidence.candidate.arguments.get(0).materialize().getValue();
            if (sign != projection.evidence.candidate.sign || !predicate.equals(projection.evidence.candidate.predicate)
                    || !(expected instanceof Number) || new java.math.BigDecimal(candidate.substring(open + 1, candidate.length() - 2))
                            .compareTo(new java.math.BigDecimal(expected.toString())) != 0)
                throw new IllegalArgumentException("Exact conflict receipt candidate required");
            InputKind authority = owner.classifyAuthorityInput(candidate, auditBudget);
            if (authority == InputKind.CONFLICT || authority == InputKind.INCONSISTENT)
                throw new IllegalStateException("Candidate conflicts with pinned Q");
            List<DmzProofWitnesses.Witness> cuts = queryCuts(auditBudget);
            Mind child = transaction.mind();
            String state = DmzObservationStateFingerprint.capture(child);
            DmzReplayProvenance.SourceCheckpoint sources = DmzReplayProvenance.sourceCheckpoint(child);
            List<String> hypotheses = hypothesisText(child);
            List<DmzTerminalRestriction> gates = new ArrayList<DmzTerminalRestriction>();
            try {
                for (DmzProofWitnesses.Witness cut : cuts) gates.add(DmzTerminalRestriction.beginPendingAdmission(child, cut));
                QueryResult result;
                try (DmzTerminalRestriction.QueryScope audit = DmzTerminalRestriction.prepareQuery(child, auditBudget);
                        TechnicalMindTransaction probe = TechnicalMindTransaction.beginIsolated(child)) {
                    InputKind input = classifyCurrent(probe.mind(), candidate, auditBudget, true,
                            DmzTerminalRestriction.activeNoGoods(probe.mind()));
                    if (input == InputKind.CONFLICT || input == InputKind.INCONSISTENT)
                        throw new IllegalStateException("Candidate retains an unexcluded branch conflict");
                    if (input != InputKind.FRESH) throw new IllegalStateException("Fresh receipt candidate required");
                    if (!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(probe.mind(), source, sourceRule,
                            DmzReplayProvenance.Authority.EXTERNAL, candidate)))
                        throw new IllegalStateException("Support choice candidate was not accepted: " + candidate);
                    DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(probe.mind(), auditBudget, true);
                    if (!inventory.eligible) throw new IllegalStateException("Candidate continuation inventory unavailable");
                    if (classifyCurrent(probe.mind(), candidate, auditBudget, true,
                            DmzTerminalRestriction.activeNoGoods(probe.mind())) != InputKind.EXISTING_PRIMARY)
                        throw new IllegalStateException("Candidate lacks an unconflicted current primary support");
                    try { auditConsequences(probe.mind(), consequenceBudget); }
                    catch (ConsequenceAuditFailure fresh) {
                        if (prior != null) recognizeReplay(prior, fresh, source, sourceRule, recognitionBudget);
                        if (projectionResult != null) {
                            TerminalSupportCapture.Ground selected = prior.conflicts.get(conflictIndex).ground;
                            int liveIndex = -1;
                            for (int i = 0; i < fresh.conflicts.size(); ++i)
                                if (selected.equivalent(fresh.conflicts.get(i).ground)) { liveIndex = i; break; }
                            if (liveIndex < 0) throw new IllegalStateException("Selected live replay conflict missing");
                            ReplaySymmetricProjection liveProjection = projectLiveConflict(probe.mind(), fresh, liveIndex, projectionBudget);
                            if (applicationBudget > 0) {
                                List<ReplayAppliedChoice> applications = new ArrayList<ReplayAppliedChoice>();
                                if (query != null && query.path != null) applications.add(applyReplayChoice(probe.mind(), liveProjection,
                                        query.path.steps.get(0).side, applicationBudget, projectionBudget, query));
                                else {
                                    applications.add(applyReplayChoice(probe.mind(), liveProjection, ReplaySide.SUPPORTING, applicationBudget, projectionBudget, query));
                                    applications.add(applyReplayChoice(probe.mind(), liveProjection, ReplaySide.OPPOSING, applicationBudget, projectionBudget, query));
                                }
                                liveProjection = new ReplaySymmetricProjection(fresh, liveIndex, liveProjection.grounds, applications);
                            }
                            projectionResult[0] = liveProjection;
                        }
                        throw fresh;
                    }
                    QueryResult snapshot = querySnapshot(probe.mind().query(queryStatement, null, false), probe.mind());
                    result = new QueryResult(snapshot.value, snapshot.hypotheses, snapshot.hypothesisAssertions,
                            QueryResult.CandidateMode.NEW_INPUT);
                }
                return result;
            } finally {
                for (int i = gates.size() - 1; i >= 0; --i) gates.get(i).close();
                if (!state.equals(DmzObservationStateFingerprint.capture(child)) || !hypotheses.equals(hypothesisText(child))
                        || !sources.isCurrent(child) || !projection.isCurrent()) {
                    close(); throw new IllegalStateException("Native support choice candidate boundary changed");
                }
            }
        }
        /** Scoped native masks and exact pending gates; inspection only, no query/input/commit. */
        private ReplayAppliedChoice applyReplayChoice(Mind parent, ReplaySymmetricProjection projected,
                ReplaySide side, int limit, int inspectionLimit, ReplayQueryRequest request) throws Exception {
            String state = DmzObservationStateFingerprint.capture(parent);
            List<String> hypotheses = hypothesisText(parent);
            DmzReplayProvenance.SourceCheckpoint sources = DmzReplayProvenance.sourceCheckpoint(parent);
            List<DmzTerminalRestriction> gates = new ArrayList<DmzTerminalRestriction>();
            try (TechnicalMindTransaction child = TechnicalMindTransaction.beginIsolated(parent)) {
                int[] work = { limit };
                int hidden = 0;
                ConsequenceConflict conflict = projected.evidence.conflicts.get(projected.conflictIndex);
                List<ConflictSupport> cuts = side == ReplaySide.SUPPORTING ? conflict.opposing : conflict.supporting;
                for (ConflictSupport cut : cuts) if (cut.witness.step >= 0)
                    gates.add(DmzTerminalRestriction.beginPendingChoice(child.mind(), cut.witness));
                for (ReplayProjectedGround row : projected.grounds) {
                    int kept = side == ReplaySide.SUPPORTING ? row.keepSupporting : row.keepOpposing;
                    if (row.before == 0 || kept != 0) continue;
                    for (IRule item : child.mind().getRules()) {
                        if (work[0]-- == 0) throw new SupportChoiceBudgetFailure("replay application", hidden);
                        if (item.isDeleted(child.mind()) || item.isQuery()) continue;
                        Rule rule = (Rule) item;
                        if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
                        Domain literal = rule.getTree().get(0).get(0);
                        TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                                literal.isAntc(), literal.getArguments().convertBase(child.mind())), child.mind());
                        if (!row.ground.equivalent(ground)) continue;
                        boolean pending = false;
                        for (DmzReplayProvenance.SourceObservation observation : DmzReplayProvenance.observedSources(parent, item.getId())) {
                            if (work[0]-- == 0) throw new SupportChoiceBudgetFailure("replay source", hidden);
                            if (observation.outcome == DmzReplayProvenance.Outcome.ACCEPTED)
                                throw new IllegalStateException("Accepted Q occurrence cannot be masked in replay choice");
                            if (observation.outcome == DmzReplayProvenance.Outcome.PENDING) pending = true;
                        }
                        if (!parent.getRules().isGenerated(item) && !pending)
                            throw new IllegalStateException("Replay choice requires pending primary or generated cache");
                        rule.setDeleted(true, child.mind()); ++hidden;
                    }
                }
                work = new int[] { inspectionLimit };
                DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(child.mind(), inspectionLimit, true);
                if (!inventory.eligible) throw new IllegalStateException("Replay child inventory unavailable");
                List<ReplayAppliedGround> result = new ArrayList<ReplayAppliedGround>();
                for (ReplayProjectedGround row : projected.grounds) {
                    projectionWork(work);
                    DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(row.ground,
                            DmzTerminalRestriction.activeNoGoods(child.mind()), inspectionLimit);
                    if (proofs.truncated) throw new IllegalStateException("Replay child witness enumeration incomplete");
                    int kept = 0;
                    for (DmzProofWitnesses.Witness witness : proofs.witnesses) {
                        boolean excluded = false;
                        for (ConflictSupport cut : cuts)
                            if (containsExactWitness(witness, cut.witness, work)) { excluded = true; break; }
                        if (!excluded) ++kept;
                    }
                    if (kept != (side == ReplaySide.SUPPORTING ? row.keepSupporting : row.keepOpposing))
                        throw new IllegalStateException("Replay child support differs from live projection");
                    boolean visible = false;
                    for (IRule item : child.mind().getRules()) {
                        projectionWork(work);
                        if (item.isDeleted(child.mind()) || item.isQuery()) continue;
                        Rule rule = (Rule) item;
                        if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
                        Domain literal = rule.getTree().get(0).get(0);
                        TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                                literal.isAntc(), literal.getArguments().convertBase(child.mind())), child.mind());
                        if (row.ground.equivalent(ground)) { visible = true; break; }
                    }
                    if (row.before > 0 && kept == 0 && visible)
                        throw new IllegalStateException("Replay child retained a last-support visibility loss");
                    result.add(new ReplayAppliedGround(row.ground, kept, visible));
                }
                if (!inventory.isCurrent(child.mind()) || !projection.isCurrent())
                    throw new IllegalStateException("Replay child inspection boundary changed");
                if (request == null) return new ReplayAppliedChoice(side, hidden, result);
                ConsequenceAuditFailure remaining = null;
                try { auditConsequences(child.mind(), request.budget); }
                catch (ConsequenceAuditFailure conflictEvidence) { remaining = conflictEvidence; }
                if (request.path != null) recognizePathTerminal(request, result, remaining);
                if (request.extension != null) {
                    if (remaining == null || remaining.kind != ConsequenceConflictKind.INTERNAL)
                        throw new IllegalStateException("Internal live conflict required for extension");
                    int nextIndex = -1;
                    int[] nextWork = { request.recognitionBudget };
                    for (int i = 0; i < remaining.conflicts.size(); ++i) {
                        projectionWork(nextWork);
                        if (request.extension.conflict.equivalent(remaining.conflicts.get(i).ground)) nextIndex = i;
                    }
                    if (nextIndex < 0) throw new IllegalStateException("Nested replay conflict missing");
                    ReplaySymmetricProjection next = projectLiveConflict(child.mind(), remaining, nextIndex,
                            request.extension.projectionBudget);
                    ReplayAppliedChoice prefix = new ReplayAppliedChoice(side, hidden, result,
                            ReplayQueryDisposition.NEEDS_BRANCHING, null, remaining);
                    List<ReplayAppliedChoice> applications = new ArrayList<ReplayAppliedChoice>();
                    List<ReplayPath> paths = new ArrayList<ReplayPath>();
                    for (ReplaySide nextSide : ReplaySide.values()) {
                        ReplayAppliedChoice applied = applyReplayChoice(child.mind(), next, nextSide,
                                request.extension.applicationBudget, request.extension.projectionBudget,
                                new ReplayQueryRequest(request.statement, request.budget));
                        applications.add(applied);
                        List<ReplayPathStep> steps = new ArrayList<ReplayPathStep>();
                        steps.add(new ReplayPathStep(projected.evidence, conflict.ground, prefix));
                        steps.add(new ReplayPathStep(remaining, remaining.conflicts.get(nextIndex).ground, applied));
                        paths.add(new ReplayPath(replayOwner, projected.evidence, steps, applied));
                    }
                    request.extension.result = new ReplayPathExtension(paths, applications);
                }
                if (remaining != null) return new ReplayAppliedChoice(side, hidden, result,
                        remaining.kind == ConsequenceConflictKind.AUTHORITY ? ReplayQueryDisposition.DECLINED_BY_Q
                                : ReplayQueryDisposition.NEEDS_BRANCHING, null, remaining);
                Mind branch = child.mind();
                String queryState = DmzObservationStateFingerprint.capture(branch);
                List<String> queryHypotheses = hypothesisText(branch);
                DmzReplayProvenance.SourceCheckpoint querySources = DmzReplayProvenance.sourceCheckpoint(branch);
                QueryResult snapshot;
                try (DmzTerminalRestriction.QueryScope audit = DmzTerminalRestriction.prepareChildQuery(branch, request.budget);
                        TechnicalMindTransaction queryProbe = TechnicalMindTransaction.beginIsolated(branch)) {
                    snapshot = querySnapshot(queryProbe.mind().query(request.statement, null, false), queryProbe.mind());
                } finally {
                    if (!queryState.equals(DmzObservationStateFingerprint.capture(branch))
                            || !queryHypotheses.equals(hypothesisText(branch)) || !querySources.isCurrent(branch)
                            || !inventory.isCurrent(branch) || !projection.isCurrent())
                        throw new IllegalStateException("Replay child query rollback changed boundary");
                    owner.requireAuthorityCurrent();
                }
                return new ReplayAppliedChoice(side, hidden, result, ReplayQueryDisposition.QUERIED, snapshot, null);
            } finally {
                for (int i = gates.size() - 1; i >= 0; --i) gates.get(i).close();
                if (!state.equals(DmzObservationStateFingerprint.capture(parent)) || !hypotheses.equals(hypothesisText(parent))
                        || !sources.isCurrent(parent) || !projection.isCurrent())
                    throw new IllegalStateException("Replay child rollback changed boundary");
                owner.requireAuthorityCurrent();
            }
        }
        private void recognizePathTerminal(ReplayQueryRequest request, List<ReplayAppliedGround> live,
                ConsequenceAuditFailure remaining) throws Exception {
            ReplayPath path = request.path;
            List<ReplayAppliedGround> historical = path.steps.get(0).grounds;
            if (historical.size() != live.size()) throw new IllegalStateException("Replay path ground coverage changed");
            int[] work = { request.recognitionBudget };
            boolean[] used = new boolean[live.size()];
            for (ReplayAppliedGround old : historical) {
                boolean found = false;
                for (int i = 0; i < live.size(); ++i) {
                    projectionWork(work);
                    ReplayAppliedGround current = live.get(i);
                    if (!used[i] && old.ground.equivalent(current.ground)) {
                        if (old.proofs != current.proofs || old.visible != current.visible)
                            throw new IllegalStateException("Replay path child support or visibility changed");
                        used[i] = true; found = true; break;
                    }
                }
                if (!found) throw new IllegalStateException("Replay path child ground missing");
            }
            if (path.disposition == ReplayQueryDisposition.NEEDS_BRANCHING) {
                if (remaining == null) throw new IllegalStateException("Replay path remaining conflict disappeared");
                recognizeReplay(path.remaining, remaining, request.source, request.sourceRule, request.recognitionBudget);
            } else if (remaining != null) throw new IllegalStateException("Replay path gained a remaining conflict");
        }
        private ReplaySymmetricProjection projectLiveConflict(Mind probe, ConsequenceAuditFailure evidence,
                int index, int limit) throws Exception {
            DmzCurrentUnaryProofInventory inventory = DmzCurrentUnaryProofInventory.capture(probe, limit, true);
            if (!inventory.eligible) throw new IllegalStateException("Live replay projection inventory unavailable");
            ConsequenceConflict conflict = evidence.conflicts.get(index);
            List<ReplayProjectedGround> grounds = new ArrayList<ReplayProjectedGround>();
            List<DmzProofWitnesses.NoGood> exclusions = DmzTerminalRestriction.activeNoGoods(probe);
            int[] work = { limit };
            for (DmzObservedProofGraph.Node node : inventory.graph.observed.nodes) {
                projectionWork(work);
                DmzCurrentUnaryProofInventory.Proofs proofs = inventory.proofs(node.ground, exclusions, limit);
                if (proofs.truncated) throw new IllegalStateException("Live replay projection witness budget exceeded");
                int supporting = 0, opposing = 0;
                for (DmzProofWitnesses.Witness witness : proofs.witnesses) {
                    projectionWork(work);
                    boolean losesSupporting = false, losesOpposing = false;
                    for (ConflictSupport cut : conflict.supporting)
                        if (containsExactWitness(witness, cut.witness, work)) { losesSupporting = true; break; }
                    for (ConflictSupport cut : conflict.opposing)
                        if (containsExactWitness(witness, cut.witness, work)) { losesOpposing = true; break; }
                    if (!losesOpposing) ++supporting;
                    if (!losesSupporting) ++opposing;
                }
                grounds.add(new ReplayProjectedGround(node.ground, proofs.witnesses.size(), supporting, opposing));
            }
            if (!inventory.isCurrent(probe) || !projection.isCurrent())
                throw new IllegalStateException("Live replay projection boundary changed");
            owner.requireAuthorityCurrent();
            return new ReplaySymmetricProjection(evidence, index, grounds);
        }
        private DmzReplayProvenance.Binding candidateBinding(ConsequenceAuditFailure packet,
                org.kanger.interfaces.IContextResults.Revision source, long label, int[] work) {
            if (source == null) throw new IllegalArgumentException("Replay source required");
            java.util.Set<DmzReplayProvenance.Binding> found = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<DmzReplayProvenance.Binding, Boolean>());
            for (ConsequenceConflict conflict : packet.conflicts) {
                for (ConflictSupport support : conflict.supporting) findCandidate(support.witness, support.pending, source, label, found, work);
                for (ConflictSupport support : conflict.opposing) findCandidate(support.witness, support.pending, source, label, found, work);
            }
            if (found.size() != 1) throw new IllegalArgumentException("Unique exact pending replay candidate source required");
            return found.iterator().next();
        }
        private void findCandidate(DmzProofWitnesses.Witness witness, List<DmzReplayProvenance.Binding> pending,
                org.kanger.interfaces.IContextResults.Revision source, long label,
                java.util.Set<DmzReplayProvenance.Binding> found, int[] work) {
            projectionWork(work);
            DmzReplayProvenance.Binding binding = witness.source;
            if (witness.step == -1 && projection.evidence.candidate.equivalent(witness.graph.observed.nodes.get(witness.node).ground)
                    && binding.context.equals(source.getContextId()) && binding.revision == source.getRevision()
                    && binding.sourceRule == label && binding.authority == DmzReplayProvenance.Authority.EXTERNAL
                    && pending.contains(binding)) found.add(binding);
            for (DmzProofWitnesses.Witness premise : witness.premises) findCandidate(premise, pending, source, label, found, work);
        }
        private boolean sameReplayWitness(DmzProofWitnesses.Witness old, DmzProofWitnesses.Witness live,
                DmzReplayProvenance.Binding oldCandidate, DmzReplayProvenance.Binding liveCandidate, int[] work) {
            projectionWork(work);
            if ((old.source == oldCandidate ? live.source != liveCandidate : old.source != live.source)
                    || (old.step == -1) != (live.step == -1) || old.premises.size() != live.premises.size()
                    || !old.graph.observed.nodes.get(old.node).ground.equivalent(live.graph.observed.nodes.get(live.node).ground)) return false;
            for (int i = 0; i < old.premises.size(); ++i)
                if (!sameReplayWitness(old.premises.get(i), live.premises.get(i), oldCandidate, liveCandidate, work)) return false;
            return true;
        }
        private void recognizeSupports(List<ConflictSupport> old, List<ConflictSupport> live,
                DmzReplayProvenance.Binding oldCandidate, DmzReplayProvenance.Binding liveCandidate, int[] work) {
            if (old.size() != live.size()) throw new IllegalStateException("Replay support alternatives changed");
            boolean[] used = new boolean[live.size()];
            for (ConflictSupport support : old) {
                boolean matched = false;
                for (int i = 0; i < live.size(); ++i) if (!used[i]
                        && sameReplayWitness(support.witness, live.get(i).witness, oldCandidate, liveCandidate, work)) {
                    used[i] = true; matched = true; break;
                }
                if (!matched) throw new IllegalStateException("Replay exact witness not recognized");
            }
        }
        private void recognizeReplay(ConsequenceAuditFailure old, ConsequenceAuditFailure live,
                org.kanger.interfaces.IContextResults.Revision source, long label, int limit) throws Exception {
            if (live.kind != ConsequenceConflictKind.INTERNAL || live.conflicts.size() != old.conflicts.size())
                throw new IllegalStateException("Replay conflict boundary changed");
            int[] work = { limit };
            DmzReplayProvenance.Binding before = candidateBinding(old, source, label, work);
            DmzReplayProvenance.Binding after = candidateBinding(live, source, label, work);
            if (before == after) throw new IllegalStateException("Fresh replay candidate binding required");
            boolean[] used = new boolean[live.conflicts.size()];
            for (ConsequenceConflict conflict : old.conflicts) {
                boolean matched = false;
                for (int i = 0; i < live.conflicts.size(); ++i) {
                    projectionWork(work);
                    ConsequenceConflict current = live.conflicts.get(i);
                    if (!used[i] && conflict.ground.equivalent(current.ground)) {
                        recognizeSupports(conflict.supporting, current.supporting, before, after, work);
                        recognizeSupports(conflict.opposing, current.opposing, before, after, work);
                        used[i] = true; matched = true; break;
                    }
                }
                if (!matched) throw new IllegalStateException("Replay conflict ground not recognized");
            }
            if (!projection.isCurrent()) throw new IllegalStateException("Replay boundary changed");
        }
        public void close() throws Exception {
            if (closed) return;
            Mind parent = owner.transaction.mind();
            String state = DmzObservationStateFingerprint.capture(parent);
            List<String> hypotheses = hypothesisText(parent);
            closed = true;
            transaction.close();
            if (!state.equals(DmzObservationStateFingerprint.capture(parent)) || !hypotheses.equals(hypothesisText(parent)))
                throw new IllegalStateException("Support choice rollback changed parent boundary");
        }
    }
    /** Apply only projected last-support visibility losses; exact cut enforcement is not yet an inference API. */
    NativeSupportChoice beginSupportChoice(SymmetricProjection projection, SupportChoice choice, int budget) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (projection == null || projection.evidence.owner != this || choice == null)
            throw new IllegalArgumentException("Owned symmetric projection and explicit choice required");
        if (budget < 1 || budget > 10000) throw new IllegalArgumentException("Support choice budget must be between 1 and 10000");
        if (!projection.isCurrent()) throw new IllegalStateException("Current symmetric projection required");
        Mind parent = transaction.mind();
        TechnicalMindTransaction child = TechnicalMindTransaction.beginIsolated(parent);
        try {
            int remaining = budget, hidden = 0;
            if (choice == SupportChoice.KEEP_CANDIDATE) for (ProjectedGround projected : projection.grounds) {
                if (!projected.losesLastSupport()) continue;
                for (IRule item : child.mind().getRules()) {
                    if (remaining-- == 0) throw new SupportChoiceBudgetFailure("application", hidden);
                    if (item.isDeleted(child.mind()) || item.isQuery()) continue;
                    Rule rule = (Rule) item;
                    if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
                    Domain literal = rule.getTree().get(0).get(0);
                    TerminalSupportCapture.Ground ground = TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(),
                            literal.isAntc(), literal.getArguments().convertBase(child.mind())), child.mind());
                    if (!projected.ground.equivalent(ground)) continue;
                    boolean pending = false;
                    for (DmzReplayProvenance.SourceObservation source : DmzReplayProvenance.observedSources(parent, item.getId())) {
                        if (remaining-- == 0) throw new SupportChoiceBudgetFailure("source", hidden);
                        if (source.outcome == DmzReplayProvenance.Outcome.ACCEPTED)
                            throw new IllegalStateException("Accepted primary occurrence cannot be hidden");
                        if (source.outcome == DmzReplayProvenance.Outcome.PENDING) pending = true;
                    }
                    if (!parent.getRules().isGenerated(item) && !pending)
                        throw new IllegalStateException("Exact pending primary or generated cache required");
                    rule.setDeleted(true, child.mind()); ++hidden;
                }
            }
            if (!projection.isCurrent()) throw new IllegalStateException("Support choice application boundary changed");
            return new NativeSupportChoice(this, projection, choice, child, budget, hidden);
        } catch (Exception failure) { child.close(); throw failure; }
    }
    private static void projectionWork(int[] work) {
        if (work[0]-- == 0) throw new IllegalStateException("Symmetric projection traversal budget exceeded");
    }
    private static boolean containsExactWitness(DmzProofWitnesses.Witness witness, DmzProofWitnesses.Witness cut, int[] work) {
        projectionWork(work);
        if (sameExactWitness(witness, cut, work)) return true;
        for (DmzProofWitnesses.Witness premise : witness.premises)
            if (containsExactWitness(premise, cut, work)) return true;
        return false;
    }
    private static boolean sameExactWitness(DmzProofWitnesses.Witness a, DmzProofWitnesses.Witness b, int[] work) {
        projectionWork(work);
        if (a.source != b.source || (a.step < 0) != (b.step < 0) || a.premises.size() != b.premises.size()
                || !a.graph.observed.nodes.get(a.node).ground.equivalent(b.graph.observed.nodes.get(b.node).ground)) return false;
        for (int i = 0; i < a.premises.size(); ++i)
            if (!sameExactWitness(a.premises.get(i), b.premises.get(i), work)) return false;
        return true;
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
