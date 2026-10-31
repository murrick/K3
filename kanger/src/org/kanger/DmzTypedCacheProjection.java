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
        return project(guard,proof,selected,target,budget,false,false,null,null,null);
    }
    static DmzTypedCacheProjection projectRecursive(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return project(guard,proof,selected,target,budget,true,false,null,null,null);
    }
    static DmzTypedCacheProjection projectPending(TechnicalMindTransaction owner,DmzGroundCollisionRestriction guard,
            DmzProvisionalCollisionProof proof,DmzProofWitnesses.Witness selected,int budget) throws Exception {
        Mind target=owner.pendingBoundary();
        return project(guard,proof,selected,target,budget,selected!=null && selected.step>=0
                && selected.premises.get(0).step>=0,true,null,null,null);
    }
    static DmzTypedCacheProjection projectCache(DmzCacheCollisionEvidence evidence,DmzProofWitnesses.Witness selected,
            Mind target,int budget) throws Exception {
        return project(null,null,selected,target,budget,selected!=null && selected.step>=0
                && selected.premises.get(0).step>=0,false,evidence,null,null);
    }
    static DmzTypedCacheProjection projectAccumulated(DmzCacheRestrictionSet set,Mind target,int budget) throws Exception {
        if(set==null || set.selections.isEmpty())throw new IllegalArgumentException("Nonempty cache restrictions required");
        DmzProofWitnesses.Witness first=set.selections.get(0);
        return project(null,null,first,target,budget,first.premises.get(0).step>=0,false,set.evidence,set,null);
    }
    static DmzTypedCacheProjection projectPath(DmzCacheRestrictionPath path,Mind target,int budget) throws Exception {
        DmzProofWitnesses.Witness first=path.selections.get(0);
        return project(null,null,first,target,budget,first.premises.get(0).step>=0,false,path.evidence.get(0),null,path);
    }
    private static DmzTypedCacheProjection project(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget,boolean recursive,boolean pending,DmzCacheCollisionEvidence evidence,DmzCacheRestrictionSet set,DmzCacheRestrictionPath path) throws Exception {
        if(target==null || (guard==null && evidence==null) || budget<1 || budget>10000)throw new IllegalArgumentException("Current bounded typed cache projection required");
        boolean eligible,truncated;
        if(path==null && evidence!=null && !evidence.isInitial())return refused(target,false,0);
        if(path!=null) {eligible=path.isCurrent(target);truncated=false;} else if(set!=null) {eligible=set.isCurrent(target);truncated=false;} else if(evidence!=null) {
            DmzCacheCollisionEvidence.Projection restriction=evidence.project(selected,target,budget);
            eligible=restriction.eligible;truncated=restriction.truncated;
        } else {
            DmzGroundCollisionRestriction.Result restriction=guard.project(proof,selected,target,budget);
            eligible=restriction.eligible;truncated=restriction.truncated;
        }
        if(!eligible || (!recursive && selected.premises.get(0).step>=0))return refused(target,truncated,0);
        if(recursive && !DmzProofWitnesses.NoGood.fromTyped(selected).recursiveWithin(8))
            return refused(target,false,0);
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,pending);
        if(!inventory.eligible || (!pending && inventory.provisional))return refused(target,inventory.truncated,0);
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        List<DmzProofWitnesses.NoGood> patterns=path!=null?path.patterns:set==null?Collections.singletonList(pattern):set.patterns;
        DmzSourcedProofGraph graph=inventory.graph.restrict(patterns);
        int remaining=budget;
        List<DmzProofWitnesses.Witness> selections=path!=null?path.selections:set!=null?set.selections:Collections.singletonList(selected);
        Map<Integer,Integer> roots=new LinkedHashMap<Integer,Integer>();
        for(DmzProofWitnesses.Witness choice:selections) {
            TerminalSupportCapture.Ground root=choice.graph.observed.nodes.get(choice.node).ground;int index=-1;
            for(int i=0;i<inventory.graph.observed.nodes.size();++i) {
                if(--remaining<0)return refused(target,true,budget);
                if(root.equivalent(inventory.graph.observed.nodes.get(i).ground))index=i;
            }
            if(index<0)return refused(target,false,budget-remaining);
            roots.put(index,roots.containsKey(index)?roots.get(index)+1:1);
        }
        DmzProofWitnesses.Result before,after;
        for(Map.Entry<Integer,Integer> root:roots.entrySet()) {
            if(remaining<=0)return refused(target,true,budget);
            before=DmzProofWitnesses.enumerate(inventory.graph,root.getKey(),remaining);
            if(before.truncated || (remaining-=before.created)<=0)return refused(target,true,budget);
            after=DmzProofWitnesses.enumerate(graph,root.getKey(),remaining);
            if(after.truncated || (remaining-=after.created)<=0)return refused(target,true,budget);
            if(before.witnesses.size()-after.witnesses.size()!=root.getValue())return refused(target,false,budget-remaining);
        }
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
