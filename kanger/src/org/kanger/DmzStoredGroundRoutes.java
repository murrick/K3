/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.IRule;

/** Current bounded historical native routes. Diagnostic only, never native cut permission. */
final class DmzStoredGroundRoutes {
    final boolean covered,truncated,complete=false;
    final DmzSourcedProofGraph graph;
    final int expectedProofs,observedProofs;
    private final Mind target;
    private final String state;
    private final DmzReplayProvenance.SourceCheckpoint sources;
    private final TerminalSupportCapture capture;
    private final TerminalSupportCapture.Checkpoint checkpoint;
    private DmzStoredGroundRoutes(Mind target,TerminalSupportCapture capture,
            TerminalSupportCapture.Checkpoint checkpoint,DmzReplayProvenance.SourceCheckpoint sources,
            String state,boolean covered,boolean truncated,DmzSourcedProofGraph graph,int expected,int observed) {
        this.target=target;this.capture=capture;this.checkpoint=checkpoint;this.sources=sources;this.state=state;
        this.covered=covered;this.truncated=truncated;this.graph=graph;expectedProofs=expected;observedProofs=observed;
    }
    static DmzStoredGroundRoutes capture(TerminalSupportCapture capture,Mind target,long nativeCache,int budget) throws Exception {
        if(budget<1 || budget>10000)throw new IllegalArgumentException("Bounded stored route audit required");
        TerminalSupportCapture.Checkpoint boundary=capture.baselineCheckpoint(target);
        DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(target);
        String state=DmzObservationStateFingerprint.capture(target);
        IRule selected=null;int remaining=budget;
        for(IRule rule:target.getRules()) {
            if(--remaining<0)return refused(target,capture,boundary,sources,state,true);
            if(rule.getId()==nativeCache && !rule.isDeleted(target))selected=rule;
        }
        if(selected==null || !target.getRules().isGenerated(selected)
                || !DmzReplayProvenance.observedSources(target,nativeCache).isEmpty())
            return refused(target,capture,boundary,sources,state,false);
        TerminalSupportCapture.Ground root=DmzGroundIdentity.capture(selected,target).ground();
        DmzCurrentGroundProofInventory expected=DmzCurrentGroundProofInventory.capture(target,budget,false);
        if(!expected.eligible || expected.provisional)return refused(target,capture,boundary,sources,state,expected.truncated);
        List<TerminalSupportCapture.ApplicationSources> routes=new ArrayList<TerminalSupportCapture.ApplicationSources>();
        Set<TerminalSupportCapture.Application> seen=Collections.newSetFromMap(new IdentityHashMap<TerminalSupportCapture.Application,Boolean>());
        boolean storedRoot=false;
        // Only actual storage-time applications from committed observed operations are admitted.
        List<TerminalSupportCapture.StoredObservation> history;
        try {history=capture.storedSnapshotBounded(budget);}
        catch(IllegalStateException exhausted){return refused(target,capture,boundary,sources,state,true);}
        for(TerminalSupportCapture.StoredObservation stored:history) {
            if(--remaining<0)return refused(target,capture,boundary,sources,state,true);
            if(stored.outcome!=TerminalSupportCapture.Outcome.COMMITTED || !stored.materialization.generated)continue;
            if(stored.materialization.nativeRule==nativeCache
                    && root.equivalent(stored.materialization.causes.nodes.get(stored.materialization.causes.root).ground))storedRoot=true;
            for(TerminalSupportCapture.Application app:stored.routes) {
                if(--remaining<0)return refused(target,capture,boundary,sources,state,true);
                if(!seen.add(app))continue;
                TerminalSupportCapture.ProvisionalApplication frozen=capture.historicalSources(app);
                if(frozen==null || !frozen.excludedPairs.isEmpty() || !frozen.noGoods.isEmpty())continue;
                List<DmzReplayProvenance.Binding> production=current(frozen.ruleSources,sources);
                List<List<DmzReplayProvenance.Binding>> supports=new ArrayList<List<DmzReplayProvenance.Binding>>();
                for(List<DmzReplayProvenance.SourceObservation> input:frozen.supportSources)supports.add(current(input,sources));
                if(production.isEmpty())continue;
                TerminalSupportCapture.Application copy=new TerminalSupportCapture.Application(boundary.target,app.rule,
                        app.ruleOrigin,app.conclusion,app.ground,app.bindings,app.supports,app.sourceCandidates);
                routes.add(new TerminalSupportCapture.ApplicationSources(copy,production,supports));
            }
        }
        if(!storedRoot)return refused(target,capture,boundary,sources,state,false);
        DmzSourcedProofGraph actual=DmzSourcedProofGraph.build(routes);
        int node=-1;
        for(int i=0;i<actual.observed.nodes.size();++i) {
            if(--remaining<0)return refused(target,capture,boundary,sources,state,true);
            if(root.equivalent(actual.observed.nodes.get(i).ground))node=i;
        }
        if(node<0 || remaining<1)return refused(target,capture,boundary,sources,state,remaining<1);
        DmzProofWitnesses.Result observed=DmzProofWitnesses.enumerate(actual,node,remaining);
        if(observed.truncated || (remaining-=observed.created)<1)return refused(target,capture,boundary,sources,state,true);
        DmzCurrentGroundProofInventory.Proofs wanted=expected.proofs(root,remaining);
        if(wanted.truncated)return refused(target,capture,boundary,sources,state,true);
        boolean covered=!wanted.witnesses.isEmpty() && observed.witnesses.size()==wanted.witnesses.size();
        for(DmzProofWitnesses.Witness witness:wanted.witnesses) {
            DmzProofWitnesses.NoGood exact;
            try {exact=DmzProofWitnesses.NoGood.fromTyped(witness);}catch(IllegalArgumentException unsupported){covered=false;break;}
            int matches=0;
            for(DmzProofWitnesses.Witness nativeProof:observed.witnesses) {
                if((remaining-=exact.workCost)<0)return refused(target,capture,boundary,sources,state,true);
                if(exact.matches(nativeProof.graph,nativeProof.node,nativeProof.step,nativeProof.source,nativeProof.premises))++matches;
            }
            covered &= matches==1;
        }
        covered &= capture.isCurrent(boundary,target) && sources.isCurrent(target) && expected.isCurrent(target);
        return new DmzStoredGroundRoutes(target,capture,boundary,sources,state,covered,false,actual,wanted.witnesses.size(),observed.witnesses.size());
    }
    private static List<DmzReplayProvenance.Binding> current(List<DmzReplayProvenance.SourceObservation> frozen,
            DmzReplayProvenance.SourceCheckpoint sources) {
        List<DmzReplayProvenance.Binding> result=new ArrayList<DmzReplayProvenance.Binding>();
        for(DmzReplayProvenance.SourceObservation source:frozen)if(sources.contains(source.binding))result.add(source.binding);
        return result;
    }
    boolean isCurrent(Mind candidate) throws Exception {
        return covered && candidate==target && capture.isCurrent(checkpoint,candidate)
                && sources.isCurrent(candidate) && state.equals(DmzObservationStateFingerprint.capture(candidate));
    }
    private static DmzStoredGroundRoutes refused(Mind target,TerminalSupportCapture capture,
            TerminalSupportCapture.Checkpoint checkpoint,DmzReplayProvenance.SourceCheckpoint sources,String state,boolean truncated) {
        return new DmzStoredGroundRoutes(target,capture,checkpoint,sources,state,false,truncated,null,0,0);
    }
}
