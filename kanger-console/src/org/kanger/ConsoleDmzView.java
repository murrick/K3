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
    static void explain(DmzSourcesQuery.Result result) {
        System.out.println("DMZ policy: "+result.request.policy);
        System.out.println("Q: "+result.acquisition.authority.locator+" "+pin(result.acquisition.authority.revision));
        System.out.println("Query: "+result.request.queryStatement);
        for(DmzSourcePlan.Source component:result.acquisition.authorityComponents)
            System.out.println("Q component: "+component.locator+" "+pin(component.revision)+(component.configured ? " [configured by Q]" : ""));
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
    static void opinions(DmzSourcesQuery.Result result,String locator,boolean trees,Long selected) throws Exception {
        java.util.List<Integer> numbers=DmzOpinionSelection.select(result,locator,selected);
        if(numbers.isEmpty())System.out.println("No saved opinions for "+locator);
        if(trees) {
            int ordinal=0;
            for(Integer number:numbers)tree(result,number,selected==null ? ++ordinal : selected.intValue());
        } else showSelected(result,numbers);
    }
    static void show(DmzSourcesQuery.Result result) {
        java.util.List<Integer> numbers=new java.util.ArrayList<Integer>();
        for(int i=1;i<=result.viewOpinions.size();i++)numbers.add(i);
        showSelected(result,numbers);
    }
    private static void showSelected(DmzSourcesQuery.Result result,java.util.List<Integer> numbers) {
        if(result.restartRequired)System.out.println("PARTIAL opinions; restart required");
        int number=0;
        for(Integer selected:numbers) {
            DmzSourcesQuery.ViewOpinion opinion=result.viewOpinions.get(selected-1);
            java.util.Set<String> sources=new java.util.LinkedHashSet<String>();
            for(DmzSourcesQuery.Proof proof:opinion.proofs)sources(result,proof,sources);
            System.out.println("Opinion "+(++number)+": "+truth(opinion.value)+(sources.isEmpty() ? "" : " from "+String.join(", ",sources)));
            java.util.Set<String> causes=new java.util.LinkedHashSet<String>();
            for(DmzSourcesQuery.Proof proof:opinion.proofs)causes(proof,causes);
            if(!causes.isEmpty())System.out.println("  "+(causes.size()==1 ? "Cause: " : "Causes: ")+String.join("; ",causes));
        }
        if(result.viewOpinions.isEmpty())System.out.println("No completed opinions");
        else System.out.println("Use \"ctx opinions <locator> tree [<N>]\" for inference tree.");
    }
    static void tree(DmzSourcesQuery.Result result,int number) { tree(result,number,number); }
    private static void tree(DmzSourcesQuery.Result result,int number,int displayed) {
        DmzSourcesQuery.ViewOpinion opinion=result.viewOpinions.get(number-1);
        System.out.println("Opinion "+displayed+": "+truth(opinion.value)+"; query "+result.request.queryStatement);
        if(result.restartRequired)System.out.println("PARTIAL historical opinion");
        int alternative=0;
        for(DmzSourcesQuery.Proof proof:opinion.proofs) {
            System.out.println("Inference "+(++alternative)+":");tree(result,proof,"  ");
        }
        if(opinion.proofs.isEmpty()) {
            System.out.println("No resolved inference tree");
            for(String hypothesis:opinion.hypotheses)System.out.println("  Hypothesis: "+hypothesis);
        }
    }
    private static void causes(DmzSourcesQuery.Proof proof,java.util.Set<String> values) {
        if(proof.premises.isEmpty())values.add(proof.conclusion);
        else for(DmzSourcesQuery.Proof child:proof.premises)causes(child,values);
    }
    private static String truth(Boolean value) { return value==null ? "UNKNOWN" : value ? "TRUE" : "FALSE"; }
    private static void sources(DmzSourcesQuery.Result result,DmzSourcesQuery.Proof proof,java.util.Set<String> values) {
        values.add(locator(result,proof.source.revision));
        for(DmzSourcesQuery.Proof child:proof.premises)sources(result,child,values);
    }
    private static void tree(DmzSourcesQuery.Result result,DmzSourcesQuery.Proof proof,String indent) {
        DmzSourcePlan.Source source=source(result,proof.source.revision);
        System.out.println(indent+proof.conclusion+" from "+source.locator+"@"+source.revision.getRevision()
                +" ["+pin(source.revision)+":"+proof.source.rule+"]"+(source.configured ? " [configured by Q]" : ""));
        if(!proof.premises.isEmpty())System.out.println(indent+"  Rule: "+proof.source.statement);
        for(DmzSourcesQuery.Proof child:proof.premises)tree(result,child,indent+"  ");
    }
    private static DmzSourcePlan.Source source(DmzSourcesQuery.Result result,IContextResults.Revision revision) {
        if(result.acquisition.authority.revision.getContextId().equals(revision.getContextId()))return result.acquisition.authority;
        for(DmzSourcePlan.Source source:result.acquisition.authorityComponents)
            if(source.revision.getContextId().equals(revision.getContextId()))return source;
        for(DmzSourcePlan.Source source:result.acquisition.external)
            if(source.revision.getContextId().equals(revision.getContextId()))return source;
        throw new IllegalStateException("DMZ proof refers to an unacquired Context");
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
        return source(result,revision).locator;
    }
}
