/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;

/** Tests diagnostic attribution limits; source annotations are fixture-supplied. */
public final class DmzSourceCandidatesRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-sources-").toString());
        User user = new User(); new UDF().init(user); Mind q = new Mind(user); user.setCurrentMind(q);
        String rule = "!@x a(x) -> male(x);", fact = "!a(John);";
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        require(DmzSourceCandidates.capture(q, rule).status == DmzSourceCandidates.Status.UNRESOLVED,
                "missing provenance does not imply Q ownership");
        q.addContextRuleOrigin(rule, cause(a, 1, 10, false));
        q.addContextRuleOrigin(rule, cause(a, 1, 10, false));
        require(DmzSourceCandidates.capture(q, rule).sources.size() == 1, "exact annotation duplicates collapsed");
        q.addContextRuleOrigin(rule, cause(b, 1, 10, false));
        q.addContextRuleOrigin(rule, cause(a, 2, 10, false));
        q.addContextRuleOrigin(fact, cause(a, 1, 20, false));
        DmzSourceCandidates candidates = DmzSourceCandidates.capture(q, rule);
        require(candidates.status == DmzSourceCandidates.Status.MULTIPLE_CANDIDATES && candidates.sources.size() == 3,
                "same text retains distinct contexts and revisions");
        List<TerminalSupportCapture.Application> applications;
        try (TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            require(q.compile(rule + fact, null, false), "annotated native program");
            applications = capture.applicationSnapshot();
            TerminalSupportCapture.Checkpoint checkpoint = capture.checkpoint(q);
            require(capture.isCurrent(checkpoint, q), "fresh provenance checkpoint");
            q.addContextRuleOrigin(rule, cause(b, 2, 11, false));
            require(!capture.isCurrent(checkpoint, q), "source annotation change invalidates checkpoint");
        }
        boolean observed = false;
        for (TerminalSupportCapture.Application application : applications) {
            if (!"!male(John);".equals(application.conclusion)) continue;
            observed = true;
            require(application.sourceCandidates.sources.size() == 3, "application preserves all rule candidates");
            require(application.supports.size() == 1, "native one-premise support");
            require(application.supports.get(0).sourceCandidates.status == DmzSourceCandidates.Status.SINGLE_CANDIDATE,
                    "input annotation captured separately");
        }
        require(observed, "target application observed");
        q.addContextRuleOrigin(rule, cause(b, 2, 11, false));
        require(candidates.sources.size() == 3, "detached attribution unchanged by later annotation");
        try { candidates.sources.clear(); throw new AssertionError("mutable sources"); }
        catch (UnsupportedOperationException expected) { ++checks; }
        q.addContextRuleOrigin("unknown", new IContextResults.ProofCause(1, "", null, "", false,
                Collections.<IContextResults.ProofCause>emptyList()));
        require(DmzSourceCandidates.capture(q, "unknown").unsupported == 1, "missing context source remains unsupported");
        q.addContextRuleOrigin("hypothesis", cause(a, 1, 30, true));
        require(DmzSourceCandidates.capture(q, "hypothesis").sources.isEmpty(), "hypothesis cannot become authoritative source");
        require(Boolean.TRUE.equals(q.query("?male(John);", null, false)), "attribution does not change truth");
        System.out.println("DMZ_SOURCE_CANDIDATES_PASS checks=" + checks);
        System.out.println("DMZ_OPEN_REQUIREMENT exact_application_source_qualified=false text_index_ambiguous=true");
    }
    private static IContextResults.ProofCause cause(UUID source, long revision, long rule, boolean hypothesis) {
        return new IContextResults.ProofCause(rule, "fixture", null, "", false,
                Collections.<IContextResults.ProofCause>emptyList(), hypothesis, null, null,
                new IContextResults.Revision(source, revision));
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
