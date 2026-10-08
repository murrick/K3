package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
/** Native single-head delete and flush boundaries; no process-halt recovery claim. */
public final class DeleteFlushRunner {
 static int checks,activeChecks;static boolean armed,shadow=Boolean.getBoolean("persistence.shadow");static String operation,point;static Base base;
 public static final class InjectedMutationFailure extends RuntimeException{InjectedMutationFailure(String p){super("diagnostic delete-flush boundary "+p);}}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
 static void write(String suffix,String text)throws Exception{Files.write(Paths.get(System.getProperty("journal.path")+suffix),Collections.singletonList(text));}
 public static void hit(String p){if(!armed||!p.startsWith(operation+"-"))return;
  try{String before=ResidentPersistentRunner.fingerprint(base);boolean refused=false;try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){refused=e.getMessage().contains("active native "+operation);}require(refused&&before.equals(ResidentPersistentRunner.fingerprint(base)),"active mutation refusal pure");activeChecks++;}
  catch(Exception e){throw new AssertionError(e);}if(p.equals(point))throw new InjectedMutationFailure(p);
 }
 static List<ResidentPersistentRead.Row> snapshot()throws Exception{String before=ResidentPersistentRunner.fingerprint(base);List<ResidentPersistentRead.Row> rows=ResidentPersistentRead.snapshot(base);require(before.equals(ResidentPersistentRunner.fingerprint(base)),"settled observation pure");return rows;}
 static void refused(String reason)throws Exception{String before=ResidentPersistentRunner.fingerprint(base);boolean failed=false;try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){failed=e.getMessage().contains(reason);}require(failed&&before.equals(ResidentPersistentRunner.fingerprint(base)),"failed mutation refusal pure");}
 static boolean pending()throws Exception{Method m=Base.class.getDeclaredMethod("hasPendingRecovery");m.setAccessible(true);return (Boolean)m.invoke(base);}
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(args[0],args[1]);}finally{armed=false;RecordedLinks.disable();}}
 static void run(String op,String selected)throws Exception{
  operation=op;point=selected;boolean fault=!point.equals("none");require(operation.equals("delete")||operation.equals("flush"),"operation");
  User u=(User)UserFactory.createUser("delete-flush","delete-flush");Mind owner=new Mind(u);u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");base=new Base(Files.createTempDirectory("delete-flush-").resolve("values").toString(),9,new Object(),false,u);IStep previous=null;
  for(long id:new long[]{40,7,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(base,s);base.add(previous);}
  base.getRoot();base.get(7);base.get(91);base.get(40);require(snapshot().size()==3&&pending(),"prepared native resident chain and uncheckpointed WAL");Object root=raw(base,"rootId"),tail=raw(base,"topId");long flushes=(Long)raw(base,"flushCount");
  Throwable thrown=null;armed=true;try{if(operation.equals("delete"))base.delete(91);else base.flush();}catch(Throwable e){thrown=e;}finally{armed=false;}
  require((thrown!=null)==fault,"mutation outcome");if(fault)require(thrown.getClass()==InjectedMutationFailure.class&&thrown.getMessage().equals("diagnostic delete-flush boundary "+point),"native probe exception preserved");
  if(fault){refused("failed native "+operation);require(Objects.equals(root,raw(base,"rootId"))&&Objects.equals(tail,raw(base,"topId")),"failed body did not invalidate endpoints");
   if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.finish();}refused("failed native "+operation);
  }else{List<ResidentPersistentRead.Row> after=snapshot();require(after.size()==(operation.equals("delete")?2:3),"settled physical outcome");if(operation.equals("delete"))require(after.get(0).id==7&&after.get(1).id==40&&raw(base,"rootId")==null&&raw(base,"topId")==null,"successful delete infers remaining endpoints locally");else require(Objects.equals(root,raw(base,"rootId"))&&Objects.equals(tail,raw(base,"topId")),"successful flush preserves native endpoints");}
  // Native controls only after the primary read-only outcome.
  boolean indexed=base.containsKey(91),manifest=((Map<?,?>)raw(raw(base,"integrity"),"entries")).containsKey(91L),cached=((Map<?,?>)raw(base,"cache")).containsKey(91L),wal=pending();
  if(operation.equals("delete")){require(indexed==(point.equals("delete-after-wal")),"native delete index phase");require(manifest==(fault&&!point.equals("delete-after-integrity")),"native delete manifest phase");require(!cached,"native eager cache invalidation");require(wal,"delete remains pending until checkpoint");}
  else{require(indexed&&manifest&&cached,"flush keeps resident record universe");require((Long)raw(base,"flushCount")==flushes+1,"one native flush attempt");require(wal==(fault&&!point.equals("flush-after-checkpoint")),"native recovery checkpoint phase");}
  if(fault){base.clearCache();base.get(7);base.get(40);refused("failed native "+operation);}
  String row="DELETE_FLUSH_NATIVE operation="+operation+" point="+point+" exception="+(fault?"InjectedMutationFailure":"none")+" indexed_head="+indexed+" manifest_head="+manifest+" cached_head="+cached+" pending_WAL="+wal+" physical="+(fault?"refused":"admitted")+" active_checks="+activeChecks;
  write(".physical.txt",row);System.out.println(row);System.out.println("DELETE_FLUSH_OK checks="+checks+" shadow="+shadow);
 }
}
