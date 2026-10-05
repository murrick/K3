package org.kanger;
import java.util.*;
import org.kanger.units.Domain;
/** Diagnostic counters: no semantic getters or retained Domain objects. */
public final class ClassificationCounters {
    static final String[] NAMES={"excluded","calculated","candidates","assumed","stored"};
    static Thread target;
    static long[][] totals,peaks,classified,finals;
    public static void begin(){target=Thread.currentThread();totals=new long[5][8];peaks=new long[5][64];classified=new long[5][64];finals=new long[5][64];}
    public static Set<Domain> create(int group){return Thread.currentThread()==target?new CountedSet(group):new HashSet<Domain>();}
    static final class CountedSet extends HashSet<Domain> {
        final int group;int peak;
        CountedSet(int group){this.group=group;totals[group][0]++;}
        public boolean add(Domain d){totals[group][1]++;boolean result=super.add(d);if(result)totals[group][2]++;peak=Math.max(peak,super.size());return result;}
        public void clear(){totals[group][3]++;super.clear();}
        public boolean contains(Object d){totals[group][4]++;if(super.isEmpty())totals[group][5]++;return super.contains(d);}
        public Iterator<Domain> iterator(){totals[group][6]++;return super.iterator();}
        void checkpoint(){classified[group][Math.min(63,super.size())]++;}
        void finish(){totals[group][7]++;peaks[group][Math.min(63,peak)]++;finals[group][Math.min(63,super.size())]++;}
    }
    public static void checkpoint(Set<Domain>... sets){if(Thread.currentThread()==target)for(Set<Domain> s:sets)((CountedSet)s).checkpoint();}
    public static void complete(Set<Domain>... sets){if(Thread.currentThread()==target)for(Set<Domain> s:sets)((CountedSet)s).finish();}
    public static void finish(int sample){target=null;for(int g=0;g<5;g++){if(totals[g][0]!=totals[g][7])throw new AssertionError("created/completed mismatch");System.out.println("CLASSIFICATION sample="+sample+" group="+NAMES[g]+" totals="+Arrays.toString(totals[g])+" peaks="+Arrays.toString(peaks[g])+" classified="+Arrays.toString(classified[g])+" finals="+Arrays.toString(finals[g]));}}
}
