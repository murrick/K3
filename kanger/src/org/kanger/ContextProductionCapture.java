/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.enums.QueryPass;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/**
 * Opt-in, detached observation of native qualification applications before
 * stored-conclusion suppression. This journal does not itself certify a
 * fixed point, completeness, attribution or acceptance of a Context candidate.
 */
public final class ContextProductionCapture implements AutoCloseable {
    private static final ThreadLocal<ContextProductionCapture> ACTIVE = new ThreadLocal<>();

    /** Exact primary source occurrence supplied by the isolated replay owner. */
    public static final class Source {
        public final UUID context;
        public final long revision, rule;
        public final boolean localX;
        public final String view;
        public Source(UUID context, long revision, long rule, boolean localX, String view) {
            if (context == null || revision < 0 || rule < 0 || view == null)
                throw new IllegalArgumentException("Exact source and effective-view identity required");
            this.context = context; this.revision = revision; this.rule = rule;
            this.localX = localX; this.view = view;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Source)) return false;
            Source s = (Source) other;
            return context.equals(s.context) && revision == s.revision && rule == s.rule
                    && localX == s.localX && view.equals(s.view);
        }
        @Override public int hashCode() { return Objects.hash(context, revision, rule, localX, view); }
    }

    /** Semantic signed ground; transport never recompiles rendered argument text. */
    public static final class Fact {
        public final String predicate;
        public final boolean positive;
        public final List<SemanticTermSnapshot> arguments;
        private Fact(String predicate, boolean positive, List<SemanticTermSnapshot> arguments) {
            this.predicate = predicate; this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }
        static Fact of(Domain domain, Mind mind, boolean opposite) throws Exception {
            if (!domain.isComplete() || domain.isSystem(mind) || domain.isCalculated(mind)
                    || "rule(1)".equals(domain.getPredicate(mind).toString(mind))) return null;
            List<SemanticTermSnapshot> arguments = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                ITerm value = argument.getValue(mind);
                if (!SemanticTermSnapshot.isOrdinaryValue(value)) return null;
                arguments.add(SemanticTermSnapshot.capture(value));
            }
            return new Fact(domain.getPredicate(mind).getName(mind),
                    opposite ? !domain.isAntc() : domain.isAntc(), arguments);
        }
        public boolean equivalent(Fact other) {
            if (other == null || positive != other.positive || !predicate.equals(other.predicate)
                    || arguments.size() != other.arguments.size()) return false;
            for (int i = 0; i < arguments.size(); ++i)
                if (!arguments.get(i).semanticallyEquals(other.arguments.get(i))) return false;
            return true;
        }
        /** A placeholder carrier; arguments must be canonically projected into target. */
        public String assertion() {
            StringBuilder text = new StringBuilder(positive ? "!" : "!~").append(predicate).append('(');
            for (int i = 0; i < arguments.size(); ++i) {
                if (i != 0) text.append(',');
                text.append('?');
            }
            return text.append(");").toString();
        }
        public Queue<ITerm> project(Mind target) throws Exception {
            Queue<ITerm> result = new LinkedList<>();
            for (SemanticTermSnapshot argument : arguments)
                result.add(target.getTerms().projectSemantic(argument.materialize()));
            return result;
        }
    }

    /** A slot is either an ordinary semantic value or a clause-local universal variable. */
    public static final class Slot {
        public final SemanticTermSnapshot value;
        public final int variable;
        private Slot(SemanticTermSnapshot value, int variable) { this.value = value; this.variable = variable; }
    }
    public static final class Atom {
        public final String predicate;
        public final boolean positive;
        public final List<Slot> arguments;
        private Atom(String predicate, boolean positive, List<Slot> arguments) {
            this.predicate = predicate; this.positive = positive;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }
        private static Atom of(Fact fact) {
            List<Slot> slots = new ArrayList<>();
            for (SemanticTermSnapshot value : fact.arguments) slots.add(new Slot(value, -1));
            return new Atom(fact.predicate, fact.positive, slots);
        }
        private static Atom of(Domain domain, Mind mind, boolean unbound, Map<Long,Integer> variables) throws Exception {
            if (domain.isSystem(mind) || domain.isCalculated(mind)
                    || "rule(1)".equals(domain.getPredicate(mind).toString(mind))) return null;
            List<Slot> slots = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                if (argument.isEmpty(mind)) {
                    if (!unbound || argument.getType() != ArgumentType.TVARIABLE) return null;
                    Integer variable = variables.get(argument.getId());
                    if (variable == null) { variable = variables.size(); variables.put(argument.getId(), variable); }
                    slots.add(new Slot(null, variable));
                } else {
                    ITerm value = argument.getValue(mind);
                    if (!SemanticTermSnapshot.isOrdinaryValue(value)) return null;
                    slots.add(new Slot(SemanticTermSnapshot.capture(value), -1));
                }
            }
            return new Atom(domain.getPredicate(mind).getName(mind), domain.isAntc(), slots);
        }
        public Fact fact() {
            List<SemanticTermSnapshot> values = new ArrayList<>();
            for (Slot slot : arguments) { if (slot.variable >= 0) return null; values.add(slot.value); }
            return new Fact(predicate, positive, values);
        }
        private boolean matches(Atom other, Map<Integer,Integer> forward, Map<Integer,Integer> reverse) {
            if (positive != other.positive || !predicate.equals(other.predicate) || arguments.size() != other.arguments.size()) return false;
            for (int i=0; i<arguments.size(); ++i) {
                Slot left = arguments.get(i), right = other.arguments.get(i);
                if (left.variable < 0 || right.variable < 0) {
                    if (left.variable != right.variable || !left.value.semanticallyEquals(right.value)) return false;
                } else {
                    Integer mapped = forward.get(left.variable), back = reverse.get(right.variable);
                    if ((mapped != null && mapped.intValue() != right.variable) || (back != null && back.intValue() != left.variable)) return false;
                    forward.put(left.variable, right.variable); reverse.put(right.variable, left.variable);
                }
            }
            return true;
        }
        private boolean identical(Atom other) {
            Map<Integer,Integer> identity = new HashMap<>();
            for (Slot slot : arguments) if (slot.variable >= 0) identity.put(slot.variable, slot.variable);
            return matches(other, new HashMap<>(identity), new HashMap<>(identity));
        }
    }

    /** Detached disjunction; scoped variables never become ordinary facts or values. */
    public static final class Clause {
        public final List<Atom> literals;
        public final Set<Integer> variables;
        public Clause(List<Fact> literals) { this(atoms(literals)); }
        private static List<Atom> atoms(List<Fact> facts) {
            List<Atom> atoms = new ArrayList<>(); for (Fact fact : facts) atoms.add(Atom.of(Objects.requireNonNull(fact))); return atoms;
        }
        private Clause(Collection<Atom> literals) {
            if (literals.isEmpty()) throw new IllegalArgumentException("Nonempty clause required");
            List<Atom> copy = new ArrayList<>();
            for (Atom atom : literals) { boolean exists = false;
                for (Atom old : copy) exists |= old.identical(atom);
                if (!exists) copy.add(atom);
            }
            this.literals = Collections.unmodifiableList(copy);
            Set<Integer> variables = new TreeSet<>();
            for (Atom atom : copy) for (Slot slot : atom.arguments) if (slot.variable >= 0) variables.add(slot.variable);
            this.variables = Collections.unmodifiableSet(variables);
        }
        static Clause of(List<Domain> branch, Mind mind, boolean unbound) throws Exception {
            List<Atom> atoms = new ArrayList<>(); Map<Long,Integer> variables = new HashMap<>();
            for (Domain domain : branch) {
                Atom atom = Atom.of(domain, mind, unbound, variables);
                if (atom == null) return null;
                atoms.add(atom);
            }
            return atoms.isEmpty() ? null : new Clause(atoms);
        }
        public boolean equivalent(Clause other) {
            if (other == null || literals.size() != other.literals.size() || variables.size() != other.variables.size()) return false;
            return match(other, 0, new boolean[literals.size()], new HashMap<Integer,Integer>(), new HashMap<Integer,Integer>(), new int[]{10000});
        }
        private boolean match(Clause other, int slot, boolean[] used, Map<Integer,Integer> forward,
                              Map<Integer,Integer> reverse, int[] budget) {
            if (--budget[0] < 0) throw new IllegalStateException("Clause comparison budget; qualification incomplete");
            if (slot == literals.size()) return true;
            for (int i=0; i<used.length; ++i) if (!used[i]) {
                if (--budget[0] < 0) throw new IllegalStateException("Clause comparison budget; qualification incomplete");
                Map<Integer,Integer> next = new HashMap<>(forward), back = new HashMap<>(reverse);
                if (!literals.get(slot).matches(other.literals.get(i), next, back)) continue;
                used[i] = true;
                boolean found = match(other, slot+1, used, next, back, budget);
                used[i] = false;
                if (found) return true;
            }
            return false;
        }
        public String assertion() {
            StringBuilder text = new StringBuilder("!");
            for (int variable : variables) text.append("@ctxroundv").append(variable).append(' ');
            text.append('(');
            boolean first = true;
            for (Atom atom : literals) {
                if (!first) text.append(" || "); first = false;
                if (!atom.positive) text.append('~');
                text.append(atom.predicate).append('(');
                for (int i=0; i<atom.arguments.size(); ++i) {
                    if (i != 0) text.append(',');
                    Slot argument = atom.arguments.get(i);
                    if (argument.variable >= 0) text.append("ctxroundv").append(argument.variable);
                    else text.append('?');
                }
                text.append(')');
            }
            return text.append(");").toString();
        }
        public Queue<ITerm> project(Mind target) throws Exception {
            Queue<ITerm> values = new LinkedList<>();
            for (Atom atom : literals) for (Slot slot : atom.arguments) if (slot.variable < 0)
                values.add(target.getTerms().projectSemantic(slot.value.materialize()));
            return values;
        }
        /** Eigenparameters absent from all primary knowledge are universally generalizable.
         * Only direct slots are generalized; structured parameter values are unsupported. */
        public Clause generalize(List<SemanticTermSnapshot> parameters) {
            List<Atom> result = new ArrayList<>();
            int offset = variables.isEmpty() ? 0 : Collections.max(variables) + 1;
            for (Atom atom : literals) {
                List<Slot> slots = new ArrayList<>();
                for (Slot slot : atom.arguments) {
                    Slot replacement = slot;
                    if (slot.variable < 0) for (int i=0; i<parameters.size(); ++i)
                        if (slot.value.semanticallyEquals(parameters.get(i))) {
                            replacement = new Slot(null, offset+i); break;
                        } else if (slot.value.containsSemanticValue(parameters.get(i)))
                            throw new IllegalStateException("Structured eigenparameter; qualification incomplete");
                    slots.add(replacement);
                }
                result.add(new Atom(atom.predicate,atom.positive,slots));
            }
            return new Clause(result);
        }
    }
    /** Each entry is one whole alternative proof, never an alias per premise. */
    public static final class Support {
        public final Set<Source> sources;
        public final int depth;
        public Support(Set<Source> sources, int depth) {
            if (sources.isEmpty() || depth < 0) throw new IllegalArgumentException("Proof required");
            this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources)); this.depth = depth;
        }
        @Override public boolean equals(Object other) {
            return other instanceof Support && depth == ((Support) other).depth
                    && sources.equals(((Support) other).sources);
        }
        @Override public int hashCode() { return Objects.hash(sources, depth); }
    }
    public static final class Residual {
        public final Clause conclusion;
        public final List<Fact> premises;
        public final List<Support> producerSupports;
        public final int steps;
        Residual(Clause conclusion, List<Fact> premises, List<Support> supports) {
            this(conclusion, premises, supports, 1);
        }
        Residual(Clause conclusion, List<Fact> premises, List<Support> supports, int steps) {
            this.steps = steps;
            this.conclusion = conclusion; this.premises = Collections.unmodifiableList(new ArrayList<>(premises));
            producerSupports = Collections.unmodifiableList(new ArrayList<>(supports));
        }
    }

    public static final class Input {
        public final Source source;
        public final long nativeRule;
        public final String origin;
        public final Fact fact;
        public final Clause clause;
        public final List<Clause> clauses;
        public final boolean duplicate;
        private Input(Source source, Rule rule, Mind mind, boolean duplicate) throws Exception {
            this.source = source; nativeRule = rule.getId(); origin = rule.getOrigin();
            fact = rule.isStored() && !rule.isQuery() ? Fact.of(rule.getDomain(), mind, false) : null;
            List<Clause> captured = new ArrayList<>();
            for (List<Domain> branch : rule.getTree()) {
                Clause clause = Clause.of(branch, mind, source == null || branch.size() == 1);
                if (clause != null) captured.add(clause);
            }
            clauses = Collections.unmodifiableList(captured);
            clause = rule.getTree().size() == 1 && captured.size() == 1 ? captured.get(0) : null;
            this.duplicate = duplicate;
        }
    }

    /** One native application with the whole ordinary donor group at its live binding. */
    public static final class Application {
        public final Fact conclusion;
        public final List<Fact> premises;
        public final List<Source> producers;
        public final List<Support> producerSupports;
        public final long nativeRule;
        public final String origin;
        public final int steps;
        private Application(Fact conclusion, List<Fact> premises, List<Support> supports, Rule rule, int steps) throws Exception {
            this.steps = steps;
            this.conclusion = conclusion;
            this.premises = Collections.unmodifiableList(new ArrayList<>(premises));
            producerSupports = Collections.unmodifiableList(new ArrayList<>(supports));
            Set<Source> producers = new LinkedHashSet<>();
            for (Support support : supports) producers.addAll(support.sources);
            this.producers = Collections.unmodifiableList(new ArrayList<>(producers));
            nativeRule = rule.getId(); origin = rule.getOrigin();
        }
    }

    public static final class Snapshot {
        public final List<Input> inputs;
        public final List<Application> applications;
        public final List<Residual> residuals;
        public final List<String> gaps;
        public final int eventCount;
        private Snapshot(ContextProductionCapture capture) {
            eventCount = capture.inputs.size() + capture.applications.size() + capture.residuals.size() + capture.stored.size();
            inputs = Collections.unmodifiableList(new ArrayList<>(capture.inputs));
            applications = Collections.unmodifiableList(new ArrayList<>(capture.applications));
            residuals = Collections.unmodifiableList(new ArrayList<>(capture.residuals));
            Set<String> gaps = new LinkedHashSet<>(capture.gaps);
            for (Materialization stored : capture.stored) {
                boolean covered = false;
                for (Application application : capture.applications)
                    if (application.nativeRule == stored.producer && application.conclusion.equivalent(stored.fact)) {
                        covered = true; break;
                    }
                if (!covered) gaps.add("unobserved-materialization:" + stored.producer);
            }
            this.gaps = Collections.unmodifiableList(new ArrayList<>(gaps));
        }
    }
    private static final class Materialization {
        final long producer;
        final Fact fact;
        Materialization(long producer, Fact fact) { this.producer = producer; this.fact = fact; }
    }

    private final ContextProductionCapture previous;
    private final Thread owner = Thread.currentThread();
    private final Mind boundary;
    private final int maximumEvents;
    private final List<Input> inputs = new ArrayList<>();
    private final List<Application> applications = new ArrayList<>();
    private final List<Residual> residuals = new ArrayList<>();
    private final List<Materialization> stored = new ArrayList<>();
    private final List<SemanticTermSnapshot> primaryValues = new ArrayList<>();
    private final Set<String> gaps = new LinkedHashSet<>();
    private final Map<Mind, Map<Long, List<Support>>> labels = new IdentityHashMap<>();
    private Mind inputTarget;
    private Source inputSource;
    private List<Support> inputSupports;
    private Clause inputClause;
    private boolean closed;

    private ContextProductionCapture(Mind boundary, int maximumEvents) {
        if (boundary == null || maximumEvents < 1) throw new IllegalArgumentException("Bounded replay required");
        this.boundary = boundary; this.maximumEvents = maximumEvents;
        previous = ACTIVE.get(); ACTIVE.set(this);
    }
    public static ContextProductionCapture begin(Mind boundary, int maximumEvents) {
        return new ContextProductionCapture(boundary, maximumEvents);
    }
    public void verifyParametersFresh(List<SemanticTermSnapshot> parameters) {
        check();
        for (SemanticTermSnapshot value : primaryValues) for (SemanticTermSnapshot parameter : parameters)
            if (value.containsSemanticValue(parameter))
                throw new IllegalStateException("Eigenparameter occurs in primary knowledge; qualification incomplete");
    }
    private void check() {
        if (closed || Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Active owning production journal required");
    }
    private boolean visible(Mind mind) {
        for (Mind level = mind; level != null; level = (Mind) level.getNext())
            if (level == boundary) return true;
        return false;
    }
    private void take() {
        if ((long) inputs.size() + applications.size() + residuals.size() + stored.size() >= maximumEvents) {
            gaps.add("observation-budget-exhausted");
            throw new IllegalStateException("Context production observation limit exceeded; qualification incomplete");
        }
    }
    public void replay(Mind target, Source source, String statement) throws Exception {
        check();
        if (!visible(target) || source == null || statement == null || !statement.startsWith("!"))
            throw new IllegalArgumentException("Sourced primary assertion in this boundary required");
        org.kanger.compiler.Token token = org.kanger.enums.Tools.extractLine(statement, null);
        if (token == null || org.kanger.enums.Tools.extractLine(statement, token) != null)
            throw new IllegalArgumentException("Exactly one complete source assertion required");
        Mind previousTarget = inputTarget;
        Source previousSource = inputSource;
        inputTarget = target; inputSource = source;
        int before = inputs.size();
        try {
            target.compileLine(statement, false, new LinkedList<ITerm>());
            if (inputs.size() == before) gaps.add("unbound-input");
        }
        finally { inputTarget = previousTarget; inputSource = previousSource; }
    }
    public void replayDerived(Mind target, Clause clause, List<Support> supports) throws Exception {
        check();
        if (!visible(target) || supports.isEmpty()) throw new IllegalArgumentException("Supported derived clause required");
        if (inputTarget != null) throw new IllegalStateException("Nested replay");
        inputTarget = target; inputSupports = new ArrayList<>(supports); inputClause = Objects.requireNonNull(clause);
        int before = inputs.size();
        try {
            target.compileLine(clause.assertion(), false, clause.project(target));
            if (inputs.size() == before) gaps.add("unbound-derived-input");
        } finally { inputTarget = null; inputSupports = null; inputClause = null; }
    }
    static void compiled(Mind mind, Rule rule, boolean duplicate) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || capture.inputTarget != mind || (capture.inputSource == null && capture.inputSupports == null) || rule.isQuery()) return;
        capture.check(); capture.take();
        Map<Long, List<Support>> local = capture.labels.computeIfAbsent(mind, ignored -> new HashMap<>());
        List<Support> sources = local.computeIfAbsent(rule.getId(), ignored -> new ArrayList<>());
        List<Support> incoming = capture.inputSource != null
                ? Collections.singletonList(new Support(Collections.singleton(capture.inputSource), 0)) : capture.inputSupports;
        for (Support support : incoming) if (!sources.contains(support)) sources.add(support);
        Input input = new Input(capture.inputSource, rule, mind, duplicate);
        capture.inputs.add(input);
        if (capture.inputClause != null && !capture.inputClause.equivalent(input.clause))
            capture.gaps.add("derived-clause-transport-mismatch:" + rule.getId());
        for (List<Domain> branch : rule.getTree()) if (branch.size() == 1
                && Clause.of(branch, mind, true) == null)
            capture.gaps.add("nonordinary-primary-fact:" + rule.getId());
        for (List<Domain> branch : rule.getTree()) for (Domain literal : branch) {
            if (literal.isSystem(mind) || literal.isCalculated(mind))
                capture.gaps.add("calculated-input:" + rule.getId());
            if ("rule(1)".equals(literal.getPredicate(mind).toString(mind)))
                capture.gaps.add("local-rule-id-input:" + rule.getId());
            for (IArgument argument : literal.getArguments()) {
                if (argument.getType() == ArgumentType.FUNCTION) {
                    capture.gaps.add("functional-input:" + rule.getId());
                } else if (!argument.isEmpty(mind)) {
                    ITerm value = argument.getValue(mind);
                    if (value != null && value.isCVariable()) capture.gaps.add("cvariable-input:" + rule.getId());
                    else if (!SemanticTermSnapshot.isOrdinaryValue(value)) capture.gaps.add("nonordinary-input:" + rule.getId());
                    else if (capture.inputSource != null && argument.getType() != ArgumentType.TVARIABLE)
                        capture.primaryValues.add(SemanticTermSnapshot.capture(value));
                }
            }
        }
    }
    private List<Support> sources(Mind mind, long rule) {
        List<Support> result = new ArrayList<>();
        for (Mind level = mind; level != null; level = (Mind) level.getNext()) {
            Map<Long, List<Support>> local = labels.get(level);
            if (local != null && local.containsKey(rule))
                for (Support source : local.get(rule)) if (!result.contains(source)) result.add(source);
            if (level == boundary) break;
        }
        return result;
    }
    static void record(Mind mind, Domain conclusion, List<Domain> branch) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || !capture.visible(mind)
                || (mind.getQueryPass() != QueryPass.CHECK && mind.getQueryPass() != QueryPass.ACCEPT)) return;
        capture.check();
        Rule rule = (Rule) conclusion.getRule();
        if (rule == null || rule.isQuery()) return;
        boolean instance = branch.size() == 1;
        if (instance && (!rule.isSubstitutable() || rule.isGenerated())) return;
        Fact result = Fact.of(conclusion, mind, false);
        if (result == null) {
            if (!instance) capture.gaps.add("nonordinary-conclusion:" + rule.getId());
            return;
        }
        List<Fact> premises = new ArrayList<>();
        for (Domain literal : branch) {
            if (literal == conclusion) continue;
            if (literal.isSystem(mind) || literal.isCalculated(mind)) {
                capture.gaps.add("calculated-premise:" + rule.getId()); return;
            }
            if (!literal.isExcluded(mind)) { capture.gaps.add("unclosed-premise:" + rule.getId()); return; }
            Fact premise = Fact.of(literal, mind, true);
            if (premise == null) { capture.gaps.add("nonordinary-premise:" + rule.getId()); return; }
            premises.add(premise);
        }
        List<Support> producers = capture.sources(mind, rule.getId());
        if (producers.isEmpty()) capture.gaps.add("unlabelled-production:" + rule.getId());
        capture.take();
        capture.applications.add(new Application(result, premises, producers, rule, instance ? 0 : 1));
    }
    /** Observe unsupported schema interaction without treating a waiter assumption as a fact. */
    static void waiterComposition(Mind mind, Domain waiter, Domain literal) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || !capture.visible(mind)
                || (mind.getQueryPass() != QueryPass.CHECK && mind.getQueryPass() != QueryPass.ACCEPT)) return;
        capture.check();
        Rule rule = (Rule) literal.getRule();
        if (rule == null || rule.isQuery() || waiter.getPredicateId() != literal.getPredicateId()
                || waiter.isAntc() == literal.isAntc()) return;
        if (!waiter.getArguments().getTVariables(mind).isEmpty()
                && !literal.getArguments().getTVariables(mind).isEmpty())
            capture.gaps.add("unbound-waiter-composition:" + rule.getId());
    }
    static void incompleteResidual(Mind mind, List<Domain> branch) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || !capture.visible(mind)
                || (mind.getQueryPass() != QueryPass.CHECK && mind.getQueryPass() != QueryPass.ACCEPT)) return;
        capture.check();
        for (Domain literal : branch) if (literal.isExcluded(mind)) {
            compoundResidual(mind, branch); return;
        }
    }
    static void compoundResidual(Mind mind, List<Domain> branch) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || !capture.visible(mind)
                || (mind.getQueryPass() != QueryPass.CHECK && mind.getQueryPass() != QueryPass.ACCEPT)) return;
        capture.check();
        if (branch.isEmpty()) return;
        Rule rule = (Rule) branch.get(0).getRule();
        if (rule.isQuery()) return;
        List<Domain> remaining = new ArrayList<>(); List<Fact> premises = new ArrayList<>();
        for (Domain literal : branch) {
            if (!literal.isExcluded(mind)) { remaining.add(literal); continue; }
            Fact fact = Fact.of(literal, mind, true);
            if (fact == null) { capture.gaps.add("compound-residual:" + rule.getId()); return; }
            premises.add(fact);
        }
        if (remaining.isEmpty()) return;
        Clause conclusion = Clause.of(remaining, mind, true);
        // C-variable witnesses cannot be replaced by universal slots.
        if (conclusion == null) {
            capture.gaps.add("compound-residual:" + rule.getId()); return;
        }
        List<Support> supports = capture.sources(mind, rule.getId());
        if (supports.isEmpty()) capture.gaps.add("unlabelled-residual:" + rule.getId());
        capture.take(); capture.residuals.add(new Residual(conclusion, premises, supports));
    }
    static void stored(Mind mind, Domain producer, IRule result) throws Exception {
        ContextProductionCapture capture = ACTIVE.get();
        if (capture == null || !capture.visible(mind)
                || (mind.getQueryPass() != QueryPass.CHECK && mind.getQueryPass() != QueryPass.ACCEPT)) return;
        capture.check(); capture.take();
        Fact fact = Fact.of(((Rule) result).getDomain(), mind, false);
        if (fact == null) capture.gaps.add("nonordinary-materialization:" + producer.getRuleId());
        else capture.stored.add(new Materialization(producer.getRuleId(), fact));
    }
    public Snapshot snapshot() { check(); return new Snapshot(this); }
    @Override public void close() {
        if (closed) return;
        check(); closed = true; inputTarget = null; inputSource = null; inputSupports = null; inputClause = null; labels.clear();
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
