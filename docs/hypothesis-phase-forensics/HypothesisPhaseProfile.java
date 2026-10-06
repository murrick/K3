package org.kanger;

import java.lang.management.ManagementFactory;
import java.util.*;
import org.kanger.units.Rule;

/** Coarse diagnostic intervals; no original semantic getters are added. */
public final class HypothesisPhaseProfile {
    private static Thread target;
    private static java.lang.management.ThreadMXBean cpu;
    private static com.sun.management.ThreadMXBean allocation;
    private static long threadId, startWall, startCpu, startAllocation;
    private static Mark current;
    private static Candidate candidate;
    private static List<Candidate> candidates;
    private static Map<String,long[]> phases, core, passes;
    private static Set<String> candidateSources;
    private static Map<String,Long> events;
    public static final class Mark {
        final String label;
        final Mark parent;
        final long wall, cpu, allocation;
        Mark(String label,Mark parent){this.label=label;this.parent=parent;this.cpu=cpuTime();this.allocation=allocated();this.wall=System.nanoTime();}
    }
    private static final class Candidate {
        String source;
        boolean compiled, collision, accepted;
        Boolean answer;
        boolean replayed;
        final Map<String,long[]> phases=new TreeMap<>();
    }
    private static boolean active(){return Thread.currentThread()==target;}
    private static long cpuTime(){return cpu==null?-1:cpu.getCurrentThreadCpuTime();}
    private static long allocated(){return allocation==null?-1:allocation.getThreadAllocatedBytes(threadId);}
    private static long[] row(Map<String,long[]> rows,String label,int size){long[] r=rows.get(label);if(r==null){r=new long[size];rows.put(label,r);}return r;}
    public static void begin(){
        java.lang.management.ThreadMXBean bean=ManagementFactory.getThreadMXBean();
        cpu=bean.isCurrentThreadCpuTimeSupported()?bean:null;
        if(cpu!=null&&!cpu.isThreadCpuTimeEnabled())cpu.setThreadCpuTimeEnabled(true);
        allocation=bean instanceof com.sun.management.ThreadMXBean?(com.sun.management.ThreadMXBean)bean:null;
        if(allocation!=null){if(!allocation.isThreadAllocatedMemorySupported())allocation=null;else allocation.setThreadAllocatedMemoryEnabled(true);}
        target=Thread.currentThread();threadId=target.getId();current=null;candidate=null;
        candidates=new ArrayList<>();phases=new TreeMap<>();core=new TreeMap<>();passes=new TreeMap<>();events=new TreeMap<>();candidateSources=new HashSet<>();
        startCpu=cpuTime();startAllocation=allocated();startWall=System.nanoTime();
    }
    public static Mark start(String label){if(!active())return null;Mark m=new Mark(label,current);current=m;return m;}
    public static Mark startPass(String label){if(!active())return null;return start((current==null?"unscoped":current.label)+"/"+label);}
    public static void end(Mark m){
        if(m==null)return;
        long wall=System.nanoTime()-m.wall,ns=cpuTime(),bytes=allocated();
        if(current!=m)throw new AssertionError("Unbalanced phase");current=m.parent;
        long[] values={1,wall,ns<0?-1:ns-m.cpu,bytes<0?-1:bytes-m.allocation};
        accumulate(row(phases,m.label,4),values);
        if(candidate!=null)accumulate(row(candidate.phases,m.label,4),values);
    }
    private static void accumulate(long[] a,long[] b){for(int n=0;n<a.length;n++)a[n]+=b[n];}
    public static void event(String label,long n){if(active()){Long v=events.get(label);events.put(label,v==null?n:v+n);}}
    public static void core(int kind){if(active())row(core,current==null?"unscoped":current.label,5)[kind]++;}
    public static void beginCandidate(Object h){if(active()){candidate=new Candidate();candidates.add(candidate);event("candidate_class_"+h.getClass().getName(),1);}}
    public static void endCandidate(){if(active())candidate=null;}
    public static String source(String s){if(active()){candidate.source=s;event(candidateSources.add(s)?"unique_candidate_source":"duplicate_candidate_source",1);}return s;}
    public static Rule compiled(Rule r){if(active()){candidate.compiled=r!=null;event(r==null?"compile_null":"compile_rule",1);}return r;}
    public static boolean collision(boolean value){if(active()){candidate.collision=value;event(value?"collision_true":"collision_false",1);}return value;}
    public static Boolean answer(Boolean value,boolean expanded){if(active()){event((expanded?"expanded_answer_":"candidate_answer_")+(value==null?"unknown":value.toString()),1);if(!expanded){candidate.replayed=true;candidate.answer=value;}}return value;}
    public static Boolean passResult(Boolean value){if(active())row(passes,current.label,3)[value==null?2:value?0:1]++;return value;}
    public static void accepted(){if(active()){candidate.accepted=true;event("accepted",1);}}
    public static String mergeSource(String s,boolean expanded){event(expanded?"expanded_render":"original_render",1);return s;}
    public static boolean admission(boolean yes){event(yes?"expanded_unique_source":"expanded_duplicate_source",1);return yes;}
    public static void finish(int sample){
        long totalWall=System.nanoTime()-startWall,totalCpu=cpuTime()-startCpu,totalAllocation=allocated()-startAllocation;
        if(current!=null||candidate!=null)throw new AssertionError("Unbalanced optimization");
        target=null;
        System.out.println("HYPOTHESIS_TOTAL "+sample+" "+Arrays.toString(new long[]{totalWall,totalCpu,totalAllocation}));
        for(Map.Entry<String,Long> e:events.entrySet())System.out.println("HYPOTHESIS_EVENT "+sample+" "+e.getKey()+" "+e.getValue());
        for(Map.Entry<String,long[]> e:phases.entrySet())System.out.println("HYPOTHESIS_PHASE "+sample+" "+e.getKey()+" "+Arrays.toString(e.getValue()));
        for(Map.Entry<String,long[]> e:core.entrySet())System.out.println("HYPOTHESIS_CORE "+sample+" "+e.getKey()+" "+Arrays.toString(e.getValue()));
        for(Map.Entry<String,long[]> e:passes.entrySet())System.out.println("HYPOTHESIS_PASS "+sample+" "+e.getKey()+" "+Arrays.toString(e.getValue()));
        for(int n=0;n<candidates.size();n++){
            Candidate c=candidates.get(n);
            System.out.println("HYPOTHESIS_CANDIDATE "+sample+" "+n+" compiled="+c.compiled+" collision="+c.collision+" replayed="+c.replayed+" answer="+c.answer+" accepted="+c.accepted+" source="+c.source);
            for(Map.Entry<String,long[]> e:c.phases.entrySet())System.out.println("HYPOTHESIS_CANDIDATE_PHASE "+sample+" "+n+" "+e.getKey()+" "+Arrays.toString(e.getValue()));
        }
        candidates=null;phases=null;core=null;passes=null;events=null;candidateSources=null;
    }
}
