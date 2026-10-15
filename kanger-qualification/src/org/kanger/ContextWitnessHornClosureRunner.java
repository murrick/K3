/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Sound native results must also discharge all Horn literal/source/depth obligations. */
public final class ContextWitnessHornClosureRunner {
    private static int checks;
    private static final int WORK = 4000000;
    private static Source s(int c,int n) { return new Source(new UUID(0,c),1,n,c == 0,"base"); }
    private static Declaration d(Source source,String text) { return new Declaration(source,text); }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Packet base() throws Exception {
        Bundle b = ContextWitnessBundle.capture(d(s(0,0),"!$w p(w,1)&&flag(w,1);"),10000);
        try (Session r = ContextWitnessBundle.restoreObserved(Arrays.asList(b),10000,100000)) {
            r.capture.replay(r.mind,s(0,10),"!@w p(w,1)->warm(w,1);"); r.mind.setQueryPass(QueryPass.ACCEPT); r.mind.link(null,false);
            Result result = ContextWitnessResultTransport.export(r,16,WORK); require(result.exported,"base exports"); return result.packet;
        }
    }
    private static ContextWitnessSeedReplay round(Packet input,Declaration... rules) throws Exception {
        ContextWitnessSeedReplay r = ContextWitnessSeedReplay.open(input,10000,100000);
        for (Declaration rule : rules) r.rule(rule.source,rule.statement); return r;
    }
    private static Packet step(Packet input,Declaration... rules) throws Exception {
        try (ContextWitnessSeedReplay r = round(input,rules)) {
            Result result = ContextWitnessResultTransport.exportNext(r,16,WORK); require(result.exported,"native step: "+result.reason); return result.packet;
        }
    }
    private static Packet covered(Packet input,Cut cut,Declaration... rules) throws Exception {
        try (ContextWitnessSeedReplay r = round(input,rules)) {
            Result result = ContextWitnessResultTransport.exportCoveredNext(r,cut,Arrays.asList(rules),16,WORK,100000);
            require(result.exported,"covered owning pass: "+result.reason); return result.packet;
        }
    }
    private static Item item(Packet p,String name) { return p.items.stream().filter(i -> i.atom.predicate.equals(name)).findFirst().orElseThrow(() -> new AssertionError(name)); }
    private static boolean proof(Item item,int depth,Source... sources) {
        Set<Source> expected = new HashSet<>(Arrays.asList(sources));
        return item.proofs.stream().anyMatch(p -> p.depth == depth && p.sources.equals(expected));
    }
    private static int audit(Packet pool,Declaration... rules) throws Exception {
        int[] remaining = {WORK}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("test work limit"); };
        List<Input> inputs = ContextWitnessDeclarationCut.capture(pool,Cut.of(rules[0].source),Arrays.asList(rules),work,WORK,100000);
        return ContextWitnessHornClosure.verify(pool,inputs,16,work);
    }
    private static void missing(Packet pool,String label,Declaration... rules) throws Exception {
        boolean refused = false; try { audit(pool,rules); } catch (IllegalStateException expected) { refused = true; }
        require(refused,label);
    }
    private static void refused(Result result,String why) { require(!result.exported && result.packet == null,why+": "+result.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-horn-closure-").toString());
            Packet base = base(); Cut b = Cut.of(s(2,2));
            Declaration pq = d(s(2,2),"!@w p(w,1)->q(w,1);"),qr = d(s(2,3),"!@w q(w,1)->r(w,1);");
            missing(base,"a sound input without q cannot discharge an enabled p->q",pq);
            Packet chain = covered(base,b,pq,qr);
            require(chain.observedStep && chain.coveredCut.equals(b) && chain.emptyCut == null && chain.basis.equals(base.identity),"productive coverage has explicit cut/kind/exact basis");
            require(proof(item(chain,"r"),2,s(0,0),s(2,2),s(2,3)),"closure retains actual chain proof and depth2");
            require(audit(chain,pq,qr) >= 2,"all actual chain obligations discharge without generating a product");
            Packet altSeeds = step(base,d(s(1,1),"!@w p(w,1)->q(w,1);"),d(s(1,4),"!@w p(w,1)->q(w,1);"));
            Declaration qout = d(s(2,2),"!@w q(w,1)&&flag(w,1)->out(w,1);");
            Packet alternatives = covered(altSeeds,b,qout);
            require(item(alternatives,"out").proofs.size() == 2 && proof(item(alternatives,"out"),2,s(0,0),s(1,1),s(2,2))
                    && proof(item(alternatives,"out"),2,s(0,0),s(1,4),s(2,2)),"each donor alternative requires a whole conclusion alternative");
            Packet singleQ = step(base,d(s(1,1),"!@w p(w,1)->q(w,1);"));
            Packet oldOut = step(singleQ,qout);
            Packet newDonor = step(oldOut,d(s(1,4),"!@w p(w,1)->q(w,1);"));
            require(item(newDonor,"q").proofs.size() == 2 && item(newDonor,"out").proofs.size() == 1,"concrete sound pool has an old head but a newly added donor authority");
            missing(newDonor,"missing second out source group prevents closure even though all atoms exist",qout);
            Packet repaired = covered(newDonor,b,qout);
            require(item(repaired,"out").proofs.size() == 2,"only actual next native inference supplies the missing proof alternative");
            Packet multi = step(base,d(s(1,1),"!@w p(w,1)->q(w,1);"),d(s(1,4),"!@w p(w,1)->q(w,1);"),
                    d(s(1,5),"!@w p(w,1)->r(w,1);"),d(s(1,6),"!@w p(w,1)->r(w,1);"));
            Packet joined = covered(multi,b,d(s(2,2),"!@w q(w,1)&&r(w,1)->out(w,1);"));
            require(item(joined,"out").proofs.size() == 4,"two premise alternative sets require their full Cartesian product");
            require(proof(item(joined,"out"),2,s(0,0),s(1,4),s(1,6),s(2,2)),"join keeps each selected whole donor group");
            // A deeper valid proof cannot satisfy a shallower obligation with exactly the same sources.
            Packet deep = step(base,d(s(2,2),"!@w (p(w,1)->mid(w,1))&&(mid(w,1)->q(w,1));"));
            require(proof(item(deep,"q"),2,s(0,0),s(2,2)),"deep proof uses one conjunctive source declaration twice");
            missing(deep,"same source group at depth2 cannot discharge the depth1 shortcut",pq);
            try (ContextWitnessSeedReplay r = round(base,pq)) {
                Result changed = ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(d(s(2,2),"!@z missing(z,1)->q(z,1);")),16,WORK,100000);
                refused(changed,"same Source label cannot authenticate another rule"); require(changed.reason.contains("owning"),"refusal occurs at exact owning declaration binding");
                Result alpha = ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(d(s(2,2),"!@z p(z,1)->q(z,1);")),16,WORK,100000);
                require(alpha.exported,"alpha-equivalent original declaration is accepted after owning comparison");
                refused(ContextWitnessResultTransport.exportCoveredNext(r,Cut.of(s(3,3)),Arrays.asList(pq),16,WORK,100000),"wrong target cut refuses coverage");
                refused(ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(pq),16,1,100000),"work stop gives no covered prefix");
            }
            try (ContextWitnessSeedReplay r = round(base,pq,qr)) {
                refused(ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(pq),16,WORK,100000),"omitting an owning declaration refuses coverage");
                Result reversed = ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(qr,pq),16,WORK,100000);
                require(reversed.exported,"declaration list order does not affect owning snapshot or closure");
                refused(ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(pq,qr),1,WORK,100000),"native proof depth stop gives no prefix");
                refused(ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(pq,qr),16,WORK,1),"declaration capture event stop gives no prefix");
            }
            Declaration asleep = d(s(2,2),"!@w missing(w,1)->q(w,1);");
            try (ContextWitnessSeedReplay r = round(base,asleep)) {
                refused(ContextWitnessResultTransport.exportCoveredNext(r,b,Arrays.asList(asleep),16,WORK,100000),"no native observation does not fabricate a productive certificate");
            }
            missing(base,"head-only variables remain outside finite proof-pool closure",d(s(2,2),"!@x @y missing(x,1)->q(y,1);"));
            missing(base,"universal units remain outside fact-free proof closure",d(s(2,2),"!@w q(w,1);"));
            missing(base,"negative head remains outside positive proof closure",d(s(2,2),"!@w p(w,1)->~q(w,1);"));
            Packet signed = step(base,d(s(1,1),"!@w p(w,1)->~n(w,1);"));
            missing(signed,"signed seed packets cannot receive a positive Horn closure verdict",asleep);
            Packet fixed = covered(base,b,d(s(2,2),"!@w p(w,1)->q(w,John,2);"));
            require(proof(item(fixed,"q"),1,s(0,0),s(2,2)),"new fixed head values use semantic instance matching");
            Packet self = covered(base,Cut.of(s(0,0)),d(s(0,30),"!@w p(w,1)->q(w,1);"));
            require(self.coveredCut.localX,"explicit self-X cut can be covered");
            // A family merge invalidates branch closure tags: sibling products may enable rules.
            Result empty = ContextWitnessResultTransport.exportDormantEmpty(base,Cut.of(s(1,1)),Arrays.asList(d(s(1,1),"!@w missing(w,1)->flag(w,1);")),16,WORK,100000);
            require(empty.exported,"empty sibling certifies");
            Map<Cut,Packet> outputs = new LinkedHashMap<>(); outputs.put(Cut.of(s(1,1)),empty.packet); outputs.put(b,chain);
            FamilyResult family = ContextWitnessResultTransport.family(base,new ArrayList<>(outputs.keySet()),WORK).finish(outputs,WORK);
            require(family.status == FamilyStatus.MERGED && family.changed && family.packet.coveredCut == null,"family retains results but cannot inherit one branch's closure tag");
            require(ContextWitnessResultTransport.family(family.packet,Collections.singletonList(b),WORK).finish(Collections.singletonMap(b,chain),WORK).status == FamilyStatus.INCOMPLETE,"covered branch cannot be reused at a new input barrier");
            ContextWitnessSeedReplay closed = round(base,pq); closed.close();
            refused(ContextWitnessResultTransport.exportCoveredNext(closed,b,Arrays.asList(pq),16,WORK,100000),"closed owner refuses a coverage prefix");
            System.out.println("CONTEXT_WITNESS_HORN_CLOSURE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
