/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.DmzSourcesQuery;

/** Immutable accumulated exact choices for covered cache-conflict roots. */
final class DmzCacheRestrictionSet {
    enum ContinuationStatus { COMPLETE, NEXT_CONFLICT, STOPPED_UNCOVERED }
    static final class Continuation {
        final ContinuationStatus status;
        final DmzTypedNativeResume.Result result;
        final Next next;
        final int choices;
        private Continuation(ContinuationStatus status,DmzTypedNativeResume.Result result,Next next,int choices) {
            this.status=status;this.result=result;this.next=next;this.choices=choices;
        }
        org.kanger.interfaces.DmzSourcesQuery.RestrictionOutcome detached() {
            return new org.kanger.interfaces.DmzSourcesQuery.RestrictionOutcome(
                    org.kanger.interfaces.DmzSourcesQuery.RestrictionStatus.valueOf(status.name()),choices);
        }
    }
    static final class UncoveredConflict extends IllegalStateException {
        private static final long serialVersionUID=1L;
        UncoveredConflict(String message) { super(message); }
    }
    final DmzCacheCollisionEvidence evidence;
    final List<DmzProofWitnesses.Witness> selections;
    final List<DmzProofWitnesses.NoGood> patterns;
    final DmzSourcedProofGraph graph;
    private final List<DmzStoredGroundRoutes> receipts;
    static final class PreparedSources {
        private final DmzCacheRestrictionSet prefix;
        private final DmzCandidateSession session;
        final List<DmzSourcesQuery.Proof> proofs;
        private PreparedSources(DmzCacheRestrictionSet prefix,DmzCandidateSession session,List<DmzSourcesQuery.Proof> proofs) {
            this.prefix=prefix;this.session=session;this.proofs=Collections.unmodifiableList(new ArrayList<DmzSourcesQuery.Proof>(proofs));
        }
        boolean isCurrent(DmzCacheRestrictionSet candidate,DmzCandidateSession owner) throws Exception {
            return candidate==prefix && owner==session && session.isCurrent();
        }
        DmzTypedNativeResume.Result runAccumulated(Mind target,int budget) throws Exception {
            consume(target,budget);
            return DmzTypedNativeResume.runAccumulated(prefix,target,budget);
        }
        private void consume(Mind target,int budget) throws Exception {
            if(budget<1 || budget>10000 || !isCurrent(prefix,session))
                throw new IllegalStateException("Current prepared accumulated restrictions required");
            // The prepared owner has a nested replay journal. Close it first so the
            // original collision evidence's journal is active again for validation.
            session.close();
            if(!prefix.isCurrent(target))
                throw new IllegalStateException("Originating accumulated restriction boundary changed");
        }
        Continuation continueAccumulatedQuery(Mind target,int budget,String query) throws Exception {
            if(query==null || !query.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
                throw new IllegalArgumentException("Signed unary integer query required");
            consume(target,budget);
            return classify(prefix,DmzTypedNativeResume.queryAccumulated(prefix,target,budget,query),target,budget);
        }
        Continuation continueAccumulated(Mind target,int budget) throws Exception {
            DmzTypedNativeResume.Result result=runAccumulated(target,budget);
            return classify(prefix,result,target,budget);
        }
    }
    static final class SourceReceipt {
        private final DmzCacheRestrictionSet prefix;
        private final Map<String,RequiredSource> required;
        final List<DmzSourcesQuery.Proof> proofs;
        private SourceReceipt(DmzCacheRestrictionSet prefix,Map<String,RequiredSource> required,List<DmzSourcesQuery.Proof> proofs) {
            this.prefix=prefix;this.required=Collections.unmodifiableMap(new HashMap<String,RequiredSource>(required));
            this.proofs=Collections.unmodifiableList(new ArrayList<DmzSourcesQuery.Proof>(proofs));
        }
    }
    private static final class RequiredSource {
        final boolean production;
        final String statement;
        RequiredSource(boolean production,String statement) { this.production=production;this.statement=statement; }
    }
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
        return appendVerified(selected,budget,receipts);
    }
    /** First causal subchain cut only; endpoint-only callers retain their existing permissions. */
    DmzCacheRestrictionSet appendCausal(DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        if(!selections.isEmpty() || !receipts.isEmpty() || !isCurrent(target))
            throw new IllegalStateException("Fresh current causal choice required");
        DmzCacheCollisionEvidence.Projection projection=evidence.projectCausal(selected,target,budget);
        if(!projection.eligible || projection.causalRoute==null)
            throw new IllegalStateException("Covered observed external causal choice required");
        return appendVerified(selected,budget,Collections.singletonList(projection.causalRoute));
    }
    private DmzCacheRestrictionSet appendVerified(DmzProofWitnesses.Witness selected,int budget,List<DmzStoredGroundRoutes> history) throws Exception {
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        DmzProofWitnesses.Result before=DmzProofWitnesses.enumerate(graph,selected.node,budget);
        if(before.truncated || before.created>=budget)throw new IllegalStateException("Accumulated cache witness budget exceeded");
        DmzSourcedProofGraph next=graph.restrict(Collections.singletonList(pattern));
        DmzProofWitnesses.Result after=DmzProofWitnesses.enumerate(next,selected.node,budget-before.created);
        if(after.truncated)throw new IllegalStateException("Accumulated cache witness budget exceeded");
        if(before.witnesses.size()-after.witnesses.size()!=1)throw new IllegalStateException("New exact cache choice required");
        List<DmzProofWitnesses.Witness> choices=new ArrayList<DmzProofWitnesses.Witness>(selections);choices.add(selected);
        List<DmzProofWitnesses.NoGood> rules=new ArrayList<DmzProofWitnesses.NoGood>(patterns);rules.add(pattern);
        return new DmzCacheRestrictionSet(evidence,choices,rules,next,history);
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
            DmzStoredGroundRoutes receipt;
            try { receipt=evidence.routes(endpoint,target,budget); }
            catch(IllegalStateException uncovered) {
                if(uncovered.getMessage()!=null && uncovered.getMessage().contains("Covered live"))
                    throw new UncoveredConflict(uncovered.getMessage());
                throw uncovered;
            }
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
        if(pairs.isEmpty())throw new UncoveredConflict("Covered live successive conflict required");
        if(!isCurrent(target) || !inventory.isCurrent(target))throw new IllegalStateException("Remaining successive conflict boundary changed");
        return new Next(this,remaining,pairs,history);
    }
    static Continuation classify(DmzCacheRestrictionSet prefix,DmzTypedNativeResume.Result result,Mind target,int budget) throws Exception {
        if(prefix==null || result==null || !result.belongsTo(prefix,target))
            throw new IllegalStateException("Owned accumulated continuation result required");
        if(result.compiled)return new Continuation(ContinuationStatus.COMPLETE,result,null,prefix.selections.size());
        try { return new Continuation(ContinuationStatus.NEXT_CONFLICT,result,prefix.next(result,target,budget),prefix.selections.size()); }
        catch(UncoveredConflict uncovered) {
            return new Continuation(ContinuationStatus.STOPPED_UNCOVERED,result,null,prefix.selections.size());
        }
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
    SourceReceipt prepareSourceReceipt(List<DmzCandidateSession.Participant> participants,Mind target,int budget) throws Exception {
        if(budget<1 || budget>10000 || participants==null || !isCurrent(target))
            throw new IllegalStateException("Current accumulated source preparation required");
        Map<String,DmzSourceShape.Declaration> declarations=new HashMap<String,DmzSourceShape.Declaration>();
        Map<java.util.UUID,Long> revisions=new HashMap<java.util.UUID,Long>();
        int remaining=budget;
        for(DmzCandidateSession.Participant participant:participants) {
            if(participant==null)throw new IllegalStateException("Acquired source participant required");
            Long old=revisions.put(participant.revision.getContextId(),participant.revision.getRevision());
            if(old!=null && old.longValue()!=participant.revision.getRevision())
                throw new IllegalStateException("One exact source revision per Context required");
            for(DmzSourceShape.Declaration declaration:participant.inputs.declarations) {
                if(--remaining<1)throw new IllegalStateException("Accumulated source preparation budget exceeded");
                String key=participant.revision.getContextId()+"@"+participant.revision.getRevision()+":"+declaration.sourceRule;
                if(declarations.put(key,declaration)!=null)throw new IllegalStateException("Unique prepared source rule required");
            }
        }
        for(DmzProofWitnesses.Witness selected:selections) {
            for(DmzProofWitnesses.Witness current=selected;current!=null;current=current.premises.isEmpty()?null:current.premises.get(0)) {
                if(--remaining<1)throw new IllegalStateException("Accumulated source chain budget exceeded");
                DmzReplayProvenance.Binding source=current.source;
                if(source==null || source.authority==null)throw new IllegalStateException("Sourced accumulated chain required");
                String key=source.context+"@"+source.revision+":"+source.sourceRule;
                DmzSourceShape.Declaration declaration=declarations.get(key);
                if(declaration==null || current.step>=0 && declaration.unitStatement==null
                        || current.step<0 && declaration.groundStatement==null)
                    throw new IllegalStateException("Selected restriction source is absent from prepared exact revisions");
            }
        }
        if(!isCurrent(target))throw new IllegalStateException("Accumulated source preparation boundary changed");
        Map<String,RequiredSource> required=new HashMap<String,RequiredSource>();
        for(DmzProofWitnesses.Witness selected:selections)
            for(DmzProofWitnesses.Witness current=selected;current!=null;current=current.premises.isEmpty()?null:current.premises.get(0)) {
                DmzReplayProvenance.Binding source=current.source;
                String key=source.context+"@"+source.revision+":"+source.sourceRule;
                required.put(key,new RequiredSource(current.step>=0,declarations.get(key).statement));
            }
        List<DmzSourcesQuery.Proof> proofs=new ArrayList<DmzSourcesQuery.Proof>();
        for(DmzProofWitnesses.Witness selected:selections)proofs.add(publicProof(selected,declarations));
        return new SourceReceipt(this,required,proofs);
    }
    PreparedSources attachSourceReceipt(DmzCandidateSession session,List<DmzCandidateSession.Participant> participants,
            SourceReceipt receipt,int budget) throws Exception {
        if(budget<1 || budget>10000 || session==null || participants==null || receipt==null
                || receipt.prefix!=this || !session.isCurrent())
            throw new IllegalStateException("Owned accumulated source receipt required");
        Map<String,DmzSourceShape.Declaration> declarations=new HashMap<String,DmzSourceShape.Declaration>();
        int remaining=budget;
        for(DmzCandidateSession.Participant participant:participants)for(DmzSourceShape.Declaration declaration:participant.inputs.declarations) {
            if(--remaining<1)throw new IllegalStateException("Accumulated source attachment budget exceeded");
            declarations.put(participant.revision.getContextId()+"@"+participant.revision.getRevision()+":"+declaration.sourceRule,declaration);
        }
        for(Map.Entry<String,RequiredSource> entry:receipt.required.entrySet()) {
            if(--remaining<1)throw new IllegalStateException("Accumulated source attachment budget exceeded");
            DmzSourceShape.Declaration declaration=declarations.get(entry.getKey());
            RequiredSource wanted=entry.getValue();
            if(declaration==null || !wanted.statement.equals(declaration.statement)
                    || wanted.production && declaration.unitStatement==null
                    || !wanted.production && declaration.groundStatement==null)
                throw new IllegalStateException("Prepared sources do not match accumulated receipt");
        }
        if(!session.isCurrent())throw new IllegalStateException("Prepared source session changed");
        return new PreparedSources(this,session,receipt.proofs);
    }
    static final class ProofProjectionBudgetExceeded extends IllegalStateException {
        private static final long serialVersionUID=1L;
        ProofProjectionBudgetExceeded() { super("Detached source proof budget exceeded"); }
    }
    static DmzSourcesQuery.Proof publicProof(DmzProofWitnesses.Witness witness,
            Map<String,DmzSourceShape.Declaration> declarations) {
        return publicProof(witness,declarations,new int[]{10000});
    }
    static DmzSourcesQuery.Proof publicProof(DmzProofWitnesses.Witness witness,
            Map<String,DmzSourceShape.Declaration> declarations,int[] remaining) {
        if(--remaining[0]<0)throw new ProofProjectionBudgetExceeded();
        DmzReplayProvenance.Binding source=witness.source;
        String key=source.context+"@"+source.revision+":"+source.sourceRule;
        DmzSourceShape.Declaration declaration=declarations.get(key);
        if(declaration==null)throw new IllegalStateException("Prepared proof source disappeared");
        TerminalSupportCapture.Ground ground=witness.graph.observed.nodes.get(witness.node).ground;
        if(ground.arguments.size()!=1)throw new IllegalStateException("Public source proof requires unary ground");
        Object value=ground.arguments.get(0).materialize().getValue();
        if(!(value instanceof Number))throw new IllegalStateException("Public source proof requires integer ground");
        java.math.BigInteger integer=new java.math.BigDecimal(value.toString()).toBigIntegerExact();
        String conclusion="!"+(ground.sign?"":"~")+ground.predicate+"("+integer+");";
        List<DmzSourcesQuery.Proof> premises=new ArrayList<DmzSourcesQuery.Proof>();
        for(DmzProofWitnesses.Witness premise:witness.premises)premises.add(publicProof(premise,declarations,remaining));
        DmzSourcesQuery.Input input=new DmzSourcesQuery.Input(
                new org.kanger.interfaces.IContextResults.Revision(source.context,source.revision),source.sourceRule,declaration.statement);
        return new DmzSourcesQuery.Proof(input,conclusion,premises);
    }
}
