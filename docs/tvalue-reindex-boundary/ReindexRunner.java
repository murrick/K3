package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
/** Explicit native controls surround pure physical observations. No batch guard added. */
public final class ReindexRunner {
 static Base source,target;static Mind owner;static int copied,checks,selected;static boolean armed;
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 public static final class InjectedCopyFailure extends RuntimeException{InjectedCopyFailure(){super("diagnostic reindex copy boundary");}}
 static int read(Base b)throws Exception{String before=ResidentPersistentRunner.fingerprint(b);int n=ResidentPersistentRead.snapshot(b).size();require(before.equals(ResidentPersistentRunner.fingerprint(b)),"physical observation pure");return n;}
 public static void copied(Object from,Object to,Object step)throws Exception{
  if(!armed)return;require(from==source&&to==target,"native source/target identity");copied++;
  // Deliberate native destination decode, not part of the read-only observer.
  target.get(((IStep)step).getId());
  require(read(source)==3&&read(target)==copied,"native source complete and target prefix admitted");
  if(copied==selected)throw new InjectedCopyFailure();
 }
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(Integer.parseInt(args[0]));}finally{armed=false;RecordedLinks.disable();}}
 static void run(int stop)throws Exception{
  selected=stop;User u=(User)UserFactory.createUser("reindex","reindex");owner=new Mind(u);u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");Path dir=Files.createTempDirectory("reindex-boundary-");source=new Base(dir.resolve("source").toString(),9,new Object(),false,u);target=new Base(dir.resolve("target").toString(),9,new Object(),false,u);
  IStep previous=null;for(long id:new long[]{7,40,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(source,s);source.add(previous);}
  source.getRoot();source.get(7);source.get(40);source.get(91);require(read(source)==3&&read(target)==0,"prepared source and empty target");long writes=(Long)ResidentPersistentRead.raw(source,"writeCount");
  Throwable thrown=null;armed=true;try{source.reindex(target,owner);}catch(Throwable e){thrown=e;}finally{armed=false;}
  require((thrown!=null)==(stop!=0),"native copy outcome");if(thrown!=null)require(thrown.getClass()==InjectedCopyFailure.class&&thrown.getMessage().equals("diagnostic reindex copy boundary"),"native exception preserved");
  require(copied==(stop==0?3:stop)&&read(source)==3&&read(target)==copied,"post-return physical source and prefix");require((Long)ResidentPersistentRead.raw(source,"writeCount")==writes&&(Long)ResidentPersistentRead.raw(target,"writeCount")==copied,"native per-Base counters");
  RecordedLinks.requireSettled(source);RecordedLinks.requireSettled(target);
  if(Boolean.getBoolean("persistence.shadow")){TValueDirtyJournal.begin();TValueDirtyJournal.finish();}require(read(target)==copied,"new empty session cannot certify whole-copy completion");
  String row="REINDEX_NATIVE stop="+stop+" exception="+(thrown==null?"none":"InjectedCopyFailure")+" source=3 target="+copied+" physical=admitted whole_copy="+(stop==0?"returned":"failed");Files.write(Paths.get(System.getProperty("journal.path")+".physical.txt"),Collections.singletonList(row));System.out.println(row);System.out.println("REINDEX_OK checks="+checks);
 }
}
