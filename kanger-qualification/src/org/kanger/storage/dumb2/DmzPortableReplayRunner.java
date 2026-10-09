/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.kanger.DmzReplayProvenance;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Native portable replay transport fixture; not a pinned-storage acquisition qualification. */
public final class DmzPortableReplayRunner {
    private static int checks;
    private static final String RULE = "!@x a(x) -> male(x);";
    private static final String FACT = "!a(John);";
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-replay-").toString());
        Mind a = root(), b = root(), q = root();
        require(a.compile(RULE + FACT, null, false), "source A");
        require(b.compile(RULE + FACT, null, false), "source B");
        require(q.compile(RULE + FACT, null, false), "Q authored knowledge");
        IContextResults.Revision pinA = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision pinB = new IContextResults.Revision(UUID.randomUUID(), 2);
        IContextResults.Revision pinQ = new IContextResults.Revision(UUID.randomUUID(), 3);
        List<DmzReplayProvenance.Binding> bindings;
        try (DmzReplayProvenance capture = DmzReplayProvenance.begin()) {
            for (IRule candidate : q.getRules()) {
                if (!q.getRules().isGenerated(candidate) && !candidate.isDeleted(q))
                    DmzReplayProvenance.replayRule(q, pinQ, candidate.getId(),
                            DmzReplayProvenance.Authority.TARGET_Q, candidate.getOrigin());
            }
            PairQualification.PortableSource.capture(a).replay(q, pinA);
            PairQualification.PortableSource.capture(b).replay(q, pinB);
            bindings = capture.snapshot();
        }
        require(bindings.size() == 6, "two primary inputs from each of three sources");
        long canonical = id(q, RULE);
        int sources = 0;
        for (DmzReplayProvenance.Binding binding : bindings) {
            require(binding.duplicate, "existing canonical rule reused");
            if (binding.nativeRule != canonical) continue;
            ++sources;
            if (binding.context.equals(pinQ.getContextId())) {
                require(binding.authority == DmzReplayProvenance.Authority.TARGET_Q && binding.revision == 3,
                        "Q authority retained independently");
                require(binding.sourceRule == canonical, "Q source rule ID exact");
            } else if (binding.context.equals(pinA.getContextId())) {
                require(binding.authority == DmzReplayProvenance.Authority.EXTERNAL && binding.revision == 1,
                        "A exact source pin");
                require(binding.sourceRule == id(a, RULE), "A source rule ID exact");
            } else {
                require(binding.context.equals(pinB.getContextId()) && binding.revision == 2, "B exact source pin");
                require(binding.sourceRule == id(b, RULE), "B source rule ID exact");
            }
        }
        require(sources == 3, "canonical duplicate preserves all three source occurrences");
        try { bindings.clear(); throw new AssertionError("mutable bindings"); }
        catch (UnsupportedOperationException expected) { ++checks; }
        Mind fresh = root();
        try (DmzReplayProvenance capture = DmzReplayProvenance.begin()) {
            PairQualification.PortableSource.capture(a).replay(fresh, pinA);
            require(capture.snapshot().size() == 2, "only primary source inputs replayed");
            for (DmzReplayProvenance.Binding binding : capture.snapshot()) require(!binding.duplicate, "new canonical inputs bound");
            require(fresh.query("?male(John);", null, false) == Boolean.TRUE, "native replay inference unchanged");
            require(capture.snapshot().size() == 2, "inference cannot invent source replay bindings");
        }
        require(q.query("?male(John);", null, false) == Boolean.TRUE, "Q truth preserved");
        System.out.println("DMZ_PORTABLE_REPLAY_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static long id(Mind mind, String origin) throws Exception {
        for (IRule rule : mind.getRules()) if (origin.equals(rule.getOrigin())) return rule.getId();
        throw new AssertionError("missing native input " + origin);
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
