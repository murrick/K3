package org.kanger;

import java.util.*;
import org.kanger.interfaces.IMind;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.TVariable;

/** Diagnostic-only; the selected main thread owns all counters. */
public final class VariableTraversalProfile {
    private static Thread target;
    private static String route;
    private static int depth;
    private static long outsideDeletion;
    private static Map<String,long[]> stamps;
    private static Map<String,Record> records;
    private static final class Record {
        final long[] counts = new long[8];
        final IdentityHashMap<ArgumentsList,Boolean> lists = new IdentityHashMap<>();
    }
    public static void reset() {
        target=Thread.currentThread();route=null;depth=0;outsideDeletion=0;
        records=new TreeMap<>();stamps=new TreeMap<>();
    }
    public static List<TVariable> variables(ArgumentsList list, IMind mind, String site) throws Exception {
        if (Thread.currentThread()!=target) return list.getTVariables(mind);
        String previous=route;
        if (depth==0) route=site;
        Record r=records.get(route);
        if(r==null){r=new Record();records.put(route,r);}
        r.counts[0]++;
        r.counts[depth==0?1:2]++;
        r.lists.put(list,Boolean.TRUE);
        depth++;
        try {
            List<TVariable> result=list.getTVariables(mind);
            if(result.getClass()==ArrayList.class){
                int size=result.size();
                r.counts[5]+=size;
                r.counts[6]=Math.max(r.counts[6],size);
                if(size==0)r.counts[7]++;
            }
            return result;
        } finally {depth--;route=previous;}
    }
    public static void argument() {
        if(Thread.currentThread()==target && depth>0)records.get(route).counts[3]++;
    }
    public static void deletion() {
        if(Thread.currentThread()!=target)return;
        if(depth>0)records.get(route).counts[4]++;
        else outsideDeletion++;
    }
    public static void stamp(String site, int position) {
        if(Thread.currentThread()!=target)return;
        long[] counts=stamps.get(site);
        if(counts==null){counts=new long[2];stamps.put(site,counts);}
        counts[position]++;
    }
    public static void report(int sample) {
        target=null;
        for(Map.Entry<String,Record> e:records.entrySet()) {
            Record r=e.getValue();
            System.out.println("VARIABLE_ROUTE "+sample+" "+e.getKey()+" "+Arrays.toString(r.counts)+" unique_lists="+r.lists.size());
        }
        System.out.println("VARIABLE_OUTSIDE_DELETION "+sample+" "+outsideDeletion);
        for(Map.Entry<String,long[]> e:stamps.entrySet())
            System.out.println("STAMP_ROUTE "+sample+" "+e.getKey()+" "+Arrays.toString(e.getValue()));
        records=null;stamps=null;
    }
}
