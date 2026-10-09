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
    private Mind boundary;
    private final Map<Integer, Frame> frames = new java.util.HashMap<Integer, Frame>();
    private Input input;
    private boolean closed;
    private DmzReplayProvenance(Mind boundary) { this.boundary = boundary; previous = ACTIVE.get(); ACTIVE.set(this); }
    public static DmzReplayProvenance begin() { return new DmzReplayProvenance(null); }
    public static DmzReplayProvenance begin(Mind boundary) {
        if (boundary == null) throw new IllegalArgumentException("Explicit boundary required");
        return new DmzReplayProvenance(boundary);
    }
    public enum Outcome { ACCEPTED, PENDING, DISCARDED, UNTRACKED }
    public static final class Settlement {
        public final List<Binding> accepted;
        public final int pending, discarded, untracked;
        private Settlement(List<Binding> accepted, int pending, int discarded, int untracked) {
            this.accepted = Collections.unmodifiableList(accepted);
            this.pending = pending; this.discarded = discarded; this.untracked = untracked;
        }
    }
    private static final class Frame {
        final int parent;
        final boolean boundary;
        Outcome outcome = Outcome.PENDING;
        Frame(int parent, boolean boundary) { this.parent = parent; this.boundary = boundary; }
    }
    private int identity(Mind mind) {
        Integer index = targets.get(mind);
        if (index == null) { index = targets.size(); targets.put(mind, index); }
        return index;
    }
    static void transactionOpened(Mind parent, Mind child) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture != null) capture.frames.put(capture.identity(child),
                new Frame(capture.identity(parent), parent == capture.boundary
                        || (capture.boundary == null && parent.getNext() == null)));
    }
    static void transactionSettled(Mind child, boolean committed) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null) return;
        Integer index = capture.targets.get(child);
        Frame frame = index == null ? null : capture.frames.get(index);
        if (frame != null) frame.outcome = committed ? Outcome.ACCEPTED : Outcome.DISCARDED;
    }
    public Settlement settlementSnapshot() {
        List<Binding> accepted = new ArrayList<Binding>();
        int pending = 0, discarded = 0, untracked = 0;
        for (Binding binding : bindings) {
            Outcome outcome = outcome(binding.target);
            if (outcome == Outcome.ACCEPTED) accepted.add(binding);
            else if (outcome == Outcome.PENDING) ++pending;
            else if (outcome == Outcome.DISCARDED) ++discarded;
            else ++untracked;
        }
        return new Settlement(accepted, pending, discarded, untracked);
    }
    private Outcome outcome(int target) {
        Frame frame = frames.get(target);
        if (frame == null) return Outcome.UNTRACKED;
        boolean pending = false;
        while (frame != null) {
            if (frame.outcome == Outcome.DISCARDED) return Outcome.DISCARDED;
            pending |= frame.outcome == Outcome.PENDING;
            Frame ancestor = frames.get(frame.parent);
            if (ancestor == null && !frame.boundary) return Outcome.UNTRACKED;
            frame = ancestor;
        }
        return pending ? Outcome.PENDING : Outcome.ACCEPTED;
    }

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
        Integer index = capture.identity(target);
        // Direct replay to a declared/root boundary is already locally settled.
        if (!capture.frames.containsKey(index) && (target == capture.boundary
                || (capture.boundary == null && target.getNext() == null))) {
            Frame frame = new Frame(-1, true); frame.outcome = Outcome.ACCEPTED;
            capture.frames.put(index, frame);
        }
        capture.bindings.add(new Binding(index, rule.getId(), capture.input, duplicate));
    }
    public List<Binding> snapshot() { return Collections.unmodifiableList(new ArrayList<Binding>(bindings)); }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Replay capture must close on owner thread in stack order");
        closed = true; input = null; boundary = null; targets.clear(); frames.clear();
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
