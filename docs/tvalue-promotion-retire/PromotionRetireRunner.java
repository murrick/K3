package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.units.*;
/** Typed native TValueFactory promotion, child clear/release and explicit diagnostic retirement. */
public final class PromotionRetireRunner {
 static int checks,controls;
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static void observe(Mind m,String reason)throws Exception{AuthorityContextRunner.observe(m,reason);}
 static void retire(Mind m){BeforeAuthorityJournal.retire(m);StreamAuthorityJournal.retire(m);}
 static void touch(Mind m,TValue v){BeforeAuthorityJournal.touch(m,v,"child-add");StreamAuthorityJournal.touch(m,v,"child-add");}
 static final class Model {
  final Mind root;final List<TVariable> vars=new ArrayList<>();final List<TValue> base=new ArrayList<>();final List<TValue> promoted=new ArrayList<>();final Term[] terms=new Term[8];
  Model(User u)throws Exception{root=new Mind(u);for(int i=0;i<terms.length;i++)terms[i]=(Term)root.getTerms().add("payload-"+i);for(int i=0;i<4;i++){Rule r=new Rule(root);root.getRules().register(r);TVariable v=root.getTVars().createTVar(r,root.getTerms().add("v"+i));vars.add(v);for(int j=0;j<2;j++)base.add(root.getTValues().add(v,terms[j]));}}
  List<TValue> expected(){List<TValue> result=new ArrayList<>(base);result.addAll(promoted);return result;}
  List<TValue> add(Mind m,int round,boolean notify)throws Exception{List<TValue> out=new ArrayList<>();for(TVariable v:vars){TValue x=m.getTValues().add(v,terms[round+2]);out.add(x);if(notify)touch(m,x);}return out;}
  void control(Mind m,List<TValue> expected,String reason)throws Exception{
   SortedMap<Long,String> view=new TreeMap<>();for(TVariable v:vars){List<TValue> want=new ArrayList<>();for(TValue x:expected)if(x.getTVarId()==v.getId())want.add(x);List<TValue> actual=new ArrayList<>();m.getTValues().forEach(v,o->{actual.add((TValue)o);return true;});require(actual.size()==want.size(),"native membership size "+reason);for(int i=0;i<want.size();i++){require(actual.get(i)==want.get(i),"native canonical order "+reason);require(m.getTValues().get(want.get(i).getId())==want.get(i),"native ID identity "+reason);}String before=ResidentTValueRead.fingerprint(m);List<TValue> bucket=ResidentTValueRead.bucket(m,v.getId());require(before.equals(ResidentTValueRead.fingerprint(m)),"bucket pure");require(bucket.size()==actual.size(),"bucket/native size");for(int i=0;i<bucket.size();i++)require(bucket.get(i)==actual.get(i),"bucket equals native canonical references");if(!actual.isEmpty())view.put(v.getId(),ResidentTValueRead.encode(m,actual));controls++;}
   String before=ResidentTValueRead.fingerprint(m);require(view.equals(ResidentTValueRead.authority(m))&&view.equals(StreamAuthorityRead.authority(m)),"full authorities equal native enumeration "+reason);require(before.equals(ResidentTValueRead.fingerprint(m)),"full authorities pure");
  }
 }
 static String finishOld(){try{BeforeAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 static String finishNew(){try{StreamAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 public static void main(String[] args)throws Exception{
  int selected=Integer.parseInt(args[0]);User u=(User)UserFactory.createUser("promotion-retire","promotion-retire");Model m=new Model(u);Mind sibling=new Mind(m.root);m.control(m.root,m.base,"initial-root");m.control(sibling,m.base,"initial-sibling");AuthorityContextRunner.begin();observe(m.root,"baseline");observe(sibling,"baseline");List<TValue> discarded=new ArrayList<>();
  for(int round=0;round<6;round++){
   Mind child=new Mind(m.root);List<TValue> before=m.expected();m.control(child,before,"child-baseline");observe(child,"child-baseline-"+round);List<TValue> local=m.add(child,round,true);List<TValue> childExpected=new ArrayList<>(before);childExpected.addAll(local);m.control(child,childExpected,"child-added");observe(child,"child-added-"+round);observe(m.root,"parent-before-"+round);observe(sibling,"sibling-before-"+round);
   if(round%2==0){
    m.root.getTValues().commit(child.getTValues());for(TValue v:local)require(v.getMindId()==m.root.getId(),"native promotion changes owner ID");BeforeAuthorityJournal.promoted(m.root,child.getTValues());StreamAuthorityJournal.promoted(m.root,child.getTValues());m.promoted.addAll(local);
    m.control(m.root,m.expected(),"promoted-parent");m.control(sibling,m.base,"old-sibling-after-promotion");observe(m.root,"promoted-"+round);observe(sibling,"promoted-"+round);observe(child,"promoted-"+round);
   }else{
    child.getTValues().clear();for(TValue v:local)require(child.getTValues().get(v.getId())==null&&m.root.getTValues().get(v.getId())==null,"native discard removes child additions only");discarded.addAll(local);
    for(TValue v:local)touch(child,v);m.control(child,before,"cleared-child");observe(child,"cleared-"+round);observe(m.root,"discarded-"+round);observe(sibling,"discarded-"+round);
   }
   m.root.release(child);retire(child);
   // After native settlement the detached child factory can be cleared. No retired observer may inspect it.
   child.getTValues().clear();BeforeAuthorityJournal.reset(child);StreamAuthorityJournal.reset(child);BeforeAuthorityJournal.touch(child,local.get(selected),"retired-touch");StreamAuthorityJournal.touch(child,local.get(selected),"retired-touch");BeforeAuthorityJournal.observe(child,"after-retirement");StreamAuthorityJournal.observe(child,"after-retirement");
   m.control(m.root,m.expected(),"settled-parent");m.control(sibling,m.base,"settled-sibling");observe(m.root,"settled-"+round);observe(sibling,"settled-"+round);
   Mind fresh=new Mind(m.root);m.control(fresh,m.expected(),"fresh-child");observe(fresh,"fresh-"+round);m.root.release(fresh);retire(fresh);
  }
  for(TValue v:discarded)require(m.root.getTValues().get(v.getId())==null,"discarded ID never promoted later");
  AuthorityContextRunner.setter(m.promoted.get(selected),m.terms[7],true);m.control(m.root,m.expected(),"promoted-payload-rewrite");m.control(sibling,m.base,"sibling-excludes-promoted-payload");observe(m.root,"promoted-payload-rewrite");observe(sibling,"promoted-payload-rewrite");List<TValue> packed=m.expected();require(m.root.pendingTransactionCount()==1,"only sibling reservation remains");m.root.release(sibling);retire(sibling);require(m.root.pendingTransactionCount()==0,"native root quiescent");for(TValue v:packed){BeforeAuthorityJournal.touch(m.root,v,"root-pack");StreamAuthorityJournal.touch(m.root,v,"root-pack");require(m.root.getTValues().get(v.getId())==null,"native final pack removes unsupported value");}m.control(m.root,Collections.emptyList(),"root-packed");observe(m.root,"only-parent-live");
  List<String> old=BeforeAuthorityJournal.finish(),fresh=StreamAuthorityJournal.finish();require(old.equals(fresh),"complete lifecycle traces equal");String target=System.getProperty("result.path");Files.write(Paths.get(target+".old.trace"),old);Files.write(Paths.get(target+".memo.trace"),fresh);
  List<String> errors=new ArrayList<>();for(int mode=0;mode<3;mode++){
   Model n=new Model(u);Mind guard=new Mind(n.root);Mind child=new Mind(n.root);AuthorityContextRunner.begin();observe(n.root,"negative-parent");observe(child,"negative-child");
   if(mode==0){n.add(child,0,true);observe(child,"negative-child-added");n.root.getTValues().commit(child.getTValues());observe(n.root,"missing-promotion");n.root.release(child);retire(child);}
   else if(mode==1){BeforeAuthorityJournal.mark(child);StreamAuthorityJournal.mark(child);retire(child);BeforeAuthorityJournal.complete(child);StreamAuthorityJournal.complete(child);n.root.release(child);retire(child);}
   else{BeforeAuthorityJournal.beginSettlement(child);StreamAuthorityJournal.beginSettlement(child);retire(child);BeforeAuthorityJournal.endSettlement(child);StreamAuthorityJournal.endSettlement(child);n.root.release(child);retire(child);}
   String a=finishOld(),b=finishNew();require(a!=null&&a.equals(b),"both journals refuse same lifecycle gap");require(a.contains(mode==0?"dirty projection mismatch ctx=1":"retired unfinished context"),"expected lifecycle failure");errors.add("mode="+mode+" "+a);n.root.release(guard);require(n.root.pendingTransactionCount()==0,"negative fixture releases all reservations");
  }
  Files.write(Paths.get(target+".negative-errors.txt"),errors);System.out.println("PROMOTION_RETIRE_OK selected="+selected+" rounds=6 promotions=3 discards=3 negative=3 controls="+controls+" checks="+checks);
 }
}
