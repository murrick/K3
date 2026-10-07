package org.kanger;
import org.kanger.units.*;
import java.util.*;
/** Native unsafe metadata changes complete; observer qualification rejects. */
public final class TValueMetadataGapRunner {
    public static void main(String[] args)throws Exception{
        String mode=args[0];boolean shadow=TValueMetadataNativeRunner.shadow();if(shadow)TValueDirtyJournal.begin();
        Mind m=new Mind(new User());TVariable v=TValueMetadataNativeRunner.variable(m,"old_owner"),w=TValueMetadataNativeRunner.variable(m,"new_owner");
        Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");TValue t=m.getTValues().add(v,a);
        // Establish both native variable index and journal baseline before write.
        TValueMetadataNativeRunner.ids(m,v);TValueMetadataNativeRunner.observe(m,"gap-baseline");
        if(mode.equals("setTVar"))t.setTVar(w);
        else if(mode.equals("persistent-variable"))t.setPersistentReferences(a.getId(),w.getId());
        else if(mode.equals("applyMap")){Map<String,Object> map=new HashMap<>();map.put("id",t.getId());map.put("mind_id",t.getMindId());map.put("deleted",false);map.put("value_id",b.getId());map.put("tvar_id",v.getId());t.applyMap(map);}
        else throw new AssertionError(mode);
        boolean variable=!mode.equals("applyMap");
        if(t.getTVarId()!=(variable?w.getId():v.getId())||t.getValueId()!=(variable?a.getId():b.getId())||m.getTValues().get(t.getId())!=t)throw new AssertionError("native setter changed");
        if(!TValueMetadataNativeRunner.ids(m,v).equals(Collections.singletonList(t.getId()))||!TValueMetadataNativeRunner.ids(m,w).isEmpty())throw new AssertionError("native variable routing changed");
        if(m.getTValues().find(v,a)!=null||m.getTValues().find(variable?w:v,variable?a:b)!=null)throw new AssertionError("native stale hash lookup changed");
        System.out.println("METADATA_GAP_NATIVE mode="+mode+" value="+t.getId()+":"+t.getTVarId()+":"+t.getValueId()+" old_bucket="+TValueMetadataNativeRunner.ids(m,v)+" new_bucket="+TValueMetadataNativeRunner.ids(m,w)+" hash=both_missing reservations="+m.pendingTransactionCount());
        if(shadow){
            TValueDirtyJournal.observe(m,"unsupported-"+mode);boolean rejected=false;
            try{TValueDirtyJournal.finish();}catch(AssertionError e){rejected=e.getMessage().contains(variable?"unsupported registered identity mutation":"dirty projection mismatch");System.out.println("EXPECTED_OBSERVER_REJECTION mode="+mode+" identity="+variable+" projection="+e.getMessage().contains("dirty projection mismatch"));}
            if(!rejected||m.getTValues().get(t.getId())!=t)throw new AssertionError("observer must reject without native repair");
            TValueDirtyJournal.begin();TValueDirtyJournal.finish();
        }
        System.out.println("TVALUE_METADATA_GAP_OK mode="+mode);
    }
}
