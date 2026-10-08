package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
/** Native clear/close boundaries, exact native nested flush call sites only. */
public final class ClearCloseRunner {
 static int checks,activeChecks,flushVisits;static boolean armed,shadow=Boolean.getBoolean("persistence.shadow");static String operation,point;static Base base;
 public static final class InjectedBoundaryFailure extends RuntimeException{InjectedBoundaryFailure(String p){super("diagnostic clear-close boundary "+p);}}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
 static void refused(String reason)throws Exception{String before=ResidentPersistentRunner.fingerprint(base);boolean failed=false;try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){failed=e.getMessage().contains(reason);}require(failed&&before.equals(ResidentPersistentRunner.fingerprint(base)),"pure refusal "+reason);}
 public static void hit(String p){if(!armed||!(p.startsWith(operation+"-")||p.startsWith("flush-")))return;
  String selected=p;if(p.startsWith("flush-")){if(p.equals("flush-after-index"))flushVisits++;selected=p+"-"+flushVisits;}
  try{if(base.isClosed()){refused("closed or custom data");boolean active=false;try{RecordedLinks.requireSettled(base);}catch(AssertionError e){active=e.getMessage().contains("active native "+operation);}require(active,"closed handle still inside native operation");}else refused("active native "+operation);activeChecks++;}catch(Exception e){throw new AssertionError(e);}
  if(selected.equals(point))throw new InjectedBoundaryFailure(selected);
 }
 static int physical()throws Exception{String before=ResidentPersistentRunner.fingerprint(base);int n=ResidentPersistentRead.snapshot(base).size();require(before.equals(ResidentPersistentRunner.fingerprint(base)),"pure settled read");return n;}
 static boolean pending()throws Exception{Method m=Base.class.getDeclaredMethod("hasPendingRecovery");m.setAccessible(true);return (Boolean)m.invoke(base);}
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(args[0],args[1]);}finally{armed=false;RecordedLinks.disable();}}
 static void run(String op,String selected)throws Exception{
  operation=op;point=selected;boolean empty=point.equals("empty"),fault=!point.equals("none")&&!empty;
  User u=(User)UserFactory.createUser("clear-close","clear-close");Mind owner=new Mind(u);u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");base=new Base(Files.createTempDirectory("clear-close-").resolve("values").toString(),9,new Object(),false,u);IStep previous=null;
  if(!empty)for(long id:new long[]{40,7,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(base,s);base.add(previous);}
  if(!empty){base.getRoot();base.get(7);base.get(91);base.get(40);}require(physical()==(empty?0:3),"prepared native resident chain");long flushes=(Long)raw(base,"flushCount");
  Throwable thrown=null;armed=true;try{if(op.equals("clear"))base.clear();else base.close();}catch(Throwable e){thrown=e;}finally{armed=false;}
  require((thrown!=null)==fault,"native outcome");
  boolean wrapped=op.equals("close")&&(point.startsWith("flush-")||point.equals("close-after-compact"));
  if(fault){require(thrown.getClass()==(wrapped?java.io.IOException.class:InjectedBoundaryFailure.class),"original native exception class");require(thrown.getMessage().equals((wrapped?InjectedBoundaryFailure.class.getName()+": ":"")+"diagnostic clear-close boundary "+point),"original native exception text");}
  boolean closed=base.isClosed();int entries=((Map<?,?>)raw(raw(base,"integrity"),"entries")).size(),cached=((Map<?,?>)raw(base,"cache")).size();boolean wal=pending();long count=(Long)raw(base,"flushCount")-flushes;
  if(fault){if(closed)refused("closed or custom data");else refused("failed native "+op);if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.finish();}if(closed)refused("closed or custom data");else refused("failed native "+op);
   // Closed handles cause the reader's earlier native-open check; expose only diagnostic gate to prove retained failure too.
   boolean retained=false;try{RecordedLinks.requireSettled(base);}catch(AssertionError e){retained=e.getMessage().contains("failed native "+op);}require(retained,"failure remains even after data.close");
  }else if(op.equals("clear")){require(physical()==0&&!closed&&entries==0&&cached==0&&!wal&&count==(empty?0:2),"settled clear and exact nested flush count");require(raw(base,"rootId")==null&&raw(base,"topId")==null,"native endpoint invalidation");}
  else{require(closed&&entries==3&&cached==0&&!wal&&count==1,"settled close native controls");refused("closed or custom data");RecordedLinks.requireSettled(base);}
  if(fault){int phase=point.startsWith("flush-")?(point.endsWith("-2")?2:1):0;
   if(op.equals("clear")){boolean erased=phase==2||point.equals("clear-after-integrity")||point.equals("clear-after-cache")||point.equals("clear-after-last-id")||point.equals("clear-after-endpoints");require(entries==(erased?0:3),"native manifest phase");boolean cacheCleared=point.equals("clear-after-cache")||point.equals("clear-after-last-id")||point.equals("clear-after-endpoints");require(cached==(cacheCleared?0:3)&&!closed,"native cache and handle phase");require(count==(phase==2||cacheCleared?2:1),"native clear flush attempts");}
   else{require(entries==3&&count==1,"native close manifest and flush count");boolean beforeCache=point.startsWith("flush-")||point.equals("close-after-compact");require(cached==(beforeCache?3:0),"native close cache phase");require(closed==(point.equals("close-after-data")||point.equals("close-after-endpoints")),"native close handle phase");}
   require(wal==(point.startsWith("flush-")&&!point.startsWith("flush-after-checkpoint")&&point.endsWith("-1")),"native WAL checkpoint phase");
  }
  String row="CLEAR_CLOSE_NATIVE operation="+op+" point="+point+" exception="+(thrown==null?"none":thrown.getClass().getSimpleName())+" manifest="+entries+" cached="+cached+" closed="+closed+" pending_WAL="+wal+" flushes="+count+" active_checks="+activeChecks;
  Files.write(Paths.get(System.getProperty("journal.path")+".physical.txt"),Collections.singletonList(row));System.out.println(row);System.out.println("CLEAR_CLOSE_OK checks="+checks+" shadow="+shadow);
 }
}
