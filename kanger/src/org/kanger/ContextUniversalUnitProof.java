/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextOrdinaryResidualRounds.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Detached source-authenticated flat resolution with at least one unit parent. */
public final class ContextUniversalUnitProof {
    public enum Scope { ORDINARY_FLAT_UNIT_RESOLUTION }
    public static final class Proof {
        public final Clause left,right,conclusion;
        public final boolean contradiction,dependsOnX;
        public final Support leftSupport,rightSupport,support;
        public final int leftPivot,rightPivot;
        private Proof(Clause left,int leftPivot,Clause right,int rightPivot,Clause conclusion,Support a,Support b,Support support) {
            this.left=left; this.right=right; this.leftPivot=leftPivot; this.rightPivot=rightPivot;
            this.conclusion=conclusion; contradiction=conclusion==null;
            leftSupport=a; rightSupport=b; this.support=support;
            dependsOnX=support.sources.stream().anyMatch(s -> s.localX);
        }
    }
    public static final class Evidence {
        public final Scope scope=Scope.ORDINARY_FLAT_UNIT_RESOLUTION;
        public final boolean proved;
        public final String reason;
        public final Frontier input;
        public final Snapshot x,target;
        public final List<Proof> proofs;
        private Evidence(boolean proved,String reason,Snapshot x,Frontier input,Snapshot target,List<Proof> proofs) {
            this.proved=proved; this.reason=reason; this.x=x; this.input=input; this.target=target;
            this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    private static void add(List<Support> result,Clause actual,Support proof,Clause proposed,Runnable work) {
        work.run(); if(actual.equivalent(proposed) && !result.contains(proof)) result.add(proof);
    }
    private static List<Support> authenticate(Clause clause,Snapshot x,Frontier input,Snapshot target,
            List<ContextUniversalCompositionProof.Evidence> compositions,Runnable work) {
        if(clause==null) throw new IllegalStateException("Whole ordinary parent required");
        List<Support> result=new ArrayList<>();
        for(Entry entry:declarations(x,target,work))
            for(Clause actual:entry.clauses) add(result,actual,new Support(Collections.singleton(entry.source),0),clause,work);
        for(Product product:input==null?Collections.<Product>emptyList():input.products) add(result,product.clause,product.proof,clause,work);
        for(ContextUniversalCompositionProof.Evidence e:compositions) for(ContextUniversalCompositionProof.Proof p:e.proofs)
            add(result,p.conclusion,p.support,clause,work);
        if(result.isEmpty()) throw new IllegalStateException("Whole unit-resolution parent absent from exact cut sources or sealed proofs");
        return result;
    }
    static List<Entry> declarations(Snapshot x,Snapshot target,Runnable work) {
        ContextProductionFamilyAssembly.identity(x,true,work);
        List<Entry> result=new ArrayList<>(x.primary);
        if(target!=x) {
            ContextProductionFamilyAssembly.identity(target,false,work);
            if(x.inventory.context.equals(target.inventory.context)) throw new IllegalStateException("Unit cut cannot alias X");
            result.addAll(target.primary);
        }
        if(result.size()>64) throw new IllegalStateException("Unit proof declaration limit");
        for(Entry entry:result) { work.run(); if(entry.bundle!=null) throw new IllegalStateException("Ordinary unit proof cannot rebind witnesses"); }
        return result;
    }
    /** Detached primary-only cut: intentionally no old ground Frontier is created for universal units. */
    public static Evidence provePrimary(Snapshot x,Snapshot foreign,Clause left,int leftPivot,Clause right,int rightPivot,int maximumDepth,int maximumWork) {
        return proveInternal(x,null,foreign==null?x:foreign,left,leftPivot,right,rightPivot,Collections.emptyList(),maximumDepth,maximumWork);
    }
    /** Supplied composition evidence is historical and must belong to this exact input/cut. */
    public static Evidence prove(Frontier input,Snapshot target,Clause left,int leftPivot,Clause right,int rightPivot,
            List<ContextUniversalCompositionProof.Evidence> compositions,int maximumDepth,int maximumWork) {
        return proveInternal(input==null?null:input.x,input,target,left,leftPivot,right,rightPivot,compositions,maximumDepth,maximumWork);
    }
    private static Evidence proveInternal(Snapshot x,Frontier input,Snapshot target,Clause left,int leftPivot,Clause right,int rightPivot,
            List<ContextUniversalCompositionProof.Evidence> compositions,int maximumDepth,int maximumWork) {
        if(maximumDepth<0 || maximumWork<1) throw new IllegalArgumentException("Bounded unit proof required");
        return proveBound(x,input,target,left,leftPivot,right,rightPivot,compositions,maximumDepth,ContextGroundProductionRounds.budget(maximumWork));
    }
    static Evidence proveBound(Snapshot x,Frontier input,Snapshot target,Clause left,int leftPivot,Clause right,int rightPivot,
            List<ContextUniversalCompositionProof.Evidence> compositions,int maximumDepth,Runnable work) {
        try {
            if(x==null || target==null || input!=null && !input.cuts.contains(target)) throw new IllegalStateException("Exact sealed input/cut required");
            if(left==null || right==null || left.literals.size()!=1 && right.literals.size()!=1)
                throw new IllegalStateException("At least one whole unit parent required");
            List<ContextUniversalCompositionProof.Evidence> evidence=new ArrayList<>(compositions);
            for(ContextUniversalCompositionProof.Evidence e:evidence) {
                work.run(); if(input==null || e==null || !e.proved || e.input!=input || e.target!=target)
                    throw new IllegalStateException("Composition evidence has wrong input, cut or status");
            }
            List<Support> a=authenticate(left,x,input,target,evidence,work),b=authenticate(right,x,input,target,evidence,work);
            Clause conclusion=left.resolveWithUnits(right,leftPivot,rightPivot,work);
            List<Proof> result=new ArrayList<>();
            for(Support leftProof:a) for(Support rightProof:b) {
                work.run(); int depth=Math.max(leftProof.depth,rightProof.depth);
                if(depth==Integer.MAX_VALUE || ++depth>maximumDepth) throw new IllegalStateException("Unit resolution cumulative depth limit");
                Set<Source> sources=new LinkedHashSet<>();
                for(Source s:leftProof.sources) { work.run(); sources.add(s); }
                for(Source s:rightProof.sources) { work.run(); sources.add(s); }
                Support support=new Support(sources,depth); boolean duplicate=false;
                for(Proof old:result) { work.run(); if(old.support.equals(support)) { duplicate=true; break; } }
                if(!duplicate) {
                    if(result.size()>=256) throw new IllegalStateException("Unit resolution alternative proof limit");
                    result.add(new Proof(left,leftPivot,right,rightPivot,conclusion,leftProof,rightProof,support));
                }
            }
            return new Evidence(true,"Checked flat unit resolution/opposition; transport, coverage and admission unknown",x,input,target,result);
        } catch(Exception failure) { return new Evidence(false,failure.getMessage(),x,input,target,Collections.emptyList()); }
    }
}
