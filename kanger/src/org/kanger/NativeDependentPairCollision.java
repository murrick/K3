/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.List;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Refutation-only authoring candidate for one existential unit and a binary prohibition. */
final class NativeDependentPairCollision {
    private NativeDependentPairCollision() { }

    /** Returns true only for a legacy join disproved by replay of its two exact primary roots. */
    static boolean rejectsLegacyJoin(Mind mind,List<Domain> tree) throws Exception {
        if(tree.size()!=2 || tree.get(0).isAntc() || tree.get(1).isAntc())return false;
        Rule receiver=(Rule)tree.get(0).getRule();
        if(receiver==null || receiver.isQuery() || receiver.isGenerated()
                || receiver.getTree().size()!=1 || receiver.getTree().get(0).size()!=2
                || tree.get(1).getRuleId()!=receiver.getId())return false;
        Rule producer=null;
        for(Domain premise:tree) {
            if(premise.isSystem(mind) || premise.isCalculated(mind) || !premise.isComplete()
                    || premise.getArguments().getCVariables(mind).isEmpty())return false;
            org.kanger.primitives.Solve wanted=new org.kanger.primitives.Solve(premise.getPredicate(),true,
                    premise.getArguments().convertBase(mind));
            IRule donor=mind.getRules().find(wanted);
            if(donor==null || donor.isDeleted(mind) || !donor.isGenerated() || donor.getCauses().isEmpty())return false;
            for(org.kanger.interfaces.ICause cause:donor.getCauses()) {
                IRule origin=cause.getRule(mind);
                if(!(origin instanceof Rule) || origin.isGenerated() || origin.isDeleted(mind)
                        || origin.isQuery() || cause.getDonor(mind)!=null
                        || producer!=null && producer.getId()!=origin.getId())return false;
                producer=(Rule)origin;
            }
        }
        if(producer==null || producer.getOrigin()==null || producer.getOrigin().indexOf('$')<0)return false;
        NativePrenexUnitScope scope;
        try {scope=NativePrenexUnitScope.observeFactoryRow(producer.getId(),mind,ContextGroundProductionRounds.budget(100000));}
        catch(UnsupportedOperationException unsupported){return false;}
        if(scope.binders.stream().allMatch(binder->binder.universal))return false;
        for(Domain literal:receiver.getTree().get(0))for(org.kanger.interfaces.IArgument argument:literal.getArguments())
            if(argument.getType()!=org.kanger.enums.ArgumentType.TVARIABLE
                    && (argument.getType()!=org.kanger.enums.ArgumentType.TERM
                    || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))))return false;
        try(NativeDependentMatchFrame frame=NativeDependentMatchFrame.openPair(mind,producer.getId(),receiver.getId())) {
            NativeDependentMatchFrame.Entry source=frame.pairEntry(producer.getId());
            NativeDependentMatchFrame.Match first=frame.match(source,frame.instantiate(frame.pairEntry(receiver.getId()),0),0);
            if(first==null)return false;
            frame.verify(first);
            return frame.match(source,first.after,0)==null;
        }
    }

    static ContextQualification.CollisionWitness find(Mind mind) throws Exception {
        Runnable work=ContextGroundProductionRounds.budget(100000);
        List<Rule> producers=new ArrayList<>(),receivers=new ArrayList<>();
        for(IRule item:mind.getRules()) {
            work.run();
            Rule rule=(Rule)item;
            if(rule.isGenerated() || rule.isQuery() || rule.isDeleted(mind) || rule.getTree().size()!=1)continue;
            List<Domain> branch=rule.getTree().get(0);
            if(branch.size()==2 && !branch.get(0).isAntc() && !branch.get(1).isAntc())receivers.add(rule);
            else if(branch.size()==1 && branch.get(0).isAntc() && rule.getOrigin()!=null
                    && rule.getOrigin().indexOf('$')>=0) {
                NativePrenexUnitScope scope;
                try {scope=NativePrenexUnitScope.observeFactoryRow(rule.getId(),mind,work);}
                catch(UnsupportedOperationException unsupported){continue;}
                for(NativePrenexUnitScope.Binder binder:scope.binders)if(!binder.universal) {
                    producers.add(rule);break;
                }
            }
        }
        int attempts=0;
        for(Rule receiver:receivers)for(Rule producer:producers) {
            work.run();
            Domain unit=producer.getDomain();
            boolean matching=true;
            for(Domain literal:receiver.getTree().get(0))
                matching &= literal.getRange()==unit.getRange()
                        && literal.getPredicate(mind).getName(mind).equals(unit.getPredicate(mind).getName(mind));
            if(!matching)continue;
            // A receiver outside this exact fragment is not evidence of a conflict.
            boolean ordinary=true;
            for(Domain literal:receiver.getTree().get(0)) {
                ordinary &= !literal.isSystem(mind) && !literal.isCalculated(mind);
                for(org.kanger.interfaces.IArgument argument:literal.getArguments()) {
                    work.run();
                    if(argument.getType()!=org.kanger.enums.ArgumentType.TVARIABLE
                            && (argument.getType()!=org.kanger.enums.ArgumentType.TERM
                            || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))))ordinary=false;
                }
            }
            if(!ordinary)continue;
            if(++attempts>16)throw new IllegalStateException("Dependent pair refutation search limit");
            try(NativeDependentMatchFrame frame=NativeDependentMatchFrame.openPair(mind,producer.getId(),receiver.getId())) {
                NativeDependentMatchFrame.Entry source=frame.pairEntry(producer.getId());
                NativeDependentMatchFrame.Match first=frame.match(source,frame.instantiate(frame.pairEntry(receiver.getId()),0),0);
                if(first==null)continue;
                NativeDependentMatchFrame.Match last=frame.match(source,first.after,0);
                if(last==null || !last.after.remaining.isEmpty())continue;
                frame.verify(last);
                CollisionProofCapture.recordDependent(frame,last);
                return ContextQualification.witness(receiver.getOrigin(),producer.getOrigin());
            }
        }
        return null;
    }
}
