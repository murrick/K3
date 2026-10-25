/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalUnitClosure.*;

/** Exhaustive symbolic unit closure must cover pairs absent from native encounter journals. */
public final class ContextUniversalUnitClosureRunner {
    static final int WORK=64000000;static int checks;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Source source(Snapshot x,String text){return ContextPrivateWitnessPoolRunner.source(x,text);}
    static Set<Source> set(Source... s){return new HashSet<>(Arrays.asList(s));}
    static Clause clause(String s)throws Exception{return bind(false,s).primary.get(0).clauses.get(0);}
    static Result close(Snapshot x,Snapshot a){return ContextUniversalUnitClosure.close(x,a,16,16,512,256,WORK);}
    static List<Proof> find(Result r,Clause c){List<Proof> out=new ArrayList<>();for(Proof p:r.proofs)if(p.clause.equivalent(c))out.add(p);return out;}
    static void exact(Result r,Clause c,int depth,Set<Source> sources,String why){require(find(r,c).stream().anyMatch(p->p.support.depth==depth&&p.support.sources.equals(sources)),why);}
    static void incomplete(Result r,String why){require(!r.unitClosed&&r.proofs.isEmpty(),why+": "+r.reason);}
    static void missingPair(boolean local,boolean reverse,boolean ground)throws Exception {
        String unit="!@w gate(w,1);",rule=ground?"!@w seed(w,1)&&gate(7,1)->mid(w,1);":"!@w seed(w,1)&&gate(w,1)->mid(w,1);";
        Snapshot x=bind(true,"!anchor(1);"+(local?unit:"")),a=bind(false,local?rule:(reverse?rule+unit:unit+rule));
        Result r=close(x,a);require(r.unitClosed,"all theoretical unit/body pairs: "+r.reason);
        exact(r,clause("!@w seed(w,1)->mid(w,1);"),1,set(source(local?x:a,unit),source(a,rule)),"nonobserved ground pivot has exact whole-source principal residual");
        require(r.rounds==2&&r.obligations>0,"productive immutable barrier followed by complete unchanged barrier");
        require(r.proofs.stream().noneMatch(p->p.original==null&&p.support.sources.contains(source(x,"!anchor(1);"))),"unrelated X anchor never borrowed for symbolic provenance");
        incomplete(ContextUniversalUnitClosure.close(x,a,1,16,512,256,WORK),"productive first barrier cannot be advertised as closed");
        incomplete(ContextUniversalUnitClosure.close(x,a,16,0,512,256,WORK),"new symbolic inference cannot masquerade as depth0 primary");
    }
    static void chainAndHeadFree()throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);",qr="!@w q(w,1)->r(w,1);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,p+pq+qr);Result r=close(x,a);require(r.unitClosed&&r.rounds==3,"new universal unit feeds following immutable barrier and then stability");
        exact(r,clause("!@w r(w,1);"),2,set(source(a,p),source(a,pq),source(a,qr)),"universal chain uses exact three original Sources/depth2");
        for(Proof proof:r.proofs)if(proof.original==null)require(proof.unit!=null&&proof.horn!=null&&proof.support.depth==Math.max(proof.unit.support.depth,proof.horn.support.depth)+1,"derived theorem retains explicit parent proof graph");
        incomplete(ContextUniversalUnitClosure.close(x,a,16,1,512,256,WORK),"second derivation exceeds depth1");
        String unit="!@u p(u,1);",q="!q(7,1);",rule="!@u @v p(u,1)&&q(v,1)->rel(u,v);";
        Snapshot b=bind(false,unit+q+rule);Result headFree=close(x,b);require(headFree.unitClosed,"proved residual may free a formerly body-bound universal variable: "+headFree.reason);
        exact(headFree,clause("!@u rel(u,7);"),2,set(source(b,unit),source(b,q),source(b,rule)),"head-only variable in derived residual remains universally quantified");
        require(headFree.proofs.stream().anyMatch(t->t.original==null&&t.clause.literals.size()>1&&t.clause.variables.size()==2),"actual proof-backed non-range-restricted intermediate retained");
        incomplete(close(x,bind(false,"!@u @v p(u,1)->rel(u,v);")),"unsupported head-only primary is not waived by accepting proved residuals");
    }
    static void alternativesAndCuts(boolean reverse)throws Exception {
        String p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,p),b=bind(false,pq),group=ContextProductionNativeBinding.trusted("own",reverse?Arrays.asList(b,a):Arrays.asList(a,b),WORK);
        Clause q=clause("!@w q(w,1);");require(find(close(x,a),q).isEmpty()&&find(close(x,b),q).isEmpty(),"independent cuts cannot lend each other a raw universal unit");
        Result combined=close(x,group);require(combined.unitClosed,"explicit shared cut closes");exact(combined,q,1,set(source(a,p),source(b,pq)),"only declared trusted frame combines original members");
        String mixed="!@w p(w,1)&&(p(w,1)->q(w,1));";
        Snapshot c=bind(false,mixed),d=bind(false,mixed),duplicates=ContextProductionNativeBinding.trusted("dup",reverse?Arrays.asList(d,c):Arrays.asList(c,d),WORK);
        Result result=close(x,duplicates);require(result.unitClosed,"duplicate whole-row source alternatives close");
        Set<Set<Source>> actual=new HashSet<>();for(Proof proof:find(result,q)){require(proof.support.depth==1,"duplicate choice depth stays1");actual.add(proof.support.sources);}
        require(actual.equals(new HashSet<>(Arrays.asList(set(source(c,mixed)),set(source(d,mixed)),set(source(c,mixed),source(d,mixed))))),"four parent choices collapse to three exact original Source sets");
        incomplete(ContextUniversalUnitClosure.close(x,duplicates,16,16,512,2,WORK),"alternative limit cannot omit a source group");
        Snapshot reversed=ContextProductionNativeBinding.trusted("dup",reverse?Arrays.asList(c,d):Arrays.asList(d,c),WORK);Result other=close(x,reversed);
        require(other.unitClosed&&result.proofs.size()==other.proofs.size(),"member order preserves number of clauses/alternatives");
        for(Proof proof:result.proofs)require(other.proofs.stream().anyMatch(v->v.clause.equivalent(proof.clause)&&v.support.depth==proof.support.depth&&v.support.sources.equals(proof.support.sources)),"member order preserves every exact theorem/support/depth");
    }
    static void diagonalAndKnownHead()throws Exception {
        String pair="!@w pair(w,w);",seed="!seed(7,1);",rule="!@u @v pair(u,v)&&seed(u,1)->rel(u,v);";
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,pair+seed+rule);Result r=close(x,a);
        require(r.unitClosed,"diagonal/fixed-value closure: "+r.reason);
        exact(r,clause("!rel(7,7);"),2,set(source(a,pair),source(a,seed),source(a,rule)),"repeated variables constrain both principal resolution paths");
        require(find(r,clause("!rel(7,8);")).isEmpty(),"diagonal equality cannot split into independent argument values");
        String direct="!@w q(w,1);",p="!@w p(w,1);",pq="!@w p(w,1)->q(w,1);";
        Snapshot known=bind(true,"!anchor(1);"+direct),producer=bind(false,p+pq);Result covered=close(known,producer);
        exact(covered,clause(direct),0,set(source(known,direct)),"known universal head retains its exact original primary proof");
        exact(covered,clause(direct),1,set(source(producer,p),source(producer,pq)),"already-known head still needs the distinct derived Source group");
        incomplete(ContextUniversalUnitClosure.close(known,producer,16,0,512,256,WORK),"same-valued primary head cannot certify a missing source alternative");
    }
    static void boundariesAndFreshValues()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@w p(w,1); !@w p(w,1)->q(w,1);");
        incomplete(ContextUniversalUnitClosure.close(x,a,16,16,3,256,WORK),"whole clause limit returns no prefix");
        incomplete(ContextUniversalUnitClosure.close(x,a,16,16,512,256,1),"shared work limit returns no prefix");
        incomplete(close(x,x),"X cannot be used as a foreign cut");
        incomplete(close(x,bind(false,"!@w p(w,1)&&(p(w,1)->~q(w,1));")),"positive conjunct cannot waive unsupported signed clause in same whole Source");
        Snapshot witness=bind(true,"!$w p(w,1)&&flag(w,1);");Result separate=close(witness,null);
        require(separate.unitClosed&&separate.proofs.isEmpty()&&separate.separateWitnessSources.equals(set(witness.primary.get(0).source)),"witness bundle is explicitly separate, never a universal schema proof");
        Result r=close(x,a);Proof q=find(r,clause("!@w q(w,1);")).get(0);User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);
        m.compileLine(q.clause.assertion(),false,q.clause.project(m));
        require(Boolean.TRUE.equals(m.query("?q(John,1);",null,false))&&Boolean.TRUE.equals(m.query("?q(73,1);",null,false)),"symbolic result covers unmentioned admissible values without enumerating constants");
        require(m.query("?q(John,2);",null,false)==null,"symbolic closure preserves fixed arguments");
    }
    public static void main(String[] args){try {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-unit-closure-").toString());
        for(boolean l:new boolean[]{false,true})for(boolean r:new boolean[]{false,true})for(boolean g:new boolean[]{false,true})missingPair(l,r,g);
        chainAndHeadFree();diagonalAndKnownHead();alternativesAndCuts(false);alternativesAndCuts(true);boundariesAndFreshValues();
        System.out.println("CONTEXT_UNIVERSAL_UNIT_CLOSURE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
