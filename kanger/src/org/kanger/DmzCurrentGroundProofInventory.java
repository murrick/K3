/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Finite typed unit-clause reconstruction from current sourced native inputs.
 * Supports range-restricted variable permutations/repetitions and constants.
 * Eligibility is scoped closure coverage, never a general native certificate.
 * Active branch restrictions, joins and term constructors are not supported.
 */
final class DmzCurrentGroundProofInventory {
    static final class Proofs {
        final List<DmzProofWitnesses.Witness> witnesses;
        final boolean truncated, provisional;
        private Proofs(List<DmzProofWitnesses.Witness> witnesses, boolean truncated, boolean provisional) {
            this.witnesses = Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(witnesses));
            this.truncated = truncated; this.provisional = provisional;
        }
    }
    private static final class Parameter {
        final Long variable;
        final SemanticTermSnapshot constant;
        Parameter(Long variable, SemanticTermSnapshot constant) { this.variable = variable; this.constant = constant; }
    }
    private static final class Pattern {
        final String predicate;
        final boolean sign;
        final List<Parameter> parameters = new ArrayList<Parameter>();
        final Set<Long> variables = new HashSet<Long>();
        Pattern(Domain literal, Mind mind) throws Exception {
            predicate = literal.getPredicate().getName(mind); sign = literal.isAntc();
        }
    }
    private static final class Applied {
        final TerminalSupportCapture.Ground conclusion;
        final List<TerminalSupportCapture.Binding> bindings;
        Applied(TerminalSupportCapture.Ground conclusion, List<TerminalSupportCapture.Binding> bindings) {
            this.conclusion=conclusion; this.bindings=bindings;
        }
    }
    private static final class Clause {
        int index;
        String origin;
        final Pattern[] patterns = new Pattern[2];
        List<DmzReplayProvenance.Binding> sources;
    }
    private static final class Seed {
        final TerminalSupportCapture.Ground ground;
        final List<DmzReplayProvenance.Binding> sources = new ArrayList<DmzReplayProvenance.Binding>();
        Seed(TerminalSupportCapture.Ground ground) { this.ground = ground; }
    }
    final boolean eligible, truncated, provisional;
    final boolean complete = false;
    final List<String> gaps;
    final DmzSourcedProofGraph graph;
    private final Mind target;
    private final int limit;
    private final String state;
    private final DmzReplayProvenance.SourceCheckpoint checkpoint;
    private final List<String> errors = new ArrayList<String>();
    private final List<Seed> seeds = new ArrayList<Seed>();
    private final List<Clause> clauses = new ArrayList<Clause>();
    private final List<TerminalSupportCapture.Ground> atoms = new ArrayList<TerminalSupportCapture.Ground>();
    private final List<TerminalSupportCapture.Ground> caches = new ArrayList<TerminalSupportCapture.Ground>();
    private final List<TerminalSupportCapture.ApplicationSources> associations = new ArrayList<TerminalSupportCapture.ApplicationSources>();
    private int work;
    private boolean exhausted, pending;

    static DmzCurrentGroundProofInventory capture(Mind target, int budget, boolean allowPending) throws Exception {
        return new DmzCurrentGroundProofInventory(target, budget, allowPending);
    }
    private DmzCurrentGroundProofInventory(Mind target, int budget, boolean allowPending) throws Exception {
        if (target == null || budget < 1 || budget > 10000) throw new IllegalArgumentException("Bounded ground inventory required");
        this.target = target; limit = budget;
        state = DmzObservationStateFingerprint.capture(target);
        checkpoint = DmzReplayProvenance.sourceCheckpoint(target);
        if (!DmzTerminalRestriction.activeNoGoods(target).isEmpty()) errors.add("active-restriction-not-qualified");
        Map<UUID, Long> revisions = new HashMap<UUID, Long>();
        Map<String, Long> occurrences = new HashMap<String, Long>();
        for (IRule item : target.getRules()) {
            if (!take()) break;
            if (item.isDeleted(target)) continue;
            Rule rule = (Rule) item;
            if (rule.isQuery() || rule.getTree().size() != 1) { errors.add("unsupported-live-rule"); continue; }
            List<Domain> row = rule.getTree().get(0);
            boolean generated = target.getRules().isGenerated(rule);
            List<DmzReplayProvenance.Binding> labels = new ArrayList<DmzReplayProvenance.Binding>();
            for (DmzReplayProvenance.SourceObservation observed : DmzReplayProvenance.observedSources(target, rule.getId())) {
                if (!take()) break;
                if (observed.outcome == DmzReplayProvenance.Outcome.PENDING) {
                    pending = true;
                    if (!allowPending) { errors.add("pending-source-not-authorized"); continue; }
                } else if (observed.outcome != DmzReplayProvenance.Outcome.ACCEPTED) {
                    errors.add("unsettled-live-source"); continue;
                }
                DmzReplayProvenance.Binding binding = observed.binding;
                Long previous = revisions.put(binding.context, binding.revision);
                if (previous != null && previous.longValue() != binding.revision) errors.add("inconsistent-context-revisions");
                String key = binding.context + "@" + binding.revision + ":" + binding.sourceRule;
                Long nativeId = occurrences.put(key, rule.getId());
                if (nativeId != null && nativeId.longValue() != rule.getId()) errors.add("source-rule-bound-to-different-inputs");
                labels.add(binding);
            }
            if (row.size() == 1) {
                TerminalSupportCapture.Ground atom = atom(rule);
                if (atom == null) { errors.add("unsupported-ground"); continue; }
                if (generated) caches.add(atom);
                if (generated && labels.isEmpty()) continue;
                if (labels.isEmpty()) { errors.add("missing-primary-source"); continue; }
                Seed seed = seed(atom);
                if (seed == null && !exhausted) { seed = new Seed(atom); seeds.add(seed); }
                if (seed != null) seed.sources.addAll(labels);
            } else if (row.size() == 2 && !generated) {
                if (labels.isEmpty()) { errors.add("missing-production-source"); continue; }
                Clause clause = new Clause(); clause.index = clauses.size(); clause.origin = rule.getOrigin(); clause.sources = labels;
                clause.patterns[0] = pattern(row.get(0)); clause.patterns[1] = pattern(row.get(1));
                if (clause.patterns[0] == null || clause.patterns[1] == null
                        || clause.patterns[0].variables.isEmpty()
                        || !clause.patterns[0].variables.equals(clause.patterns[1].variables))
                    errors.add("unsupported-unit-pattern");
                else clauses.add(clause);
            } else errors.add("unsupported-live-clause");
        }
        if (!exhausted && errors.isEmpty()) build();
        if (!exhausted && errors.isEmpty()) for (TerminalSupportCapture.Ground cache : caches)
            if (find(cache) < 0) errors.add("generated-cache-without-reconstructed-support");
        if (!checkpoint.isCurrent(target) || !state.equals(DmzObservationStateFingerprint.capture(target))) errors.add("inventory-boundary-changed");
        truncated = exhausted; provisional = pending;
        gaps = Collections.unmodifiableList(new ArrayList<String>(errors));
        eligible = !truncated && gaps.isEmpty();
        graph = eligible ? DmzSourcedProofGraph.build(associations) : null;
    }
    private boolean take() { if (++work > limit) { exhausted = true; return false; } return true; }
    private boolean ordinary(Domain literal) throws Exception {
        return !literal.isSystem(target) && !literal.isCalculated(target) && !literal.isQuery(target)
                && literal.getArguments().size() <= 16;
    }
    private boolean value(ITerm value, int depth) throws Exception {
        if (!take()) return false;
        if (!(value instanceof Term) || value.isCVariable() || depth > 8) return false;
        Term term = (Term) value;
        switch (term.getType()) {
            case SET:
                for (ITerm member : term.semanticMembers()) if (!value(member, depth + 1)) return false;
                break;
            case INTERVAL:
                if (!(term.getValue() instanceof Collection)) return false;
                for (Object member : (Collection<?>) term.getValue())
                    if (!(member instanceof ITerm) || !value((ITerm) member, depth + 1)) return false;
                break;
            case TERM:
                return term.getValue() instanceof ITerm && value((ITerm) term.getValue(), depth + 1);
            default: break;
        }
        return true;
    }
    private TerminalSupportCapture.Ground atom(Rule rule) throws Exception {
        Domain literal = rule.getTree().get(0).get(0);
        if (!ordinary(literal)) return null;
        for (IArgument argument : literal.getArguments())
            if (argument.getType() != ArgumentType.TERM || !value(argument.getValue(target), 0)) return null;
        return DmzGroundIdentity.capture(rule, target).ground();
    }
    private Pattern pattern(Domain literal) throws Exception {
        if (!ordinary(literal)) return null;
        Pattern pattern = new Pattern(literal, target);
        for (IArgument argument : literal.getArguments()) {
            if (!take()) return null;
            if (argument.getType() == ArgumentType.TVARIABLE) {
                Long variable = argument.getId(); pattern.variables.add(variable);
                pattern.parameters.add(new Parameter(variable, null));
            } else if (argument.getType() == ArgumentType.TERM && value(argument.getValue(target), 0))
                pattern.parameters.add(new Parameter(null, SemanticTermSnapshot.capture(argument.getValue(target))));
            else return null;
        }
        return pattern;
    }
    private Seed seed(TerminalSupportCapture.Ground atom) {
        for (Seed seed : seeds) { if (!take()) return null; if (seed.ground.equivalent(atom)) return seed; }
        return null;
    }
    private int find(TerminalSupportCapture.Ground atom) {
        for (int i = 0; i < atoms.size(); ++i) { if (!take()) return -1; if (atoms.get(i).equivalent(atom)) return i; }
        return -1;
    }
    private int add(TerminalSupportCapture.Ground atom) {
        int index = find(atom); if (index >= 0 || exhausted) return index;
        if (!take()) return -1;
        atoms.add(atom); return atoms.size() - 1;
    }
    private Applied apply(Pattern premise, Pattern conclusion, TerminalSupportCapture.Ground atom) {
        if (!take() || !premise.predicate.equals(atom.predicate) || premise.sign == atom.sign
                || premise.parameters.size() != atom.arguments.size()) return null;
        Map<Long, SemanticTermSnapshot> bindings = new HashMap<Long, SemanticTermSnapshot>();
        for (int i = 0; i < premise.parameters.size(); ++i) {
            if (!take()) return null;
            Parameter parameter = premise.parameters.get(i); SemanticTermSnapshot actual = atom.arguments.get(i);
            if (parameter.variable == null) {
                if (!parameter.constant.semanticallyEquals(actual)) return null;
            } else {
                SemanticTermSnapshot prior = bindings.put(parameter.variable, actual);
                if (prior != null && !prior.semanticallyEquals(actual)) return null;
            }
        }
        List<SemanticTermSnapshot> arguments = new ArrayList<SemanticTermSnapshot>();
        for (Parameter parameter : conclusion.parameters) {
            if (!take()) return null;
            SemanticTermSnapshot argument = parameter.variable == null ? parameter.constant : bindings.get(parameter.variable);
            if (argument == null) throw new IllegalStateException("Unit range restriction changed");
            arguments.add(argument);
        }
        List<Long> variables = new ArrayList<Long>(bindings.keySet()); Collections.sort(variables);
        List<TerminalSupportCapture.Binding> substitution = new ArrayList<TerminalSupportCapture.Binding>();
        for (int i=0; i<variables.size(); ++i) {
            if (!take()) return null;
            SemanticTermSnapshot actual = bindings.get(variables.get(i));
            substitution.add(new TerminalSupportCapture.Binding(i, variables.get(i), String.valueOf(variables.get(i)),
                    "<"+actual.getType()+">", actual));
        }
        return new Applied(new TerminalSupportCapture.Ground(conclusion.predicate, conclusion.sign, arguments), substitution);
    }
    private void build() {
        for (int i = 0; i < seeds.size(); ++i) {
            Seed seed = seeds.get(i); int node = add(seed.ground); if (exhausted) return;
            associate(-1 - i, "primary-seed", seed.ground, node, true,
                    Collections.<DmzReplayProvenance.Binding>emptyList(), seed.sources,
                    Collections.<TerminalSupportCapture.Binding>emptyList());
        }
        for (int cursor = 0; cursor < atoms.size() && !exhausted; ++cursor) {
            TerminalSupportCapture.Ground premise = atoms.get(cursor);
            for (Clause clause : clauses) for (int slot = 0; slot < 2; ++slot) {
                Applied applied = apply(clause.patterns[1-slot], clause.patterns[slot], premise);
                if (exhausted) return;
                if (applied == null) continue;
                add(applied.conclusion); Seed primary = seed(premise); if (exhausted) return;
                associate(clause.index, clause.origin, applied.conclusion, cursor, primary != null, clause.sources,
                        primary == null ? Collections.<DmzReplayProvenance.Binding>emptyList() : primary.sources, applied.bindings);
            }
        }
    }
    private void associate(int rule, String origin, TerminalSupportCapture.Ground conclusion, int premise,
            boolean primary, List<DmzReplayProvenance.Binding> ruleSources, List<DmzReplayProvenance.Binding> groundSources,
            List<TerminalSupportCapture.Binding> substitution) {
        if (!take()) return;
        TerminalSupportCapture.Support support = new TerminalSupportCapture.Support(premise, premise, "typed-premise", atoms.get(premise), primary);
        TerminalSupportCapture.Application application = new TerminalSupportCapture.Application(0, rule, origin, "computed-unit",
                conclusion, substitution, Collections.singletonList(support));
        associations.add(new TerminalSupportCapture.ApplicationSources(application,
                Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(ruleSources)),
                Collections.singletonList(Collections.unmodifiableList(new ArrayList<DmzReplayProvenance.Binding>(groundSources)))));
    }
    boolean isCurrent(Mind candidate) throws Exception {
        return eligible && candidate == target && DmzTerminalRestriction.activeNoGoods(candidate).isEmpty()
                && checkpoint.isCurrent(candidate)
                && state.equals(DmzObservationStateFingerprint.capture(candidate));
    }
    Proofs proofs(TerminalSupportCapture.Ground atom, int budget) throws Exception {
        if (atom == null || budget < 1 || budget > 10000) throw new IllegalArgumentException("Bounded typed witness request required");
        if (!isCurrent(target)) throw new IllegalStateException("Eligible current typed inventory required");
        for (int i = 0; i < graph.observed.nodes.size(); ++i) if (atom.equivalent(graph.observed.nodes.get(i).ground)) {
            DmzProofWitnesses.Result result = DmzProofWitnesses.enumerate(graph, i, budget);
            return new Proofs(result.witnesses, result.truncated, provisional);
        }
        return new Proofs(Collections.<DmzProofWitnesses.Witness>emptyList(), false, provisional);
    }
}
