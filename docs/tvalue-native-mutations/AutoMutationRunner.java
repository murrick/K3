package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.units.*;
/** Native ordinary mutations and live descendant routing; no manual mutation notifications. */
public final class AutoMutationRunner {
 static final boolean HOOKED=Boolean.getBoolean("native.hooks");static int checks,controls;static final List<String> nativeRows=new ArrayList<>();
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static void observe(Mind m,String reason)throws Exception{if(HOOKED)AuthorityContextRunner.observe(m,reason);}
 static final class Model {
  final Mind root,child,grand;final List<TVariable> vars=new ArrayList<>();final List<TValue> base=new ArrayList<>(),childLocal=new ArrayList<>(),grandLocal=new ArrayList<>();final Term rewrite,newTerm,temp;
  Model(User u)throws Exception{
   root=new Mind(u);for(String name:new String[]{"t0","t1","t2","rewrite","newterm","temporary"})require(root.compileLine("!keep("+name+");",false,null)!=null,"native supporting rule "+name);rewrite=(Term)root.getTerms().add("rewrite");newTerm=(Term)root.getTerms().add("newterm");temp=(Term)root.getTerms().add("temporary");
   for(int i=0;i<4;i++){Rule r=new Rule(root);root.getRules().register(r);TVariable v=root.getTVars().createTVar(r,root.getTerms().add("v"+i));vars.add(v);for(int j=0;j<3;j++)base.add(root.getTValues().add(v,root.getTerms().add("t"+j)));}
   child=new Mind(root);for(TVariable v:vars)childLocal.add(child.getTValues().add(v,child.getTerms().add("child-local")));grand=new Mind(child);for(TVariable v:vars)grandLocal.add(grand.getTValues().add(v,grand.getTerms().add("grand-local")));
  }
  List<TValue> rootExpected(){return new ArrayList<>(base);}
  List<TValue> childExpected(){List<TValue> r=rootExpected();r.addAll(childLocal);return r;}
  List<TValue> grandExpected(){List<TValue> r=childExpected();r.addAll(grandLocal);return r;}
  void control(Mind mind,List<TValue> expected,String reason)throws Exception{
   SortedMap<Long,String> view=new TreeMap<>();for(TVariable variable:vars){List<TValue> want=new ArrayList<>();for(TValue v:expected)if(v.getTVarId()==variable.getId())want.add(v);List<TValue> actual=new ArrayList<>();mind.getTValues().forEach(variable,o->{actual.add((TValue)o);return true;});require(actual.size()==want.size(),"native enumeration width "+reason+" level="+mind.getTransactionLevel()+" actual="+actual+" wanted="+want);for(int i=0;i<want.size();i++){require(actual.get(i)==want.get(i),"native reference/order "+reason);require(mind.getTValues().get(want.get(i).getId())==want.get(i),"native ID canonical "+reason);}String before=ResidentTValueRead.fingerprint(mind);List<TValue> read=ResidentTValueRead.bucket(mind,variable.getId());require(read.size()==actual.size(),"pure reader size");for(int i=0;i<read.size();i++)require(read.get(i)==actual.get(i),"pure reader references");require(before.equals(ResidentTValueRead.fingerprint(mind)),"bucket pure");if(!actual.isEmpty())view.put(variable.getId(),ResidentTValueRead.encode(mind,actual));controls++;}
   String before=ResidentTValueRead.fingerprint(mind);require(view.equals(ResidentTValueRead.authority(mind))&&view.equals(StreamAuthorityRead.authority(mind)),"both authorities equal actual native enumeration");require(before.equals(ResidentTValueRead.fingerprint(mind)),"authorities pure");nativeRows.add("NATIVE_VIEW reason="+reason+" level="+mind.getTransactionLevel()+" view="+view);observe(mind,reason);
  }
  void all(String reason)throws Exception{control(root,rootExpected(),reason);control(child,childExpected(),reason);control(grand,grandExpected(),reason);}
  void close()throws Exception{child.release(grand);root.release(child);require(root.pendingTransactionCount()==0&&child.pendingTransactionCount()==0,"all native reservations closed");}
 }
 static int metadataCount(){return MutationJournalHooks.counts.getOrDefault("metadata",0);}
 static String finishOld(List<String>[] out){try{out[0]=BeforeAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 static String finishNew(List<String>[] out){try{out[1]=StreamAuthorityJournal.finish();return null;}catch(AssertionError e){return e.getMessage();}}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  int selected=Integer.parseInt(args[0]);User user=(User)UserFactory.createUser("auto-mutations","auto-mutations");if(HOOKED)MutationJournalHooks.begin();Model m=new Model(user);m.all("baseline");TValue rewritten=m.base.get(selected*3);rewritten.setValue(m.rewrite);m.all("term-rewrite");rewritten.setValue(m.rewrite);m.all("duplicate-term-rewrite");
  int count=metadataCount();long termId=rewritten.getValueId();boolean failed=false;try{rewritten.setValue(null);}catch(NullPointerException e){failed=true;}require(failed&&rewritten.getValueId()==termId&&ResidentTValueRead.field(rewritten,"value")==null,"native setter partial failure preserved");if(HOOKED)require(metadataCount()==count+1,"setter finally callback on native failure");m.all("partial-setter-failure");rewritten.setValue(m.rewrite);m.all("partial-setter-restored");
  TValue visibility=m.base.get(selected*3+1);visibility.setDeleted(true,m.child);require(!visibility.isDeleted(m.root)&&visibility.isDeleted(m.child)&&visibility.isDeleted(m.grand),"child deletion inherited without parent deletion");m.all("child-delete");visibility.setDeleted(true,m.child);m.all("duplicate-child-delete");TValue resurrected=m.child.getTValues().add(m.vars.get(selected),m.child.getTerms().add("t1"));require(resurrected==visibility&&!visibility.isDeleted(m.child)&&!visibility.isDeleted(m.grand),"native canonical resurrection");m.all("child-resurrect");
  TValue removed=m.base.get(selected*3+2);removed.setDeleted(true,m.root);m.all("root-delete-before-pack");m.root.getTValues().pack();m.base.remove(removed);require(m.root.getTValues().get(removed.getId())==null,"native root pack removes ID");require(m.child.getTValues().get(removed.getId())==removed&&m.grand.getTValues().get(removed.getId())==removed,"existing descendant ID caches retain captured canonical object");m.all("root-pack");
  m.child.getTValues().clear();m.childLocal.clear();m.all("child-clear");m.root.getTValues().clear();m.base.clear();m.all("root-clear");Mind fresh=new Mind(m.root);m.control(fresh,Collections.emptyList(),"fresh-after-clear");m.root.release(fresh);m.close();m.control(m.root,Collections.emptyList(),"settled-empty-parent");List<TValue> newValues=new ArrayList<>();for(TVariable v:m.vars)newValues.add(m.root.getTValues().add(v,m.newTerm));m.base.addAll(newValues);m.control(m.root,m.rootExpected(),"new-root-add-after-retirement");m.root.getTValues().mark();TValue provisional=m.root.getTValues().add(m.vars.get(selected),m.temp);observe(m.root,"provisional-checkpoint");m.root.getTValues().release();require(m.root.getTValues().get(provisional.getId())==null,"native provisional add rolled back");m.control(m.root,m.rootExpected(),"checkpoint-rollback");m.control(m.root,m.rootExpected(),"settled-parent");
  String target=System.getProperty("result.path");Files.write(Paths.get(target+".native-rows.txt"),nativeRows);int positiveControls=controls;
  if(HOOKED){List<String>[] pair=(List<String>[])new List<?>[2];String a=finishOld(pair),b=finishNew(pair);MutationJournalHooks.stop();if(a!=null||b!=null)Files.write(Paths.get(target+".positive-errors.txt"),Arrays.asList(String.valueOf(a),String.valueOf(b)));require(a==null&&b==null,"positive automatic mutation journal gaps: "+a+" / "+b);require(pair[0].equals(pair[1]),"full mutation traces equal");Files.write(Paths.get(target+".old.trace"),pair[0]);Files.write(Paths.get(target+".memo.trace"),pair[1]);require(MutationJournalHooks.errors.isEmpty(),"adapter errors absent");}
  if(HOOKED){List<String> errors=new ArrayList<>();for(int mode=0;mode<3;mode++){
   Model n=new Model(user);MutationJournalHooks.begin();n.all("negative-baseline");
   if(mode==0){MutationJournalHooks.suppressMetadata=true;n.base.get(selected*3).setValue(n.rewrite);MutationJournalHooks.suppressMetadata=false;}
   else if(mode==1){TValue v=n.base.get(selected*3+2);v.setDeleted(true,n.root);n.all("negative-before-pack");MutationJournalHooks.suppressRemove=true;n.root.getTValues().pack();MutationJournalHooks.suppressRemove=false;n.base.remove(v);}
   else{MutationJournalHooks.suppressResetFanout=true;n.child.getTValues().clear();MutationJournalHooks.suppressResetFanout=false;n.childLocal.clear();}
   n.all("missing-native-event");List<String>[] pair=(List<String>[])new List<?>[2];String a=finishOld(pair),b=finishNew(pair);MutationJournalHooks.stop();require(a!=null&&a.equals(b),"both journals detect exact native-hook gap");require(a.contains("dirty projection mismatch"),"native-hook gap refused");errors.add("mode="+mode+" "+a);n.close();
  }Files.write(Paths.get(target+".negative-errors.txt"),errors);Files.write(Paths.get(target+".hook-counts.txt"),Collections.singletonList(MutationJournalHooks.counts.toString()));}
  System.out.println("AUTO_MUTATIONS_OK selected="+selected+" hooked="+HOOKED+" positiveControls="+positiveControls+" totalControls="+controls+" checks="+checks);
 }
}
