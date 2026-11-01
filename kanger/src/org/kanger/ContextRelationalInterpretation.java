/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextWitnessResultTransport.Origin;
/** Immutable chosen all-admissible relations. Defaults cover every unlisted tuple, not asserted facts. */
final class ContextRelationalInterpretation {
 /** Choice identity only; unlike a transport Origin this creates no native evidence. */
 static final class Binder {
  final Source source; final int index;
  Binder(Source source,int index){this.source=Objects.requireNonNull(source);if(index<0)throw new IllegalArgumentException("Nonnegative original binder required");this.index=index;}
  Binder(Origin origin){this(origin.source,origin.binder);}
  @Override public boolean equals(Object o){return o instanceof Binder&&source.equals(((Binder)o).source)&&index==((Binder)o).index;}
  @Override public int hashCode(){return Objects.hash(source,index);}
 }
 static final class Signature {
  final String name;final int arity;
  Signature(String name,int arity){if(name==null||name.isEmpty()||name.length()>256||arity<0)throw new IllegalArgumentException("Actual predicate signature required");this.name=name;this.arity=arity;}
  @Override public boolean equals(Object o){return o instanceof Signature&&arity==((Signature)o).arity&&name.equals(((Signature)o).name);}
  @Override public int hashCode(){return Objects.hash(name,arity);}
 }
 static final class Table {
  final boolean otherwise;final Map<List<Integer>,Boolean> exceptions;
  Table(boolean otherwise,Map<List<Integer>,Boolean> exceptions){if(exceptions==null||exceptions.size()>16)throw new IllegalArgumentException("Bounded sparse relation data required");this.otherwise=otherwise;Map<List<Integer>,Boolean> copy=new LinkedHashMap<>();for(Map.Entry<List<Integer>,Boolean> e:exceptions.entrySet())copy.put(Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(e.getKey()))),Objects.requireNonNull(e.getValue()));this.exceptions=Collections.unmodifiableMap(copy);}
  boolean holds(List<Integer> tuple,Runnable work){ContextArgumentBudget.positions(tuple,work);Boolean value=exceptions.get(tuple);return value==null?otherwise:value;}
 }
 final List<SemanticTermSnapshot> points;final Map<Signature,Table> relations;final Map<Binder,Integer> witnesses;final Map<Origin,Integer> denotations;
 ContextRelationalInterpretation(List<SemanticTermSnapshot> points,Map<Signature,Table> relations,Map<Binder,Integer> witnesses,Map<Origin,Integer> denotations){if(points==null||points.isEmpty()||relations==null||relations.size()>64||witnesses==null||denotations==null)throw new IllegalArgumentException("Bounded relational interpretation required");this.points=Collections.unmodifiableList(new ArrayList<>(points));this.relations=Collections.unmodifiableMap(new LinkedHashMap<>(relations));this.witnesses=Collections.unmodifiableMap(new LinkedHashMap<>(witnesses));this.denotations=Collections.unmodifiableMap(new LinkedHashMap<>(denotations));}
}
