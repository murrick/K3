/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.interfaces.*;

/** Provider bridge. Owns its reconstruction and exports only historical DTOs. */
public final class DmzSourceRestrictionWorkflow {
    private DmzSourceRestrictionWorkflow() { }
    public static final class Result {
        public final DmzSourcesQuery.RestrictionOutcome outcome;
        public final List<DmzSourcesQuery.Proof> proofs;
        public final DmzConditionalResult.Answer answer;
        private Result(DmzSourcesQuery.RestrictionOutcome outcome,List<DmzSourcesQuery.Proof> proofs,DmzConditionalResult.Answer answer) {
            this.outcome=outcome;this.proofs=Collections.unmodifiableList(new ArrayList<DmzSourcesQuery.Proof>(proofs));this.answer=answer;
        }
    }
    private static final class Input {
        final DmzCandidateSession.Participant participant;
        final DmzSourceShape.Declaration declaration;
        final DmzReplayProvenance.Authority authority;
        Input(DmzCandidateSession.Participant participant,DmzSourceShape.Declaration declaration,boolean target) {
            this.participant=participant;this.declaration=declaration;
            authority=target?DmzReplayProvenance.Authority.TARGET_Q:DmzReplayProvenance.Authority.EXTERNAL;
        }
    }
    private static final class Option {
        final DmzCacheRestrictionSet prefix;
        final DmzSourcesQuery.Proof proof;
        Option(DmzCacheRestrictionSet prefix,DmzSourcesQuery.Proof proof) { this.prefix=prefix;this.proof=proof; }
    }
    public static Result execute(DmzCandidateSession.Participant authority,List<DmzCandidateSession.Participant> components,
            List<DmzCandidateSession.Participant> external,DmzSourcesQuery.Request request) throws Exception {
        if(authority==null || components==null || external==null || request==null || request.restrictions==null)
            throw new IllegalArgumentException("Explicit source restriction workflow required");
        DmzSourcesQuery.RestrictionRequest choice=request.restrictions;
        if(choice.choices.size()>request.limits.maximumVisits || request.queryStatement==null
                || !request.queryStatement.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Bounded restriction visits and signed unary integer query required");
        int budget=choice.budget;
        List<DmzCandidateSession.Participant> sources=new ArrayList<DmzCandidateSession.Participant>(external);
        Collections.sort(sources,(a,b)->a.revision.getContextId().toString().compareTo(b.revision.getContextId().toString()));
        DmzSourceDispatchPlan.prepare(authority,sources);
        List<DmzCandidateSession.Participant> all=new ArrayList<DmzCandidateSession.Participant>();
        all.add(authority);all.addAll(components);all.addAll(sources);
        Map<String,DmzSourceShape.Declaration> declarations=new HashMap<String,DmzSourceShape.Declaration>();
        List<Input> units=new ArrayList<Input>(),grounds=new ArrayList<Input>();Input trigger=null;
        int remaining=budget;
        for(DmzCandidateSession.Participant participant:all)for(DmzSourceShape.Declaration declaration:participant.inputs.declarations) {
            if(--remaining<1)throw new IllegalStateException("Restriction reconstruction budget exceeded");
            Input input=new Input(participant,declaration,participant==authority || components.contains(participant));
            declarations.put(participant.revision.getContextId()+"@"+participant.revision.getRevision()+":"+declaration.sourceRule,declaration);
            if(declaration.unitStatement!=null)units.add(input);
            else if(declaration.groundStatement!=null)grounds.add(input);
            else throw new IllegalArgumentException("Restriction reconstruction requires checked units and grounds");
            if(participant.revision.getContextId().equals(choice.triggerRevision.getContextId())
                    && participant.revision.getRevision()==choice.triggerRevision.getRevision() && declaration.sourceRule==choice.triggerRule)
                trigger=input;
        }
        if(trigger==null || trigger.authority!=DmzReplayProvenance.Authority.EXTERNAL)
            throw new IllegalArgumentException("Exact acquired external trigger required");
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            for(Input input:units)replay(q,input);
            // Warm supported caches before adding contrary primaries. All primaries remain present;
            // this ordering grants no physical Q edit or permission to retract a primary.
            List<Input> deferred=new ArrayList<Input>();
            for(Input input:grounds) {
                TerminalSupportCapture.Ground ground=DmzGroundReplay.parseIdentity(input.declaration.groundStatement).ground();
                boolean contrary=false;
                for(Input unit:units) {
                    if(--remaining<1)throw new IllegalStateException("Restriction warm ordering budget exceeded");
                    String head=unit.declaration.unitStatement.substring(unit.declaration.unitStatement.indexOf("->")+2).trim();
                    boolean positive=!head.startsWith("~");
                    String predicate=head.substring(positive?0:1,head.indexOf('('));
                    if(predicate.equals(ground.predicate) && positive!=ground.sign)contrary=true;
                }
                if(contrary)deferred.add(input);
                else if(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,input.participant.revision,input.declaration.sourceRule,
                        input.authority,input.declaration.groundStatement)))replay(q,input);
            }
            for(Input input:deferred)replay(q,input);
            String state=DmzObservationStateFingerprint.capture(q);
            DmzReplayProvenance.SourceCheckpoint checkpoint=DmzReplayProvenance.sourceCheckpoint(q);
            DmzCacheCollisionEvidence.Baseline baseline=DmzCacheCollisionEvidence.beforeOperation(capture,q);
            CollisionProofCapture.Conflict conflict;
            try(CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                if(q.compile(trigger.declaration.statement,null,false) || collisions.snapshot().isEmpty())
                    throw new IllegalStateException("Restriction trigger did not produce a native conflict");
                conflict=collisions.snapshot().get(0);
            }
            DmzCacheCollisionEvidence evidence=DmzCacheCollisionEvidence.bind(baseline,conflict,q,budget);
            if(!evidence.eligible || evidence.truncated || !state.equals(DmzObservationStateFingerprint.capture(q)) || !checkpoint.isCurrent(q))
                throw new IllegalStateException("Restriction initial conflict lacks current complete source history");
            DmzCacheRestrictionSet prefix=DmzCacheRestrictionSet.begin(evidence,q);
            DmzCacheRestrictionSet.Next next=null;
            List<DmzWitnessConflicts.Combination> pairs=evidence.combinations;
            List<DmzSourcesQuery.Proof> selected=Collections.emptyList();
            List<DmzSourcesQuery.RestrictionStep> trace=new ArrayList<DmzSourcesQuery.RestrictionStep>();
            for(int index=0;;++index) {
                SortedMap<String,DmzSourcesQuery.RestrictionConflict> observed=new TreeMap<String,DmzSourcesQuery.RestrictionConflict>();
                int[] conflictBudget={Math.min(budget,request.limits.projection)};
                try { for(DmzWitnessConflicts.Combination pair:pairs) {
                    boolean positive=pair.left.graph.observed.nodes.get(pair.left.node).ground.sign;
                    DmzSourcesQuery.Proof a=DmzCacheRestrictionSet.publicProof(positive?pair.left:pair.right,declarations,conflictBudget);
                    DmzSourcesQuery.Proof b=DmzCacheRestrictionSet.publicProof(positive?pair.right:pair.left,declarations,conflictBudget);
                    DmzSourcesQuery.RestrictionConflict conflictPair=new DmzSourcesQuery.RestrictionConflict(a,b);
                    observed.put(conflictPair.identity(),conflictPair);
                } } catch(DmzCacheRestrictionSet.ProofProjectionBudgetExceeded exceeded) {
                    throw new DmzSourcesQuery.ProjectionRefusal(DmzSourcesQuery.ProjectionPhase.CONFLICT_EVIDENCE,index,
                            request.limits.projection,budget);
                }
                List<DmzSourcesQuery.RestrictionConflict> conflicts=new ArrayList<DmzSourcesQuery.RestrictionConflict>(observed.values());
                SortedMap<String,Option> options=new TreeMap<String,Option>();
                Set<DmzProofWitnesses.Witness> seen=Collections.newSetFromMap(new IdentityHashMap<DmzProofWitnesses.Witness,Boolean>());
                for(DmzWitnessConflicts.Combination pair:pairs) {
                    Deque<DmzProofWitnesses.Witness> pending=new ArrayDeque<DmzProofWitnesses.Witness>();
                    pending.add(pair.left);pending.add(pair.right);
                    while(!pending.isEmpty()) {
                        if(--remaining<1)throw new IllegalStateException("Restriction choice projection budget exceeded");
                        DmzProofWitnesses.Witness witness=pending.removeFirst();
                        if(!seen.add(witness))continue;
                        boolean endpoint=witness==pair.left || witness==pair.right;
                        boolean causal=index==0 && choice.choiceScope==DmzSourcesQuery.RestrictionChoiceScope.INITIAL_OBSERVED_CAUSAL_CHAINS;
                        if(causal)pending.addAll(witness.premises);
                        if(witness.step<0 || witness.source.authority!=DmzReplayProvenance.Authority.EXTERNAL)continue;
                        DmzCacheRestrictionSet candidate;
                        if(endpoint)candidate=next==null?prefix.append(witness,q,budget):prefix.append(next,witness,q,budget);
                        else {
                            if(!causal)continue;
                            DmzCacheCollisionEvidence.Projection projection=evidence.projectCausal(witness,q,budget);
                            if(projection.truncated)throw new IllegalStateException("Causal restriction choice evidence budget exceeded");
                            if(!projection.eligible)continue;
                            candidate=prefix.appendCausal(witness,q,budget);
                        }
                        DmzCacheRestrictionSet.SourceReceipt receipt=candidate.prepareSourceReceipt(all,q,budget);
                        DmzSourcesQuery.Proof proof=receipt.proofs.get(receipt.proofs.size()-1);
                        options.put(proof.identity(),new Option(candidate,proof));
                    }
                }
                List<DmzSourcesQuery.Proof> offered=new ArrayList<DmzSourcesQuery.Proof>();
                for(Option option:options.values())offered.add(option.proof);
                if(index==choice.choices.size()) {
                    if(options.isEmpty() && !trace.isEmpty()) {
                        DmzSourcesQuery.RestrictionStep last=trace.get(trace.size()-1);
                        trace.set(trace.size()-1,new DmzSourcesQuery.RestrictionStep(last.number,last.selected,last.alternatives,
                                DmzSourcesQuery.RestrictionStatus.STOPPED_UNCOVERED,last.conflicts));
                    }
                    return new Result(new DmzSourcesQuery.RestrictionOutcome(options.isEmpty()?DmzSourcesQuery.RestrictionStatus.STOPPED_UNCOVERED
                            :DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT,index,offered,trace,
                            options.isEmpty()?Collections.<DmzSourcesQuery.RestrictionConflict>emptyList():conflicts),selected,null);
                }
                Option option=options.get(choice.choices.get(index));
                if(option==null)throw new IllegalArgumentException("Requested proof is not an exact current conflict choice");
                prefix=option.prefix;
                DmzCacheRestrictionSet.SourceReceipt receipt=prefix.prepareSourceReceipt(all,q,budget);selected=receipt.proofs;
                DmzCacheRestrictionSet.Continuation continued;
                try(DmzCandidateSession prepared=DmzCandidateSession.open(authority,components,sources,request.limits.preparation)) {
                    continued=prepared.prepareAccumulatedRestrictions(prefix,receipt,budget).continueAccumulatedQuery(q,budget,request.queryStatement);
                }
                if(!state.equals(DmzObservationStateFingerprint.capture(q)) || !checkpoint.isCurrent(q) || !prefix.isCurrent(q)
                        || q.hasPendingTransactions() || !DmzTerminalRestriction.activeNoGoods(q).isEmpty() || user.getCurrentMind()!=q)
                    throw new IllegalStateException("Restriction workflow failed to restore its reconstruction");
                DmzSourcesQuery.RestrictionOutcome summary=continued.detached();
                trace.add(new DmzSourcesQuery.RestrictionStep(index+1,option.proof,offered,summary.status,conflicts));
                if(continued.status!=DmzCacheRestrictionSet.ContinuationStatus.NEXT_CONFLICT) {
                    if(index+1!=choice.choices.size())throw new IllegalArgumentException("Unused restriction choices after workflow stop");
                    DmzConditionalResult.Answer answer=null;
                    if(continued.status==DmzCacheRestrictionSet.ContinuationStatus.COMPLETE) {
                        List<DmzSourcesQuery.Proof> proofs=new ArrayList<DmzSourcesQuery.Proof>();
                        DmzTypedNativeResume.QueryObservation observation=continued.result.queryAnswer;
                        if(observation==null)throw new IllegalStateException("Completed restriction query observation required");
                        int[] projectionBudget={Math.min(budget,request.limits.projection)};
                        try {
                            for(DmzProofWitnesses.Witness witness:observation.proofs)proofs.add(DmzCacheRestrictionSet.publicProof(witness,declarations,projectionBudget));
                        } catch(DmzCacheRestrictionSet.ProofProjectionBudgetExceeded exceeded) {
                            throw new DmzSourcesQuery.ProjectionRefusal(DmzSourcesQuery.ProjectionPhase.QUERY_ANSWER,index+1,
                                    request.limits.projection,budget);
                        }
                        answer=new DmzConditionalResult.Answer(DmzConditionalResult.CandidateMode.NONE,observation.value,
                                observation.hypotheses,observation.assertions,proofs);
                    }
                    return new Result(new DmzSourcesQuery.RestrictionOutcome(summary.status,summary.choices,
                            Collections.<DmzSourcesQuery.Proof>emptyList(),trace),selected,answer);
                }
                next=continued.next;pairs=next.combinations;
            }
        }
    }
    private static void replay(Mind q,Input input) throws Exception {
        DmzReplayProvenance.replayRule(q,input.participant.revision,input.declaration.sourceRule,input.authority,input.declaration.statement);
    }
}
