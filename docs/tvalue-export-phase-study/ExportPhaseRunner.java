package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import org.kanger.units.*;
/** Bounded resident microbenchmark; never an inference-throughput claim. */
public final class ExportPhaseRunner {
 static final com.sun.management.ThreadMXBean ALLOCATION=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static final int WARMUP=64,SAMPLES=128,SET_ROUNDS=1024,EXPORT_CYCLES=8,DIRTY=8;
 static volatile long sink;
 static Mind mind;static Term a,b;static List<TVariable> vars=new ArrayList<>();static List<TValue> values=new ArrayList<>();
 static String mode;static int checks;static List<String> referenceTrace;
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static long bytes(){long result=ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId());require(result>=0,"allocation counter valid");return result;}
 static void observe(String reason){BeforeAuthorityJournal.observe(mind,reason);StreamAuthorityJournal.observe(mind,reason);}
 static void mutate(Term term){for(int i=0;i<DIRTY;i++){TValue value=values.get(i);value.setValue(term);sink=value.getValueId();}}
 static void setters(){long total=0;for(int i=0;i<SET_ROUNDS;i++){mutate((i&1)==0?b:a);total+=sink;}sink=total;}
 static final String[] PHASES={"open","baseline","mutate_b","changed","mutate_a","restored","finish","close"};
 static long[] wall=new long[8],cpu=new long[8],allocation=new long[8];
 static long started,allocated,cpuStarted;
 static void start(){allocated=bytes();cpuStarted=ALLOCATION.getCurrentThreadCpuTime();started=System.nanoTime();}
 static void stop(int phase){wall[phase]+=System.nanoTime()-started;cpu[phase]+=ALLOCATION.getCurrentThreadCpuTime()-cpuStarted;allocation[phase]+=bytes()-allocated;}
 static List<String> exports(){List<String> trace=null;
  for(int i=0;i<EXPORT_CYCLES;i++){
   start();QualifiedJournalConsumer.Session session=QualifiedJournalConsumer.open();stop(0);
   try{
    start();observe("baseline");stop(1);
    start();mutate(b);stop(2);
    start();observe("changed");stop(3);
    start();mutate(a);stop(4);
    start();observe("restored");stop(5);
    start();trace=session.finish();stop(6);
   }finally{start();session.close();stop(7);}
  }return trace;
 }
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
  List<String> rows=new ArrayList<>();rows.add("workload,iteration,operations,wall_ns,allocated_bytes,cpu_ns");
  for(int i=-WARMUP;i<SAMPLES;i++){
   Arrays.fill(wall,0);Arrays.fill(cpu,0);Arrays.fill(allocation,0);
   long alloc=bytes(),start=System.nanoTime();List<String> trace=exports();long elapsed=System.nanoTime()-start,allocated=bytes()-alloc;
   if(i>=0)rows.add("total,"+i+","+EXPORT_CYCLES+","+elapsed+","+allocated+",0");
   if(i>=0)for(int phase=0;phase<8;phase++)rows.add(PHASES[phase]+","+i+","+EXPORT_CYCLES+","+wall[phase]+","+allocation[phase]+","+cpu[phase]);
   traceControl(trace);require(baseline.equals(nativeControl()),"export native parity");require(!TValueObservation.attached(),"export owner released");
  }
  Files.write(Paths.get(target+".csv"),rows);Files.write(Paths.get(target+".native.txt"),Collections.singletonList(baseline));
  if(referenceTrace!=null)Files.write(Paths.get(target+".export.trace"),referenceTrace);
  require(!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty(),"no diagnostic capture references retained");
  System.out.println("EXPORT_PHASE_OK mode="+mode+" size="+size+" samples="+SAMPLES+" warmup="+WARMUP+" checks="+checks+" sink="+sink);
 }
}
