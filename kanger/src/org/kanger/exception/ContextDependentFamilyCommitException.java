/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.exception;

import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.enums.StorageLifecycleErrorCode;

/** Sourced family refusal; retained evidence is diagnostic, never admission authority. */
public final class ContextDependentFamilyCommitException extends StorageLifecycleException {
    private final ContextDependentModelSearchInspection.Result result;
    private ContextDependentFamilyCommitException(ContextDependentModelSearchInspection.Result result,List<ContextQualification.CollisionWitness> witnesses){
        super(StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT,"Proposed dependent Context family has a sourced collision",witnesses);this.result=result;
    }
    public ContextDependentModelSearchInspection.Result getFamilyResult(){return result;}
    private static String original(Source source,Map<UUID,String> names,Map<UUID,ContextProductionInventory> inventories){
        ContextProductionInventory inventory=inventories.get(source.context);String name=names.get(source.context);
        if(name==null||inventory==null||inventory.revision!=source.revision||inventory.localX!=source.localX||!source.view.equals("inventory:"+inventory.fingerprint))throw new IllegalArgumentException("Exact named physical refutation Source required");
        for(ContextProductionInventory.Row row:inventory.primary)if(row.nativeRule==source.rule)return name+"@"+source.revision+"#"+source.rule+" "+row.origin;
        throw new IllegalArgumentException("Exact original refutation row required");
    }
    public static ContextDependentFamilyCommitException collision(ContextDependentModelSearchInspection.Result result,Map<UUID,String> names,Map<UUID,ContextProductionInventory> inventories){
        if(result==null||result.status!=ContextDependentModelSearchInspection.Status.REFUTED||result.family==null
                ||result.family.status!=ContextDependentIterationInspection.Status.COLLISION||result.family.checked==null||result.family.checked.barriers.isEmpty())throw new IllegalArgumentException("Fresh sourced dependent family refutation required");
        List<ContextQualification.CollisionWitness> witnesses=new ArrayList<>();
        ContextDependentRoundInspection.Barrier last=result.family.checked.barriers.get(result.family.checked.barriers.size()-1);
        for(ContextDependentProductionInspection.Result cut:last.cuts.values()){
            List<ContextDependentProductionInspection.Refutation> proofs=new ArrayList<>(cut.originalUnitRefutations);proofs.addAll(cut.finalUnitRefutations);
            for(ContextDependentProductionInspection.Refutation proof:proofs){
                if(!proof.sources.contains(proof.constraint))throw new IllegalArgumentException("Whole constraint ancestry required");
                List<Source> sources=new ArrayList<>(proof.sources);sources.sort(Comparator.comparing((Source s)->s.context.toString()).thenComparingLong(s->s.rule));
                StringBuilder support=new StringBuilder();for(Source source:sources)if(!source.equals(proof.constraint)){if(support.length()>0)support.append("; ");support.append(original(source,names,inventories));}
                witnesses.add(ContextQualification.witness(original(proof.constraint,names,inventories)+" depth="+proof.depth,support.toString()));
            }
        }
        if(witnesses.isEmpty())throw new IllegalArgumentException("Nonempty actual sourced refutation required");
        return new ContextDependentFamilyCommitException(result,witnesses);
    }
}
