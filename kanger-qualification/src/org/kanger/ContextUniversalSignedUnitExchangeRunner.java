/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.*;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalSignedUnitExchange.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Symbolic transfer is universal reasoning, never a sample over the stored finite domain. */
public final class ContextUniversalSignedUnitExchangeRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static ContextUniversalNativeRows.Result rows(Snapshot x,Snapshot a){ContextUniversalNativeRows.Result r=ContextUniversalNativeRows.inspect(x,a,64,WORK);require(r.classified&&r.withinShape,r.reason);return r;}
    static Result close(Snapshot x,Snapshot a,Packet p,PrivatePool pool,Exchange exchange){Result r=exchange==null?ContextUniversalFrozenConstraints.closeSigned(rows(x,a),p,pool,16,16,512,256,WORK):ContextUniversalFrozenConstraints.closeSignedUnits(rows(x,a),p,pool,exchange,16,16,512,256,WORK);require(r.closed,r.reason);ContextUniversalFrozenConstraints.check(r,16,()->{});return r;}
    static Map<Cut,Result> cuts(Snapshot x,List<Snapshot> foreign,Packet p,Map<Cut,PrivatePool> pools,Exchange exchange){Map<Cut,Result> r=new LinkedHashMap<>();r.put(Cut.of(x),close(x,null,p,null,exchange));for(Snapshot a:foreign)r.put(Cut.of(a),close(x,a,p,pools.get(Cut.of(a)),exchange));return r;}
    static Exchange collect(Snapshot x,Packet p,Map<Cut,Result> r,int depth){ContextUniversalSignedUnitExchange.Collection c=ContextUniversalSignedUnitExchange.collect(x,p,new ArrayList<>(r.keySet()),r,512,256,depth,WORK);require(c.collected&&c.output!=null,c.reason);ContextUniversalSignedUnitExchange.check(c.output,x,p,depth,()->{});for(Unit unit:c.output.units){require(unit.owner.proofs.contains(unit.proof)&&unit.clause==unit.proof.clause&&unit.support==unit.proof.support,"actual symbolic proof references");require(unit.clause.literals.size()==1&&!unit.clause.variables.isEmpty()&&unit.support.sources.stream().anyMatch(s->s.localX),"only X-dependent universal units");}return c.output;}
    static void no(ContextUniversalSignedUnitExchange.Collection c,String why){require(!c.collected&&c.output==null,why+": "+c.reason);}
    static boolean has(Exchange e,String predicate,boolean positive){return e.units.stream().anyMatch(u->u.clause.literals.get(0).predicate.equals(predicate)&&u.clause.literals.get(0).positive==positive);}
    static void example()throws Exception {
        String rule="!@w p(w,1)->q(w,1);",p="!@w p(w,1);",nq="!@w ~q(w,1);";Snapshot x=bind(true,"!anchor(1); "+rule),a=bind(false,p),b=bind(false,nq);Packet common=ContextPrivateWitnessPoolRunner.common(x);Map<Cut,Result> before=cuts(x,Arrays.asList(a,b),common,Collections.emptyMap(),null);require(before.values().stream().noneMatch(r->r.xRefuted||r.foreignRefuted),"each independent initial cut compatible");Exchange exchange=collect(x,common,before,1);
        require(exchange.units.size()==2&&has(exchange,"q",true)&&has(exchange,"p",false),"both signed symbolic products preserved without finite samples");
        for(Unit unit:exchange.units)require(unit.support.depth==1&&unit.support.sources.size()==2,"symbolic production1 with complete two Sources");
        Set<Source> collision=new HashSet<>(Arrays.asList(ContextPrivateWitnessPoolRunner.source(x,rule),ContextPrivateWitnessPoolRunner.source(a,p),ContextPrivateWitnessPoolRunner.source(b,nq)));
        Result afterA=close(x,a,common,null,exchange),afterB=close(x,b,common,null,exchange);require(afterA.xRefuted&&afterB.xRefuted,"next symbolic input refutes both affected cuts");
        for(Result r:Arrays.asList(afterA,afterB)){require(r.exchange==exchange&&r.emptyProofs.stream().anyMatch(proof->proof.support.depth==2&&proof.support.sources.equals(collision)),"empty2 retains exact cross-context Source history");require(r.proofs.stream().anyMatch(proof->proof.exchanged!=null&&exchange.units.contains(proof.exchanged)),"explicit actual symbolic proof root");}
        require(!close(x,null,common,null,exchange).xRefuted,"self-X does not borrow raw foreign universal facts");
        require(!close(x,a,common,null,null).xRefuted,"old immutable input never sees same-round products");
        Map<Cut,Result> reverse=cuts(x,Arrays.asList(b,a),common,Collections.emptyMap(),null);Exchange back=collect(x,common,reverse,1);for(Unit u:exchange.units)require(back.units.stream().anyMatch(v->u.support.equals(v.support)&&u.clause.equivalent(v.clause,()->{})),"donor order invariant exact alternatives");
        no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),before,512,256,0,WORK),"symbolic grade limit no prefix");
        no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),before,1,256,16,WORK),"unit bucket limit no prefix");
        no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),before,512,256,16,1),"work limit no prefix");
        Map<Cut,Result> missing=new LinkedHashMap<>(before);missing.remove(Cut.of(x));no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(missing.keySet()),missing,512,256,16,WORK),"mandatory self-X");
        no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),missing,512,256,16,WORK),"missing later cut");
        Map<Cut,Result> swapped=new LinkedHashMap<>(before);swapped.put(Cut.of(a),before.get(Cut.of(b)));no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),swapped,512,256,16,WORK),"swapped branch receipts");
        Packet equal=ContextPrivateWitnessPoolRunner.common(x);Result wrong=ContextUniversalFrozenConstraints.closeSignedUnits(rows(x,a),equal,null,exchange,16,16,512,256,WORK);require(!wrong.closed&&wrong.proofs.isEmpty()&&wrong.emptyProofs.isEmpty(),"equal new Packet cannot replace exact symbolic common basis");
        Snapshot otherX=bind(true,"!anchor(1); "+rule);wrong=ContextUniversalFrozenConstraints.closeSignedUnits(rows(otherX,a),common,null,exchange,16,16,512,256,WORK);require(!wrong.closed&&wrong.proofs.isEmpty(),"reconstructed X cannot replace actual symbolic owner");
        wrong=ContextUniversalFrozenConstraints.closeSignedUnits(rows(x,a),common,null,exchange,16,0,512,256,WORK);require(!wrong.closed&&wrong.proofs.isEmpty(),"input exploration bound never becomes contradiction");
        Map<Cut,Result> mixed=new LinkedHashMap<>(before);mixed.put(Cut.of(x),close(x,null,common,null,exchange));no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(before.keySet()),mixed,512,256,16,WORK),"same Packet with different symbolic input barrier forbidden");
        Map<Cut,Result> refuted=cuts(x,Arrays.asList(a,b),common,Collections.emptyMap(),exchange);no(ContextUniversalSignedUnitExchange.collect(x,common,new ArrayList<>(refuted.keySet()),refuted,512,256,16,WORK),"refuted next input never republishes explosive products");
        try{exchange.units.clear();throw new AssertionError("mutable units");}catch(UnsupportedOperationException expected){++checks;}try{exchange.checks.clear();throw new AssertionError("mutable checks");}catch(UnsupportedOperationException expected){++checks;}
    }
    static void separationAndAlternatives()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@w p(w,1);"),b=bind(false,"!@w ~p(w,1);");Packet common=ContextPrivateWitnessPoolRunner.common(x);Exchange e=collect(x,common,cuts(x,Arrays.asList(a,b),common,Collections.emptyMap(),null),16);require(e.units.isEmpty(),"independent contradictory universal opinions not communalized by unrelated anchor");
        Snapshot owner=bind(true,"!anchor(1); !@w p(w,1)->q(w,1);"),seed=bind(false,"!@w p(w,1);"),loop=bind(false,"!@v q(v,1)->q(v,1);");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(seed,loop),WORK);Packet cp=ContextPrivateWitnessPoolRunner.common(owner);Map<Cut,Result> r=cuts(owner,Collections.singletonList(group),cp,Collections.emptyMap(),null);Exchange alts=collect(owner,cp,r,2);
        List<Unit> q=new ArrayList<>();for(Unit u:alts.units)if(u.clause.literals.get(0).predicate.equals("q"))q.add(u);require(q.size()==2&&q.stream().anyMatch(u->u.support.depth==1&&u.support.sources.size()==2)&&q.stream().anyMatch(u->u.support.depth==2&&u.support.sources.size()==3),"alpha equivalent universal Source supersets keep their separate minima");
        no(ContextUniversalSignedUnitExchange.collect(owner,cp,new ArrayList<>(r.keySet()),r,512,1,16,WORK),"Source alternative cap");no(ContextUniversalSignedUnitExchange.collect(owner,cp,new ArrayList<>(r.keySet()),r,512,256,1,WORK),"shorter different Source proof cannot cover deeper superset");
        Snapshot pairX=bind(true,"!anchor(1); !@w p(w,w)->q(w,w);"),pair=bind(false,"!@w p(w,w);"),off=bind(false,"!~q(1,2);");Packet pp=ContextPrivateWitnessPoolRunner.common(pairX);Exchange pe=collect(pairX,pp,cuts(pairX,Arrays.asList(pair,off),pp,Collections.emptyMap(),null),16);require(!close(pairX,off,pp,null,pe).xRefuted,"repeated universal variable cannot split into two distinct values");
        Snapshot on=bind(false,"!~q(987654,987654);");require(close(pairX,on,pp,null,pe).xRefuted,"universal applies to previously unmentioned ordinary value");
    }
    static void origins()throws Exception {
        Snapshot x=bind(true,"!$w seed(w,1); !@v top(v,1); !@w @v seed(w,1)&&top(v,1)->p(w,v);"),a=bind(false,"!@w @v p(w,v)->q(w,v);");Packet common=ContextPrivateWitnessPoolRunner.common(x);Exchange e=collect(x,common,cuts(x,Collections.singletonList(a),common,Collections.emptyMap(),null),16);require(e.units.stream().anyMatch(u->u.clause.variables.size()==1&&!u.clause.origins.isEmpty()),"mixed rigid Origin/universal variable remains typed symbolic unit");
        Snapshot other=bind(false,"!$w other(w,1); !@w @v other(w,1)&&top(v,1)->~q(w,v);");PrivatePool pool=ContextWitnessResultTransport.privatePool(common,x,other,WORK);Result separate=close(x,other,common,pool,e);require(!separate.xRefuted,"different physical witness Origins cannot unify via universal renaming");
        Snapshot negative=bind(false,"!@w @v ~q(w,v);");Result collision=close(x,negative,common,null,e);require(collision.xRefuted&&collision.emptyProofs.stream().anyMatch(p->p.support.sources.stream().anyMatch(s->s.equals(x.primary.get(0).source))),"symbolic rigid binding keeps actual seed Source in contradiction");
        for(Proof p:collision.proofs)if(p.exchanged!=null)require(p.exchanged.owner.local.items.stream().anyMatch(i->i.atom.arguments.stream().anyMatch(s->s.origin!=null)),"Origin proof retains actual original input Item frame");
    }
    static void chain()throws Exception {
        Snapshot x=bind(true,"!anchor(1); !@w p(w,1)->q(w,1);"),a=bind(false,"!@w p(w,1);"),b=bind(false,"!@w q(w,1)->r(w,1);");Packet common=ContextPrivateWitnessPoolRunner.common(x);List<Snapshot> targets=Arrays.asList(a,b);
        Exchange first=collect(x,common,cuts(x,targets,common,Collections.emptyMap(),null),16);require(has(first,"q",true)&&!has(first,"r",true),"first immutable symbolic barrier");
        Map<Cut,Result> next=cuts(x,targets,common,Collections.emptyMap(),first);Exchange second=collect(x,common,next,16);require(has(second,"r",true),"second symbolic barrier preserves derived universal");
        Unit r=second.units.stream().filter(u->u.clause.literals.get(0).predicate.equals("r")).findFirst().get();require(r.support.depth==2&&r.support.sources.size()==3&&r.owner.exchange==first,"nested owning receipt preserves grade2 and full three Sources");
        Snapshot negative=bind(false,"!@w ~r(w,1);");Result collision=close(x,negative,common,null,second);require(collision.xRefuted&&collision.emptyProofs.stream().anyMatch(p->p.support.depth==3&&p.support.sources.size()==4),"third supplied target consumes nested receipt with exact empty3");
        Result concrete=close(x,bind(false,"!@w r(w,1)&&need(w,1)->s(w,1); !need(1234567,1);"),common,null,second);ConstraintTransfer t=ContextWitnessResultTransport.transferConstraints(concrete,common,16,WORK);require(t.transferred,t.reason);require(t.output.items.stream().anyMatch(i->i.atom.predicate.equals("s")),"universal import participates in fresh concrete production");ContextUniversalSignedProductAudit.Result audit=ContextUniversalSignedProductAudit.bind(concrete,t,65536,WORK);require(audit.bound,audit.reason);
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("signed-exchange-").toString());example();separationAndAlternatives();origins();chain();System.out.println("CONTEXT_UNIVERSAL_SIGNED_UNIT_EXCHANGE_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
