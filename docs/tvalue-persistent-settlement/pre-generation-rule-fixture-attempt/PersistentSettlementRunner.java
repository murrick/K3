package org.kanger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.*;
/** Actual composite Mind settlement over a resident DUMB root. */
public final class PersistentSettlementRunner {
 static int checks;static boolean shadow=Boolean.getBoolean("persistence.shadow");
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static class InjectedFailure extends RuntimeException {InjectedFailure(){super("persistent settlement analyzer failure");}}
 static class FailingAnalyzer extends Analyzer {FailingAnalyzer(Mind m){super(m);}public boolean checkDatabase(Set<Long> rules,boolean logging){throw new InjectedFailure();}}
 static void write(String suffix,List<String> rows)throws Exception{Files.write(Paths.get(System.getProperty("journal.path")+suffix),rows,StandardCharsets.UTF_8);}
 static void observe(Mind m,String why)throws Exception{String before=ResidentTValueRead.fingerprint(m);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(before.equals(ResidentTValueRead.fingerprint(m)),"pure boundary "+why);}
 static List<Long> ids(Mind m,TVariable v)throws Exception{List<Long> rows=new ArrayList<>();m.getTValues().forEach(v,o->{rows.add(((TValue)o).getId());return true;});return rows;}
 public static void main(String[] args)throws Exception {
  String mode=args[0];require(Arrays.asList("accept","reject","user-reject","exception").contains(mode),"mode");
  User user=(User)UserFactory.createUser("persistent-settlement","persistent-settlement");user.setProperty("cache.data.size","0");new DB().init(user);Mind parent=new Mind(user);parent=(Mind)parent.useStorage("settlement");parent.getTValues().transaction(null);Base base=(Base)user.getStorage("tvalues");
  require(parent.compileLine("!keep(b);",false,null)!=null,"retain b");Rule owner=(Rule)parent.compileLine("!@x settlement_owner(x);",false,null);require(owner!=null,"native variable owner");TVariable v=owner.getTree().get(0).get(0).getArguments().getTVariables(parent).iterator().next();TValue a=parent.getTValues().add(v,parent.getTerms().add("a"));long aId=a.getId();parent.getTValues().update();base.flush();base.getRoot();parent.getTValues().forEach(v,o->true);
  Mind held=new Mind(parent),child=new Mind(parent);if(mode.equals("accept"))require(child.compileLine("!keep(a);",false,null)!=null,"accepted child retains a");TValue b=child.getTValues().add(v,child.getTerms().add("b"));long bId=b.getId();
  if(!mode.equals("accept")){
   if(mode.contains("reject")){Rule bad=(Rule)child.compileLine("!~keep(a);",false,null);require(bad!=null,"contradictory child operand");bad.setStored(child);}
   Mind sibling=new Mind(parent);Rule marker=(Rule)sibling.compileLine("!sibling_marker;",false,null);require(marker!=null,"sibling native rule");marker.setStored(sibling);Rule fact=(Rule)sibling.compileLine("!keep(a);",false,null);require(fact!=null,"sibling native stored fact");fact.setStored(sibling);require(parent.commit(sibling),"sibling committed");require(!parent.getRules().isSequencedBy(child.getRules()),"actual non-sequenced path");
  }
  if(shadow)TValueDirtyJournal.begin();observe(parent,"persistent-parent-baseline");observe(child,"child-prepared");require(ids(parent,v).equals(Collections.singletonList(aId)),"prepared child invisible to parent");
  int changes=shadow?TValueDirtyJournal.contextChangeCount(parent):0;
  Object originalAnalyzer=ResidentTValueRead.field(parent,"analyzer");if(mode.equals("exception")){Field field=Mind.class.getDeclaredField("analyzer");field.setAccessible(true);field.set(parent,new FailingAnalyzer(parent));}
  boolean accepted=false,failure=false;try{accepted=mode.equals("user-reject")?parent.commitUserTransaction(child):parent.commit(child);}catch(InjectedFailure e){failure=true;require(e.getMessage().equals("persistent settlement analyzer failure"),"original analyzer exception preserved");}
  if(mode.equals("exception")){Field field=Mind.class.getDeclaredField("analyzer");field.setAccessible(true);field.set(parent,originalAnalyzer);}
  require(accepted==mode.equals("accept")&&failure==mode.equals("exception"),"original native outcome");observe(parent,"after-composite-settlement");require(ids(parent,v).equals(mode.equals("accept")?Arrays.asList(aId,bId):Collections.singletonList(aId)),"native canonical effects after settlement");
  if(shadow)require(TValueDirtyJournal.contextChangeCount(parent)==changes+(mode.equals("accept")?1:0),"no false parent delta after rollback");
  if(mode.equals("user-reject")){require(parent.pendingTransactionCount()==2,"user rejection retains child and held reservations");observe(child,"live-user-rejected-child");parent.release(child);}
  require(parent.pendingTransactionCount()==1,"only held reservation remains");
  if(shadow)write(".settlement.trace",TValueDirtyJournal.finish());
  // A separate session makes quiescent finalization an explicit boundary.
  if(shadow)TValueDirtyJournal.begin();observe(parent,"before-quiescent-release");parent.release(held);require(parent.pendingTransactionCount()==0,"quiescent native finalization consumed reservation");
  boolean finalRejected=false;String finalError="";
  if(shadow){try{write(".finalization.trace",TValueDirtyJournal.finish());}catch(AssertionError e){finalError=e.getMessage();finalRejected=finalError.contains("unresolved endpoints");write(".finalization-error.txt",Collections.singletonList(finalError));}require(finalRejected==mode.equals("accept"),"quiescent qualification outcome "+finalError);}
  // Explicit native endpoint resolution followed by a distinct fresh session.
  base.getRoot();parent.getTValues().forEach(v,o->true);require(ids(parent,v).equals(mode.equals("accept")?Arrays.asList(aId,bId):Collections.singletonList(aId)),"final native durable values retained");
  if(shadow){TValueDirtyJournal.begin();observe(parent,"fresh-after-native-finalization");write(".fresh.trace",TValueDirtyJournal.finish());}
  System.out.println("PERSISTENT_SETTLEMENT_NATIVE mode="+mode+" accepted="+accepted+" analyzer_failure="+failure+" surviving_values="+(mode.equals("accept")?2:1)+" reservations=0");
  System.out.println("PERSISTENT_SETTLEMENT_OK checks="+checks+" shadow="+shadow+" finalization_rejected="+finalRejected);
 }
}
