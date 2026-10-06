package org.kanger;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.HypothesisStore;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

/** Shadow algorithm study; production optimizer is unchanged. */
public final class PreparedQueryReplayStudy {
    private static int checks;
    private static final class Candidate {
        final IHypothesis hypothesis;final String fixed;
        Candidate(IHypothesis h){hypothesis=h;fixed=null;}
        Candidate(String s){hypothesis=null;fixed=s;}
        String source(Mind m)throws Exception{return hypothesis==null?fixed:((Hypothesis)hypothesis).toString(m);}
    }
    private static final class Branch {
        boolean nullRule,before;Boolean after;
        boolean collision(){return before||Boolean.TRUE.equals(after);}
        String text(){return "null="+nullRule+",before="+before+",after="+after;}
    }
    private static final class Prepared implements AutoCloseable {
        final Mind base;
        Mind negative,positive;
        Prepared(Mind base,QueryReplayContext.Snapshot query,boolean close)throws Exception {
            this.base=base;
            try{
                negative=prepare(base,query,false,close);
                positive=prepare(base,query,true,close);
                check(base.pendingTransactionCount()==2,"two prepared query reservations");
            }catch(Exception e){close();throw e;}
        }
        private static Mind prepare(Mind base,QueryReplayContext.Snapshot snapshot,boolean positive,boolean closure)throws Exception {
            Mind q=new Mind(base);boolean ready=false;
            try{
                q.setQueryPass(positive?QueryPass.CHECKTRUE:QueryPass.CHECKFALSE);
                String source=positive?snapshot.getSource():(String)invoke(Mind.class,q,"invert",new Class<?>[]{String.class},snapshot.getSource());
                Queue<ITerm> ext=(Queue<ITerm>)invoke(Mind.class,q,"convertExternals",new Class<?>[]{Object[].class},(Object)snapshot.getExternals());
                Rule r=(Rule)q.compileLine(source,true,ext);
                check(r!=null&&!r.isSecond(),"prepared query compiles as new rule");
                check(!q.analyze(r,false),"prepared query consistent before closure");
                if(closure){q.link(r,false);check(!q.analyze(r,false),"prepared query consistent after closure");}
                ready=true;return q;
            }finally{if(!ready)base.release(q);}
        }
        Branch branch(String source,boolean positive,boolean full)throws Exception {
            Mind parent=positive?this.positive:negative;
            Mind child=new Mind(parent);
            try{
                child.setQueryPass(positive?QueryPass.CHECKTRUE:QueryPass.CHECKFALSE);
                Rule r=(Rule)child.compileLine(source,false,null);
                Branch answer=new Branch();answer.nullRule=r==null;
                answer.before=child.analyze(null,false);
                if(!answer.before&&(r!=null||full)){
                    child.link(full?null:r,false);
                    answer.after=child.analyze(null,false);
                }
                return answer;
            }finally{parent.release(child);check(parent.pendingTransactionCount()==0,"candidate fork settled");}
        }
        public void close()throws Exception {
            if(positive!=null){Mind q=positive;positive=null;base.release(q);}
            if(negative!=null){Mind q=negative;negative=null;base.release(q);}
        }
    }
    private static Object invoke(Class<?> owner,Object target,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=owner.getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static Mind base(String source)throws Exception {
        User u=new User();new UDF().init(u);Mind m=(Mind)new Mind(u).clearWorkspace();
        check(m.compile(source),"program compiles");check(m.pendingTransactionCount()==0,"program settled");return m;
    }
    private static List<String> texts(Mind m)throws Exception {
        List<String> rows=new ArrayList<>();for(IHypothesis h:m.getHypothesis())rows.add(((Hypothesis)h).toString(m));Collections.sort(rows);return rows;
    }
    private static List<Candidate> expandedPool(Mind base,QueryReplayContext.Snapshot replay)throws Exception {
        Mind expanded=new Mind(base);
        try{
            expanded.includeAbstractiveHypothesis(true);
            check(expanded.query(replay.getSource(),replay.getExternals(),false)==null,"expanded query unknown");
            invoke(HypothesisStore.class,base.getHypothesis(),"mergeExpanded",new Class<?>[]{Mind.class},expanded);
        }finally{base.release(expanded);}
        List<Candidate> rows=new ArrayList<>();for(IHypothesis h:base.getHypothesis())rows.add(new Candidate(h));return rows;
    }
    private static void run(Mind base,String label,String query,Object[] externals,boolean son,String mode)throws Exception {
        check(base.query(query,externals)==null,"initial query unknown");
        List<String> raw=texts(base);
        check(base.pendingTransactionCount()==0,"initial query settled");
        QueryReplayContext.Snapshot replay=QueryReplayContext.snapshot(base);
        check(replay!=null&&replay.getSource().equals(query),"replay source captured");
        List<Candidate> pool=son?expandedPool(base,replay):Arrays.asList(new Candidate("!seed(item);"),new Candidate("!~target(item);"),new Candidate("!unrelated(item);"));
        if(son)check(raw.size()==18&&pool.size()==47,"SON candidate cardinalities");
        Integer focus=Integer.getInteger("study.focusIndex");
        if(focus!=null)pool=Collections.singletonList(pool.get(focus));
        if(Boolean.getBoolean("study.reverse"))Collections.reverse(pool);
        List<String> output=new ArrayList<>();
        boolean measure=Boolean.getBoolean("study.measure");
        java.lang.management.ThreadMXBean cpu=java.lang.management.ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocation=(com.sun.management.ThreadMXBean)cpu;
        if(measure)allocation.setThreadAllocatedMemoryEnabled(true);
        long cpuBefore=measure?cpu.getCurrentThreadCpuTime():0;
        long allocationBefore=measure?allocation.getThreadAllocatedBytes(Thread.currentThread().getId()):0;
        long wallBefore=measure?System.nanoTime():0;
        Prepared prepared=mode.equals("exact")?null:new Prepared(base,replay,!mode.equals("compiled"));
        List<String> accepted=new ArrayList<>();
        try{
            int index=0;
            for(Candidate c:pool){
                Mind m=new Mind(base);String source=null;boolean compiled=false,collision=false;Boolean answer=null;
                Branch negative=null,positive=null;
                try{
                    source=c.source(m);
                    Rule r=(Rule)m.compileLine(source,false,null);compiled=r!=null;
                    if(compiled){
                        m.link(r,false);collision=m.analyze(null,false);
                        if(!collision){
                            if(prepared==null)answer=m.query(replay.getSource(),replay.getExternals(),false);
                            else{
                                negative=prepared.branch(source,false,mode.equals("full"));
                                if(negative.collision())answer=false;
                                else{
                                    positive=prepared.branch(source,true,mode.equals("full"));
                                    if(positive.collision())answer=true;
                                }
                            }
                        }
                    }
                    boolean admit=compiled&&!collision&&answer!=null;
                    if(admit)accepted.add(source);
                    String encoded=Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
                    output.add("ROW "+label+" "+index+" compiled="+compiled+" collision="+collision+" answer="+answer+" accepted="+admit+" negative="+(negative==null?"skipped":negative.text())+" positive="+(positive==null?"skipped":positive.text())+" source="+encoded);
                }finally{base.release(m);check(base.pendingTransactionCount()==(prepared==null?0:2),"validation child settled");}
                ++index;
            }
        }finally{if(prepared!=null)prepared.close();}
        long wall=measure?System.nanoTime()-wallBefore:0;
        long cpuNs=measure?cpu.getCurrentThreadCpuTime()-cpuBefore:0;
        long allocated=measure?allocation.getThreadAllocatedBytes(Thread.currentThread().getId())-allocationBefore:0;
        for(String row:output)System.out.println(row);
        if(measure)System.out.println("TIMING "+label+" wall_ns="+wall+" cpu_ns="+cpuNs+" allocation_bytes="+allocated);
        check(base.pendingTransactionCount()==0,"prepared query contexts settled");
        check(base.query(query,externals,false)==null,"base remains unknown");
        check(texts(base).equals(raw),"base RAW unchanged after forks");
        check(base.pendingTransactionCount()==0,"final base query settled");
        Collections.sort(accepted);
        System.out.println("ACCEPTED "+label+" "+accepted);
        System.out.println("CASE_OK "+label+" rows="+pool.size()+" accepted="+accepted.size()+" raw="+raw.size()+" reservations=0");
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("prepared-query-study-").toString());
        String mode=args[0],cases=args[1];
        check(Arrays.asList("exact","prepared","compiled","full").contains(mode),"study mode");
        if(cases.equals("small")||cases.equals("all")){
            run(base("!@x seed(x) -> target(x);"),"small","?target(item);",null,false,mode);
            Mind ext=base("!@x seed(x) -> target(x);");
            run(ext,"external-item","?target(?);",new Object[]{"item"},false,mode);
            run(ext,"external-other","?target(?);",new Object[]{"other"},false,mode);
        }
        if(cases.equals("son")||cases.equals("all")){
            String source=new String(Files.readAllBytes(Paths.get("natives.k")),StandardCharsets.UTF_8);
            Mind son=base(source);
            int samples=Integer.getInteger("study.samples",1);
            for(int sample=0;sample<samples;sample++)run(son,samples==1?"son":"son-"+sample,"?$x son(John,x);",null,true,mode);
        }
        System.out.println("PREPARED_QUERY_STUDY_OK mode="+mode+" checks="+checks);
    }
}
