/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.UUID;
import java.util.List;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Exact native IDs associate accepted replay alternatives with bounded applications. */
public final class DmzApplicationSourcesRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-app-sources-").toString());
        for (boolean reverse : new boolean[] {false, true}) run(reverse);
        generated();
        System.out.println("DMZ_APPLICATION_SOURCES_PASS checks=" + checks);
    }
    private static Mind root() throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static void run(boolean reverse) throws Exception {
        Mind q = root();
        IContextResults.Revision[] pins = {new IContextResults.Revision(UUID.randomUUID(), 1),
                new IContextResults.Revision(UUID.randomUUID(), 2), new IContextResults.Revision(UUID.randomUUID(), 3)};
        List<TerminalSupportCapture.ApplicationSources> detached;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            for (int visit = 0; visit < 3; ++visit) {
                int i = reverse ? 2 - visit : visit;
                DmzReplayProvenance.replayRule(q, pins[i], 10 + i,
                        i == 0 ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                        "!@x a(x) -> male(x);");
            }
            for (int visit = 0; visit < 3; ++visit) {
                int i = reverse ? 2 - visit : visit;
                DmzReplayProvenance.replayRule(q, pins[i], 20 + i,
                        i == 0 ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                        "!a(John);");
            }
            require(q.compile("!anchor(Trigger);", null, false), "native inference operation");
            detached = capture.sourceSnapshot(journal);
            require(!detached.isEmpty(), "native application observed");
            for (TerminalSupportCapture.ApplicationSources sources : detached) {
                require(sources.ruleSources.size() == 3, "three canonical production occurrences");
                require(sources.supportSources.size() == 1 && sources.supportSources.get(0).size() == 3,
                        "three primary premise occurrences");
                for (DmzReplayProvenance.Binding binding : sources.ruleSources)
                    require(binding.sourceRule >= 10 && binding.sourceRule <= 12, "exact production IDs");
                for (DmzReplayProvenance.Binding binding : sources.supportSources.get(0))
                    require(binding.sourceRule >= 20 && binding.sourceRule <= 22, "exact premise IDs");
                int local = 0;
                for (DmzReplayProvenance.Binding binding : sources.ruleSources)
                    if (binding.authority == DmzReplayProvenance.Authority.TARGET_Q) ++local;
                require(local == 1, "Q alternative preserved");
            }
            Mind unrelated = root();
            require(unrelated.compile("!@x a(x) -> male(x); !a(John);", null, false), "unrelated inference");
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal))
                if (sources.application.mind != detached.get(0).application.mind)
                    require(sources.ruleSources.isEmpty(), "unrelated Mind cannot inherit source labels");
        }
        require(!detached.get(0).ruleSources.isEmpty(), "detached associations survive scope close");
        try { detached.get(0).supportSources.get(0).clear(); throw new AssertionError("mutable"); }
        catch (UnsupportedOperationException expected) { ++checks; }
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
    private static void generated() throws Exception {
        Mind q = root();
        IContextResults.Revision pin = new IContextResults.Revision(UUID.randomUUID(), 7);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin();
                TerminalSupportCapture capture = TerminalSupportCapture.begin()) {
            DmzReplayProvenance.replayRule(q, pin, 1, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x a(x) -> male(x);");
            DmzReplayProvenance.replayRule(q, pin, 2, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x male(x) -> adult(x);");
            DmzReplayProvenance.replayRule(q, pin, 3, DmzReplayProvenance.Authority.EXTERNAL, "!a(John);");
            require(q.compile("!anchor(Trigger);", null, false), "recursive inference operation");
            boolean found = false;
            for (TerminalSupportCapture.ApplicationSources sources : capture.sourceSnapshot(journal))
                if ("!adult(John);".equals(sources.application.conclusion)) {
                    found = true;
                    require(sources.ruleSources.size() == 1 && sources.ruleSources.get(0).sourceRule == 2,
                            "second-step production exact source");
                    require(!sources.application.supports.get(0).primary && sources.supportSources.get(0).isEmpty(),
                            "generated support requires upstream proof, never borrowed primary label");
                }
            require(found, "generated support application observed");
        }
    }
}
