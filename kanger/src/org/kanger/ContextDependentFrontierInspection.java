/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextProductionCapture.Source;

/** Detached finite frontier comparison. No hash/text equality, saturation or admission authority. */
public final class ContextDependentFrontierInspection {
    private ContextDependentFrontierInspection(){}
    public static final class Snapshot {
        public final List<Row> common;
        public final Map<String,List<Row>> pools;
        private final Map<UUID,String> basis;
        private final Map<String,Set<UUID>> scopes;
        private Snapshot(List<Row> common,Map<String,List<Row>> pools,Map<UUID,String> basis,Map<String,Set<UUID>> scopes){this.common=Collections.unmodifiableList(new ArrayList<>(common));this.pools=Collections.unmodifiableMap(new LinkedHashMap<>(pools));this.basis=basis;this.scopes=scopes;}
    }
    public static final class Comparison {
        public final boolean changed;
        public final int newOrImproved,removedOrWorsened;
        private Comparison(int added,int removed){newOrImproved=added;removedOrWorsened=removed;changed=added!=0||removed!=0;}
    }
    private static final class Pair {
        final TermView left,right;
        Pair(TermView left,TermView right){this.left=left;this.right=right;}
    }
    private static final class Budget {
        final Runnable work;final int capacity;int rows;
        Budget(int maximumWork,int capacity){if(capacity<1||capacity>100000)throw new IllegalArgumentException("Bounded whole frontier required");work=ContextGroundProductionRounds.budget(maximumWork);this.capacity=capacity;}
        void row(){work.run();if(rows==capacity)throw new IllegalStateException("Whole frontier representative limit");rows++;}
    }
    private static boolean conclusion(Row a,Row b,Runnable work){
        work.run();if(a.positive!=b.positive||!a.predicate.equals(b.predicate)||a.arguments.size()!=b.arguments.size())return false;
        Map<Integer,Integer> forward=new HashMap<>(),back=new HashMap<>();Deque<Pair> pending=new ArrayDeque<>();for(int i=0;i<a.arguments.size();i++)pending.push(new Pair(a.arguments.get(i),b.arguments.get(i)));
        while(!pending.isEmpty()){
            work.run();Pair pair=pending.pop();TermView x=pair.left,y=pair.right;if(x.kind!=y.kind)return false;
            if(x.kind==Kind.VARIABLE){Integer f=forward.get(x.variable),r=back.get(y.variable);if(f==null&&r==null){forward.put(x.variable,y.variable);back.put(y.variable,x.variable);}else if(f==null||r==null||f!=y.variable||r!=x.variable)return false;}
            else if(x.kind==Kind.VALUE){if(!x.value.semanticallyEquals(y.value))return false;}
            else {if(x.binder!=y.binder||!x.symbolSource.equals(y.symbolSource)||x.arguments.size()!=y.arguments.size())return false;for(int i=0;i<x.arguments.size();i++){work.run();pending.push(new Pair(x.arguments.get(i),y.arguments.get(i)));}}
        }
        return true;
    }
    /** Alpha equivalence of the signed conclusion alone, including exact physical witness identity. */
    public static boolean sameConclusion(Row a,Row b,int maximumWork){if(a==null||b==null)throw new IllegalArgumentException("Whole detached rows required");return conclusion(a,b,ContextGroundProductionRounds.budget(maximumWork));}
    private static boolean support(Row a,Row b,Runnable work){work.run();if(a.sources.size()!=b.sources.size())return false;for(Source source:a.sources){work.run();if(!b.sources.contains(source))return false;}return true;}
    private static int find(List<Row> rows,Row row,Runnable work){for(int i=0;i<rows.size();i++){work.run();Row old=rows.get(i);if(support(old,row,work)&&conclusion(old,row,work))return i;}return -1;}
    private static void add(List<Row> rows,Row row,Budget budget){budget.work.run();int at=find(rows,row,budget.work);if(at<0){budget.row();rows.add(row);}else if(row.depth<rows.get(at).depth)rows.set(at,row);}
    /** Canonical next-input pools, including products exported at this barrier but not yet received. */
    public static Snapshot inspect(ContextDependentRoundInspection.Barrier barrier,int maximumWork,int capacity){
        if(barrier==null)throw new IllegalArgumentException("Whole checked barrier required");Budget budget=new Budget(maximumWork,capacity);List<Row> common=new ArrayList<>();
        for(ContextDependentRoundInspection.Ticket ticket:barrier.common){budget.work.run();Row row=barrier.observed.get(ticket);if(row==null)throw new IllegalStateException("Missing complete common proof occurrence");add(common,row,budget);}
        Map<String,List<Row>> pools=new LinkedHashMap<>();for(Map.Entry<String,Result> entry:barrier.cuts.entrySet()){budget.work.run();List<Row> rows=new ArrayList<>();for(Row row:common)add(rows,row,budget);for(Row row:entry.getValue().products)add(rows,row,budget);pools.put(entry.getKey(),Collections.unmodifiableList(rows));}
        return new Snapshot(common,pools,barrier.basis,barrier.scopes);
    }
    private static int additions(List<Row> before,List<Row> after,Runnable work){int added=0;for(Row row:after){work.run();int at=find(before,row,work);if(at<0||row.depth<before.get(at).depth)added++;}return added;}
    /** Different original bases or physical scope manifests refuse instead of comparing empty pools equal. */
    public static Comparison compare(Snapshot before,Snapshot after,int maximumWork){
        if(before==null||after==null||!before.basis.equals(after.basis)||!before.scopes.equals(after.scopes)||!before.pools.keySet().equals(after.pools.keySet()))throw new IllegalArgumentException("Same complete physical basis and cut scopes required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);int added=additions(before.common,after.common,work),removed=additions(after.common,before.common,work);
        for(String cut:before.pools.keySet()){work.run();added+=additions(before.pools.get(cut),after.pools.get(cut),work);removed+=additions(after.pools.get(cut),before.pools.get(cut),work);}return new Comparison(added,removed);
    }
}
