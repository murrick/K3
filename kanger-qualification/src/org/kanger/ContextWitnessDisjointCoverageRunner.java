/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Certified empty branches are structural evidence, never an absent observation. */
public final class ContextWitnessDisjointCoverageRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static Source s(int context,int id) { return new Source(new UUID(0,context),1,id,context == 0,"base"); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Mind mind() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static Packet base() throws Exception {
        Bundle bundle = ContextWitnessBundle.capture(d(s(0,0),"!$w p(w,1)&&flag(w,1);"),10000);
        try (Session r = ContextWitnessBundle.restoreObserved(Arrays.asList(bundle),10000,100000)) {
            r.capture.replay(r.mind,s(0,10),"!@w p(w,1)->warm(w,1);"); r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(r,16,WORK);
            require(result.exported,"base exports"); return result.packet;
        }
    }
    private static Packet step(Packet input,Source source,String rule) throws Exception {
        try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(input,10000,100000)) {
            round.rule(source,rule); Result result = ContextWitnessResultTransport.exportNext(round,16,WORK);
            require(result.exported,"native productive branch: "+result.reason); return result.packet;
        }
    }
    private static Result empty(Packet input,Declaration... declarations) {
        return ContextWitnessResultTransport.exportDisjointEmpty(input,Cut.of(s(2,2)),Arrays.asList(declarations),16,WORK,100000);
    }
    private static void refused(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    private static Family family(Packet input) { return ContextWitnessResultTransport.family(input,Arrays.asList(Cut.of(s(1,1)),Cut.of(s(2,2))),WORK); }
    private static Map<Cut,Packet> outputs(Packet nativeStep,Packet emptyStep) {
        Map<Cut,Packet> result = new LinkedHashMap<>(); result.put(Cut.of(s(1,1)),nativeStep); result.put(Cut.of(s(2,2)),emptyStep); return result;
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-disjoint-").toString());
            Packet base = base(); Declaration dormant = d(s(2,2),"!@x a(x,1)->b(x,1);");
            // Concrete counterexample to treating disjoint predicate names alone as coverage.
            Mind unsafe = mind(); require(unsafe.compile("!$w p(w,1); !@x a(x,1)->b(x,1); !@x a(x,1)->~b(x,1);",null,false),"disjoint mixed-sign rules are initially consistent");
            require(!unsafe.compile("!a(17,1);",null,false),"their nontrivial negative consequence holds at a previously unmentioned value");
            refused(empty(base,dormant,d(s(2,3),"!@x a(x,1)->~b(x,1);")),"disjointness alone cannot certify this rule pair");
            try (ContextWitnessSeedReplay round = ContextWitnessSeedReplay.open(base,10000,100000)) {
                round.rule(dormant.source,dormant.statement);
                Result absent = ContextWitnessResultTransport.exportNext(round,16,WORK);
                require(!absent.exported && absent.packet == null,"no observed native application remains incomplete");
            }
            Result checked = empty(base,dormant);
            require(checked.exported && checked.packet != null,"a disjoint fact-free definite Horn cut has explicit empty evidence: "+checked.reason);
            require(!checked.packet.observedStep && checked.packet.emptyCut.equals(Cut.of(s(2,99))),"empty certificate has distinct kind and exact cut");
            require(base.identity.equals(checked.packet.basis) && !base.identity.equals(checked.packet.identity),"empty step binds this exact barrier with a fresh output identity");
            require(ContextWitnessResultTransport.sameContents(base,checked.packet,WORK),"empty step retains all atoms and whole source/depth proofs");
            require(checked.packet.producers.equals(Collections.singleton(s(2,2))),"producer ledger comes from actual captured declarations");
            Packet q = step(base,s(1,1),"!@w p(w,1)->q(w,1);");
            FamilyResult joined = family(base).finish(outputs(q,checked.packet),WORK);
            require(joined.status == FamilyStatus.MERGED && joined.changed,"a complete mixed productive/empty family can finish");
            require(joined.packet.items.stream().anyMatch(i -> i.atom.predicate.equals("q")),"productive sibling's q is retained");
            require(joined.packet.items.stream().noneMatch(i -> i.atom.predicate.equals("a") || i.atom.predicate.equals("b")),"empty branch introduces no foreign literal seeds");
            require(ContextWitnessResultTransport.exportDisjointEmpty(joined.packet,Cut.of(s(2,2)),Arrays.asList(dormant),16,WORK,100000).exported,"new packet requires and permits a fresh certificate");
            Family nextFamily = ContextWitnessResultTransport.family(joined.packet,Collections.singletonList(Cut.of(s(2,2))),WORK);
            FamilyResult stale = nextFamily.finish(Collections.singletonMap(Cut.of(s(2,2)),checked.packet),WORK);
            require(stale.status == FamilyStatus.INCOMPLETE && stale.packet == null,"old empty certificate cannot be reused across barriers");
            require(family(base).finish(outputs(q,q),WORK).status == FamilyStatus.INCOMPLETE,"an unproved branch cannot be relabelled empty");
            require(family(base).finish(Collections.singletonMap(Cut.of(s(1,1)),q),WORK).status == FamilyStatus.INCOMPLETE,"missing branch still prevents completion");
            Result other = ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(1,1)),Arrays.asList(d(s(1,1),"!@x c(x,1)->d(x,1);")),16,WORK,100000);
            require(other.exported,"second independently empty cut exports");
            FamilyResult unchanged = family(base).finish(outputs(other.packet,checked.packet),WORK);
            require(unchanged.status == FamilyStatus.MERGED && !unchanged.changed && ContextWitnessResultTransport.sameContents(base,unchanged.packet,WORK),"all certified empty branches produce an unchanged pool");
            require(!unchanged.packet.observedStep && unchanged.packet.emptyCut == null,"a family pool still cannot masquerade as a completed branch");
            refused(empty(base,d(s(2,2),"!@x p(x,2)->q(x,2);")),"overlapping predicate refuses even when fixed arguments do not match");
            refused(empty(base,d(s(2,2),"!@x a(x,1)->p(x,1);")),"overlap in conclusion also refuses");
            refused(empty(base,dormant,d(s(0,30),"!@w p(w,1)->q(w,1);")),"parent X producers are included in the whole cut vocabulary check");
            refused(empty(base,d(s(2,2),"!@x a(x,1);")),"positive universal unit is not a fact-free implication");
            refused(empty(base,d(s(2,2),"!@x ~a(x,1);")),"negative universal unit refused");
            refused(empty(base,d(s(2,2),"!a(1,1);")),"ground fact refused");
            refused(empty(base,d(s(2,2),"!@x ~a(x,1)->b(x,1);")),"negative antecedent is outside definite Horn scope");
            refused(empty(base,d(s(2,2),"!@x a(x,1)||b(x,1);")),"positive disjunction refused");
            refused(empty(base,d(s(2,2),"!$x a(x,1);")),"new existential input refused");
            refused(empty(base,d(s(2,2),"!@x a(x,1)->b(x+1,1);")),"function argument refused");
            refused(empty(base,dormant,d(s(3,3),"!@x c(x,1)->d(x,1);")),"another sibling cut refused");
            refused(empty(base,d(new Source(new UUID(0,2),2,2,false,"base"),dormant.statement)),"wrong cut revision refused");
            refused(empty(base,d(new Source(new UUID(0,2),1,2,false,"changed"),dormant.statement)),"wrong cut view refused");
            refused(empty(base,dormant,dormant),"duplicate source occurrence refused"); refused(empty(base),"empty declaration list does not certify an alleged complete cut");
            refused(ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(2,2)),Arrays.asList(dormant),0,WORK,100000),"carried derived proof depth is not reset");
            refused(ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(2,2)),Arrays.asList(dormant),16,1,100000),"work stop exposes no empty packet");
            refused(ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(2,2)),Arrays.asList(dormant,d(s(2,3),"!@x b(x,1)->c(x,1);")),16,WORK,1),"event stop exposes no empty packet");
            require(empty(base,dormant,d(s(2,3),"!@x b(x,1)->a(x,1);")).exported,"disjoint fact-free Horn cycles remain empty");
            require(empty(base,d(s(2,2),"!@x @y a(x,1)&&b(y,1)->c(x,y);")).exported,"two-variable Horn join remains empty over every admissible value");
            Result self = ContextWitnessResultTransport.exportDisjointEmpty(base,Cut.of(s(0,0)),Arrays.asList(d(s(0,30),"!@x a(x,1)->b(x,1);")),16,WORK,100000);
            require(self.exported,"same structural certificate supports an explicit self-X cut");
            require(ContextWitnessResultTransport.family(base,Collections.singletonList(Cut.of(s(0,0))),WORK).finish(Collections.singletonMap(Cut.of(s(0,0)),self.packet),WORK).status == FamilyStatus.MERGED,"self-X certificate passes exact family validation");
            // The all-false and all-true extensions are concrete controls, not the coverage proof.
            require(mind().compile("!@x a(x,1)->b(x,1); !@x b(x,1)->a(x,1); !@x ~a(x,1); !@x ~b(x,1);",null,false),"all-false native extension is consistent");
            require(mind().compile("!@x a(x,1)->b(x,1); !@x b(x,1)->a(x,1); !@x a(x,1); !@x b(x,1);",null,false),"all-true native extension is consistent");
            System.out.println("CONTEXT_WITNESS_DISJOINT_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
