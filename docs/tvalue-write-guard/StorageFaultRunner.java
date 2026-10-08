package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import org.kanger.storage.*;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
/** Throw-probe qualification at real native upsert boundaries; NOT process crash recovery. */
public final class StorageFaultRunner {
 static int checks,hits;static boolean armed,shadow=Boolean.getBoolean("persistence.shadow");static String point;static Base guardedBase;static int activeChecks;
 public static final class InjectedStorageFailure extends RuntimeException{InjectedStorageFailure(String p){super("diagnostic native storage boundary "+p);}}
 public static void hit(String p){if(!armed)return;
  try{String before=ResidentPersistentRunner.fingerprint(guardedBase);boolean denied=false;
   try{ResidentPersistentRead.snapshot(guardedBase);}catch(AssertionError e){denied=e.getMessage().contains("active native upsert");}
   require(denied&&before.equals(ResidentPersistentRunner.fingerprint(guardedBase)),"active native upsert reader refusal is pure");activeChecks++;
  }catch(Exception e){throw new AssertionError(e);}
  if(p.equals(point)&&++hits==2)throw new InjectedStorageFailure(p);
 }
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
 static void write(String suffix,List<String> rows)throws Exception{Files.write(Paths.get(System.getProperty("journal.path")+suffix),rows);}
 @SuppressWarnings("unchecked")static List<String> rows()throws Exception{Field f=TValueDirtyJournal.class.getDeclaredField("CURRENT");f.setAccessible(true);Object s=((ThreadLocal<?>)f.get(null)).get();return new ArrayList<>((List<String>)raw(s,"rows"));}
 static Object session()throws Exception{Field f=TValueDirtyJournal.class.getDeclaredField("CURRENT");f.setAccessible(true);return ((ThreadLocal<?>)f.get(null)).get();}
 static void observe(Mind m,String why)throws Exception{String before=ResidentTValueRead.fingerprint(m);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(before.equals(ResidentTValueRead.fingerprint(m)),"pure factory view");}
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(args[0]);}finally{armed=false;RecordedLinks.disable();}}
 @SuppressWarnings("unchecked")static void run(String selected)throws Exception{
  point=selected;boolean fault=!selected.equals("none");require(Arrays.asList("none","upsert-after-wal","upsert-after-data","upsert-after-index","upsert-after-integrity").contains(point),"point");
  User u=(User)UserFactory.createUser("storage-fault","storage-fault");u.setProperty("cache.data.size","0");new DB().init(u);Mind m=new Mind(u);m=(Mind)m.useStorage("storage-fault");TValueFactory f=m.getTValues();f.transaction(null);Base base=(Base)u.getStorage(TValueFactory.SCHEMA);
  TVariable x=MaterializationRoutingRunner.variable(m,"x");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");TValue lead=f.add(x,m.getTerms().add("lead")),middle=f.add(x,a),tail=f.add(x,m.getTerms().add("tail"));Mind lease=new Mind(m);f.forEach(x,o->true);
  Map<Long,IStep> memory=(Map<Long,IStep>)raw(raw(f,"cache"),"memoryById");
  // Store physical copies without replacing the factory's actual native memory layer.
  for(TValue value:new TValue[]{lead,middle,tail})base.add(new Sapato(base,memory.get(value.getId())));
  base.flush();base.getRoot();for(TValue value:new TValue[]{lead,middle,tail})base.get(value.getId());
  Map<Long,IStep> resident=(Map<Long,IStep>)raw(base,"cache");IStep cachedMiddle=null;for(Map.Entry<Long,IStep> e:resident.entrySet())if(e.getKey()==middle.getId())cachedMiddle=e.getValue();
  require(cachedMiddle!=null&&((TValue)raw(cachedMiddle,"data")).getValueId()==a.getId(),"stored cached middle starts at a");
  Object integrity=raw(base,"integrity");Map<Long,?> entries=(Map<Long,?>)raw(integrity,"entries");Object oldEntry=entries.get(middle.getId());
  middle.setPersistentReferences(b.getId(),x.getId());if(shadow)TValueDirtyJournal.begin();observe(m,"memory-baseline-before-storage-fault");
  guardedBase=base;long beforeWrites=(Long)raw(base,"writeCount");Throwable thrown=null;armed=true;try{f.update();}catch(Throwable e){thrown=e;}finally{armed=false;}
  require((thrown!=null)==fault,"native throw outcome");if(fault)require(thrown.getClass()==InjectedStorageFailure.class&&thrown.getMessage().equals("diagnostic native storage boundary "+point)&&hits==2,"exact throw probe");
  require((Long)raw(base,"writeCount")-beforeWrites==(fault?2:3),"native write attempts");require(raw(base,"rootId")==null&&raw(base,"topId")==null,"native endpoints remain unresolved");
  String before=ResidentPersistentRunner.fingerprint(base);boolean physicalRejected=false;String physicalError="";List<ResidentPersistentRead.Row> physical=null;
  try{physical=ResidentPersistentRead.snapshot(base);}catch(AssertionError e){physicalRejected=true;physicalError=e.getMessage();}
  require(before.equals(ResidentPersistentRunner.fingerprint(base)),"pure physical observation");boolean staleEntry=point.equals("upsert-after-integrity");require(physicalRejected==fault,"physical admission expected by write guard");
  if(fault)require(physicalError.contains("failed native upsert"),"failed write refusal");else require(physical.size()==3,"complete cached physical universe admitted");
  if(fault){require(entries.get(middle.getId())==oldEntry==!staleEntry,"manifest generation boundary");require(resident.containsValue(cachedMiddle)&&((TValue)raw(cachedMiddle,"data")).getValueId()==a.getId(),"failed native write did not invalidate old cached middle");}
  String proof="STORAGE_FAULT_PHYSICAL point="+point+" admitted="+!physicalRejected+" rows="+physical+" error="+physicalError+" native_endpoints=unresolved reader=pure";write(".physical.txt",Collections.singletonList(proof));
  if(shadow){
   if(fault){Object s=session();require(raw(s,"update")==null&&((List<?>)raw(s,"errors")).contains("unsupported incomplete native factory update"),"unconditional factory failure before oracle");write(".failure-prefix.trace",rows());boolean denied=false;try{TValueDirtyJournal.finish();}catch(AssertionError e){denied=e.getMessage().contains("unsupported incomplete native factory update");write(".failure-error.txt",Collections.singletonList(e.getMessage()));}require(denied,"journal refuses failed native update");TValueDirtyJournal.begin();TValueDirtyJournal.finish();}
   else{observe(m,"successful-storage-update");write(".success.trace",TValueDirtyJournal.finish());}
  }else if(!fault)observe(m,"successful-storage-update");
  // Explicit uncached native disk control only AFTER candidate/journal outcomes.
  Index index=(Index)raw(base,"index");Data data=(Data)raw(base,"data");IStep stored=data.getUncached(index.getOne(middle.getId()).getLong());long diskTerm=((TValue)stored.getData()).getValueId();boolean dataChanged=!point.equals("upsert-after-wal");require(diskTerm==(dataChanged?b.getId():a.getId()),"disk term at actual native fault boundary");
  if(fault){
   String again=ResidentPersistentRunner.fingerprint(base);boolean denied=false;
   try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){denied=e.getMessage().contains("failed native upsert")||e.getMessage().contains("saved-link recording error");}
   require(denied&&again.equals(ResidentPersistentRunner.fingerprint(base)),"later disk decode cannot heal failed write admission");
   if(shadow){TValueDirtyJournal.begin();String nativeBefore=ResidentPersistentRunner.fingerprint(base);TValueDirtyJournal.observe(m,"new-session-same-failed-base");require(nativeBefore.equals(ResidentPersistentRunner.fingerprint(base)),"new failed session observation is pure");boolean rejected=false;
    try{TValueDirtyJournal.finish();}catch(AssertionError e){rejected=e.getMessage().contains("failed native upsert")||e.getMessage().contains("saved-link recording error");write(".new-session-error.txt",Collections.singletonList(e.getMessage()));}
    require(rejected,"new journal session cannot clear failed storage guard");}
  }
  System.out.println("WRITE_GUARD_ACTIVE point="+point+" checks="+activeChecks+" later_decode="+(fault?"still-refused":"success"));
  require(m.pendingTransactionCount()==1,"quarantined fixture retains held lease");
  System.out.println("STORAGE_FAULT_NATIVE point="+point+" exception="+(fault?"InjectedStorageFailure":"none")+" write_attempts="+(fault?2:3)+" disk_term="+(dataChanged?"b":"a")+" cached_middle="+(fault?"a":"b")+" manifest="+(fault&&!staleEntry?"old":"new")+" physical="+(physicalRejected?"refused":"admitted")+" lease=held");System.out.println("STORAGE_FAULT_OK checks="+checks+" shadow="+shadow);
 }
}
