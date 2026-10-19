package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
/** Bounded resident queries through the public Mind API, not a full corpus. */
public final class ResidentInferenceRunner {
 static int checks;static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static void observe(Mind m,String reason){BeforeAuthorityJournal.observe(m,reason);StreamAuthorityJournal.observe(m,reason);}
 static String bindings(Mind m){SortedMap<Long,String> rows=new TreeMap<>();m.getQueryValues().forEach((v,values)->{List<Long> terms=new ArrayList<>();values.forEach(value->terms.add(value.getValueId()));Collections.sort(terms);rows.put(v.getId(),terms.toString());});return rows.toString();}
 static String values(Mind m){List<String> rows=new ArrayList<>();for(Map<String,org.kanger.interfaces.ITerm> row:m.getValues()){SortedMap<String,String> sorted=new TreeMap<>();row.forEach((key,value)->sorted.put(key,value.toString()));rows.add(sorted.toString());}Collections.sort(rows);return rows.toString();}
 public static void main(String[] args)throws Exception{
  String mode=args[0],target=System.getProperty("result.path");int repeats=Integer.parseInt(args[1]);
  require(mode.equals("attached")||mode.equals("disabled"),"explicit mode");
  User u=(User)UserFactory.createUser("resident-inference","resident-inference");Mind m=new Mind(u);
  String[] source={"!@x @y father(x,y) -> parent(x,y);","!@x @y parent(x,y) -> related(x,y);","!father(John,Tom);","!father(Tom,Sam);","!~male(Mary);"};
  for(String line:source)require(m.query(line,null,false)!=null,"accepted source "+line);
  require(!m.isStorageUsed(),"resident fixture");
  String[] queries={"?father(John,Tom);","?parent(John,Tom);","?related(John,Tom);","?male(Mary);","?unknown(John);","?$x parent(John,x);","?$x related(x,Sam);"};
  Boolean[] expected={true,true,true,false,null,true,true};
  List<String> nativeRows=new ArrayList<>(),csv=new ArrayList<>();csv.add("iteration,queries,wall_ns,allocated_bytes,query_wall_ns,query_allocated_bytes");
  com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();require(bean.isThreadAllocatedMemorySupported(),"allocation supported");bean.setThreadAllocatedMemoryEnabled(true);
  for(int i=0;i<repeats;i++){
   QualifiedJournalConsumer.Session session=mode.equals("attached")?QualifiedJournalConsumer.open():null;
   long queryWall=0,queryBytes=0;
   List<String> trace=null;SortedMap<String,Integer> events=null;
   long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
   try{
    if(session!=null)observe(m,"baseline");
    for(int q=0;q<queries.length;q++){
     long qa=bean.getThreadAllocatedBytes(Thread.currentThread().getId()),qs=System.nanoTime();
     Boolean result=m.query(queries[q],null,false);queryWall+=System.nanoTime()-qs;queryBytes+=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-qa;
     require(Objects.equals(result,expected[q]),"native answer "+queries[q]+" was "+result);
     require(!m.isStorageUsed(),"query remains resident");
     if(q>=5)require(values(m).equals("[{x=Tom}]"),"exact returned substitution");
     nativeRows.add(i+" "+queries[q]+" result="+result+" query="+m.getQueryString()+" reservations="+m.pendingTransactionCount()+" values="+values(m)+" bindings="+bindings(m)+" authority="+ResidentTValueRead.authority(m));
     require(m.pendingTransactionCount()==0,"query transactions released");
     if(session!=null)observe(m,"query-"+q);
    }
    if(session!=null){events=new TreeMap<>(ConsumerHooks.counts);require(events.getOrDefault("metadata",0)>0&&events.getOrDefault("add",0)>0,"real substitution callbacks");trace=session.finish();}
   }finally{if(session!=null)session.close();}
   long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;
   csv.add(i+","+queries.length+","+elapsed+","+bytes+","+queryWall+","+queryBytes);
   if(trace!=null){require(!trace.isEmpty(),"qualified trace");Files.write(Paths.get(target+"."+i+".trace"),trace);Files.write(Paths.get(target+"."+i+".events.txt"),Collections.singletonList(events.toString()));}
   require(!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty(),"session releases references");
  }
  Files.write(Paths.get(target+".native.txt"),nativeRows);Files.write(Paths.get(target+".csv"),csv);
  System.out.println("RESIDENT_INFERENCE_OK mode="+mode+" repeats="+repeats+" queries="+(repeats*queries.length)+" checks="+checks);
 }
}
