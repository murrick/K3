package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

/** Native probes of the existing candidate validation/replay protocol. */
public final class HypothesisPhaseWitness {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static Mind prepared(String source)throws Exception {
        User user=new User();new UDF().init(user);
        Mind base=(Mind)new Mind(user).clearWorkspace();
        check(base.compile(source),"base source compiles");return base;
    }
    private static Boolean evaluate(Mind child,String assumption,String query)throws Exception {
        Rule r=(Rule)child.compileLine(assumption,false,null);
        check(r!=null,"candidate compiles");child.link(r,false);
        check(!child.analyze(null,false),"candidate is consistent");
        return child.query(query,null,false);
    }
    private static List<Boolean> isolation(boolean reuse)throws Exception {
        Mind base=prepared("!@x seed(x) -> target(x);");
        String query="?target(item);";
        check(base.query(query,null,false)==null,"base result unknown");
        List<Boolean> answers=new ArrayList<>();
        if(reuse){
            Mind child=new Mind(base);
            try{
                answers.add(evaluate(child,"!seed(item);",query));
                answers.add(evaluate(child,"!unrelated(item);",query));
            }finally{base.release(child);}
        }else{
            for(String assumption:new String[]{"!seed(item);","!unrelated(item);"}){
                Mind child=new Mind(base);
                try{answers.add(evaluate(child,assumption,query));}
                finally{base.release(child);}
            }
        }
        check(base.query(query,null,false)==null,"released candidate does not enter base");
        return answers;
    }
    private static void truthStates()throws Exception {
        Mind base=prepared("!@x seed(x) -> target(x);");
        List<Boolean> answers=new ArrayList<>();
        for(String assumption:new String[]{"!seed(item);","!~target(item);","!unrelated(item);"}){
            Mind child=new Mind(base);
            try{answers.add(evaluate(child,assumption,"?target(item);"));}
            finally{base.release(child);}
        }
        check(answers.equals(Arrays.asList(Boolean.TRUE,Boolean.FALSE,null)),"three replay answers");
        check(answers.get(2)==null,"consistency alone does not establish relevance");
        System.out.println("TRUTH_STATES answers="+answers+" consistent=[true,true,true]");
    }
    private static void collision()throws Exception {
        Mind base=prepared("!~seed(item);");Mind child=new Mind(base);
        try{
            Rule r=(Rule)child.compileLine("!seed(item);",false,null);
            check(r!=null,"conflicting candidate compiles");child.link(r,false);
            check(child.analyze(null,false),"conflicting candidate fails validation");
        }finally{base.release(child);}
        System.out.println("COLLISION_GATE consistent=false");
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("hypothesis-phase-witness-").toString());
        List<Boolean> fresh=isolation(false),shared=isolation(true);
        check(fresh.equals(Arrays.asList(Boolean.TRUE,null)),"isolated candidate answers");
        check(shared.equals(Arrays.asList(Boolean.TRUE,Boolean.TRUE)),"shared child leaks first assumption");
        check(!fresh.equals(shared),"child reuse changes candidate decision");
        check(!Objects.equals(fresh.get(0),fresh.get(1)),"same query requires candidate state in cache key");
        System.out.println("CANDIDATE_ISOLATION fresh="+fresh+" reused="+shared);
        truthStates();collision();
        System.out.println("HYPOTHESIS_PHASE_WITNESS_OK checks="+checks);
    }
}
