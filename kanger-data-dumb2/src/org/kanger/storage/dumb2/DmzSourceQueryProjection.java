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
        if(request.policy!=DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID)
            throw new IllegalArgumentException("Unsupported DMZ dispatch policy");
        DmzSourcesQuery.Limits l=request.limits;
        DmzCandidateSession.SourcesResult value;
        try {
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
            opinions.add(new DmzSourcesQuery.Opinion(steps(opinion.steps,candidates),opinion.answer));
        List<DmzSourcesQuery.Event> events=new ArrayList<DmzSourcesQuery.Event>();
        for(DmzCandidateSession.SequenceEvent event:run.events)
            events.add(new DmzSourcesQuery.Event(event.inputIndex,steps(event.prefix,candidates),event.conditional));
        return new DmzSourcesQuery.Result(request,session.plan,sources,candidates,productions,opinions,events,
                steps(run.interruptedPrefix,candidates),run.restartRequired,run.visitLimit,run.visits,run.interruptedInput);
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
