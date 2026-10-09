package org.kanger;
import java.util.*;import java.nio.file.*;import org.kanger.units.*;
/** Characterizes reused identity; disagreement is evidence, never silently accepted qualification. */
public final class RecycledIdRunner {
 static final boolean HOOKED=Boolean.getBoolean("native.hooks"),GUARD=Boolean.getBoolean("recycled.guard");static int checks,controls;
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
 static String oldFinish(List<String>[] pair){try{pair[0]=BeforeAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 static String newFinish(List<String>[] pair){try{pair[1]=StreamAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  int rep=Integer.parseInt(args[0]);String target=System.getProperty("result.path");int aliases=0,blind=0,journalRejected=0;
  for(String boundary:new String[]{"root-clear","child-clear","pack"})for(int offset=0;offset<3;offset++){
   int variable=(offset+rep)%3;String label=boundary+"-v"+variable;if(HOOKED)RecycledIdHooks.begin();Model m=new Model("recycled-"+label);m.all(label+"-baseline",null);Mind owner=boundary.equals("child-clear")?m.child:m.root;
   if(boundary.equals("pack")){m.base.get(0).setDeleted(true,m.root);m.root.getTValues().pack();}else owner.getTValues().clear();m.all(label+"-cleared",null);
   TValue added=owner.getTValues().add(m.vars.get(variable),m.replacement);require(added!=m.base.get(0),"replacement distinct object");require(owner.getTValues().get(added.getId())==added,"owner canonical replacement");
   if(boundary.equals("root-clear")){require(added.getId()==m.base.get(0).getId(),"root clear recycles ID");require(m.child.getTValues().get(added.getId())==m.base.get(0)&&m.grand.getTValues().get(added.getId())==m.base.get(0),"old descendants resolve reused ID to captured object");}
   else require(added.getId()>m.deep.getId(),"non-clear-root control keeps monotonic IDs");
   rows.add("ID case="+label+" owner="+m.token(owner.getTValues().get(added.getId()),added)+" child="+m.token(m.child.getTValues().get(added.getId()),added)+" grand="+m.token(m.grand.getTValues().get(added.getId()),added));m.all(label+"-replacement",added);
   Mind fresh=new Mind(owner);m.snapshot(fresh,label+"-fresh",added);require(fresh.getTValues().get(added.getId())==added,"fresh context resolves new canonical identity");owner.release(fresh);
   if(HOOKED){List<String>[] pair=(List<String>[])new List<?>[2];String a=oldFinish(pair),b=newFinish(pair);require(Objects.equals(a,b),"journal finish verdicts equal");if(a==null){require(pair[0].equals(pair[1]),"journal traces equal");for(int i=0;i<2;i++)Files.write(Paths.get(target+"."+label+"."+(i==0?"old":"memo")+".trace"),pair[i]);}else journalRejected++;
    int wanted=GUARD&&boundary.equals("root-clear")?2:0;require(RecycledIdHooks.aliases.size()==wanted,"guard identifies exactly two old descendants");require(RecycledIdHooks.errors.isEmpty(),"guard capture pure and supported");aliases+=wanted;diagnostics.add("CASE "+label+" aliases="+RecycledIdHooks.aliases+" journal="+(a==null?"accepted":a)+" gate="+(wanted==0?"ordinary": "refused-recycled-identity"));
    if(boundary.equals("root-clear")&&variable==2&&a==null)blind++;RecycledIdHooks.stop();
   }m.close();
  }
  Files.write(Paths.get(target+".native-rows.txt"),rows);if(HOOKED)Files.write(Paths.get(target+".diagnostics.txt"),diagnostics);System.out.println("RECYCLED_ID_OK repetition="+rep+" hooked="+HOOKED+" guard="+GUARD+" cases=9 controls="+controls+" aliasRefusals="+aliases+" journalRejected="+journalRejected+" fullOracleBlindCases="+blind+" checks="+checks);
 }
}
