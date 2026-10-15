/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.QueryPass;

/** Negative owner/instance controls for actual ordinary native observations. */
public final class ContextGroundApplicationVerifierRunner {
    private static int checks;
    private static void require(boolean value,String reason) { ++checks; if (!value) throw new AssertionError(reason); }
    private static void refused(ContextWitnessPrimaryStepVerifier.Scope scope,Application application,String why) {
        boolean refused = false;
        try { ContextGroundApplicationVerifier.application(scope,application,() -> {}); }
        catch (IllegalStateException expected) { refused = true; }
        require(refused,why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-application-").toString());
            User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
            require(mind.compile("!scalar(1);",null,false),"ordinary seed fixture"); mind.setQueryPass(QueryPass.ACCEPT);
            Source source = new Source(new UUID(0,1),1,1,false,"exact-cut");
            Snapshot snapshot;
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
                capture.replay(mind,source,"!@x scalar(x)->warm(x);"); mind.link(null,false); snapshot = capture.snapshot();
            }
            ContextWitnessPrimaryStepVerifier.Scope scope = ContextWitnessPrimaryStepVerifier.scope(Collections.emptyList(),snapshot,() -> {},true);
            Application observation = snapshot.applications.stream().filter(a -> a.conclusion.predicate.equals("warm")).findFirst().orElseThrow(() -> new AssertionError("actual application missing"));
            ContextGroundApplicationVerifier.application(scope,observation,() -> {}); require(true,"actual ordinary application authenticates");
            refused(new ContextWitnessPrimaryStepVerifier.Scope(Collections.emptyMap(),scope.shapes),observation,"missing source ownership refuses");
            Map<Long,Set<Source>> forged = new HashMap<>(scope.producers);
            forged.put(observation.nativeRule,Collections.singleton(new Source(source.context,2,1,false,"exact-cut")));
            refused(new ContextWitnessPrimaryStepVerifier.Scope(forged,scope.shapes),observation,"same context but wrong revision refuses");
            refused(new ContextWitnessPrimaryStepVerifier.Scope(scope.producers,Collections.emptyMap()),observation,"producer label without whole clause instance refuses");
            User otherUser = new User(); Mind other = new Mind(otherUser); otherUser.setCurrentMind(other);
            Snapshot wrong;
            try (ContextProductionCapture capture = ContextProductionCapture.begin(other,10000)) {
                capture.replay(other,source,"!@x scalar(x)->warm(2);"); wrong = capture.snapshot();
            }
            Map<Long,List<Clause>> shapes = new HashMap<>(); shapes.put(observation.nativeRule,wrong.inputs.get(0).shapes);
            refused(new ContextWitnessPrimaryStepVerifier.Scope(scope.producers,shapes),observation,"matching predicate and owner cannot replace fixed conclusion value");
            shapes.put(observation.nativeRule,new ArrayList<>(Collections.singletonList(new Clause(Arrays.asList(observation.conclusion,observation.premises.get(0))))));
            refused(new ContextWitnessPrimaryStepVerifier.Scope(scope.producers,shapes),observation,"wrong premise polarity refuses");
            boolean stopped = false;
            try { ContextGroundApplicationVerifier.application(scope,observation,() -> { throw new IllegalStateException("work"); }); }
            catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"work exhaustion never certifies an application");
            System.out.println("CONTEXT_GROUND_APPLICATION_VERIFIER_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
