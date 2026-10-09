package org.kanger;
import java.util.*;
import org.kanger.units.TValue;
import org.kanger.factory.TValueFactory;
/** Diagnostic build adapter: no native state writes; forwards to both unchanged journals. */
public final class RecycledIdHooks {
 public static boolean suppressPromotion,suppressMetadata,suppressRemove,suppressResetFanout;public static final List<String> errors=new ArrayList<>();static boolean active; static final List<Mind> contexts=new ArrayList<>(); public static final List<String> aliases=new ArrayList<>(); static final boolean GUARD=Boolean.getBoolean("recycled.guard");
 public static final SortedMap<String,Integer> counts=new TreeMap<>();
 static boolean event(String name){if(!active)return false;counts.put(name,counts.getOrDefault(name,0)+1);return true;}
 public static void begin(){BeforeAuthorityJournal.begin();StreamAuthorityJournal.begin();contexts.clear();aliases.clear();errors.clear();active=true;}
 public static void stop(){active=false;suppressPromotion=false;suppressMetadata=false;suppressRemove=false;suppressResetFanout=false;}
 public static void constructed(Mind m){if(event("constructed")){contexts.add(m);BeforeAuthorityJournal.constructed(m);StreamAuthorityJournal.constructed(m);}}
 public static void reset(Mind m){if(event("reset")){BeforeAuthorityJournal.reset(m);StreamAuthorityJournal.reset(m);}}
 public static void mark(Mind m){if(event("mark")){BeforeAuthorityJournal.mark(m);StreamAuthorityJournal.mark(m);}}
 public static void complete(Mind m){if(event("complete")){BeforeAuthorityJournal.complete(m);StreamAuthorityJournal.complete(m);}}
 public static void touch(Mind m,TValue v,String reason){if(active&&GUARD&&reason.equals("add"))checkAlias(m,v);if(event(reason)&&!(suppressRemove&&reason.equals("remove"))){BeforeAuthorityJournal.touch(m,v,reason);StreamAuthorityJournal.touch(m,v,reason);}}
 public static void promoted(Mind m,TValueFactory f){if(event("promoted")&&!suppressPromotion){BeforeAuthorityJournal.promoted(m,f);StreamAuthorityJournal.promoted(m,f);}}
 public static void metadata(TValue v,long id,long variable,long term,String reason){if(event("metadata")&&!suppressMetadata){BeforeAuthorityJournal.metadata(v,id,variable,term,reason);StreamAuthorityJournal.metadata(v,id,variable,term,reason);}}
 public static void beginSettlement(Mind m){if(event("beginSettlement")){BeforeAuthorityJournal.beginSettlement(m);StreamAuthorityJournal.beginSettlement(m);BeforeAuthorityJournal.observe(m,"native-settlement-probe");StreamAuthorityJournal.observe(m,"native-settlement-probe");}}
 public static void endSettlement(Mind m){if(event("endSettlement")){BeforeAuthorityJournal.endSettlement(m);StreamAuthorityJournal.endSettlement(m);}}
 public static void retire(Mind m){if(event("retire")){contexts.remove(m);BeforeAuthorityJournal.retire(m);StreamAuthorityJournal.retire(m);}}
 static void checkAlias(Mind owner,TValue value){try{for(Mind target:new ArrayList<>(contexts)){boolean dependent=false;for(Mind m=target;m!=null;m=(Mind)m.getNext())if(m==owner)dependent=true;if(!dependent||target==owner)continue;String before=ResidentTValueRead.fingerprint(target);TValue old=ResidentTValueRead.layer(target.getTValues()).byId.get(value.getId());if(!before.equals(ResidentTValueRead.fingerprint(target)))throw new AssertionError("alias probe impure");if(old!=null&&old!=value)aliases.add("unsupported recycled TValue identity ownerLevel="+owner.getTransactionLevel()+" descendantLevel="+target.getTransactionLevel()+" id="+value.getId()+" oldVariable="+old.getTVarId()+" newVariable="+value.getTVarId());}}catch(Throwable e){errors.add(e.toString());}}
 public static Object beforeClear(Mind m,TValueFactory f){if(!event("beforeClear"))return null;try{String before=ResidentTValueRead.fingerprint(m);List<TValue> values=ResidentTValueRead.values(f);if(!before.equals(ResidentTValueRead.fingerprint(m)))throw new AssertionError("clear capture impure");return values;}catch(Throwable e){errors.add(e.toString());return null;}}
 @SuppressWarnings("unchecked") public static void afterClear(Mind m,Object token){if(!event("clear-fanout")||suppressResetFanout||token==null)return;for(TValue v:(List<TValue>)token)touch(m,v,"clear-removed-route");}
}

