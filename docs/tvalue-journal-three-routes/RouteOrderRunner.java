package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.lang.reflect.*;
import org.kanger.units.*;
/** Fault-oriented route order probe: changes diagnostic state only, never native data. */
public final class RouteOrderRunner {
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Mind mind=new Mind((User)UserFactory.createUser("route-order","route-order"));
  Rule r=new Rule(mind);mind.getRules().register(r);TVariable variable=mind.getTVars().createTVar(r,mind.getTerms().add("v"));
  TValue value=mind.getTValues().add(variable,mind.getTerms().add("a"));mind.getTValues().forEach(variable,o->true);
  String nativeBefore=ResidentTValueRead.fingerprint(mind);long current=value.getTVarId();require(current==0,"known native route");
  long[] candidates={Long.MIN_VALUE,-1,0,1,Long.MAX_VALUE};List<String> evidence=new ArrayList<>();int cases=0;
  for(Class<?> journal:new Class<?>[]{BeforeAuthorityJournal.class,StreamAuthorityJournal.class})for(long route:candidates)for(long old:candidates){
   journal.getMethod("begin").invoke(null);
   try{
    journal.getMethod("observe",Mind.class,String.class).invoke(null,mind,"baseline");
    Field currentField=journal.getDeclaredField("CURRENT");currentField.setAccessible(true);ThreadLocal<?> local=(ThreadLocal<?>)currentField.get(null);
    Object session=local.get();Map<Mind,Object> states=(Map<Mind,Object>)ResidentTValueRead.field(session,"states");Object state=states.get(mind);
    ((Map<TValue,Long>)ResidentTValueRead.field(state,"observed")).put(value,route);
    List<String> rows=(List<String>)ResidentTValueRead.field(session,"rows");int start=rows.size();
    journal.getMethod("metadata",TValue.class,long.class,long.class,long.class,String.class).invoke(null,value,value.getId(),old,value.getValueId(),"route-probe");
    List<String> actual=new ArrayList<>(rows.subList(start,rows.size()));SortedSet<Long> expected=new TreeSet<>(Arrays.asList(route,old,current));require(actual.size()==expected.size(),"deduplicated count");int index=0;
    for(long id:expected){require(actual.get(index++).contains(" variable="+id+" value="),"signed route ordering");}
    require(((Number)ResidentTValueRead.field(session,"touches")).intValue()==expected.size(),"touch count preserved");
    List<String> errors=(List<String>)ResidentTValueRead.field(session,"errors");require(errors.size()==(old==current?0:1),"identity mutation diagnostic preserved");
    evidence.add(journal.getSimpleName()+" route="+route+" old="+old+" rows="+actual+" errors="+errors);cases++;
    require(nativeBefore.equals(ResidentTValueRead.fingerprint(mind)),"native untouched");
   }finally{journal.getDeclaredMethod("discard").invoke(null);}
  }
  require(!TValueObservation.attached(),"no observer retained");Files.write(Paths.get(System.getProperty("result.path")+".routes.txt"),evidence);
  System.out.println("ROUTE_ORDER_OK cases="+cases+" nativeUntouched=true");
 }
}
