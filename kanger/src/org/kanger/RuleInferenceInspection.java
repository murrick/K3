/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.IContextResults;

/** Read-only inference projection, distinct from a rule's internal structure. */
public final class RuleInferenceInspection {
    private RuleInferenceInspection() { }
    public static List<List<String>> tree(Mind mind,IRule rule) throws Exception {
        List<List<String>> out=new ArrayList<List<String>>();
        List<IContextResults.ProofCause> causes=rule.isStored()
                ? ContextProofProjection.solution(mind,rule).causes : Collections.<IContextResults.ProofCause>emptyList();
        if(causes.isEmpty())out.add(Collections.singletonList(rule.isGenerated()
                ? "No saved inference causes" : "Primary cause: "+rule.toString(mind)));
        append(causes,out,"");return out;
    }
    private static void append(List<IContextResults.ProofCause> causes,List<List<String>> out,String indent) {
        for(IContextResults.ProofCause cause:causes) {
            String source=cause.contextSource==null ? "" : " from "+cause.contextSource.getContextId()+"@"+cause.contextSource.getRevision();
            out.add(Collections.singletonList(indent+"Rule: "+cause.ruleStatement+source));
            out.add(Collections.singletonList(indent+"Cause: "+cause.donorStatement));
            if(cause.cycle)out.add(Collections.singletonList(indent+"  [cycle]"));
            else append(cause.causes,out,indent+"  ");
        }
    }
}
