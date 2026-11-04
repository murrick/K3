/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentRuntimeExecution.*;
import org.kanger.ContextDependentRuntimeExecution.Step;
import org.kanger.ContextDependentRuntimeExecution.Donor;
import org.kanger.ContextDependentRuntimeExecution.Request;
import org.kanger.ContextDependentRuntimeExecution.Result;
/** QA-only native literal-order helper and independent certificate challenge seam. */
public final class ContextDependentRuntimeExecutionAuditProbe {
    public static Step step(Member member,String cut,long rule,String head,boolean positive,Map<String,Donor> donors)throws Exception{ContextProductionCapture.Clause c=NativeDependentProductionRows.ordinary((org.kanger.units.Rule)member.actual.getRules().get(rule),member.actual,()->{});int retained=-1;for(int i=0;i<c.literals.size();i++)if(c.literals.get(i).predicate.equals(head)&&c.literals.get(i).positive==positive)retained=i;if(retained<0)throw new AssertionError("Native retained literal missing");List<Donor> selected=new ArrayList<>();for(int i=0;i<c.literals.size();i++)if(i!=retained){Donor d=donors.get(c.literals.get(i).predicate);if(d==null)throw new AssertionError("Exact native donor predicate missing");selected.add(d);}return new Step(cut,new Ref(member.inventory.context,rule),retained,selected);}
    public static void audit(Member child,List<Cut> cuts,Result result,Request request)throws Exception{new ContextDependentRuntimeExecutionAudit(child,cuts,result.preparation.frame,request).check(result.outputs);}
    public static void corrupt(Member child,List<Cut> cuts,Result result,Request request,String mode)throws Exception{Output output=result.outputs.get(0);String field;Object value;if(mode.equals("sources")){field="sources";Set<ContextProductionCapture.Source> s=new LinkedHashSet<>(output.sources);s.remove(s.iterator().next());value=Collections.unmodifiableSet(s);}else if(mode.equals("depth")){field="depth";value=output.depth+1;}else if(mode.equals("extra")){field="nodes";List<Node> ns=new ArrayList<>(output.nodes);ns.add(ns.get(0));value=Collections.unmodifiableList(ns);}else if(mode.equals("bindings")){field="ruleBindings";value=Collections.emptyMap();}else if(mode.equals("donor")){field="donors";List<List<Integer>> ds=new ArrayList<>(output.donors);List<Integer> args=new ArrayList<>(ds.get(0));args.set(args.size()-1,output.arguments.get(0));ds.set(0,Collections.unmodifiableList(args));value=Collections.unmodifiableList(ds);}else throw new AssertionError("Unknown certificate corruption");java.lang.reflect.Field f=output.getClass().getDeclaredField(field);f.setAccessible(true);Object before=f.get(output);try{f.set(output,value);audit(child,cuts,result,request);}finally{f.set(output,before);}}
}
