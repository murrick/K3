/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.udf.UDF;

/** Native rejected operation retains exact event-time source metadata before rollback. */
public final class DmzNativeCollisionSourcesRunner {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-native-collision-").toString());
        run(false); run(true);
        System.out.println("DMZ_NATIVE_COLLISION_SOURCES_PASS checks=" + checks);
    }
    private static void run(boolean reverse) throws Exception {
        User user = new User(); new UDF().init(user);
        Mind q = new Mind(user); user.setCurrentMind(q);
        IContextResults.Revision a = new IContextResults.Revision(UUID.randomUUID(), 1);
        IContextResults.Revision b = new IContextResults.Revision(UUID.randomUUID(), 2);
        List<CollisionProofCapture.Conflict> detached;
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            DmzReplayProvenance.replayRule(q, a, 10, DmzReplayProvenance.Authority.TARGET_Q,
                    "!@x a(x) -> male(x);");
            DmzReplayProvenance.replayRule(q, b, 20, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x b(x) -> ~male(x);");
            try (TechnicalMindTransaction outer = TechnicalMindTransaction.begin(q);
                    CollisionProofCapture capture = CollisionProofCapture.begin();
                    TerminalSupportCapture supportCapture = TerminalSupportCapture.begin(outer.mind())) {
                for (int visit = 0; visit < 2; ++visit) {
                    boolean positive = reverse ? visit == 1 : visit == 0;
                    DmzReplayProvenance.replayRule(outer.mind(), positive ? a : b, positive ? 11 : 21,
                            positive ? DmzReplayProvenance.Authority.TARGET_Q : DmzReplayProvenance.Authority.EXTERNAL,
                            positive ? "!a(John);" : "!b(John);");
                }
                require(!outer.mind().compile("!anchor(Trigger);", null, false), "native conflicting operation rejected");
                detached = capture.snapshot();
                require(!detached.isEmpty(), "event captured before rollback");
                boolean pendingFact = false, acceptedRule = false;
                for (CollisionProofCapture.Conflict conflict : detached) {
                    require(conflict.observations.captureActive, "provisional capture explicitly active");
                    require(!conflict.observations.applications.isEmpty(), "native event retains bounded applications");
                    for (TerminalSupportCapture.ProvisionalApplication application : conflict.observations.applications) {
                        require(application.outcome == TerminalSupportCapture.Outcome.PENDING,
                                "event application is provisional, never accepted before operation settlement");
                        require(!application.ruleSources.isEmpty(), "event application rule source retained");
                    }
                    CollisionProofCapture.Node left = conflict.left.nodes.get(conflict.left.root);
                    CollisionProofCapture.Node right = conflict.right.nodes.get(conflict.right.root);
                    require(left.ground != null && right.ground != null, "typed ground collision roots");
                    require(left.ground.sign != right.ground.sign && left.ground.predicate.equals(right.ground.predicate),
                            "native opposite roots retained");
                    boolean observedLeft = false, observedRight = false;
                    for (TerminalSupportCapture.ProvisionalApplication application : conflict.observations.applications) {
                        observedLeft |= left.ground.equivalent(application.application.ground);
                        observedRight |= right.ground.equivalent(application.application.ground);
                    }
                    require(observedLeft && observedRight, "both native roots have provisional bounded applications");
                    for (CollisionProofCapture.Graph graph : new CollisionProofCapture.Graph[] {conflict.left, conflict.right})
                        for (CollisionProofCapture.Node node : graph.nodes) {
                            if (node.generated) require(node.sources.isEmpty(), "generated node not assigned primary replay authorship");
                            for (DmzReplayProvenance.SourceObservation source : node.sources) {
                                require(source.binding.nativeRule == node.nativeRule, "exact event native ID");
                                require(source.binding.context.equals(a.getContextId()) || source.binding.context.equals(b.getContextId()),
                                        "source context preserved");
                                pendingFact |= source.outcome == DmzReplayProvenance.Outcome.PENDING &&
                                        (source.binding.sourceRule == 11 || source.binding.sourceRule == 21);
                                acceptedRule |= source.outcome == DmzReplayProvenance.Outcome.ACCEPTED &&
                                        (source.binding.sourceRule == 10 || source.binding.sourceRule == 20);
                            }
                        }
                }
                require(pendingFact && acceptedRule, "event distinguishes pending facts and accepted productions");
                require(supportCapture.settlementSnapshot().accepted.isEmpty(), "rejected operation cannot promote provisional proofs");
                outer.rollback();
                require(journal.settlementSnapshot().discarded == 2, "outer rollback discards both fact occurrences");
            }
        }
        require(!detached.isEmpty(), "event remains detached after scopes close");
        require(!Boolean.TRUE.equals(q.query("?male(John);", null, false)), "rejected branch did not alter Q");
    }
    private static void require(boolean condition, String message) {
        ++checks; if (!condition) throw new AssertionError(message);
    }
}
