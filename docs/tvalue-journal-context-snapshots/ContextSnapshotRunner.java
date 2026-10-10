package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import org.kanger.units.*;
/** Reentrant diagnostic hook probe; no claim of arbitrary extension qualification. */
public final class ContextSnapshotRunner {
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 static Object session(Class<?> journal)throws Exception{Field f=journal.getDeclaredField("CURRENT");f.setAccessible(true);return ((ThreadLocal<?>)f.get(null)).get();}
 @SuppressWarnings("unchecked") static Object state(Class<?> journal,Mind mind)throws Exception{return ((Map<Mind,Object>)ResidentTValueRead.field(session(journal),"states")).get(mind);}
 @SuppressWarnings("unchecked") static void registerProbe(Class<?> journal,Mind mind,TValue probe)throws Exception{((Map<TValue,Long>)ResidentTValueRead.field(state(journal,mind),"observed")).put(probe,probe.getTVarId());}
 static final class Probe extends TValue {
  final TValue nativeValue;Runnable action;
  Probe(TValue value){nativeValue=value;}
  public long getTVarId(){Runnable work=action;action=null;if(work!=null)work.run();return nativeValue.getTVarId();}
  public long getId(){return nativeValue.getId();} public long getValueId(){return nativeValue.getValueId();}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  List<String> evidence=new ArrayList<>();int cases=0;
  for(Class<?> journal:new Class<?>[]{BeforeAuthorityJournal.class,StreamAuthorityJournal.class})for(String operation:new String[]{"touch","metadata"}){
   Mind root=new Mind((User)UserFactory.createUser("membership-"+journal.getSimpleName()+"-"+operation,"membership-"+journal.getSimpleName()+"-"+operation));Rule rule=new Rule(root);root.getRules().register(rule);TVariable variable=root.getTVars().createTVar(rule,root.getTerms().add("v"));TValue value=root.getTValues().add(variable,root.getTerms().add("a"));root.getTValues().forEach(variable,o->true);
   Mind child=new Mind(root),grand=new Mind(child),late=new Mind(root);String before=ResidentTValueRead.fingerprint(root)+ResidentTValueRead.fingerprint(child)+ResidentTValueRead.fingerprint(grand)+ResidentTValueRead.fingerprint(late);
   journal.getMethod("begin").invoke(null);
   try{
    for(Mind m:new Mind[]{root,child,grand})journal.getMethod("observe",Mind.class,String.class).invoke(null,m,"baseline");
    Object current=session(journal);List<String> rows=(List<String>)ResidentTValueRead.field(current,"rows");Probe probe=new Probe(value);
    if(operation.equals("metadata"))for(Mind m:new Mind[]{root,child,grand})registerProbe(journal,m,probe);
    Object oldArray=null;
    try{oldArray=ResidentTValueRead.field(current,"ordered");}catch(NoSuchFieldException baseline){}
    final Probe target=probe;
    probe.action=()->{try{
     journal.getMethod("mark",Mind.class).invoke(null,late);registerProbe(journal,late,target);
     journal.getMethod("retire",Mind.class).invoke(null,child);
    }catch(Exception e){throw new RuntimeException(e);}};
    int start=rows.size();
    if(operation.equals("touch"))journal.getMethod("touch",Mind.class,TValue.class,String.class).invoke(null,root,probe,"membership-probe");
    else journal.getMethod("metadata",TValue.class,long.class,long.class,long.class,String.class).invoke(null,probe,value.getId(),value.getTVarId(),value.getValueId(),"membership-probe");
    List<String> first=new ArrayList<>();for(String row:rows.subList(start,rows.size()))if(row.startsWith("TOUCH "))first.add(row);
    require(first.size()==2&&first.get(0).startsWith("TOUCH ctx=1 ")&&first.get(1).startsWith("TOUCH ctx=3 "),"entry snapshot excludes new context and skips newly retired state");
    if(oldArray!=null){Object next=ResidentTValueRead.field(current,"ordered");require(next!=oldArray&&Array.getLength(oldArray)==3&&Array.getLength(next)==4,"old array membership immutable");for(int i=0;i<3;i++)require(Array.get(oldArray,i)==Array.get(next,i),"old entries preserved");}
    journal.getMethod("complete",Mind.class).invoke(null,late);start=rows.size();
    if(operation.equals("touch"))journal.getMethod("touch",Mind.class,TValue.class,String.class).invoke(null,root,probe,"membership-next");
    else journal.getMethod("metadata",TValue.class,long.class,long.class,long.class,String.class).invoke(null,probe,value.getId(),value.getTVarId(),value.getValueId(),"membership-next");
    List<String> second=new ArrayList<>();for(String row:rows.subList(start,rows.size()))if(row.startsWith("TOUCH "))second.add(row);
    require(second.size()==3&&second.get(0).startsWith("TOUCH ctx=1 ")&&second.get(1).startsWith("TOUCH ctx=3 ")&&second.get(2).startsWith("TOUCH ctx=4 "),"next callback sees new context in order");
    List<String> trace=(List<String>)journal.getMethod("finish").invoke(null);require(session(journal)==null,"finish drops cached snapshot");
    evidence.add(journal.getSimpleName()+" operation="+operation+" first="+first+" second="+second+" trace="+trace);cases++;
    String after=ResidentTValueRead.fingerprint(root)+ResidentTValueRead.fingerprint(child)+ResidentTValueRead.fingerprint(grand)+ResidentTValueRead.fingerprint(late);require(before.equals(after),"native state unchanged");
   }finally{journal.getDeclaredMethod("discard").invoke(null);root.release(late);child.release(grand);root.release(child);}
  }
  Files.write(Paths.get(System.getProperty("result.path")+".contexts.txt"),evidence);System.out.println("CONTEXT_SNAPSHOT_OK cases="+cases+" reentrantRegistration=true retirement=true nativeUntouched=true");
 }
}
