/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import java.util.regex.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.*;

/** Typed fresh-family result boundary. A checked model is a precondition, never query truth or standalone write authority. */
public final class ContextDependentFamilyOutcome {
    private ContextDependentFamilyOutcome() {}
    private static final Pattern UUID_TEXT=Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    public static String readable(String text,Map<UUID,String> names){if(text==null)return "No complete qualification result";Matcher matcher=UUID_TEXT.matcher(text);StringBuffer out=new StringBuffer();while(matcher.find()){String name=names==null?null:names.get(UUID.fromString(matcher.group()));matcher.appendReplacement(out,Matcher.quoteReplacement(name==null?"<unknown context>":name));}matcher.appendTail(out);return out.toString();}
    public static String reason(Throwable failure){if(failure==null)return "No failure detail";String message=failure.getMessage();return message==null||message.isEmpty()?failure.toString():message;}
    public static StorageLifecycleException incomplete(String reason,Map<UUID,String> names){return new StorageLifecycleException(StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE,"Symbolic family qualification incomplete: "+readable(reason,names));}
    public static void requireModelled(ContextDependentModelSearchInspection.Result result,Map<UUID,String> names,Map<UUID,ContextProductionInventory> inventories)throws StorageLifecycleException{
        if(result==null)throw incomplete("No checked family result",names);
        if(names==null||inventories==null)throw incomplete("Exact named physical family manifest required",names);
        if(result.status==ContextDependentModelSearchInspection.Status.REFUTED){
            try{throw ContextDependentFamilyCommitException.collision(result,names,inventories);}catch(IllegalArgumentException invalid){throw incomplete("Refutation is not bound to complete named original evidence",names);}
        }
        if(result.status!=ContextDependentModelSearchInspection.Status.MODELLED_PENDING)throw incomplete(result.phase+": "+result.reason,names);
        if(result.family==null||result.family.status!=ContextDependentIterationInspection.Status.FIXED_POINT||result.frame==null||result.models.isEmpty()||result.finalObligations!=result.runtimeObligations)throw incomplete("Whole receiving/model/runtime audits required",names);
        Set<String> expected=new HashSet<>();int local=0;for(Map.Entry<UUID,ContextProductionInventory> entry:inventories.entrySet()){ContextProductionInventory inventory=entry.getValue();if(inventory==null||!entry.getKey().equals(inventory.context)||names.get(entry.getKey())==null||names.get(entry.getKey()).isEmpty())throw incomplete("Exact named physical family manifest required",names);if(inventory.localX){local++;expected.add(ContextDependentCutInspection.local(inventory.context));}else expected.add(ContextDependentCutInspection.independent(inventory.context));}
        if(local!=1||!result.models.keySet().equals(expected))throw incomplete("Complete independent family model manifest required",names);
    }
}
