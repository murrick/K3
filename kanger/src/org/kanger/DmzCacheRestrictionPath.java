/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Immutable choices connected by actual owned rejected continuations. */
final class DmzCacheRestrictionPath {
    final List<DmzCacheCollisionEvidence> evidence;
    final List<DmzProofWitnesses.Witness> selections;
    final List<DmzProofWitnesses.NoGood> patterns;
    private DmzCacheRestrictionPath(List<DmzCacheCollisionEvidence> evidence,List<DmzProofWitnesses.Witness> selections,
            List<DmzProofWitnesses.NoGood> patterns) {
        this.evidence=Collections.unmodifiableList(new ArrayList<DmzCacheCollisionEvidence>(evidence));
        this.selections=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(selections));
        this.patterns=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.NoGood>(patterns));
    }
    static DmzCacheRestrictionPath start(DmzCacheCollisionEvidence evidence,DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(evidence==null || !evidence.isInitial() || !evidence.project(selected,target,budget).eligible)
            throw new IllegalStateException("Current initial cache choice required");
        return new DmzCacheRestrictionPath(Collections.singletonList(evidence),Collections.singletonList(selected),
                Collections.singletonList(DmzProofWitnesses.NoGood.fromTyped(selected)));
    }
    DmzCacheRestrictionPath append(DmzCacheCollisionEvidence next,DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(!isCurrent(target) || selections.size()>=128 || next==null || !next.followsSelections(selections,target)
                || !next.project(selected,target,budget).eligible)
            throw new IllegalStateException("Current successor of this exact restriction prefix required");
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,false);
        if(!inventory.eligible || inventory.provisional)throw new IllegalStateException("Current settled typed inventory required");
        TerminalSupportCapture.Ground root=selected.graph.observed.nodes.get(selected.node).ground;int index=-1,remaining=budget;
        for(int i=0;i<inventory.graph.observed.nodes.size();++i) {
            if(--remaining<0)throw new IllegalStateException("Successor root budget exceeded");
            if(root.equivalent(inventory.graph.observed.nodes.get(i).ground))index=i;
        }
        if(index<0 || remaining<1)throw new IllegalStateException("Current successor root required");
        DmzSourcedProofGraph current=inventory.graph.restrict(patterns);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(current,index,remaining);
        if(before.truncated || (remaining-=before.created)<1)throw new IllegalStateException("Successor witness budget exceeded");
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(current.restrict(Collections.singletonList(pattern)),index,remaining);
        if(after.truncated)throw new IllegalStateException("Successor witness budget exceeded");
        if(before.witnesses.size()-after.witnesses.size()!=1)throw new IllegalStateException("One new remaining successor choice required");
        if(!inventory.isCurrent(target))throw new IllegalStateException("Successor inventory changed");
        List<DmzCacheCollisionEvidence> receipts=new ArrayList<DmzCacheCollisionEvidence>(evidence);receipts.add(next);
        List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>(selections);choices.add(selected);
        List<DmzProofWitnesses.NoGood> rules=new ArrayList<DmzProofWitnesses.NoGood>(patterns);rules.add(pattern);
        return new DmzCacheRestrictionPath(receipts,choices,rules);
    }
    boolean isCurrent(Mind target) throws Exception {
        for(DmzCacheCollisionEvidence receipt:evidence)if(!receipt.isCurrent(target))return false;
        return true;
    }
}
