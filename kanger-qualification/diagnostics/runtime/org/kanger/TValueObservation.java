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
    /** Called only while holding TValueObservation.class. */
    private static Attachment ownedAttachment() {
        Attachment a=current;
        if(a!=null && Thread.currentThread()!=a.owner) { a.failed=true; return null; }
        return a;
    }
    public static void constructed(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.constructed(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void reset(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.reset(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void mark(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.mark(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void complete(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.complete(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void touch(Mind m,TValue v,String reason) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.touch(m,v,reason); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void promoted(Mind m,TValueFactory f) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.promoted(m,f); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void metadata(TValue v,long id,long variable,long term,String reason) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.metadata(v,id,variable,term,reason); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void beginSettlement(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.beginSettlement(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void endSettlement(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.endSettlement(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static void retire(Mind m) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.retire(m); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
    public static Object beforeClear(Mind m,TValueFactory f) {
        if(current==null)return null;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return null;
            try { return a.observer.beforeClear(m,f); }
            catch(Throwable failure) { a.failed=true; return null; }
        }
    }
    public static void afterClear(Mind m,Object token) {
        if(current==null)return;
        synchronized(TValueObservation.class) {
            Attachment a=ownedAttachment(); if(a==null)return;
            try { a.observer.afterClear(m,token); }
            catch(Throwable failure) { a.failed=true; }
        }
    }
}
