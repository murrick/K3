package org.kanger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
/** Post-native qualification failures preserve native return and disk payload. */
public final class MaterializationScopeRunner {
    public static void main(String[] args)throws Exception {
        String mode=args[0];if(!mode.equals("variable")&&!mode.equals("index"))throw new AssertionError(mode);
        User user=(User)UserFactory.createUser("materialization-scope","materialization-scope");user.setProperty("cache.data.size","0");new DB().init(user);Mind m=new Mind(user);m=(Mind)m.useStorage("scope");
        TValueFactory f=m.getTValues();f.transaction(null);Base base=(Base)user.getStorage(TValueFactory.SCHEMA);
        TVariable x=MaterializationRoutingRunner.variable(m,"x"),y=MaterializationRoutingRunner.variable(m,"y");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b"),c=(Term)m.getTerms().add("c");
        TValue first=f.add(x,a);f.add(x,b);f.add(y,c);long id=first.getId();f.update();base.flush();base.getRoot();f.forEach(x,o->true);Mind lease=new Mind(m);
        IStep old=base.get(id);long targetVariable=mode.equals("variable")?y.getId():x.getId();
        TValueDirtyJournal.begin();TValueDirtyJournal.observe(m,"scope-baseline");
        if(mode.equals("index")){f.mark();f.add(y,b);f.release();if((Boolean)ResidentTValueRead.field(ResidentTValueRead.field(f,"cache"),"indexValid"))throw new AssertionError("native release should invalidate lookup metadata");}
        // A native direct storage update is the trigger, not a registered
        // TValue setter. It replaces only the oldest physical record.
        TValue replacement=new TValue(m);replacement.setId(id);replacement.setPersistentReferences(a.getId(),targetVariable);
        Step step=new Step();step.setId(id);step.setHash(replacement.getHash());step.setData(replacement);step.setNext(old.getNext());new Sapato(base,step).update();base.getRoot();
        TValue returned=(TValue)base.get(id).getData();if(returned.getId()!=id||returned.getTVarId()!=targetVariable||returned.getValueId()!=a.getId())throw new AssertionError("native get result altered");
        boolean rejected=false;String error="";try{TValueDirtyJournal.finish();}catch(AssertionError e){error=e.getMessage();rejected=error.contains(mode.equals("variable")?"unsupported materialized variable change":"materialization requires valid native lookup metadata");}
        if(!rejected)throw new AssertionError("scope qualification must reject "+mode+" "+error);
        Files.write(Paths.get(System.getProperty("journal.path")+".scope-error.txt"),Collections.singletonList(error),StandardCharsets.UTF_8);
        // Current session is closed. Re-decode from disk to establish that the
        // diagnostic did not undo or compensate the original native write.
        base.clearCache();TValue persisted=(TValue)base.get(id).getData();if(persisted.getTVarId()!=targetVariable||persisted.getValueId()!=a.getId())throw new AssertionError("native disk payload repaired");
        TValueDirtyJournal.begin();TValueDirtyJournal.finish();m.release(lease);if(m.pendingTransactionCount()!=0)throw new AssertionError("reservation cleanup");
        System.out.println("MATERIALIZATION_SCOPE_OK mode="+mode+" rejected=true native_return=preserved native_disk=preserved session=closed reservations=0");
    }
}
