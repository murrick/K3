/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Read-only supplied-snapshot diagnostic facade. No result/receipt can be submitted for writing. */
public final class ContextNativeCandidateInspection {
    private ContextNativeCandidateInspection(){}
    public enum Status { STABLE_SUPPLIED, REFUTED, INCOMPLETE }
    public static final class Limits {
        public final int rows,rounds,depth,preparation,events,work,audit,capacity;
        private final ContextUniversalNativeCandidate.Policy policy;
        /** Existing kernel16/local8/clause512/alternative256 policies; all work allowances stay separate. */
        public Limits(int rows,int rounds,int depth,int preparation,int events,int work,int audit,int capacity){
            if(audit<1)throw new IllegalArgumentException("Bounded final diagnostic audit required");
            ContextUniversalFrozenBranch.Limits kernel=new ContextUniversalFrozenBranch.Limits(16,depth,depth,512,256,work,65536,65536);
            ContextUniversalGroupedLocalRounds.Limits local=new ContextUniversalGroupedLocalRounds.Limits(8,16,512,512,256,depth,65536,work);
            policy=new ContextUniversalNativeCandidate.Policy(new ContextUniversalNativeBranch.Policy(kernel,local,rounds,work,capacity),rows,preparation,events);
            this.rows=rows;this.rounds=rounds;this.depth=depth;this.preparation=preparation;this.events=events;this.work=work;this.audit=audit;this.capacity=capacity;
        }
        ContextUniversalNativeBranch.Policy branchPolicy(){return policy.branch;}
    }
    public static final class Opposition {
        public final Set<UUID> targetContexts;public final String trustedGroup;
        public final boolean usesCandidate,xRefuted,foreignRefuted,initial;
        public final int round,depth;public final Source candidate;public final Set<Source> originalSources;
        private Opposition(ContextWitnessResultTransport.Cut cut,ContextUniversalNativeCandidate.Attribution a){
            Set<UUID> contexts=new LinkedHashSet<>();if(cut.trustedGroup==null)contexts.add(cut.context);else contexts.addAll(cut.members.keySet());targetContexts=Collections.unmodifiableSet(contexts);trustedGroup=cut.trustedGroup;
            usesCandidate=a.usesCandidate;xRefuted=a.rejection.xRefuted;foreignRefuted=a.rejection.foreignRefuted;initial=a.rejection.phase==ContextUniversalNativeBranch.RejectionPhase.INITIAL;round=a.rejection.round;depth=a.proof.support.depth;candidate=a.candidate;originalSources=Collections.unmodifiableSet(new LinkedHashSet<>(a.originalSources));
        }
    }
    public static final class Result {
        public final Status status;public final String phase,reason;
        /** Historical request metadata, not live-provider leases or native admission capability. */
        public final UUID baseContext;public final long baseRevision;public final String baseFingerprint;
        public final List<String> foreignFingerprints;public final Source candidate;public final List<Opposition> oppositions;
        private Result(Status status,String phase,String reason,Snapshot base,List<Snapshot> foreign,Source candidate,List<Opposition> oppositions){
            this.status=status;this.phase=phase;this.reason=reason;this.candidate=candidate;baseContext=base==null?null:base.inventory.context;baseRevision=base==null?0:base.inventory.revision;baseFingerprint=base==null?null:base.inventory.fingerprint;
            List<String> fingerprints=new ArrayList<>();if(foreign!=null)for(Snapshot cut:foreign)fingerprints.add(cut.inventory.fingerprint);foreignFingerprints=Collections.unmodifiableList(fingerprints);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    /** Pure detached snapshots alone give historical evidence; an owning provider must guard currentness. */
    public static Result inspect(Snapshot base,List<Snapshot> foreign,String statement,Limits limits){
        String phase="preflight";
        try {
            if(base==null||foreign==null||limits==null)throw new IllegalArgumentException("Actual native diagnostic request required");List<Snapshot> cuts=new ArrayList<>(foreign);
            phase="candidate";ContextUniversalNativeCandidate.Result trial=ContextUniversalNativeCandidate.qualify(base,cuts,statement,limits.policy);
            if(trial.status==ContextUniversalNativeBranch.Status.INCOMPLETE)return new Result(Status.INCOMPLETE,phase,trial.phase+": "+trial.reason,base,cuts,null,Collections.emptyList());
            if(trial.status==ContextUniversalNativeBranch.Status.REFUTED){List<Opposition> oppositions=new ArrayList<>();Source candidate=null;for(Map.Entry<ContextWitnessResultTransport.Cut,List<ContextUniversalNativeCandidate.Attribution>> e:trial.oppositions.entrySet())for(ContextUniversalNativeCandidate.Attribution a:e.getValue()){oppositions.add(new Opposition(e.getKey(),a));candidate=a.candidate;}return new Result(Status.REFUTED,"sourced-refutation","Internally checked supplied-branch refutation, explicit candidate causality; no write authority",base,cuts,candidate,oppositions);}
            phase="candidate-audit";ContextUniversalNativeCandidate.check(trial.output,base,cuts,statement,limits.policy,limits.audit,limits.capacity);
            return new Result(Status.STABLE_SUPPLIED,"historical-supplied","Fresh exact native candidate/branch receipts checked; full native/write eligibility remains open",base,cuts,trial.output.artifact.candidate,Collections.emptyList());
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,null,Collections.emptyList());}
    }
}
