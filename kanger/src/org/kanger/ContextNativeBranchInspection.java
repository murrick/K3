/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Already-prepared whole supplied X inspection. No candidate append, settlement or writer capability. */
public final class ContextNativeBranchInspection {
    private ContextNativeBranchInspection(){}
    public enum Status { STABLE_SUPPLIED, REFUTED, INCOMPLETE }
    public static final class View {
        public final UUID context;public final long revision;public final String fingerprint;public final List<String> initialization;
        private View(ContextProductionInventory i){context=i.context;revision=i.revision;fingerprint=i.fingerprint;initialization=Collections.unmodifiableList(new ArrayList<>(i.initialization));}
    }
    public static final class CutView {
        public final String trustedGroup;public final List<View> members;
        private CutView(Snapshot cut){trustedGroup=cut.trustedGroup;List<View> views=new ArrayList<>();for(Snapshot member:cut.physicalMembers())views.add(new View(member.inventory));members=Collections.unmodifiableList(views);}
    }
    public static final class Opposition {
        public final Set<UUID> targetContexts;public final String trustedGroup;public final boolean xRefuted,foreignRefuted,initial;public final int round,depth;public final Set<Source> sources;
        Opposition(ContextUniversalNativeBranch.Rejection rejection,ContextUniversalFrozenConstraints.Proof proof){ContextWitnessResultTransport.Cut cut=rejection.cut;Set<UUID> contexts=new LinkedHashSet<>();if(cut.trustedGroup==null)contexts.add(cut.context);else contexts.addAll(cut.members.keySet());targetContexts=Collections.unmodifiableSet(contexts);trustedGroup=cut.trustedGroup;xRefuted=rejection.xRefuted;foreignRefuted=rejection.foreignRefuted;initial=rejection.phase==ContextUniversalNativeBranch.RejectionPhase.INITIAL;round=rejection.round;depth=proof.support.depth;sources=Collections.unmodifiableSet(new LinkedHashSet<>(proof.support.sources));}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final View proposed;public final List<CutView> foreign;public final List<Opposition> oppositions;
        private Result(Status status,String phase,String reason,Snapshot x,List<Snapshot> cuts,List<Opposition> oppositions){this.status=status;this.phase=phase;this.reason=reason;proposed=x==null?null:new View(x.inventory);List<CutView> views=new ArrayList<>();if(cuts!=null)for(Snapshot cut:cuts)views.add(new CutView(cut));foreign=Collections.unmodifiableList(views);this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));}
    }
    /** Detached snapshots are historical; an actual pending provider lease must guard its own call. */
    public static Result inspect(Snapshot proposed,List<Snapshot> foreign,ContextNativeCandidateInspection.Limits limits){
        String phase="preflight";
        try {
            if(proposed==null||foreign==null||limits==null)throw new IllegalArgumentException("Actual whole native X and explicit limits required");List<Snapshot> cuts=new ArrayList<>(foreign);ContextUniversalNativeBranch.Policy policy=limits.branchPolicy();
            phase="native-branch";ContextUniversalNativeBranch.Result branch=ContextUniversalNativeBranch.run(proposed,cuts,policy);
            if(branch.status==ContextUniversalNativeBranch.Status.INCOMPLETE)return new Result(Status.INCOMPLETE,phase,branch.phase+": "+branch.reason,null,null,Collections.emptyList());
            if(branch.status==ContextUniversalNativeBranch.Status.REFUTED){List<Opposition> oppositions=new ArrayList<>();for(ContextUniversalNativeBranch.Rejection r:branch.refutations.values())for(ContextUniversalFrozenConstraints.Proof proof:r.proofs)oppositions.add(new Opposition(r,proof));return new Result(Status.REFUTED,"sourced-refutation","Internally checked whole supplied-X refutation; no admission authority",proposed,cuts,oppositions);}
            phase="branch-audit";ContextUniversalNativeBranch.check(branch.output,proposed,cuts,policy,limits.audit,limits.capacity);
            return new Result(Status.STABLE_SUPPLIED,"historical-whole-supplied","Actual whole X/initial/native-minimum/chain receipts checked; native admission remains open",proposed,cuts,Collections.emptyList());
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,null,Collections.emptyList());}
    }
}
