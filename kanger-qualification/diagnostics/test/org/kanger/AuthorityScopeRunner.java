package org.kanger;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;
import org.kanger.units.*;
/** Reporter equivalence plus real-memory transitions with explicit bridges. */
public final class AuthorityScopeRunner {
 static int checks;static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static TVariable variable(Mind m,String name)throws Exception{Rule r=new Rule(m);m.getRules().register(r);return m.getTVars().createTVar(r,m.getTerms().add(name));}
 static void begin(){BeforeAuthorityJournal.begin();StreamAuthorityJournal.begin();}
 static void touch(Mind m,TValue value){BeforeAuthorityJournal.touch(m,value,"scope-native-change");StreamAuthorityJournal.touch(m,value,"scope-native-change");}
 static void observe(Mind m,String why)throws Exception{String before=ResidentTValueRead.fingerprint(m);BeforeAuthorityJournal.observe(m,why);require(before.equals(ResidentTValueRead.fingerprint(m)),"before report pure");StreamAuthorityJournal.observe(m,why);require(before.equals(ResidentTValueRead.fingerprint(m)),"stream report pure");}
 static void set(TValue v,Term t){long id=v.getId(),variable=v.getTVarId(),old=v.getValueId();v.setValue(t);BeforeAuthorityJournal.metadata(v,id,variable,old,"setValue");StreamAuthorityJournal.metadata(v,id,variable,old,"setValue");}
 static void maps()throws Exception{
  Method oldEncode=BeforeAuthorityJournal.class.getDeclaredMethod("encode",SortedMap.class),newEncode=StreamAuthorityJournal.class.getDeclaredMethod("encode",SortedMap.class),keys=StreamAuthorityJournal.class.getDeclaredMethod("changedKeys",SortedMap.class,SortedMap.class);oldEncode.setAccessible(true);newEncode.setAccessible(true);keys.setAccessible(true);Random random=new Random(0x5eed);
  for(int i=0;i<1028;i++){
   SortedMap<Long,String> a=new TreeMap<>(),b=new TreeMap<>();
   if(i>=4)for(int j=0;j<34;j++){long id=j==0?Long.MIN_VALUE:j==33?Long.MAX_VALUE:j-17;String x=j%3==0?"[]":"["+j+":2:0]",y=j%2==0?x:"["+j+":3:1]";if(random.nextBoolean())a.put(id,x);if(random.nextBoolean())b.put(id,y);}
   else{if(i==1||i==3)a.put(1L,"[1:2:0]");if(i==2||i==3)b.put(1L,"[1:3:0]");}
   SortedMap<Long,String> aCopy=new TreeMap<>(a),bCopy=new TreeMap<>(b);SortedSet<Long> union=new TreeSet<>(a.keySet());union.addAll(b.keySet());List<Long> expected=new ArrayList<>();for(long id:union)if(!a.getOrDefault(id,"[]").equals(b.getOrDefault(id,"[]")))expected.add(id);
   require(keys.invoke(null,a,b).equals(expected),"same sorted changes, including removed/added keys");require(oldEncode.invoke(null,a).equals(newEncode.invoke(null,a))&&oldEncode.invoke(null,b).equals(newEncode.invoke(null,b)),"same complete map encoding");require(a.equals(aCopy)&&b.equals(bCopy),"reporters do not mutate maps");
  }
 }
 static void authorityPair(Mind m,String why)throws Exception{
  String before=ResidentTValueRead.fingerprint(m);SortedMap<Long,String> old=ResidentTValueRead.authority(m);
  require(before.equals(ResidentTValueRead.fingerprint(m)),"legacy authority pure "+why);
  SortedMap<Long,String> fresh=StreamAuthorityRead.authority(m);require(old.equals(fresh),"full authority equivalent "+why);
  require(before.equals(ResidentTValueRead.fingerprint(m)),"stream authority pure "+why);
 }
 static void authorityControls()throws Exception{
  User u=(User)UserFactory.createUser("authority-controls","authority-controls");Mind root=new Mind(u);authorityPair(root,"empty");
  List<TVariable> variables=new ArrayList<>();for(int i=0;i<3;i++)variables.add(variable(root,"route-"+i));
  List<TValue> values=new ArrayList<>();Map<Long,TValue> byId=new HashMap<>();
  for(int i=0;i<24;i++){TValue v=root.getTValues().add(variables.get(i%3),(Term)root.getTerms().add("term-"+i));values.add(v);byId.put(v.getId(),v);}
  authorityPair(root,"multi-value-before-forEach");for(TVariable v:variables)root.getTValues().forEach(v,o->true);authorityPair(root,"initialized-native-indices");
  Random random=new Random(0x5eed);List<Long> pool=new ArrayList<>(byId.keySet());pool.add(Long.MIN_VALUE);pool.add(Long.MAX_VALUE);
  for(int i=0;i<1028;i++){
   Collections.shuffle(pool,random);LinkedHashSet<Long> ids=new LinkedHashSet<>();
   if(i>0)for(long id:pool)if(random.nextBoolean())ids.add(id);
   if(i==1){ids.clear();ids.add(Long.MIN_VALUE);ids.add(Long.MAX_VALUE);}
   List<TValue> selected=new ArrayList<>();for(long id:ids){TValue v=byId.get(id);if(v!=null)selected.add(v);}
   LinkedHashSet<Long> idsBefore=new LinkedHashSet<>(ids);Map<Long,TValue> mapBefore=new HashMap<>(byId);
   String expected=selected.isEmpty()?null:ResidentTValueRead.encode(root,selected);
   require(Objects.equals(expected,StreamAuthorityRead.encodeRoutes(root,ids,byId)),"same ordered resident intersection and complete encoding");
   require(ids.equals(idsBefore)&&byId.equals(mapBefore),"serializer inputs unchanged");
  }
  Mind child=new Mind(root);authorityPair(child,"inherited-before-forEach");for(TVariable v:variables)child.getTValues().forEach(v,o->true);authorityPair(child,"inherited-native-indices");
  TValue local=child.getTValues().add(variables.get(0),(Term)child.getTerms().add("child-term"));authorityPair(child,"parent-and-child-route-order");
  child.setUnitDeleted(values.get(0),true);authorityPair(child,"inherited-value-deleted");child.setUnitDeleted(values.get(0),false);authorityPair(child,"inherited-value-restored");
  local.setValue((Term)child.getTerms().add("child-rewritten"));authorityPair(child,"local-term-rewrite");root.release(child);
  root.getTValues().clear();authorityPair(root,"cleared");System.out.println("AUTHORITY_CONTROLS_OK routeCases=1028 nativeStates=10");
 }
 public static void main(String[] args)throws Exception{
  authorityControls();maps();User user=(User)UserFactory.createUser("report-scope","report-scope");Mind m=new Mind(user);TVariable x=variable(m,"x"),z=variable(m,"z"),y=variable(m,"y");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");begin();observe(m,"empty-baseline");
  TValue vy=m.getTValues().add(y,a),vx=m.getTValues().add(x,a);touch(m,vy);touch(m,vx);observe(m,"insert-low-and-high");observe(m,"unchanged");set(vx,b);observe(m,"term-change");m.setUnitDeleted(vy,true);observe(m,"deleted-visibility");m.setUnitDeleted(vy,false);observe(m,"restored-visibility");
  // Direct comparison model: clear natively, then explicitly dirty both known routes.
  m.getTValues().clear();touch(m,vx);touch(m,vy);require(ResidentTValueRead.authority(m).isEmpty(),"native clear really removes keys");observe(m,"all-keys-removed");TValue vz=m.getTValues().add(z,b);touch(m,vz);observe(m,"new-key-after-clear");BeforeAuthorityJournal.reset(m);StreamAuthorityJournal.reset(m);observe(m,"explicit-reset");
  List<String> oldTrace=BeforeAuthorityJournal.finish(),newTrace=StreamAuthorityJournal.finish();require(oldTrace.equals(newTrace),"transition traces identical");String target=System.getProperty("result.path");Files.write(Paths.get(target+".old.trace"),oldTrace);Files.write(Paths.get(target+".memo.trace"),newTrace);List<String> errors=new ArrayList<>();
  for(int mode=0;mode<3;mode++){
   Mind n=new Mind(user);TVariable variable=variable(n,"negative-"+mode);Term one=(Term)n.getTerms().add("one"),two=(Term)n.getTerms().add("two");TValue v=n.getTValues().add(variable,one);begin();observe(n,"negative-baseline");
   if(mode==0){v.setValue(two);require(v.getValueId()==two.getId(),"unnotified native setter executes");}else if(mode==1)n.getTValues().add(variable,two);else n.getTValues().clear();observe(n,"missing-event");String oldError=null,newError=null;try{BeforeAuthorityJournal.finish();}catch(AssertionError e){oldError=e.getMessage();}try{StreamAuthorityJournal.finish();}catch(AssertionError e){newError=e.getMessage();}
   require(oldError!=null&&oldError.contains("dirty projection mismatch")&&oldError.equals(newError),"both full oracles refuse identical missing-event gap");errors.add("mode="+mode+" "+oldError);
  }
  Files.write(Paths.get(target+".negative-errors.txt"),errors);System.out.println("REPORT_SCOPE_OK maps=1028 negative=3 checks="+checks);
 }
}
