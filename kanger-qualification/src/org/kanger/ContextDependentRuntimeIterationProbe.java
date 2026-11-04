/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
/** QA-only actual-native minimal receiving prefix and replay challenges. Never packaged in production. */
public final class ContextDependentRuntimeIterationProbe {
    private static ContextDependentRoundInspection.Result initial(Member child,List<Cut> cuts,ContextDependentRuntimeIterationInspection.Request r)throws Exception{
        Map<String,List<ContextDependentRoundInspection.Step>> empty=new LinkedHashMap<>();empty.put(ContextDependentCutInspection.local(child.inventory.context),Collections.emptyList());for(Cut cut:cuts)empty.put(cut.key,Collections.emptyList());ContextDependentIterationInspection.Request p=r.first.execution.preparation.projection.qualification.append.iteration;return ContextDependentRoundInspection.inspect(child,cuts,new ContextDependentRoundInspection.Request(p.policy,p.capacity,Collections.singletonList(new ContextDependentRoundInspection.Round(empty))));
    }
    public static ContextDependentRuntimeIterationInspection.Result minimal(Member base,Member child,List<Cut> cuts,ContextDependentRuntimeIterationInspection.Request r)throws Exception{
        ContextDependentCandidateInspection.Result candidate=ContextDependentCandidateInspection.inspect(base,child,cuts,r.first.execution.preparation.projection.qualification.append);if(candidate.originals.isEmpty())throw new AssertionError("Actual complete candidate manifest required");ContextDependentRoundInspection.Result initial=initial(child,cuts,r);Map<String,List<ContextDependentRoundInspection.Step>> empty=new LinkedHashMap<>();for(String cut:initial.barriers.get(0).cuts.keySet())empty.put(cut,Collections.emptyList());return ContextDependentRuntimeIterationInspection.run(child,cuts,r,null,candidate,initial,Collections.singletonList(new ContextDependentRoundInspection.Round(empty)));
    }
    public static void audit(Member child,List<Cut> cuts,ContextDependentRuntimeIterationInspection.Result result,ContextDependentRuntimeIterationInspection.Request r)throws Exception{
        ContextDependentRoundInspection.Result previous=result.first==null?initial(child,cuts,r):result.first.checked;List<ContextDependentRoundInspection.Round> history=result.first==null?Collections.singletonList(new ContextDependentRoundInspection.Round(empty(previous))):result.first.history;
        ContextDependentIterationInspection.Request p=r.first.execution.preparation.projection.qualification.append.iteration;
        for(int i=0;i<result.cycles.size();i++){ContextDependentRuntimeIterationInspection.Cycle cycle=result.cycles.get(i);ContextDependentRuntimeBarrierInspection.Replay replay=ContextDependentRuntimeBarrierInspection.replay(child,cuts,new ContextDependentRoundInspection.Request(p.policy,p.capacity,history),previous,cycle.input,cycle.discovery.steps,cycle.discovery.outputs,r.rounds.get(i).bridgeWork);if(replay.checked.barriers.size()!=cycle.checked.barriers.size())throw new AssertionError("Whole cycle history required");previous=replay.checked;history=cycle.history;}
    }
    private static Map<String,List<ContextDependentRoundInspection.Step>> empty(ContextDependentRoundInspection.Result checked){Map<String,List<ContextDependentRoundInspection.Step>> out=new LinkedHashMap<>();for(String cut:checked.barriers.get(0).cuts.keySet())out.put(cut,Collections.emptyList());return out;}
    public static void corrupt(Member child,List<Cut> cuts,ContextDependentRuntimeIterationInspection.Result result,ContextDependentRuntimeIterationInspection.Request r)throws Exception{
        ContextDependentRuntimeExecution.Output output=result.cycles.get(0).discovery.outputs.get(0);java.lang.reflect.Field f=output.getClass().getDeclaredField("depth");f.setAccessible(true);Object old=f.get(output);try{f.set(output,output.depth+1);audit(child,cuts,result,r);}finally{f.set(output,old);}
    }
}
