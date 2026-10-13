/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.interfaces.IRule;

/** Current read-only cache plan for one direct or bounded recursive typed collision restriction.
 * Native IDs are diagnostic addresses, never a deletion or continuation capability.
 */
final class DmzTypedCacheProjection {
    static final class Cache {
        final long nativeRule;
        final TerminalSupportCapture.Ground ground;
        final int before,after;
        Cache(long id,TerminalSupportCapture.Ground ground,int before,int after) {
            nativeRule=id;this.ground=ground;this.before=before;this.after=after;
        }
        boolean losesLastSupport() {return before>0 && after==0;}
    }
    final boolean eligible,truncated;
    final boolean complete=false;
    final List<Cache> caches;
    final DmzSourcedProofGraph graph;
    final int work;
    private final Mind target;
    private final String state;
    private final DmzReplayProvenance.SourceCheckpoint sources;
    private DmzTypedCacheProjection(Mind target,boolean eligible,boolean truncated,List<Cache> caches,
            DmzSourcedProofGraph graph,int work,String state,DmzReplayProvenance.SourceCheckpoint sources) {
        this.target=target;this.eligible=eligible;this.truncated=truncated;this.graph=graph;this.work=work;
        this.caches=Collections.unmodifiableList(new ArrayList<Cache>(caches));this.state=state;this.sources=sources;
    }
    static DmzTypedCacheProjection project(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return project(guard,proof,selected,target,budget,false);
    }
    static DmzTypedCacheProjection projectRecursive(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return project(guard,proof,selected,target,budget,true);
    }
    private static DmzTypedCacheProjection project(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget,boolean recursive) throws Exception {
        if(target==null || guard==null || budget<1 || budget>10000)throw new IllegalArgumentException("Current bounded typed cache projection required");
        DmzGroundCollisionRestriction.Result restriction=guard.project(proof,selected,target,budget);
        if(!restriction.eligible || (!recursive && selected.premises.get(0).step>=0))
            return refused(target,restriction.truncated,0);
        if(recursive && !DmzProofWitnesses.NoGood.fromTyped(selected).recursiveWithin(8))
            return refused(target,false,0);
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,false);
        if(!inventory.eligible || inventory.provisional)return refused(target,inventory.truncated,0);
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        DmzSourcedProofGraph graph=inventory.graph.restrict(Collections.singletonList(pattern));
        int remaining=budget;
        // Recognize the exact selected source/substitution in this current reconstruction,
        // independently of whether the selected ground has been materialized.
        TerminalSupportCapture.Ground root=selected.graph.observed.nodes.get(selected.node).ground;
        int index=-1;
        for(int i=0;i<inventory.graph.observed.nodes.size();++i) {
            if(--remaining<0)return refused(target,true,budget);
            if(root.equivalent(inventory.graph.observed.nodes.get(i).ground))index=i;
        }
        if(index<0)return refused(target,false,budget-remaining);
        if(remaining<=0)return refused(target,true,budget);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(inventory.graph,index,remaining);
        if(before.truncated || (remaining-=before.created)<=0)return refused(target,true,budget);
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(graph,index,remaining);
        if(after.truncated || (remaining-=after.created)<=0)return refused(target,true,budget);
        if(before.witnesses.size()-after.witnesses.size()!=1)return refused(target,false,budget-remaining);
        List<Cache> caches=new ArrayList<Cache>();
        for(IRule rule:target.getRules()) {
            if(--remaining<0)return refused(target,true,budget);
            if(rule.isDeleted(target) || !target.getRules().isGenerated(rule))continue;
            TerminalSupportCapture.Ground atom=DmzGroundIdentity.capture(rule,target).ground();
            int node=-1;
            for(int i=0;i<inventory.graph.observed.nodes.size();++i) {
                if(--remaining<0)return refused(target,true,budget);
                if(atom.equivalent(inventory.graph.observed.nodes.get(i).ground))node=i;
            }
            if(node<0)return refused(target,false,budget-remaining);
            if(remaining<=0)return refused(target,true,budget);
            before=DmzProofWitnesses.enumerate(inventory.graph,node,remaining);
            if(before.truncated || (remaining-=before.created)<=0)return refused(target,true,budget);
            after=DmzProofWitnesses.enumerate(graph,node,remaining);
            if(after.truncated || (remaining-=after.created)<0)return refused(target,true,budget);
            if(before.witnesses.isEmpty() || after.witnesses.size()>before.witnesses.size())return refused(target,false,budget-remaining);
            // Native generated materializations can also carry asserted source labels.
            // A plan may never retract such a primary occurrence.
            List<DmzReplayProvenance.SourceObservation> labels=DmzReplayProvenance.observedSources(target,rule.getId());
            if((remaining-=labels.size())<0)return refused(target,true,budget);
            if(after.witnesses.isEmpty() && !labels.isEmpty())return refused(target,false,budget-remaining);
            caches.add(new Cache(rule.getId(),atom,before.witnesses.size(),after.witnesses.size()));
        }
        if(!inventory.isCurrent(target))return refused(target,false,budget-remaining);
        return new DmzTypedCacheProjection(target,true,false,caches,graph,budget-remaining,
                DmzObservationStateFingerprint.capture(target),DmzReplayProvenance.sourceCheckpoint(target));
    }
    boolean isCurrent(Mind candidate) throws Exception {
        return eligible && candidate==target && DmzTerminalRestriction.activeNoGoods(candidate).isEmpty()
                && sources.isCurrent(candidate) && state.equals(DmzObservationStateFingerprint.capture(candidate));
    }
    private static DmzTypedCacheProjection refused(Mind target,boolean truncated,int work) {
        return new DmzTypedCacheProjection(target,false,truncated,Collections.<Cache>emptyList(),null,work,null,null);
    }
}
