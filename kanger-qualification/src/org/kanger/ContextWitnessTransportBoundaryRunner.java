/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.*;
import org.kanger.ContextWitnessScopeObservation.*;

/** Concrete counterexamples to lossy witness transport, not an existential certificate. */
public final class ContextWitnessTransportBoundaryRunner {
    private static int checks;
    private static final Source X = new Source(new UUID(0,1),1,0,true,"base");
    private static final Source A = new Source(new UUID(0,2),1,0,false,"base");
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static boolean accepted(String text) throws Exception { return memory().compile(text,null,false); }
    private static Scope scope(Source source,String text) throws Exception {
        return ContextWitnessScopeObservation.inspect(new Declaration(source,text),10000);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-boundary-").toString());
            require(accepted("!$x p(x); !~p(1);"),"an existential witness need not be the fixed value1");
            require(!accepted("!p(1); !~p(1);"),"replacing a witness with a named value can create a false collision");
            require(!accepted("!@x p(x); !~p(1);"),"universalizing a witness can create a false collision");
            String prohibit = "!@x ~(p(x) && q(x));";
            require(!accepted("!$x p(x) && q(x);"+prohibit),"shared existential scope conflicts with the exclusion");
            require(accepted("!$x p(x); !$x q(x);"+prohibit),"separate scopes admit different witnesses");
            Scope shared = scope(X,"!$x p(x) && q(x);");
            require(shared.witnesses.size() == 1 && shared.uses.size() == 2,"one descriptor is shared across conjunction branches");
            require(shared.uses.get(0).witness == shared.uses.get(1).witness
                    && shared.uses.get(0).branch != shared.uses.get(1).branch,"branch boundaries cannot freshen a shared witness");
            Scope alpha = scope(X,"!$z p(z) && q(z);");
            require(alpha.witnesses.size() == 1 && alpha.uses.size() == 2,"binder spelling does not determine scope topology");
            Scope replay = scope(X,"!$x p(x) && q(x);");
            require(!shared.replay.equals(replay.replay),"fresh replay identity prevents raw native ID aliasing");
            Scope foreign = scope(A,"!$x p(x) && q(x);");
            require(!foreign.replay.equals(shared.replay) && !foreign.source.equals(shared.source),"source and replay scopes remain separate");
            Scope two = scope(X,"!$x $y pair(x,y);");
            require(two.witnesses.size() == 2 && two.uses.size() == 2 && two.uses.get(0).witness != two.uses.get(1).witness,
                    "different binders in the same rule cannot be merged");
            Scope diagonal = scope(X,"!$x pair(x,x);");
            require(diagonal.witnesses.size() == 1 && diagonal.uses.size() == 2,
                    "repeated positions preserve a single witness");
            Scope dependent = scope(X,"!@x $y edge(x,y);");
            Scope dominating = scope(X,"!$y @x edge(x,y);");
            require(dependent.universals.size() == 1 && dominating.universals.size() == 1,"mixed prefixes expose native universal positions");
            require(!dependent.witnesses.get(0).domini && dominating.witnesses.get(0).domini,
                    "prefix order changes the native domini descriptor");
            String restriction = "!@y ~(edge(1,y) && edge(2,y));";
            require(accepted("!@x $y edge(x,y);"+restriction),"forall-exists allows distinct dependent witnesses");
            require(!accepted("!$y @x edge(x,y);"+restriction),"exists-forall must retain its common witness");
            boolean refused = false;
            try { SemanticTermSnapshot.capture(witnessTerm()); } catch (IllegalArgumentException expected) { refused = true; }
            require(refused,"ordinary semantic transport still rejects C-variable descriptors");
            Result boundary = ContextProductionRoundsReference.inspectWithVerifiedHornCoverage(
                    Collections.singletonList(new Declaration(X,"!$x p(x);")),
                    Collections.singletonList(Collections.singletonList(new Declaration(A,"!~p(1);"))),
                    new Limits(16,50000,4000000,64));
            require(boundary.status == Status.INCOMPLETE && boundary.coverage == Coverage.NONE && boundary.rounds == 0,
                    "metadata observation cannot silently widen the certified Horn profile");
            boolean stopped = false;
            try { ContextWitnessScopeObservation.inspect(new Declaration(X,"!$x p(x);"),1); }
            catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"resource exhaustion cannot return a partial scope");
            require(shared.witnesses.get(0).ownerRule == shared.nativeRule && shared.witnesses.get(0).parent == null,
                    "primary witness owner is observed, not inferred from branch position");
            System.out.println("CONTEXT_WITNESS_TRANSPORT_BOUNDARY_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static org.kanger.interfaces.ITerm witnessTerm() throws Exception {
        Mind mind = memory(); org.kanger.units.Rule rule = (org.kanger.units.Rule)mind.compileLine(
                "!$x p(x);",false,new LinkedList<org.kanger.interfaces.ITerm>());
        return rule.getTree().get(0).get(0).get(0).getValue(mind);
    }
}
