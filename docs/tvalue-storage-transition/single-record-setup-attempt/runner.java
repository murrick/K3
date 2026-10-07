package org.kanger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
/** Native transition witness; no diagnostic registration from the oracle. */
public final class StorageTransitionRunner {
 static int checks;static boolean shadow=Boolean.getBoolean("persistence.shadow");
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void observe(Mind m,String why)throws Exception{String before=ResidentTValueRead.fingerprint(m);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(before.equals(ResidentTValueRead.fingerprint(m)),"pure observation "+why);}
 public static void main(String[] args)throws Exception{
  String mode=args[0];require(Arrays.asList("update","generation","publication").contains(mode),"mode");
  User user=(User)UserFactory.createUser("transition","transition");user.setProperty("cache.data.size","0");new DB().init(user);Mind m=new Mind(user);m=(Mind)m.useStorage("transition");TValueFactory f=m.getTValues();f.transaction(null);Base base=(Base)user.getStorage(TValueFactory.SCHEMA);
  TVariable x=MaterializationRoutingRunner.variable(m,"x");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");TValue old=f.add(x,a);long id=old.getId();Mind lease=new Mind(m);
  f.forEach(x,o->true);if(shadow)TValueDirtyJournal.begin();observe(m,"memory-baseline");
  Object cache=ResidentTValueRead.field(f,"cache");Map<?,?> memory=(Map<?,?>)ResidentTValueRead.field(cache,"memoryById");require(memory.containsKey(id),"native memory masks stored identity before update");
  f.update();base.flush();IStep root=(IStep)ResidentTValueRead.field(cache,"root");require(root.getClass()==Sapato.class,"native update publishes Sapato");TValue fresh=(TValue)ResidentTValueRead.field(root,"data");require(fresh!=old&&fresh.getId()==id&&fresh.getValueId()==a.getId(),"native update redecodes same ID");require(!memory.containsKey(id)&&((Set<?>)ResidentTValueRead.field(cache,"persistentIds")).contains(id),"native update switches lookup visibility");
  // Read only raw resident cache; never call Base.get after native update.
  Map<?,?> resident=(Map<?,?>)ResidentTValueRead.field(base,"cache");IStep diskNode=null;for(Map.Entry<?,?> e:resident.entrySet())if(((Long)e.getKey())==id)diskNode=(IStep)e.getValue();require(diskNode!=null&&ResidentTValueRead.field(diskNode,"data")==fresh,"new canonical unit already resident");
  observe(m,"after-native-update-same-payload");
  Mind child=null;
  if(mode.equals("generation")){
   f.transaction(null);require(ResidentTValueRead.field(f,"cache")!=cache,"native generation replaces cache");
   // Root anchor and native lookup are initialized by explicit native code.
   // A reset state ignores materialized; next seed must register this object.
   f.forEach(x,o->true);observe(m,"after-native-generation");
  }else if(mode.equals("publication")){
   child=new Mind(m);observe(child,"publication-child-baseline");TValue extra=child.getTValues().add(x,b);require(extra!=fresh,"native child addition");
   // Typed factory commit only: not composite Mind settlement qualification.
   f.commit(child.getTValues());observe(m,"after-typed-publication");
  }
  fresh.setPersistentReferences(b.getId(),x.getId());observe(m,"post-transition-setter");require(fresh.getValueId()==b.getId()&&old.getValueId()==a.getId(),"native setter writes new object only");
  boolean rejected=false;String error="";
  if(shadow){try{List<String> rows=TValueDirtyJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")+".trace"),rows,StandardCharsets.UTF_8);}catch(AssertionError e){error=e.getMessage();rejected=error.contains("dirty projection mismatch");Files.write(Paths.get(System.getProperty("journal.path")+".error.txt"),Collections.singletonList(error),StandardCharsets.UTF_8);}require(rejected==mode.equals("update"),"expected qualification outcome "+error);}
  require(fresh.getValueId()==b.getId(),"diagnostic preserves native mutation");fresh.setPersistentReferences(a.getId(),x.getId());
  if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.finish();}
  if(child!=null)m.release(child);m.release(lease);require(m.pendingTransactionCount()==0,"native reservation cleanup");
  System.out.println("STORAGE_TRANSITION_NATIVE mode="+mode+" replacement=true visibility=memory-to-storage payload=preserved reservations=0");
  System.out.println("STORAGE_TRANSITION_OK mode="+mode+" checks="+checks+" shadow="+shadow+" expected_rejection="+rejected);
 }
}
