/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextUniversalUnitProof.*;

/** Detached unit resolution/opposition with independent finite-interpretation controls. */
public final class ContextUniversalUnitProofRunner {
    private static int checks,ids;
    private static final int WORK=16000000;
    private static void require(boolean yes,String why) { ++checks; if(!yes) throw new AssertionError(why); }
    private static Snapshot snapshot(boolean x,String text) throws Exception {
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        require(m.compile(text,null,false),"native fixture");
        ContextProductionInventory inventory=ContextProductionInventory.inspect(m,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
        ContextProductionNativeBinding.Result b=ContextProductionNativeBinding.bind(m,inventory,WORK,500000);
        require(b.bound,"native binding: "+b.reason); return b.snapshot;
    }
    private static Clause clause(Snapshot s) { return s.primary.get(0).clauses.get(0); }
    private static int pivot(Clause c,String predicate) {
        for(int i=0;i<c.literals.size();i++) if(c.literals.get(i).predicate.equals(predicate)) return i;
        throw new AssertionError("missing pivot");
    }
    private static Frontier start(Snapshot x,Snapshot a) { return ContextOrdinaryResidualRounds.start(x,Collections.singletonList(a),WORK); }
    private static Evidence prove(Frontier f,Snapshot a,Clause left,int lp,Clause right,int rp,List<ContextUniversalCompositionProof.Evidence> e) {
        Evidence result=ContextUniversalUnitProof.prove(f,a,left,lp,right,rp,e,16,WORK);
        return checked(result,f,a);
    }
    private static Evidence primary(Snapshot x,Snapshot a,Clause left,int lp,Clause right,int rp) {
        Evidence e=ContextUniversalUnitProof.provePrimary(x,a,left,lp,right,rp,16,WORK);
        require(e.x==x,"exact primary X snapshot"); return checked(e,null,a);
    }
    private static Evidence checked(Evidence result,Frontier f,Snapshot a) {
        require(result.proved && !result.proofs.isEmpty(),"checked unit proof: "+result.reason);
        require(result.input==f && result.target==a && result.scope==Scope.ORDINARY_FLAT_UNIT_RESOLUTION,"exact historical scope");
        for(Proof p:result.proofs) {
            Set<Source> sources=new HashSet<>(p.leftSupport.sources); sources.addAll(p.rightSupport.sources);
            require(p.support.sources.equals(sources) && p.support.depth==Math.max(p.leftSupport.depth,p.rightSupport.depth)+1,"whole-source alternatives and cumulative depth");
            require(p.contradiction==(p.conclusion==null),"empty resolvent is typed opposition, never an ordinary empty clause");
        }
        return result;
    }
    private static void fail(Evidence e,String why) { require(!e.proved && e.proofs.isEmpty(),why+": "+e.reason); }
    private static void instances(Clause c,List<Integer> vars,int at,Map<Integer,SemanticTermSnapshot> map,List<SemanticTermSnapshot> domain,List<Clause> result) {
        if(at==vars.size()) { result.add(c.instantiate(map)); return; }
        for(SemanticTermSnapshot v:domain) { map.put(vars.get(at),v); instances(c,vars,at+1,map,domain,result); }
        map.remove(vars.get(at));
    }
    private static boolean truth(Clause c,List<Fact> atoms,int bits) {
        for(Atom a:c.literals) {
            Fact f=a.fact(),positive=f.positive?f:f.opposite(); int index=-1;
            for(int i=0;i<atoms.size();i++) if(atoms.get(i).equivalent(positive)) { index=i; break; }
            if(index<0) throw new AssertionError("missing oracle atom");
            if((((bits>>index)&1)!=0)==f.positive) return true;
        }
        return false;
    }
    private static void oracle(Proof proof) throws Exception {
        List<Clause> originals=new ArrayList<>(Arrays.asList(proof.left,proof.right));
        if(!proof.contradiction) originals.add(proof.conclusion);
        List<SemanticTermSnapshot> domain=new ArrayList<>();
        for(Clause c:originals) for(Atom a:c.literals) for(Slot s:a.arguments)
            if(s.variable<0 && domain.stream().noneMatch(v -> v.semanticallyEquals(s.value))) domain.add(s.value);
        User u=new User(); Mind m=new Mind(u); u.setCurrentMind(m);
        boolean empty=domain.isEmpty(); domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshUnitOracleOne")));
        if(empty) domain.add(SemanticTermSnapshot.capture(m.getTerms().add("FreshUnitOracleTwo")));
        List<Clause> parents=new ArrayList<>(),results=new ArrayList<>();
        for(Clause c:Arrays.asList(proof.left,proof.right)) instances(c,new ArrayList<>(c.variables),0,new HashMap<>(),domain,parents);
        if(!proof.contradiction) instances(proof.conclusion,new ArrayList<>(proof.conclusion.variables),0,new HashMap<>(),domain,results);
        List<Fact> atoms=new ArrayList<>(); List<Clause> all=new ArrayList<>(parents); all.addAll(results);
        for(Clause c:all) for(Atom a:c.literals) { Fact f=a.fact(),p=f.positive?f:f.opposite(); if(atoms.stream().noneMatch(old -> old.equivalent(p))) atoms.add(p); }
        require(atoms.size()<=14,"bounded independent oracle"); int models=0;
        for(int bits=0;bits<(1<<atoms.size());bits++) {
            boolean satisfied=true; for(Clause c:parents) satisfied &= truth(c,atoms,bits);
            if(!satisfied) continue; models++;
            require(!proof.contradiction,"opposed universal parents have no finite interpretation, including fresh values");
            for(Clause c:results) require(truth(c,atoms,bits),"all instances of result follow in each parent interpretation");
        }
        require(proof.contradiction?models==0:models>0,"oracle checks opposition or nonvacuous consequence");
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-unit-proof-").toString());
            Snapshot x=snapshot(true,"!@x p(x);"),a=snapshot(false,"!@y p(y)->q(y);");
            Evidence generic=primary(x,a,clause(x),0,clause(a),pivot(clause(a),"p"));
            require(generic.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@z q(z);"))),"universal donor produces universal result without constant sampling"); oracle(generic.proofs.get(0));
            Snapshot negative=snapshot(false,"!~p(9);");
            Evidence opposition=primary(x,negative,clause(x),0,clause(negative),0);
            require(opposition.proofs.get(0).contradiction && opposition.proofs.get(0).dependsOnX,"universal/ground opposition has X-dependent typed proof"); oracle(opposition.proofs.get(0));
            Snapshot cx=snapshot(true,"!@x p(x,1);"),ca=snapshot(false,"!@x ~p(2,x);");
            Evidence crossed=primary(cx,ca,clause(cx),0,clause(ca),0);
            require(crossed.proofs.get(0).contradiction,"independent parent variable names bind to different constants in opposition"); oracle(crossed.proofs.get(0));
            Snapshot repeat=snapshot(true,"!@x p(x,x);"),bad=snapshot(false,"!~p(1,2);");
            fail(ContextUniversalUnitProof.provePrimary(repeat,bad,clause(repeat),0,clause(bad),0,16,WORK),"repeated variable forbids incoherent opposition");
            Snapshot good=snapshot(false,"!~p(1,1);");
            Evidence coherent=primary(repeat,good,clause(repeat),0,clause(good),0); oracle(coherent.proofs.get(0));
            Snapshot mixedX=snapshot(true,"!@x p(x,1);"),mixedA=snapshot(false,"!@y ~p(y,2);");
            fail(ContextUniversalUnitProof.provePrimary(mixedX,mixedA,clause(mixedX),0,clause(mixedA),0,16,WORK),"different constants cannot be hidden by universal variables");
            Snapshot sx=snapshot(true,"!@x p(x)||left(x);"),sa=snapshot(false,"!@y ~p(y)||left(y); !@z left(z)->done(z);"); Frontier sf=start(sx,sa);
            Receipt r=ContextOrdinaryResidualRounds.inspectWithProbes(sf,sa,16,WORK,500000,256);
            require(r.verified,"actual composition baseline"); Clause partner=clause(sa);
            ContextUniversalCompositionProof.Evidence unit=ContextUniversalCompositionProof.prove(r,clause(sx),pivot(clause(sx),"p"),partner,pivot(partner,"p"),16,WORK);
            require(unit.proved && unit.proofs.get(0).conclusion.literals.size()==1,"actual sealed derived universal unit");
            Clause rule=sa.primary.get(1).clauses.get(0),derived=unit.proofs.get(0).conclusion;
            Evidence done=prove(sf,sa,derived,0,rule,pivot(rule,"left"),Collections.singletonList(unit));
            require(done.proofs.get(0).conclusion.equivalent(clause(snapshot(false,"!@z done(z);"))) && done.proofs.get(0).support.sources.size()==3 && done.proofs.get(0).support.depth==2,"derived universal donor retains all original causes and cumulative depth"); oracle(done.proofs.get(0));
            fail(ContextUniversalUnitProof.prove(sf,sa,derived,0,rule,pivot(rule,"left"),Collections.emptyList(),16,WORK),"invented derived unit without sealed proof refuses");
            fail(ContextUniversalUnitProof.prove(sf,sx,derived,0,rule,pivot(rule,"left"),Collections.singletonList(unit),16,WORK),"foreign cut evidence cannot authenticate at self cut");
            fail(ContextUniversalUnitProof.prove(firstSuccessor(sf),sa,derived,0,rule,pivot(rule,"left"),Collections.singletonList(unit),16,WORK),"old input evidence cannot authenticate on successor");
            fail(ContextUniversalUnitProof.prove(sf,sa,derived,0,rule,pivot(rule,"left"),Collections.singletonList(unit),1,WORK),"derived cumulative depth cannot reset");
            fail(ContextUniversalUnitProof.provePrimary(x,a,clause(x),0,clause(a),pivot(clause(a),"p"),16,1),"work exhaustion yields no proof prefix");
            fail(ContextUniversalUnitProof.provePrimary(x,a,clause(x),0,clause(a),pivot(clause(a),"p"),0,WORK),"depth exhaustion yields no proof prefix");
            fail(ContextUniversalUnitProof.provePrimary(x,a,clause(x),1,clause(a),0,16,WORK),"invalid unit pivot refuses");
            fail(ContextUniversalUnitProof.prove(sf,sa,clause(sx),0,partner,0,Collections.emptyList(),16,WORK),"two compound parents remain outside unit scope");
            Snapshot sibling=snapshot(false,"!~p(11);"); Frontier siblings=ContextOrdinaryResidualRounds.start(sx,Arrays.asList(sa,sibling),WORK);
            fail(ContextUniversalUnitProof.prove(siblings,sa,clause(sibling),0,rule,pivot(rule,"left"),Collections.emptyList(),16,WORK),"raw sibling unit cannot authenticate in another cut");
            require(sf.products.isEmpty(),"detached proof creates no shared Product");
            boolean immutable=false; try { done.proofs.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"proof results immutable");
            require(!ContextOrdinaryResidualRounds.inspectWithUniversalCompositions(sf,sa,16,WORK,500000,256).verified,"new detached unit proof does not relax old transport gate");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,a,Collections.emptyList(),16,WORK).covered,"missing generic unit consequence refuses coverage");
            require(ContextUniversalUnitCoverage.auditPrimary(x,a,Collections.singletonList(generic),16,WORK).covered,"generic unit evidence covers exact primary cut");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,negative,Collections.emptyList(),16,WORK).covered,"missing typed opposition refuses coverage even without result clause");
            require(ContextUniversalUnitCoverage.auditPrimary(x,negative,Collections.singletonList(opposition),16,WORK).covered,"typed opposition discharges unit/unit obligation");
            require(ContextUniversalUnitCoverage.auditPrimary(cx,ca,Collections.singletonList(crossed),16,WORK).covered,"apart-renamed universal opposition covered");
            require(ContextUniversalUnitCoverage.auditPrimary(repeat,bad,Collections.emptyList(),16,WORK).covered,"independent equality graph finds no incoherent repeated-variable obligation");
            require(ContextUniversalUnitCoverage.auditPrimary(mixedX,mixedA,Collections.emptyList(),16,WORK).covered,"independent graph rejects unequal ground constants");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,a,Collections.singletonList(opposition),16,WORK).covered,"evidence from another target refuses coverage");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,a,Collections.singletonList(generic),0,WORK).covered,"unit consequence depth bound cannot be bypassed by supplying proof");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,a,Collections.singletonList(generic),16,1).covered,"one shared audit work budget refuses without partial coverage");
            require(ContextUniversalUnitCoverage.audit(sf,sa,Collections.singletonList(unit),Collections.singletonList(done),16,WORK).covered,"actual derived universal unit and all source recipes audited");
            require(!ContextUniversalUnitCoverage.audit(sf,sa,Collections.singletonList(unit),Collections.emptyList(),16,WORK).covered,"derived unit consequence cannot disappear from audit");
            require(!ContextUniversalUnitCoverage.audit(sf,sx,Collections.singletonList(unit),Collections.emptyList(),16,WORK).covered,"wrong-cut compound authority refuses before vacuous success");
            require(!ContextUniversalUnitCoverage.auditPrimary(sx,sa,Collections.singletonList(done),16,WORK).covered,"frontier-derived evidence cannot pass as primary-only evidence");
            Snapshot multi=snapshot(false,"!@y ~p(y)||~p(1)||q(y);"); Clause multiClause=clause(multi);
            List<Evidence> allPivots=new ArrayList<>();
            for(int i=0;i<multiClause.literals.size();i++) if(multiClause.literals.get(i).predicate.equals("p"))
                allPivots.add(primary(x,multi,clause(x),0,multiClause,i));
            require(allPivots.size()==2 && !allPivots.get(0).proofs.get(0).conclusion.equivalent(allPivots.get(1).proofs.get(0).conclusion),"one unit and one compound require two different whole-clause consequences for distinct compatible pivot positions");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,multi,Collections.singletonList(allPivots.get(0)),16,WORK).covered,"first matching pivot is not complete coverage");
            require(!ContextUniversalUnitCoverage.auditPrimary(x,multi,Collections.singletonList(allPivots.get(1)),16,WORK).covered,"other single pivot is not complete coverage either");
            require(ContextUniversalUnitCoverage.auditPrimary(x,multi,allPivots,16,WORK).covered,"every compatible pivot consequence closes detached obligations");
            for(Evidence e:allPivots) oracle(e.proofs.get(0));
            require(!ContextUniversalUnitCoverage.auditPrimary(null,a,Collections.emptyList(),16,WORK).covered,"missing snapshot refuses coverage");
            System.out.println("CONTEXT_UNIVERSAL_UNIT_PROOF_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static Frontier firstSuccessor(Frontier f) {
        List<Receipt> receipts=new ArrayList<>();
        // Empty new observations are unnecessary: ordinary probes contain no transportable unit here.
        for(Snapshot s:f.cuts) receipts.add(ContextOrdinaryResidualRounds.inspectWithProbes(f,s,16,WORK,500000,256));
        Barrier b=ContextOrdinaryResidualRounds.merge(f,receipts,WORK); require(b.status==Status.MERGED,"historical successor fixture"); return b.frontier;
    }
}
