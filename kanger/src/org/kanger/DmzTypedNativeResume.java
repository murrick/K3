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
        final boolean compiled;
        final boolean complete=false;
        final int applicationDenied,queryDenied;
        final DmzSourcedProofGraph graph;
        final List<CollisionProofCapture.Conflict> conflicts;
        final List<Long> retractedCaches;
        final List<Materialization> materializations;
        Result(boolean compiled,Scope scope,DmzSourcedProofGraph graph,List<CollisionProofCapture.Conflict> conflicts,
                List<Rule> removed,Mind branch,int budget) throws Exception {
            this.compiled=compiled;applicationDenied=scope.denied;queryDenied=scope.queryDenied;this.graph=graph;
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
    private static final class Scope implements AutoCloseable {
        final Mind boundary;
        final DmzProofWitnesses.Witness selected;
        final DmzProofWitnesses.NoGood pattern;
        final List<DmzProofWitnesses.Witness> retained;
        final TerminalSupportCapture.Ground root,premise;
        final List<TerminalSupportCapture.Binding> bindings;
        int denied,queryDenied;
        Scope(Mind boundary,DmzProofWitnesses.Witness selected,List<DmzProofWitnesses.Witness> retained) {
            if(ACTIVE.get()!=null)throw new IllegalStateException("Nested typed resume is not qualified");
            this.boundary=boundary;this.selected=selected;this.retained=retained;
            pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
            root=selected.graph.observed.nodes.get(selected.node).ground;
            premise=selected.graph.observed.nodes.get(selected.premises.get(0).node).ground;
            bindings=selected.graph.observed.steps.get(selected.step).application.bindings;
            ACTIVE.set(this);
        }
        boolean local(Mind mind) {
            for(Mind current=mind;current!=null;current=(Mind)current.getNext())if(current==boundary)return true;
            return false;
        }
        boolean alternative(boolean exactAssignment) {
            for(DmzProofWitnesses.Witness witness:retained)
                if(witness.step>=0 && witness.source.nativeRule==selected.source.nativeRule
                        && witness.premises.size()==1
                        && (!exactAssignment || premise.equivalent(witness.graph.observed.nodes.get(witness.premises.get(0).node).ground)
                            && DmzProofWitnesses.NoGood.sameBindings(bindings,
                            witness.graph.observed.steps.get(witness.step).application.bindings)))return true;
            return false;
        }
        public void close() {
            if(ACTIVE.get()!=this)throw new IllegalStateException("Typed resume ownership changed");
            ACTIVE.remove();
        }
    }
    static Result run(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return run(guard,proof,selected,target,budget,false);
    }
    static Result runMaterialized(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget) throws Exception {
        return run(guard,proof,selected,target,budget,true);
    }
    private static Result run(DmzGroundCollisionRestriction guard,DmzProvisionalCollisionProof proof,
            DmzProofWitnesses.Witness selected,Mind target,int budget,boolean materialized) throws Exception {
        if(target.hasPendingTransactions())throw new IllegalStateException("Exclusive typed resume boundary required");
        if(ACTIVE.get()!=null || !DmzTerminalRestriction.activeNoGoods(target).isEmpty())
            throw new IllegalStateException("Unrestricted typed resume boundary required");
        DmzGroundCollisionRestriction.Result projection=guard.project(proof,selected,target,budget);
        if(!projection.eligible || selected.premises.get(0).step>=0)
            throw new IllegalStateException("Current direct typed collision projection required");
        for(IRule rule:target.getRules())if(!materialized && !rule.isDeleted(target) && target.getRules().isGenerated(rule))
            throw new IllegalStateException("Cold typed resume cannot retract existing caches");
        DmzTypedCacheProjection cachePlan=materialized?DmzTypedCacheProjection.project(guard,proof,selected,target,budget):null;
        if(materialized && (!cachePlan.eligible || !cachePlan.isCurrent(target)))
            throw new IllegalStateException("Current settled typed cache plan required");
        DmzCurrentGroundProofInventory inventory=DmzCurrentGroundProofInventory.capture(target,budget,false);
        if(!inventory.eligible || inventory.provisional)throw new IllegalStateException("Settled typed inventory required");
        DmzCurrentGroundProofInventory.Proofs choices=inventory.proofs(selected.graph.observed.nodes.get(selected.node).ground,budget);
        if(choices.truncated)throw new IllegalStateException("Typed resume witness budget exceeded");
        DmzProofWitnesses.NoGood pattern=DmzProofWitnesses.NoGood.fromTyped(selected);
        List<DmzProofWitnesses.Witness> retained=new ArrayList<DmzProofWitnesses.Witness>();
        int removed=0;
        for(DmzProofWitnesses.Witness choice:choices.witnesses)
            if(pattern.matches(choice.graph,choice.node,choice.step,choice.source,choice.premises))++removed;
            else retained.add(choice);
        if(removed!=1)throw new IllegalStateException("Exact typed source choice required");
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
        try(TechnicalMindTransaction tx=TechnicalMindTransaction.beginIsolated(target)) {
            try(Scope scope=new Scope(tx.mind(),selected,retained);
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
                    if(materialized)graph=graph.restrict(Collections.singletonList(scope.pattern));
                    return new Result(false,scope,graph,collisions.snapshot(),cacheRules,tx.mind(),budget);
                }
                DmzSourcedProofGraph graph=DmzSourcedProofGraph.build(observations);
                if(materialized)graph=graph.restrict(Collections.singletonList(scope.pattern));
                return new Result(compiled,scope,graph,collisions.snapshot(),cacheRules,tx.mind(),budget);
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
        Scope scope=ACTIVE.get();return scope!=null && scope.local(mind) ? Collections.singletonList(scope.pattern) : Collections.<DmzProofWitnesses.NoGood>emptyList();
    }
    static boolean denies(Mind mind,Domain conclusion,List<Domain> tree) throws Exception {
        Scope scope=ACTIVE.get();
        if(scope==null || !scope.local(mind) || conclusion.getRule()==null || conclusion.getRule().getId()!=scope.selected.source.nativeRule)return false;
        TerminalSupportCapture.Ground actual=TerminalSupportCapture.Ground.capture(new Solve(conclusion.getPredicate(),conclusion.isAntc(),conclusion.getArguments().convertBase(mind)),mind);
        if(!scope.root.equivalent(actual))return false;
        if(tree.size()!=2)throw new IllegalStateException("Typed native unit shape changed");
        List<TerminalSupportCapture.Binding> bindings=assignment(mind,tree);
        if(!DmzProofWitnesses.NoGood.sameBindings(scope.bindings,bindings))return false;
        for(Domain premise:tree)if(premise!=conclusion) {
            if(!premise.isExcluded(mind))throw new IllegalStateException("Typed native premise is not satisfied");
            TerminalSupportCapture.Ground ground=TerminalSupportCapture.Ground.capture(new Solve(premise.getPredicate(),!premise.isAntc(),premise.getArguments().convertBase(mind)),mind);
            if(scope.premise.equivalent(ground) && !scope.alternative(true)) {++scope.denied;return true;}
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
        if(production.getRule()==null || production.getRule().getId()!=scope.selected.source.nativeRule
                || production.isAntc()!=scope.root.sign || !production.getPredicate().getName(mind).equals(scope.root.predicate))return false;
        TerminalSupportCapture.Ground wanted=TerminalSupportCapture.Ground.capture(new Solve(opposite.getPredicate(),!opposite.isAntc(),opposite.getArguments().convertBase(mind)),mind);
        return scope.root.equivalent(wanted) && !scope.alternative(false);
    }
    static List<DmzProofWitnesses.NoGood> patterns(Mind mind,long rule,TerminalSupportCapture.Ground root,
            TerminalSupportCapture.Ground premise,List<TerminalSupportCapture.Binding> bindings) {
        Scope scope=ACTIVE.get();
        return scope!=null && scope.local(mind) && rule==scope.selected.source.nativeRule && scope.root.equivalent(root)
                && scope.premise.equivalent(premise) && DmzProofWitnesses.NoGood.sameBindings(scope.bindings,bindings)
                ? Collections.singletonList(scope.pattern) : Collections.<DmzProofWitnesses.NoGood>emptyList();
    }
}
