/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Rule;
import org.kanger.interfaces.IArgument;
import org.kanger.enums.ArgumentType;
import java.util.IdentityHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Experimental operation-local journal of terminal inference candidates.
 * Records before stored-result suppression and cause optimization. Each
 * raw event preserves its donor group without binding certification. The
 * bounded application surface revalidates ordinary live ground premises;
 * neither surface proves completeness of all logical derivations.
 * This is observational only and cannot authorize DMZ branch restrictions.
 */
final class TerminalSupportCapture implements AutoCloseable {
    private static final ThreadLocal<TerminalSupportCapture> ACTIVE =
            new ThreadLocal<TerminalSupportCapture>();

    static final class Event {
        final String conclusion;
        final String ruleOrigin;
        final List<String> donors;
        Event(String conclusion, String ruleOrigin, List<String> donors) {
            this.conclusion = conclusion;
            this.ruleOrigin = ruleOrigin;
            List<String> sorted = new ArrayList<String>(donors);
            Collections.sort(sorted);
            this.donors = Collections.unmodifiableList(sorted);
        }
    }

    /** Partial substitution observed at one premise match, not a complete application. */
    static final class Binding {
        final int variable;
        final String name;
        final String rendering;
        final SemanticTermSnapshot value;
        Binding(int variable, String name, String rendering, SemanticTermSnapshot value) {
            this.variable = variable;
            this.name = name;
            this.rendering = rendering;
            this.value = value;
        }
    }

