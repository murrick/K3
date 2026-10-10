package org.kanger;
import java.util.*;
import java.lang.reflect.*;
import org.kanger.units.*;
/** Deliberately corrupt only one journal's rows to exercise the export gate. */
public final class GateDisagreementRunner {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Object session(Class<?> journal)throws Exception{Field f=journal.getDeclaredField("CURRENT");f.setAccessible(true);return ((ThreadLocal<?>)f.get(null)).get();}
 static void observe(Mind m){BeforeAuthorityJournal.observe(m,"baseline");StreamAuthorityJournal.observe(m,"baseline");}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  Mind m=new Mind((User)UserFactory.createUser("journal-disagreement","journal-disagreement"));Rule r=new Rule(m);m.getRules().register(r);TVariable v=m.getTVars().createTVar(r,m.getTerms().add("v"));m.getTValues().add(v,m.getTerms().add("a"));m.getTValues().forEach(v,o->true);
  String nativeBefore=ResidentTValueRead.fingerprint(m);
  try(QualifiedJournalConsumer.Session owner=QualifiedJournalConsumer.open()){
   observe(m);((List<String>)ResidentTValueRead.field(session(BeforeAuthorityJournal.class),"rows")).add("FAULT_INJECTED");
   try{owner.finish();throw new AssertionError("disagreement exported");}catch(QualifiedJournalConsumer.Refusal e){require(e.code.equals("JOURNAL_DISAGREEMENT")&&!e.journalRejected&&e.aliasCount==0,"exact mismatch refusal");}
  }
  require(session(BeforeAuthorityJournal.class)==null&&session(StreamAuthorityJournal.class)==null&&!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty(),"both journals and owner released");
  try(QualifiedJournalConsumer.Session owner=QualifiedJournalConsumer.open()){observe(m);List<String> trace=owner.finish();require(!trace.isEmpty()&&!trace.contains("FAULT_INJECTED"),"fresh session recovers");}
  require(nativeBefore.equals(ResidentTValueRead.fingerprint(m)),"native untouched");
  System.out.println("JOURNAL_DISAGREEMENT_GATE_OK outputRows=0 recovery=true nativeUntouched=true");
 }
}
