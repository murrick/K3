package org.kanger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
public final class OptionsContract {
 static int checks;
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
 static void unknown(Runnable r){try{r.run();throw new AssertionError("accepted unknown name");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args)throws Exception {
  String[] names=OptimizationOptions.names(); Map<String,String> saved=new HashMap<>();
  for(String n:names)saved.put(n,System.getProperty("kanger.experiment."+n));
  try {
   User ua=(User)UserFactory.createUser("keys-a","keys-a"),ub=(User)UserFactory.createUser("keys-b","keys-b");
   Mind a=new Mind(ua),same=new Mind(ua),b=new Mind(ub);
   for(String n:names){String key="kanger.experiment."+n;
    System.clearProperty(key);check(OptimizationOptions.enabled(null,n),"default");
    System.setProperty(key,"false");check(!OptimizationOptions.enabled(null,n),"live false");
    System.setProperty(key,"TrUe");check(OptimizationOptions.enabled(null,n),"live true");
    System.setProperty(key,"garbage");check(!OptimizationOptions.enabled(null,n),"parse false");
    System.clearProperty(key);check(OptimizationOptions.enabled(a,n),"default after read");
    OptimizationOptions.set(a,n,false);check(!OptimizationOptions.enabled(same,n),"shared User");check(OptimizationOptions.enabled(b,n),"User isolation");
    System.setProperty(key,"false");OptimizationOptions.set(a,n,true);check(OptimizationOptions.enabled(same,n),"live individual override");check(!OptimizationOptions.enabled(b,n),"other user JVM fallback");
   }
   for(boolean value:new boolean[]{false,true,false}){OptimizationOptions.setAll(a,value);for(String n:names){check(OptimizationOptions.enabled(a,n)==value,"setAll");check(OptimizationOptions.enabled(same,n)==value,"setAll shared User");check(!OptimizationOptions.enabled(b,n),"setAll isolation");}}
   unknown(()->OptimizationOptions.enabled(a,"unknown"));unknown(()->OptimizationOptions.enabled(null,"unknown"));unknown(()->OptimizationOptions.enabled(a,null));unknown(()->OptimizationOptions.set(a,"unknown",true));unknown(()->OptimizationOptions.set(null,names[0],true));unknown(()->OptimizationOptions.setAll(null,true));
   check(OptimizationOptions.snapshot(a).size()==8,"snapshot");names[0]="mutated";check(!OptimizationOptions.names()[0].equals("mutated"),"names clone");
   AtomicReference<Throwable> failure=new AtomicReference<>();
   Thread writer=new Thread(()->{try{for(int i=0;i<200000;i++)OptimizationOptions.setAll(a,(i&1)==0);}catch(Throwable t){failure.set(t);}});
   writer.start();int reads=0;do{int word=ua.optimizationOptions().overrides;check(word==0xff||word==0xffff,"atomic publication "+word);reads++;}while(writer.isAlive());writer.join();check(failure.get()==null,"writer");
   // Separate enabled/snapshot calls are not a multi-option transaction.
   System.out.println("OPTIONS_CONTRACT_OK checks="+checks+" atomic_reads="+reads);
  }finally{for(Map.Entry<String,String> e:saved.entrySet()){String k="kanger.experiment."+e.getKey();if(e.getValue()==null)System.clearProperty(k);else System.setProperty(k,e.getValue());}}
 }
}
