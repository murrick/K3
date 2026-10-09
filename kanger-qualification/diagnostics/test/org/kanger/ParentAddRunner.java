package org.kanger;
import java.util.*;
import java.nio.file.*;
import org.kanger.units.*;
/** Existing versus newly constructed native memory snapshots after parent additions. */
public final class ParentAddRunner {
 static int checks,controls;
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Context {
  final Mind mind;int waves;final boolean childLocal,grandLocal;
  Context(Mind m,int w,boolean c,boolean g){mind=m;waves=w;childLocal=c;grandLocal=g;}
 }
 static final class Model {
  final AuthorityContextRunner.Fixture fixture;final List<Context> contexts=new ArrayList<>();final List<List<TValue>> additions=new ArrayList<>();
  Model(User u)throws Exception{fixture=new AuthorityContextRunner.Fixture(u);contexts.add(new Context(fixture.minds[0],0,false,false));contexts.add(new Context(fixture.minds[1],0,true,false));contexts.add(new Context(fixture.minds[2],0,true,true));}
  TVariable variable(int i)throws Exception{return fixture.rootValues.get(i*8).getTVar(fixture.minds[0]);}
  void addWave(int round,boolean notify)throws Exception{
   List<TValue> added=new ArrayList<>();for(int i=0;i<4;i++){TValue v=fixture.minds[0].getTValues().add(variable(i),fixture.minds[0].getTerms().add("late-"+round));added.add(v);if(notify){BeforeAuthorityJournal.touch(fixture.minds[0],v,"parent-add-"+round);StreamAuthorityJournal.touch(fixture.minds[0],v,"parent-add-"+round);}}
   additions.add(added);contexts.get(0).waves=round;
  }
  void freshSibling(int waves)throws Exception{contexts.add(new Context(new Mind(fixture.minds[0]),waves,false,false));}
  void freshGrandFromOldChild()throws Exception{contexts.add(new Context(new Mind(fixture.minds[1]),0,true,false));}
  void nativeControls(String reason)throws Exception{
   for(Context c:contexts){
    SortedMap<Long,String> nativeView=new TreeMap<>();
    for(int i=0;i<4;i++){
     List<TValue> expected=new ArrayList<>(fixture.rootValues.subList(i*8,i*8+8));for(int w=0;w<c.waves;w++)expected.add(additions.get(w).get(i));if(c.childLocal)expected.add(fixture.childValues.get(i));if(c.grandLocal)expected.add(fixture.grandValues.get(i));
     List<TValue> actual=new ArrayList<>();c.mind.getTValues().forEach(variable(i),o->{actual.add((TValue)o);return true;});require(actual.size()==expected.size(),"native snapshot width "+reason);for(int j=0;j<actual.size();j++)require(actual.get(j)==expected.get(j),"native snapshot membership/reference/order "+reason);
     for(int w=0;w<additions.size();w++){TValue v=additions.get(w).get(i);require(c.mind.getTValues().get(v.getId())==(w<c.waves?v:null),"native ID lookup respects captured parent chain "+reason);}
     String before=ResidentTValueRead.fingerprint(c.mind);List<TValue> read=ResidentTValueRead.bucket(c.mind,variable(i).getId());require(before.equals(ResidentTValueRead.fingerprint(c.mind)),"qualified bucket read pure");require(read.size()==actual.size(),"qualified/native size");for(int j=0;j<read.size();j++)require(read.get(j)==actual.get(j),"qualified/native canonical order");nativeView.put(variable(i).getId(),ResidentTValueRead.encode(c.mind,actual));controls++;
    }
    String before=ResidentTValueRead.fingerprint(c.mind);require(nativeView.equals(ResidentTValueRead.authority(c.mind))&&nativeView.equals(StreamAuthorityRead.authority(c.mind)),"both full authorities equal actual native enumeration");require(before.equals(ResidentTValueRead.fingerprint(c.mind)),"both authority reads pure");
   }
  }
  void all(String reason)throws Exception{for(Context c:contexts)AuthorityContextRunner.observe(c.mind,reason);}
  void close()throws Exception{for(int i=contexts.size()-1;i>=3;i--){Context c=contexts.get(i);((Mind)c.mind.getNext()).release(c.mind);}fixture.close();}
 }
 public static void main(String[] args)throws Exception{
  int selected=Integer.parseInt(args[0]);User user=(User)UserFactory.createUser("parent-add","parent-add");Model m=new Model(user);m.nativeControls("initial");AuthorityContextRunner.begin();m.all("baseline");
  m.addWave(1,true);m.nativeControls("after-parent-add-1");m.all("parent-add-1");m.freshSibling(1);m.freshGrandFromOldChild();m.nativeControls("new-contexts-after-first-add");AuthorityContextRunner.observe(m.contexts.get(3).mind,"new-sibling-1");AuthorityContextRunner.observe(m.contexts.get(4).mind,"new-grand-from-old-child");m.all("after-native-controls-1");
  m.addWave(2,true);m.nativeControls("after-parent-add-2");m.all("parent-add-2");m.freshSibling(2);m.nativeControls("new-sibling-after-second-add");AuthorityContextRunner.observe(m.contexts.get(5).mind,"new-sibling-2");m.all("after-native-controls-2");
  TValue late=m.additions.get(0).get(selected);AuthorityContextRunner.setter(late,m.fixture.a,true);m.all("late-value-term-rewrite");
  TValue second=m.additions.get(1).get(selected);int[] counts=new int[m.contexts.size()];for(int i=0;i<counts.length;i++)counts[i]=StreamAuthorityJournal.contextChangeCount(m.contexts.get(i).mind);
  TValue duplicate=m.fixture.minds[0].getTValues().add(m.variable(selected),m.fixture.minds[0].getTerms().add("late-2"));require(duplicate==second,"native duplicate add returns same canonical value");BeforeAuthorityJournal.touch(m.fixture.minds[0],duplicate,"duplicate-parent-add");StreamAuthorityJournal.touch(m.fixture.minds[0],duplicate,"duplicate-parent-add");m.nativeControls("duplicate-add");m.all("duplicate-parent-add");for(int i=0;i<counts.length;i++)require(counts[i]==StreamAuthorityJournal.contextChangeCount(m.contexts.get(i).mind),"duplicate does not invent membership or semantic change");
  AuthorityContextRunner.setter(m.fixture.rootValues.get(selected*8),m.fixture.b,true);m.all("existing-inherited-term-rewrite");List<String> old=BeforeAuthorityJournal.finish(),fresh=StreamAuthorityJournal.finish();require(old.equals(fresh),"complete snapshot traces identical");String target=System.getProperty("result.path");Files.write(Paths.get(target+".old.trace"),old);Files.write(Paths.get(target+".memo.trace"),fresh);m.close();
  List<String> errors=new ArrayList<>();
  for(int mode=0;mode<3;mode++){
   Model n=new Model(user);n.nativeControls("negative-initial");AuthorityContextRunner.begin();n.all("negative-baseline");
   if(mode==0)n.addWave(1,false);
   else if(mode==1){n.addWave(1,true);n.all("negative-notified-add");n.freshSibling(1);n.nativeControls("negative-fresh-sibling");AuthorityContextRunner.observe(n.contexts.get(3).mind,"negative-new-sibling");AuthorityContextRunner.setter(n.additions.get(0).get(selected),n.fixture.a,false);}
   else AuthorityContextRunner.setter(n.fixture.rootValues.get(selected*8),n.fixture.b,false);
   n.nativeControls("negative-after-mutation");n.all("missing-event");String a=null,b=null;try{BeforeAuthorityJournal.finish();}catch(AssertionError e){a=e.getMessage();}try{StreamAuthorityJournal.finish();}catch(AssertionError e){b=e.getMessage();}require(a!=null&&a.equals(b),"both journals refuse identical missing-event gap");require(a.split("dirty projection mismatch ctx=",-1).length-1==2*(mode+1),"only snapshots actually containing the changed value refuse");errors.add("mode="+mode+" "+a);n.close();
  }
  Files.write(Paths.get(target+".negative-errors.txt"),errors);System.out.println("PARENT_ADD_OK selected="+selected+" contexts=6 waves=2 negative=3 controls="+controls+" checks="+checks);
 }
}
