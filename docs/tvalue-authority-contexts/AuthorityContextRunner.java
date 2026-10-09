package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.units.*;
/** Native memory ancestry qualification; explicit post-setter diagnostic bridges. */
public final class AuthorityContextRunner {
 static int checks;
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Fixture {
  final Mind[] minds=new Mind[3];final List<TValue> rootValues=new ArrayList<>(),childValues=new ArrayList<>(),grandValues=new ArrayList<>();final Term a,b;
  Fixture(User user)throws Exception{
   minds[0]=new Mind(user);Mind root=minds[0];a=(Term)root.getTerms().add("rewrite-a");b=(Term)root.getTerms().add("rewrite-b");List<TVariable> vars=new ArrayList<>();
   for(int i=0;i<4;i++){Rule rule=new Rule(root);root.getRules().register(rule);TVariable v=root.getTVars().createTVar(rule,root.getTerms().add("v"+i));vars.add(v);for(int j=0;j<8;j++)rootValues.add(root.getTValues().add(v,root.getTerms().add("term-"+j)));}
   minds[1]=new Mind(root);for(TVariable v:vars)childValues.add(minds[1].getTValues().add(v,minds[1].getTerms().add("child-local")));
   minds[2]=new Mind(minds[1]);for(TVariable v:vars)grandValues.add(minds[2].getTValues().add(v,minds[2].getTerms().add("grand-local")));
   for(int depth=0;depth<3;depth++){
    Mind m=minds[depth];require(!m.isStorageUsed(),"memory-only ancestry");
    for(TVariable v:vars){List<TValue> nativeValues=new ArrayList<>();m.getTValues().forEach(v,o->{nativeValues.add((TValue)o);return true;});require(nativeValues.size()==8+depth,"native inherited bucket width");String before=ResidentTValueRead.fingerprint(m);List<TValue> read=ResidentTValueRead.bucket(m,v.getId());require(before.equals(ResidentTValueRead.fingerprint(m)),"fixture reader pure");require(nativeValues.size()==read.size(),"native bucket size");for(int i=0;i<read.size();i++)require(read.get(i)==nativeValues.get(i),"native canonical reference and order");}
    require(ResidentTValueRead.values(m.getTValues()).size()==32+4*depth,"native complete context values");
   }
  }
  void close()throws Exception{minds[1].release(minds[2]);minds[0].release(minds[1]);}
 }
 static void begin(){BeforeAuthorityJournal.begin();StreamAuthorityJournal.begin();}
 static void observe(Mind m,String reason)throws Exception{
  String before=ResidentTValueRead.fingerprint(m);require(ResidentTValueRead.authority(m).equals(StreamAuthorityRead.authority(m)),"independent complete authorities equal");require(before.equals(ResidentTValueRead.fingerprint(m)),"full authorities pure");
  BeforeAuthorityJournal.observe(m,reason);require(before.equals(ResidentTValueRead.fingerprint(m)),"prior journal pure");StreamAuthorityJournal.observe(m,reason);require(before.equals(ResidentTValueRead.fingerprint(m)),"stream journal pure");
 }
 static void all(Fixture f,String reason)throws Exception{for(Mind m:f.minds)observe(m,reason);}
 static void setter(TValue v,Term t,boolean notify){long id=v.getId(),variable=v.getTVarId(),term=v.getValueId();v.setValue(t);require(v.getValueId()==t.getId(),"actual native setter");if(notify){BeforeAuthorityJournal.metadata(v,id,variable,term,"native-term-rewrite");StreamAuthorityJournal.metadata(v,id,variable,term,"native-term-rewrite");}}
 public static void main(String[] args)throws Exception{
  long seed=Long.parseLong(args[0]);Random random=new Random(seed);User user=(User)UserFactory.createUser("context-journal","context-journal");Fixture f=new Fixture(user);begin();all(f,"baseline");
  for(int step=0;step<48;step++){
   int mode=step%6;TValue v=f.rootValues.get(random.nextInt(f.rootValues.size()));
   if(mode==0)setter(v,v.getValueId()==f.a.getId()?f.b:f.a,true);
   else if(mode==1||mode==2||mode==5){Mind m=f.minds[mode==1?1:mode==2?2:0];boolean deleted=!v.isDeleted(m);m.setUnitDeleted(v,deleted);require(v.isDeleted(m)==deleted,"native context visibility toggle");}
   else{List<TValue> local=mode==3?f.childValues:f.grandValues;TValue chosen=local.get(random.nextInt(local.size()));setter(chosen,chosen.getValueId()==f.a.getId()?f.b:f.a,true);}
   if(step==24){BeforeAuthorityJournal.reset(f.minds[1]);StreamAuthorityJournal.reset(f.minds[1]);}
   if(step==36){BeforeAuthorityJournal.reset(f.minds[2]);StreamAuthorityJournal.reset(f.minds[2]);}
   all(f,"step-"+step);
  }
  List<String> old=BeforeAuthorityJournal.finish(),fresh=StreamAuthorityJournal.finish();require(old.equals(fresh),"complete ancestry traces identical");String target=System.getProperty("result.path");Files.write(Paths.get(target+".old.trace"),old);Files.write(Paths.get(target+".memo.trace"),fresh);f.close();
  List<String> errors=new ArrayList<>();
  for(int mode=0;mode<3;mode++){
   Fixture n=new Fixture(user);begin();all(n,"negative-baseline");TValue v=mode==0?n.rootValues.get(0):mode==1?n.childValues.get(0):n.grandValues.get(0);setter(v,n.a,false);all(n,"missing-event");String a=null,b=null;
   try{BeforeAuthorityJournal.finish();}catch(AssertionError e){a=e.getMessage();}try{StreamAuthorityJournal.finish();}catch(AssertionError e){b=e.getMessage();}
   require(a!=null&&a.equals(b)&&a.contains("dirty projection mismatch"),"both journals refuse missing event");int occurrences=a.split("dirty projection mismatch ctx=",-1).length-1;require(occurrences==2*(3-mode),"only owning context and descendants refuse, at observation and finish");errors.add("mode="+mode+" "+a);n.close();
  }
  Files.write(Paths.get(target+".negative-errors.txt"),errors);System.out.println("AUTHORITY_CONTEXTS_OK seed="+seed+" contexts=3 steps=48 negative=3 checks="+checks);
 }
}
