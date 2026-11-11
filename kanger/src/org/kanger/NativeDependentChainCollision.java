/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextDependentProductionInspection.Donor;
import org.kanger.ContextDependentProductionInspection.Step;
import org.kanger.interfaces.*;
import org.kanger.primitives.Solve;
import org.kanger.units.*;

/** Isolated authoring candidate: declaration proofs repair cache-based comparisons, not cache coverage or query history. */
final class NativeDependentChainCollision {
    private NativeDependentChainCollision() {}
    private static final ContextDependentReplayInspection.Policy POLICY=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    private static final class Scope {
        final ContextProductionInventory inventory;
        final List<Rule> roots=new ArrayList<>(),receivers=new ArrayList<>(),horns=new ArrayList<>();
        Scope(ContextProductionInventory inventory){this.inventory=inventory;}
    }
    private static void current(Mind mind,ContextProductionInventory expected)throws Exception{
        if(!DmzTerminalRestriction.activeNoGoods(mind).isEmpty())throw new IllegalStateException("Restricted branches require separate chain qualification");
        ContextDependentReplayInspection.current(mind,expected,POLICY);
    }
    private static Scope scope(Mind mind)throws Exception{
        if(!DmzTerminalRestriction.activeNoGoods(mind).isEmpty())return null;
        List<Rule> potential=new ArrayList<>(),receivers=new ArrayList<>(),horns=new ArrayList<>();
        Runnable work=ContextGroundProductionRounds.budget(1000000);
        for(IRule item:mind.getRules()){
            work.run();Rule rule=(Rule)item;if(rule.isDeleted(mind))continue;if(rule.isQuery())return null;if(rule.isGenerated()||rule.getTree().size()!=1)continue;
            List<Domain> literals=rule.getTree().get(0);String origin=rule.getOrigin();
            if(literals.size()==1&&literals.get(0).isAntc()&&origin!=null&&origin.indexOf('$')>=0)potential.add(rule);
            else if(literals.size()==2){int heads=0;for(Domain d:literals)if(d.isAntc())heads++;if(ordinary(rule,mind,work)){if(heads==0)receivers.add(rule);else if(heads==1)horns.add(rule);}}
        }
        if(potential.isEmpty()||receivers.isEmpty()||horns.isEmpty())return null;
        Scope scope=new Scope(ContextProductionInventory.inspect(mind,UUID.randomUUID(),0,true,Collections.emptyList(),POLICY.rows));
        scope.receivers.addAll(receivers);scope.horns.addAll(horns);
        for(Rule rule:potential){
            NativePrenexUnitScope prefix;try{prefix=NativePrenexUnitScope.observeFactoryRow(rule.getId(),mind,work);}catch(UnsupportedOperationException unsupported){continue;}
            for(NativePrenexUnitScope.Binder binder:prefix.binders)if(!binder.universal){scope.roots.add(rule);break;}
        }
        return scope.roots.isEmpty()?null:scope;
    }
    private static boolean ordinary(Rule rule,Mind mind,Runnable work)throws Exception{
        for(Domain domain:rule.getTree().get(0)){
            work.run();if(domain.isSystem(mind)||domain.isCalculated(mind))return false;
            for(IArgument arg:domain.getArguments()){
                work.run();if(arg.getType()==org.kanger.enums.ArgumentType.TVARIABLE){if(((TVariable)arg.getObject(mind)).getRuleId()!=rule.getId())return false;}
                else if(arg.getType()!=org.kanger.enums.ArgumentType.TERM||!SemanticTermSnapshot.isOrdinaryValue(arg.getValue(mind)))return false;
            }
        }return true;
    }
    private static boolean predicate(Domain a,Domain b,Mind mind)throws Exception{return a.getRange()==b.getRange()&&a.getPredicate(mind).getName(mind).equals(b.getPredicate(mind).getName(mind));}
    private static Domain head(Rule rule)throws Exception{for(Domain d:rule.getTree().get(0))if(d.isAntc())return d;return null;}
    private static Domain body(Rule rule)throws Exception{for(Domain d:rule.getTree().get(0))if(!d.isAntc())return d;return null;}
    private static List<Rule> routes(Scope scope,Rule root,Rule receiver,Mind mind)throws Exception{
        List<Rule> routes=new ArrayList<>();for(Rule horn:scope.horns)if(predicate(body(horn),root.getDomain(),mind)
                &&predicate(head(horn),receiver.getTree().get(0).get(0),mind)&&predicate(head(horn),receiver.getTree().get(0).get(1),mind))routes.add(horn);return routes;
    }
    static ContextQualification.CollisionWitness find(Mind mind)throws Exception{
        Scope scope=scope(mind);if(scope==null)return null;int attempts=0;
        try{
            for(Rule receiver:scope.receivers)for(Rule root:scope.roots){
                List<Rule> routes=routes(scope,root,receiver,mind);
                for(int i=0;i<routes.size();i++)for(int j=i;j<routes.size();j++){
                    if(++attempts>16)throw new IllegalStateException("Dependent chain refutation attempt bound");
                    Rule a=routes.get(i),b=routes.get(j);Set<Long> selected=new HashSet<>(Arrays.asList(root.getId(),a.getId(),b.getId(),receiver.getId()));
                    NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,scope.inventory,selected);
                    List<Step> steps=Arrays.asList(new Step(theory.ids.get(a.getId()),Collections.singletonList(Donor.original(theory.ids.get(root.getId())))),
                            new Step(theory.ids.get(b.getId()),Collections.singletonList(Donor.original(theory.ids.get(root.getId())))));
                    ContextDependentProductionInspection.Request request=new ContextDependentProductionInspection.Request(POLICY,1000,steps);
                    ContextDependentProductionInspection.Result checked=ContextDependentProductionInspection.inspect(theory.copy,theory.inventory,request);
                    if(checked.originalUnitRefutations.isEmpty()&&checked.finalUnitRefutations.isEmpty())continue;
                    if(CollisionProofCapture.isActive())CollisionProofCapture.recordDependentProduction(ContextDependentProductionReplay.capture(theory.copy,theory.inventory,request));
                    return ContextQualification.witness(receiver.getOrigin(),root.getOrigin()+" "+a.getOrigin()+" "+b.getOrigin());
                }
            }
            return null;
        }finally{current(mind,scope.inventory);}
    }
    static boolean rejectsLegacyJoin(Mind mind,List<Domain> tree)throws Exception{
        if(tree.size()!=2||tree.get(0).isAntc()||tree.get(1).isAntc())return false;
        Rule receiver=(Rule)tree.get(0).getRule();if(receiver==null||receiver.isGenerated()||receiver.isQuery()||receiver.getTree().size()!=1
                ||receiver.getTree().get(0).size()!=2||tree.get(1).getRuleId()!=receiver.getId())return false;
        // Dispatch only an actual symbolic generated join with primary Horn producers.
        Set<Long> producingRules=new HashSet<>();
        for(Domain literal:tree){
            if(!literal.isComplete()||literal.getArguments().getCVariables(mind).isEmpty())return false;
            IRule donor=mind.getRules().find(new Solve(literal.getPredicate(),true,literal.getArguments().convertBase(mind)));
            if(donor==null||!donor.isGenerated()||donor.getCauses().isEmpty())return false;
            for(ICause cause:donor.getCauses()){IRule rule=cause.getRule(mind);if(rule==null||rule.isGenerated())return false;producingRules.add(rule.getId());}
        }
        Scope scope=scope(mind);if(scope==null)return false;boolean selected=false;
        for(Rule root:scope.roots){List<Rule> routes=routes(scope,root,receiver,mind);Set<Long> candidates=new HashSet<>();for(Rule rule:routes)candidates.add(rule.getId());if(candidates.containsAll(producingRules)){selected=true;break;}}
        if(!selected)return false;
        try{
            NativeDependentPrimaryTheory theory=NativeDependentPrimaryTheory.rebuild(mind,scope.inventory,null);
            ContextDependentModelSearchInspection.Result model=ContextDependentAutomaticInspection.inspect(new ContextDependentCutInspection.Member(theory.copy,theory.inventory),Collections.emptyList(),
                    new ContextDependentIterationInspection.Request(POLICY,10000,8),
                    new ContextDependentModelSearchInspection.Limits(30,100000,100000,20000,64000000,64000000,64000000,64000000,64000000),
                    new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
            if(model.status==ContextDependentModelSearchInspection.Status.MODELLED_PENDING)return true;
            if(model.status==ContextDependentModelSearchInspection.Status.REFUTED)return false;
            throw new IllegalStateException(model.phase+": "+model.reason);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        catch(IllegalStateException | IllegalArgumentException | UnsupportedOperationException unsupported){
            org.kanger.exception.ContextProductionCommitException failure=org.kanger.exception.ContextProductionCommitException.incomplete("Dependent chain declaration qualification incomplete: "+unsupported.getMessage(),null);
            failure.initCause(unsupported);throw failure;
        }finally{current(mind,scope.inventory);}
    }
}
