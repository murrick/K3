/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.units.Rule;

/** A Rule's variable roles must survive canonical lookup and sourced replay. */
public final class RuleVariableIdentityRunner {
    private static int checks;
    private static final String SHARED = "!@x edge(x,1) -> out(2,x);";
    private static final String INDEPENDENT = "!@x @y edge(x,1) -> out(2,y);";
    private static Mind memory() throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    private static Source source(int rule) { return new Source(new UUID(0,1),1,rule,true,"base"); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("rule-variable-identity-").toString());
            nativeOrders(); sameCountDifferentSharing(); actualAliases(); derivedTransport();
            System.out.println("RULE_VARIABLE_IDENTITY_PASS checks="+checks);
            System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void nativeOrders() throws Exception {
        for (boolean reverse : new boolean[]{false,true}) {
            Mind mind = memory();
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
                capture.replay(mind,source(0),reverse ? INDEPENDENT : SHARED);
                capture.replay(mind,source(1),reverse ? SHARED : INDEPENDENT);
                List<Input> inputs = capture.snapshot().inputs;
                require(!inputs.get(1).duplicate && inputs.get(0).nativeRule != inputs.get(1).nativeRule,
                        "independent and shared binders are distinct in either insertion order");
                Rule a = (Rule)mind.getRules().get(inputs.get(0).nativeRule);
                Rule b = (Rule)mind.getRules().get(inputs.get(1).nativeRule);
                require(a.getHash() == b.getHash(),"legacy coarse structural hashes remain compatible");
                require(!a.equalsTo(b) && !b.equalsTo(a),"a hash collision does not erase variable identity");
            }
            require(mind.compile("!edge(3,1);",null,false),"ordinary ground premise accepted");
            require(Boolean.TRUE.equals(mind.query("?out(2,4);",null,false)),"independent conclusion is order independent");
            require(!mind.compile("!~out(2,4);",null,false),"inconsistent ordinary assertion is refused");
        }
        Mind shared = memory();
        require(shared.compile(SHARED+"!edge(3,1);",null,false),"shared-only control accepted");
        require(shared.query("?out(2,4);",null,false) == null,"shared-only rule cannot supply a distinct head value");
        require(Boolean.TRUE.equals(shared.query("?out(2,3);",null,false)),"shared-only matching instance is retained");
        for (boolean reverse : new boolean[]{false,true}) {
            Mind unit = memory();
            String diagonal = "!@x pair(x,x);", independent = "!@x @y pair(x,y);";
            require(unit.compile(reverse ? independent+diagonal : diagonal+independent,null,false),"universal unit controls accepted");
            require(Boolean.TRUE.equals(unit.query("?pair(3,4);",null,false)),"unit repeated-position distinction survives");
        }
    }
    private static void sameCountDifferentSharing() throws Exception {
        for (boolean reverse : new boolean[]{false,true}) {
            Mind mind = memory();
            String a = "!@x @y edge(x,1) && tag(y) -> out(2,x);";
            String b = "!@x @y edge(x,1) && tag(y) -> out(2,y);";
            try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
                capture.replay(mind,source(0),reverse ? b : a);
                capture.replay(mind,source(1),reverse ? a : b);
                List<Input> inputs = capture.snapshot().inputs;
                require(inputs.get(0).variableCount == 2 && inputs.get(1).variableCount == 2
                        && !inputs.get(1).duplicate,"equal variable counts cannot hide different cross-literal sharing");
            }
            require(mind.compile("!edge(3,1);!tag(4);",null,false),"two ground premises accepted");
            require(Boolean.TRUE.equals(mind.query("?out(2,3);",null,false))
                    && Boolean.TRUE.equals(mind.query("?out(2,4);",null,false)),"both distinct two-variable rules remain effective");
        }
    }
    private static void actualAliases() throws Exception {
        Mind mind = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
            capture.replay(mind,source(0),INDEPENDENT);
            capture.replay(mind,source(1),"!@a @b aux(a,b);");
            capture.replay(mind,source(2),"!@u @v (out(2,v) || ~edge(u,1));");
            List<Input> inputs = capture.snapshot().inputs;
            require(inputs.get(2).duplicate && inputs.get(0).nativeRule == inputs.get(2).nativeRule,
                    "binder renaming, literal reordering and unrelated variable allocation retain actual aliases");
            require(inputs.get(2).source.equals(source(2)),"duplicate source occurrence keeps its own label");
        }
    }
    private static void derivedTransport() throws Exception {
        Mind mind = memory();
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,10000)) {
            capture.replay(mind,source(0),SHARED);
            Mind template = memory();
            Rule rule = (Rule)template.compileLine(INDEPENDENT,false,new LinkedList<org.kanger.interfaces.ITerm>());
            Clause independent = Clause.shapeOf(rule.getTree().get(0),template);
            capture.replayDerived(mind,independent,Collections.singletonList(new Support(Collections.singleton(source(1)),1)));
            Snapshot snapshot = capture.snapshot();
            require(snapshot.gaps.isEmpty() && !snapshot.inputs.get(1).duplicate
                    && snapshot.inputs.get(1).clause.equivalent(independent),"derived transport cannot collapse two binders into one");
        }
        List<Declaration> x = Collections.singletonList(new Declaration(source(0),"!@x @y edge(x,y);"));
        UUID foreign = new UUID(0,2);
        List<Declaration> a = Arrays.asList(new Declaration(new Source(foreign,1,0,false,"base"),"!edge(1,2);"),
                new Declaration(new Source(foreign,1,1,false,"base"),"!@x @y @z edge(x,y) && edge(y,z) -> out(z,x);"));
        Result result = ContextProductionRoundsReference.inspect(x,Collections.singletonList(a),new Limits(16,20000,500000,64));
        require(result.status == Status.INCOMPLETE && result.coverage == Coverage.NONE
                && result.reason.contains("unproved-symbolic-coverage") && !result.reason.contains("transport-mismatch"),
                "native generic join transports distinct scopes while retaining the unproved coverage boundary: "+result.reason);
    }
    private static void require(boolean value,String message) { ++checks; if (!value) throw new AssertionError(message); }
}
