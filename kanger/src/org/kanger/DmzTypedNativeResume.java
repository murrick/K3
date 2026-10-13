/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.primitives.Solve;

/** One owned rollback-only typed resume. No arbitrary query/input or exposed child Mind. */
final class DmzTypedNativeResume {
    static final class Materialization {
        final long nativeRule;
        final boolean generated;
        final TerminalSupportCapture.Ground ground;
        Materialization(IRule rule,Mind mind) throws Exception {
            nativeRule=rule.getId();generated=mind.getRules().isGenerated(rule);
            ground=DmzGroundIdentity.capture(rule,mind).ground();
        }
    }
    static final class Result {
        final boolean compiled,provisional;
        final boolean complete=false;
        final int applicationDenied,queryDenied;
        final DmzSourcedProofGraph graph;
        final List<CollisionProofCapture.Conflict> conflicts;
        final List<Long> retractedCaches;
        final List<Materialization> materializations;
        private final DmzCacheRestrictionSet prefix;
        private final Mind target;
        private final String state;
        private final DmzReplayProvenance.SourceCheckpoint sources;
        boolean belongsTo(DmzCacheRestrictionSet candidate,Mind boundary) throws Exception {
            return prefix!=null && candidate==prefix && target==boundary && prefix.isCurrent(boundary)
                    && sources.isCurrent(boundary) && state.equals(DmzObservationStateFingerprint.capture(boundary));
        }
        private Result(boolean compiled,Scope scope,DmzSourcedProofGraph graph,List<CollisionProofCapture.Conflict> conflicts,
                List<Rule> removed,Mind branch,int budget,boolean provisional,DmzCacheRestrictionSet prefix,Mind target) throws Exception {
            this.prefix=prefix;this.target=target;state=DmzObservationStateFingerprint.capture(target);sources=DmzReplayProvenance.sourceCheckpoint(target);
            this.compiled=compiled;this.provisional=provisional;applicationDenied=scope.denied;queryDenied=scope.queryDenied;this.graph=graph;
            this.conflicts=Collections.unmodifiableList(new ArrayList<CollisionProofCapture.Conflict>(conflicts));
            List<Long> ids=new ArrayList<Long>();for(Rule rule:removed)ids.add(rule.getId());
            retractedCaches=Collections.unmodifiableList(ids);
            List<Materialization> live=new ArrayList<Materialization>();
            for(IRule rule:branch.getRules()) {
                if(--budget<0)throw new IllegalStateException("Native materialization snapshot budget exceeded");
                Rule actual=(Rule)rule;
                if(!rule.isDeleted(branch) && !rule.isQuery() && actual.getTree().size()==1 && actual.getTree().get(0).size()==1)
                    live.add(new Materialization(rule,branch));
            }
            materializations=Collections.unmodifiableList(live);
        }
    }
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<Scope>();
    private static final class Choice {
        final DmzProofWitnesses.Witness selected;
        final DmzProofWitnesses.NoGood pattern;
        final TerminalSupportCapture.Ground root,premise;
        final List<TerminalSupportCapture.Binding> bindings;
        Choice(DmzProofWitnesses.Witness selected) {
            this.selected=selected;pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
            root=selected.graph.observed.nodes.get(selected.node).ground;
            premise=selected.graph.observed.nodes.get(selected.premises.get(0).node).ground;
            bindings=selected.graph.observed.steps.get(selected.step).application.bindings;
        }
    }
    private static final class Scope implements AutoCloseable {
        final Mind boundary;
        final List<Choice> choices=new ArrayList<Choice>();
        final List<DmzProofWitnesses.NoGood> patterns=new ArrayList<DmzProofWitnesses.NoGood>();
        final List<DmzProofWitnesses.Witness> retained;
        int denied,queryDenied;
        Scope(Mind boundary,List<DmzProofWitnesses.Witness> selected,List<DmzProofWitnesses.Witness> retained) {
            if(ACTIVE.get()!=null)throw new IllegalStateException("Nested typed resume is not qualified");
            this.boundary=boundary;this.retained=retained;
            for(DmzProofWitnesses.Witness witness:selected){Choice choice=new Choice(witness);choices.add(choice);patterns.add(choice.pattern);}
            ACTIVE.set(this);
        }
        boolean local(Mind mind) {
            for(Mind current=mind;current!=null;current=(Mind)current.getNext())if(current==boundary)return true;
            return false;
        }
        boolean alternative(Choice choice,boolean exactAssignment) {
            for(DmzProofWitnesses.Witness witness:retained)
                if(witness.step>=0 && witness.source.nativeRule==choice.selected.source.nativeRule && witness.premises.size()==1
                        && choice.root.equivalent(witness.graph.observed.nodes.get(witness.node).ground)
                        && (!exactAssignment || choice.premise.equivalent(witness.graph.observed.nodes.get(witness.premises.get(0).node).ground)
                            && DmzProofWitnesses.NoGood.sameBindings(choice.bindings,witness.graph.observed.steps.get(witness.step).application.bindings)))return true;
            return false;
        }
        public void close(){if(ACTIVE.get()!=this)throw new IllegalStateException("Typed resume ownership changed");ACTIVE.remove();}
    }
    static Result run(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return run(guard,proof,selected,target,budget,false,false,null,null,null);
    }
    static Result runMaterialized(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return run(guard,proof,selected,target,budget,true,false,null,null,null);
    }
    static Result runRecursive(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return run(guard,proof,selected,target,budget,true,true,null,null,null);
    }
    static Result runPending(TechnicalMindTransaction owner,DmzGroundCollisionRestriction guard,
            DmzProvisionalCollisionProof proof,DmzProofWitnesses.Witness selected,int budget) throws Exception {
        Mind target=owner.pendingBoundary();
        return run(guard,proof,selected,target,budget,true,selected!=null && selected.step>=0
                && selected.premises.get(0).step>=0,owner,null,null);
    }
    static Result runCache(DmzCacheCollisionEvidence evidence,DmzProofWitnesses.Witness selected,
            Mind target,int budget) throws Exception {
        return run(null,null,selected,target,budget,true,selected!=null && selected.step>=0
                && selected.premises.get(0).step>=0,null,evidence,null);
    }
    static Result runAccumulated(DmzCacheRestrictionSet set,Mind target,int budget) throws Exception {
        if(set==null || set.selections.isEmpty())throw new IllegalArgumentException("Nonempty cache restrictions required");
        DmzProofWitnesses.Witness first=set.selections.get(0);
        return run(null,null,first,target,budget,true,first.premises.get(0).step>=0,null,set.evidence,set);
    }
    private static Result run(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget,boolean materialized,boolean recursive,TechnicalMindTransaction owner,DmzCacheCollisionEvidence evidence,DmzCacheRestrictionSet set) throws Exception {
        if(target.hasPendingTransactions())throw new IllegalStateException("Exclusive typed resume boundary required");
        if(ACTIVE.get()!=null || !DmzTerminalRestriction.activeNoGoods(target).isEmpty())
            throw new IllegalStateException("Unrestricted typed resume boundary required");
        boolean projected;
        if(set!=null)projected=set.isCurrent(target);
        else if(evidence!=null)projected=evidence.project(selected,target,budget).eligible;
        else projected=guard.project(proof,selected,target,budget).eligible;
        if(!projected || (!recursive && selected.premises.get(0).step>=0))
            throw new IllegalStateException("Current direct typed collision projection required");
        if(recursive && !DmzProofWitnesses.NoGood.fromTyped(selected).recursiveWithin(8))
            throw new IllegalStateException("Bounded recursive typed chain required");
        for(IRule rule:target.getRules())if(!materialized && !rule.isDeleted(target) && target.getRules().isGenerated(rule))
            throw new IllegalStateException("Cold typed resume cannot retract existing caches");
        DmzTypedCacheProjection cachePlan=materialized?(set!=null
                ?DmzTypedCacheProjection.projectAccumulated(set,target,budget):evidence!=null
                ?DmzTypedCacheProjection.projectCache(evidence,selected,target,budget):owner!=null
                ?DmzTypedCacheProjection.projectPending(owner,guard,proof,selected,budget):recursive
                ?DmzTypedCacheProjection.projectRecursive(guard,proof,selected,target,budget)
                :DmzTypedCacheProjection.project(guard,proof,selected,target,budget)):null;
        if(materialized && (!cachePlan.eligible || !cachePlan.isCurrent(target)))
            throw new IllegalStateException("Current settled typed cache plan required");
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,owner!=null);
        if(!inventory.eligible || (owner==null && inventory.provisional))throw new IllegalStateException("Settled typed inventory required");
        List<DmzProofWitnesses.Witness> selections=set==null?Collections.singletonList(selected):set.selections;
        List<DmzProofWitnesses.NoGood> patterns=set==null?Collections.singletonList(DmzProofWitnesses.NoGood.fromTyped(selected)):set.patterns;
        List<DmzProofWitnesses.Witness> retained=DmzCacheRestrictionSet.retained(inventory,selections,patterns,budget);
        List<Rule> cacheRules=new ArrayList<Rule>();
        if(materialized) {
            Map<Long,Rule> live=new HashMap<Long,Rule>();int remaining=budget;
            for(IRule item:target.getRules()) {
                if(--remaining<0)throw new IllegalStateException("Native cache lookup budget exceeded");
                if(!item.isDeleted(target) && live.put(item.getId(),(Rule)item)!=null)
                    throw new IllegalStateException("Unique native cache addresses required");
            }
            for(DmzTypedCacheProjection.Cache cache:cachePlan.caches)if(cache.losesLastSupport()) {
                if(--remaining<0)throw new IllegalStateException("Native cache lookup budget exceeded");
                Rule found=live.get(cache.nativeRule);
                if(found==null || !target.getRules().isGenerated(found)
                        || !DmzGroundIdentity.capture(found,target).ground().equivalent(cache.ground)
                        || !DmzReplayProvenance.observedSources(target,found.getId()).isEmpty())
                    throw new IllegalStateException("Exact generated-only cache retraction required");
                cacheRules.add(found);
            }
        }
        String state=DmzObservationStateFingerprint.capture(target);
        DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(target);
        // Fresh zero-arity predicate cannot supply bindings to the qualified nonempty-variable unit rules.
        String probe="dmz_internal_probe_"+UUID.randomUUID().toString().replace("-","");
        for(IRule item:target.getRules())for(List<Domain> row:((Rule)item).getTree())for(Domain domain:row)
            if(domain.getPredicate().getName(target).equals(probe))throw new IllegalStateException("Probe predicate already exists");
        if(materialized && !cachePlan.isCurrent(target))throw new IllegalStateException("Typed cache plan changed before native child");
        if(evidence!=null && !evidence.isCurrent(target))throw new IllegalStateException("Cache collision evidence changed before native child");
        if(owner!=null && owner.pendingBoundary()!=target)throw new IllegalStateException("Pending transaction ownership changed");
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(target)) {
            try(Scope scope=new Scope(tx.mind(),selections,retained);
                    TerminalSupportCapture capture=TerminalSupportCapture.begin(tx.mind());
                    CollisionProofCapture collisions=CollisionProofCapture.begin()) {
                for(Rule rule:cacheRules)rule.setDeleted(true,tx.mind());
                boolean compiled=tx.mind().compile("!"+probe+"();",null,false);
                List<TerminalSupportCapture.ApplicationSources> observations=new ArrayList<TerminalSupportCapture.ApplicationSources>();
                for(TerminalSupportCapture.ProvisionalApplication app:capture.provisionalSourceSnapshot()) {
                    List<List<DmzReplayProvenance.Binding>> supports=new ArrayList<List<DmzReplayProvenance.Binding>>();
                    for(List<DmzReplayProvenance.SourceObservation> support:app.supportSources)supports.add(labels(support));
                    observations.add(new TerminalSupportCapture.ApplicationSources(app.application,labels(app.ruleSources),supports,app.excludedPairs,app.noGoods));
                }
                // Rejected native frames remain event-owned diagnostics after their inner rollback.
                if(!compiled && !collisions.snapshot().isEmpty()) {
                    CollisionProofCapture.Conflict event=collisions.snapshot().get(collisions.snapshot().size()-1);
                    DmzSourcedProofGraph graph=DmzProvisionalCollisionProof.build(event,budget,budget).graph;
                    if(materialized)graph=graph.restrict(scope.patterns);
                    return new Result(false,scope,graph,collisions.snapshot(),cacheRules,tx.mind(),budget,inventory.provisional,set,target);
                }
                DmzSourcedProofGraph graph=DmzSourcedProofGraph.build(observations);
                if(materialized)graph=graph.restrict(scope.patterns);
                return new Result(compiled,scope,graph,collisions.snapshot(),cacheRules,tx.mind(),budget,inventory.provisional,set,target);
            }
        } finally {
            if(!state.equals(DmzObservationStateFingerprint.capture(target)) || !sources.isCurrent(target))
                throw new IllegalStateException("Typed resume changed authority boundary");
        }
    }
    private static List<DmzReplayProvenance.Binding> labels(List<DmzReplayProvenance.SourceObservation> observations) {
        List<DmzReplayProvenance.Binding> result=new ArrayList<DmzReplayProvenance.Binding>();
        for(DmzReplayProvenance.SourceObservation source:observations)
            if(source.outcome==DmzReplayProvenance.Outcome.ACCEPTED || source.outcome==DmzReplayProvenance.Outcome.PENDING)result.add(source.binding);
        return result;
    }
    static List<DmzProofWitnesses.NoGood> active(Mind mind) {
        Scope scope=ACTIVE.get();return scope!=null && scope.local(mind) ? Collections.unmodifiableList(scope.patterns) : Collections.<DmzProofWitnesses.NoGood>emptyList();
    }
    static boolean denies(Mind mind,Domain conclusion,List<Domain> tree) throws Exception {
        Scope scope=ACTIVE.get();
        if(scope==null || !scope.local(mind) || conclusion.getRule()==null)return false;
        for(Choice choice:scope.choices) {
            if(conclusion.getRule().getId()!=choice.selected.source.nativeRule)continue;
            TerminalSupportCapture.Ground actual=TerminalSupportCapture.Ground.capture(new Solve(conclusion.getPredicate(),conclusion.isAntc(),conclusion.getArguments().convertBase(mind)),mind);
            if(!choice.root.equivalent(actual))continue;
            if(tree.size()!=2)throw new IllegalStateException("Typed native unit shape changed");
            if(!DmzProofWitnesses.NoGood.sameBindings(choice.bindings,assignment(mind,tree)))continue;
            for(Domain premise:tree)if(premise!=conclusion) {
                if(!premise.isExcluded(mind))throw new IllegalStateException("Typed native premise is not satisfied");
                TerminalSupportCapture.Ground ground=TerminalSupportCapture.Ground.capture(new Solve(premise.getPredicate(),!premise.isAntc(),premise.getArguments().convertBase(mind)),mind);
                if(choice.premise.equivalent(ground) && !scope.alternative(choice,true)){++scope.denied;return true;}
            }
        }
        return false;
    }
    private static List<TerminalSupportCapture.Binding> assignment(Mind mind,List<Domain> tree) throws Exception {
        Map<Long,SemanticTermSnapshot> values=new TreeMap<Long,SemanticTermSnapshot>();
        for(Domain literal:tree)for(IArgument argument:literal.getArguments())if(argument.getType()==ArgumentType.TVARIABLE) {
            if(!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))throw new IllegalStateException("Ordinary native binding required");
            SemanticTermSnapshot value=SemanticTermSnapshot.capture(argument.getValue(mind));
            SemanticTermSnapshot old=values.put(argument.getId(),value);
            if(old!=null && !old.semanticallyEquals(value))throw new IllegalStateException("Native binding disagrees across unit literals");
        }
        List<TerminalSupportCapture.Binding> result=new ArrayList<TerminalSupportCapture.Binding>();
        for(Map.Entry<Long,SemanticTermSnapshot> value:values.entrySet())result.add(new TerminalSupportCapture.Binding(result.size(),value.getKey(),"native","native",value.getValue()));
        return result;
    }
    static boolean deniesQueryPair(Mind mind,Domain left,Domain right) throws Exception {
        Scope scope=ACTIVE.get();
        if(scope==null || !scope.local(mind))return false;
        if(mind.getQueryPass()!=org.kanger.enums.QueryPass.ACCEPT && mind.getQueryPass()!=org.kanger.enums.QueryPass.CHECKTRUE
                && mind.getQueryPass()!=org.kanger.enums.QueryPass.CHECKFALSE)return false;
        if(query(scope,mind,left,right) || query(scope,mind,right,left)) {++scope.queryDenied;return true;}
        return false;
    }
    private static boolean query(Scope scope,Mind mind,Domain production,Domain opposite) throws Exception {
        for(Choice choice:scope.choices) {
            if(production.getRule()==null || production.getRule().getId()!=choice.selected.source.nativeRule
                    || production.isAntc()!=choice.root.sign || !production.getPredicate().getName(mind).equals(choice.root.predicate))continue;
            TerminalSupportCapture.Ground wanted=TerminalSupportCapture.Ground.capture(new Solve(opposite.getPredicate(),!opposite.isAntc(),opposite.getArguments().convertBase(mind)),mind);
            if(choice.root.equivalent(wanted) && !scope.alternative(choice,false))return true;
        }
        return false;
    }
    static List<DmzProofWitnesses.NoGood> patterns(Mind mind,long rule,TerminalSupportCapture.Ground root,
            TerminalSupportCapture.Ground premise,List<TerminalSupportCapture.Binding> bindings) {
        Scope scope=ACTIVE.get();List<DmzProofWitnesses.NoGood> result=new ArrayList<DmzProofWitnesses.NoGood>();
        if(scope!=null && scope.local(mind))for(Choice choice:scope.choices)
            if(rule==choice.selected.source.nativeRule && choice.root.equivalent(root) && choice.premise.equivalent(premise)
                    && DmzProofWitnesses.NoGood.sameBindings(choice.bindings,bindings))result.add(choice.pattern);
        return result;
    }
}
