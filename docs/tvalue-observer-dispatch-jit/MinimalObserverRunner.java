package org.kanger;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
/** Isolated dispatch probe, not a diagnostic consumer or inference benchmark. */
public final class MinimalObserverRunner {
 static final int CALLS=65536,WARMUP=64,SAMPLES=64;
 static final com.sun.management.ThreadMXBean ALLOC=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static volatile long sink;
 static final class Counter implements TValueObserver {
  long count,sum; int other;
  public void metadata(TValue v,long id,long variable,long term,String reason){count++;sum+=id+variable+term+reason.length();}
  public void constructed(Mind m){other++;} public void reset(Mind m){other++;} public void mark(Mind m){other++;}
  public void complete(Mind m){other++;} public void touch(Mind m,TValue v,String s){other++;}
  public void promoted(Mind m,TValueFactory f){other++;} public void beginSettlement(Mind m){other++;}
  public void endSettlement(Mind m){other++;} public void retire(Mind m){other++;}
  public Object beforeClear(Mind m,TValueFactory f){other++;return null;} public void afterClear(Mind m,Object t){other++;}
 }
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static long bytes(){return ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());}
 public static void main(String[] args)throws Exception {
  boolean attached=args[0].equals("attached");String target=System.getProperty("result.path");
  require(ALLOC.isThreadAllocatedMemorySupported(),"allocation supported");ALLOC.setThreadAllocatedMemoryEnabled(true);
  ObserverCostRunner.mind=new Mind((User)UserFactory.createUser("dispatch-probe","dispatch-probe"));
  Mind mind=ObserverCostRunner.mind;Term a=(Term)mind.getTerms().add("a"),b=(Term)mind.getTerms().add("b");
  ObserverCostRunner.a=a;ObserverCostRunner.b=b;
  for(int i=0;i<8;i++){Rule r=new Rule(mind);mind.getRules().register(r);TVariable v=mind.getTVars().createTVar(r,mind.getTerms().add("v"+i));ObserverCostRunner.vars.add(v);ObserverCostRunner.values.add(mind.getTValues().add(v,a));}
  for(TVariable v:ObserverCostRunner.vars)mind.getTValues().forEach(v,o->true);
  require(!mind.isStorageUsed(),"resident only");String baseline=ObserverCostRunner.nativeControl();
  List<String> rows=new ArrayList<>();rows.add("workload,iteration,operations,wall_ns,allocated_bytes");
  for(String workload:new String[]{"bridge","native"}){
   Counter counter=new Counter();TValueObservation.Attachment session=attached?TValueObservation.attach(counter):null;
   try {
    long expected=0;
    for(int i=0;i<8;i++){TValue v=ObserverCostRunner.values.get(i);expected+=(long)(CALLS/8)*(v.getId()+v.getTVarId()+"setValue".length())+(long)(CALLS/16)*(a.getId()+b.getId());}
    for(int sample=-WARMUP;sample<SAMPLES;sample++){
     long count=counter.count,sum=counter.sum,total=0,allocation=bytes(),start=System.nanoTime();
     for(int n=0;n<CALLS;n++){
      TValue v=ObserverCostRunner.values.get(n&7);Term next=((n/8)&1)==0?b:a;
      if(workload.equals("bridge"))TValueObservation.metadata(v,v.getId(),v.getTVarId(),((n/8)&1)==0?a.getId():b.getId(),"setValue");
      else v.setValue(next);
      total+=v.getValueId();
     }
     long elapsed=System.nanoTime()-start,allocated=bytes()-allocation;sink=total;
     require(counter.count-count==(attached?CALLS:0),"exact callbacks");require(counter.sum-sum==(attached?expected:0),"callback payload checksum");
     require(total==(workload.equals("bridge")?(long)CALLS*a.getId():(long)(CALLS/2)*(a.getId()+b.getId())),"native payload consumed");
     require(counter.other==0,"metadata only");require(baseline.equals(ObserverCostRunner.nativeControl()),"native restore and full readers");
     if(sample>=0)rows.add(workload+","+sample+","+CALLS+","+elapsed+","+allocated);
    }
    if(session!=null)require(!session.failed(),"session untainted");
   }finally{if(session!=null)session.close();}
  }
  require(!TValueObservation.attached(),"owner released");Files.write(Paths.get(target+".csv"),rows);Files.write(Paths.get(target+".native.txt"),Collections.singletonList(baseline));
  System.out.println("MINIMAL_OBSERVER_OK calls="+CALLS+" samples="+SAMPLES+" warmup="+WARMUP+" attached="+attached);
 }
}
