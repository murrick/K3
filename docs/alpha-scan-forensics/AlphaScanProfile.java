package org.kanger;
import java.util.*;

/** Diagnostic only: records stages without reading semantic objects. */
public final class AlphaScanProfile {
    private static Thread target;
    private static Map<String,Long> events;
    private static Map<String,long[]> tests;
    private static Map<String,Long> histogram;
    private static Deque<Scan> scans;
    private static final class Scan {long visits;String outcome="exception";}
    public static void reset(){target=Thread.currentThread();events=new TreeMap<>();tests=new TreeMap<>();histogram=new TreeMap<>();scans=new ArrayDeque<>();}
    public static void event(String name){if(Thread.currentThread()!=target)return;Long n=events.get(name);events.put(name,n==null?1:n+1);}
    public static boolean test(String name,boolean value){
        if(Thread.currentThread()==target){long[] n=tests.get(name);if(n==null){n=new long[2];tests.put(name,n);}n[value?1:0]++;}
        return value;
    }
    public static void enter(){if(Thread.currentThread()==target){event("calls");scans.push(new Scan());}}
    public static void visit(){if(Thread.currentThread()==target){event("visits");scans.peek().visits++;}}
    public static void finish(String outcome){if(Thread.currentThread()==target){event(outcome);scans.peek().outcome=outcome;}}
    public static void leave(){
        if(Thread.currentThread()!=target)return;
        Scan s=scans.pop();if(s.outcome.equals("exception"))event("exception");
        String key=s.outcome+":"+s.visits;Long n=histogram.get(key);histogram.put(key,n==null?1:n+1);
    }
    public static void report(int sample){
        if(!scans.isEmpty())throw new AssertionError("unfinished scan");target=null;
        for(Map.Entry<String,Long> e:events.entrySet())System.out.println("ALPHA_EVENT "+sample+" "+e.getKey()+" "+e.getValue());
        for(Map.Entry<String,long[]> e:tests.entrySet())System.out.println("ALPHA_TEST "+sample+" "+e.getKey()+" false="+e.getValue()[0]+" true="+e.getValue()[1]);
        for(Map.Entry<String,Long> e:histogram.entrySet())System.out.println("ALPHA_HIST "+sample+" "+e.getKey()+" "+e.getValue());
        events=null;tests=null;histogram=null;scans=null;
    }
}
