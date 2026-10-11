/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.List;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.IContextResults;

/** Detached DMZ histories; presentation never queries or changes a Context. */
final class ConsoleDmzView {
    private ConsoleDmzView() { }
    static void show(DmzSourcesQuery.Result result) {
        System.out.println("DMZ policy: "+result.request.policy);
        System.out.println("Q: "+result.acquisition.authority.locator+" "+pin(result.acquisition.authority.revision));
        System.out.println("Query: "+result.request.queryStatement);
        DmzSourcesQuery.Limits l=result.request.limits;
        System.out.println("Limits: rows="+result.request.sources.maximumRows+", preparation="+l.preparation+", audit="+l.audit
                +", lookup="+l.lookup+", recognition="+l.recognition+", projection="+l.projection+", application="+l.application
                +", query="+l.query+", depth="+l.depth+", nodes="+l.nodes+", work="+l.work+", visits="+l.maximumVisits);
        for(DmzSourcesQuery.SourceInventory source:result.sources)
            System.out.println("Source: "+locator(result,source.revision)+" "+pin(source.revision)+", primaries="+source.primaryRules
                    +", inspected="+source.inspectedRows+", excluded caches="+source.excludedGeneratedCaches+", digest="+source.primaryDigest);
        System.out.println("Fixed productions: "+result.productions.size());
        for(DmzSourcesQuery.Input input:result.productions)System.out.println("  "+identity(result,input)+" "+input.statement);
        System.out.println("Ordered grounds: "+result.candidates.size());
        for(int i=0;i<result.candidates.size();++i) {
            DmzSourcesQuery.Input input=result.candidates.get(i);System.out.println("  ["+i+"] "+identity(result,input)+" "+input.statement);
        }
        if(result.restartRequired) {
            System.out.println("DMZ traversal: PARTIAL; restart required with a fresh full invocation");
            System.out.println("Completed leaves below are partial historical observations");
        } else System.out.println("DMZ traversal: COMPLETE under the displayed ordered policy");
        System.out.println("Visits: "+result.visits+"/"+l.maximumVisits+", visit limit="+result.visitLimit);
        if(result.visitLimit) {
            System.out.println("Interrupted input: "+result.interruptedInput);
            steps(result,result.interruptedPrefix,"  prefix ");
        }
        int ordinal=0;
        for(DmzSourcesQuery.Opinion opinion:result.opinions) {
            System.out.println("Opinion "+(++ordinal)+": "+(opinion.answer.value==null ? "UNKNOWN" : opinion.answer.value ? "TRUE" : "FALSE"));
            steps(result,opinion.steps,"  ");
            for(String hypothesis:opinion.answer.hypotheses)System.out.println("  Hypothesis (diagnostic): "+hypothesis);
            for(String assertion:opinion.answer.hypothesisAssertions)System.out.println("  Hypothesis assertion (diagnostic): "+assertion);
        }
        if(result.opinions.isEmpty())System.out.println("No completed final-query opinions");
        for(DmzSourcesQuery.Event event:result.events) {
            DmzConditionalResult c=event.conditional;
            System.out.println("Conditional event at input "+event.inputIndex+": "+c.disposition+", restart="+c.restartRequired
                    +", discovery retry="+c.retryDiscovery+", work="+c.traversalWork+"/"+c.workLimit);
            steps(result,event.prefix,"  prefix ");
            if(c.authorityConflict!=null)System.out.println("  Q authority conflicts: "+c.authorityConflict.conflicts.size());
            if(c.family!=null) {
                System.out.println("  Conditional traversal: "+c.family.outcome+", Q-declined paths="+c.family.declined.size()
                        +", pending paths="+c.family.frontier.size()+", unstarted root="+(c.family.unstartedRoot!=null));
                for(DmzConditionalResult.Frontier frontier:c.family.frontier)System.out.println("  Pending path: "+frontier.reason);
                if(c.family.unstartedRoot!=null)System.out.println("  Unstarted root: "+c.family.unstartedRoot.reason);
            }
        }
    }
    private static void steps(DmzSourcesQuery.Result result,List<DmzSourcesQuery.Step> steps,String prefix) {
        for(int i=0;i<steps.size();++i) {
            DmzSourcesQuery.Step step=steps.get(i);System.out.println(prefix+"["+i+"] "+identity(result,step.input)+" "+step.decision+" variant="+step.variant);
        }
    }
    private static String identity(DmzSourcesQuery.Result result,DmzSourcesQuery.Input input) {
        return locator(result,input.revision)+"@"+input.revision.getRevision()+":"+input.rule;
    }
    private static String pin(IContextResults.Revision revision) { return revision.getContextId()+"@"+revision.getRevision(); }
    private static String locator(DmzSourcesQuery.Result result,IContextResults.Revision revision) {
        for(DmzSourcePlan.Source source:result.acquisition.external)
            if(source.revision.getContextId().equals(revision.getContextId()))return source.locator;
        throw new IllegalStateException("DMZ result refers to an unacquired Context");
    }
}
