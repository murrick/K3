package org.kanger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
/** Native root-update controls; descendant lookup identity remains contextual. */
public final class UpdateBoundaryRunner {
 static int checks,nativeCallbacks;static boolean shadow=Boolean.getBoolean("persistence.shadow");
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static void observe(Mind m,String why)throws Exception{Base base=ResidentTValueRead.activeBase(m);long reads=(Long)ResidentTValueRead.field(base,"readRequestCount");String before=ResidentTValueRead.fingerprint(m);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(reads==(Long)ResidentTValueRead.field(base,"readRequestCount")&&before.equals(ResidentTValueRead.fingerprint(m)),"pure "+why);}
 static void write(String suffix,List<String> rows)throws Exception{Files.write(Paths.get(System.getProperty("journal.path")+suffix),rows,StandardCharsets.UTF_8);}
 @SuppressWarnings("unchecked")static List<String> pendingRows()throws Exception{Field f=TValueDirtyJournal.class.getDeclaredField("CURRENT");f.setAccessible(true);Object session=((ThreadLocal<?>)f.get(null)).get();return new ArrayList<>((List<String>)ResidentTValueRead.field(session,"rows"));}
 static final class FailingValue extends TValue {
  FailingValue(Mind m,long id,long variable,long term){super(m);setId(id);setPersistentReferences(term,variable);}
  public ByteBuffer pack(){nativeCallbacks++;throw new IllegalStateException("native fixture serialization failure");}
 }
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(args);}finally{RecordedLinks.disable();}}
 public static void run(String[] args)throws Exception{
  boolean failure=args[0].equals("failure");require(failure||args[0].equals("success"),"mode");
  User user=(User)UserFactory.createUser("update-boundary","update-boundary");user.setProperty("cache.data.size","0");new DB().init(user);Mind m=new Mind(user);m=(Mind)m.useStorage("update");TValueFactory f=m.getTValues();f.transaction(null);Base base=(Base)user.getStorage(TValueFactory.SCHEMA);
  TVariable x=MaterializationRoutingRunner.variable(m,"x");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b"),c=(Term)m.getTerms().add("c");TValue lead=f.add(x,m.getTerms().add("lead")),old=f.add(x,a),tail=f.add(x,m.getTerms().add("tail"));Mind lease=new Mind(m);Mind child=failure?null:new Mind(m);
  f.forEach(x,o->true);if(child!=null)child.getTValues().forEach(x,o->true);
  if(shadow)TValueDirtyJournal.begin();observe(m,"parent-memory-baseline");if(child!=null)observe(child,"child-memory-baseline");
  Object cache=ResidentTValueRead.field(f,"cache");Map<?,?> memory=(Map<?,?>)ResidentTValueRead.field(cache,"memoryById");
  if(failure){
   // Only native serialization may invoke this callback. Diagnostic code must
   // neither inspect its fields as a canonical TValue nor invoke pack().
   IStep middle=(IStep)memory.get(old.getId());middle.setData(new FailingValue(m,old.getId(),x.getId(),a.getId()));long writes=(Long)ResidentTValueRead.field(base,"writeCount");
   Throwable original=null;try{f.update();}catch(Throwable e){original=e;}
   require(original!=null&&original.getClass()==IllegalStateException.class&&original.getMessage().equals("native fixture serialization failure"),"original native exception preserved");require(nativeCallbacks==1,"exactly one native pack callback");
   Map<?,?> resident=(Map<?,?>)ResidentTValueRead.field(base,"cache");IStep first=null;for(Map.Entry<?,?> e:resident.entrySet())if(((Long)e.getKey())==lead.getId())first=(IStep)e.getValue();require(first!=null&&((TValue)ResidentTValueRead.field(first,"data")).getId()==lead.getId(),"first native record already written before failure");require((Long)ResidentTValueRead.field(base,"writeCount")-writes==2,"native second write attempted");require(((IStep)ResidentTValueRead.field(cache,"root")).getId()==lead.getId(),"native partial root retained");
   String physicalBefore=ResidentPersistentRunner.fingerprint(base);boolean physicalDenied=false;
   try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){physicalDenied=e.getMessage().contains("failed native upsert");}
   require(physicalDenied,"partial physical admission denied by failed native upsert");
   require(physicalBefore.equals(ResidentPersistentRunner.fingerprint(base)),"partial physical refusal pure");
   require(ResidentPersistentRead.raw(base,"rootId")==null&&ResidentPersistentRead.raw(base,"topId")==null,"partial endpoints not repaired");
   write(".physical.txt",Collections.singletonList("WRITE_GUARD_SERIALIZATION physical=refused reason=failed-native-upsert native_endpoints=unresolved reader=pure"));
   if(shadow){Field current=TValueDirtyJournal.class.getDeclaredField("CURRENT");current.setAccessible(true);Object session=((ThreadLocal<?>)current.get(null)).get();
    require(ResidentPersistentRead.raw(session,"update")==null,"failed update frame cleared");
    require(((List<?>)ResidentPersistentRead.raw(session,"errors")).contains("unsupported incomplete native factory update"),"failure recorded before finish and independent of full oracle");}
   if(shadow){write(".failure-prefix.trace",pendingRows());boolean rejected=false;try{TValueDirtyJournal.finish();}catch(AssertionError e){rejected=e.getMessage().contains("unsupported incomplete native factory update");write(".failure-error.txt",Collections.singletonList(e.getMessage()));}require(rejected,"incomplete update rejected without registering pending objects");TValueDirtyJournal.begin();TValueDirtyJournal.finish();}
   // Explicit fixture cleanup only after partial native state was verified.
   middle.setData(old);base.getRoot();m.release(lease);require(m.pendingTransactionCount()==0,"failed native cleanup consumes reservation");
   System.out.println("UPDATE_BOUNDARY_NATIVE mode=failure exception=IllegalStateException first_record=written second_write=attempted partial_root=retained native_callbacks="+nativeCallbacks+" reservations=0");
  }else{
   f.update();require(ResidentPersistentRead.snapshot(base).size()==3,"successful native update has complete recorded physical chain");require(ResidentPersistentRead.raw(base,"rootId")==null&&ResidentPersistentRead.raw(base,"topId")==null,"successful endpoints not repaired");base.flush();base.getRoot();Map<?,?> resident=(Map<?,?>)ResidentTValueRead.field(base,"cache");TValue fresh=null;for(Map.Entry<?,?> e:resident.entrySet())if(((Long)e.getKey())==old.getId())fresh=(TValue)ResidentTValueRead.field(e.getValue(),"data");require(fresh!=null&&fresh!=old&&fresh.getValueId()==a.getId(),"real native replacement already resident");require(!memory.containsKey(old.getId()),"parent no longer masks stored unit");
   require(ResidentTValueRead.bucket(m,x.getId()).get(1)==fresh&&ResidentTValueRead.bucket(child,x.getId()).get(1)==old,"parent and child actual lookup select different objects");observe(m,"after-update-parent");observe(child,"after-update-child-still-memory");
   int childChanges=shadow?TValueDirtyJournal.contextChangeCount(child):0;fresh.setPersistentReferences(b.getId(),x.getId());observe(m,"fresh-parent-setter");observe(child,"child-unaffected-by-fresh-setter");if(shadow)require(childChanges==TValueDirtyJournal.contextChangeCount(child),"child old memory identity preserved");
   int parentChanges=shadow?TValueDirtyJournal.contextChangeCount(m):0;old.setPersistentReferences(c.getId(),x.getId());observe(m,"parent-unaffected-by-old-setter");observe(child,"old-child-setter");if(shadow)require(parentChanges==TValueDirtyJournal.contextChangeCount(m),"old identity retired only from parent");require(fresh.getValueId()==b.getId()&&old.getValueId()==c.getId(),"both original native scalar writes retained");
   old.setPersistentReferences(a.getId(),x.getId());fresh.setPersistentReferences(a.getId(),x.getId());observe(m,"parent-restored");observe(child,"child-restored");child.getTValues().transaction(f);child.getTValues().forEach(x,o->true);observe(child,"child-new-generation");require(ResidentTValueRead.bucket(child,x.getId()).get(1)==fresh,"native generation now selects fresh object");
   fresh.setPersistentReferences(b.getId(),x.getId());observe(m,"shared-fresh-parent");observe(child,"shared-fresh-child");int changes=shadow?TValueDirtyJournal.contextChangeCount(m)+TValueDirtyJournal.contextChangeCount(child):0;old.setPersistentReferences(c.getId(),x.getId());observe(m,"detached-old-parent");observe(child,"detached-old-child");if(shadow)require(changes==TValueDirtyJournal.contextChangeCount(m)+TValueDirtyJournal.contextChangeCount(child),"detached old setter changes no canonical projection");
   if(shadow){write(".success.trace",TValueDirtyJournal.finish());TValueDirtyJournal.begin();TValueDirtyJournal.finish();}fresh.setPersistentReferences(a.getId(),x.getId());old.setPersistentReferences(a.getId(),x.getId());m.release(child);m.release(lease);require(m.pendingTransactionCount()==0,"successful native cleanup consumes reservations");
   System.out.println("UPDATE_BOUNDARY_NATIVE mode=success replacement=true parent=stored child=memory generation=stored old_alias=contextual payload=preserved reservations=0");
  }
  System.out.println("UPDATE_BOUNDARY_OK mode="+args[0]+" checks="+checks+" shadow="+shadow);
 }
}
