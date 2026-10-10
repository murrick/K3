/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Read-only finite unary proof reconstruction from current native inputs.
 * Cache presence alone never supplies a proof seed. Not a general native coverage certificate.
 */
final class DmzCurrentUnaryProofInventory {
    static final class Proofs {
        final List<DmzProofWitnesses.Witness> witnesses;
        final boolean truncated;
        Proofs(List<DmzProofWitnesses.Witness> witnesses, boolean truncated) {
            this.witnesses = Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(witnesses));
            this.truncated = truncated;
        }
    }
    private static final class Seed {
        final TerminalSupportCapture.Ground ground;
        final List<DmzReplayProvenance.Binding> sources = new ArrayList<DmzReplayProvenance.Binding>();
        Seed(TerminalSupportCapture.Ground ground) { this.ground = ground; }
    }
    private static final class Clause {
        int index;
        String origin;
        final String[] names = new String[2];
        final boolean[] signs = new boolean[2];
        List<DmzReplayProvenance.Binding> sources;
    }
    final boolean eligible, truncated, provisional;
    final boolean complete = false;
    final List<String> gaps;
    final DmzSourcedProofGraph graph;
    private final Mind target;
    private final String state;
    private final DmzReplayProvenance.SourceCheckpoint sources;
    private final List<String> errors = new ArrayList<String>();
    private final List<Seed> seeds = new ArrayList<Seed>();
    private final List<Clause> clauses = new ArrayList<Clause>();
    private final List<TerminalSupportCapture.Ground> atoms = new ArrayList<TerminalSupportCapture.Ground>();
    private final List<TerminalSupportCapture.ApplicationSources> associations = new ArrayList<TerminalSupportCapture.ApplicationSources>();
    private final int limit;
    private int work;
    private boolean exhausted, pending;

    static DmzCurrentUnaryProofInventory capture(Mind target, int budget) throws Exception {
        return new DmzCurrentUnaryProofInventory(target, budget, false);
    }
    static DmzCurrentUnaryProofInventory capture(Mind target, int budget, boolean allowPending) throws Exception {
        return new DmzCurrentUnaryProofInventory(target, budget, allowPending);
    }
    private DmzCurrentUnaryProofInventory(Mind target, int budget, boolean allowPending) throws Exception {
        if (target == null || budget <= 0) throw new IllegalArgumentException("Target and positive inventory budget required");
        this.target = target; limit = budget;
        sources = DmzReplayProvenance.sourceCheckpoint(target);
        state = DmzObservationStateFingerprint.capture(target);
        Map<UUID, Long> revisions = new HashMap<UUID, Long>();
        for (IRule item : target.getRules()) {
            if (!take()) break;
            if (item.isDeleted(target)) continue;
            Rule rule = (Rule) item;
            if (rule.isQuery() || rule.getTree().size() != 1) { errors.add("unsupported-live-rule"); continue; }
            List<Domain> row = rule.getTree().get(0);
            boolean generated = target.getRules().isGenerated(rule);
            if (generated) {
                if (row.size() != 1 || atom(rule, target) == null) errors.add("unsupported-generated-cache");
            }
            List<DmzReplayProvenance.Binding> labels = new ArrayList<DmzReplayProvenance.Binding>();
            for (DmzReplayProvenance.SourceObservation observed : DmzReplayProvenance.observedSources(target, rule.getId())) {
                if (!take()) break;
                if (observed.outcome == DmzReplayProvenance.Outcome.PENDING) {
                    pending = true;
                    if (!allowPending) { errors.add("pending-source-not-authorized"); continue; }
                } else if (observed.outcome != DmzReplayProvenance.Outcome.ACCEPTED) {
                    errors.add("unsettled-live-source"); continue;
                }
                labels.add(observed.binding);
                Long prior = revisions.put(observed.binding.context, observed.binding.revision);
                if (prior != null && prior.longValue() != observed.binding.revision) errors.add("inconsistent-context-revisions");
            }
            // Explicit primary acceptance can alias an atom already held in the generated cache.
            // Source occurrences, rather than the cache flag alone, establish the new primary seed.
            if (generated && labels.isEmpty()) continue;
            if (labels.isEmpty()) errors.add("missing-live-source");
            if (row.size() == 1) {
                TerminalSupportCapture.Ground ground = atom(rule, target);
                if (ground == null) { errors.add("unsupported-primary-atom"); continue; }
                Seed seed = seed(ground);
                if (seed == null) { seed = new Seed(ground); seeds.add(seed); }
                seed.sources.addAll(labels);
            } else if (row.size() == 2) {
                Clause clause = new Clause(); clause.index = clauses.size(); clause.origin = rule.getOrigin(); clause.sources = labels;
                long variable = -1; boolean valid = true;
                for (int i = 0; i < 2; ++i) {
                    Domain literal = row.get(i);
                    if (!ordinary(literal, target)) { valid = false; break; }
                    IArgument argument = literal.getArguments().get(0);
                    if (argument.getType() != ArgumentType.TVARIABLE || variable >= 0 && variable != argument.getId()) { valid = false; break; }
                    variable = argument.getId(); clause.names[i] = literal.getPredicate().getName(target); clause.signs[i] = literal.isAntc();
                }
                if (valid) clauses.add(clause); else errors.add("unsupported-unit-clause");
            } else errors.add("unsupported-live-arity");
        }
        if (!exhausted && errors.isEmpty()) build();
        if (!sources.isCurrent(target) || !state.equals(DmzObservationStateFingerprint.capture(target))) errors.add("inventory-boundary-changed");
        truncated = exhausted; provisional = pending;
        gaps = Collections.unmodifiableList(new ArrayList<String>(errors));
        eligible = !truncated && gaps.isEmpty();
        graph = eligible ? DmzSourcedProofGraph.build(associations) : null;
    }
    private boolean take() { if (++work > limit) { exhausted = true; return false; } return true; }
    private Seed seed(TerminalSupportCapture.Ground ground) {
        for (Seed seed : seeds) { if (!take()) return null; if (seed.ground.equivalent(ground)) return seed; }
        return null;
    }
    private int add(TerminalSupportCapture.Ground ground) {
        for (int i = 0; i < atoms.size(); ++i) { if (!take()) return -1; if (atoms.get(i).equivalent(ground)) return i; }
        if (!take()) return -1;
        atoms.add(ground); return atoms.size() - 1;
    }
    private void build() {
        // Empty-rule-source self associations carry standalone primary seeds, never a derived proof.
        for (int i = 0; i < seeds.size(); ++i) {
            Seed seed = seeds.get(i); int node = add(seed.ground); if (exhausted) return;
            TerminalSupportCapture.Support support = new TerminalSupportCapture.Support(node, node, "primary-seed", seed.ground, true);
            associate(-1 - i, "primary-seed", seed.ground, support, Collections.<DmzReplayProvenance.Binding>emptyList(), seed.sources);
        }
        for (int cursor = 0; cursor < atoms.size() && !exhausted; ++cursor) {
            TerminalSupportCapture.Ground premise = atoms.get(cursor);
            for (Clause clause : clauses) for (int slot = 0; slot < 2; ++slot) {
                if (!take()) return;
                if (!premise.predicate.equals(clause.names[1-slot]) || premise.sign == clause.signs[1-slot]) continue;
                TerminalSupportCapture.Ground conclusion = new TerminalSupportCapture.Ground(clause.names[slot], clause.signs[slot], premise.arguments);
                add(conclusion); Seed primary = seed(premise); if (exhausted) return;
                TerminalSupportCapture.Support support = new TerminalSupportCapture.Support(cursor, cursor, "computed-premise", premise, primary != null);
                associate(clause.index, clause.origin, conclusion, support, clause.sources,
                        primary == null ? Collections.<DmzReplayProvenance.Binding>emptyList() : primary.sources);
            }
        }
    }
    private void associate(int rule, String origin, TerminalSupportCapture.Ground conclusion,
            TerminalSupportCapture.Support support, List<DmzReplayProvenance.Binding> ruleSources, List<DmzReplayProvenance.Binding> primarySources) {
        if (!take()) return;
        TerminalSupportCapture.Application application = new TerminalSupportCapture.Application(0, rule, origin, "computed-unit",
                conclusion, Collections.<TerminalSupportCapture.Binding>emptyList(), Collections.singletonList(support));
        associations.add(new TerminalSupportCapture.ApplicationSources(application,
                Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(ruleSources)),
                Collections.singletonList(Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(primarySources)))));
    }
    boolean isCurrent(Mind candidate) throws Exception {
        return eligible && candidate == target && sources.isCurrent(candidate)
                && state.equals(DmzObservationStateFingerprint.capture(candidate));
    }
    Proofs proofs(TerminalSupportCapture.Ground ground, List<DmzProofWitnesses.NoGood> noGoods, int budget) throws Exception {
        if (ground == null || budget <= 0) throw new IllegalArgumentException("Typed ground and positive witness budget required");
        if (!isCurrent(target)) throw new IllegalStateException("Current eligible unary inventory required");
        DmzSourcedProofGraph branch = graph.restrict(noGoods);
        for (int i = 0; i < branch.observed.nodes.size(); ++i) if (ground.equivalent(branch.observed.nodes.get(i).ground)) {
            DmzProofWitnesses.Result result = DmzProofWitnesses.enumerate(branch, i, budget);
            return new Proofs(result.witnesses, result.truncated);
        }
        return new Proofs(Collections.<DmzProofWitnesses.Witness>emptyList(), false);
    }
    private static boolean ordinary(Domain literal, Mind mind) throws Exception {
        return !literal.isSystem(mind) && !literal.isCalculated(mind) && !literal.isQuery(mind) && literal.getArguments().size() == 1;
    }
    private static TerminalSupportCapture.Ground atom(Rule rule, Mind mind) throws Exception {
        if (rule.getTree().size() != 1 || rule.getTree().get(0).size() != 1) return null;
        Domain literal = rule.getTree().get(0).get(0);
        if (!ordinary(literal, mind) || literal.getArguments().get(0).getType() != ArgumentType.TERM) return null;
        org.kanger.interfaces.ITerm value = literal.getArguments().get(0).getValue(mind);
        if (!SemanticTermSnapshot.isOrdinaryValue(value)) return null;
        SemanticTermSnapshot snapshot = SemanticTermSnapshot.capture(value);
        if (snapshot.getType() != DataType.NUMERIC && snapshot.getType() != DataType.STRING) return null;
        return TerminalSupportCapture.Ground.capture(new Solve(literal.getPredicate(), literal.isAntc(),
                literal.getArguments().convertBase(mind)), mind);
    }
}
