package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.storage.*;
import org.kanger.units.TValue;
import org.kanger.interfaces.internal.IStep;
/** Real DB publication control, with a controlled throw after native successful target add. */
public final class DBReindexRunner {
 static Base source,target;static DB db;static int copied,checks,selected;static boolean armed;
 public static final class InjectedDBCopyFailure extends RuntimeException{InjectedDBCopyFailure(){super("diagnostic DB reindex copy boundary");}}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static int read(Base b)throws Exception{String before=ResidentPersistentRunner.fingerprint(b);int n=ResidentPersistentRead.snapshot(b).size();require(before.equals(ResidentPersistentRunner.fingerprint(b)),"pure physical observation");return n;}
 public static void copied(Object from,Object to,Object step)throws Exception{
  if(!armed||from!=source)return;if(target==null)target=(Base)to;require(to==target&&target!=source,"distinct target identity");copied++;target.get(((IStep)step).getId());require(read(source)==3&&read(target)==copied,"during native DB copy complete source and admitted prefix");require(db.getBase("tvalues")==source&&!source.isClosed(),"live registry still publishes source");
  if(copied==selected)throw new InjectedDBCopyFailure();
 }
 public static void main(String[] args)throws Exception{RecordedLinks.enable();try{run(Integer.parseInt(args[0]));}finally{armed=false;RecordedLinks.disable();}}
 static void run(int stop)throws Exception{
  selected=stop;User u=(User)UserFactory.createUser("db-reindex","db-reindex");u.setProperty("cache.enable","true");u.setProperty("cache.size","4194304");u.setProperty("cache.data.size","0");db=new DB();db.init(u);Mind owner=new Mind(u);owner=(Mind)owner.useStorage("generation");owner.getTValues().transaction(null);source=(Base)u.getStorage("tvalues");
  IStep previous=null;for(long id:new long[]{7,40,91}){TValue v=new TValue(owner);v.setId(id);v.setPersistentReferences(300+id,100+id);Step s=new Step();s.setId(id);s.setHash(v.getHash());s.setData(v);s.setNext(previous);previous=new Sapato(source,s);source.add(previous);}
  source.getRoot();source.get(7);source.get(40);source.get(91);require(read(source)==3,"prepared native DB resident chain");db.flush();Map<String,byte[]> files=new LinkedHashMap<>();for(String suffix:new String[]{".index",".store",".integrity"})files.put(suffix,Files.readAllBytes(Paths.get(u.getDatabaseDir()+"generation"+suffix)));
  Throwable thrown=null;armed=true;try{db.reindex(null,owner);}catch(Throwable e){thrown=e;}finally{armed=false;}
  require((thrown!=null)==(stop!=0),"native DB outcome");if(thrown!=null)require(thrown.getClass()==InjectedDBCopyFailure.class&&thrown.getMessage().equals("diagnostic DB reindex copy boundary"),"native DB exception preserved");require(copied==(stop==0?3:stop),"exact completed copy count");
  Base published=(Base)db.getBase("tvalues");if(stop!=0){require(published==source&&!source.isClosed()&&read(source)==3,"failed copy never publishes partial target");for(Map.Entry<String,byte[]> e:files.entrySet())require(Arrays.equals(e.getValue(),Files.readAllBytes(Paths.get(u.getDatabaseDir()+"generation"+e.getKey()))),"source core file unchanged "+e.getKey());require(target!=null&&target.isClosed(),"native catch closes temporary Base");}
  else{require(published!=source&&source.isClosed()&&target.isClosed(),"successful DB swaps and closes old Base identities");published.getRoot();published.get(7);published.get(40);published.get(91);require(read(published)==3,"published generation complete after explicit native warm");}
  String row="DB_REINDEX_NATIVE stop="+stop+" exception="+(thrown==null?"none":"InjectedDBCopyFailure")+" copied="+copied+" published="+(published==source?"original":"new")+" source_closed="+source.isClosed()+" target_closed="+target.isClosed()+" authoritative_rows=3";Files.write(Paths.get(System.getProperty("journal.path")+".physical.txt"),Collections.singletonList(row));System.out.println(row);System.out.println("DB_REINDEX_OK checks="+checks);
 }
}
