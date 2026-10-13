/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.IRule;

/** Historical cache routes bound to exact current rejected-operation endpoints.
 * Read-only bound evidence; only the owned native consumer can revalidate and continue.
 */
final class DmzCacheCollisionEvidence {
    static final class Projection {
        final boolean eligible,truncated,complete=false;
        final int removed;
        final DmzSourcedProofGraph graph;
        Projection(boolean eligible,boolean truncated,int removed,DmzSourcedProofGraph graph) {
            this.eligible=eligible;this.truncated=truncated;this.removed=removed;this.graph=graph;
        }
    }
    static final class Baseline {
        private final TerminalSupportCapture capture;
        private final Mind target;
        private final TerminalSupportCapture.Checkpoint state;
        private final DmzReplayProvenance.SourceCheckpoint sources;
        private Baseline(TerminalSupportCapture capture,Mind target) throws Exception {
            this.capture=capture;this.target=target;state=capture.baselineCheckpoint(target);
            sources=DmzReplayProvenance.sourceCheckpoint(target);
        }
        boolean current(CollisionProofCapture.Conflict event,Mind candidate) throws Exception {
            return candidate==target && event!=null && capture.isCurrent(state,target)
                    && sources.isCurrent(target) && capture.followsImmediate(state,event.observations);
        }
    }
    final boolean eligible,truncated,complete=false;
    final DmzSourcedProofGraph graph;
    final List<DmzWitnessConflicts.Combination> combinations;
    private final Baseline baseline;
    private final CollisionProofCapture.Conflict event;
    private final List<DmzStoredGroundRoutes> receipts;
    private DmzCacheCollisionEvidence(Baseline baseline,CollisionProofCapture.Conflict event,
            boolean eligible,boolean truncated,DmzSourcedProofGraph graph,List<DmzWitnessConflicts.Combination> combinations,
            List<DmzStoredGroundRoutes> receipts) {
        this.baseline=baseline;this.event=event;this.eligible=eligible;this.truncated=truncated;this.graph=graph;
        this.combinations=Collections.unmodifiableList(new ArrayList<DmzWitnessConflicts.Combination>(combinations));
        this.receipts=Collections.unmodifiableList(new ArrayList<DmzStoredGroundRoutes>(receipts));
    }
    static Baseline beforeOperation(TerminalSupportCapture capture,Mind target) throws Exception {
        return new Baseline(capture,target);
    }
    static DmzCacheCollisionEvidence bind(Baseline baseline,CollisionProofCapture.Conflict event,Mind target,int budget) throws Exception {
        if(budget<1 || budget>10000)throw new IllegalArgumentException("Bounded cache collision evidence required");
        if(!baseline.current(event,target))return refused(baseline,event,false);
        CollisionProofCapture.Node left=event.left.nodes.get(event.left.root),right=event.right.nodes.get(event.right.root);
        if(left.ground==null || right.ground==null || left.ground.sign==right.ground.sign
                || !left.ground.equivalent(new TerminalSupportCapture.Ground(right.ground.predicate,left.ground.sign,right.ground.arguments)))
            return refused(baseline,event,false);
        List<TerminalSupportCapture.ApplicationSources> associations=new ArrayList<TerminalSupportCapture.ApplicationSources>();
        List<DmzStoredGroundRoutes> receipts=new ArrayList<DmzStoredGroundRoutes>();int remaining=budget;
        for(CollisionProofCapture.Node endpoint:Arrays.asList(left,right)) {
            IRule live=null;
            for(IRule rule:target.getRules()) {
                if(--remaining<0)return refused(baseline,event,true);
                if(rule.getId()==endpoint.nativeRule && !rule.isDeleted(target))live=rule;
            }
            if(live==null || !endpoint.ground.equivalent(DmzGroundIdentity.capture(live,target).ground()))return refused(baseline,event,false);
            if(target.getRules().isGenerated(live)) {
                DmzStoredGroundRoutes receipt=DmzStoredGroundRoutes.capture(baseline.capture,target,endpoint.nativeRule,remaining);
                if(!receipt.covered || !receipt.isCurrent(target))return refused(baseline,event,receipt.truncated);
                receipts.add(receipt);
                for(int i=0;i<receipt.graph.observed.steps.size();++i) {
                    if(--remaining<0)return refused(baseline,event,true);
                    TerminalSupportCapture.Application app=receipt.graph.observed.steps.get(i).application;
                    DmzSourcedProofGraph.Step sources=receipt.graph.steps.get(i);
                    TerminalSupportCapture.Application copy=new TerminalSupportCapture.Application(event.observations.operation,
                            app.rule,app.ruleOrigin,app.conclusion,app.ground,app.bindings,app.supports,app.sourceCandidates);
                    associations.add(new TerminalSupportCapture.ApplicationSources(copy,sources.ruleSources,sources.primarySources));
                }
            } else {
                List<DmzReplayProvenance.Binding> labels=new ArrayList<DmzReplayProvenance.Binding>();
                for(DmzReplayProvenance.SourceObservation source:endpoint.sources) {
                    if(--remaining<0)return refused(baseline,event,true);
                    if(source.outcome!=DmzReplayProvenance.Outcome.ACCEPTED || !baseline.sources.contains(source.binding))return refused(baseline,event,false);
                    labels.add(source.binding);
                }
                if(labels.isEmpty())return refused(baseline,event,false);
                TerminalSupportCapture.Support support=new TerminalSupportCapture.Support(-1,-1,"event-primary",endpoint.ground,true);
                TerminalSupportCapture.Application primary=new TerminalSupportCapture.Application(event.observations.operation,-1,
                        "event-primary","event-primary",endpoint.ground,Collections.<TerminalSupportCapture.Binding>emptyList(),Collections.singletonList(support));
                associations.add(new TerminalSupportCapture.ApplicationSources(primary,Collections.<DmzReplayProvenance.Binding>emptyList(),Collections.singletonList(labels)));
            }
        }
        if(receipts.isEmpty() || remaining<1)return refused(baseline,event,remaining<1);
        DmzSourcedProofGraph graph=DmzSourcedProofGraph.build(associations);
        DmzWitnessConflicts.Result all=DmzWitnessConflicts.collect(graph,remaining,remaining);
        if(all.truncated)return refused(baseline,event,true);
        List<DmzWitnessConflicts.Combination> pairs=new ArrayList<DmzWitnessConflicts.Combination>();
        for(DmzWitnessConflicts.Combination pair:all.combinations) {
            if(--remaining<0)return refused(baseline,event,true);
            TerminalSupportCapture.Ground a=graph.observed.nodes.get(pair.left.node).ground,b=graph.observed.nodes.get(pair.right.node).ground;
            if(a.equivalent(left.ground) && b.equivalent(right.ground) || b.equivalent(left.ground) && a.equivalent(right.ground))pairs.add(pair);
        }
        boolean eligible=!pairs.isEmpty() && baseline.current(event,target);
        for(DmzStoredGroundRoutes receipt:receipts)eligible &= receipt.isCurrent(target);
        return new DmzCacheCollisionEvidence(baseline,event,eligible,false,graph,pairs,receipts);
    }
    boolean isCurrent(Mind candidate) throws Exception {
        if(!eligible || !baseline.current(event,candidate))return false;
        for(DmzStoredGroundRoutes receipt:receipts)if(!receipt.isCurrent(candidate))return false;
        return true;
    }
    Projection project(DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(budget<1 || budget>10000)throw new IllegalArgumentException("Bounded cache projection required");
        if(!isCurrent(target) || selected==null || selected.graph!=graph || selected.step<0
                || selected.source.authority!=DmzReplayProvenance.Authority.EXTERNAL)return new Projection(false,false,0,null);
        boolean member=false;
        for(DmzWitnessConflicts.Combination pair:combinations)member |= selected==pair.left || selected==pair.right;
        if(!member)return new Projection(false,false,0,null);
        DmzProofWitnesses.NoGood pattern;
        try {pattern=DmzProofWitnesses.NoGood.fromTyped(selected);}catch(IllegalArgumentException unsupported){return new Projection(false,false,0,null);}
        if(pattern.depth>8)return new Projection(false,false,0,null);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(graph,selected.node,budget);
        if(before.truncated || before.created>=budget)return new Projection(false,true,0,null);
        DmzSourcedProofGraph reduced=graph.restrict(Collections.singletonList(pattern));
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(reduced,selected.node,budget-before.created);
        if(after.truncated)return new Projection(false,true,0,null);
        int removed=before.witnesses.size()-after.witnesses.size();
        return removed==1 ? new Projection(true,false,1,reduced):new Projection(false,false,0,null);
    }
    private static DmzCacheCollisionEvidence refused(Baseline baseline,CollisionProofCapture.Conflict event,boolean truncated) {
        return new DmzCacheCollisionEvidence(baseline,event,false,truncated,null,Collections.<DmzWitnessConflicts.Combination>emptyList(),Collections.<DmzStoredGroundRoutes>emptyList());
    }
}
