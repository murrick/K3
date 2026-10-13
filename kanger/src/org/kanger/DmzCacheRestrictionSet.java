/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Immutable accumulated exact choices for covered cache-conflict roots. */
final class DmzCacheRestrictionSet {
    final DmzCacheCollisionEvidence evidence;
    final List<DmzProofWitnesses.Witness> selections;
    final List<DmzProofWitnesses.NoGood> patterns;
    final DmzSourcedProofGraph graph;
    private final List<DmzStoredGroundRoutes> receipts;
    static final class Next {
        final List<DmzWitnessConflicts.Combination> combinations;
        final DmzSourcedProofGraph graph;
        private final DmzCacheRestrictionSet prefix;
        private final List<DmzStoredGroundRoutes> receipts;
        private Next(DmzCacheRestrictionSet prefix,DmzSourcedProofGraph graph,List<DmzWitnessConflicts.Combination> combinations,
                List<DmzStoredGroundRoutes> receipts) {
            this.prefix=prefix;this.graph=graph;
            this.combinations=Collections.unmodifiableList(new ArrayList<DmzWitnessConflicts.Combination>(combinations));
            this.receipts=Collections.unmodifiableList(new ArrayList<DmzStoredGroundRoutes>(receipts));
        }
    }
    private DmzCacheRestrictionSet(DmzCacheCollisionEvidence evidence,List<DmzProofWitnesses.Witness> selections,
            List<DmzProofWitnesses.NoGood> patterns,DmzSourcedProofGraph graph,List<DmzStoredGroundRoutes> receipts) {
        this.evidence=evidence;this.graph=graph;this.receipts=Collections.unmodifiableList(new ArrayList<DmzStoredGroundRoutes>(receipts));
        this.selections=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.Witness>(selections));
        this.patterns=Collections.unmodifiableList(new ArrayList<DmzProofWitnesses.NoGood>(patterns));
    }
    static DmzCacheRestrictionSet begin(DmzCacheCollisionEvidence evidence,Mind target) throws Exception {
        if(evidence==null || !evidence.isCurrent(target))throw new IllegalStateException("Current cache evidence required");
        return new DmzCacheRestrictionSet(evidence,Collections.<DmzProofWitnesses.Witness>emptyList(),Collections.<DmzProofWitnesses.NoGood>emptyList(),evidence.graph,Collections.<DmzStoredGroundRoutes>emptyList());
    }
    DmzCacheRestrictionSet append(DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(!receipts.isEmpty() || !isCurrent(target) || selections.size()>=128 || !evidence.project(selected,target,budget).eligible)
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
        return new DmzCacheRestrictionSet(evidence,choices,rules,next,receipts);
    }
    Next next(DmzTypedNativeResume.Result result,Mind target,int budget) throws Exception {
        if(budget<1 || budget>10000)throw new IllegalArgumentException("Bounded successive conflict required");
        if(result==null || !isCurrent(target) || !result.belongsTo(this,target) || result.compiled || result.provisional || result.conflicts.isEmpty())
            throw new IllegalStateException("Owned rejected accumulated continuation required");
        CollisionProofCapture.Conflict event=result.conflicts.get(result.conflicts.size()-1);
        CollisionProofCapture.Node left=event.left.nodes.get(event.left.root),right=event.right.nodes.get(event.right.root);
        if(left.ground==null || right.ground==null || left.ground.sign==right.ground.sign
                || !left.ground.equivalent(new TerminalSupportCapture.Ground(right.ground.predicate,left.ground.sign,right.ground.arguments)))
            throw new IllegalStateException("Exact opposite successive endpoints required");
        List<DmzStoredGroundRoutes> history=new ArrayList<DmzStoredGroundRoutes>(receipts);
        boolean generated=false;
        for(CollisionProofCapture.Node endpoint:Arrays.asList(left,right)) {
            boolean live=false;
            for(DmzTypedNativeResume.Materialization item:result.materializations) {
                if(--budget<1)throw new IllegalStateException("Successive endpoint budget exceeded");
                if(item.nativeRule==endpoint.nativeRule && item.ground.equivalent(endpoint.ground))live=true;
            }
            if(!live)throw new IllegalStateException("Live owned successive endpoint required");
            DmzStoredGroundRoutes receipt=evidence.routes(endpoint,target,budget);
            if(receipt!=null){history.add(receipt);generated=true;}
        }
        if(!generated)throw new IllegalStateException("Successive generated cache conflict required");
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,false);
        if(!inventory.eligible || inventory.provisional)throw new IllegalStateException("Current successive inventory required");
        DmzSourcedProofGraph remaining=inventory.graph.restrict(patterns);
        DmzWitnessConflicts.Result conflicts=DmzWitnessConflicts.collect(remaining,budget,budget);
        if(conflicts.truncated)throw new IllegalStateException("Successive conflict witness budget exceeded");
        List<DmzWitnessConflicts.Combination> pairs=new ArrayList<DmzWitnessConflicts.Combination>();
        for(DmzWitnessConflicts.Combination pair:conflicts.combinations) {
            if(--budget<1)throw new IllegalStateException("Successive conflict witness budget exceeded");
            TerminalSupportCapture.Ground a=remaining.observed.nodes.get(pair.left.node).ground,b=remaining.observed.nodes.get(pair.right.node).ground;
            if(a.equivalent(left.ground) && b.equivalent(right.ground) || a.equivalent(right.ground) && b.equivalent(left.ground))pairs.add(pair);
        }
        if(pairs.isEmpty() || !isCurrent(target) || !inventory.isCurrent(target))throw new IllegalStateException("Remaining successive conflict required");
        return new Next(this,remaining,pairs,history);
    }
    DmzCacheRestrictionSet append(Next next,DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(budget<1 || budget>10000)throw new IllegalArgumentException("Bounded successive choice required");
        if(next==null || next.prefix!=this || !isCurrent(target) || selections.size()>=128 || selected==null
                || selected.graph!=next.graph || selected.step<0 || selected.source.authority!=DmzReplayProvenance.Authority.EXTERNAL)
            throw new IllegalStateException("Current owned successive derived choice required");
        boolean member=false;
        for(DmzWitnessConflicts.Combination pair:next.combinations)member |= selected==pair.left || selected==pair.right;
        if(!member)throw new IllegalStateException("Successive conflict member required");
        for(DmzStoredGroundRoutes receipt:next.receipts)if(!receipt.isCurrent(target))throw new IllegalStateException("Current successive history required");
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        if(pattern.depth>8)throw new IllegalStateException("Bounded successive typed choice required");
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(next.graph,selected.node,budget);
        if(before.truncated || before.created>=budget)throw new IllegalStateException("Successive choice budget exceeded");
        DmzSourcedProofGraph reduced=next.graph.restrict(Collections.singletonList(pattern));
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(reduced,selected.node,budget-before.created);
        if(after.truncated || before.witnesses.size()-after.witnesses.size()!=1)throw new IllegalStateException("New exact successive choice required");
        List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>(selections);choices.add(selected);
        List<DmzProofWitnesses.NoGood> rules=new ArrayList<DmzProofWitnesses.NoGood>(patterns);rules.add(pattern);
        return new DmzCacheRestrictionSet(evidence,choices,rules,reduced,next.receipts);
    }
    static List<DmzProofWitnesses.Witness> retained(DmzCurrentGroundProofInventory inventory,
            List<DmzProofWitnesses.Witness> selections,List<DmzProofWitnesses.NoGood> patterns,int budget) throws Exception {
        List<TerminalSupportCapture.Ground> roots=new ArrayList<TerminalSupportCapture.Ground>();
        List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>();
        int remaining=budget;
        for(DmzProofWitnesses.Witness selected:selections) {
            TerminalSupportCapture.Ground root=selected.graph.observed.nodes.get(selected.node).ground;
            boolean seen=false;
            for(TerminalSupportCapture.Ground old:roots){if(--remaining<1)throw new IllegalStateException("Typed root budget exceeded");seen |= old.equivalent(root);}
            if(seen)continue;
            roots.add(root);
            int node=-1;
            for(int i=0;i<inventory.graph.observed.nodes.size();++i) {
                if(--remaining<1)throw new IllegalStateException("Typed root budget exceeded");
                if(root.equivalent(inventory.graph.observed.nodes.get(i).ground))node=i;
            }
            if(node<0)throw new IllegalStateException("Current typed root required");
            DmzProofWitnesses.Result proofs=DmzProofWitnesses.enumerate(inventory.graph,node,remaining);
            if(proofs.truncated || (remaining-=proofs.created)<1)throw new IllegalStateException("Typed root witness budget exceeded");
            choices.addAll(proofs.witnesses);
        }
        int[] matches=new int[patterns.size()];
        for(DmzProofWitnesses.Witness choice:choices) {
            for(int i=0;i<patterns.size();++i) {
                if((remaining-=patterns.get(i).workCost)<0)throw new IllegalStateException("Typed restriction matching budget exceeded");
                if(patterns.get(i).matches(choice.graph,choice.node,choice.step,choice.source,choice.premises)){++matches[i];}
            }
        }
        for(int count:matches)if(count!=1)throw new IllegalStateException("Exact typed source choice required");
        // Enumerate the fully restricted graph, including restrictions inside other root chains.
        List<DmzProofWitnesses.Witness> retained=new ArrayList<DmzProofWitnesses.Witness>();
        DmzSourcedProofGraph reduced=inventory.graph.restrict(patterns);
        for(TerminalSupportCapture.Ground root:roots) {
            int node=-1;
            for(int i=0;i<reduced.observed.nodes.size();++i) {
                if(--remaining<1)throw new IllegalStateException("Typed retained root budget exceeded");
                if(root.equivalent(reduced.observed.nodes.get(i).ground))node=i;
            }
            if(node<0)throw new IllegalStateException("Current typed root required");
            DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(reduced,node,remaining);
            if(after.truncated || (remaining-=after.created)<1)throw new IllegalStateException("Typed retained witness budget exceeded");
            retained.addAll(after.witnesses);
        }
        return retained;
    }
    boolean isCurrent(Mind target) throws Exception {
        if(!evidence.isCurrent(target))return false;
        for(DmzStoredGroundRoutes receipt:receipts)if(!receipt.isCurrent(target))return false;
        return true;
    }
}
