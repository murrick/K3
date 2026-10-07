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
 static void observe(Mind m,String why)throws Exception{Base active=ResidentTValueRead.activeBase(m);long reads=(Long)ResidentTValueRead.field(active,"readRequestCount");String before=ResidentTValueRead.fingerprint(m);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(before.equals(ResidentTValueRead.fingerprint(m))&&reads==(Long)ResidentTValueRead.field(active,"readRequestCount"),"pure observation "+why);}
 public static void main(String[] args)throws Exception{
  String mode=args[0];require(Arrays.asList("update","generation","publication").contains(mode),"mode");
  User user=(User)UserFactory.createUser("transition","transition");user.setProperty("cache.data.size","0");new DB().init(user);Mind m=new Mind(user);m=(Mind)m.useStorage("transition");TValueFactory f=m.getTValues();f.transaction(null);Base base=(Base)user.getStorage(TValueFactory.SCHEMA);
  TVariable x=MaterializationRoutingRunner.variable(m,"x");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");f.add(x,m.getTerms().add("lead"));TValue old=f.add(x,a);f.add(x,m.getTerms().add("tail"));long id=old.getId();Mind lease=new Mind(m);
  f.forEach(x,o->true);if(shadow)TValueDirtyJournal.begin();observe(m,"memory-baseline");
  Object cache=ResidentTValueRead.field(f,"cache");Map<?,?> memory=(Map<?,?>)ResidentTValueRead.field(cache,"memoryById");require(memory.containsKey(id),"native memory masks stored identity before update");
  f.update();base.flush();base.getRoot();IStep root=(IStep)ResidentTValueRead.field(cache,"root");require(root.getClass()==Sapato.class,"native update publishes Sapato");require(!memory.containsKey(id)&&((Set<?>)ResidentTValueRead.field(cache,"persistentIds")).contains(id),"native update switches lookup visibility");
  // Root endpoint lookup above targets the newest ID, never this middle ID.
  // Find the tested unit by raw resident-cache iteration only.
  Map<?,?> resident=(Map<?,?>)ResidentTValueRead.field(base,"cache");IStep diskNode=null;for(Map.Entry<?,?> e:resident.entrySet())if(((Long)e.getKey())==id)diskNode=(IStep)e.getValue();require(diskNode!=null,"new canonical unit already resident");TValue fresh=(TValue)ResidentTValueRead.field(diskNode,"data");require(fresh!=old&&fresh.getId()==id&&fresh.getValueId()==a.getId(),"native update redecodes same ID");
  observe(m,"after-native-update-same-payload");
  boolean updateRejected=false;
  if(shadow){try{TValueDirtyJournal.finish();}catch(AssertionError e){String error=e.getMessage();updateRejected=error.contains("materialization requires valid native lookup metadata");Files.write(Paths.get(System.getProperty("journal.path")+".update-error.txt"),Collections.singletonList(error),StandardCharsets.UTF_8);}require(updateRejected,"transient native lookup metadata rejection retained");}
  // Close rejected update evidence before any new generation/publication session.
  if(!mode.equals("update")&&shadow){TValueDirtyJournal.begin();observe(m,"fresh-session-baseline");}
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
  if(shadow&&!mode.equals("update")){try{List<String> rows=TValueDirtyJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")+".trace"),rows,StandardCharsets.UTF_8);}catch(AssertionError e){error=e.getMessage();rejected=error.contains("dirty projection mismatch");Files.write(Paths.get(System.getProperty("journal.path")+".error.txt"),Collections.singletonList(error),StandardCharsets.UTF_8);}require(!rejected,"new session qualified "+error);}
  require(fresh.getValueId()==b.getId(),"diagnostic preserves native mutation");fresh.setPersistentReferences(a.getId(),x.getId());
  if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.finish();}
  if(child!=null)m.release(child);m.release(lease);require(m.pendingTransactionCount()==0,"native reservation cleanup");
  System.out.println("STORAGE_TRANSITION_NATIVE mode="+mode+" replacement=true visibility=memory-to-storage payload=preserved reservations=0");
  System.out.println("STORAGE_TRANSITION_OK mode="+mode+" checks="+checks+" shadow="+shadow+" update_rejected="+updateRejected+" subsequent_rejection="+rejected);
 }
}
