/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextGroundCutEvidence.*;

/** Exact native contraposition evidence without importing sibling raw knowledge. */
public final class ContextGroundCutEvidenceRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static final class Fixture {
        final Mind mind; final Snapshot snapshot;
        Fixture(int id,boolean x,String text) throws Exception {
            User u = new User(); mind = new Mind(u); u.setCurrentMind(mind);
            if (!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory inventory = ContextProductionInventory.inspect(mind,new UUID(0,id),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(mind,inventory,WORK,100000);
            require(bound.bound,"whole declaration binding"); snapshot = bound.snapshot;
        }
        void unchanged() throws Exception {
            ContextProductionInventory i = snapshot.inventory;
            require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"physical cut inventory unchanged");
        }
    }
    private static Source s(Snapshot cut,String text) { return cut.primary.stream().filter(e -> e.statement.contains(text)).findFirst().get().source; }
    private static Production proof(Result r,String name,boolean positive,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return r.productions.stream().filter(p -> p.conclusion.predicate.equals(name) && p.conclusion.positive == positive && p.proof.depth == depth && p.proof.sources.equals(expected)).findFirst().orElse(null);
    }
    private static void refused(Result r,String why) { require(!r.verified && r.productions.isEmpty(),why+": "+r.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("ground-cut-evidence-").toString());
            Fixture x = new Fixture(0,true,"!@x left(x)&&right(x)->p(x);");
            Fixture a = new Fixture(1,false,"!left(1); !p(1);"), b = new Fixture(2,false,"!right(1); !~p(1);");
            ContextProductionFamilyAssembly.Result prior = ContextProductionFamilyAssembly.assemble(x.snapshot,Arrays.asList(a.snapshot,b.snapshot),WORK);
            require(!prior.assembled,"concrete original family is still outside existing seed-anchored executor");
            Result fromB = ContextGroundCutEvidence.inspect(x.snapshot,b.snapshot,16,WORK,100000);
            require(fromB.verified,"signed foreign facts supply actual local donors: "+fromB.reason);
            Production negativeLeft = proof(fromB,"left",false,1,s(x.snapshot,"@x"),s(b.snapshot,"right"),s(b.snapshot,"~p"));
            require(negativeLeft != null,"contraposition retains X rule and both exact signed foreign fact sources at depth1");
            require(!a.mind.compile("!~left(1);",null,false),"actual transported literal conflicts with A despite masked p");
            Result fromA = ContextGroundCutEvidence.inspect(x.snapshot,a.snapshot,16,WORK,100000);
            require(fromA.verified && fromA.productions.stream().noneMatch(p -> p.conclusion.predicate.equals("left") && !p.conclusion.positive),"A cannot consume B raw facts while inspecting its own cut");
            require(fromB.productions.stream().noneMatch(p -> p.conclusion.predicate.equals("right") && p.proof.depth == 0),"raw foreign fact is never exported as an X production");
            Fixture unrelated = new Fixture(3,true,"!unrelated(1);");
            Result ua = ContextGroundCutEvidence.inspect(unrelated.snapshot,a.snapshot,16,WORK,100000),ub = ContextGroundCutEvidence.inspect(unrelated.snapshot,b.snapshot,16,WORK,100000);
            require(ua.verified && ub.verified && ua.productions.isEmpty() && ub.productions.isEmpty(),"opposite sibling p facts do not become a monolithic conflict or X knowledge");
            Fixture privateRule = new Fixture(4,false,"!a(1); !@x a(x)->b(x); !@x b(x)->c(x);");
            Result privateOnly = ContextGroundCutEvidence.inspect(unrelated.snapshot,privateRule.snapshot,16,WORK,100000);
            require(privateOnly.verified && privateOnly.productions.isEmpty(),"pure foreign-derived chains remain local even when native applications occur");
            Fixture joined = new Fixture(5,true,"!@x c(x)->out(x);");
            Result chain = ContextGroundCutEvidence.inspect(joined.snapshot,privateRule.snapshot,16,WORK,100000);
            require(chain.verified && proof(chain,"out",true,3,s(joined.snapshot,"@x"),s(privateRule.snapshot,"!a"),s(privateRule.snapshot,"a(x)->b"),s(privateRule.snapshot,"b(x)->c")) != null,"foreign-only intermediate proof may feed X rule with full causal depth and sources");
            Fixture signedX = new Fixture(8,true,"!@x a(x)->~b(x);");
            Fixture signedA = new Fixture(9,false,"!a(1);");
            Result signed = ContextGroundCutEvidence.inspect(signedX.snapshot,signedA.snapshot,16,WORK,100000);
            require(signed.verified && proof(signed,"b",false,1,s(signedX.snapshot,"@x"),s(signedA.snapshot,"!a")) != null,"negative native head retains its signed two-source proof");
            Fixture alternativesX = new Fixture(10,true,"!@x a(x)->b(x);");
            Fixture alternativesA = new Fixture(11,false,"!c(1); !d(1); !@x c(x)->a(x); !@x d(x)->a(x);");
            Result alternatives = ContextGroundCutEvidence.inspect(alternativesX.snapshot,alternativesA.snapshot,16,WORK,100000);
            require(alternatives.verified && proof(alternatives,"b",true,2,s(alternativesX.snapshot,"@x"),s(alternativesA.snapshot,"!c"),s(alternativesA.snapshot,"c(x)->a")) != null
                    && proof(alternatives,"b",true,2,s(alternativesX.snapshot,"@x"),s(alternativesA.snapshot,"!d"),s(alternativesA.snapshot,"d(x)->a")) != null,"independent foreign donor alternatives remain separate whole X-dependent proofs");
            refused(ContextGroundCutEvidence.inspect(x.snapshot,b.snapshot,0,WORK,100000),"native production cannot bypass depth0");
            refused(ContextGroundCutEvidence.inspect(x.snapshot,b.snapshot,16,1,100000),"work stop exports no proof prefix");
            refused(ContextGroundCutEvidence.inspect(x.snapshot,b.snapshot,16,WORK,1),"event stop exports no proof prefix");
            refused(ContextGroundCutEvidence.inspect(b.snapshot,x.snapshot,16,WORK,100000),"roles cannot be reversed");
            Fixture witness = new Fixture(6,false,"!$w a(w,1);");
            refused(ContextGroundCutEvidence.inspect(x.snapshot,witness.snapshot,16,WORK,100000),"witness cannot be flattened to an ordinary fact");
            Fixture universal = new Fixture(7,false,"!@x a(x);");
            refused(ContextGroundCutEvidence.inspect(x.snapshot,universal.snapshot,16,WORK,100000),"universal unit is not a finite raw fact ledger");
            boolean immutable = false; try { fromB.productions.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"historical observed products are immutable");
            x.unchanged(); a.unchanged(); b.unchanged(); unrelated.unchanged(); privateRule.unchanged();
            System.out.println("CONTEXT_GROUND_CUT_EVIDENCE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
