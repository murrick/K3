/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionFamilyAssembly.*;
import org.kanger.ContextWitnessResultTransport.*;

/** Complete native private-row accounting, no sibling fact sharing, and exact seeded support. */
public final class ContextPrivateWitnessAssemblyRunner {
    static int checks;static final int WORK=64000000,EVENTS=500000;
    static final String MID="!@w seed(w,1)&&gate(1)&&spare(1)->mid(w,1);",DONE="!@w mid(w,1)->done(w,1);",OUT="!@w done(w,1)->out(w,1);",NEG="!@w done(w,1)->~flag(w,1);",BUNDLE="!$w seed(w,1)&&flag(w,1);",FACT="!gate(1)&&spare(1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot s,String text){return ContextPrivateWitnessPoolRunner.source(s,text);}
    static Set<Source> set(Source...s){return new HashSet<>(Arrays.asList(s));}
    static Item item(Packet p,String name){return ContextPrivateWitnessPoolRunner.item(p,name);}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static Artifact assemble(Snapshot x,List<Snapshot> cuts){ContextProductionFamilyAssembly.Result r=ContextProductionFamilyAssembly.assemble(x,cuts,WORK);require(r.assembled,"complete native assembly: "+r.reason);return r.artifact;}
    static void one(boolean reverse,boolean grouped,boolean negative)throws Exception{
        Snapshot x=bind(true,"!anchor(1); "+MID+" "+(negative?NEG:OUT)),a=bind(false,BUNDLE),b=bind(false,FACT+" "+DONE);List<Snapshot> members=reverse?Arrays.asList(b,a):Arrays.asList(a,b),cuts=grouped?Arrays.asList(ContextProductionNativeBinding.trusted("own",members,WORK)):members;
        Artifact f=assemble(x,cuts);require(f.rows.size()==x.primary.size()+a.primary.size()+b.primary.size(),"one row per whole original primary across all members");require(f.seedAtoms==1&&!has(f.input,"gate")&&!has(f.input,"seed"),"only X anchor starts common, private witness/ground facts absent");
        for(Snapshot member:Arrays.asList(a,b))for(Entry entry:member.primary){RowUse row=f.rows.stream().filter(r->r.source.equals(entry.source)).findFirst().get();boolean seeded=entry.statement.equals(BUNDLE)||entry.statement.equals(FACT);require(row.use==(seeded?Use.PRIVATE_SEEDS:Use.FOREIGN_CUT)&&row.seedLiterals==(seeded?2:0),"exact whole-source private seed/rule row accounting");}
        Evaluation e=f.evaluate(12,16,WORK,EVENTS);
        require(e.status==(negative?(grouped?Status.COLLISION:Status.INCOMPLETE):Status.FIXED_POINT),"grouping determines private ground-fact availability: "+e.reason);
        if(negative&&grouped){require(!e.refutations.isEmpty()&&e.barrier==1,"same trusted cut may complete a native chain then refute at barrier1");for(Refutation r:e.refutations)for(Support p:r.proofs)require(p.depth==3&&p.sources.equals(set(source(a,BUNDLE),source(b,FACT),source(b,DONE),source(x,MID),source(x,NEG))),"five original whole Sources, compound fact used once, cumulative depth3");}
        else if(!negative){Packet shared=e.qualification.fixedPoint.packet;require(!has(shared,"seed")&&!has(shared,"flag")&&!has(shared,"gate")&&!has(shared,"spare"),"no raw private witness or ground fact imported into common X");require(has(shared,"out")==grouped,"independent sibling cannot lend gate/spare to A");if(grouped)require(item(shared,"out").proofs.stream().anyMatch(p->p.depth==3&&p.sources.equals(set(source(a,BUNDLE),source(b,FACT),source(b,DONE),source(x,MID),source(x,OUT)))),"exact five-source result without clause-wise Source splitting");}
        else require(e.refutations.isEmpty(),"independent siblings produce no invented refutation");
    }
    static void alternatives()throws Exception{
        Snapshot x=bind(true,"!anchor(1); "+MID+" "+OUT),a=bind(false,BUNDLE),b=bind(false,FACT+" "+DONE),c=bind(false,FACT);Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b,c),WORK);Evaluation e=assemble(x,Arrays.asList(group)).evaluate(12,16,WORK,EVENTS);require(e.status==Status.FIXED_POINT,"duplicate member ground facts retain distinct proof alternatives");Item out=item(e.qualification.fixedPoint.packet,"out");require(out.proofs.size()==3,"four donor combinations retain three distinct exact whole-source alternatives");Set<Set<Source>> actual=new HashSet<>();for(Support p:out.proofs){require(p.depth==3,"all alternatives preserve cumulative depth3");actual.add(p.sources);}Set<Source> core=set(source(a,BUNDLE),source(b,DONE),source(x,MID),source(x,OUT));Set<Set<Source>> expected=new HashSet<>();for(Source gate:Arrays.asList(source(b,FACT),source(c,FACT)))for(Source spare:Arrays.asList(source(b,FACT),source(c,FACT))){Set<Source> support=new HashSet<>(core);support.add(gate);support.add(spare);expected.add(support);}require(actual.equals(expected),"same-source donor combinations collapse only identical exact support sets");
    }
    static void boundaries()throws Exception{
        Snapshot x=bind(true,"!anchor(1); !@w seed(w,1)&&gate(1)->out(w,1);"),a=bind(false,BUNDLE),mixed=bind(false,"!@w gate(1)&&(seed(w,1)->mid(w,1));");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,mixed),WORK);Artifact f=assemble(x,Arrays.asList(group));RowUse row=f.rows.stream().filter(r->r.source.equals(mixed.primary.get(0).source)).findFirst().get();require(row.use==Use.PRIVATE_SEEDS_AND_PRODUCER&&row.seedLiterals==1,"mixed whole fact/rule Source is accounted in both roles without synthetic clause identity");require(has(f.plan.branches.stream().filter(b->b.privatePool!=null).findFirst().get().privatePool.local,"gate"),"mixed row ground fact is authenticated in the local seed ledger");require(f.evaluate(12,16,WORK,EVENTS).status==Status.FIXED_POINT,"whole mixed source has complete ground-seed and Horn coverage");
        Snapshot onlyX=bind(true,"!anchor(1);"),onlyA=bind(false,BUNDLE),onlyB=bind(false,FACT);Artifact idle=assemble(onlyX,Arrays.asList(onlyA,onlyB));Evaluation closed=idle.evaluate(12,16,WORK,EVENTS);require(closed.status==Status.FIXED_POINT&&closed.qualification.fixedPoint.rounds.size()==1,"seed-only private witness/ordinary branches need no fake producer/native event");require(closed.qualification.fixedPoint.packet.items.size()==1,"seed-only siblings remain entirely private");
        Snapshot signed=bind(false,"!$w seed(w,1)&&~flag(w,1);"),multi=bind(false,"!$u $v pair(u,v);");require(!ContextProductionFamilyAssembly.assemble(onlyX,Arrays.asList(signed),WORK).assembled,"signed private bundle remains explicit unsupported scope");require(!ContextProductionFamilyAssembly.assemble(onlyX,Arrays.asList(multi),WORK).assembled,"multiple origins in one literal remain unsupported");
        Snapshot neg=bind(false,"!~anchor(1);");Evaluation collision=assemble(onlyX,Arrays.asList(onlyA,neg)).evaluate(12,16,WORK,EVENTS);require(collision.status==Status.COLLISION&&collision.barrier==0,"original negative private clause still scanned, never discarded as a positive seed");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("private-witness-assembly-").toString());for(boolean r:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})for(boolean n:new boolean[]{false,true})one(r,g,n);alternatives();boundaries();System.out.println("CONTEXT_PRIVATE_WITNESS_ASSEMBLY_PASS checks="+(checks+ContextPrivateWitnessPoolRunner.checks+ContextTrustedWitnessScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
