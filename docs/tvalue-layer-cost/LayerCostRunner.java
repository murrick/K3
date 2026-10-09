package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import org.kanger.units.*;
/** Paired, alternating order, direct-memory bucket microbenchmark. */
public final class LayerCostRunner {
 static volatile long sink;static int checks;static Mind mind;static List<TVariable> vars=new ArrayList<>();
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static TVariable variable(String name)throws Exception{Rule r=new Rule(mind);mind.getRules().register(r);return mind.getTVars().createTVar(r,mind.getTerms().add(name));}
 static List<List<TValue>> read(int count,boolean memo)throws Exception{
  List<List<TValue>> result=new ArrayList<>();
  if(memo)try(ObservationLayers f=new ObservationLayers(mind)){for(int i=0;i<count;i++)result.add(f.bucket(vars.get(i).getId()));}
  else for(int i=0;i<count;i++)result.add(ResidentTValueRead.bucket(mind,vars.get(i).getId()));
  return result;
 }
 static final com.sun.management.ThreadMXBean allocation=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static long bytes(){return allocation.getThreadAllocatedBytes(Thread.currentThread().getId());}
 static long[] sample(int count,boolean memo)throws Exception{
  long b=bytes(),t=System.nanoTime();List<List<TValue>> result=read(count,memo);t=System.nanoTime()-t;b=bytes()-b;
  long total=0;for(List<TValue> bucket:result)for(TValue v:bucket)total+=v.getId();sink=total;return new long[]{t,b};
 }
 public static void main(String[] args)throws Exception{
  int size=Integer.parseInt(args[0]),count=Integer.parseInt(args[1]);require(allocation.isThreadAllocatedMemorySupported(),"allocation supported");allocation.setThreadAllocatedMemoryEnabled(true);
  User u=(User)UserFactory.createUser("layer-cost","layer-cost");mind=new Mind(u);Term a=(Term)mind.getTerms().add("a"),b=(Term)mind.getTerms().add("b");TValue edited=null;
  for(int i=0;i<size;i++){TVariable v=variable("v"+i);vars.add(v);TValue value=mind.getTValues().add(v,a);if(i==0)edited=value;}
  for(TVariable v:vars)mind.getTValues().forEach(v,o->true);require(!mind.isStorageUsed(),"memory only");
  try(ObservationLayers control=new ObservationLayers(mind)){for(int j=0;j<count;j++)control.bucket(vars.get(j).getId());require(control.extracted()==1,"root layer once");}
  for(int i=0;i<48;i++){edited.setValue(i%2==0?b:a);if(i%2==0){sample(count,false);sample(count,true);}else{sample(count,true);sample(count,false);}}
  List<String> rows=new ArrayList<>();rows.add("iteration,first,old_ns,memo_ns,old_allocated_bytes,memo_allocated_bytes");
  for(int i=0;i<64;i++){
   edited.setValue(i%2==0?b:a);String before=ResidentTValueRead.fingerprint(mind);long[] old,memo;
   if(i%2==0){old=sample(count,false);memo=sample(count,true);}else{memo=sample(count,true);old=sample(count,false);}
   require(before.equals(ResidentTValueRead.fingerprint(mind)),"pure paired sample");List<List<TValue>> x=read(count,false),y=read(count,true);require(x.equals(y),"canonical order and references equal");
   SortedMap<Long,String> full=ResidentTValueRead.authority(mind);require(full.size()==size,"fresh independent full view");
   for(int j=0;j<count;j++)require(ResidentTValueRead.encode(mind,y.get(j)).equals(full.get(vars.get(j).getId())),"bucket agrees with full view");
   rows.add(i+","+(i%2==0?"old":"memo")+","+old[0]+","+memo[0]+","+old[1]+","+memo[1]);
  }
  Files.write(Paths.get(System.getProperty("result.path")+".csv"),rows);Files.write(Paths.get(System.getProperty("result.path")+".final.txt"),Collections.singletonList(ResidentTValueRead.authority(mind).toString()));
  System.out.println("LAYER_COST_OK size="+size+" buckets="+count+" pairs=64 checks="+checks);
 }
}
