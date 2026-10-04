package org.kanger;
import java.util.*;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.units.*;

/** Diagnostic only. No public getters are used to inspect context identity. */
public final class DomainBindingProfile {
    private static Thread target;
    private static String caller;
    private static int depth;
    private static boolean sameDomain;
    private static Map<String,Record> records;
    private static final class Record {
        final long[] counts=new long[7];
        final IdentityHashMap<Domain,Boolean> domains=new IdentityHashMap<>();
    }
    public static void reset(){target=Thread.currentThread();caller=null;depth=0;records=new TreeMap<>();}
    public static Object select(IUnit unit,Mind mind,String site)throws Exception {
        if(Thread.currentThread()!=target)return unit.setMind(mind);
        String old=caller;caller=old==null?site:old+">"+site;
        try{return unit.setMind(mind);}finally{caller=old;}
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
        records=null;
    }
}
