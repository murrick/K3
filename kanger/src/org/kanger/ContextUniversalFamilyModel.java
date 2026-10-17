/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalQuotientModel.Model;

/** Historical flat universal family consistency evidence. No native write authority. */
public final class ContextUniversalFamilyModel {
    public enum Scope { FACTORED_STABLE_FLAT_UNIVERSAL_FAMILY }
    public static final class Cut {
        public final Receipt receipt;
        public final Model model;
        private Cut(Receipt receipt,Model model) { this.receipt=receipt; this.model=model; }
    }
    public static final class Certificate {
        public final Scope scope=Scope.FACTORED_STABLE_FLAT_UNIVERSAL_FAMILY;
        public final Frontier input;
        public final List<Cut> cuts;
        public final int maximumDepth;
        private Certificate(Frontier input,List<Cut> cuts,int maximumDepth) {
            this.input=input; this.cuts=Collections.unmodifiableList(new ArrayList<>(cuts)); this.maximumDepth=maximumDepth;
        }
    }
    public static final class Result {
        public final boolean certified;
        public final String reason;
        public final Certificate certificate;
        private Result(boolean certified,String reason,Certificate certificate) { this.certified=certified; this.reason=reason; this.certificate=certificate; }
    }
    /** Every exact private closure, including self, needs its own checked model. Order is irrelevant. */
    public static Result certify(Frontier input,List<Receipt> receipts,List<Model> models,int maximumDepth,int maximumInstances,int maximumWork) {
        if(maximumDepth<0 || maximumInstances<1 || maximumWork<1) throw new IllegalArgumentException("Bounded family model required");
        return certifyBound(input,receipts,models,maximumDepth,new int[]{Math.min(maximumInstances,65536)},ContextGroundProductionRounds.budget(maximumWork));
    }
    static Result certifyBound(Frontier input,List<Receipt> receipts,List<Model> models,int maximumDepth,int[] remaining,Runnable work) {
        try {
            List<Receipt> outputs=new ArrayList<>(receipts); List<Model> witnesses=new ArrayList<>(models);
            ContextUniversalFamilyCoverage.Audit coverage=ContextUniversalFamilyCoverage.audit(input,outputs,
                ContextUniversalFamilyCoverage.Mode.FACTORED_STABLE_SHARED,maximumDepth,work);
            if(!coverage.covered) throw new IllegalStateException(coverage.reason);
            if(witnesses.size()!=outputs.size()) throw new IllegalStateException("One exact model per private cut required");
            Set<Model> used=Collections.newSetFromMap(new IdentityHashMap<Model,Boolean>()); List<Cut> cuts=new ArrayList<>();
            for(Receipt receipt:outputs) {
                Model found=null;
                for(Model model:witnesses) {
                    work.run(); if(model==null) throw new IllegalStateException("Missing private model");
                    if(model.ledger==receipt.closureStep.input) {
                        if(found!=null) throw new IllegalStateException("Duplicate exact private model"); found=model;
                    }
                }
                if(found==null || !used.add(found)) throw new IllegalStateException("Missing or reused exact historical private model");
                ContextUniversalQuotientModel.Check check=ContextUniversalQuotientModel.check(receipt.closureStep.input,found,remaining,work);
                if(!check.valid) throw new IllegalStateException(check.reason);
                cuts.add(new Cut(receipt,found));
            }
            return new Result(true,"Every exact stable private cut has an all-value model; native write admission unknown",new Certificate(input,cuts,maximumDepth));
        } catch(Exception failure) { return new Result(false,failure.getMessage(),null); }
    }
}
