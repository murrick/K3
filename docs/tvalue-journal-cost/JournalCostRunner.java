package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.units.*;
import org.kanger.factory.TValueFactory;
/** No I/O/settlement; one root, one stable bucket per variable, one scalar term rewrite. */
public final class JournalCostRunner {
 static Mind mind;static TValue edited;static Term a,b;static int checks;static boolean shadow=Boolean.getBoolean("persistence.shadow");
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static List<String> run(int updates,boolean measured)throws Exception{
  List<String> wall=new ArrayList<>();List<String> profile=null;SortedMap<Long,String> last=null;
  if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.observe(mind,"baseline");profile=TValueDirtyJournal.profile();}
  for(int i=0;i<updates;i++){
   Term value=(i%2==0)?b:a;long setter=System.nanoTime();edited.setValue(value);setter=System.nanoTime()-setter;
   String before=ResidentTValueRead.fingerprint(mind);long start=System.nanoTime();
   if(shadow)TValueDirtyJournal.observe(mind,"update-"+i);else last=ResidentTValueRead.authority(mind);
   long duration=System.nanoTime()-start;require(before.equals(ResidentTValueRead.fingerprint(mind)),"observer pure");require(edited.getValueId()==value.getId(),"native setter outcome");
   wall.add(i+","+setter+","+duration);
  }
  if(shadow){List<String> trace=TValueDirtyJournal.finish();require(trace.get(trace.size()-1).contains("bucketReads="+updates+" seeds=1"),"one bucket per update and single baseline");if(measured){Files.write(Paths.get(System.getProperty("journal.path")+".trace"),trace);Files.write(Paths.get(System.getProperty("journal.path")+".phases.csv"),profile);}}
  if(measured)Files.write(Paths.get(System.getProperty("journal.path")+".wall.csv"),wall);
  return wall;
 }
 public static void main(String[] args)throws Exception{
  int size=Integer.parseInt(args[0]),updates=48;User u=(User)UserFactory.createUser("journal-cost","journal-cost");mind=new Mind(u);TValueFactory f=mind.getTValues();a=(Term)mind.getTerms().add("cost-a");b=(Term)mind.getTerms().add("cost-b");List<TVariable> vars=new ArrayList<>();
  for(int i=0;i<size;i++){TVariable v=MaterializationRoutingRunner.variable(mind,"cost-var-"+i);vars.add(v);TValue value=f.add(v,a);if(i==0)edited=value;}
  for(TVariable v:vars)f.forEach(v,o->true);require(!mind.isStorageUsed(),"memory-only admitted scope");require(ResidentTValueRead.authority(mind).size()==size,"prepared all variables");
  run(16,false);run(updates,true);SortedMap<Long,String> finalView=ResidentTValueRead.authority(mind);require(finalView.size()==size&&edited.getValueId()==a.getId(),"complete final projection");
  String row="COST_NATIVE size="+size+" updates="+updates+" variables="+finalView.size()+" final_term=a";Files.write(Paths.get(System.getProperty("journal.path")+".native.txt"),Collections.singletonList(row));Files.write(Paths.get(System.getProperty("journal.path")+".final-view.txt"),Collections.singletonList(finalView.toString()));System.out.println(row);System.out.println("JOURNAL_COST_OK checks="+checks+" shadow="+shadow);
 }
}
