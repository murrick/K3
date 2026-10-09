package org.kanger;
import org.kanger.units.TValue;
import org.kanger.factory.TValueFactory;
/** Opt-in single-owner bridge for the generated qualification runtime only. */
public final class TValueObservation {
    private TValueObservation() {}
    // Publish attachment changes to the disabled fast path; dispatch still rechecks under the monitor.
    private static volatile Attachment current;
    public static final class Attachment implements AutoCloseable {
        private final Thread owner=Thread.currentThread();
        private TValueObserver observer;
        private boolean failed;
        private Attachment(TValueObserver observer) { this.observer=observer; }
        public boolean failed() { synchronized(TValueObservation.class) { checkOwner(); return failed; } }
        private void checkOwner() {
            if(Thread.currentThread()!=owner)throw new IllegalStateException("observer session owner thread required");
        }
        public void close() { synchronized(TValueObservation.class) {
            checkOwner(); if(current==this)current=null; observer=null;
        } }
    }
    public static synchronized Attachment attach(TValueObserver observer) {
        if(observer==null)throw new NullPointerException("observer");
        if(current!=null)throw new IllegalStateException("observer session already owned");
        current=new Attachment(observer); return current;
    }
    public static synchronized boolean attached() { return current!=null; }
    private interface Callback { Object call(TValueObserver observer); }
    private static synchronized Object dispatch(Callback callback) {
        Attachment a=current; if(a==null)return null;
        if(Thread.currentThread()!=a.owner) { a.failed=true; return null; }
        try { return callback.call(a.observer); }
        catch(Throwable failure) { a.failed=true; return null; }
    }
    public static void constructed(Mind m) { if(current==null)return; dispatch(o -> { o.constructed(m); return null; }); }
    public static void reset(Mind m) { if(current==null)return; dispatch(o -> { o.reset(m); return null; }); }
    public static void mark(Mind m) { if(current==null)return; dispatch(o -> { o.mark(m); return null; }); }
    public static void complete(Mind m) { if(current==null)return; dispatch(o -> { o.complete(m); return null; }); }
    public static void touch(Mind m,TValue v,String reason) { if(current==null)return; dispatch(o -> { o.touch(m,v,reason); return null; }); }
    public static void promoted(Mind m,TValueFactory f) { if(current==null)return; dispatch(o -> { o.promoted(m,f); return null; }); }
    public static void metadata(TValue v,long id,long variable,long term,String reason) { if(current==null)return; dispatch(o -> { o.metadata(v,id,variable,term,reason); return null; }); }
    public static void beginSettlement(Mind m) { if(current==null)return; dispatch(o -> { o.beginSettlement(m); return null; }); }
    public static void endSettlement(Mind m) { if(current==null)return; dispatch(o -> { o.endSettlement(m); return null; }); }
    public static void retire(Mind m) { if(current==null)return; dispatch(o -> { o.retire(m); return null; }); }
    public static Object beforeClear(Mind m,TValueFactory f) { if(current==null)return null; return dispatch(o -> o.beforeClear(m,f)); }
    public static void afterClear(Mind m,Object token) { if(current==null)return; dispatch(o -> { o.afterClear(m,token); return null; }); }
}
