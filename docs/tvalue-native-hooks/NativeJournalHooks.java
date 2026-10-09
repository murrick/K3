package org.kanger;
import java.util.*;
import org.kanger.units.TValue;
import org.kanger.factory.TValueFactory;
/** Diagnostic build adapter: no native state writes; forwards to both unchanged journals. */
public final class NativeJournalHooks {
 public static boolean suppressPromotion;static boolean active;
 public static final SortedMap<String,Integer> counts=new TreeMap<>();
 static boolean event(String name){if(!active)return false;counts.put(name,counts.getOrDefault(name,0)+1);return true;}
 public static void begin(){BeforeAuthorityJournal.begin();StreamAuthorityJournal.begin();active=true;}
 public static void stop(){active=false;suppressPromotion=false;}
 public static void constructed(Mind m){if(event("constructed")){BeforeAuthorityJournal.constructed(m);StreamAuthorityJournal.constructed(m);}}
 public static void reset(Mind m){if(event("reset")){BeforeAuthorityJournal.reset(m);StreamAuthorityJournal.reset(m);}}
 public static void mark(Mind m){if(event("mark")){BeforeAuthorityJournal.mark(m);StreamAuthorityJournal.mark(m);}}
 public static void complete(Mind m){if(event("complete")){BeforeAuthorityJournal.complete(m);StreamAuthorityJournal.complete(m);}}
 public static void touch(Mind m,TValue v,String reason){if(event(reason)){BeforeAuthorityJournal.touch(m,v,reason);StreamAuthorityJournal.touch(m,v,reason);}}
 public static void promoted(Mind m,TValueFactory f){if(event("promoted")&&!suppressPromotion){BeforeAuthorityJournal.promoted(m,f);StreamAuthorityJournal.promoted(m,f);}}
 public static void metadata(TValue v,long id,long variable,long term,String reason){if(event("metadata")){BeforeAuthorityJournal.metadata(v,id,variable,term,reason);StreamAuthorityJournal.metadata(v,id,variable,term,reason);}}
 public static void beginSettlement(Mind m){if(event("beginSettlement")){BeforeAuthorityJournal.beginSettlement(m);StreamAuthorityJournal.beginSettlement(m);BeforeAuthorityJournal.observe(m,"native-settlement-probe");StreamAuthorityJournal.observe(m,"native-settlement-probe");}}
 public static void endSettlement(Mind m){if(event("endSettlement")){BeforeAuthorityJournal.endSettlement(m);StreamAuthorityJournal.endSettlement(m);}}
 public static void retire(Mind m){if(event("retire")){BeforeAuthorityJournal.retire(m);StreamAuthorityJournal.retire(m);}}
}
