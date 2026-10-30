/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;

/** Invocation-owned authentication reuse for immutable detached lineage only. No proof/write receipt. */
final class ContextSignedWitnessAuditSession implements AutoCloseable {
 private static final ThreadLocal<ContextSignedWitnessAuditSession> ACTIVE=new ThreadLocal<>();
 private final Thread owner=Thread.currentThread();private final ContextSignedWitnessAuditSession previous;
 private final Runnable budget;private final int capacity;
 private final IdentityHashMap<ContextSignedWitnessContinuationScope.Receipt,Map<String,Entry>> memo=new IdentityHashMap<>();
 private final Set<ContextSignedWitnessContinuationScope.Receipt> running=Collections.newSetFromMap(new IdentityHashMap<ContextSignedWitnessContinuationScope.Receipt,Boolean>());
 private boolean closed,failed;private int entries;long hits,verified,bypasses;
 private static final class Frame {
  final Object[] refs;final int rows,generation,work,capacity;
  Frame(ContextSignedWitnessContinuationScope.Receipt r){rows=r.rows;generation=r.generation;work=r.auditWork;capacity=r.capacity;refs=new Object[]{r.barrier,r.later,r.prior,r.seeds,r.limits,r.current(),r.current().packet,r.current().privatePools,r.current().branches,r.units(),r.compounds()};}
  boolean same(ContextSignedWitnessContinuationScope.Receipt r){Frame f=new Frame(r);if(rows!=f.rows||generation!=f.generation||work!=f.work||capacity!=f.capacity)return false;for(int i=0;i<refs.length;++i)if(refs[i]!=f.refs[i])return false;return true;}
 }
 private static final class Entry {final Frame frame;boolean complete;Entry(ContextSignedWitnessContinuationScope.Receipt r){frame=new Frame(r);}}
 static final class Token {
  final boolean reused;private final ContextSignedWitnessAuditSession session;private final ContextSignedWitnessContinuationScope.Receipt receipt;private final Entry entry;private final Frame frame;
  private Token(boolean reused,ContextSignedWitnessAuditSession session,ContextSignedWitnessContinuationScope.Receipt receipt,Entry entry){this.reused=reused;this.session=session;this.receipt=receipt;this.entry=entry;frame=reused?null:entry==null?new Frame(receipt):entry.frame;}
 }
 private ContextSignedWitnessAuditSession(int work,int capacity){if(work<1||capacity<1||capacity>4096)throw new IllegalArgumentException("Bounded invocation audit session required");budget=ContextGroundProductionRounds.budget(work);this.capacity=capacity;previous=ACTIVE.get();ACTIVE.set(this);}
 static ContextSignedWitnessAuditSession open(int work,int capacity){return new ContextSignedWitnessAuditSession(work,capacity);}
 private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Invocation audit belongs to its invoking thread");}
 private void tick(){thread();if(closed||failed||ACTIVE.get()!=this)throw new IllegalStateException("Invocation audit closed/failed/not current");try{budget.run();}catch(RuntimeException|Error failure){poison();throw failure;}}
 private void poison(){failed=true;memo.clear();running.clear();entries=0;}
 int entries(){thread();return entries;}
 static void abort(){ContextSignedWitnessAuditSession s=ACTIVE.get();if(s!=null){s.thread();s.poison();}}
 static Token begin(ContextSignedWitnessContinuationScope.Receipt r,int work,int capacity){
  ContextSignedWitnessAuditSession s=ACTIVE.get();if(s==null)return null;s.tick();String key=work+":"+capacity;Map<String,Entry> keys=s.memo.get(r);Entry e=keys==null?null:keys.get(key);
  if(e!=null&&e.complete&&e.frame.same(r)){++s.hits;return new Token(true,s,r,e);}if(e!=null){keys.remove(key);--s.entries;if(keys.isEmpty())s.memo.remove(r);}
  if(!s.running.add(r)){s.poison();throw new IllegalStateException("Cyclic lineage authentication");}
  if(s.entries<s.capacity){if(keys==null||!s.memo.containsKey(r)){keys=new HashMap<>();s.memo.put(r,keys);}e=new Entry(r);keys.put(key,e);++s.entries;}else{e=null;++s.bypasses;}return new Token(false,s,r,e);
 }
 static void finish(Token t,boolean valid){if(t==null||t.reused)return;ContextSignedWitnessAuditSession s=t.session;s.thread();if(!valid){s.poison();return;}s.tick();if(!t.frame.same(t.receipt)){s.poison();throw new IllegalStateException("Lineage frame changed during invocation audit");}s.running.remove(t.receipt);if(t.entry!=null)t.entry.complete=true;++s.verified;}
 public void close(){thread();if(ACTIVE.get()!=this)throw new IllegalStateException("Invocation audit scopes must close in order");memo.clear();running.clear();entries=0;closed=true;if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
}
