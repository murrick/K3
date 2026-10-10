package org.kanger;
import java.lang.reflect.Proxy;
/** Bridge-only lifecycle/callback contract, independent of native mutation fixtures. */
public final class ObserverDispatchRunner {
    static int calls; static boolean throwing; static final Object TOKEN=new Object();
    static void require(boolean b) { if(!b)throw new AssertionError(); }
    static TValueObserver observer() {
        return (TValueObserver)Proxy.newProxyInstance(TValueObserver.class.getClassLoader(),new Class<?>[]{TValueObserver.class},(proxy,method,args)->{
            calls++; if(throwing)throw new AssertionError("capture");
            return method.getName().equals("beforeClear")?TOKEN:null;
        });
    }
    static Object all() {
        TValueObservation.constructed(null); TValueObservation.reset(null);
        TValueObservation.mark(null); TValueObservation.complete(null);
        TValueObservation.touch(null,null,"test"); TValueObservation.promoted(null,null);
        TValueObservation.metadata(null,1,2,3,"test");
        TValueObservation.beginSettlement(null); TValueObservation.endSettlement(null);
        TValueObservation.retire(null); Object token=TValueObservation.beforeClear(null,null);
        TValueObservation.afterClear(null,token); return token;
    }
    static void illegal(Runnable r) { try { r.run();throw new AssertionError("accepted"); } catch(IllegalStateException expected) {} }
    static void other(Runnable r) throws Exception {
        final Throwable[] error={null};Thread t=new Thread(()->{try { r.run(); }catch(Throwable f){error[0]=f;}});
        t.start();t.join();if(error[0]!=null)throw new AssertionError(error[0]);
    }
    public static void main(String[] args) throws Exception {
        require(!TValueObservation.attached()); require(all()==null && calls==0);
        TValueObservation.Attachment old=TValueObservation.attach(observer());
        require(all()==TOKEN && calls==12 && !old.failed());
        illegal(()->TValueObservation.attach(observer()));
        other(()->{illegal(()->old.close());illegal(()->old.failed());});
        require(TValueObservation.attached() && !old.failed());
        old.close();old.close();require(!TValueObservation.attached());
        require(all()==null && calls==12);
        throwing=true;
        try(TValueObservation.Attachment a=TValueObservation.attach(observer())) {
            require(all()==null && calls==24 && a.failed());
        }
        throwing=false;
        try(TValueObservation.Attachment a=TValueObservation.attach(observer())) {
            old.close();require(TValueObservation.attached());
            other(()->require(all()==null));require(calls==24 && a.failed());
        }
        try(TValueObservation.Attachment a=TValueObservation.attach(observer())) {
            require(all()==TOKEN && calls==36 && !a.failed());
        }
        require(!TValueObservation.attached());require(all()==null && calls==36);
        System.out.println("OBSERVER_DISPATCH_CONTRACT_OK callbacks=12");
    }
}
