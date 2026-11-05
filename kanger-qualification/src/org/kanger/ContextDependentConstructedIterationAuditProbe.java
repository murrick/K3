/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
/** QA-only independent native/indexed rechecks of every constructed receiving model. */
public final class ContextDependentConstructedIterationAuditProbe {
    public static void audit(Member child,List<Cut> cuts,ContextDependentRuntimeIterationInspection.Result r,ContextDependentModelSearchInspection.Request search)throws Exception{
        for(ContextDependentRuntimeIterationInspection.Cycle c:r.cycles){
            ContextDependentModelSearchInspection.Limits l=search.limits;ContextDependentRuntimeContinuationInspection.Projection p=search.projection;
            ContextDependentModelAudit nativeAudit=new ContextDependentModelAudit(child,cuts,c.checked,new ContextDependentModelInspection.Request(search.iteration,c.constructedModels,l.points,l.cells,l.originalWork,l.finalWork));
            if(nativeAudit.original()!=c.originalRows||nativeAudit.finalModel()!=c.finalObligations)throw new AssertionError("Every original and raw receiving obligation required");
            ContextDependentRuntimeModelAudit indexed=new ContextDependentRuntimeModelAudit(child,cuts,c.received,c.constructedModels,l.points,l.cells,p.products,p.nodes,p.truthWork,ContextGroundProductionRounds.budget(p.shapeWork));
            if(indexed.truth()!=c.finalObligations)throw new AssertionError("Independent indexed truth covers every occurrence");
        }
    }
}
