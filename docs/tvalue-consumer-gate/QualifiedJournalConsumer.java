package org.kanger;
import java.util.*;
/** Atomic diagnostic export: no trace escapes before all checks finish. */
public final class QualifiedJournalConsumer {
 private static final ThreadLocal<Boolean> OPEN=new ThreadLocal<>();
 public static final class Refusal extends RuntimeException {
  public final String code;public final int aliasCount;public final boolean journalRejected;
  Refusal(String code,int aliases,boolean journalRejected){super(code+" aliases="+aliases+" journalRejected="+journalRejected);this.code=code;this.aliasCount=aliases;this.journalRejected=journalRejected;}
 }
 public static void begin(){if(Boolean.TRUE.equals(OPEN.get()))throw new IllegalStateException("consumer session already open");ConsumerHooks.begin();OPEN.set(true);}
 public static List<String> finish(){
  if(!Boolean.TRUE.equals(OPEN.get()))throw new Refusal("NO_SESSION",0,false);
  List<String> old=null,stream=null;AssertionError oldFailure=null,streamFailure=null;
  int aliases=ConsumerHooks.aliases.size();boolean adapterFailure=!ConsumerHooks.errors.isEmpty();
  try {
   // Both finishes execute even when the first refuses, clearing both journal sessions.
   try{old=BeforeAuthorityJournal.finish();}catch(AssertionError e){oldFailure=e;}
   try{stream=StreamAuthorityJournal.finish();}catch(AssertionError e){streamFailure=e;}
   boolean rejected=oldFailure!=null||streamFailure!=null;
   if(adapterFailure)throw new Refusal("ADAPTER_FAILURE",aliases,rejected);
   if(aliases!=0)throw new Refusal("RECYCLED_ID",aliases,rejected);
   if(rejected)throw new Refusal("FULL_AUTHORITY_MISMATCH",0,true);
   if(old==null||stream==null||!old.equals(stream))throw new Refusal("JOURNAL_DISAGREEMENT",0,false);
   return Collections.unmodifiableList(new ArrayList<>(old));
  } finally {ConsumerHooks.stop();OPEN.remove();}
 }
}
