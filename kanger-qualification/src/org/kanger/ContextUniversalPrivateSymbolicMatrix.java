/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalGroupedRounds.*;
import org.kanger.ContextUniversalGroupedRounds.Result;
import org.kanger.ContextWitnessResultTransport.*;
import static org.kanger.ContextUniversalContinuationMatrix.*;

/** QA only: two physical private rows, free symbolic results, compound residuals and signed constraints. */
final class ContextUniversalPrivateSymbolicMatrix {
    static final class Fixture {
        final Snapshot x,a,b;final Packet common;final PrivatePool pool;
        Fixture(boolean constraint,boolean conflict)throws Exception {
            x=bind(true,"!anchor(1); !@u @v a(u,1)&&b(u,v)->r(u,v);");
            a=bind(false,"!$u a(u,1); !$v a(v,1);");
            b=bind(false,"!@u @v b(u,v); !@u @v r(u,v)&&need(v)->s(u,v); !need(2);"+(constraint?" !@u @v ~s(u,v)||~ban(v); !ban("+(conflict?2:3)+");":""));
            common=ContextPrivateWitnessPoolRunner.common(x);pool=ContextWitnessResultTransport.privatePool(common,x,a,WORK);
            require(a.primary.size()==2&&pool.sources.size()==2&&a.primary.stream().allMatch(e->e.witnesses()==1&&pool.sources.contains(e.source)),"two separate physical Source rows, one binder each");
        }
        Frame frame(boolean reverse){return new Frame(x,reverse?Arrays.asList(b,a):Arrays.asList(a,b),common,Collections.singletonMap(pool.cut,pool));}
        Set<Source> support(Origin origin,int... rows){Set<Source> s=new HashSet<>();s.add(x.primary.get(1).source);s.add(origin.source);for(int row:rows)s.add(b.primary.get(row).source);return s;}
    }
    static Set<Origin> origins(Packet p,String predicate){Set<Origin> result=new HashSet<>();for(Item item:p.items)if(item.atom.predicate.equals(predicate))for(Slot slot:item.atom.arguments)if(slot.origin!=null)result.add(slot.origin);return result;}
    static boolean literal(ContextUniversalInputResiduals.Residual c,String name,boolean positive){return c.literals.stream().anyMatch(l->l.predicate.equals(name)&&l.positive==positive);}
    static void ancestry(org.kanger.ContextUniversalFrozenConstraints.Proof p,Set<org.kanger.ContextUniversalFrozenConstraints.Proof> seen){
        if(p==null||!seen.add(p))return;ancestry(p.unit,seen);ancestry(p.horn,seen);ancestry(p.producer,seen);for(org.kanger.ContextUniversalFrozenConstraints.Proof d:p.donors)ancestry(d,seen);if(p.exchanged!=null)ancestry(p.exchanged.proof,seen);if(p.compound!=null)ancestry(p.compound.proof,seen);
    }
    static void products(Fixture f,Result r,boolean constraints){
        Packet p=r.output.packet;Set<Origin> os=origins(p,"s");require(os.size()==2&&os.stream().allMatch(o->f.pool.sources.contains(o.source))&&os.stream().map(o->o.source).distinct().count()==2,"fixed results preserve distinct physical Origin Sources");
        for(Item i:p.items)if(i.atom.predicate.equals("s")){Origin o=i.atom.arguments.get(0).origin;require(o!=null&&i.atom.positive&&i.proofs.size()==1&&i.proofs.get(0).depth==3&&i.proofs.get(0).sources.equals(f.support(o,0,1,2)),"fixed s3 has exact five Sources including its OWN private row, no sibling seed");}
        Set<Origin> units=new HashSet<>(),residuals=new HashSet<>(),routes=new HashSet<>();
        for(ContextUniversalSignedUnitExchange.Unit u:r.symbolicOutput.units)if(literal(u.clause,"r",true)){require(u.clause.literals.size()==1&&u.clause.variables.size()==1&&u.clause.origins.size()==1,"r unit retains free universal argument and rigid Origin separately");Origin o=u.clause.origins.iterator().next();require(u.support.sources.equals(f.support(o,0)),"symbolic r has exact producer/private/b sources");units.add(o);}
        for(ContextUniversalCompoundExchange.ResidualProof c:r.compoundOutput.clauses)if(c.clause.origins.size()==1&&literal(c.clause,"b",false)&&literal(c.clause,"r",true)){Origin o=c.clause.origins.iterator().next();require(c.clause.variables.size()==1&&c.clause.literals.size()==2&&c.support.sources.equals(f.support(o)),"b(Origin,v)->r(Origin,v) retains free variable and exact two physical Sources");residuals.add(o);}
        for(Round row:r.history)for(ConstraintTransfer t:row.barrier.branches.values())for(ContextUniversalOriginBindings.Route route:t.bindings.routes)routes.add(route.origin);
        Set<Origin> linked=new HashSet<>();for(Round row:r.history)for(org.kanger.ContextUniversalFrozenConstraints.Result check:row.checks.values())for(org.kanger.ContextUniversalFrozenConstraints.Proof proof:check.proofs)if(proof.clause!=null&&proof.clause.literals.size()==1&&literal(proof.clause,"s",true)&&proof.clause.variables.isEmpty()&&proof.clause.origins.size()==1){Origin o=proof.clause.origins.iterator().next();Set<org.kanger.ContextUniversalFrozenConstraints.Proof> parents=Collections.newSetFromMap(new IdentityHashMap<org.kanger.ContextUniversalFrozenConstraints.Proof,Boolean>());ancestry(proof,parents);boolean symbolic=false,compound=false;for(org.kanger.ContextUniversalFrozenConstraints.Proof parent:parents)if(parent.clause!=null&&parent.clause.origins.contains(o)){symbolic|=parent.clause.literals.size()==1&&!parent.clause.variables.isEmpty()&&literal(parent.clause,"r",true);compound|=parent.clause.literals.size()>1&&literal(parent.clause,"b",false)&&literal(parent.clause,"r",true);}if(symbolic&&compound)linked.add(o);}
        require(linked.equals(os),"actual fixed-s proof ancestry passes through BOTH symbolic r and multi-literal Origin residual");
        require(units.equals(os)&&residuals.equals(os)&&routes.containsAll(os),"both actual Origins covered in symbolic, compound and theorem-specific argument routes");
        require(p.items.stream().noneMatch(i->i.atom.predicate.equals("a")||i.atom.predicate.equals("b")||i.atom.predicate.equals("r")||(i.atom.positive&&(i.atom.predicate.equals("need")||i.atom.predicate.equals("ban")))),"no raw private seeds, foreign b/need/ban or fabricated fixed r disclosed");
        if(constraints){for(String name:new String[]{"need","ban"}){Item i=p.items.stream().filter(z->z.atom.predicate.equals(name)).findFirst().orElseThrow(()->new AssertionError("missing signed consequence "+name));require(!i.atom.positive&&i.proofs.size()==2,"negative "+name+" retains BOTH exact Source alternatives");Set<Source> seeds=new HashSet<>();for(Support s:i.proofs){Set<Source> own=new HashSet<>(s.sources);own.retainAll(f.pool.sources);require(own.size()==1&&s.depth==(name.equals("need")?3:4),"signed consequence keeps per-Source grade without merging independent seeds");Source seed=own.iterator().next();Origin o=os.stream().filter(z->z.source.equals(seed)).findFirst().get();require(s.sources.equals(name.equals("need")?f.support(o,0,1,3,4):f.support(o,0,1,2,3)),"full exact signed Source alternative, no subset pruning");seeds.add(seed);}require(seeds.equals(f.pool.sources),"both physical seeds represented independently");}}
        PrivatePool out=r.output.privatePools.get(f.pool.cut);require(out.sources.equals(f.pool.sources)&&out.snapshot==f.a&&out.declarations.size()==f.pool.declarations.size()&&origins(out.local,"s").equals(os),"complete actual private manifest/local products retained");require(f.pool.common==f.common&&origins(f.pool.local,"s").isEmpty(),"original private input unchanged");
    }
    static void stableCases()throws Exception {
        for(boolean constraints:new boolean[]{false,true}){Fixture f=new Fixture(constraints,false);Result previous=null;for(boolean reverse:new boolean[]{false,true}){Frame frame=f.frame(reverse);SessionRun initial=run(frame,8,WORK,4096);audited(frame,frame.input,frame.pools,frame.units,frame.compounds,8,initial);products(f,initial.result,constraints);SessionRun continued=stable(frame,1);same(initial.result,continued.result);if(previous!=null)same(previous,continued.result);previous=continued.result;
                SessionRun low=run(frame,8,1,4096);incomplete(low,"insufficient cumulative work exposes no prefix");noStableAudit(frame,low);
            }}
        System.out.println("PRIVATE_SYMBOLIC_MATRIX sources=2 cutOrders=2 fixtures=2 continuationsPerCase=1 exactSignedAlternatives=2");
    }
    static Map<Cut,Set<Support>> evidence(Result result){Map<Cut,Set<Support>> map=new HashMap<>();for(Refutation ref:result.refutations.values()){Set<Support> supports=new HashSet<>();for(org.kanger.ContextUniversalFrozenConstraints.Proof proof:ref.proofs)supports.add(proof.support);map.put(ref.cut,supports);}return map;}
    static void refutedCases()throws Exception {
        Fixture f=new Fixture(true,true);Map<Cut,Set<Support>> previous=null;
        for(boolean reverse:new boolean[]{false,true}){Frame frame=f.frame(reverse);SessionRun run=run(frame,8,WORK,4096);require(run.result.status==Status.REFUTED&&run.result.output==null&&run.result.symbolicOutput==null&&run.result.compoundOutput==null&&run.result.history.isEmpty()&&run.metrics.entries==0&&!run.result.refutations.isEmpty(),"signed conflict exports sourced rejection only, no stable prefix: "+run.result.phase+": "+run.result.reason);noStableAudit(frame,run);Map<Cut,Set<Support>> current=evidence(run.result);for(Set<Support> proofs:current.values())for(Support s:proofs){Set<Source> own=new HashSet<>(s.sources);own.retainAll(f.pool.sources);require(own.size()==1&&s.sources.size()==7&&s.depth<=16,"empty proof retains one actual private seed and all six rule/fact Sources");Set<Source> expected=new HashSet<>();expected.add(f.x.primary.get(1).source);expected.addAll(own);for(org.kanger.ContextProductionNativeBinding.Entry e:f.b.primary)expected.add(e.source);require(s.sources.equals(expected),"exact complete physical Source rejection");}if(previous!=null)require(previous.equals(current),"same full per-cut Source/grade evidence in reverse enumeration");previous=current;
            Result ordinary=ContextUniversalGroupedRounds.run(f.x,frame.cuts,frame.input,frame.pools,frame.units,frame.compounds,frame.limits,frame.local,8,WORK);require(ordinary.status==Status.REFUTED&&evidence(ordinary).equals(current),"ordinary rejection agrees on full per-cut Source/grade alternatives: "+ordinary.phase+": "+ordinary.reason);
        }
        System.out.println("PRIVATE_SYMBOLIC_CONSTRAINT_REJECTION orders=2 ordinaryComparisons=2");
    }
}
