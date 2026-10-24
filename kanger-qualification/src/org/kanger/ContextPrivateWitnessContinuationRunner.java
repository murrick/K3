/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.*;

/** Private progress must survive a barrier even when common X has not changed. */
public final class ContextPrivateWitnessContinuationRunner {
    static int checks;static final int WORK=64000000,EVENTS=500000;
    static final String FIRST="!@w seed(w,1)->hidden(w,1);",SECOND="!@w hidden(w,1)->ready(w,1);",OUT="!@w ready(w,1)->out(w,1);",NEG="!@w out(w,1)->~flag(w,1);";
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    interface Action{void run()throws Exception;}
    static void reject(Action action,String why)throws Exception{boolean refused=false;try{action.run();}catch(IllegalStateException|IllegalArgumentException e){refused=true;}require(refused,why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot s,String text){return ContextPrivateWitnessPoolRunner.source(s,text);}
    static Set<Source> set(Source...s){return new LinkedHashSet<>(Arrays.asList(s));}
    static Item item(Packet p,String name){return ContextPrivateWitnessPoolRunner.item(p,name);}
    static boolean has(Packet p,String name){return p.items.stream().anyMatch(i->i.atom.predicate.equals(name));}
    static void exact(Item i,Set<Source> expected,int depth){require(i.proofs.stream().anyMatch(p->p.sources.equals(expected)&&p.depth==depth),"exact cumulative whole-source support/depth");}
    static void one(boolean reverse,boolean split)throws Exception{
        Snapshot x=bind(true,"!anchor(1); "+OUT+" "+NEG),a=bind(false,split?"!$w seed(w,1); !$w flag(w,1);":"!$w seed(w,1)&&flag(w,1);"),b=bind(false,FIRST+" "+SECOND);
        Snapshot group=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
        Packet common=ContextPrivateWitnessPoolRunner.common(x);PrivatePool initial=ContextWitnessResultTransport.privatePool(common,x,group,WORK);
        Source seed=a.primary.get(0).source,first=source(b,FIRST),second=source(b,SECOND),out=source(x,OUT);Set<Source> all=set(first,second,out);
        reject(()->ContextWitnessResultTransport.verifyPrivateClosed(initial,all,16,WORK,EVENTS),"initial local proof pool is not closed");
        PrivateAdvance p1=ContextWitnessResultTransport.advancePrivate(initial,set(first),16,WORK,EVENTS);
        require(p1.before==initial&&p1.localChanged&&!p1.commonChanged&&p1.obligations==1,"private progress despite unchanged common pool");
        require(ContextWitnessResultTransport.sameContents(common,p1.output,WORK),"foreign-only result never enters common X");
        exact(item(p1.after.local,"hidden"),set(seed,first),1);
        require(!has(initial.local,"hidden")&&!has(common,"hidden"),"immutable initial branch and common pool");
        require(ContextWitnessResultTransport.verifyPrivateClosed(p1.after,set(first),16,WORK,EVENTS)==1,"selected first producer closed locally");
        reject(()->ContextWitnessResultTransport.verifyPrivateClosed(p1.after,all,16,WORK,EVENTS),"unchanged common must not certify later private obligations");
        PrivatePool rebased=ContextWitnessResultTransport.rebasePrivate(p1.after,p1.output,WORK);
        require(has(rebased.local,"hidden")&&rebased.common==p1.output,"private intermediate retained across fresh common barrier");
        require(item(rebased.local,"hidden").atom.arguments.get(0).origin.equals(item(rebased.local,"seed").atom.arguments.get(0).origin),"original private witness reused on rebase");
        PrivateAdvance p2=ContextWitnessResultTransport.advancePrivate(rebased,set(second),16,WORK,EVENTS);
        require(p2.localChanged&&!p2.commonChanged&&p2.obligations==1,"second private progress remains local");exact(item(p2.after.local,"ready"),set(seed,first,second),2);
        PrivatePool third=ContextWitnessResultTransport.rebasePrivate(p2.after,p2.output,WORK);
        reject(()->ContextWitnessResultTransport.advancePrivate(third,set(out),2,WORK,EVENTS),"cumulative private depth cannot reset at common barrier");
        PrivateAdvance p3=ContextWitnessResultTransport.advancePrivate(third,set(out),16,WORK,EVENTS);
        require(p3.localChanged&&p3.commonChanged&&p3.obligations==1,"X producer finally creates shareable product");exact(item(p3.output,"out"),set(seed,first,second,out),3);
        for(String raw:Arrays.asList("seed","flag","hidden","ready"))require(!has(p3.output,raw),"raw and intermediate private facts stay out of X: "+raw);
        require(p3.output.basis==null&&p3.output.coveredCut==null&&p3.output.roundCut==null,"local continuation is no family coverage certificate");
        PrivatePool last=ContextWitnessResultTransport.rebasePrivate(p3.after,p3.output,WORK);
        require(ContextWitnessResultTransport.verifyPrivateClosed(last,all,16,WORK,EVENTS)==3,"final full selected local closure includes private obligations");
        PrivateAdvance stable=ContextWitnessResultTransport.advancePrivate(last,all,16,WORK,EVENTS);
        require(!stable.localChanged&&!stable.commonChanged&&stable.obligations==3,"fresh replay preserves exact saturated proof pool");
        ContextWitnessClauseCollision.Result collision=ContextWitnessClauseCollision.inspectPrivate(stable.after,16,WORK,EVENTS);
        require(collision.scanned&&collision.evidence.isEmpty()==split,"private flag identity controls opposition after continuation");
        if(!split)for(ContextWitnessClauseCollision.Evidence e:collision.evidence)for(Support p:e.proofs)require(p.sources.equals(set(seed,first,second,out,source(x,NEG)))&&p.depth==4,"local collision retains whole five-source proof and cumulative depth4");
        reject(()->ContextWitnessResultTransport.advancePrivate(last,set(source(x,NEG)),16,WORK,EVENTS),"negative constraint cannot masquerade as Horn producer");
        reject(()->ContextWitnessResultTransport.advancePrivate(last,set(source(x,"!anchor(1);")),16,WORK,EVENTS),"unit fact cannot become a productive Horn declaration");
        reject(()->ContextWitnessResultTransport.advancePrivate(last,set(seed),16,WORK,EVENTS),"bundle seed cannot masquerade as ordinary producer");
        Snapshot unrelated=bind(false,FIRST);reject(()->ContextWitnessResultTransport.advancePrivate(last,set(unrelated.primary.get(0).source),16,WORK,EVENTS),"different context's same-text producer not accepted");
        reject(()->ContextWitnessResultTransport.rebasePrivate(last,common,WORK),"common rollback cannot discard published proof");
        Snapshot otherX=bind(true,"!anchor(1);");Packet other=ContextPrivateWitnessPoolRunner.common(otherX);reject(()->ContextWitnessResultTransport.rebasePrivate(last,other,WORK),"another X cut cannot own the continuation");
        reject(()->ContextWitnessResultTransport.advancePrivate(last,all,16,1,EVENTS),"work exhaustion cannot create a local receipt");
        reject(()->ContextWitnessResultTransport.advancePrivate(last,Collections.emptySet(),16,WORK,EVENTS),"empty producer selection cannot claim closure");
        Snapshot sibling=bind(false,"!$w seed(w,1)&&flag(w,1); "+FIRST+" "+SECOND);
        PrivatePool separate=ContextWitnessResultTransport.privatePool(common,x,sibling,WORK);
        PrivatePool earlySibling=ContextWitnessResultTransport.rebasePrivate(separate,p1.output,WORK);
        require(!has(earlySibling.local,"hidden")&&!has(earlySibling.local,"ready"),"private intermediates never cross to independent sibling cut");
        PrivatePool laterSibling=ContextWitnessResultTransport.rebasePrivate(separate,p3.output,WORK);
        require(has(laterSibling.local,"out")&&!item(laterSibling.local,"out").atom.arguments.get(0).origin.equals(item(laterSibling.local,"flag").atom.arguments.get(0).origin),"common product retains other cut origin beside sibling private witness");
        ContextWitnessClauseCollision.Result separateScan=ContextWitnessClauseCollision.inspectPrivate(laterSibling,16,WORK,EVENTS);
        require(separateScan.scanned&&separateScan.evidence.isEmpty(),"same spelling in independent private cut cannot refute carried common witness");
        require(!ContextProductionFamilyAssembly.assemble(x,Arrays.asList(group),WORK).assembled,"local continuation alone does not activate incomplete family admission");
    }
    static void alternatives()throws Exception{
        Snapshot x=bind(true,"!anchor(1); "+OUT),a=bind(false,"!$w seed(w,1);"),b=bind(false,FIRST+" "+SECOND),c=bind(false,FIRST);
        Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b,c),WORK);PrivatePool initial=ContextWitnessResultTransport.privatePool(ContextPrivateWitnessPoolRunner.common(x),x,group,WORK);
        Source seed=a.primary.get(0).source,b1=source(b,FIRST),c1=source(c,FIRST),b2=source(b,SECOND),out=source(x,OUT);
        PrivateAdvance original=ContextWitnessResultTransport.advancePrivate(initial,set(b1),16,WORK,EVENTS);
        PrivateAdvance first=ContextWitnessResultTransport.advancePrivate(ContextWitnessResultTransport.rebasePrivate(original.after,original.output,WORK),set(c1),16,WORK,EVENTS);
        require(first.after.local.items.size()==original.after.local.items.size(),"source-alternative-only progress adds no atom");
        require(first.localChanged&&!first.commonChanged&&item(first.after.local,"hidden").proofs.size()==2,"identical private producers retain both original source alternatives");
        PrivatePool p=ContextWitnessResultTransport.rebasePrivate(first.after,first.output,WORK);
        PrivateAdvance second=ContextWitnessResultTransport.advancePrivate(p,set(b2),16,WORK,EVENTS);
        exact(item(second.after.local,"ready"),set(seed,b1,b2),2);exact(item(second.after.local,"ready"),set(seed,c1,b2),2);
        PrivateAdvance third=ContextWitnessResultTransport.advancePrivate(ContextWitnessResultTransport.rebasePrivate(second.after,second.output,WORK),set(out),16,WORK,EVENTS);
        exact(item(third.output,"out"),set(seed,b1,b2,out),3);exact(item(third.output,"out"),set(seed,c1,b2,out),3);
        require(item(third.output,"out").proofs.size()==2&&!has(third.output,"hidden")&&!has(third.output,"ready"),"all alternatives cross X boundary while private intermediates do not");
        require(ContextWitnessResultTransport.verifyPrivateClosed(third.after,set(b1,c1,b2,out),16,WORK,EVENTS)==6,"closure accounts for all private and X-dependent alternatives");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("private-witness-continuation-").toString());for(boolean reverse:new boolean[]{false,true})for(boolean split:new boolean[]{false,true})one(reverse,split);alternatives();System.out.println("CONTEXT_PRIVATE_WITNESS_CONTINUATION_PASS checks="+(checks+ContextPrivateWitnessPoolRunner.checks+ContextTrustedWitnessScopeRunner.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
