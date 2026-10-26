/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextUniversalNativeRows.*;
import org.kanger.ContextUniversalNativeRows.Result;

/** Exact original row inventory does not silently turn mixed Sources into producer-only knowledge. */
public final class ContextUniversalNativeRowsRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean b,String why){++checks;if(!b)throw new AssertionError(why);}
    static Snapshot bind(boolean x,String s)throws Exception{return ContextPrivateWitnessPoolRunner.bind(x,s);}
    static Result inspect(Snapshot x,Snapshot a){Result r=ContextUniversalNativeRows.inspect(x,a,64,WORK);require(r.classified,r.reason);audit(r);return r;}
    static void audit(Result r) {
        List<Entry> entries=new ArrayList<>(r.x.primary);if(r.foreign!=null)entries.addAll(r.foreign.primary);require(r.rows.size()==entries.size(),"all actual original rows exactly once");Set<Source> sources=new HashSet<>();
        for(int i=0;i<entries.size();++i) {
            Row row=r.rows.get(i);Entry entry=entries.get(i);require(row.original==entry,"original native Entry object, never reconstructed/filtered row");sources.add(entry.source);
            if(entry.bundle!=null)require(row.parts.size()==1&&row.parts.get(0).clauseIndex==-1&&row.parts.get(0).clause==null,"existential remains whole separate row");
            else {require(row.parts.size()==entry.clauses.size(),"all original clauses retained even in mixed/unsupported Source");for(int j=0;j<row.parts.size();++j){Part p=row.parts.get(j);require(p.clauseIndex==j&&p.clause==entry.clauses.get(j),"exact original clause/index, no minted Source or clause copy");}}
            require(row.withinShape==row.parts.stream().noneMatch(p->p.role==Role.UNSUPPORTED),"whole row scope includes unsupported part");
        }
        require(r.sources.equals(sources),"whole complete Source inventory");require(r.parent.equals(org.kanger.ContextWitnessResultTransport.Cut.of(r.x)),"actual parent cut");
        require(r.target.equals(org.kanger.ContextWitnessResultTransport.Cut.of(r.foreign==null?r.x:r.foreign)),"actual target cut");
    }
    static void no(Result r,String why){require(!r.classified&&!r.withinShape&&r.rows.isEmpty()&&r.sources.isEmpty()&&r.parent==null&&r.target==null,why+": "+r.reason);}
    static void roles()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@u good(u,1); !~bad(1);");Result r=inspect(x,a);require(r.withinShape,"positive producer and negative constraint are both classified language");require(r.rows.get(1).parts.get(0).role==Role.PRODUCER&&r.rows.get(2).parts.get(0).role==Role.CONSTRAINT,"negative original does not erase earlier producers");
        org.kanger.ContextUniversalFrozenBranch.Result branch=org.kanger.ContextUniversalFrozenBranch.runWithPremises(x,a,ContextPrivateWitnessPoolRunner.common(x),null,ContextUniversalFrozenBranchRunner.full());require(!branch.produced&&branch.phase.equals("schemas")&&branch.products==null,"classification does not activate constraints or bypass existing conservative refusal");
        Snapshot mixed=bind(false,"!@u good(u,1)&&~bad(u,1);");Result m=inspect(x,mixed);Row row=m.rows.get(1);require(row.parts.size()==2&&row.parts.stream().anyMatch(p->p.role==Role.PRODUCER)&&row.parts.stream().anyMatch(p->p.role==Role.CONSTRAINT),"mixed original whole Source retains both roles");require(m.sources.size()==2&&row.original.source.equals(mixed.primary.get(0).source),"mixed clauses share one actual original Source");
        Snapshot constraint=bind(false,"!@u @v ~(left(u,1)&&right(v,1));");Result c=inspect(x,constraint);require(c.withinShape&&c.rows.get(1).parts.get(0).role==Role.CONSTRAINT,"two-variable headless negative body classified without a positive head");
        Snapshot only=bind(true,"!~bad(1);");require(inspect(only,null).withinShape,"negative X inventory need not invent positive seed or infer consistency");
        Snapshot conflictX=bind(true,"!p(1);"),conflictA=bind(false,"!~p(1);");Result conflict=inspect(conflictX,conflictA);require(conflict.withinShape&&conflict.rows.get(1).parts.get(0).role==Role.CONSTRAINT,"even contradictory supplied facts may be classified, never qualified by classification");
        try{m.rows.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}try{row.parts.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}try{m.sources.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){++checks;}
    }
    static void unsupportedAndWitness()throws Exception {
        Snapshot x=bind(true,"!anchor(1);");
        for(String text:Arrays.asList("!a(1)||b(1);","!@u @v a(u,1)->b(v,1);","!@u @v @w triple(u,v,w);")) {
            Snapshot a=bind(false,text);Result r=inspect(x,a);require(!r.withinShape&&!r.rows.get(1).withinShape&&r.rows.get(1).parts.stream().anyMatch(p->p.role==Role.UNSUPPORTED),"unsupported full row preserved: "+text);
        }
        Snapshot mixed=bind(false,"!good(1)&&(a(1)||b(1));");Result m=inspect(x,mixed);Row row=m.rows.get(1);require(!m.withinShape&&row.parts.stream().anyMatch(p->p.role==Role.PRODUCER)&&row.parts.stream().anyMatch(p->p.role==Role.UNSUPPORTED),"later unsupported clause cannot leave a whole Source producer-only plan");
        Snapshot w=bind(false,"!$u seed(u,1)&&flag(u,1);");Result positive=inspect(x,w);require(positive.withinShape&&positive.rows.get(1).parts.get(0).role==Role.WITNESS,"positive existential row retains its native bundle identity");
        Snapshot signed=bind(false,"!$u seed(u,1)&&~flag(u,1);");Result negative=inspect(x,signed);require(!negative.withinShape&&negative.rows.get(1).parts.size()==1&&negative.rows.get(1).parts.get(0).role==Role.UNSUPPORTED,"signed existential cannot be split into producer and universal constraint");
        Snapshot pair=bind(false,"!$u $v pair(u,v);");Result two=inspect(x,pair);require(two.withinShape&&two.rows.get(1).parts.get(0).role==Role.WITNESS,"two original binders are a witness inventory, not an execution readiness claim");
    }
    static void trustedAndBounds()throws Exception {
        Snapshot x=bind(true,"!anchor(1);"),a=bind(false,"!@u good(u,1);"),b=bind(false,"!~bad(1);");Snapshot group=ContextProductionNativeBinding.trusted("own",Arrays.asList(a,b),WORK),reverse=ContextProductionNativeBinding.trusted("own",Arrays.asList(b,a),WORK);Result r=inspect(x,group),s=inspect(x,reverse);require(r.sources.equals(s.sources)&&r.withinShape&&s.withinShape,"member order preserves all actual Sources/roles without relabeling to routing member");
        for(Row row:r.rows)if(!row.original.source.localX)require(a.primary.contains(row.original)||b.primary.contains(row.original),"trusted original physical member Entry preserved");
        no(ContextUniversalNativeRows.inspect(x,group,2,WORK),"whole row cap returns no partial inventory");no(ContextUniversalNativeRows.inspect(x,group,64,1),"work cap no partial inventory");no(ContextUniversalNativeRows.inspect(x,x,64,WORK),"X cannot be relabelled as foreign");no(ContextUniversalNativeRows.inspect(null,b,64,WORK),"actual X required");no(ContextUniversalNativeRows.inspect(b,null,64,WORK),"foreign cannot become X by position");
        try{ContextUniversalNativeRows.inspect(x,b,0,WORK);throw new AssertionError();}catch(IllegalArgumentException expected){++checks;}
        require(x.primary.size()==1&&group.primary.size()==2,"native inputs unchanged after all inventories/failures");
    }
    public static void main(String[] args){try{System.setProperty("user.home",java.nio.file.Files.createTempDirectory("native-rows-").toString());roles();unsupportedAndWitness();trustedAndBounds();System.out.println("CONTEXT_UNIVERSAL_NATIVE_ROWS_PASS checks="+checks);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
