package org.kanger;
import java.util.*;
/** Atomic diagnostic export: no trace escapes before all checks finish. */
public final class QualifiedJournalConsumer {
 private static final ThreadLocal<Session> OPEN=new ThreadLocal<>();
 public static final class Refusal extends RuntimeException {
  public final String code;public final int aliasCount;public final boolean journalRejected;
  Refusal(String code,int aliases,boolean journalRejected){super(code+" aliases="+aliases+" journalRejected="+journalRejected);this.code=code;this.aliasCount=aliases;this.journalRejected=journalRejected;}
 }
 /** Session owns the bridge and journals until finish or abandonment. */
 public static final class Session implements AutoCloseable {
  private final Thread owner=Thread.currentThread();
  private final TValueObservation.Attachment attachment;
  private boolean closed;
  private Session(TValueObservation.Attachment attachment){this.attachment=attachment;}
  private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("consumer session owner thread required");if(closed||OPEN.get()!=this)throw new Refusal("NO_SESSION",0,false);}
  public List<String> finish(){check();return QualifiedJournalConsumer.finish();}
  public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("consumer session owner thread required");if(closed)return;check();try{BeforeAuthorityJournal.discard();StreamAuthorityJournal.discard();}finally{cleanup(this);}}
 }
 private static void requireInstrumentation(){
  for(Class<?> type:new Class<?>[]{Mind.class,org.kanger.factory.TValueFactory.class,org.kanger.units.TValue.class})try{
   if(!"tvalue-observer-v1".equals(type.getMethod("tvalueObserverProtocol").invoke(null)))throw new Refusal("NO_INSTRUMENTATION",0,false);
  }catch(ReflectiveOperationException e){throw new Refusal("NO_INSTRUMENTATION",0,false);}
 }
 public static Session open(){
  if(OPEN.get()!=null)throw new IllegalStateException("consumer session already open");
  requireInstrumentation();
  TValueObservation.Attachment attachment=TValueObservation.attach(ConsumerHooks.OBSERVER);
  try{ConsumerHooks.begin();Session session=new Session(attachment);OPEN.set(session);return session;}
  catch(Throwable failure){BeforeAuthorityJournal.discard();StreamAuthorityJournal.discard();ConsumerHooks.stop();attachment.close();throw failure;}
 }
 public static void begin(){open();}
 private static void cleanup(Session session){ConsumerHooks.stop();BeforeAuthorityJournal.discard();StreamAuthorityJournal.discard();session.attachment.close();session.closed=true;OPEN.remove();}
 public static List<String> finish(){
  Session session=OPEN.get();if(session==null)throw new Refusal("NO_SESSION",0,false);session.check();
  List<String> old=null,stream=null;AssertionError oldFailure=null,streamFailure=null;
  int aliases=ConsumerHooks.aliases.size();boolean adapterFailure=!ConsumerHooks.errors.isEmpty()||session.attachment.failed();
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
  } finally {cleanup(session);}
 }
}
