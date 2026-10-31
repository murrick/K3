/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Immutable accumulated exact choices for one covered cache-conflict root. */
final class DmzCacheRestrictionSet {
    final DmzCacheCollisionEvidence evidence;
    final List<DmzProofWitnesses.Witness> selections;
    final List<DmzProofWitnesses.NoGood> patterns;
    final DmzSourcedProofGraph graph;
    private DmzCacheRestrictionSet(DmzCacheCollisionEvidence evidence,List<DmzProofWitnesses.Witness> selections,
            List<DmzProofWitnesses.NoGood> patterns,DmzSourcedProofGraph graph) {
        this.evidence=evidence;this.graph=graph;
        this.selections=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(selections));
        this.patterns=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.NoGood>(patterns));
    }
    static DmzCacheRestrictionSet begin(DmzCacheCollisionEvidence evidence,Mind target) throws Exception {
        if(evidence==null || !evidence.isInitial() || !evidence.isCurrent(target))throw new IllegalStateException("Current cache evidence required");
        return new DmzCacheRestrictionSet(evidence,Collections.<DmzProofWitnesses.Witness>emptyList(),Collections.<DmzProofWitnesses.NoGood>emptyList(),evidence.graph);
    }
    DmzCacheRestrictionSet append(DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(!isCurrent(target) || selections.size()>=128 || !evidence.project(selected,target,budget).eligible)
            throw new IllegalStateException("Current bounded cache choice required");
        if(!selections.isEmpty() && !graph.observed.nodes.get(selections.get(0).node).ground.equivalent(graph.observed.nodes.get(selected.node).ground))
            throw new IllegalStateException("One exact cache root required");
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(graph,selected.node,budget);
        if(before.truncated || before.created>=budget)throw new IllegalStateException("Accumulated cache witness budget exceeded");
        DmzSourcedProofGraph next=graph.restrict(Collections.singletonList(pattern));
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(next,selected.node,budget-before.created);
        if(after.truncated)throw new IllegalStateException("Accumulated cache witness budget exceeded");
        if(before.witnesses.size()-after.witnesses.size()!=1)throw new IllegalStateException("New exact cache choice required");
        List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>(selections);choices.add(selected);
        List<DmzProofWitnesses.NoGood> rules=new ArrayList<DmzProofWitnesses.NoGood>(patterns);rules.add(pattern);
        return new DmzCacheRestrictionSet(evidence,choices,rules,next);
    }
    boolean isCurrent(Mind target) throws Exception {return evidence.isCurrent(target);}
}
