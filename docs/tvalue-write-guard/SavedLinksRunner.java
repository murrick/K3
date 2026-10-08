package org.kanger;
import java.nio.file.*;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
public final class SavedLinksRunner {
 static int checks;
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
 @SuppressWarnings("unchecked")static Map<Long,IStep> cache(Base b)throws Exception{return (Map<Long,IStep>)raw(b,"cache");}
 static void refusal(Base b,String why)throws Exception{String before=ResidentPersistentRunner.fingerprint(b);boolean failed=false;try{ResidentPersistentRead.snapshot(b);}catch(AssertionError e){failed=e.getMessage().contains(why);}require(failed,"refusal "+why);require(before.equals(ResidentPersistentRunner.fingerprint(b)),"pure refusal "+why);}
 public static void main(String[] args)throws Exception{try{run();}finally{RecordedLinks.disable();}}
 static void run()throws Exception{
  User u=(User)UserFactory.createUser("saved-links","saved-links");Mind owner=new Mind(u);u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");
  Base b=new Base(Files.createTempDirectory("saved-links-").resolve("values").toString(),9,new Object(),false,u);IStep previous=null;
  for(long id:new long[]{40,7,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(b,s);b.add(previous);}b.flush();b.get(7);b.get(91);b.get(40);
  refusal(b,"recording inactive");RecordedLinks.enable();refusal(b,"no saved-link witness");
  // Native reload is fixture preparation, never a reader repair.
  b.clearCache();b.get(7);b.get(91);b.get(40);String before=ResidentPersistentRunner.fingerprint(b);List<ResidentPersistentRead.Row> pristine=ResidentPersistentRead.snapshot(b);
  require(pristine.toString().equals("[91:191:391:-1, 7:107:307:-1, 40:140:340:-1]"),"saved order");require(before.equals(ResidentPersistentRunner.fingerprint(b)),"positive pure");require(raw(b,"rootId")==null&&raw(b,"topId")==null,"endpoints remain unresolved");
  Map<Long,IStep> nodes=new HashMap<>();for(Map.Entry<Long,IStep> e:cache(b).entrySet())nodes.put(e.getKey(),e.getValue());
  nodes.get(7L).setNext(nodes.get(40L));nodes.get(40L).setNext(nodes.get(91L));nodes.get(91L).setNext(null);refusal(b,"resident link differs from saved witness");
  nodes.get(91L).setNext(nodes.get(7L));nodes.get(7L).setNext(nodes.get(40L));nodes.get(40L).setNext(null);require(ResidentPersistentRead.snapshot(b).toString().equals(pristine.toString()),"restored links match witness");
  // Controlled stale resident reinsertion after a real native rewrite: same bytes, new manifest entry identity.
  IStep old=nodes.get(91L);old.getData(owner);b.update(old);cache(b).put(91L,old);refusal(b,"stale saved-link witness");
  b.clearCache();b.get(91);b.get(7);refusal(b,"missing resident link");b.get(40);require(ResidentPersistentRead.snapshot(b).toString().equals(pristine.toString()),"native redecode replaces stale witness");
  RecordedLinks.disable();refusal(b,"recording inactive");RecordedLinks.enable();refusal(b,"no saved-link witness");b.close();refusal(b,"closed or custom data");
  System.out.println("SAVED_LINKS_NATIVE order="+pristine+" native_endpoints=unresolved unsaved_links=refused stale_entry=refused missing=refused inactive=refused late_activation=refused closed=refused");System.out.println("SAVED_LINKS_OK checks="+checks);
 }
}
