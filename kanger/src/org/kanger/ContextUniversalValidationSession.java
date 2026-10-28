/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextUniversalFrozenConstraints.Result;

/** Bounded invocation-local authentication reuse for immutable detached graphs; no proof/write receipt. */
final class ContextUniversalValidationSession {
    static final class Request {
        final Result result;final int depth;
        Request(Result result,int depth){this.result=result;this.depth=depth;}
    }
    static final class Metrics {
        final long work,lookups,hits,verified,capacityBypass;
        final int entries,peakEntries,capacity;
        private Metrics(Work w){work=w.attempts;lookups=w.lookups;hits=w.hits;verified=w.verified;capacityBypass=w.capacityBypass;entries=w.entries;peakEntries=w.peak;capacity=w.capacity;}
    }
    static final class Scope implements AutoCloseable {
        final Runnable work;private final Work owner;
        private Scope(int maximumWork,int capacity){owner=new Work(maximumWork,capacity);work=owner;}
        Metrics metrics(){owner.thread();return new Metrics(owner);}
        public void close(){owner.thread();owner.closed=true;owner.memo.clear();owner.entries=0;}
    }
    /** Internal trusted code only: graph inputs must remain immutable for this lexical lifetime. */
    static Scope open(int maximumWork,int capacity){if(maximumWork<1||capacity<1||capacity>4096)throw new IllegalArgumentException("Bounded validation session required");return new Scope(maximumWork,capacity);}
    private static final class Frame {
        final Object[] refs;final int flags,rounds,obligations;
        Frame(Result r){refs=refs(r);flags=flags(r);rounds=r==null?0:r.rounds;obligations=r==null?0:r.obligations;}
        private static Object[] refs(Result r){return r==null?new Object[0]:new Object[]{r.input,r.common,r.local,r.pool,r.exchange,r.compounds,r.groupedBase,r.groupedInputs,r.proofs,r.emptyProofs,r.reason};}
        private static int flags(Result r){return r==null?0:(r.closed?1:0)|(r.xRefuted?2:0)|(r.foreignRefuted?4:0)|(r.signedUnits?8:0)|(r.applications?16:0)|(r.signedApplications?32:0)|(r.symbolicHeads?64:0)|(r.groundedMixed?128:0)|(r.instantiatedUnits?256:0);}
        boolean same(Result r){if(r==null)return refs.length==0;Object[] current=refs(r);if(flags!=flags(r)||rounds!=r.rounds||obligations!=r.obligations||refs.length!=current.length)return false;for(int i=0;i<refs.length;++i)if(refs[i]!=current[i])return false;return true;}
    }
    private static final class Entry {
        final Frame frame;boolean complete;
        Entry(Result r){frame=new Frame(r);}
    }
    private static final class Work implements Runnable {
        final Thread owner=Thread.currentThread();final Runnable budget;final int capacity;
        final IdentityHashMap<Result,Map<Integer,Entry>> memo=new IdentityHashMap<>();
        boolean closed,failed;int entries,peak;long attempts,lookups,hits,verified,capacityBypass;
        Work(int maximumWork,int capacity){budget=ContextGroundProductionRounds.budget(maximumWork);this.capacity=capacity;}
        void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Validation session belongs to its invoking thread");}
        void active(){thread();if(closed||failed)throw new IllegalStateException("Validation session closed or failed");}
        void poison(){failed=true;memo.clear();entries=0;}
        public void run(){active();++attempts;try{budget.run();}catch(RuntimeException|Error e){poison();throw e;}}
    }
    static final class Token {
        final boolean reused;private final Work owner;private final Result result;private final Entry entry;private final Frame frame;
        private Token(boolean reused,Work owner,Result result,Entry entry){this.reused=reused;this.owner=owner;this.result=result;this.entry=entry;frame=reused?null:entry==null?new Frame(result):entry.frame;}
    }
    private static final Token HIT=new Token(true,null,null,null);
    static Token begin(Result result,int depth,Runnable callback){
        if(!(callback instanceof Work))return null;Work w=(Work)callback;w.run();++w.lookups;
        boolean bounded=result!=null&&depth>=0&&depth<=64;Map<Integer,Entry> keys=bounded?w.memo.get(result):null;Entry entry=keys==null?null:keys.get(depth);
        if(entry!=null){
            if(!entry.complete){w.poison();throw new IllegalStateException("Cyclic or changing validation frame");}
            if(entry.frame.same(result)){++w.hits;return HIT;}
            keys.remove(depth);--w.entries;if(keys.isEmpty())w.memo.remove(result);keys=null;
        }
        if(bounded&&w.entries<w.capacity){if(keys==null){keys=w.memo.get(result);if(keys==null){keys=new HashMap<>();w.memo.put(result,keys);}}entry=new Entry(result);keys.put(depth,entry);++w.entries;w.peak=Math.max(w.peak,w.entries);}else{entry=null;++w.capacityBypass;}
        return new Token(false,w,result,entry);
    }
    static void finish(Token token,boolean valid){
        if(token==null||token.reused)return;Work w=token.owner;
        if(!valid){w.poison();return;}w.active();if(!token.frame.same(token.result)){w.poison();throw new IllegalStateException("Validation frame changed during authentication");}++w.verified;if(token.entry!=null)token.entry.complete=true;
    }
    static Metrics checkAll(List<Request> requests,int maximumWork,int capacity){
        if(requests==null||requests.size()>65536)throw new IllegalArgumentException("Bounded actual validation requests required");List<Request> actual=new ArrayList<>(requests);
        try(Scope scope=open(maximumWork,capacity)){for(Request r:actual){scope.work.run();if(r==null)throw new IllegalStateException("Actual validation request required");ContextUniversalFrozenConstraints.check(r.result,r.depth,scope.work);}return scope.metrics();}
    }
    static Metrics checkCertificate(ContextUniversalGroupedLocalRounds.Certificate certificate,int maximumWork,int capacity){try(Scope scope=open(maximumWork,capacity)){ContextUniversalGroupedLocalRounds.check(certificate,scope.work);return scope.metrics();}}
}
