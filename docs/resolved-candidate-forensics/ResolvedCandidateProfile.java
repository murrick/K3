package org.kanger;

import java.util.*;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.IRule;
import org.kanger.units.*;

/** Diagnostic only: records values already evaluated by the original path. */
public final class ResolvedCandidateProfile {
    public static final String[] COLUMNS = {
        "select_calls", "multiple_domain", "empty_bucket", "resolved_calls", "resolved_ids",
        "final_get", "final_null", "final_deleted", "returned_rules", "empty_resolved",
        "bucket_visits", "filtered_rules", "resolved_unique_ids", "resolved_ids_outside_bucket",
        "local_collect", "signature_ids", "position_ids", "batch_eligible", "memo_hit", "memo_miss",
        "batch_get", "batch_null", "batch_removed", "rule_bind", "domain_bind", "variable_bind",
        "final_repeated_object", "batch_repeated_object", "factory_exact", "select_exception",
        "find_exception", "link_candidate_visits", "domain_pairs", "unifications"
    };
    private static Thread target;
    private static long[] c;
    private static int stage;
    private static long[][] bindings;
    private static Frame frame;
    private static final IdentityHashMap<Rule,Boolean>[] reads = new IdentityHashMap[]{new IdentityHashMap<Rule,Boolean>(),new IdentityHashMap<Rule,Boolean>()};
    private static IdentityHashMap<Domain,Boolean> sources;
    private static Map<String,Long> lengths;
    private static Map<Integer,Long> depths;
    private static int depth;
    public static final class Frame {
        final Frame parent;
        final Set<Long> allowed = new HashSet<>();
        final Set<Long> matched = new HashSet<>();
        long bucket, ids, returned, filtered;
        boolean completed;
        Frame(Frame parent){this.parent=parent;}
    }
    public static void begin(){target=Thread.currentThread();c=new long[COLUMNS.length];bindings=new long[2][3];stage=0;frame=null;depth=0;reads[0].clear();reads[1].clear();sources=new IdentityHashMap<>();lengths=new TreeMap<>();depths=new TreeMap<>();}
    private static boolean active(){return Thread.currentThread()==target;}
    public static void add(int i,long n){if(active())c[i]+=n;}
    public static Frame select(){if(!active())return null;add(0,1);Frame f=new Frame(frame);frame=f;return f;}
    public static void selected(){if(active())frame.completed=true;}
    public static void end(Frame f){if(f==null)return;if(!f.completed)add(29,1);if(f.completed && f.bucket>0){add(13,f.allowed.size()-f.matched.size());String k=f.bucket+","+f.ids+","+f.returned+","+f.filtered;Long n=lengths.get(k);lengths.put(k,n==null?1:n+1);}frame=f.parent;}
    public static void bucket(long size,Domain source){if(active()){frame.bucket=size;sources.put(source,Boolean.TRUE);}}
    public static void ids(long size){add(4,size);if(active()&&frame!=null)frame.ids+=size;}
    public static long allowed(long id){if(active()){if(frame.allowed.add(id))add(12,1);}return id;}
    public static long bucketId(long id){add(10,1);if(active()&&frame.allowed.contains(id))frame.matched.add(id);return id;}
    public static void filtered(){add(11,1);if(active())frame.filtered++;}
    public static void returned(){add(8,1);if(active()&&frame!=null)frame.returned++;}
    public static Rule lookup(RuleFactory factory,long id,int kind)throws Exception {
        if(!active())return factory.get(id);
        int old=stage;stage=kind;
        add(kind==1?5:20,1);
        try{Rule r=factory.get(id);if(r==null)add(kind==1?6:21,1);else {IdentityHashMap<Rule,Boolean> seen=reads[kind-1];if(seen.put(r,Boolean.TRUE)!=null)add(kind==1?26:27,1);}return r;}
        finally{stage=old;}
    }
    public static void binding(int kind){if(active()&&stage!=0){add(23+kind,1);bindings[stage-1][kind]++;}}
    public static void local(int kind,long n){add(kind,n);}
    public static void enterCollect(){if(active()){++depth;Long n=depths.get(depth);depths.put(depth,n==null?1:n+1);}}
    public static void leaveCollect(){if(active())--depth;}
    public static void finish(int sample){
        if(frame!=null||stage!=0||depth!=0)throw new AssertionError("Unbalanced diagnostic scope");
        target=null;
        for(int n=0;n<2;n++)System.out.println("RESOLVED_BINDINGS "+sample+" "+(n+1)+" "+Arrays.toString(bindings[n]));
        System.out.println("RESOLVED_COUNTS "+sample+" "+Arrays.toString(c));
        System.out.println("RESOLVED_IDENTITIES "+sample+" final_rules="+reads[0].size()+" batch_rules="+reads[1].size()+" sources="+sources.size());
        for(Map.Entry<String,Long> e:lengths.entrySet())System.out.println("RESOLVED_LENGTHS "+sample+" "+e.getKey()+" "+e.getValue());
        for(Map.Entry<Integer,Long> e:depths.entrySet())System.out.println("RESOLVED_DEPTH "+sample+" "+e.getKey()+" "+e.getValue());
        reads[0].clear();reads[1].clear();sources=null;lengths=null;depths=null;
    }
}
