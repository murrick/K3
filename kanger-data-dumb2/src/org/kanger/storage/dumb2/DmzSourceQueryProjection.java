/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.List;
import org.kanger.DmzCandidateSession;
import org.kanger.DmzSourceDispatchPlan;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.IContextResults;

/** Converts internal traversal to the detached public operation contract before owner closure. */
final class DmzSourceQueryProjection {
    private DmzSourceQueryProjection() { }
    static DmzSourcesQuery.Result execute(DmzSourceSession session,DmzSourcesQuery.Request request) throws Exception {
        return execute(session,request,new DmzSourcesQuery.RestrictionOutcome(
                DmzSourcesQuery.RestrictionStatus.NOT_REQUESTED,0));
    }
    static DmzSourcesQuery.Result execute(DmzSourceSession session,DmzSourcesQuery.Request request,
            DmzSourcesQuery.RestrictionOutcome restrictionOutcome) throws Exception {
        if(request.policy!=DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID)
            throw new IllegalArgumentException("Unsupported DMZ dispatch policy");
        if(restrictionOutcome==null)throw new NullPointerException("restrictionOutcome");
        DmzSourcesQuery.Limits l=request.limits;
        DmzCandidateSession.SourcesResult value;
        try {
            if(request.restrictions!=null)return restrictions(session,request);
            value=session.evaluateSources(request.queryStatement,new DmzCandidateSession.Limits(l.preparation,l.audit,
                    l.lookup,l.recognition,l.projection,l.application,l.query,l.depth,l.nodes,l.work),l.maximumVisits);
        } catch(DmzSourceDispatchPlan.PlanningFailure refusal) {
            throw new DmzSourcesQuery.PlanningRefusal(DmzSourcesQuery.Refusal.valueOf(refusal.reason.name()),
                    refusal.offendingInput==null ? null : refusal.offendingInput.context,
                    refusal.offendingInput==null ? null : Long.valueOf(refusal.offendingInput.rule),refusal.getMessage());
        }
        DmzCandidateSession.SequenceResult run=value.sequence;
        List<DmzSourcesQuery.SourceInventory> sources=new ArrayList<DmzSourcesQuery.SourceInventory>();
        for(DmzSourceDispatchPlan.SourceInventory source:value.plan.sources)
            sources.add(new DmzSourcesQuery.SourceInventory(source.revision,source.primaryRules,source.inspectedRows,
                    source.excludedGeneratedCaches,source.primaryDigest));
        List<DmzSourcesQuery.Input> candidates=inputs(run.candidates,run.candidateRevisions,run.candidateStatements);
        List<DmzSourcesQuery.Input> productions=inputs(run.productions,run.productionRevisions,run.productionStatements);
        List<DmzSourcesQuery.Opinion> opinions=new ArrayList<DmzSourcesQuery.Opinion>();
        for(DmzCandidateSession.SequenceOpinion opinion:run.opinions)
            opinions.add(new DmzSourcesQuery.Opinion(steps(opinion.steps,candidates),opinion.answer,run.restrictionProofs));
        List<DmzSourcesQuery.Event> events=new ArrayList<DmzSourcesQuery.Event>();
        for(DmzCandidateSession.SequenceEvent event:run.events)
            events.add(new DmzSourcesQuery.Event(event.inputIndex,steps(event.prefix,candidates),event.conditional));
        return new DmzSourcesQuery.Result(request,session.plan,sources,candidates,productions,opinions,events,
                steps(run.interruptedPrefix,candidates),run.restartRequired,run.visitLimit,run.visits,run.interruptedInput,
                run.restrictionProofs,restrictionOutcome);
    }
    private static DmzSourcesQuery.Result restrictions(DmzSourceSession session,DmzSourcesQuery.Request request) throws Exception {
        DmzSourceDispatchPlan plan=session.planSources();
        org.kanger.DmzSourceRestrictionWorkflow.Result run=session.restrict(request);
        List<DmzSourcesQuery.SourceInventory> sources=new ArrayList<DmzSourcesQuery.SourceInventory>();
        for(DmzSourceDispatchPlan.SourceInventory source:plan.sources)
            sources.add(new DmzSourcesQuery.SourceInventory(source.revision,source.primaryRules,source.inspectedRows,
                    source.excludedGeneratedCaches,source.primaryDigest));
        List<DmzSourcesQuery.Input> candidates=plannedInputs(plan.candidates,plan.candidateStatements,plan.sources);
        List<DmzSourcesQuery.Input> productions=plannedInputs(plan.productions,plan.productionStatements,plan.sources);
        List<DmzSourcesQuery.Opinion> opinions=new ArrayList<DmzSourcesQuery.Opinion>();
        if(run.answer!=null)opinions.add(new DmzSourcesQuery.Opinion(java.util.Collections.<DmzSourcesQuery.Step>emptyList(),run.answer,run.proofs));
        return new DmzSourcesQuery.Result(request,session.plan,sources,candidates,productions,opinions,
                java.util.Collections.<DmzSourcesQuery.Event>emptyList(),java.util.Collections.<DmzSourcesQuery.Step>emptyList(),
                run.outcome.status!=DmzSourcesQuery.RestrictionStatus.COMPLETE,false,run.outcome.choices,-1,run.proofs,run.outcome);
    }
    private static List<DmzSourcesQuery.Input> plannedInputs(List<DmzCandidateSession.SourceRule> rules,List<String> statements,
            List<DmzSourceDispatchPlan.SourceInventory> sources) {
        List<IContextResults.Revision> pins=new ArrayList<IContextResults.Revision>();
        for(DmzCandidateSession.SourceRule rule:rules) {
            IContextResults.Revision pin=null;
            for(DmzSourceDispatchPlan.SourceInventory source:sources)if(rule.context.equals(source.revision.getContextId()))pin=source.revision;
            if(pin==null)throw new IllegalStateException("Planned source pin unavailable");
            pins.add(pin);
        }
        return inputs(rules,pins,statements);
    }
    private static List<DmzSourcesQuery.Input> inputs(List<DmzCandidateSession.SourceRule> rules,
            List<IContextResults.Revision> pins,List<String> statements) {
        List<DmzSourcesQuery.Input> result=new ArrayList<DmzSourcesQuery.Input>();
        for(int i=0;i<rules.size();++i) {
            if(!rules.get(i).context.equals(pins.get(i).getContextId()))throw new IllegalStateException("DMZ input pin mismatch");
            result.add(new DmzSourcesQuery.Input(pins.get(i),rules.get(i).rule,statements.get(i)));
        }
        return result;
    }
    private static List<DmzSourcesQuery.Step> steps(List<DmzCandidateSession.SequenceStep> steps,List<DmzSourcesQuery.Input> candidates) {
        List<DmzSourcesQuery.Step> result=new ArrayList<DmzSourcesQuery.Step>();
        for(int i=0;i<steps.size();++i) {
            DmzCandidateSession.SequenceStep step=steps.get(i);DmzSourcesQuery.Input input=candidates.get(i);
            if(!step.input.context.equals(input.revision.getContextId()) || step.input.rule!=input.rule)
                throw new IllegalStateException("DMZ history differs from ordered input plan");
            result.add(new DmzSourcesQuery.Step(input,DmzSourcesQuery.Decision.valueOf(step.decision.name()),step.variant));
        }
        return result;
    }
}
