package org.kanger;
import java.util.*;import java.nio.file.*;import org.kanger.units.*;
/** Characterizes reused identity; disagreement is evidence, never silently accepted qualification. */
public final class ConsumerRunner {
 static final boolean HOOKED=Boolean.getBoolean("native.hooks");static int checks,controls;
 static final List<String> rows=new ArrayList<>(),diagnostics=new ArrayList<>();
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static final class Model {
  Mind root,child,grand;List<TVariable> vars=new ArrayList<>();List<TValue> base=new ArrayList<>();TValue local,deep;Term replacement;
  Model(String name)throws Exception{
   User u=(User)UserFactory.createUser(name,name);root=new Mind(u);for(String t:new String[]{"a","b","local","deep","replacement"})require(root.compileLine("!keep("+t+");",false,null)!=null,"support rule");replacement=(Term)root.getTerms().add("replacement");
   for(int i=0;i<3;i++){Rule r=new Rule(root);root.getRules().register(r);vars.add(root.getTVars().createTVar(r,root.getTerms().add("v"+i)));if(i<2)for(String t:new String[]{"a","b"})base.add(root.getTValues().add(vars.get(i),root.getTerms().add(t)));}
   child=new Mind(root);local=child.getTValues().add(vars.get(1),child.getTerms().add("local"));grand=new Mind(child);deep=grand.getTValues().add(vars.get(1),grand.getTerms().add("deep"));
  }
  String token(TValue v,TValue added){if(v==null)return "null";String identity=v==added?"new":v==local?"local":v==deep?"deep":"old";return identity+":"+v.getId()+":"+v.getTVarId()+":"+v.getValueId();}
  SortedMap<Long,String> snapshot(Mind m,String stage,TValue added)throws Exception{
   SortedMap<Long,String> nativeView=new TreeMap<>();List<String> refs=new ArrayList<>();for(TVariable var:vars){List<TValue> actual=new ArrayList<>();m.getTValues().forEach(var,o->{actual.add((TValue)o);return true;});String before=ResidentTValueRead.fingerprint(m);List<TValue> pure=ResidentTValueRead.bucket(m,var.getId());require(actual.size()==pure.size(),"native bucket size");for(int i=0;i<actual.size();i++){require(actual.get(i)==pure.get(i),"native bucket reference and order");refs.add(var.getId()+"="+token(actual.get(i),added));}require(before.equals(ResidentTValueRead.fingerprint(m)),"bucket pure");if(!actual.isEmpty())nativeView.put(var.getId(),ResidentTValueRead.encode(m,actual));controls++;}
   String before=ResidentTValueRead.fingerprint(m);SortedMap<Long,String> a=ResidentTValueRead.authority(m),b=StreamAuthorityRead.authority(m);require(a.equals(b),"old stream full authority agreement");require(before.equals(ResidentTValueRead.fingerprint(m)),"full authority pure");rows.add("VIEW stage="+stage+" level="+m.getTransactionLevel()+" native="+nativeView+" full="+a+" refs="+refs+" fullMatchesNative="+a.equals(nativeView));if(HOOKED)AuthorityContextRunner.observe(m,stage);return nativeView;
  }
  void all(String stage,TValue added)throws Exception{snapshot(root,stage,added);snapshot(child,stage,added);snapshot(grand,stage,added);}
  void close()throws Exception{child.release(grand);root.release(child);require(root.pendingTransactionCount()==0,"reservation cleanup");}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  int rep=Integer.parseInt(args[0]);String target=System.getProperty("result.path");int refused=0,accepted=0;
  for(String boundary:new String[]{"root-clear","child-clear","pack"})for(int offset=0;offset<3;offset++){
   int variable=(offset+rep)%3;String label=boundary+"-v"+variable;if(HOOKED)QualifiedJournalConsumer.begin();Model m=new Model("recycled-"+label);m.all(label+"-baseline",null);Mind owner=boundary.equals("child-clear")?m.child:m.root;
   if(boundary.equals("pack")){m.base.get(0).setDeleted(true,m.root);m.root.getTValues().pack();}else owner.getTValues().clear();m.all(label+"-cleared",null);
   TValue added=owner.getTValues().add(m.vars.get(variable),m.replacement);require(added!=m.base.get(0),"replacement distinct object");require(owner.getTValues().get(added.getId())==added,"owner canonical replacement");
   if(boundary.equals("root-clear")){require(added.getId()==m.base.get(0).getId(),"root clear recycles ID");require(m.child.getTValues().get(added.getId())==m.base.get(0)&&m.grand.getTValues().get(added.getId())==m.base.get(0),"old descendants resolve reused ID to captured object");}
   else require(added.getId()>m.deep.getId(),"non-clear-root control keeps monotonic IDs");
   rows.add("ID case="+label+" owner="+m.token(owner.getTValues().get(added.getId()),added)+" child="+m.token(m.child.getTValues().get(added.getId()),added)+" grand="+m.token(m.grand.getTValues().get(added.getId()),added));m.all(label+"-replacement",added);
   Mind fresh=new Mind(owner);m.snapshot(fresh,label+"-fresh",added);require(fresh.getTValues().get(added.getId())==added,"fresh context resolves new canonical identity");owner.release(fresh);
   if(HOOKED){boolean denied=false;try{List<String> trace=QualifiedJournalConsumer.finish();require(!boundary.equals("root-clear"),"recycled trace must never be returned");Files.write(Paths.get(target+"."+label+".accepted.trace"),trace);accepted++;try{trace.add("tamper");throw new AssertionError("mutable consumer output");}catch(UnsupportedOperationException expected){checks++;}diagnostics.add("ACCEPT case="+label+" rows="+trace.size());}catch(QualifiedJournalConsumer.Refusal e){denied=true;require(boundary.equals("root-clear")&&e.code.equals("RECYCLED_ID"),"exact consumer refusal");require(e.aliasCount==2,"both old descendants recorded");require(e.journalRejected==(variable==2),"independent underlying oracle verdict retained");refused++;diagnostics.add("REFUSE case="+label+" code="+e.code+" aliases="+e.aliasCount+" journalRejected="+e.journalRejected+" outputRows=0");}require(denied==boundary.equals("root-clear"),"supported consumer boundary");}
   m.close();
  }
  Files.write(Paths.get(target+".native-rows.txt"),rows);
  if(HOOKED){
   // A missing callback must still refuse through the unchanged full comparison.
   QualifiedJournalConsumer.begin();Model n=new Model("consumer-missing");n.all("missing-baseline",null);ConsumerHooks.suppressMetadata=true;n.base.get(0).setValue(n.replacement);ConsumerHooks.suppressMetadata=false;n.all("missing-metadata",null);expectRefusal("FULL_AUTHORITY_MISMATCH");n.close();
   // Adapter failures cannot be mistaken for qualified views even when journals agree.
   QualifiedJournalConsumer.begin();Model e=new Model("consumer-adapter");e.all("adapter-baseline",null);ConsumerHooks.errors.add("injected unsupported diagnostic capture");expectRefusal("ADAPTER_FAILURE");e.close();
   // A fresh session after any refusal must recover without carrying taint.
   QualifiedJournalConsumer.begin();Model fresh=new Model("consumer-recovery");fresh.all("recovery-baseline",null);List<String> trace=QualifiedJournalConsumer.finish();Files.write(Paths.get(target+".recovery.accepted.trace"),trace);fresh.close();require(!trace.isEmpty(),"fresh session recovers");
   // Finishing outside a session cannot silently return an empty qualified result.
   expectRefusal("NO_SESSION");Files.write(Paths.get(target+".diagnostics.txt"),diagnostics);
  }
  System.out.println("CONSUMER_GATE_OK repetition="+rep+" hooked="+HOOKED+" cases=9 controls="+controls+" accepted="+accepted+" refused="+refused+" checks="+checks);
 }
 static void expectRefusal(String code){try{QualifiedJournalConsumer.finish();throw new AssertionError("consumer unexpectedly returned "+code);}catch(QualifiedJournalConsumer.Refusal e){require(e.code.equals(code),"exact negative consumer code");diagnostics.add("NEGATIVE code="+code+" outputRows=0");}}
}
