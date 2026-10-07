package org.kanger;
import java.util.*;
import org.kanger.units.*;
import org.kanger.storage.ByteBuffer;
import org.kanger.exception.OutOfBufferException;
/** Identity-changing native writes complete, then observer qualification fails. */
public final class TValuePersistenceGapRunner {
    public static void main(String[] args)throws Exception{
        boolean shadow=TValuePersistenceRunner.shadow();String mode=args[0];if(shadow)TValueDirtyJournal.begin();Mind m=new Mind(new User());
        TVariable v=TValueMetadataNativeRunner.variable(m,"id_owner");Term a=(Term)m.getTerms().add("a");TValue t=m.getTValues().add(v,a);long original=t.getId(),changed=original+91;
        TValueMetadataNativeRunner.ids(m,v);TValuePersistenceRunner.observe(m,"identity-baseline");
        if(mode.equals("setId"))t.setId(changed);
        else if(mode.equals("applyMap")){Map<String,Object> map=TValuePersistenceRunner.map(t,m.getId(),a.getId(),v.getId(),false);map.put("id",changed);t.applyMap(map);}
        else if(mode.equals("truncated-apply")){try{t.apply(new ByteBuffer().putLong(changed));throw new AssertionError("truncated ID packet must throw");}catch(OutOfBufferException expected){}}
        else throw new AssertionError(mode);
        if(t.getId()!=changed||m.getTValues().get(original)!=t||m.getTValues().get(changed)!=null||!TValueMetadataNativeRunner.ids(m,v).equals(Collections.singletonList(changed)))throw new AssertionError("native ID/cache routing changed");
        System.out.println("PERSISTENCE_ID_NATIVE mode="+mode+" cache_key="+original+" object_ID="+changed+" old_lookup=same_object new_lookup=missing ordered_ID="+changed+" reservations="+m.pendingTransactionCount());
        if(shadow){TValueDirtyJournal.observe(m,"identity-mutated-"+mode);boolean rejected=false;try{TValueDirtyJournal.finish();}catch(AssertionError e){rejected=e.getMessage().contains("unsupported registered identity mutation");System.out.println("EXPECTED_ID_REJECTION mode="+mode+" partial="+mode.equals("truncated-apply"));}if(!rejected||t.getId()!=changed||m.getTValues().get(original)!=t)throw new AssertionError("observer rejects without repair");TValueDirtyJournal.begin();TValueDirtyJournal.finish();}
        System.out.println("TVALUE_PERSISTENCE_ID_GAP_OK mode="+mode);
    }
}
