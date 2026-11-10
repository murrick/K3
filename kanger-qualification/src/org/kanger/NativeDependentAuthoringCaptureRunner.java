/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

/** Candidate contract: checked proof survives rejection and can be replayed without its live view. */
public final class NativeDependentAuthoringCaptureRunner {
    private static int checks;
    private static final String UNIT="!@a $u @b rel(a,u,b);";
    private static Mind memory() throws Exception {
        User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);return mind;
    }
    private static void require(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("dependent-authoring-capture-").toString());
        for(boolean reverse:new boolean[]{false,true}) {
            Mind mind=memory();
            String prohibition="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
            String first=reverse?prohibition:UNIT,second=reverse?UNIT:prohibition;
            require(mind.compile(first+" !@x left(x)->right(x);",null,false),"initial rows accepted");
            String before=DmzObservationStateFingerprint.capture(mind);
            NativeDependentReplayCapsule.Saved saved;
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(!mind.compile(second,null,false),"authoring refuses the complete dependent conflict");
                require(capture.dependentSnapshot().size()==1 && capture.symbolicSnapshot().isEmpty(),
                        "one dependent packet, without replacing it by another symbolic shape");
                saved=capture.dependentSnapshot().get(0);
                boolean legacyRefused=false;
                try {capture.snapshot();}
                catch(IllegalStateException refused){legacyRefused=refused.getMessage().contains("Dependent collision");}
                require(legacyRefused,"legacy pair reader cannot hide dependent evidence");
            }
            require(before.equals(DmzObservationStateFingerprint.capture(mind)) && !mind.hasPendingTransactions(),
                    "rejected authoring preserves the original view");
            NativeDependentReplayCapsule.Limits limits=new NativeDependentReplayCapsule.Limits(262144,2,2,1000000,10000);
            boolean wrongFormat=false;
            try(NativeDependentReplayCapsule.Reopened ignored=NativeDependentReplayCapsule.reopen(saved.bytes(),saved.pin,limits)) { }
            catch(java.io.IOException refused){wrongFormat=true;}
            require(wrongFormat,"pair packet is not a whole-inventory capsule");
            try(NativeDependentReplayCapsule.Reopened replay=NativeDependentReplayCapsule.reopenPair(saved.bytes(),saved.pin,limits)) {
                replay.verify();
                NativeDependentMatchFrame.Materialization proof=replay.materialization;
                require(proof.remaining.isEmpty() && proof.support.depth==2 && proof.producers.size()==2
                        && proof.support.sources.size()==2 && replay.sources.size()==2,"complete two-step proof reopens");
                require(proof.producerSources.get(0).equals(proof.producerSources.get(1)),
                        "both applications refer to the same original producer");
                java.util.Set<String> origins=new HashSet<>();
                for(NativeDependentMatchFrame.Entry entry:replay.frame.entries())origins.add(entry.origin);
                require(origins.equals(new HashSet<>(Arrays.asList(first,second))),
                        "packet contains both actual declarations and excludes the unrelated rule");
            }

            Mind compatible=memory();
            String across="!@u ~(rel(1,u,1)&&rel(2,u,1));";
            require(compatible.compile(reverse?across:UNIT,null,false),"first compatible row accepted");
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(compatible.compile(reverse?UNIT:across,null,false),"different dependency coordinates are compatible");
                require(capture.dependentSnapshot().isEmpty() && capture.symbolicSnapshot().isEmpty()
                        && capture.snapshot().isEmpty(),"false legacy join leaves no conflict in any journal");
            }
        }
        Mind ordinary=memory();
        require(ordinary.compile("!p(1);",null,false),"first ordinary fact accepted");
        try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
            require(!ordinary.compile("!~p(1);",null,false),"ordinary conflict still refused");
            require(!capture.snapshot().isEmpty() && capture.dependentSnapshot().isEmpty(),"ordinary pair keeps its original format");
        }
        System.out.println("NATIVE_DEPENDENT_AUTHORING_CAPTURE_PASS checks="+checks);
    }
}
