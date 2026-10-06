package org.kanger;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.HypothesisStore;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

/** Native exact candidate decision oracle; production optimizer is unchanged. */
public final class ExactCandidateReplayRunner {
    private static int checks;
    private static final class Candidate {
        final IHypothesis hypothesis;final String fixed;
        Candidate(IHypothesis h){hypothesis=h;fixed=null;}
        Candidate(String s){hypothesis=null;fixed=s;}
        String source(Mind m)throws Exception{return hypothesis==null?fixed:((Hypothesis)hypothesis).toString(m);}
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

        List<String> accepted=new ArrayList<>();
        {
            int index=0;
            for(Candidate c:pool){
                Mind m=new Mind(base);String source=null;boolean compiled=false,collision=false;Boolean answer=null;

                try{
                    source=c.source(m);
                    Rule r=(Rule)m.compileLine(source,false,null);compiled=r!=null;
                    if(compiled){
                        m.link(r,false);collision=m.analyze(null,false);
                        if(!collision){
                            answer=m.query(replay.getSource(),replay.getExternals(),false);
                        }
                    }
                    boolean admit=compiled&&!collision&&answer!=null;
                    if(admit)accepted.add(source);
                    String encoded=Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
                    System.out.println("ROW "+label+" "+index+" compiled="+compiled+" collision="+collision+" answer="+answer+" accepted="+admit+" source="+encoded);
                }finally{base.release(m);check(base.pendingTransactionCount()==0,"validation child settled");}
                ++index;
            }
        }
        check(base.pendingTransactionCount()==0,"candidate contexts settled");
        check(base.query(query,externals,false)==null,"base remains unknown");
        check(texts(base).equals(raw),"base RAW unchanged after forks");
        check(base.pendingTransactionCount()==0,"final base query settled");
        Collections.sort(accepted);
        System.out.println("ACCEPTED "+label+" "+accepted);
        System.out.println("CASE_OK "+label+" rows="+pool.size()+" accepted="+accepted.size()+" raw="+raw.size()+" reservations=0");
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("exact-candidate-study-").toString());
        String mode=args[0],cases=args[1];
        check(mode.equals("exact"),"study mode");
        if(cases.equals("small")||cases.equals("all")){
            run(base("!@x seed(x) -> target(x);"),"small","?target(item);",null,false,mode);
            Mind ext=base("!@x seed(x) -> target(x);");
            run(ext,"external-item","?target(?);",new Object[]{"item"},false,mode);
            run(ext,"external-other","?target(?);",new Object[]{"other"},false,mode);
        }
        if(cases.equals("son")||cases.equals("all")){
            String source=new String(Files.readAllBytes(Paths.get("natives.k")),StandardCharsets.UTF_8);
            run(base(source),"son","?$x son(John,x);",null,true,mode);
        }
        System.out.println("EXACT_CANDIDATE_REPLAY_OK mode="+mode+" checks="+checks);
    }
}
