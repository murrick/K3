/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.units.Rule;

/** Native authoring checked against an independent ordinary relational join oracle. */
public final class NativeUniversalConstraintCollisionRunner {
    private static int checks;
    private static void require(boolean value,String why) { checks++; if (!value) throw new AssertionError(why); }
    private static Mind memory() throws Exception { User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m); return m; }
    private static Set<Long> ids(Mind m) { Set<Long> ids=new HashSet<>(); for (IRule r:m.getRules()) if (!r.isDeleted(m)) ids.add(r.getId()); return ids; }
    private static String unit(String predicate,String[] pattern) { return "!@x @y "+predicate+"("+String.join(",",pattern)+");"; }
    private static Set<String> tuples(String[] pattern) {
        Set<String> result=new HashSet<>();
        String[] values={"1","2","John","\"1\"","Fresh"};
        for (String x:values) for (String y:values) {
            List<String> tuple=new ArrayList<>(); for (String slot:pattern) tuple.add(slot.equals("x")?x:slot.equals("y")?y:slot);
            result.add(tuple.toString());
        }
        return result;
    }
    private static void matrix() throws Exception {
        String[][] patterns={{"x","x"},{"x","y"},{"y","x"},{"x","1"},{"1","x"},{"1","2"},{"2","1"},{"John","x"},{"\"1\"","x"}};
        for (String[] p:patterns) for (String[] q:patterns) for (boolean reverse:new boolean[]{false,true}) for (boolean bodyReverse:new boolean[]{false,true}) {
            Set<String> join=tuples(p); join.retainAll(tuples(q)); boolean consistent=join.isEmpty();
            String units=unit("p",p)+unit("q",q);
            String constraint="!@a @b ~("+(bodyReverse?"q(a,b)&&p(a,b)":"p(a,b)&&q(a,b)")+");";
            Mind mind=memory(); require(mind.compile(reverse?constraint:units,null,false),"initial independent rules accepted");
            Set<Long> before=ids(mind);
            require(mind.compile(reverse?units:constraint,null,false)==consistent,"native compound qualification matches independent shared-tuple join");
            if (!consistent) {
                require(ids(mind).equals(before),"compound rejection atomically rolls back all incoming rows");
                require(!mind.getLastCompileQualification().isValid()&&!mind.getLastCompileQualification().getCollisions().isEmpty(),"compound authoring retains diagnostic conflict");
            }
        }
    }
    private static void proofAndCapture() throws Exception {
        Mind raw=memory();
        try (DmzReplayProvenance journal=DmzReplayProvenance.begin(raw)) {
            IContextResults.Revision source=new IContextResults.Revision(new UUID(0,502),7);
            String[] rows={"!@x p(x);","!@y q(y);","!@a ~(p(a)&&q(a));","!anchor(1);"};
            long[] labels={101,202,303,404};
            for (int i=0;i<rows.length;i++) DmzReplayProvenance.replayRule(raw,source,labels[i],DmzReplayProvenance.Authority.TARGET_Q,rows[i]);
            Set<Long> before=ids(raw);
            NativeUniversalConstraintCollision.Proof proof=NativeUniversalConstraintCollision.find(raw);
            require(proof!=null,"seedless shared universal compound refutation found"); proof.verify(raw);
            NativeUniversalConstraintCollision.Evidence evidence=proof.evidence(); evidence.check(1000000);
            require(evidence.roots.size()==3&&evidence.applications.size()==2,"proof includes constraint and both actual unit roots");
            require(proof.originals().stream().noneMatch(r->{try{return r.getOrigin().contains("anchor");}catch(Exception e){throw new RuntimeException(e);}}),"unrelated anchor never becomes proof support");
            boolean wrongOwner=false; try { proof.verify(memory()); } catch (IllegalStateException expected) { wrongOwner=true; }
            require(wrongOwner,"proof cannot be rebound to a different native Mind");
            for (int kind=0;kind<2;kind++) {
                boolean exhausted=false;
                try { NativeUniversalConstraintCollision.find(raw,kind==0?1:1000000,kind==0?512:1); }
                catch (IllegalStateException expected) { exhausted=true; }
                require(exhausted,"work/state exhaustion gives no partial refutation result");
            }
            require(ids(raw).equals(before),"symbolic search never inserts knowledge");
            try (CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(!ContextQualification.inspect(raw,false).isValid(),"explicit CHECK qualification detects compound contradiction");
                require(capture.symbolicSnapshot().size()==1,"one separate symbolic evidence event");
                CollisionProofCapture.SymbolicConflict event=capture.symbolicSnapshot().get(0);
                event.evidence.check(1000000); require(event.roots.size()==3,"capture retains ALL premise graphs");
                Set<Long> attributed=new HashSet<>();
                for (CollisionProofCapture.Graph graph:event.roots) for (DmzReplayProvenance.SourceObservation observation:graph.nodes.get(graph.root).sources) {
                    require(observation.binding.context.equals(source.getContextId())&&observation.binding.revision==7,"exact physical source context/revision observed");
                    attributed.add(observation.binding.sourceRule);
                }
                require(attributed.equals(new HashSet<>(Arrays.asList(101L,202L,303L))),"all three physical source labels, no anchor relabeling");
                boolean legacyRefused=false; try { capture.snapshot(); } catch (IllegalStateException expected) { legacyRefused=true; }
                require(legacyRefused,"legacy pair consumer cannot silently drop extra roots");
            }
            require(ids(raw).equals(before),"explicit qualification preserves raw input rows");
            try { evidence.roots.clear(); throw new AssertionError(); } catch (UnsupportedOperationException expected) { checks++; }
            try { evidence.applications.clear(); throw new AssertionError(); } catch (UnsupportedOperationException expected) { checks++; }
            Rule root=proof.originals().get(1); root.setDeleted(true,raw);
            boolean stale=false; try { proof.verify(raw); } catch (IllegalStateException expected) { stale=true; }
            require(stale,"deleted original root invalidates live proof");
            evidence.check(1000000); require(evidence.roots.size()==3,"detached evidence remains a historical immutable observation");
        }
    }
    private static void wideAndBoundary() throws Exception {
        Mind mind=memory(); StringBuilder units=new StringBuilder(),body=new StringBuilder();
        for (int i=0;i<17;i++) { units.append("!@x p").append(i).append("(x);"); if(i>0)body.append("&&"); body.append("p").append(i).append("(a)"); }
        require(mind.compile(units.toString(),null,false),"seventeen independent universal units accepted");
        Set<Long> before=ids(mind);
        require(!mind.compile("!@a ~("+body+");",null,false),"seventeen-premise compound collision has no width-eight ceiling");
        require(ids(mind).equals(before),"wide constraint rejection atomic");
        Mind mixed=memory(); require(mixed.compile("!@a @b $u rel(a,u,b);",null,false),"dependent witness premise accepted");
        require(mixed.compile("!@a @u ~(rel(a,u,1)&&rel(a,u,2));",null,false),"dependent witness is not treated as a shared global unit");
        require(NativeUniversalConstraintCollision.find(mixed)==null,"mixed rows remain outside ordinary unit proof scope");
        Mind pending=memory(); require(pending.compile("!@x p(x); !@x ~(p(x)&&missing(x));",null,false),"missing conjunct remains open");
        require(NativeUniversalConstraintCollision.find(pending)==null,"open constraint creates no refutation");
        require(Boolean.TRUE.equals(pending.query("?p(73);",null,false)),"unseen-value query path preserved");
        require(Boolean.FALSE.equals(pending.query("?missing(73);",null,false)),"existing negative inference is preserved; missing is never a positive fact");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("native-constraint-").toString());
        matrix(); proofAndCapture(); wideAndBoundary();
        System.out.println("NATIVE_UNIVERSAL_CONSTRAINT_COLLISION_PASS checks="+checks);
    }
}
