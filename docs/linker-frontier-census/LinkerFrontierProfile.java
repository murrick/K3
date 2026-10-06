package org.kanger;

import java.lang.management.*;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Cause;
import org.kanger.units.Rule;

/** Observation only. Counts do not define a valid semantic dirty signature. */
public final class LinkerFrontierProfile {
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static Session session;
    private static final class Session {int links;String order;Frame frame;List<String> rows=new ArrayList<>();}
    private static final class Frame {int id,pass;String phase;Map<String,long[]> groups=new LinkedHashMap<>();Map<String,long[]> examples=new LinkedHashMap<>();}
    public static final class Mark {long cpu;long[] before;Frame frame;String key;long ruleId;}
    public static void begin(){if(session!=null)throw new AssertionError("nested profile");session=new Session();}
    public static void finish(int sample){
        Session s=session;session=null;System.out.println("FRONTIER_BEGIN sample="+sample+" links="+s.links);
        for(String row:s.rows)System.out.println(row);
        System.out.println("FRONTIER_END sample="+sample);
    }
    public static Object beginLink(Mind mind,Rule seed){
        if(session==null)return null;
        if(mind.getClass()!=Mind.class)throw new AssertionError("native Mind required");
        if(session.frame!=null)throw new AssertionError("nested Linker unexpected");
        Frame f=new Frame();f.id=++session.links;f.phase=mind.getQueryPass().toString();session.frame=f;
        session.rows.add("LINK id="+f.id+" phase="+f.phase+" seed="+(seed==null?-1:seed.getId()));return f;
    }
    public static void endLink(Object token){
        if(token==null)return;Frame f=(Frame)token;
        for(Map.Entry<String,long[]> e:f.groups.entrySet()){
            long[] a=e.getValue();StringBuilder b=new StringBuilder("FRONTIER link="+f.id+" phase="+f.phase+" "+e.getKey());
            String[] names={"rules","cpu_ns","rotations","pairs","unifications","tvalue_attempts","tsolves","used_delta","excluded_delta","calculated_delta","causes_delta","hypothesis_delta","action_transitions","without_tvalue_attempts_with_state_change","no_counted_change"};
            for(int i=0;i<a.length;i++)b.append(' ').append(names[i]).append('=').append(a[i]);
            session.rows.add(b.toString());
        }
        for(Map.Entry<String,long[]> e:f.examples.entrySet())session.rows.add("FRONTIER_WITNESS link="+f.id+" "+e.getKey()+" rule="+e.getValue()[0]+" tsolve_delta="+e.getValue()[1]+" excluded_delta="+e.getValue()[2]+" used_delta="+e.getValue()[3]+" causes_delta="+e.getValue()[4]);
        session.frame=null;
    }
    public static void order(int pass,String order){if(session!=null){session.frame.pass=pass;session.order=order;}}
    private static long count(Map<?,? extends Collection<?>> map){long n=0;for(Collection<?> c:map.values())n+=c.size();return n;}
    private static int actions(Mind m){return (m.getRules().isAction()?1:0)|(m.getTValues().isAction()?2:0)|(m.getFValues().isAction()?4:0)|(m.getHypothesis().isAction()?8:0)|(m.getTempHypothesis().isAction()?16:0);}
    private static long[] snapshot(Mind m,LinkerStatistics s,Map<IRule,Set<Cause>> causes){return new long[]{s.getTerminalRotations(),s.getDomainPairs(),s.getUnificationAttempts(),s.getNewTValues(),m.ruleSolvesVersion(),count(m.getUsedDomains()),count(m.getExcludedDomains()),count(m.getCalculatedDomains()),count(causes),m.getHypothesis().size()+m.getTempHypothesis().size(),actions(m)};}
    public static Mark start(Mind m,LinkerStatistics s,Map<IRule,Set<Cause>> causes,IRule rule){
        if(session==null)return null;Mark mark=new Mark();mark.frame=session.frame;mark.key="pass="+mark.frame.pass+" order="+session.order;mark.ruleId=rule.getId();mark.before=snapshot(m,s,causes);mark.cpu=CPU.getCurrentThreadCpuTime();return mark;
    }
    public static void end(Mark mark,Mind m,LinkerStatistics s,Map<IRule,Set<Cause>> causes){
        if(mark==null)return;long elapsed=CPU.getCurrentThreadCpuTime()-mark.cpu;long[] now=snapshot(m,s,causes),delta=new long[now.length];
        for(int i=0;i<delta.length;i++)delta[i]=now[i]-mark.before[i];
        long[] a=mark.frame.groups.get(mark.key);if(a==null){a=new long[15];mark.frame.groups.put(mark.key,a);}a[0]++;a[1]+=elapsed;
        for(int i=0;i<10;i++)a[i+2]+=delta[i];a[12]+=now[10]!=mark.before[10]?1:0;
        boolean change=now[10]!=mark.before[10];for(int i=3;i<10;i++)change|=delta[i]!=0;
        if(delta[3]==0&&change){a[13]++;if(!mark.frame.examples.containsKey(mark.key))mark.frame.examples.put(mark.key,new long[]{mark.ruleId,delta[4],delta[6],delta[5],delta[8]});}
        if(!change)a[14]++;
    }
}
