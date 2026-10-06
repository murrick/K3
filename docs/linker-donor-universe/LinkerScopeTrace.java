package org.kanger;

import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

/** Native Rule-ID order observation only; never used to drive inference. */
public final class LinkerScopeTrace {
    private static final ThreadLocal<Session> CURRENT=new ThreadLocal<>();
    private static final class Session {int links;Deque<Integer> stack=new ArrayDeque<>();List<String> rows=new ArrayList<>();}
    public static void begin(){if(CURRENT.get()!=null)throw new AssertionError("nested trace");CURRENT.set(new Session());}
    public static void enter(Mind mind,Rule seed){Session s=CURRENT.get();if(s==null)return;if(mind.getClass()!=Mind.class)throw new AssertionError("native trace only");int id=++s.links;s.stack.push(id);s.rows.add("TRACE_LINK id="+id+" phase="+mind.getQueryPass()+" seed="+(seed==null?-1:seed.getId()));}
    public static void leave(){Session s=CURRENT.get();if(s!=null)s.stack.pop();}
    private static String ids(Collection<IRule> rules){StringBuilder b=new StringBuilder("[");for(IRule r:rules){if(b.length()>1)b.append(',');b.append(r.getId());}return b.append(']').toString();}
    public static void observe(int pass,String direction,Collection<IRule> execution,Collection<IRule> donors){Session s=CURRENT.get();if(s==null)return;String e=ids(execution),d=execution==donors?e:ids(donors);s.rows.add("TRACE_SCOPE link="+s.stack.peek()+" pass="+pass+" order="+direction+" execution="+e+" donors="+d);}
    public static void finish(int sample){Session s=CURRENT.get();CURRENT.remove();if(!s.stack.isEmpty())throw new AssertionError("trace stack open");System.out.println("TRACE_BEGIN sample="+sample+" links="+s.links);for(String row:s.rows)System.out.println(row);System.out.println("TRACE_END sample="+sample);}
}
