/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalFamilyCoverage.Mode;

/** Independent family coverage and a concrete binary-stable but unsatisfiable factoring boundary. */
public final class ContextUniversalFamilyCoverageRunner {
    private static int checks,ids;
    private static final int WORK=64000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding"); return b.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static List<Receipt> pass(Frontier f) {
        List<Receipt> result=new ArrayList<>();
        for(Snapshot s:f.cuts) { Receipt r=ContextUniversalUnitRounds.inspectCombined(f,s,16,16,WORK); require(r.verified,"verified combined receipt: "+r.reason); result.add(r); }
        return result;
    }
    private static boolean covered(Frontier f,List<Receipt> r,Mode mode) { return ContextUniversalFamilyCoverage.audit(f,r,mode,16,WORK).covered; }
    private static void instances(Clause c,List<Integer> vars,int at,Map<Integer,SemanticTermSnapshot> map,List<SemanticTermSnapshot> domain,List<Clause> result) {
        if(at==vars.size()) { result.add(c.instantiate(map)); return; }
        for(SemanticTermSnapshot value:domain) { map.put(vars.get(at),value); instances(c,vars,at+1,map,domain,result); }
        map.remove(vars.get(at));
    }
    private static void noFiniteModel(Clause positive,Clause negative) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        List<SemanticTermSnapshot> domain=Arrays.asList(SemanticTermSnapshot.capture(m.getTerms().add("UnmentionedCoverageOne")),SemanticTermSnapshot.capture(m.getTerms().add("UnmentionedCoverageTwo")));
        List<Clause> all=new ArrayList<>();
        for(Clause c:Arrays.asList(positive,negative)) instances(c,new ArrayList<>(c.variables),0,new HashMap<>(),domain,all);
        int models=0;
        for(int bits=0;bits<4;bits++) {
            boolean model=true;
            for(Clause c:all) {
                boolean truth=false;
                for(Atom a:c.literals) {
                    Fact fact=a.fact(); int index=-1;
                    for(int i=0;i<domain.size();i++) if(fact.arguments.get(0).semanticallyEquals(domain.get(i))) index=i;
                    require(index>=0,"every oracle atom belongs to fresh test domain");
                    truth |= ((((bits>>index)&1)!=0)==fact.positive);
                }
                model &= truth;
            }
            if(model) models++;
        }
        require(models==0,"all independent finite interpretations violate one parent; diagonal argument proves all-domain inconsistency");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-family-coverage-").toString());
            Snapshot x=snapshot(true,"!@x p(x);"),a=snapshot(false,"!@x p(x)->q(x);");
            Frontier f=ContextUniversalUnitRounds.start(x,Collections.singletonList(a),WORK); List<Receipt> r=pass(f);
            require(covered(f,r,Mode.CLOSED_CUTS),"exact original/common inventories, both step audits, retained conclusions and complete export covered");
            require(!covered(f,r,Mode.STABLE_SHARED),"new common q requires next common round, not premature family stability");
            Barrier b=ContextUniversalUnitRounds.merge(f,r,16,WORK); require(b.status==Status.MERGED && b.changed,"first common carrier fixture");
            List<Receipt> stable=pass(b.frontier);
            ContextUniversalFamilyCoverage.Audit audit=ContextUniversalFamilyCoverage.audit(b.frontier,stable,Mode.STABLE_SHARED,16,WORK);
            require(audit.covered && audit.reason.contains("satisfiability and write admission unknown"),"stable scope remains diagnostic binary coverage, never SAT/write certificate");
            List<Receipt> reversed=new ArrayList<>(stable); Collections.reverse(reversed);
            require(covered(b.frontier,reversed,Mode.STABLE_SHARED),"independent receipt ordering preserves complete-family coverage");
            require(!covered(f,Collections.singletonList(r.get(0)),Mode.CLOSED_CUTS),"missing cut refuses coverage");
            require(!covered(f,Arrays.asList(r.get(0),r.get(0)),Mode.CLOSED_CUTS),"duplicate cut refuses coverage");
            require(!covered(b.frontier,r,Mode.CLOSED_CUTS),"stale family receipts refuse coverage");
            List<Receipt> mixed=new ArrayList<>(r); mixed.set(1,ContextUniversalUnitRounds.inspect(f,a,16,16,WORK));
            require(!covered(f,mixed,Mode.CLOSED_CUTS),"unit-only receipt cannot certify combined mode");
            List<Receipt> limited=new ArrayList<>(r); limited.set(1,ContextUniversalUnitRounds.inspectCombined(f,a,1,16,WORK));
            require(!covered(f,limited,Mode.CLOSED_CUTS),"incomplete local closure blocks whole family audit");
            require(!ContextUniversalFamilyCoverage.audit(f,r,Mode.CLOSED_CUTS,0,WORK).covered,"lower audit depth refuses existing derived row");
            require(!ContextUniversalFamilyCoverage.audit(f,r,Mode.CLOSED_CUTS,16,1).covered,"shared audit budget fails without coverage prefix");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),rawA=snapshot(false,"!@x p(x);"),rawB=snapshot(false,"!~p(9);");
            Frontier separate=ContextUniversalUnitRounds.start(unrelated,Arrays.asList(rawA,rawB),WORK);
            require(covered(separate,pass(separate),Mode.STABLE_SHARED),"raw sibling opposition remains independent in exact-family coverage");
            Snapshot positive=snapshot(true,"!@x @y p(x)||p(y);"),negative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            require(clause(positive).variables.size()==2 && clause(positive).literals.size()==2 && clause(negative).variables.size()==2 && clause(negative).literals.size()==2,"actual native declarations retain both distinct variables and same-sign literals");
            Frontier counter=ContextUniversalUnitRounds.start(positive,Collections.singletonList(negative),WORK); boolean closed=false;
            for(int round=0;round<6;round++) {
                List<Receipt> cr=pass(counter); Barrier cb=ContextUniversalUnitRounds.merge(counter,cr,16,WORK);
                require(cb.status==Status.MERGED && cb.oppositions.isEmpty(),"binary-only path has no opposition in concrete factoring counterexample");
                require(covered(counter,cr,Mode.CLOSED_CUTS),"both binary inference obligations are genuinely covered in counterexample");
                if(!cb.changed) {
                    ContextUniversalFamilyCoverage.Audit boundary=ContextUniversalFamilyCoverage.audit(counter,cr,Mode.STABLE_SHARED,16,WORK);
                    require(!boundary.covered && boundary.reason.contains("same-sign factoring"),"stable guard refuses actual unresolved factoring boundary");
                    require(cr.stream().flatMap(receipt -> receipt.closureStep.input.rows.stream()).allMatch(row -> row.clause.literals.size()==2),"closed binary ledger really has no unit, not hidden early opposition");
                    closed=true; break;
                }
                counter=cb.frontier;
            }
            require(closed,"concrete inconsistent theory reaches unchanged binary recipes within bounds");
            noFiniteModel(clause(positive),clause(negative));
            Snapshot sharedVariable=snapshot(true,"!@x p(x,1)||p(2,x);"),empty=snapshot(false,"!unrelated(7);");
            Clause sv=clause(sharedVariable);
            require(!ContextUniversalCompositionCoverage.factorable(sv.literals.get(0),sv.literals.get(1),()->{}),"factoring uses SAME parent variable scope: x cannot bind to2 and1 independently");
            Frontier valid=ContextUniversalUnitRounds.start(sharedVariable,Collections.singletonList(empty),WORK);
            require(covered(valid,pass(valid),Mode.STABLE_SHARED),"nonunifiable same-sign literals are not falsely classified as factoring boundary");
            Snapshot independent=snapshot(true,"!@x @y p(x,1)||p(2,y);"); Clause iv=clause(independent);
            require(ContextUniversalCompositionCoverage.factorable(iv.literals.get(0),iv.literals.get(1),()->{}),"independent same-parent variables can factor coherently");
            Frontier fi=ContextUniversalUnitRounds.start(independent,Collections.singletonList(empty),WORK);
            require(!covered(fi,pass(fi),Mode.STABLE_SHARED),"actual independently factorable same-sign clause stays outside stable certificate scope");
            require(!ContextUniversalFamilyCoverage.audit(null,Collections.emptyList(),Mode.CLOSED_CUTS,16,WORK).covered,"missing family input refuses coverage");
            System.out.println("CONTEXT_UNIVERSAL_FAMILY_COVERAGE_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
