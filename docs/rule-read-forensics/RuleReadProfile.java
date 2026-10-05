package org.kanger;
import java.util.*;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.units.*;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Solve;

/** Diagnostic counters only; every original virtual read and setter executes. */
public final class RuleReadProfile {
    private static Thread target;
    private static String caller;
    private static int depth;
    private static boolean sameDomain;
    private static Map<String,Record> records;
    private static Map<String,Read> reads;
    private static final class Record {
        final long[] counts=new long[7];
        final IdentityHashMap<Domain,Boolean> domains=new IdentityHashMap<>();
    }
    private static final class Read {
        long count;
        final IdentityHashMap<Rule,Boolean> rules=new IdentityHashMap<>();
        final Map<String,Long> stacks=new TreeMap<>();
    }
    public static void reset(){target=Thread.currentThread();caller=null;depth=0;records=new TreeMap<>();reads=new TreeMap<>();}
    public static Rule lookup(RuleFactory factory,long id,String site)throws Exception {
        if(Thread.currentThread()!=target)return factory.get(id);
        String old=caller;caller=old==null?site:old+">"+site;
        try{return factory.get(id);}finally{caller=old;}
    }
    public static IRule find(RuleFactory factory,Solve solve,String site)throws Exception {
        if(Thread.currentThread()!=target)return factory.find(solve);
        String old=caller;caller=old==null?site:old+">"+site;
        try{return factory.find(solve);}finally{caller=old;}
    }
    public static <T> Iterable<T> iterate(final Iterable<T> source,final String site){
        return new Iterable<T>(){
            public Iterator<T> iterator(){
                final Iterator<T> raw=source.iterator();
                return new Iterator<T>(){
                    public boolean hasNext(){return raw.hasNext();}
                    public void remove(){raw.remove();}
                    public T next(){
                        if(Thread.currentThread()!=target)return raw.next();
                        String old=caller;caller=old==null?site:old+">"+site;
                        try{return raw.next();}finally{caller=old;}
                    }
                };
            }
        };
    }
    public static Object read(IStep step,Mind mind,String site)throws Exception {
        if(Thread.currentThread()!=target)return step.getData(mind);
        String old=caller;caller=old==null?site:old+">"+site;
        try{return step.getData(mind);}finally{caller=old;}
    }
    public static Object select(IUnit unit,Mind mind,String site)throws Exception {
        if(Thread.currentThread()!=target)return unit.setMind(mind);
        String old=caller;caller=old==null?site:old+">"+site;
        try{
            if(unit instanceof Rule && site.startsWith("Step:")){
                Read r=reads.get(caller);if(r==null){r=new Read();reads.put(caller,r);}
                r.count++;r.rules.put((Rule)unit,Boolean.TRUE);
                if((r.count&1023)==1){
                    StringBuilder stack=new StringBuilder();
                    for(StackTraceElement f:new Throwable().getStackTrace())
                        if(f.getClassName().startsWith("org.kanger.") && !f.getClassName().equals(RuleReadProfile.class.getName()) && !f.getClassName().equals("org.kanger.RuleReadProfileRunner")){
                            if(stack.length()>0)stack.append("<");stack.append(f.toString());
                        }
                    String key=stack.toString();Long n=r.stacks.get(key);r.stacks.put(key,n==null?1:n+1);
                }
            }
            return unit.setMind(mind);
        }finally{caller=old;}
    }
    private static Record record(){
        String key=caller==null?"unattributed":caller;
        Record r=records.get(key);if(r==null){r=new Record();records.put(key,r);}return r;
    }
    public static void enter(Domain domain,Mind before,Mind requested){
        if(Thread.currentThread()!=target)return;
        if(depth!=0)throw new AssertionError("unexpected nested Domain.setMind");
        depth++;sameDomain=before==requested;
        Record r=record();r.counts[0]++;r.counts[sameDomain?1:2]++;r.domains.put(domain,Boolean.TRUE);
    }
    public static void leave(){if(Thread.currentThread()==target)depth--;}
    public static boolean inspectVariable(){return Thread.currentThread()==target && depth>0;}
    public static void variable(TVariable variable,Mind before,Mind requested){
        Record r=record();r.counts[3]++;r.counts[before==requested?4:5]++;
        if(sameDomain && before!=requested)r.counts[6]++;
    }
    public static void report(int sample){
        target=null;
        for(Map.Entry<String,Record> e:records.entrySet())
            System.out.println("BINDING_ROUTE "+sample+" "+e.getKey()+" "+Arrays.toString(e.getValue().counts)+" unique_domains="+e.getValue().domains.size());
        for(Map.Entry<String,Read> e:reads.entrySet()){
            Read r=e.getValue();System.out.println("RULE_READ "+sample+" "+e.getKey()+" count="+r.count+" unique_rules="+r.rules.size());
            for(Map.Entry<String,Long> s:r.stacks.entrySet())System.out.println("RULE_STACK "+sample+" "+e.getKey()+" samples="+s.getValue()+" "+s.getKey());
        }
        records=null;reads=null;
    }
}
