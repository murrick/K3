/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentRuntimeExecution.*;
import org.kanger.ContextDependentRuntimeExecution.Request;
import org.kanger.ContextDependentRuntimeExecution.Result;
/** QA-only native-unit certificate challenges, absent from production artifacts. */
public final class ContextDependentRuntimeNativeUnitAuditProbe {
    public static void audit(Member child,List<Cut> cuts,Result result,Request request)throws Exception{new ContextDependentRuntimeExecutionAudit(child,cuts,result.preparation.frame,request).check(result.outputs);}
    public static void corrupt(Member child,List<Cut> cuts,Result result,Request request,String mode)throws Exception{
        Output output=result.outputs.get(0);Object target=output,value;String field;
        if(mode.equals("function-arguments")||mode.equals("function-binder")){Node fn=null;for(Node node:output.nodes)if(node.arguments.size()==2&&node.function!=null){fn=node;break;}if(fn==null)throw new AssertionError("Rank2 native witness fixture required");target=fn;if(mode.equals("function-binder")){field="binder";value=0;}else{field="arguments";List<Integer> args=new ArrayList<>(fn.arguments);Collections.swap(args,0,1);value=Collections.unmodifiableList(args);}}
        else if(mode.equals("native-bindings")){field="donorBindings";List<Map<Integer,Integer>> list=new ArrayList<>(output.donorBindings);Map<Integer,Integer> map=new LinkedHashMap<>(list.get(0));map.remove(map.keySet().iterator().next());list.set(0,Collections.unmodifiableMap(map));value=Collections.unmodifiableList(list);}
        else if(mode.equals("native-root")){field="donors";List<List<Integer>> rows=new ArrayList<>(output.donors);List<Integer> args=new ArrayList<>(rows.get(0));Collections.swap(args,0,2);rows.set(0,Collections.unmodifiableList(args));value=Collections.unmodifiableList(rows);}
        else throw new AssertionError("Unknown native corruption");java.lang.reflect.Field f=target.getClass().getDeclaredField(field);f.setAccessible(true);Object previous=f.get(target);try{f.set(target,value);audit(child,cuts,result,request);}finally{f.set(target,previous);}
    }
}
