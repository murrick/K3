/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalQuotientModel.*;

/** Quotient witness controls over actual constants and ALL unmentioned admissible values. */
public final class ContextUniversalQuotientModelRunner {
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
    private static ContextUniversalUnitLedger open(Snapshot x,Snapshot a) {
        ContextUniversalUnitLedger.Opened o=ContextUniversalUnitLedger.openPrimary(x,a,WORK); require(o.opened,"sealed primary ledger"); return o.ledger;
    }
    private static Model model(ContextUniversalUnitLedger ledger) {
        Result r=ContextUniversalQuotientModel.find(ledger,20000,1000000,WORK);
        require(r.modeled && r.model!=null,"checked quotient witness: "+r.reason);
        require(r.model.ledger==ledger && r.model.scope==Scope.ORDINARY_FLAT_UNIVERSAL_QUOTIENT_MODEL,"exact historical model scope");
        require(ContextUniversalQuotientModel.check(ledger,r.model,20000,WORK).valid,"independent witness check"); return r.model;
    }
    private static SemanticTermSnapshot value(String text) throws Exception {
        return snapshot(false,"!value("+text+");").primary.get(0).clauses.get(0).literals.get(0).arguments.get(0).value;
    }
    private static void noArtifact(Result r,String why) { require(!r.modeled && r.model==null,why+": "+r.reason); }
    private static boolean truth(Clause clause,Model model,Map<Integer,SemanticTermSnapshot> values) {
        for(Atom atom:clause.literals) {
            List<SemanticTermSnapshot> arguments=new ArrayList<>();
            for(Slot slot:atom.arguments) arguments.add(slot.variable>=0?values.get(slot.variable):slot.value);
            if(model.holds(atom.predicate,arguments)==atom.positive) return true;
        }
        return false;
    }
    private static void freshInstances(Clause clause,List<Integer> vars,int at,Map<Integer,SemanticTermSnapshot> values,List<SemanticTermSnapshot> domain,Model model) {
        if(at==vars.size()) { require(truth(clause,model,values),"chosen model satisfies concrete original instances with multiple distinct unmentioned values"); return; }
        for(SemanticTermSnapshot v:domain) { values.put(vars.get(at),v); freshInstances(clause,vars,at+1,values,domain,model); }
        values.remove(vars.get(at));
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-quotient-model-").toString());
            Snapshot x=snapshot(true,"!@x p(x);"),empty=snapshot(false,""); ContextUniversalUnitLedger l=open(x,empty);
            Model generic=model(l);
            require(generic.constants.isEmpty() && generic.otherIndex==0 && generic.positiveProfiles.size()==1,"constant-free universal has abstract OTHER representative, not empty known-domain shortcut");
            SemanticTermSnapshot freshA=value("FreshQuotientA"),freshB=value("FreshQuotientB"),future=value("999999");
            require(generic.holds("p",Arrays.asList(freshA)) && generic.holds("p",Arrays.asList(freshB)) && generic.holds("p",Arrays.asList(future)),"OTHER interprets every unmentioned value, including future numeric/symbolic values");
            require(!ContextUniversalQuotientModel.check(open(x,empty),generic,20000,WORK).valid,"same snapshots with a different ledger cannot reuse historical witness authority");
            require(!ContextUniversalQuotientModel.check(l,generic,20000,1).valid,"model check work bound");
            noArtifact(ContextUniversalQuotientModel.find(l,20000,1,WORK),"model assignment bound cannot turn first false assignment into a witness");
            noArtifact(ContextUniversalQuotientModel.find(l,1,1000000,WORK),"construction and final check share instance allowance; no unchecked witness prefix");
            noArtifact(ContextUniversalQuotientModel.find(l,20000,1000000,1),"shared model work bound");
            Snapshot neg=snapshot(false,"!@x ~p(x);"); ContextUniversalUnitLedger opposite=open(x,neg);
            Result noModel=ContextUniversalQuotientModel.find(opposite,20000,1000000,WORK);
            noArtifact(noModel,"constant-free universal opposition is not vacuously modeled over empty known constants");
            require(noModel.reason.contains("no sourced collision proof"),"model failure cannot invent a source-bound collision verdict");
            Snapshot constants=snapshot(true,"!p(1); !~p(2); !@x q(x);"); ContextUniversalUnitLedger cl=open(constants,empty); Model fixed=model(cl);
            SemanticTermSnapshot one=value("1.0"),two=value("2"),nine=value("9");
            require(fixed.constants.size()==2 && fixed.otherIndex==2,"every semantically distinct actual constant is a singleton, plus abstract OTHER");
            require(fixed.holds("p",Arrays.asList(one)) && !fixed.holds("p",Arrays.asList(two)) && !fixed.holds("p",Arrays.asList(nine)),"semantic numeric equality maps1.0 to actual1; absent p profile is false only in chosen model");
            require(fixed.holds("q",Arrays.asList(one)) && fixed.holds("q",Arrays.asList(two)) && fixed.holds("q",Arrays.asList(freshA)),"universal includes actual constants AND all other values");
            Snapshot pair=snapshot(true,"!@x p(x,x); !~p(1,2);"); ContextUniversalUnitLedger pl=open(pair,empty); Model relational=model(pl);
            require(relational.holds("p",Arrays.asList(one,one)) && relational.holds("p",Arrays.asList(two,two)) && !relational.holds("p",Arrays.asList(one,two)),"distinct known singleton values are never collapsed when checking repeated variables");
            require(relational.holds("p",Arrays.asList(freshA,freshA)) && relational.holds("p",Arrays.asList(freshA,freshB)),"collapsing unknown values chooses a legitimate interpretation; it asserts no inequality knowledge");
            List<SemanticTermSnapshot> samples=Arrays.asList(one,two,freshA,freshB,nine);
            for(ContextUniversalUnitLedger.Row row:pl.rows) freshInstances(row.clause,new ArrayList<>(row.clause.variables),0,new HashMap<>(),samples,relational);
            Snapshot clause=snapshot(true,"!@x p(x)||q(x);"); ContextUniversalUnitLedger dl=open(clause,empty); Model disjunction=model(dl);
            require(disjunction.holds("p",Arrays.asList(freshA)) || disjunction.holds("q",Arrays.asList(freshA)),"full universal disjunction has a chosen interpretation for unmentioned values");
            Snapshot positive=snapshot(true,"!@x @y p(x)||p(y);"),negative=snapshot(false,"!@x @y ~p(x)||~p(y);");
            noArtifact(ContextUniversalQuotientModel.find(open(positive,negative),20000,1000000,WORK),"diagonal factoring counterexample has no quotient witness even before inference");
            Snapshot unrelated=snapshot(true,"!anchor(1);"),a=snapshot(false,"!@x p(x);"),b=snapshot(false,"!~p(9);");
            Model am=model(open(unrelated,a)),bm=model(open(unrelated,b));
            require(am.holds("p",Arrays.asList(nine)) && !bm.holds("p",Arrays.asList(nine)),"independent private cuts have separate model interpretations, never communal sibling facts");
            Snapshot derive=snapshot(true,"!@x p(x);"),rules=snapshot(false,"!@x p(x)->q(x); !@x q(x)->r(x);");
            ContextUniversalUnitLedger current=open(derive,rules);
            for(int step=0;step<6;step++) {
                ContextUniversalUnitLedger.Step s=ContextUniversalUnitLedger.stepFactored(current,16,WORK);
                require(s.status==ContextUniversalUnitLedger.Status.ADVANCED,"consistent actual closed ledger fixture"); current=s.next; if(!s.changed) break;
            }
            Model closed=model(current);
            require(closed.holds("p",Arrays.asList(freshB)) && closed.holds("q",Arrays.asList(freshB)) && closed.holds("r",Arrays.asList(future)),"model covers original and actually derived generic clauses over all values");
            boolean immutable=false; try { generic.positiveProfiles.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"positive profile inventory immutable");
            immutable=false; try { generic.positiveProfiles.get(0).arguments.clear(); } catch(UnsupportedOperationException expected) { immutable=true; } require(immutable,"profile tuples immutable");
            require(l.rows.size()==1 && l.rows.get(0).support.depth==0,"model search creates no knowledge row or inference support");
            noArtifact(ContextUniversalQuotientModel.find(null,20000,1000000,WORK),"missing ledger refuses witness");
            System.out.println("CONTEXT_UNIVERSAL_QUOTIENT_MODEL_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
