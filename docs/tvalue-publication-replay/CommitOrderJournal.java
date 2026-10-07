package org.kanger;
import java.util.*;

/** Explicitly registered parent/actor recorder; independent of ThreadLocal. */
public final class CommitOrderJournal {
    private static final Map<Mind,Session> ROOTS=new IdentityHashMap<>();
    public static final class Session {
        final Mind parent;
        final IdentityHashMap<Mind,Integer> actors=new IdentityHashMap<>();
        final List<Event> events=new ArrayList<>();
        final List<String> errors=new ArrayList<>();
        Session(Mind p,Mind[] children){parent=p;for(int i=0;i<children.length;i++)actors.put(children[i],i+1);}
    }
    public static final class Event {
        final Session session;final int sequence,actor;final boolean settleRejected;
        boolean completed,accepted;String failure="none";
        Event(Session s,int n,int a,boolean policy){session=s;sequence=n;actor=a;settleRejected=policy;}
    }
    public static Session register(Mind parent,Mind[] children){
        synchronized(ROOTS){if(ROOTS.containsKey(parent))throw new AssertionError("duplicate order registration");Session s=new Session(parent,children);ROOTS.put(parent,s);return s;}
    }
    public static Event enter(Mind parent,Mind child,boolean policy){
        Session s; synchronized(ROOTS){s=ROOTS.get(parent);}if(s==null)return null;
        synchronized(s){
            try{
                Integer actor=s.actors.get(child);if(actor==null)return null;
                for(Event previous:s.events)if(previous.actor==actor)throw new AssertionError("duplicate actor entry");
                Event e=new Event(s,s.events.size()+1,actor,policy);s.events.add(e);return e;
            }catch(Throwable failure){s.errors.add(failure.toString());return null;}
        }
    }
    public static void exit(Event e,boolean accepted,Throwable failure){
        if(e==null)return;synchronized(e.session){try{if(e.completed)throw new AssertionError("duplicate order exit");e.completed=true;e.accepted=accepted;e.failure=failure==null?"none":failure.getClass().getName();}catch(Throwable problem){e.session.errors.add(problem.toString());}}
    }
    public static List<String> finish(Session s,String label){
        synchronized(ROOTS){if(ROOTS.remove(s.parent)!=s)throw new AssertionError("missing order registration");}
        synchronized(s){
            if(s.events.size()!=3||!s.errors.isEmpty())throw new AssertionError("order journal "+s.events.size()+" "+s.errors);
            List<String> rows=new ArrayList<>();Set<Integer> actors=new HashSet<>();
            for(Event e:s.events){if(!e.completed||!e.failure.equals("none")||!actors.add(e.actor)||!e.settleRejected)throw new AssertionError("incomplete native settlement");
                rows.add("ORDER_ENTER label="+label+" sequence="+e.sequence+" actor="+e.actor+" policy=settle");
                rows.add("ORDER_EXIT label="+label+" sequence="+e.sequence+" actor="+e.actor+" accepted="+e.accepted+" failure="+e.failure);
            }
            return rows;
        }
    }
    public static int[] order(Session s){synchronized(s){int[] r=new int[s.events.size()];for(int i=0;i<r.length;i++)r[i]=s.events.get(i).actor;return r;}}
    public static boolean[] outcomes(Session s){synchronized(s){boolean[] r=new boolean[3];for(Event e:s.events)r[e.actor-1]=e.accepted;return r;}}
    public static int activeRegistrations(){synchronized(ROOTS){return ROOTS.size();}}
}
