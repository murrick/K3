/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Rollback-only native overlay with narrowly qualified unit-application restrictions.
 * Recursive continuation accepts positive unary integer facts; its separate query entry point accepts either sign.
 */
final class DmzStoredRetractionTransaction implements AutoCloseable {
    private final TechnicalMindTransaction transaction;
    private final DmzTerminalRestriction restriction;
    private final boolean continuationOnly;
    private final List<Rule> retracted;
    private int cacheAdmissions;
    private boolean closed;
    private DmzStoredRetractionTransaction(TechnicalMindTransaction transaction, DmzTerminalRestriction restriction,
            boolean continuationOnly, List<Rule> retracted) {
        this.transaction = transaction; this.restriction = restriction;
        this.continuationOnly = continuationOnly;
        this.retracted = new ArrayList<Rule>(retracted);
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
        TechnicalMindTransaction transaction = TechnicalMindTransaction.beginIsolated(target);
        try {
            for (Rule rule : removed) rule.setDeleted(true, transaction.mind());
            return new DmzStoredRetractionTransaction(transaction, continuationOnly
                    ? DmzTerminalRestriction.beginContinuation(transaction.mind(), blocked)
                    : DmzTerminalRestriction.begin(transaction.mind(), blocked), continuationOnly, removed);
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
    Boolean accept(org.kanger.interfaces.IContextResults.Revision source, long sourceRule,
            DmzReplayProvenance.Authority authority, String statement) throws Exception {
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (statement == null || !statement.matches("!~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only explicit unary integer-fact continuation is qualified");
        if (continuationOnly && statement.startsWith("!~"))
            throw new IllegalArgumentException("Recursive continuation currently qualifies positive facts only");
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
        if (closed) throw new IllegalStateException("Retraction overlay closed");
        if (!continuationOnly || candidate == null || !candidate.matches("![A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only positive unary integer candidates are qualified");
        if (queryStatement == null || !queryStatement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Only signed unary integer candidate queries are qualified");
        if (auditBudget < 1 || auditBudget > 10000) throw new IllegalArgumentException("Query audit budget must be between 1 and 10000");
        if (source == null || sourceRule < 0 || source.getCommune() != null || !source.getCommuneMembers().isEmpty())
            throw new IllegalArgumentException("Exact atomic candidate source required");
        Mind child = transaction.mind();
        String state = DmzObservationStateFingerprint.capture(child);
        List<String> hypotheses = hypothesisText(child);
        try {
            QueryResult result;
            try (TechnicalMindTransaction probe = TechnicalMindTransaction.beginIsolated(child)) {
                QueryResult.CandidateMode mode = existingPrimary(child, candidate)
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
    private static boolean existingPrimary(Mind mind, String statement) throws Exception {
        int open = statement.indexOf('(');
        String predicate = statement.substring(1, open);
        java.math.BigDecimal value = new java.math.BigDecimal(statement.substring(open + 1, statement.length() - 2));
        for (IRule candidate : mind.getRules()) {
            if (candidate.isDeleted(mind) || candidate.isQuery() || mind.getRules().isGenerated(candidate)) continue;
            Rule rule = (Rule) candidate;
            if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) continue;
            Domain literal = rule.getDomain();
            if (!literal.isAntc() || !literal.getPredicate().getName(mind).equals(predicate)
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
