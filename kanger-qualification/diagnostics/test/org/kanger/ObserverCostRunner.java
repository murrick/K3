package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import org.kanger.units.*;
/** Bounded resident microbenchmark; never an inference-throughput claim. */
public final class ObserverCostRunner {
 static final com.sun.management.ThreadMXBean ALLOCATION=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static final int WARMUP=16,SAMPLES=32,SET_ROUNDS=1024,EXPORT_CYCLES=16,DIRTY=8;
 static volatile long sink;
 static Mind mind;static Term a,b;static List<TVariable> vars=new ArrayList<>();static List<TValue> values=new ArrayList<>();
 static String mode;static int checks;static List<String> referenceTrace;
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static long bytes(){long result=ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId());require(result>=0,"allocation counter valid");return result;}
 static void observe(String reason){BeforeAuthorityJournal.observe(mind,reason);StreamAuthorityJournal.observe(mind,reason);}
 static void mutate(Term term){for(int i=0;i<DIRTY;i++){TValue value=values.get(i);value.setValue(term);sink=value.getValueId();}}
 static void setters(){long total=0;for(int i=0;i<SET_ROUNDS;i++){mutate((i&1)==0?b:a);total+=sink;}sink=total;}
 static List<String> exportCycle(){
  QualifiedJournalConsumer.Session session=mode.equals("attached")?QualifiedJournalConsumer.open():null;
  try{
   if(session!=null)observe("baseline");mutate(b);if(session!=null)observe("changed");mutate(a);if(session!=null)observe("restored");
   return session==null?null:session.finish();
  }finally{if(session!=null)session.close();}
 }
 static List<String> exports(){List<String> trace=null;for(int i=0;i<EXPORT_CYCLES;i++)trace=exportCycle();return trace;}
 static String nativeControl()throws Exception{return nativeControl(a);}
 static String nativeControl(Term expected)throws Exception{
  SortedMap<Long,String> independent=new TreeMap<>();
  for(int i=0;i<vars.size();i++){
   List<TValue> actual=new ArrayList<>();mind.getTValues().forEach(vars.get(i),o->{actual.add((TValue)o);return true;});
   require(actual.size()==1&&actual.get(0)==values.get(i),"native identity/order");require(actual.get(0).getValueId()==(i<DIRTY?expected:a).getId(),"native final payload");
   independent.put(vars.get(i).getId(),ResidentTValueRead.encode(mind,actual));
  }
  String before=ResidentTValueRead.fingerprint(mind);require(independent.equals(ResidentTValueRead.authority(mind)),"native full authority");require(independent.equals(StreamAuthorityRead.authority(mind)),"native stream authority");require(before.equals(ResidentTValueRead.fingerprint(mind)),"pure full readers");
  return independent.toString();
 }
 static void traceControl(List<String> trace){if(trace==null)return;require(!trace.isEmpty(),"checked export");if(referenceTrace==null)referenceTrace=trace;else require(referenceTrace.equals(trace),"repeatable complete trace");}
 public static void main(String[] args)throws Exception{
  mode=args[0];int size=Integer.parseInt(args[1]);String target=System.getProperty("result.path");
  require(size>=DIRTY,"fixture size");require(mode.equals("clean")||mode.equals("disabled")||mode.equals("attached"),"explicit mode");require(!Boolean.getBoolean("journal.cost.enabled"),"internal phase timers disabled");
  require(ALLOCATION.isThreadAllocatedMemorySupported(),"allocation supported");ALLOCATION.setThreadAllocatedMemoryEnabled(true);require(!TValueObservation.attached(),"observer initially disabled");
  User user=(User)UserFactory.createUser("observer-cost","observer-cost");mind=new Mind(user);a=(Term)mind.getTerms().add("a");b=(Term)mind.getTerms().add("b");
  for(int i=0;i<size;i++){Rule rule=new Rule(mind);mind.getRules().register(rule);TVariable v=mind.getTVars().createTVar(rule,mind.getTerms().add("v"+i));vars.add(v);values.add(mind.getTValues().add(v,a));}
  for(TVariable v:vars)mind.getTValues().forEach(v,o->true);require(!mind.isStorageUsed(),"resident memory only");String baseline=nativeControl();require(a.getId()!=b.getId(),"distinct payload IDs");mutate(b);require(!baseline.equals(nativeControl(b)),"real native payload changes");mutate(a);require(baseline.equals(nativeControl()),"native payload restores");
  List<String> rows=new ArrayList<>();rows.add("workload,iteration,operations,wall_ns,allocated_bytes");
  // Each callback-only batch owns a fresh seeded session. Seed/check/finish are outside its timer.
  List<String> setterReference=null;
  for(int i=-WARMUP;i<SAMPLES;i++){
   QualifiedJournalConsumer.Session writes=mode.equals("attached")?QualifiedJournalConsumer.open():null;
   try{
    if(writes!=null)observe("baseline");
    long alloc=bytes(),start=System.nanoTime();setters();long elapsed=System.nanoTime()-start,allocated=bytes()-alloc;
    require(sink==(SET_ROUNDS/2)*(a.getId()+b.getId()),"every setter batch consumed actual payloads");
    if(i>=0)rows.add("setter,"+i+","+(SET_ROUNDS*DIRTY)+","+elapsed+","+allocated);
    if(writes!=null){
     require(ConsumerHooks.counts.get("metadata")==SET_ROUNDS*DIRTY,"all native setter callbacks");
     observe("batch");List<String> trace=writes.finish();require(!trace.isEmpty(),"setter session fully checked");
     if(setterReference==null)setterReference=trace;else require(setterReference.equals(trace),"repeatable setter trace");
    }else require(ConsumerHooks.counts.isEmpty(),"disabled hooks record nothing");
   }finally{if(writes!=null)writes.close();}
   require(baseline.equals(nativeControl()),"setter native parity");
  }
  if(setterReference!=null)Files.write(Paths.get(target+".setter.trace"),setterReference);
  for(int i=-WARMUP;i<SAMPLES;i++){
   long alloc=bytes(),start=System.nanoTime();List<String> trace=exports();long elapsed=System.nanoTime()-start,allocated=bytes()-alloc;
   if(i>=0)rows.add("export,"+i+","+EXPORT_CYCLES+","+elapsed+","+allocated);
   traceControl(trace);require(baseline.equals(nativeControl()),"export native parity");require(!TValueObservation.attached(),"export owner released");
  }
  Files.write(Paths.get(target+".csv"),rows);Files.write(Paths.get(target+".native.txt"),Collections.singletonList(baseline));
  if(referenceTrace!=null)Files.write(Paths.get(target+".export.trace"),referenceTrace);
  require(!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty(),"no diagnostic capture references retained");
  System.out.println("OBSERVER_COST_OK mode="+mode+" size="+size+" samples="+SAMPLES+" warmup="+WARMUP+" checks="+checks+" sink="+sink);
 }
}
