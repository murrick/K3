/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFactoringProof.*;

/** Same-parent MGU factoring with independent all-instance finite interpretation search. */
public final class ContextUniversalFactoringProofRunner {
    private static int checks,ids;
    private static final int WORK=32000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding: "+b.reason); return b.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static ContextUniversalUnitLedger open(Snapshot x,Snapshot a) {
        ContextUniversalUnitLedger.Opened o=ContextUniversalUnitLedger.openPrimary(x,a,WORK); require(o.opened,"sealed primary ledger"); return o.ledger;
    }
    private static int[] pivots(Clause c) {
        List<Integer> p=new ArrayList<>(); for(int i=0;i<c.literals.size();i++) if(c.literals.get(i).predicate.equals("p")) p.add(i);
        require(p.size()==2,"exact two proposed factoring literals"); return new int[]{p.get(0),p.get(1)};
    }
    private static Evidence prove(ContextUniversalUnitLedger l,Clause owner,int depth) {
        int[] p=pivots(owner); Evidence e=ContextUniversalFactoringProof.prove(l,owner,p[0],p[1],depth,WORK);
        require(e.proved && !e.proofs.isEmpty(),"checked factor: "+e.reason);
        require(e.ledger==l && e.scope==Scope.ORDINARY_FLAT_SAME_SIGN_FACTORING,"exact historical factoring ledger scope");
        for(Proof proof:e.proofs) require(proof.support.sources.equals(proof.ownerSupport.sources) && proof.support.depth==proof.ownerSupport.depth+1,"unary factoring keeps exact whole parent sources and increments depth once");
        return e;
    }
    private static void fail(Evidence e,String why) { require(!e.proved && e.proofs.isEmpty(),why+": "+e.reason); }
    private static void instances(Clause c,List<Integer> vars,int at,Map<Integer,SemanticTermSnapshot> map,List<SemanticTermSnapshot> domain,List<Clause> result) {
        if(at==vars.size()) { result.add(c.instantiate(map)); return; }
        for(SemanticTermSnapshot value:domain) { map.put(vars.get(at),value); instances(c,vars,at+1,map,domain,result); }
        map.remove(vars.get(at));
    }
    private static boolean truth(Clause c,List<Fact> atoms,int bits) {
        for(Atom atom:c.literals) {
            Fact f=atom.fact(),p=f.positive?f:f.opposite(); int index=-1;
            for(int i=0;i<atoms.size();i++) if(atoms.get(i).equivalent(p)) { index=i; break; }
            if(index<0) throw new AssertionError("missing independent oracle atom");
            if((((bits>>index)&1)!=0)==f.positive) return true;
        }
        return false;
    }
    private static void oracle(Proof proof) throws Exception {
        List<SemanticTermSnapshot> domain=new ArrayList<>();
        for(Clause c:Arrays.asList(proof.owner,proof.conclusion)) for(Atom a:c.literals) for(Slot s:a.arguments)
            if(s.variable<0 && domain.stream().noneMatch(v -> v.semanticallyEquals(s.value))) domain.add(s.value);
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m); boolean empty=domain.isEmpty();
        domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshFactorOne")));
        if(empty) domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshFactorTwo")));
        List<Clause> parents=new ArrayList<>(),results=new ArrayList<>();
        instances(proof.owner,new ArrayList<>(proof.owner.variables),0,new HashMap<>(),domain,parents);
        instances(proof.conclusion,new ArrayList<>(proof.conclusion.variables),0,new HashMap<>(),domain,results);
        List<Clause> all=new ArrayList<>(parents); all.addAll(results); List<Fact> atoms=new ArrayList<>();
        for(Clause c:all) for(Atom a:c.literals) { Fact f=a.fact(),p=f.positive?f:f.opposite(); if(atoms.stream().noneMatch(old -> old.equivalent(p))) atoms.add(p); }
        require(atoms.size()<=14,"bounded finite counterexample search"); int models=0;
        for(int bits=0;bits<(1<<atoms.size());bits++) {
            boolean parent=true; for(Clause c:parents) parent &= truth(c,atoms,bits);
            if(!parent) continue; models++;
            for(Clause c:results) require(truth(c,atoms,bits),"every factor instance follows in every independent model of all parent instances");
        }
        require(models>0,"factor entailment is nonvacuous");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-factoring-proof-").toString());
            Snapshot positive=snapshot(true,"!@x @y p(x)||p(y);"),negative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            ContextUniversalUnitLedger base=open(positive,negative);
            Evidence p=prove(base,clause(positive),16),n=prove(base,clause(negative),16);
            Clause pUnit=clause(snapshot(false,"!@x p(x);")),nUnit=clause(snapshot(false,"!@x ~p(x);"));
            require(p.proofs.get(0).conclusion.equivalent(pUnit) && p.proofs.get(0).support.sources.size()==1 && p.proofs.get(0).support.depth==1,"positive diagonal clause gives real generic unit with one original Source");
            require(n.proofs.get(0).conclusion.equivalent(nUnit),"negative same-sign factor retains sign"); oracle(p.proofs.get(0)); oracle(n.proofs.get(0));
            require(!ContextUniversalFactoringCoverage.audit(base,Collections.emptyList(),16,WORK).covered,"missing factors refuse coverage");
            require(!ContextUniversalFactoringCoverage.audit(base,Collections.singletonList(p),16,WORK).covered,"one parent factor cannot hide other same-sign obligation");
            require(ContextUniversalFactoringCoverage.audit(base,Arrays.asList(p,n),16,WORK).covered,"positive and negative complete factors cover exact ledger");
            Snapshot impossible=snapshot(true,"!@x p(x,1)||p(2,x);"),empty=snapshot(false,"!unrelated(7);");
            ContextUniversalUnitLedger il=open(impossible,empty); int[] ip=pivots(clause(impossible));
            fail(ContextUniversalFactoringProof.prove(il,clause(impossible),ip[0],ip[1],16,WORK),"same parent x cannot bind to2 and1 independently");
            require(ContextUniversalFactoringCoverage.audit(il,Collections.emptyList(),16,WORK).covered,"independent same-scope graph has no impossible factor obligation");
            Snapshot constants=snapshot(true,"!@x @y p(x,1)||p(2,y);"); ContextUniversalUnitLedger cl=open(constants,empty);
            Evidence fixed=prove(cl,clause(constants),16);
            require(fixed.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!p(2,1);"))),"independent same-parent variables can give exact ground factor"); oracle(fixed.proofs.get(0));
            Snapshot repeated=snapshot(true,"!@x @y p(x,x)||p(y,y)||link(y,x);"); ContextUniversalUnitLedger rl=open(repeated,empty);
            Evidence coherent=prove(rl,clause(repeated),16);
            require(coherent.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@u p(u,u)||link(u,u);"))),"repeated-variable MGU applies to ALL remaining literals and retains one shared free root"); oracle(coherent.proofs.get(0));
            Snapshot tail=snapshot(true,"!@x @y p(x,1)||p(2,1)||tail(x,y);"); ContextUniversalUnitLedger tl=open(tail,empty);
            Evidence bound=prove(tl,clause(tail),16);
            require(bound.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@z p(2,1)||tail(2,z);"))) && bound.proofs.get(0).conclusion.variables.size()==1,"ground substitutions propagate without capturing unrelated retained variable");
            Snapshot other=snapshot(true,"!@x @y p(x)||p(y)||left(y);"); ContextUniversalUnitLedger ol=open(other,empty);
            Evidence withRemainder=prove(ol,clause(other),16);
            require(withRemainder.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@z p(z)||left(z);"))),"whole unfactored remainder survives"); oracle(withRemainder.proofs.get(0));
            int[] invented=pivots(clause(positive));
            fail(ContextUniversalFactoringProof.prove(ol,clause(positive),invented[0],invented[1],16,WORK),"matching selected literals cannot authenticate invented whole parent without remainder");
            ContextUniversalUnitLedger stable=base;
            boolean done=false;
            for(int round=0;round<8;round++) {
                ContextUniversalUnitLedger.Step s=ContextUniversalUnitLedger.stepCombined(stable,16,WORK);
                require(s.status==ContextUniversalUnitLedger.Status.ADVANCED,"actual binary counterexample fixture");
                stable=s.next; if(!s.changed) { done=true; break; }
            }
            require(done,"actual binary recipes stabilized");
            Evidence alternativeP=prove(stable,clause(positive),16),alternativeN=prove(stable,clause(negative),16);
            require(alternativeP.proofs.stream().anyMatch(proof -> proof.support.sources.size()==1 && proof.support.depth==1)
                && alternativeP.proofs.stream().anyMatch(proof -> proof.support.sources.size()==2 && proof.support.depth==3),"direct and binary-derived whole-source alternatives retain depths1/3");
            require(ContextUniversalFactoringCoverage.audit(stable,Arrays.asList(alternativeP,alternativeN),16,WORK).covered,"all actual derived parent alternatives factor and cover complete stable ledger");
            fail(ContextUniversalFactoringProof.prove(stable,clause(positive),0,1,1,WORK),"late deeper alternative returns no earlier direct factor prefix");
            require(!ContextUniversalFactoringCoverage.audit(stable,Arrays.asList(alternativeP,alternativeN),1,WORK).covered,"coverage cannot erase deeper actual parent alternative");
            require(!ContextUniversalFactoringCoverage.audit(open(positive,negative),Arrays.asList(p,n),16,WORK).covered,"same snapshots with new ledger identity cannot reuse factoring evidence");
            Clause mixed=stable.rows.stream().map(row -> row.clause).filter(c -> c.literals.size()==2 && c.literals.get(0).positive!=c.literals.get(1).positive).findFirst().get();
            fail(ContextUniversalFactoringProof.prove(stable,mixed,0,1,16,WORK),"opposite signs do not factor");
            fail(ContextUniversalFactoringProof.prove(base,clause(positive),0,0,16,WORK),"same literal is not a factoring pair");
            fail(ContextUniversalFactoringProof.prove(base,clause(positive),-1,1,16,WORK),"invalid literal index refuses");
            fail(ContextUniversalFactoringProof.prove(base,clause(positive),0,1,0,WORK),"factoring depth stop");
            fail(ContextUniversalFactoringProof.prove(base,clause(positive),0,1,16,1),"factoring work stop");
            fail(ContextUniversalFactoringProof.prove(null,clause(positive),0,1,16,WORK),"missing ledger refuses");
            require(!ContextUniversalFactoringCoverage.audit(base,Arrays.asList(p,n),16,1).covered,"coverage shares bounded work across graph/kernel/matching");
            require(base.rows.stream().allMatch(row -> row.clause.literals.size()==2),"detached factor never enters sealed rows or transport");
            require(!ContextUniversalUnitProof.prove(base,pUnit,0,nUnit,0,16,WORK).proved,"detached factors do not yet authorize a ledger unit opposition");
            boolean immutable=false; try { p.proofs.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"factoring evidence immutable");
            System.out.println("CONTEXT_UNIVERSAL_FACTORING_PROOF_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
