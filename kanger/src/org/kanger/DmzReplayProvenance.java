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
    private final Map<Binding, Integer> acceptedTargets = new IdentityHashMap<Binding, Integer>();
    private Mind boundary;
    private final Map<Integer, Frame> frames = new java.util.HashMap<Integer, Frame>();
    private Input input;
    private boolean closed;
    private DmzReplayProvenance(Mind boundary) { this.boundary = boundary; previous = ACTIVE.get(); ACTIVE.set(this); }
    public static DmzReplayProvenance begin() { return new DmzReplayProvenance(null); }
    void requireActiveJournal() {
        if (closed || Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Active owning replay journal required");
    }
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
    /** Provider-owned preparation boundary; acceptance is local to the prepared layer. */
    public static final class Preparation implements AutoCloseable {
        private final DmzReplayProvenance capture;
        private final Frame frame;
        private boolean settled;
        private Preparation(DmzReplayProvenance capture, Frame frame) {
            this.capture = capture; this.frame = frame;
        }
        private void check() {
            if (capture != null && (Thread.currentThread() != capture.owner || ACTIVE.get() != capture || capture.closed))
                throw new IllegalStateException("Preparation must settle in its owning journal scope");
        }
        public void accept() {
            check();
            if (settled) throw new IllegalStateException("Preparation already settled");
            settled = true;
            if (frame != null) frame.outcome = Outcome.ACCEPTED;
        }
        @Override public void close() {
            check();
            if (!settled) { settled = true; if (frame != null) frame.outcome = Outcome.DISCARDED; }
        }
    }
    public static Preparation preparation(Mind layer) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null) return new Preparation(null, null);
        if (layer == null) throw new IllegalArgumentException("Preparation layer required");
        int index = capture.identity(layer);
        if (capture.frames.containsKey(index)) throw new IllegalStateException("Layer already has observed lifecycle");
        Frame frame = new Frame(-1, true);
        capture.frames.put(index, frame);
        return new Preparation(capture, frame);
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
        if (frame != null) {
            frame.outcome = committed ? Outcome.ACCEPTED : Outcome.DISCARDED;
            if (committed) for (Map.Entry<Binding, Integer> entry : capture.acceptedTargets.entrySet())
                if (entry.getValue().equals(index)) entry.setValue(frame.parent);
        }
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
        if (target == null || source == null || authority == null || sourceRule < 0 || source.getCommune() != null
                || !source.getCommuneMembers().isEmpty())
            throw new IllegalArgumentException("Exact atomic source handle required");
        Input previous = capture.input;
        capture.input = new Input(source, sourceRule, authority, target);
        try { target.compileLine(statement, false, new LinkedList<ITerm>()); }
        finally { capture.input = previous; }
    }

    /** Explicitly sourced native acceptance; binds the primary input before analysis/linking.
     * No generated result is labelled. This alone is not a pre-operation certificate.
     */
    public static Boolean acceptRule(Mind target, IContextResults.Revision source, long sourceRule,
            Authority authority, String statement) throws Exception {
        if (source == null || authority == null || sourceRule < 0 || source.getCommune() != null
                || !source.getCommuneMembers().isEmpty() || statement == null || !statement.startsWith("!"))
            throw new IllegalArgumentException("Exact atomic source and acceptance statement required");
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null) return target.query(statement, null, false);
        Input previous = capture.input;
        Input accepted = new Input(source, sourceRule, authority, target, true);
        capture.input = accepted;
        try {
            Boolean result = target.query(statement, null, false);
            if (Boolean.TRUE.equals(result)) for (Binding binding : accepted.bound)
                capture.acceptedTargets.put(binding, capture.identity(target));
            return result;
        } finally { capture.input = previous; }
    }

    static void compiled(Mind target, Rule rule, boolean duplicate) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null || capture.input == null || rule.isQuery()) return;
        if (capture.input.target != target
                && (!capture.input.acceptance || target.getNext() != capture.input.target)) return;
        Integer index = capture.identity(target);
        // Direct replay to a declared/root boundary is already locally settled.
        if (!capture.frames.containsKey(index) && (target == capture.boundary
                || (capture.boundary == null && target.getNext() == null))) {
            Frame frame = new Frame(-1, true); frame.outcome = Outcome.ACCEPTED;
            capture.frames.put(index, frame);
        }
        Binding binding = new Binding(index, rule.getId(), capture.input, duplicate);
        capture.bindings.add(binding);
        if (capture.input.acceptance) capture.input.bound.add(binding);
    }
    public List<Binding> snapshot() { return Collections.unmodifiableList(new ArrayList<Binding>(bindings)); }
    /** Historical accepted occurrences visible through this Mind ancestry, keyed by native ID. */
    List<Binding> sources(Mind mind, long nativeRule) {
        if (closed || Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Source resolution requires active owning journal");
        java.util.Set<Integer> visible = new java.util.HashSet<Integer>();
        for (Mind level = mind; level != null; level = (Mind) level.getNext()) {
            Integer index = targets.get(level);
            if (index != null) visible.add(index);
        }
        List<Binding> result = new ArrayList<Binding>();
        for (Binding binding : bindings)
            if (binding.nativeRule == nativeRule && visibleBinding(binding, visible)
                    && outcome(binding.target) == Outcome.ACCEPTED) result.add(binding);
        return Collections.unmodifiableList(result);
    }
    private boolean visibleBinding(Binding binding, java.util.Set<Integer> visible) {
        Integer accepted = acceptedTargets.get(binding);
        return visible.contains(binding.target) || accepted != null && visible.contains(accepted);
    }

    static final class SourceObservation {
        final Binding binding;
        final Outcome outcome;
        SourceObservation(Binding binding, Outcome outcome) { this.binding = binding; this.outcome = outcome; }
    }
    /** Detached event-time metadata, including pending inputs; never accepted implicitly. */
    static List<SourceObservation> observedSources(Mind mind, long nativeRule) {
        return visibleSources(mind, nativeRule);
    }

    private static List<SourceObservation> visibleSources(Mind mind, Long nativeRule) {
        DmzReplayProvenance capture = ACTIVE.get();
        if (capture == null) return Collections.emptyList();
        java.util.Set<Integer> visible = new java.util.HashSet<Integer>();
        for (Mind level = mind; level != null; level = (Mind) level.getNext()) {
            Integer index = capture.targets.get(level);
            if (index != null) visible.add(index);
        }
        List<SourceObservation> result = new ArrayList<SourceObservation>();
        for (Binding binding : capture.bindings)
            if ((nativeRule == null || binding.nativeRule == nativeRule.longValue()) && capture.visibleBinding(binding, visible))
                result.add(new SourceObservation(binding, capture.outcome(binding.target)));
        return Collections.unmodifiableList(result);
    }

    /** Scope-local provenance guard; pending inputs remain pending. */
    static final class SourceCheckpoint {
        private final DmzReplayProvenance owner;
        private final List<SourceObservation> sources;
        private final java.util.Set<Long> liveRules = new java.util.HashSet<Long>();
        private SourceCheckpoint(DmzReplayProvenance owner, Mind target) {
            this.owner = owner; sources = visibleSources(target, null);
            for (org.kanger.interfaces.IRule rule : target.getRules())
                if (!rule.isDeleted(target)) liveRules.add(rule.getId());
        }
        boolean contains(Binding binding) {
            if (!liveRules.contains(binding.nativeRule)) return false;
            for (SourceObservation source : sources)
                if (source.binding == binding && (source.outcome == Outcome.ACCEPTED || source.outcome == Outcome.PENDING))
                    return true;
            return false;
        }
        boolean isCurrent(Mind target) {
            if (owner.closed || Thread.currentThread() != owner.owner || ACTIVE.get() != owner) return false;
            List<SourceObservation> current = visibleSources(target, null);
            if (sources.size() != current.size()) return false;
            for (int i = 0; i < sources.size(); ++i)
                if (sources.get(i).binding != current.get(i).binding || sources.get(i).outcome != current.get(i).outcome)
                    return false;
            return true;
        }
    }
    static SourceCheckpoint sourceCheckpoint(Mind target) {
        DmzReplayProvenance owner = ACTIVE.get();
        if (owner == null || owner.closed || Thread.currentThread() != owner.owner)
            throw new IllegalStateException("Active owning replay journal required");
        return new SourceCheckpoint(owner, target);
    }
    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Replay capture must close on owner thread in stack order");
        closed = true; input = null; boundary = null; targets.clear(); frames.clear(); acceptedTargets.clear();
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
    private static final class Input {
        final IContextResults.Revision source;
        final long rule;
        final Authority authority;
        final Mind target;
        final boolean acceptance;
        final List<Binding> bound = new ArrayList<Binding>();
        Input(IContextResults.Revision source, long rule, Authority authority, Mind target) {
            this(source, rule, authority, target, false);
        }
        Input(IContextResults.Revision source, long rule, Authority authority, Mind target, boolean acceptance) {
            this.source = source; this.rule = rule; this.authority = authority; this.target = target;
            this.acceptance = acceptance;
        }
    }
}
