package org.kanger;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.*;
import org.kanger.units.*;
/** Current native DUMB factory paths, stable resident phase and identity gap. */
public final class MaterializationRoutingRunner {
    static int checks,callbacks;static boolean shadow=Boolean.getBoolean("persistence.shadow");
    static class TrapMind extends Mind {TrapMind()throws Exception{super(new User());}public TValueFactory getTValues(){callbacks++;throw new AssertionError("custom Mind callback");}}
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    static Object raw(Object o,String n)throws Exception{return ResidentTValueRead.field(o,n);}
    static void put(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static TVariable variable(Mind m,String name)throws Exception{Rule r=new Rule(m);m.getRules().register(r);return m.getTVars().createTVar(r,m.getTerms().add(name));}
    static String fingerprint(Mind m,Base b)throws Exception{return ResidentTValueRead.fingerprint(m)+ResidentPersistentRunner.fingerprint(b);}
    static void stable(Mind m,Base b,String why)throws Exception{String before=fingerprint(m,b);if(shadow)TValueDirtyJournal.observe(m,why);else ResidentTValueRead.authority(m);require(before.equals(fingerprint(m,b)),"changed owner/index/storage: "+why);}
    static SortedMap<Long,String> nativeView(Mind m)throws Exception{
        SortedSet<Long> keys=new TreeSet<>();for(TValue v:m.getTValues())keys.add(v.getTVarId());SortedMap<Long,String> out=new TreeMap<>();
        for(long k:keys){TVariable v=new TVariable(m);v.setId(k);List<TValue> values=new ArrayList<>();m.getTValues().forEach(v,o->{values.add((TValue)o);return true;});if(!values.isEmpty())out.put(k,ResidentTValueRead.encode(m,values));}return out;
    }
    @SuppressWarnings("unchecked")static Set<Long> persistent(TValueFactory f)throws Exception{return (Set<Long>)raw(raw(f,"cache"),"persistentIds");}
    static void trace(List<String> rows,String suffix)throws Exception{Files.write(Paths.get(System.getProperty("journal.path")+suffix),rows,StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        User user=(User)UserFactory.createUser("persistent-lookup","persistent-lookup");user.setProperty("cache.data.size","0");new DB().init(user);Mind m=new Mind(user);m=(Mind)m.useStorage("lookup");
        TValueFactory f=m.getTValues();Base base=(Base)user.getStorage(TValueFactory.SCHEMA);f.transaction(null);TVariable x=variable(m,"x"),y=variable(m,"y");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b"),c=(Term)m.getTerms().add("c");
        TValue va=f.add(x,a),vb=f.add(x,b),vc=f.add(y,c);long firstId=va.getId();f.update();f.dropAction();base.flush();base.getRoot();
        require(raw(f,"connection")==base,"native root connection");require(!((Boolean)raw(f,"indexInitialized")),"native variable index remains lazy");require(raw(raw(f,"cache"),"root").getClass()==Sapato.class,"native persistent factory root");
        List<TValue> resident=ResidentTValueRead.values(f);require(resident.size()==3&&resident.get(2).getId()==firstId,"resident physical newest-first order");
        va=(TValue)base.get(firstId).getData();require(va.getId()==firstId,"actual decoded first unit");
        // Native sibling reservation keeps intermediate settlement before root pack.
        Mind lease=new Mind(m);if(shadow)TValueDirtyJournal.begin();
        SortedMap<Long,String> pure=ResidentTValueRead.authority(m);stable(m,base,"persistent-lazy-baseline");require(!((Boolean)raw(f,"indexInitialized")),"diagnostic preserves lazy index");
        require(ResidentTValueRead.bucket(m,x.getId()).get(0)==va,"oldest-first native bucket uses actual Base cache object");
        require(nativeView(m).equals(pure),"root native iterator/forEach control matches");require((Boolean)raw(f,"indexInitialized"),"control initializes native index");stable(m,base,"persistent-initialized");
        Mind child=new Mind(m);TValue extra=child.getTValues().add(x,c);va.setDeleted(true,child);va.setMind(child);
        stable(m,base,"persistent-parent-owner-child");stable(child,base,"persistent-child-local-and-deletion");require(va.getMind()==child&&!va.isDeleted(m)&&va.isDeleted(child),"context deletion and owner preserved");
        List<TValue> childBucket=ResidentTValueRead.bucket(child,x.getId());require(childBucket.size()==3&&childBucket.get(0)==va&&childBucket.get(2)==extra,"persistent ancestry then local oldest-first");
        SortedMap<Long,String> childPure=ResidentTValueRead.authority(child);require(nativeView(child).equals(childPure),"actual child forEach control");va.setMind(child);require(nativeView(m).equals(pure)&&va.getMind()==m,"native root control still rebinds owner");
        child.getTValues().mark();TValue transientValue=child.getTValues().add(y,b);stable(child,base,"persistent-checkpoint-deferred");child.getTValues().release();stable(child,base,"persistent-checkpoint-release");require(!ResidentTValueRead.bucket(child,y.getId()).contains(transientValue),"local rollback preserved");
        va.setValue(b);stable(m,base,"persistent-existing-payload-write");stable(child,base,"persistent-shared-payload-write");va.setValue(a);stable(m,base,"persistent-payload-restored");stable(child,base,"persistent-child-payload-restored");
        m.release(child);require(m.pendingTransactionCount()==1,"only independent native reservation remains");if(shadow)trace(TValueDirtyJournal.finish(),".positive.trace");
        // Raw acceleration edits deliberately expose the difference between
        // root fallback and child lookup. They are outside a journal session.
        Set<Long> rootIds=persistent(f);require(rootIds.remove(firstId),"root persistent ID present");String before=fingerprint(m,base);require(ResidentTValueRead.bucket(m,x.getId()).get(0)==va,"root connection rescues missing persistent membership");require(before.equals(fingerprint(m,base)),"root fallback simulation does not repair membership");
        List<TValue> rootNative=new ArrayList<>();f.forEach(x,o->{rootNative.add((TValue)o);return true;});require(rootNative.get(0)==va,"native root fallback control");rootIds.add(firstId);
        Mind lookupChild=new Mind(m);Set<Long> childIds=persistent(lookupChild.getTValues());lookupChild.getTValues().get(firstId);require(childIds.remove(firstId),"child native lookup index built");before=fingerprint(lookupChild,base);List<TValue> without=ResidentTValueRead.bucket(lookupChild,x.getId());require(without.size()==1&&without.get(0).getId()==vb.getId(),"child has no connection fallback");require(before.equals(fingerprint(lookupChild,base)),"child missing membership not repaired");List<TValue> nativeWithout=new ArrayList<>();lookupChild.getTValues().forEach(x,o->{nativeWithout.add((TValue)o);return true;});require(nativeWithout.equals(without),"native child missing-membership control");childIds.add(firstId);m.release(lookupChild);
        // Active generation mismatch must reject even if IDs could match.
        put(f,"connection",null);before=fingerprint(m,base);ResidentTValueRead.authority(m);require(before.equals(fingerprint(m,base)),"persistent path does not need root fallback when all memberships exist");put(f,"connection",base);
        Object userStorage=raw(user,"storage");Map<String,Object> map=(Map<String,Object>)userStorage;map.remove(TValueFactory.SCHEMA);String storageBefore=ResidentPersistentRunner.fingerprint(base);boolean identityRefusal=false;try{ResidentTValueRead.authority(m);}catch(AssertionError e){identityRefusal=e.getMessage().contains("active storage identity");}require(identityRefusal&&storageBefore.equals(ResidentPersistentRunner.fingerprint(base)),"active registry mismatch refused without reads");map.put(TValueFactory.SCHEMA,base);
        // No observation or oracle registration between baseline and write:
        // native cache materialization creates new canonical object identities.
        TValue pendingReload=(TValue)base.get(firstId).getData();pendingReload.setValue(b);
        if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.observe(m,"identity-gap-baseline");}
        Mind reboundChild=new Mind(m);reboundChild.getTValues().get(firstId);if(shadow)TValueDirtyJournal.observe(reboundChild,"materialization-child-baseline");
        TValue old=(TValue)base.get(firstId).getData();base.clearCache();for(TValue v:resident)base.get(v.getId());storageBefore=ResidentPersistentRunner.fingerprint(base);boolean stale=false;try{ResidentTValueRead.authority(m);}catch(AssertionError e){stale=e.getMessage().contains("stale Sapato anchor");}require(stale&&storageBefore.equals(ResidentPersistentRunner.fingerprint(base)),"rewarmed cache with stale native anchor fails closed");
        // Explicit native iterator repairs its own anchor; diagnostic never does.
        for(TValue ignored:f){}TValue fresh=(TValue)base.get(firstId).getData();require(fresh!=old,"real cache rematerialization replaces TValue identity");before=fingerprint(m,base);ResidentTValueRead.authority(m);require(before.equals(fingerprint(m,base)),"post-native-refresh reader remains pure");
        stable(m,base,"reloaded-without-setter");stable(reboundChild,base,"reloaded-child-without-setter");require(fresh.getValueId()==a.getId()&&old.getValueId()==b.getId(),"native reload discards unflushed old payload without a replacement setter");
        fresh.setPersistentReferences(c.getId(),x.getId());boolean metadataGap=false;
        if(shadow){TValueDirtyJournal.observe(m,"rematerialized-registered-write");TValueDirtyJournal.observe(reboundChild,"rematerialized-child-write");int changes=TValueDirtyJournal.contextChangeCount(m)+TValueDirtyJournal.contextChangeCount(reboundChild);old.setPersistentReferences(c.getId(),x.getId());require(old.getValueId()==c.getId(),"detached scalar mutation really executes");TValueDirtyJournal.observe(m,"detached-old-write");TValueDirtyJournal.observe(reboundChild,"detached-old-child-write");require(changes==TValueDirtyJournal.contextChangeCount(m)+TValueDirtyJournal.contextChangeCount(reboundChild),"detached old instance no longer canonical");old.setPersistentReferences(a.getId(),x.getId());try{trace(TValueDirtyJournal.finish(),".rebound.trace");}catch(AssertionError e){metadataGap=e.getMessage().contains("dirty projection mismatch");Files.write(Paths.get(System.getProperty("journal.path")+".negative-error.txt"),Collections.singletonList(e.getMessage()),StandardCharsets.UTF_8);}require(!metadataGap,"post-native materialization registers replacement before setter");}
        require(fresh.getValueId()==c.getId()&&old.getValueId()==a.getId(),"native replacement write retained without repair");fresh.setPersistentReferences(a.getId(),x.getId());
        if(shadow){TValueDirtyJournal.begin();TValueDirtyJournal.observe(m,"new-session-after-identity-gap");trace(TValueDirtyJournal.finish(),".fresh.trace");}
        m.release(reboundChild);m.release(lease);require(m.pendingTransactionCount()==0,"final native root cleanup consumes last reservation");
        Mind trap=new TrapMind();for(int entry=0;entry<3;entry++){boolean refused=false;try{if(entry==0)ResidentTValueRead.authority(trap);else if(entry==1)ResidentTValueRead.bucket(trap,0);else ResidentTValueRead.fingerprint(trap);}catch(AssertionError e){refused=e.getMessage().contains("Mind class or cycle");}require(refused,"Mind entry class guard "+entry);}require(callbacks==0,"no custom Mind callback");
        System.out.println("MATERIALIZATION_ROUTING_NATIVE root_fallback=present child_fallback=absent order=oldest-first deletion=contextual owner=preserved lazy_index=preserved cache_refresh=explicit-native materialization_route=registered reservations="+m.pendingTransactionCount());
        System.out.println("MATERIALIZATION_ROUTING_OK checks="+checks+" shadow="+shadow+" expected_metadata_rejection="+metadataGap);
    }
}
