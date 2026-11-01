/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.interfaces.IRule;

/** Independent finite representative oracle for flat ordinary unit intersections. */
public final class NativeUniversalUnitCollisionRunner {
    private static int checks;
    private static void require(boolean value,String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static Mind memory() throws Exception {
        User user=new User(); Mind mind=new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static Set<Long> ids(Mind mind) {
        Set<Long> result=new HashSet<>(); for (IRule rule:mind.getRules()) if (!rule.isDeleted(mind)) result.add(rule.getId()); return result;
    }
    private static String row(String[] pattern,boolean positive,boolean rename) {
        String text=String.join(",",pattern);
        if (rename) text=text.replace("x","u").replace("y","v");
        return "!"+(rename?"@u @v ":"@x @y ")+(positive?"":"~")+"pair("+text+");";
    }
    private static Set<String> instances(String[] pattern) {
        // All mentioned typed values plus one other class; no native unifier used.
        String[] values={"1","2","John","\"1\"","Fresh"}; Set<String> result=new HashSet<>();
        for (String x:values) for (String y:values) {
            List<String> tuple=new ArrayList<>();
            for (String slot:pattern) tuple.add(slot.equals("x")?x:slot.equals("y")?y:slot);
            result.add(tuple.toString());
        }
        return result;
    }
    private static void matrix() throws Exception {
        String[][] patterns={{"x","x"},{"x","y"},{"y","x"},{"x","1"},{"1","x"},
                {"1","2"},{"2","1"},{"John","x"},{"\"1\"","x"}};
        for (String[] a:patterns) for (String[] b:patterns) for (boolean reverse:new boolean[]{false,true}) {
            Set<String> intersection=instances(a); intersection.retainAll(instances(b));
            boolean consistent=intersection.isEmpty(); Mind mind=memory();
            String positive=row(a,true,false),negative=row(b,false,true);
            require(mind.compile(reverse?negative:positive,null,false),"first whole primary unit accepted");
            Set<Long> before=ids(mind);
            require(mind.compile(reverse?positive:negative,null,false)==consistent,
                    "native qualification agrees with independent tuple intersection: "+positive+negative);
            if (!consistent) {
                require(ids(mind).equals(before),"rejected whole unit rolls back atomically");
                ContextQualification q=mind.getLastCompileQualification();
                require(q!=null&&!q.isValid()&&!q.getCollisions().isEmpty(),"actual qualification retains collision roots");
            }
        }
    }
    private static void qualificationAndQueries() throws Exception {
        Mind raw=memory();
        raw.compileLine("!@x p(x);",false,new LinkedList<org.kanger.interfaces.ITerm>());
        raw.compileLine("!@y ~p(y);",false,new LinkedList<org.kanger.interfaces.ITerm>());
        Set<Long> before=ids(raw);
        boolean exhausted=false;
        try { NativeUniversalUnitCollision.find(raw,1); }
        catch (IllegalStateException expected) { exhausted=true; }
        require(exhausted,"work exhaustion refuses rather than returning a partial collision list");
        require(ids(raw).equals(before),"bounded comparison does not mutate native state");
        try (CollisionProofCapture capture=CollisionProofCapture.begin()) {
            ContextQualification q=ContextQualification.inspect(raw,false);
            require(!q.isValid()&&q.getCollisions().size()==1,"explicit CHECK qualification sees symbolic unit collision");
            require(capture.snapshot().size()==1,"existing collision capture receives the two actual roots");
            require(capture.snapshot().get(0).left.nodes.size()==1&&capture.snapshot().get(0).right.nodes.size()==1,
                    "direct root proof introduces no fabricated intermediate fact");
        }
        require(ids(raw).equals(before),"qualification does not rewrite primary rows");
        Mind mind=memory(); require(mind.compile("!@x p(x);",null,false),"universal query premise accepted");
        Set<Long> known=ids(mind);
        require(Boolean.TRUE.equals(mind.query("?p(73);",null,false)),"ordinary query answers previously unseen value");
        require(mind.query("?q(73);",null,false)==null,"unrelated query remains unresolved");
        require(ids(mind).equals(known),"queries do not persist representative values as rules");
        require(mind.queryAccept("!@y ~p(y);",null,false)==null,"actual single-row authoring also refuses opposite universal");
        require(ids(mind).equals(known),"single-row authoring refusal is atomic");
        Mind mixed=memory();
        require(mixed.compile("!@a @b $u rel(a,u,b);",null,false),"dependent witness control accepted");
        require(mixed.compile("!@a @u ~(rel(a,u,1)&&rel(a,u,2));",null,false),"dependent witness not collapsed to a global value");
    }
    private static void arity() throws Exception {
        for (int size:new int[]{17,65}) {
            StringBuilder prefix=new StringBuilder("!"); List<String> arguments=new ArrayList<>();
            for (int i=0;i<size;i++) { prefix.append("@x").append(i).append(' '); arguments.add("x"+i); }
            String atom="wide("+String.join(",",arguments)+");";
            Mind mind=memory();
            require(mind.compile(prefix+atom,null,false),"wide universal unit accepted");
            require(!mind.compile(prefix+"~"+atom,null,false),"wide symbolic opposition rejected at arity "+size);
        }
        Mind zero=memory();
        require(zero.compile("!nullary();",null,false),"zero-arity ordinary premise accepted");
        require(!zero.compile("!~nullary();",null,false),"zero-arity historical opposition unchanged");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("native-unit-").toString());
        matrix(); qualificationAndQueries(); arity();
        System.out.println("NATIVE_UNIVERSAL_UNIT_COLLISION_PASS checks="+checks);
    }
}
