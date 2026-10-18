/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Bounded observed joins retain the complete sealed proof ledger. */
public final class ContextWitnessMultiPremiseRunner {
    static int checks;static final int WORK=16000000;
    static Source source(int context,int id) {return new Source(new UUID(0,context),1,id,context==0,"base");}
    static Source x(int id) {return source(0,id);}static Source a(int id) {return source(1,id);}static Source b(int id) {return source(2,id);}
    static void require(boolean yes,String why) {++checks;if(!yes)throw new AssertionError(why);}
    static Item item(Packet p,String name) {return p.items.stream().filter(i->i.atom.predicate.equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing "+name));}
    static boolean proof(Item i,int depth,Source... sources) {Set<Source> wanted=new LinkedHashSet<>(Arrays.asList(sources));return i.proofs.stream().anyMatch(p->p.depth==depth&&p.sources.equals(wanted));}
    static void incomplete(Result r,String why) {require(!r.exported&&r.packet==null,why+": "+r.reason);}
    static Result next(ContextWitnessSeedReplay r) {return ContextWitnessResultTransport.exportNext(r,16,WORK);}
    static boolean fullProof(Item item,int n,int depth,int alternative,boolean later) {
        List<Source> expected=new ArrayList<>(Arrays.asList(x(0),a(alternative),b(2)));
        for(int i=1;i<n;i++)expected.add(a(10+i));if(later)expected.add(b(3));
        return proof(item,depth,expected.toArray(new Source[0]));
    }
    static Packet initial(boolean independent) throws Exception {
        String text="!$w p0(w,1);";
        List<Bundle> bundles=new ArrayList<>();bundles.add(ContextWitnessBundle.capture(new Declaration(x(0),text),10000));
        if(independent)bundles.add(ContextWitnessBundle.capture(new Declaration(x(20),"!$v other(v,1);"),10000));
        try(Session s=ContextWitnessBundle.restoreObserved(bundles,10000,100000)) {
            s.capture.replay(s.mind,a(1),"!@w p0(w,1)->q(w,1);");s.capture.replay(s.mind,a(4),"!@w p0(w,1)->q(w,1);");
            for(int i=1;i<=6;i++)s.capture.replay(s.mind,a(10+i),"!@w p0(w,1)->p"+i+"(w,1);");
            s.mind.setQueryPass(QueryPass.ACCEPT);s.mind.link(null,false);Result r=ContextWitnessResultTransport.export(s,16,WORK);require(r.exported,"initial packet: "+r.reason);return r.packet;
        }
    }
    static String rule(int premises,boolean ground,boolean negative,boolean reverse,boolean wrongValue) {
        List<String> terms=new ArrayList<>();terms.add("q(w,1)");for(int i=1;i<premises;i++)terms.add("p"+i+"(w,"+(wrongValue&&i==premises-1?2:1)+")");if(reverse)Collections.reverse(terms);
        return "!@w "+String.join("&&",terms)+"->"+(negative?"~":"")+"out"+(ground?"":"(w,1)")+";";
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-multi-").toString());Packet first=initial(true);
            for(int n:new int[]{3,7})for(boolean ground:new boolean[]{false,true})for(boolean negative:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
                Packet second;
                try(ContextWitnessSeedReplay r=ContextWitnessSeedReplay.open(first,10000,100000)) {
                    r.rule(b(2),rule(n,ground,negative,reverse,false));incomplete(ContextWitnessResultTransport.exportNext(r,1,WORK),"join cannot reset imported depth");
                    Result result=next(r);require(result.exported,"bounded "+n+"-premise join: "+result.reason);second=result.packet;Item out=item(second,"out");
                    require(out.atom.positive!=negative&&out.atom.arguments.size()==(ground?0:2),"signed head shape retained");
                    require(fullProof(out,n,2,1,false)&&fullProof(out,n,2,4,false)&&out.proofs.size()==2,"whole distinct alternatives at cumulative depth2");
                    require(!proof(out,1,x(0),b(2))&&!proof(out,2,x(0),a(1),a(4),b(2)),"no depth reset or merged alternatives");
                    if(!ground)require(out.atom.arguments.get(0).origin.equals(item(first,"q").atom.arguments.get(0).origin),"same canonical witness origin");
                    incomplete(ContextWitnessResultTransport.exportNext(r,16,1),"work stop exposes no verified prefix");
                }
                try(ContextWitnessSeedReplay r=ContextWitnessSeedReplay.open(second,10000,100000)) {
                    r.rule(b(3),"!"+(ground?"": "@w ")+(negative?"~":"")+"out"+(ground?"":"(w,1)")+"->done;");Result result=next(r);
                    require(result.exported&&fullProof(item(result.packet,"done"),n,3,1,true)&&fullProof(item(result.packet,"done"),n,3,4,true),"subsequent erasure retains all join dependencies at depth3: "+result.reason);
                }
            }
            for(int n:new int[]{3,7})try(ContextWitnessSeedReplay r=ContextWitnessSeedReplay.open(first,10000,100000)) {
                r.rule(b(2),rule(n,false,false,false,true));incomplete(next(r),"missing exact ordinary premise value cannot satisfy join");
            }
            try(ContextWitnessSeedReplay r=ContextWitnessSeedReplay.open(first,10000,100000)) {
                r.rule(b(2),"!@w q(w,1)&&p1(w,1)&&other(w,1)->out;");incomplete(next(r),"independent witnesses cannot share a binding even when erased");
            }
            try(ContextWitnessSeedReplay r=ContextWitnessSeedReplay.open(first,10000,100000)) {
                r.rule(b(2),"!@w q(w,1)&&p0(w,1)&&p1(w,1)&&p2(w,1)&&p3(w,1)&&p4(w,1)&&p5(w,1)&&p6(w,1)->out(w,1);");incomplete(next(r),"eight premises exceed observed branch bound; no saturation prefix");
            }
            try(Session s=ContextWitnessBundle.restoreObserved(Arrays.asList(ContextWitnessBundle.capture(new Declaration(x(0),"!$w p0(w,1)&&p1(w,1)&&p2(w,1);"),10000)),10000,100000)) {
                s.capture.replay(s.mind,a(1),"!@w p0(w,1)&&p1(w,1)&&p2(w,1)->out(w,1);");s.mind.setQueryPass(QueryPass.ACCEPT);s.mind.link(null,false);
                incomplete(ContextWitnessResultTransport.export(s,16,WORK),"initial Session entrypoint retains its original narrower producer scope");
            }
            System.out.println("CONTEXT_WITNESS_MULTI_PREMISE_PASS checks="+checks);System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
}
