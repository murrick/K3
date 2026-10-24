/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionFamilyAssembly.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Whole fact/rule Sources must retain all clauses and authenticated depth0 seeds. */
public final class ContextMixedPrimaryFamilyRunner {
    static int checks;static final int WORK=64000000,EVENTS=500000;
    static final String BUNDLE="!$w seed(w,1)&&flag(w,1);",MIXED="!@w gate(1)&&(seed(w,1)&&gate(1)->mid(w,1));",WARM="!@w seed(w,1)->warm(w,1);",DONE="!@w mid(w,1)->done(w,1);",OUT="!@w done(w,1)->out(w,1);",NEG="!@w done(w,1)->~flag(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot s,String text){return ContextPrivateWitnessPoolRunner.source(s,text);}
    static Set<Source> set(Source...s){return new HashSet<>(Arrays.asList(s));}
    static Item item(Packet p,String name){return ContextPrivateWitnessPoolRunner.item(p,name);}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static void one(boolean local,boolean grouped,boolean reverse,boolean negative)throws Exception{
        String end=negative?NEG:OUT;Snapshot x=bind(true,BUNDLE+" "+(local?MIXED:WARM)+" "+end),a=bind(false,local?WARM:MIXED),b=bind(false,DONE);List<Snapshot> members=reverse?Arrays.asList(b,a):Arrays.asList(a,b),cuts=grouped?Arrays.asList(ContextProductionNativeBinding.trusted("own",members,WORK)):members;ContextProductionFamilyAssembly.Result assembled=ContextProductionFamilyAssembly.assemble(x,cuts,WORK);require(assembled.assembled,"whole mixed row assembles: "+assembled.reason);Artifact f=assembled.artifact;Evaluation e=f.evaluate(12,16,WORK,EVENTS);
        System.out.println("MIXED_PRIMARY_CASE local="+local+" grouped="+grouped+" reverse="+reverse+" negative="+negative+" status="+e.status+" reason="+e.reason);
        require(e.status==(negative?Status.COLLISION:Status.FIXED_POINT),"whole mixed-source qualification must cover fact and rule, never INCOMPLETE: "+e.reason);
        Source mixed=source(local?x:a,MIXED);RowUse row=f.rows.stream().filter(r->r.source.equals(mixed)).findFirst().get();require(row.seedLiterals==1,"one original ground seed literal recorded for whole mixed row");require(f.rows.size()==x.primary.size()+a.primary.size()+b.primary.size(),"exactly one disposition per original whole row");
        if(local){require(row.use==Use.X_SEEDS_AND_PRODUCER&&has(f.input,"gate"),"X mixed fact seeded without removing its whole producer");require(item(f.input,"gate").proofs.stream().anyMatch(p->p.depth==0&&p.sources.equals(set(mixed))),"X fact has original whole Source depth0");}
        else {require(row.use.name().equals("PRIVATE_SEEDS_AND_PRODUCER")&&!has(f.input,"gate"),"private mixed fact never starts in common X");PrivatePool p=f.plan.branches.stream().filter(v->v.privatePool!=null).findFirst().get().privatePool;require(item(p.local,"gate").proofs.stream().anyMatch(q->q.depth==0&&q.sources.equals(set(mixed))),"private ground clause has exact whole Source depth0");require(f.plan.branches.stream().flatMap(v->v.declarations.stream()).anyMatch(d->d.source.equals(mixed)&&d.statement.equals(MIXED)),"original whole fact/rule statement replayed, not a synthetic implication Source");}
        Set<Source> expected=set(source(x,BUNDLE),mixed,source(b,DONE),source(x,end));
        if(negative){require(!e.refutations.isEmpty()&&e.barrier>0,"actual derived whole-source collision");for(Refutation r:e.refutations)for(Support p:r.proofs)require(p.depth==3&&p.sources.equals(expected),"exact four original Sources; fact and implication share one Source at cumulative depth3");}
        else {Packet output=e.qualification.fixedPoint.packet;require(item(output,"out").proofs.stream().anyMatch(p->p.depth==3&&p.sources.equals(expected)),"exact depth3 whole-source out proof");require(has(output,"gate")==local,"only already-common X fact is common; private raw fact stays private");}
        require(f.evaluate(12,0,WORK,EVENTS).status==Status.INCOMPLETE,"depth0 cannot certify derived whole-source consequences");
    }
    static void unused(boolean local)throws Exception{
        String mixed="!@w (unused(1)&&(seed(w,1)->mid(w,1)));";
        Snapshot x=bind(true,BUNDLE+" "+(local?mixed:WARM)+" "+OUT),a=local?null:bind(false,mixed);
        ContextProductionFamilyAssembly.Result f=ContextProductionFamilyAssembly.assemble(x,local?Collections.emptyList():Arrays.asList(a),WORK);require(f.assembled,"unused ground conjunct assembles");Evaluation e=f.artifact.evaluate(12,16,WORK,EVENTS);require(e.status==Status.FIXED_POINT,"native depth0 materialization of unused primary conjunct is authenticated: "+e.reason);
        Source owner=source(local?x:a,mixed);Packet pool=local?e.qualification.fixedPoint.packet:e.qualification.fixedPoint.privatePools.get(Cut.of(a)).local;
        require(item(pool,"unused").proofs.stream().allMatch(p->p.depth==0&&p.sources.equals(set(owner))),"primary materialization preserves exact Source at depth0");require(has(e.qualification.fixedPoint.packet,"unused")==local,"unused private ground fact stays local");
    }
    static void provenance()throws Exception{
        Snapshot x=bind(true,"!anchor(1); !gate(1); !@w missing(w,1)->warm(w,1);"),a=bind(false,"!gate(1);"),other=bind(false,"!gate(1);");Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool p=ContextWitnessResultTransport.privatePool(common,x,a,WORK);Source original=a.primary.get(0).source;require(ContextWitnessResultTransport.verifyPrivateClosed(p,set(original),0,WORK,EVENTS)==1,"authenticated fact closes at depth0, no invented inference step");
        ContextWitnessResultTransport.Result missing=ContextWitnessResultTransport.exportHornUnchanged(common,Cut.of(other),Arrays.asList(new ContextProductionInputs.Declaration(other.primary.get(0).source,"!gate(1);")),0,WORK,EVENTS);require(!missing.exported&&missing.packet==null,"a same-valued fact without its exact depth0 Source cannot be treated as an axiom");
        Snapshot bad=bind(false,"!@u @v gate(1)&&(missing(u,1)->out(v,1));");ContextProductionFamilyAssembly.Result malformed=ContextProductionFamilyAssembly.assemble(x,Arrays.asList(bad),WORK);require(malformed.assembled&&malformed.artifact.evaluate(12,16,WORK,EVENTS).status==Status.INCOMPLETE,"a ground conjunct cannot waive an unsupported head-only variable in the same whole Source");
        Snapshot generic=bind(false,"!@w generic(w,1);");ContextWitnessResultTransport.Result univ=ContextWitnessResultTransport.exportHornUnchanged(common,Cut.of(generic),Arrays.asList(new ContextProductionInputs.Declaration(generic.primary.get(0).source,generic.primary.get(0).statement)),16,WORK,EVENTS);require(!univ.exported,"universal unit cannot masquerade as authenticated ground seed");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("mixed-primary-family-").toString());for(boolean l:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(l,g,r,n);unused(false);unused(true);provenance();System.out.println("CONTEXT_MIXED_PRIMARY_FAMILY_PASS checks="+(checks+ContextPrivateWitnessPoolRunner.checks+ContextTrustedWitnessScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
