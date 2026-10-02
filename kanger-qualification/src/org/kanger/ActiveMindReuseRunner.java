/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Observable context/owner behavior; do not assert private reference identity. */
public final class ActiveMindReuseRunner {
    private static int checks;
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static void check(boolean ok, String label) { require(ok, label); checks++; }
    private static Mind owner(TVariable variable) throws Exception {
        Field field=TVariable.class.getDeclaredField("mind"); field.setAccessible(true);
        return (Mind)field.get(variable);
    }
    @SuppressWarnings("unchecked")
    private static WeakReference<Mind> selected(TVariable variable) throws Exception {
        Field field=TVariable.class.getDeclaredField("runtimeMind"); field.setAccessible(true);
        return ((ThreadLocal<WeakReference<Mind>>)field.get(variable)).get();
    }
    private static final class ObservedMind extends Mind {
        int nextCalls,idCalls;
        boolean nextFail,idFail;
        Runnable action;
        ObservedMind(Mind root) throws Exception { super(root); }
        @Override public IMind getNext() {
            nextCalls++;
            if (action!=null) { Runnable one=action; action=null; one.run(); }
            if (nextFail) throw new IllegalStateException("next callback");
            return super.getNext();
        }
        @Override public long getId() {
            idCalls++;
            if (idFail) throw new IllegalStateException("id callback");
            return super.getId();
        }
        void reset() { nextCalls=idCalls=0; }
    }
    private static final class ObservedVariable extends TVariable {
        int sets;
        @Override public TVariable setMind(Mind mind) { sets++; return super.setMind(mind); }
    }
    private static void callbacks(Mind root) throws Exception {
        TVariable v=new TVariable(root);
        ObservedMind child=new ObservedMind(root);
        child.reset();
        for(int i=0;i<3;i++)v.setMind(child);
        check(child.nextCalls==3 && child.idCalls==3,"owner callbacks on repeat");
        check(v.getMind()==child && owner(v)==root,"child selection and owner");
        child.nextFail=true;child.reset();
        try {v.setMind(child);throw new AssertionError("next callback skipped");}
        catch(IllegalStateException expected){check("next callback".equals(expected.getMessage()),"next failure");}
        check(child.nextCalls==1 && child.idCalls==0 && v.getMind()==child,"selection before failure");
        child.nextFail=false;child.idFail=true;child.reset();
        try {v.setMind(child);throw new AssertionError("id callback skipped");}
        catch(IllegalStateException expected){check("id callback".equals(expected.getMessage()),"id failure");}
        check(child.nextCalls==1 && child.idCalls==1 && v.getMind()==child,"id failure order");
        child.idFail=false;child.reset();
        child.action=()->v.setMind(root);v.setMind(child);
        check(v.getMind()==root && owner(v)==root,"reentrant callback selection");
        check(child.nextCalls==1 && child.idCalls==1,"reentrant callback counts");
        ObservedVariable custom=new ObservedVariable();
        custom.setMind(root);custom.setMind(child);custom.setMind(child);
        check(custom.sets==3 && custom.getMind()==child,"custom variable callbacks");
        // Historical owner update must still run even if the active reference matches.
        v.setMind(child);v.setMindId(child.getId());v.setMind(child);
        check(owner(v)==child,"persistent owner qualification on same selected context");
    }
    private static void concurrency(TVariable v,Mind root,Mind left,Mind right,ITerm a,ITerm b) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread[] threads=new Thread[2];
        for(int i=0;i<2;i++) {
            final Mind context=i==0?left:right;final ITerm expected=i==0?a:b;
            threads[i]=new Thread(()->{
                try {
                    v.setMind(context);ready.countDown();start.await();
                    for(int j=0;j<200;j++) {
                        v.setMind(context);v.setMind(context);
                        require(v.getMind()==context,"thread context");
                        require(v.getValue()==expected,"thread binding");
                        require(v.getCurrent()==context.getTValues().get(v),"thread TValue");
                    }
                }catch(Throwable problem){failure.compareAndSet(null,problem);ready.countDown();}
            });threads[i].start();
        }
        ready.await();start.countDown();for(Thread thread:threads)thread.join();
        if(failure.get()!=null)throw new AssertionError("sibling isolation",failure.get());
        check(v.getMind()==root,"main thread unchanged");
        check(owner(v)==root,"shared owner unchanged");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("active-mind-reuse-").toString());
        Mind root=new Mind(new User());
        ITerm a=root.getTerms().add("a"),b=root.getTerms().add("b");
        TVariable v=root.getTVars().createTVar(new Rule(root),a);
        TValue av=root.getTValues().add(v,a),bv=root.getTValues().add(v,b);
        root.getTValues().set(v,av);v.setMind(root);
        check(v.getMind()==root && v.getValue()==a,"root selection");
        v.setMind(root);check(v.getCurrent()==av,"root repeat");
        Mind left=new Mind(root),right=new Mind(root);
        left.getTValues().set(v,av);right.getTValues().set(v,bv);
        v.setMind(left);v.setMind(left);check(v.getValue()==a && owner(v)==root,"left repeat");
        v.setMind(right);v.setMind(right);check(v.getValue()==b && owner(v)==root,"right repeat");
        selected(v).clear();check(v.getMind()==root,"cleared weak reference fallback");
        v.setMind(right);check(v.getMind()==right && v.getValue()==b,"reselect cleared context");
        v.setMind(root);concurrency(v,root,left,right,a,b);
        TVariable empty=new TVariable();check(empty.getMind()==null,"unset");
        empty.setMind(null);empty.setMind(null);check(empty.getMind()==null,"null repeat without owner");
        try {v.setMind(null);throw new AssertionError("historical null failure skipped");}
        catch(NullPointerException expected){check(v.getMind()==root,"null failure preserves owner fallback");}
        v.setMind(left);check(v.getValue()==a,"selection after null failure");
        callbacks(root);
        v.setMind(root);root.release(left);root.release(right);
        check(v.getMind()==root,"release preserves selected context");
        System.out.println("ACTIVE_MIND_REUSE_OK checks="+checks);
    }
}
