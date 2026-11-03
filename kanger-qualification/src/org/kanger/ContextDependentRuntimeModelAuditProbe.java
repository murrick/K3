/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.Model;
/** QA-only seam: challenge indexed runtime truth with independently altered interpretations. */
public final class ContextDependentRuntimeModelAuditProbe {
    private ContextDependentRuntimeModelAuditProbe(){}
    public static int truth(Member child,List<Cut> cuts,ContextDependentRuntimeInspection.Frame frame,Map<String,Model> models,ContextDependentRuntimeModelInspection.Request request)throws Exception{return new ContextDependentRuntimeModelAudit(child,cuts,frame,models,request,ContextGroundProductionRounds.budget(request.shapeWork)).truth();}
    public static int corrupted(Member child,List<Cut> cuts,ContextDependentRuntimeInspection.Frame frame,Map<String,Model> models,ContextDependentRuntimeModelInspection.Request request,String mode)throws Exception{
        ContextDependentRuntimeInspection.Product product=frame.cuts.values().iterator().next().get(0);Object target=product,replacement;String name;
        if(mode.equals("edge")){int index=-1;for(int i=0;i<product.nodes.size();i++)if(!product.nodes.get(i).arguments.isEmpty()){index=i;break;}if(index<0)throw new AssertionError("Application fixture required");target=product.nodes.get(index);name="arguments";List<Integer> args=new ArrayList<>(((ContextDependentRuntimeInspection.Node)target).arguments);args.set(0,index);replacement=Collections.unmodifiableList(args);}
        else if(mode.equals("extra")){name="nodes";List<ContextDependentRuntimeInspection.Node> nodes=new ArrayList<>(product.nodes);nodes.add(nodes.get(0));replacement=Collections.unmodifiableList(nodes);}
        else if(mode.equals("support")){name="sources";Set<ContextProductionCapture.Source> sources=new LinkedHashSet<>(product.sources);boolean removed=false;for(ContextDependentRuntimeInspection.Node node:product.nodes)if(node.function!=null&&!node.function.equals(product.producer)){sources.remove(node.function);removed=true;break;}if(!removed)throw new AssertionError("Distinct witness/producer fixture required");replacement=Collections.unmodifiableSet(sources);}
        else throw new AssertionError("Unknown corruption mode");
        java.lang.reflect.Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);Object previous=field.get(target);try{field.set(target,replacement);return truth(child,cuts,frame,models,request);}finally{field.set(target,previous);}
    }
}
