package org.kanger;
import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TValue;
/** Candidate-only, caller-quiescent, lexical observation lifetime. Oracle never uses this memo. */
final class ObservationLayers implements AutoCloseable {
 private final Mind mind;private final Thread owner=Thread.currentThread();
 private final IdentityHashMap<TValueFactory,ResidentTValueRead.Layer> memo=new IdentityHashMap<>();private boolean closed;
 ObservationLayers(Mind m){mind=m;}
 private void open(){if(closed||Thread.currentThread()!=owner)throw new AssertionError("closed or foreign observation frame");}
 private ResidentTValueRead.Layer get(TValueFactory f)throws Exception{open();ResidentTValueRead.Layer l=memo.get(f);if(l==null){l=ResidentTValueRead.layer(f);memo.put(f,l);}return l;}
 List<TValue> values()throws Exception{return get(mind.getTValues()).chain;}
 private void collect(TValueFactory f,long variable,LinkedHashSet<Long> ids,Set<TValueFactory> seen)throws Exception{
  if(!seen.add(f))ResidentTValueRead.unsupported("factory ancestry cycle");ResidentTValueRead.Layer l=get(f);
  if(l.parent!=null)collect(l.parent,variable,ids,seen);List<Long> local=l.routes.get(variable);if(local!=null)ids.addAll(local);
 }
 List<TValue> bucket(long variable)throws Exception{
  open();ResidentTValueRead.validateMind(mind);ResidentTValueRead.Layer target=get(mind.getTValues());LinkedHashSet<Long> ids=new LinkedHashSet<>();collect(target.factory,variable,ids,Collections.newSetFromMap(new IdentityHashMap<>()));
  List<TValue> result=new ArrayList<>();for(long id:ids){TValue v=target.byId.get(id);if(v!=null)result.add(v);}return result;
 }
 int extracted(){open();return memo.size();}
 public void close(){open();memo.clear();closed=true;}
}
