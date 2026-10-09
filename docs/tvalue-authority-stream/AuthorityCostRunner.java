package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import org.kanger.units.*;
/** Full journals with independent sessions on one clean native memory state. */
public final class AuthorityCostRunner {
 static Mind mind;static List<TValue> edited=new ArrayList<>();static Term a,b;static int checks;
 static final com.sun.management.ThreadMXBean allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static long bytes(){return allocation.getThreadAllocatedBytes(Thread.currentThread().getId());}
 static long[] observe(boolean memo,String reason){long bytes=bytes(),start=System.nanoTime();if(memo)StreamAuthorityJournal.observe(mind,reason);else BeforeAuthorityJournal.observe(mind,reason);long elapsed=System.nanoTime()-start;return new long[]{elapsed,bytes()-bytes};}
 static void mutate(Term term){
  for(TValue value:edited){long id=value.getId(),variable=value.getTVarId(),old=value.getValueId();value.setValue(term);require(value.getValueId()==term.getId(),"actual native setter outcome");BeforeAuthorityJournal.metadata(value,id,variable,old,"setValue");StreamAuthorityJournal.metadata(value,id,variable,old,"setValue");}
 }
 static void run(int count,boolean measured)throws Exception{
  BeforeAuthorityJournal.begin();StreamAuthorityJournal.begin();BeforeAuthorityJournal.observe(mind,"baseline");StreamAuthorityJournal.observe(mind,"baseline");List<String> oldPhases=BeforeAuthorityJournal.profile(),memoPhases=StreamAuthorityJournal.profile();List<String> rows=new ArrayList<>();rows.add("iteration,first,old_wall_ns,memo_wall_ns,old_allocated_bytes,memo_allocated_bytes");
  for(int i=0;i<count;i++){
   mutate(i%2==0?b:a);String before=ResidentTValueRead.fingerprint(mind);long[] old,memo;
   if(i%2==0){old=observe(false,"update-"+i);require(before.equals(ResidentTValueRead.fingerprint(mind)),"prior observer pure");memo=observe(true,"update-"+i);}
   else{memo=observe(true,"update-"+i);require(before.equals(ResidentTValueRead.fingerprint(mind)),"memo observer pure");old=observe(false,"update-"+i);}
   require(before.equals(ResidentTValueRead.fingerprint(mind)),"paired observers pure");rows.add(i+","+(i%2==0?"old":"memo")+","+old[0]+","+memo[0]+","+old[1]+","+memo[1]);
  }
  List<String> oldTrace=BeforeAuthorityJournal.finish(),memoTrace=StreamAuthorityJournal.finish();require(oldTrace.equals(memoTrace),"complete traces equal");require(oldPhases.size()==count+2&&memoPhases.size()==count+2,"baseline updates end fully profiled");
  require(oldTrace.get(oldTrace.size()-1).contains("bucketReads="+(count*edited.size())+" seeds=1"),"only changed buckets and one baseline");
  if(measured){String target=System.getProperty("result.path");Files.write(Paths.get(target+".csv"),rows);Files.write(Paths.get(target+".old.phases.csv"),oldPhases);Files.write(Paths.get(target+".memo.phases.csv"),memoPhases);Files.write(Paths.get(target+".old.trace"),oldTrace);Files.write(Paths.get(target+".memo.trace"),memoTrace);Files.write(Paths.get(target+".final.txt"),Collections.singletonList(ResidentTValueRead.authority(mind).toString()));}
 }
 public static void main(String[] args)throws Exception{
  int size=Integer.parseInt(args[0]),dirty=Integer.parseInt(args[1]);require(Boolean.getBoolean("journal.cost.enabled"),"both phase timers enabled");require(allocation.isThreadAllocatedMemorySupported(),"allocation counter supported");allocation.setThreadAllocatedMemoryEnabled(true);
  User user=(User)UserFactory.createUser("paired-journal","paired-journal");mind=new Mind(user);a=(Term)mind.getTerms().add("a");b=(Term)mind.getTerms().add("b");List<TVariable> variables=new ArrayList<>();
  for(int i=0;i<size;i++){Rule rule=new Rule(mind);mind.getRules().register(rule);TVariable variable=mind.getTVars().createTVar(rule,mind.getTerms().add("v"+i));variables.add(variable);TValue value=mind.getTValues().add(variable,a);if(i<dirty)edited.add(value);}
  for(TVariable variable:variables)mind.getTValues().forEach(variable,o->true);require(!mind.isStorageUsed(),"memory only");require(ResidentTValueRead.authority(mind).size()==size,"complete native fixture");run(48,false);run(64,true);require(edited.stream().allMatch(value->value.getValueId()==a.getId()),"final native values");
  System.out.println("PAIRED_JOURNAL_OK size="+size+" dirty="+dirty+" pairs=64 checks="+checks);
 }
}
