/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in native replay transport journal; attachment labels must come from a verified caller. */
public final class DmzReplayProvenance implements AutoCloseable {
    public enum Authority { TARGET_Q, EXTERNAL }
    public static final class Binding {
        public final int target;
        public final long nativeRule;
        public final UUID context;
        public final long revision, sourceRule;
        public final Authority authority;
        public final boolean duplicate;
        private Binding(int target, long nativeRule, Input input, boolean duplicate) {
            this.target = target; this.nativeRule = nativeRule;
            context = input.source.getContextId(); revision = input.source.getRevision();
            sourceRule = input.rule; authority = input.authority; this.duplicate = duplicate;
        }
    }
    private static final ThreadLocal<DmzReplayProvenance> ACTIVE = new ThreadLocal<DmzReplayProvenance>();
    private final DmzReplayProvenance previous;
    private final Thread owner = Thread.currentThread();
    private final Map<Mind, Integer> targets = new IdentityHashMap<Mind, Integer>();
    private final List<Binding> bindings = new ArrayList<Binding>();
    private Input input;
    private boolean closed;
    private DmzReplayProvenance() { previous = ACTIVE.get(); ACTIVE.set(this); }
    public static DmzReplayProvenance begin() { return new DmzReplayProvenance(); }

    public static void replayRule(Mind target, IContextResults.Revision source, long sourceRule,
            Authority authority, String statement) throws Exception {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null) { target.compileLine(statement, false, new LinkedList<ITerm>()); return; }
        if (source == null || authority == null || sourceRule < 0 || source.getCommune() != null
                || !source.getCommuneMembers().isEmpty())
            throw new IllegalArgumentException("Exact atomic source handle required");
        Input previous = capture.input;
        capture.input = new Input(source, sourceRule, authority, target);
        try { target.compileLine(statement, false, new LinkedList<ITerm>()); }
        finally { capture.input = previous; }
    }

    static void compiled(Mind target, Rule rule, boolean duplicate) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null || capture.input == null || capture.input.target != target || rule.isQuery()) return;
        Integer index = capture.targets.get(target);
        if (index == null) { index = capture.targets.size(); capture.targets.put(target, index); }
        capture.bindings.add(new Binding(index, rule.getId(), capture.input, duplicate));
    }
    public List<Binding> snapshot() { return Collections.unmodifiableList(new ArrayList<Binding>(bindings)); }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Replay capture must close on owner thread in stack order");
        closed = true; input = null; targets.clear();
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
    private static final class Input {
        final IContextResults.Revision source;
        final long rule;
        final Authority authority;
        final Mind target;
        Input(IContextResults.Revision source, long rule, Authority authority, Mind target) {
            this.source = source; this.rule = rule; this.authority = authority; this.target = target;
        }
    }
}