    static final class Ground {
        final String predicate;
        final boolean sign;
        final List<SemanticTermSnapshot> arguments;
        Ground(String predicate, boolean sign, List<SemanticTermSnapshot> arguments) {
            this.predicate = predicate; this.sign = sign;
            this.arguments = Collections.unmodifiableList(arguments);
        }
        static Ground capture(Solve solve, Mind mind) throws Exception {
            List<SemanticTermSnapshot> arguments = new ArrayList<SemanticTermSnapshot>();
            for (IArgument argument : solve.getArguments()) {
                if (!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))) return null;
                arguments.add(SemanticTermSnapshot.capture(argument.getValue(mind)));
            }
            return new Ground(solve.getPredicate(mind).getName(mind), solve.isAntc(), arguments);
        }
        boolean equivalent(Ground other) {
            if (other == null || sign != other.sign || !predicate.equals(other.predicate)
                    || arguments.size() != other.arguments.size()) return false;
            for (int i = 0; i < arguments.size(); ++i)
                if (!arguments.get(i).semanticallyEquals(other.arguments.get(i))) return false;
            return true;
        }
    }

    static final class Match {
        final int mind;
        final int premise;
        final String ruleOrigin;
        final String donor;
        final boolean result;
        private final Ground ground;
        final List<Binding> bindings;
        Match(int mind, int premise, String ruleOrigin, String donor, Ground ground, boolean result, List<Binding> bindings) {
            this.mind = mind;
            this.premise = premise;
            this.ruleOrigin = ruleOrigin;
            this.donor = donor;
            this.result = result;
            this.ground = ground;
            this.bindings = Collections.unmodifiableList(new ArrayList<Binding>(bindings));
        }
    }

    static final class Support {
        final int premise;
        final int evidence;
        final String donor;
        final Ground ground;
        final boolean primary;
        Support(int premise, int evidence, String donor, Ground ground, boolean primary) {
            this.premise = premise; this.evidence = evidence; this.donor = donor;
            this.ground = ground; this.primary = primary;
        }
    }

    /** Bounded one-step reconstruction; not a recursive or cross-context certificate. */
    static final class Application {
        final int mind;
        final int rule;
        final String ruleOrigin;
        final String conclusion;
        final Ground ground;
        final List<Binding> bindings;
        final List<Support> supports;
        Application(int mind, int rule, String origin, String conclusion, Ground ground,
                List<Binding> bindings, List<Support> supports) {
            this.mind = mind; this.rule = rule; this.ruleOrigin = origin; this.conclusion = conclusion;
            this.ground = ground;
            this.bindings = Collections.unmodifiableList(new ArrayList<Binding>(bindings));
            this.supports = Collections.unmodifiableList(new ArrayList<Support>(supports));
        }
    }

    private final TerminalSupportCapture previous;
    private final Thread owner;
    private final List<Event> events = new ArrayList<Event>();
    private final List<Application> applications = new ArrayList<Application>();
    private final List<String> applicationGaps = new ArrayList<String>();
    private final List<Match> matches = new ArrayList<Match>();
    private final Map<Object, Integer> identities = new IdentityHashMap<Object, Integer>();
    private boolean closed;

    private int identity(Object object) {
        Integer index = identities.get(object);
        if (index == null) { index = identities.size(); identities.put(object, index); }
        return index;
    }

    static void recordMatch(Mind mind, Domain premise, Domain donor,
            List<TValue> substitution, boolean result) throws Exception {
        TerminalSupportCapture capture = ACTIVE.get();
        if (capture == null) return;
        List<Binding> bindings = new ArrayList<Binding>();
        for (TValue candidate : substitution) {
            TVariable variable = candidate.getTVar(mind);
            if (!SemanticTermSnapshot.isOrdinaryValue(candidate.getValue(mind))) return;
            bindings.add(new Binding(capture.identity(variable), variable.getName(mind).toString(),
                    candidate.getValue(mind).toString(), SemanticTermSnapshot.capture(candidate.getValue(mind))));
        }
        if (bindings.isEmpty()) return;
        IRule rule = premise.getRule();
        Solve donorSolve = new Solve(donor.getPredicate(), donor.isAntc(),
                donor.getArguments().convertBase(mind));
        Ground ground = Ground.capture(donorSolve, mind);
        if (ground == null) return;
        capture.matches.add(new Match(capture.identity(mind), capture.identity(premise),
                rule == null || rule.getOrigin() == null ? "" : rule.getOrigin(),
                donorSolve.toString(mind), ground, result, bindings));
    }

    List<Match> matchSnapshot() {
        return Collections.unmodifiableList(new ArrayList<Match>(matches));
    }

    static void recordApplication(Mind mind, Domain conclusion, List<Domain> tree,
            Collection<TVariable> variables) throws Exception {
        TerminalSupportCapture capture = ACTIVE.get();
        if (capture == null) return;
        String gap = capture.assemble(mind, conclusion, tree, variables);
        if (gap != null) capture.applicationGaps.add(gap);
    }

    private String assemble(Mind mind, Domain conclusion, List<Domain> tree,
            Collection<TVariable> variables) throws Exception {
        Rule rule = (Rule) conclusion.getRule();
        if (rule == null || rule.isQuery() || rule.getTree().size() != 1 || tree.size() < 2)
            return "unsupported-rule-shape";
        List<Binding> bindings = new ArrayList<Binding>();
        for (TVariable variable : variables) {
            TValue current = variable.getCurrent();
            if (current == null || !SemanticTermSnapshot.isOrdinaryValue(current.getValue(mind)))
                return "incomplete-or-nonordinary-binding";
            bindings.add(new Binding(identity(variable), variable.getName(mind).toString(),
                    current.getValue(mind).toString(), SemanticTermSnapshot.capture(current.getValue(mind))));
        }
        for (Domain literal : tree) {
            if (literal.isSystem(mind) || literal.isCalculated(mind) || !literal.isComplete())
                return "unsupported-literal";
            for (IArgument argument : literal.getArguments()) {
                if (argument.getType() == ArgumentType.FUNCTION
                        || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))
                    return "unsupported-argument";
            }
        }
        List<Support> supports = new ArrayList<Support>();
        for (Domain premise : tree) {
            if (premise == conclusion) continue;
            if (!premise.isExcluded(mind)) return "premise-not-excluded";
            Solve wanted = new Solve(premise.getPredicate(), !premise.isAntc(),
                    premise.getArguments().convertBase(mind));
            IRule evidence = mind.getRules().find(wanted);
            if (evidence == null || evidence.isDeleted(mind) || evidence.isQuery()
                    || ((Rule) evidence).getTree().size() != 1
                    || ((Rule) evidence).getTree().get(0).size() != 1)
                return "missing-live-ground-evidence";
            String donor = wanted.toString(mind);
            Ground ground = Ground.capture(wanted, mind);
            boolean witnessed = false;
            for (Match match : matches) {
                if (match.mind != identity(mind) || match.premise != identity(premise)
                        || !match.ground.equivalent(ground)) continue;
                boolean compatible = true;
                for (Binding partial : match.bindings) {
                    boolean found = false;
                    for (Binding full : bindings) {
                        if (full.variable == partial.variable && full.value.semanticallyEquals(partial.value)) {
                            found = true; break;
                        }
                    }
                    if (!found) { compatible = false; break; }
                }
                if (compatible) { witnessed = true; break; }
            }
            if (!witnessed) return "missing-compatible-premise-match";
            supports.add(new Support(identity(premise), identity(evidence), donor, ground,
                    !mind.getRules().isGenerated(evidence)));
        }
        Solve result = new Solve(conclusion.getPredicate(), conclusion.isAntc(),
                conclusion.getArguments().convertBase(mind));
        applications.add(new Application(identity(mind), identity(rule), rule.getOrigin(),
                result.toString(mind), Ground.capture(result, mind), bindings, supports));
        return null;
    }

    List<Application> applicationSnapshot() {
        return Collections.unmodifiableList(new ArrayList<Application>(applications));
    }

    List<String> applicationGapSnapshot() {
        return Collections.unmodifiableList(new ArrayList<String>(applicationGaps));
    }

    private TerminalSupportCapture() {
        owner = Thread.currentThread();
        previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    static TerminalSupportCapture begin() { return new TerminalSupportCapture(); }

    static void record(Mind mind, Domain domain, Collection<Cause> causes) throws Exception {
        TerminalSupportCapture capture = ACTIVE.get();
        if (capture == null || causes == null || causes.isEmpty() || !domain.isComplete()) return;
        List<String> donors = new ArrayList<String>();
        for (Cause cause : causes) donors.add(cause.getDonor().toString(mind));
        IRule rule = domain.getRule();
        String conclusion = new Solve(domain.getPredicate(), domain.isAntc(),
                domain.getArguments().convertBase(mind)).toString(mind);
        capture.events.add(new Event(conclusion,
                rule == null || rule.getOrigin() == null ? "" : rule.getOrigin(), donors));
    }

    List<Event> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Event>(events));
    }

    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || ACTIVE.get() != this) {
            throw new IllegalStateException("Terminal capture must close on owner thread in stack order");
        }
        closed = true;
        identities.clear();
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
