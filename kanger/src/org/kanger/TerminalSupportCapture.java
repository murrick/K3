/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.units.Domain;

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

    private final TerminalSupportCapture previous;
    private final Thread owner;
    private final List<Event> events = new ArrayList<Event>();
    private boolean closed;

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
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
