/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.*;
/** QA-only independent confirmation and bad-import interpretation challenge. */
public final class ContextDependentModelSearchAuditProbe {
    public static void audit(Member x,List<Cut> cuts,ContextDependentModelSearchInspection.Result r,ContextDependentModelSearchInspection.Request q)throws Exception{
        ContextDependentModelAudit audit=new ContextDependentModelAudit(x,cuts,r.family.checked,new ContextDependentModelInspection.Request(q.iteration,r.models,q.limits.points,q.limits.cells,q.limits.originalWork,q.limits.finalWork));if(audit.original()!=r.originalRows||audit.finalModel()!=r.finalObligations)throw new AssertionError("Complete native model obligations required");ContextDependentRuntimeContinuationInspection.Projection p=q.projection;ContextDependentRuntimeModelAudit runtime=new ContextDependentRuntimeModelAudit(x,cuts,r.frame,r.models,q.limits.points,q.limits.cells,p.products,p.nodes,p.truthWork,ContextGroundProductionRounds.budget(p.shapeWork));if(runtime.truth()!=r.finalObligations)throw new AssertionError("Complete independent indexed obligations required");
    }
    public static boolean falseImport(Member x,List<Cut> cuts,ContextDependentModelSearchInspection.Result r,ContextDependentModelSearchInspection.Request q,String cut)throws Exception{
        Map<String,Model> models=new LinkedHashMap<>(r.models);Model old=models.get(cut);Map<Signature,Relation> relations=new LinkedHashMap<>(old.relations);relations.put(new Signature("p",1),new Relation(false,Collections.emptyMap()));models.put(cut,new Model(old.points,old.otherRepresentative,relations,old.functions));ContextDependentModelAudit audit=new ContextDependentModelAudit(x,cuts,r.family.checked,new ContextDependentModelInspection.Request(q.iteration,models,q.limits.points,q.limits.cells,q.limits.originalWork,q.limits.finalWork));audit.original();boolean nativeFalse=false,indexedFalse=false;try{audit.finalModel();}catch(ContextDependentModelAudit.Rejected expected){nativeFalse=true;}ContextDependentRuntimeContinuationInspection.Projection p=q.projection;ContextDependentRuntimeModelAudit runtime=new ContextDependentRuntimeModelAudit(x,cuts,r.frame,models,q.limits.points,q.limits.cells,p.products,p.nodes,p.truthWork,ContextGroundProductionRounds.budget(p.shapeWork));try{runtime.truth();}catch(IllegalStateException expected){indexedFalse=true;}return nativeFalse&&indexedFalse;
    }
}
