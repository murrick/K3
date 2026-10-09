/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import java.util.IdentityHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Experimental operation-local journal of terminal inference candidates.
 * Records before stored-result suppression and cause optimization. Each
 * event preserves its donor group; it does not certify each donor against
 * current bindings or prove completeness of all logical derivations.
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

    static final class Match {
        final int premise;
        final String ruleOrigin;
        final String donor;
        final boolean result;
        final List<Binding> bindings;
        Match(int premise, String ruleOrigin, String donor, boolean result, List<Binding> bindings) {
            this.premise = premise;
            this.ruleOrigin = ruleOrigin;
            this.donor = donor;
            this.result = result;
            this.bindings = Collections.unmodifiableList(new ArrayList<Binding>(bindings));
        }
    }

    private final TerminalSupportCapture previous;
    private final Thread owner;
    private final List<Event> events = new ArrayList<Event>();
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
        capture.matches.add(new Match(capture.identity(premise),
                rule == null || rule.getOrigin() == null ? "" : rule.getOrigin(),
                new Solve(donor.getPredicate(), donor.isAntc(),
                        donor.getArguments().convertBase(mind)).toString(mind), result, bindings));
    }

    List<Match> matchSnapshot() {
        return Collections.unmodifiableList(new ArrayList<Match>(matches));
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
