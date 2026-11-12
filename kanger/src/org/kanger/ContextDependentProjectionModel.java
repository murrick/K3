/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentProductionInspection.Row;
import org.kanger.ContextDependentProductionInspection.TermView;
import org.kanger.ContextProductionCapture.Source;

/** Bounded candidate generator only. Native and independent indexed audits decide truth. */
final class ContextDependentProjectionModel {
    private ContextDependentProjectionModel() {}
    static final class Unit {
        final Source source;
        final NativePrenexUnitScope prefix;
        Unit(Source source,NativePrenexUnitScope prefix){this.source=source;this.prefix=prefix;}
    }
    private static final class TooWide extends Exception {}
    private interface Tuple {void accept(List<Integer> values)throws Exception;}
    private static final class Build {
        final List<SemanticTermSnapshot> points;
        final int classes,maximumCells;
        final Runnable work;
        int remaining,used;
        final Map<Signature,Map<List<Integer>,Boolean>> relations=new LinkedHashMap<>();
        final Map<Function,Choice> functions=new LinkedHashMap<>();
        Build(List<SemanticTermSnapshot> points,int choices,int cells,Runnable work){this.points=points;classes=points.size()+1;remaining=choices;maximumCells=cells;this.work=work;}
        void cell()throws TooWide{work.run();if(used==maximumCells)throw new TooWide();used++;}
        // Reserve the complete Cartesian pass before allocating its odometer or tuples.
        void tuples(int rank,Tuple action)throws Exception{
            work.run();int count=1;
            for(int i=0;i<rank;i++){work.run();if(count>remaining/classes)throw new TooWide();count*=classes;}
            if(count>remaining)throw new TooWide();remaining-=count;
            int[] digits=new int[rank];
            for(int row=0;row<count;row++){
                ContextDependentRuntimeExecution.interrupt();work.run();List<Integer> tuple=new ArrayList<>();for(int digit:digits){work.run();tuple.add(digit);}action.accept(tuple);
                for(int i=digits.length-1;i>=0;i--){work.run();if(++digits[i]<classes)break;digits[i]=0;}
            }
        }
        int fixed(SemanticTermSnapshot value){for(int i=0;i<points.size();i++){work.run();if(points.get(i).semanticallyEquals(value))return i;}throw new IllegalStateException("Projection guess requires every actual typed constant");}
        int choice(Function function,List<Integer> args){work.run();Choice c=functions.get(function);if(c==null||c.arity!=args.size())throw new IllegalStateException("Projection guess requires exact declared function rank");Integer v=c.exceptions.get(args);return v==null?c.otherwise:v;}
        void positive(Signature signature,List<Integer> tuple)throws TooWide{
            work.run();Map<List<Integer>,Boolean> table=relations.get(signature);if(table==null)throw new IllegalStateException("Projection guess requires complete relation vocabulary");
            if(!table.containsKey(tuple)){cell();table.put(Collections.unmodifiableList(new ArrayList<>(tuple)),true);}
        }
        void unit(Unit unit)throws Exception{
            NativePrenexUnitScope p=unit.prefix;int universals=0;
            for(NativePrenexUnitScope.Binder b:p.binders){work.run();if(b.universal)universals++;}
            tuples(universals,assignment->{
                List<Integer> values=new ArrayList<>();int next=0;
                for(int i=0;i<p.binders.size();i++){
                    work.run();NativePrenexUnitScope.Binder binder=p.binders.get(i);
                    if(binder.universal)values.add(assignment.get(next++));
                    else{List<Integer> args=new ArrayList<>();for(int dependency:binder.precedingUniversals){work.run();args.add(values.get(dependency));}values.add(choice(new Function(unit.source,i),args));}
                }
                List<Integer> tuple=new ArrayList<>();for(NativePrenexUnitScope.Slot slot:p.slots){work.run();tuple.add(slot.binder<0?fixed(slot.value):values.get(slot.binder));}
                positive(new Signature(p.predicate,p.slots.size()),tuple);
            });
        }
        int term(TermView root,Map<Integer,Integer> variables)throws Exception{
            Map<TermView,Integer> values=new IdentityHashMap<>();Deque<TermView> todo=new ArrayDeque<>();todo.push(root);
            while(!todo.isEmpty()){
                ContextDependentRuntimeExecution.interrupt();work.run();TermView t=todo.peek();if(values.containsKey(t)){todo.pop();continue;}
                switch(t.kind){
                    case VARIABLE:Integer v=variables.get(t.variable);if(v==null)throw new IllegalStateException("Projection variable assignment missing");values.put(t,v);todo.pop();break;
                    case VALUE:values.put(t,fixed(t.value));todo.pop();break;
                    case APPLICATION:
                        boolean ready=true;for(TermView child:t.arguments){work.run();if(!values.containsKey(child)){todo.push(child);ready=false;break;}}
                        if(ready){List<Integer> args=new ArrayList<>();for(TermView child:t.arguments){work.run();args.add(values.get(child));}values.put(t,choice(new Function(t.symbolSource,t.binder),args));todo.pop();}break;
                    default:throw new IllegalStateException("Unknown projection term shape");
                }
            }
            return values.get(root);
        }
        void row(Row row)throws Exception{
            // Negative obligations constrain a guess; they never become guessed positive facts.
            if(!row.positive)return;
            Set<Integer> ids=new LinkedHashSet<>();Set<TermView> seen=Collections.newSetFromMap(new IdentityHashMap<TermView,Boolean>());
            Deque<TermView> todo=new ArrayDeque<>(row.arguments);
            while(!todo.isEmpty()){work.run();TermView t=todo.pop();if(!seen.add(t))continue;if(t.kind==ContextDependentProductionInspection.Kind.VARIABLE)ids.add(t.variable);for(TermView child:t.arguments){work.run();todo.push(child);}}
            List<Integer> keys=new ArrayList<>(ids);
            tuples(keys.size(),assignment->{Map<Integer,Integer> variables=new HashMap<>();for(int i=0;i<keys.size();i++){work.run();variables.put(keys.get(i),assignment.get(i));}List<Integer> tuple=new ArrayList<>();for(TermView t:row.arguments)tuple.add(term(t,variables));positive(new Signature(row.predicate,row.arguments.size()),tuple);});
        }
    }
    static Model propose(List<SemanticTermSnapshot> points,SemanticTermSnapshot other,Set<Signature> signatures,
                         Map<Function,Integer> declarations,List<Unit> units,List<Row> rows,
                         int projection,int choices,int cells,Runnable work)throws Exception{
        return propose(points,other,signatures,declarations,units,rows,projection,0,choices,cells,work);
    }
    static Model propose(List<SemanticTermSnapshot> points,SemanticTermSnapshot other,Set<Signature> signatures,
                         Map<Function,Integer> declarations,List<Unit> units,List<Row> rows,
                         int projection,int independentClass,int choices,int cells,Runnable work)throws Exception{
        if(declarations.isEmpty())return null;
        if(independentClass<0||independentClass>points.size())throw new IllegalArgumentException("Named or ALL-other nullary witness class required");
        Build b=new Build(points,choices,cells,work);
        try{
            for(Signature signature:signatures){b.cell();b.relations.put(signature,new LinkedHashMap<>());}
            for(Map.Entry<Function,Integer> entry:declarations.entrySet()){
                ContextDependentRuntimeExecution.interrupt();b.cell();Map<List<Integer>,Integer> table=new LinkedHashMap<>();
                // Preceding-universal projections retain their old semantics; nullary witnesses try a bounded palette class.
                if(entry.getValue()>0){int selected=projection%entry.getValue();b.tuples(entry.getValue(),tuple->{if(tuple.get(selected)!=0){b.cell();table.put(Collections.unmodifiableList(new ArrayList<>(tuple)),tuple.get(selected));}});}
                b.functions.put(entry.getKey(),new Choice(entry.getValue(),entry.getValue()==0?independentClass:0,table));
            }
            for(Unit unit:units){work.run();b.unit(unit);}for(Row row:rows){work.run();b.row(row);}
            Map<Signature,Relation> relations=new LinkedHashMap<>();for(Map.Entry<Signature,Map<List<Integer>,Boolean>> entry:b.relations.entrySet()){work.run();relations.put(entry.getKey(),new Relation(false,entry.getValue()));}
            return new Model(points,other,relations,b.functions);
        }catch(TooWide skipped){return null;}
    }
}
