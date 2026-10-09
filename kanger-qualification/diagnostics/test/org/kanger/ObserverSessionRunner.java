package org.kanger;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TValue;
import java.util.List;
/** Focused wiring/ownership faults, separate from the native parity fixture. */
public final class ObserverSessionRunner {
 static int checks;
 static void require(boolean value){if(!value)throw new AssertionError();checks++;}
 static void refuses(String code,Runnable action){try{action.run();throw new AssertionError("returned "+code);}catch(QualifiedJournalConsumer.Refusal e){require(code.equals(e.code));}}
 static void illegal(Runnable action){try{action.run();throw new AssertionError("accepted ownership violation");}catch(IllegalStateException expected){checks++;}}
 static void other(Runnable work)throws Exception{final Throwable[] failure={null};Thread t=new Thread(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});t.start();t.join();if(failure[0]!=null)throw new AssertionError(failure[0]);}
 static void nativeFailure(TValue value){try{value.setValue(null);throw new AssertionError("null setter accepted");}catch(NullPointerException expected){checks++;}}
 static final TValueObserver THROWING=new TValueObserver(){
  public void constructed(Mind m){throw new AssertionError("capture");}
  public void reset(Mind m){throw new AssertionError("capture");}
  public void mark(Mind m){throw new AssertionError("capture");}
  public void complete(Mind m){throw new AssertionError("capture");}
  public void touch(Mind m,TValue v,String r){throw new AssertionError("capture");}
  public void promoted(Mind m,TValueFactory f){throw new AssertionError("capture");}
  public void metadata(TValue v,long id,long variable,long term,String r){throw new AssertionError("capture");}
  public void beginSettlement(Mind m){throw new AssertionError("capture");}
  public void endSettlement(Mind m){throw new AssertionError("capture");}
  public void retire(Mind m){throw new AssertionError("capture");}
  public Object beforeClear(Mind m,TValueFactory f){throw new AssertionError("capture");}
  public void afterClear(Mind m,Object token){throw new AssertionError("capture");}
 };
 public static void main(String[] args)throws Exception{
  require(!TValueObservation.attached());
  if(!Boolean.getBoolean("native.hooks")){
   refuses("NO_INSTRUMENTATION",()->QualifiedJournalConsumer.open());
   refuses("NO_SESSION",()->QualifiedJournalConsumer.finish());require(!TValueObservation.attached());
   System.out.println("OBSERVER_SESSION_OK clean checks="+checks);return;
  }
  // Disabled callbacks do not populate adapter state or change native results.
  ConsumerRunner.Model disabled=new ConsumerRunner.Model("observer-disabled");disabled.all("disabled",null);disabled.close();require(ConsumerHooks.contexts.isEmpty());nativeFailure(disabled.base.get(0));
  QualifiedJournalConsumer.Session session=QualifiedJournalConsumer.open();
  illegal(()->QualifiedJournalConsumer.open());
  other(()->{illegal(()->QualifiedJournalConsumer.open());illegal(()->session.finish());illegal(()->session.close());});
  ConsumerRunner.Model m=new ConsumerRunner.Model("observer-owner");m.all("owner",null);m.close();List<String> trace=session.finish();require(!trace.isEmpty());require(!TValueObservation.attached());session.close();refuses("NO_SESSION",()->session.finish());
  // Abandonment drops journals, ownership and captured object references.
  try(QualifiedJournalConsumer.Session abandoned=QualifiedJournalConsumer.open()){new ConsumerRunner.Model("observer-abandoned");}
  require(!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty());
  // A foreign-thread callback taints the owner without calling its thread-local journals.
  QualifiedJournalConsumer.Session crossed=QualifiedJournalConsumer.open();other(()->TValueObservation.reset(null));refuses("ADAPTER_FAILURE",()->crossed.finish());require(!TValueObservation.attached());
  // Actual native operation still succeeds when every observer callback throws.
  try(TValueObservation.Attachment a=TValueObservation.attach(THROWING)){
   ConsumerRunner.Model faulty=new ConsumerRunner.Model("observer-throwing");faulty.all("throwing",null);faulty.close();nativeFailure(faulty.base.get(0));require(a.failed());
   illegal(()->QualifiedJournalConsumer.open());
  }
  try(QualifiedJournalConsumer.Session recovery=QualifiedJournalConsumer.open()){
   ConsumerRunner.Model fresh=new ConsumerRunner.Model("observer-fresh");fresh.all("fresh",null);fresh.close();require(!recovery.finish().isEmpty());
  }
  require(!TValueObservation.attached()&&ConsumerHooks.contexts.isEmpty());
  System.out.println("OBSERVER_SESSION_OK hooked checks="+checks);
 }
}
