/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalConstraintClosure.*;
import org.kanger.ContextUniversalConstraintClosure.Result;

/** Whole native Horn producers and constraints share one proof ledger without witness filtering. */
public final class ContextUniversalConstraintClosureRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static ContextUniversalNativeRows.Result rows(Snapshot x,Snapshot a){ContextUniversalNativeRows.Result r=ContextUniversalNativeRows.inspect(x,a,64,WORK);require(r.classified,r.reason);return r;}
    static Result close(ContextUniversalNativeRows.Result r,int depth){return ContextUniversalConstraintClosure.close(r,16,depth,512,256,WORK);}
    static Result good(ContextUniversalNativeRows.Result rows,int depth){Result r=close(rows,depth);require(r.closed,r.reason);require(r.input==rows,"exact complete row ledger retained");ContextUniversalConstraintClosure.check(r,depth,()->{});audit(r);return r;}
    static void audit(Result r){Set<Proof> seen=Collections.newSetFromMap(new IdentityHashMap<Proof,Boolean>());for(Proof p:r.proofs)auditProof(p,r.input,seen);for(ContextUniversalNativeRows.Row row:r.input.rows)for(ContextUniversalNativeRows.Part part:row.parts)require(r.proofs.stream().anyMatch(p->p.original==row&&p.part==part),"every original part is a root, including mixed Source constraints");require(r.emptyProofs.stream().allMatch(p->p.clause==null),"empty means checked contradiction proof, never a native empty root");}
    static void auditProof(Proof p,ContextUniversalNativeRows.Result input,Set<Proof> seen){if(!seen.add(p))return;if(p.original!=null){require(input.rows.contains(p.original)&&p.original.parts.contains(p.part)&&p.part.clause==p.clause,"actual immutable original Row/Part/Clause root");require(p.support.depth==0&&p.support.sources.equals(Collections.singleton(p.original.original.source)),"whole original Source0, no per-clause attribution");}else{auditProof(p.unit,input,seen);auditProof(p.horn,input,seen);Clause expected=p.unit.clause.resolveWithUnits(p.horn.clause,0,p.pivot,()->{});require(p.clause==null?expected==null:expected!=null&&p.clause.equivalent(expected),"actual principal resolvent including empty");Set<Source> sources=new HashSet<>(p.unit.support.sources);sources.addAll(p.horn.support.sources);require(p.support.sources.equals(sources)&&p.support.depth==Math.max(p.unit.support.depth,p.horn.support.depth)+1,"exact Source union and maximum-parent-plus1 grade");}}
    static void no(Result r,String why){require(!r.closed&&!r.xRefuted&&!r.foreignRefuted&&r.proofs.isEmpty()&&r.emptyProofs.isEmpty(),why+": "+r.reason);}
    static void contradictions()throws Exception {
        Snapshot x=bind(true,"!p(1);"),a=bind(false,"!@u p(u)->q(u); !~q(1);");ContextUniversalNativeRows.Result inventory=rows(x,a);Result r=good(inventory,2);require(r.xRefuted&&!r.foreignRefuted&&r.emptyProofs.size()==1,"derived q plus native constraint proves X-dependent refutation");
        Set<Source> all=new HashSet<>(inventory.sources);require(r.emptyProofs.get(0).support.sources.equals(all)&&r.emptyProofs.get(0).support.depth==2,"all three actual Sources and cumulative empty2");
        no(close(inventory,1),"depth shortage does not return earlier producer proofs as successful closure");no(ContextUniversalConstraintClosure.close(inventory,1,16,512,256,WORK),"round cap after discoveries has no successful proof prefix");
        Snapshot universalX=bind(true,"!@u p(u);"),universalA=bind(false,"!@v ~p(v);");Result universal=good(rows(universalX,universalA),1);require(universal.xRefuted&&universal.emptyProofs.get(0).support.depth==1,"universal refutation without any previously mentioned ordinary value");
        Snapshot diagonalX=bind(true,"!@y p(7,y);"),diagonalA=bind(false,"!@z ~p(z,z);");require(good(rows(diagonalX,diagonalA),1).xRefuted,"principal unifier enforces repeated variables across independent parent scopes");
        Snapshot apartX=bind(true,"!@u p(u,1);"),apartA=bind(false,"!@u ~p(2,u);");require(good(rows(apartX,apartA),1).xRefuted,"same numeric variable ID in independent parents must be standardized apart");
        Snapshot repeatedX=bind(true,"!@u p(u,u);"),repeatedA=bind(false,"!~p(1,2);");require(!good(rows(repeatedX,repeatedA),1).xRefuted,"one repeated universal variable cannot acquire two distinct values");
        Snapshot mismatch=bind(false,"!~p(8,8);");require(!good(rows(diagonalX,mismatch),1).xRefuted,"ordinary value mismatch is not a contradiction");
        Snapshot mixed=bind(false,"!@u (p(u)->q(u))&&~q(1);");Result m=good(rows(x,mixed),2);require(m.emptyProofs.get(0).support.sources.size()==2,"producer and constraint in same original row share one physical Source");
        Snapshot two=bind(true,"!p(7); !q(8);"),constraint=bind(false,"!@v ~(p(v)&&q(v));");require(!good(rows(two,constraint),16).xRefuted,"one shared variable cannot be bound to inconsistent donors");
        Snapshot independent=bind(false,"!@u @v ~(p(u)&&q(v));");require(good(rows(two,independent),16).xRefuted,"distinct constraint variables allow distinct values");
        Snapshot open=bind(false,"!@v ~(p(v)&&missing(v));");Result pending=good(rows(x,open),16);require(!pending.xRefuted&&pending.proofs.stream().anyMatch(p->p.clause!=null&&p.clause.literals.stream().allMatch(l->!l.positive)),"unproved constraint premise remains residual, never consistency or invented fact");
        try{r.emptyProofs.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}try{r.proofs.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}
    }
    static void sourcesAndMinima()throws Exception {
        Snapshot anchor=bind(true,"!anchor(1);"),a=bind(false,"!p(1);"),b=bind(false,"!~p(1);");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK);Result privateOnly=good(rows(anchor,group),1);require(!privateOnly.xRefuted&&privateOnly.foreignRefuted,"private-only contradiction cannot borrow unrelated X anchor");for(Proof p:privateOnly.emptyProofs)require(p.support.sources.stream().noneMatch(s->s.localX),"original foreign-only evidence retained without relabeling");
        Snapshot reverse=ContextProductionNativeBinding.trusted("own",Arrays.asList(b,a),WORK);Result reversed=good(rows(anchor,reverse),1);require(reversed.emptyProofs.get(0).support.equals(privateOnly.emptyProofs.get(0).support),"trusted member order preserves physical contradiction Sources/minimum");
        Snapshot px=bind(true,"!p(1);"),negativeP=bind(false,"!~p(1);"),q=bind(false,"!q(2);"),negativeQ=bind(false,"!~q(2);");Snapshot bothKinds=ContextProductionNativeBinding.trusted("own",Arrays.asList(negativeP,q,negativeQ),WORK);Result bothRefutations=good(rows(px,bothKinds),1);require(bothRefutations.xRefuted&&bothRefutations.foreignRefuted&&bothRefutations.emptyProofs.size()==2,"X-dependent and private-only refutations retain distinct Sources; empty proof does not explode into unrelated combinations");
        Snapshot x=bind(true,"!p(1);"),pa=bind(false,"!@u p(u)->q(u); !~q(1);"),loop=bind(false,"!@u q(u)->q(u);");Snapshot target=ContextProductionNativeBinding.trusted("own",Arrays.asList(pa,loop),WORK);ContextUniversalNativeRows.Result inventory=rows(x,target);Result alternatives=good(inventory,3);
        require(alternatives.emptyProofs.stream().anyMatch(p->p.support.sources.size()==3&&p.support.depth==2)&&alternatives.emptyProofs.stream().anyMatch(p->p.support.sources.size()==4&&p.support.depth==3),"deeper Source supersets survive beside smaller refutation");no(close(inventory,2),"deeper exact Source obligation cannot disappear behind shorter different Source proof");
        no(ContextUniversalConstraintClosure.close(inventory,16,16,512,1,WORK),"alternative cap returns no earlier smaller refutation prefix");
        Snapshot both=bind(true,"!p(1)&&q(1);"),one=bind(false,"!@u (p(u)->q(u))&&~q(1);");Result minimum=good(rows(both,one),1);require(minimum.emptyProofs.stream().allMatch(p->p.support.depth==1),"shorter exact Source refutation covers deeper recipe after full minima combine");
    }
    static void wholeInputAndBounds()throws Exception {
        Snapshot x=bind(true,"!p(1);"),a=bind(false,"!~p(1);"),w=bind(false,"!$u witness(u);");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,w),WORK);no(close(rows(x,group),16),"later witness row cannot be silently omitted after an earlier direct contradiction");
        Snapshot signed=bind(false,"!$u witness(u)&&~flag(u);");no(close(rows(x,signed),16),"signed existential remains explicit unsupported scope");
        Snapshot disjunction=bind(false,"!a(1)||b(1);");no(close(rows(x,disjunction),16),"unsupported original producer cannot be dropped");
        ContextUniversalNativeRows.Result inventory=rows(x,a);no(ContextUniversalConstraintClosure.close(inventory,16,16,1,256,WORK),"clause cap no prefix");no(ContextUniversalConstraintClosure.close(inventory,16,16,512,256,1),"work cap no prefix");no(close(null,16),"sealed whole ledger required");
        Snapshot positive=bind(false,"!@u p(u)->q(u);");Result noConstraint=good(rows(x,positive),16);require(!noConstraint.xRefuted&&noConstraint.emptyProofs.isEmpty(),"positive-only inputs still close without inventing a refutation");
        try{ContextUniversalConstraintClosure.close(inventory,0,16,512,256,WORK);throw new AssertionError();}catch(IllegalArgumentException expected){++checks;}
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("constraint-closure-").toString());contradictions();sourcesAndMinima();wholeInputAndBounds();System.out.println("CONTEXT_UNIVERSAL_CONSTRAINT_CLOSURE_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
