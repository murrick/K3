/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalGroupedCandidates.Receipt;

/** Bounded local fixed point relative to one actual immutable incoming compound exchange. */
final class ContextUniversalGroupedLocalRounds {
    static final class Limits {
        final int passes,kernelRounds,candidates,clauses,alternatives,depth,attempts,work;
        Limits(int passes,int kernelRounds,int candidates,int clauses,int alternatives,int depth,int attempts,int work){if(passes<1||passes>64||kernelRounds<1||kernelRounds>64||candidates<1||candidates>512||clauses<1||clauses>512||alternatives<1||alternatives>256||depth<0||depth>64||attempts<1||attempts>65536||work<1)throw new IllegalArgumentException("Bounded local grouped rounds required");this.passes=passes;this.kernelRounds=kernelRounds;this.candidates=candidates;this.clauses=clauses;this.alternatives=alternatives;this.depth=depth;this.attempts=attempts;this.work=work;}
    }
    static final class Pass {
        final int number;final Result before,after;final Receipt candidates;final boolean changed;
        private Pass(int number,Result before,Receipt candidates,Result after,boolean changed){this.number=number;this.before=before;this.candidates=candidates;this.after=after;this.changed=changed;}
    }
    static final class Certificate {
        final Result initial,output;final List<Pass> history;final Receipt terminal;final Limits limits;
        private Certificate(Result initial,List<Pass> history,Receipt terminal,Limits limits){this.initial=initial;this.history=Collections.unmodifiableList(new ArrayList<>(history));output=history.get(history.size()-1).after;this.terminal=terminal;this.limits=limits;}
    }
    static final class Closure {
        final boolean stable;final String reason;final Certificate output;
        private Closure(boolean stable,String reason,Certificate output){this.stable=stable;this.reason=reason;this.output=output;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static boolean sameClause(Proof a,Proof b,Runnable work){return a.clause==null?b.clause==null:b.clause!=null&&a.clause.equivalent(b.clause,work);}
    private static void unique(Result result,Runnable work){for(int i=0;i<result.proofs.size();++i)for(int j=0;j<i;++j){work.run();Proof a=result.proofs.get(i),b=result.proofs.get(j);require(!sameClause(a,b,work)||!a.support.sources.equals(b.support.sources),"Duplicate local clause/exact-Source pool key");}}
    private static boolean contains(Result result,Proof expected,boolean exactDepth,Runnable work){for(Proof actual:result.proofs){work.run();if(sameClause(actual,expected,work)&&actual.support.sources.equals(expected.support.sources)&&(exactDepth?actual.support.depth==expected.support.depth:actual.support.depth<=expected.support.depth))return true;}return false;}
    static boolean sameContents(Result a,Result b,Runnable work){require(a!=null&&b!=null,"Actual local pools required");unique(a,work);unique(b,work);if(a.proofs.size()!=b.proofs.size())return false;for(Proof expected:a.proofs){work.run();if(!contains(b,expected,true,work))return false;}return true;}
    private static void nonWorsening(Result before,Result after,Runnable work){unique(before,work);unique(after,work);for(Proof old:before.proofs){work.run();require(contains(after,old,false,work),"Local pass lost a clause/Source alternative or worsened its grade");}}
    private static void policy(Receipt candidates,Limits l){require(candidates.maximumCandidates==l.candidates&&candidates.maximumClauses==l.clauses&&candidates.maximumAlternatives==l.alternatives&&candidates.maximumDepth==64&&candidates.maximumAttempts==l.attempts,"Different sealed local enumeration policy");}
    private static Receipt scan(Result base,Limits l,Runnable work){return ContextUniversalGroupedCandidates.checked(base,l.candidates,l.clauses,l.alternatives,64,l.attempts,work);}
    private static void bound(Result before,Receipt candidates,Result after,Limits l,Runnable work){work.run();policy(candidates,l);require(candidates.base==before&&after.closed&&after.groupedBase==before&&after.groupedInputs.equals(candidates.minima),"Kernel manifest must be the FULL actual checked minima inventory");require(after.input==before.input&&after.common==before.common&&after.pool==before.pool&&after.exchange==before.exchange&&after.compounds==before.compounds,"Local pass changed immutable native/common/private/incoming frame");}
    private static boolean sameCandidates(Receipt a,Receipt b,Runnable work){if(a.minima.size()!=b.minima.size())return false;for(ContextUniversalGroupedCompletion.Receipt expected:a.minima){work.run();boolean found=false;for(ContextUniversalGroupedCompletion.Receipt actual:b.minima){work.run();if(actual.clause.equivalent(expected.clause,work)&&actual.support.equals(expected.support)){found=true;break;}}if(!found)return false;}return true;}
    private static void covered(Result output,Receipt terminal,Runnable work){for(ContextUniversalGroupedCompletion.Receipt expected:terminal.minima){work.run();boolean found=false;for(Proof actual:output.proofs){work.run();if(actual.clause!=null&&actual.clause.equivalent(expected.clause,work)&&actual.support.sources.equals(expected.support.sources)&&actual.support.depth<=expected.support.depth){found=true;break;}}require(found,"Final fresh grouped candidate not covered by stable local pool");}}
    static Closure run(Result initial,Limits limits) {
        if(limits==null)throw new IllegalArgumentException("Actual local limits required");Runnable work=ContextGroundProductionRounds.budget(limits.work);
        try{require(initial!=null&&initial.closed&&initial.compounds!=null,"Actual closed compound receiver required");ContextUniversalFrozenConstraints.checkSaturation(initial,64,work);List<Pass> history=new ArrayList<>();Result before=initial;
            for(int number=1;number<=limits.passes;++number){work.run();Receipt candidates=scan(before,limits,work);Result after=ContextUniversalFrozenConstraints.closeSignedGroupedShared(before,candidates.minima,limits.kernelRounds,limits.depth,limits.clauses,limits.alternatives,limits.work,work);require(after.closed,after.reason);bound(before,candidates,after,limits,work);ContextUniversalFrozenConstraints.checkSaturation(after,limits.depth,work);nonWorsening(before,after,work);boolean changed=!sameContents(before,after,work);history.add(new Pass(number,before,candidates,after,changed));
                if(!changed){Receipt terminal=scan(after,limits,work);require(sameCandidates(candidates,terminal,work),"Fresh final candidate minima changed after purported local stability");covered(after,terminal,work);return new Closure(true,"Local ordinary/grouped Source/grade pool stable for fixed actual incoming exchange; all-cut/native/admission coverage remains open",new Certificate(initial,history,terminal,limits));}before=after;
            }throw new IllegalStateException("Local grouped pass limit");
        }catch(Exception failure){return new Closure(false,failure.toString(),null);}
    }
    static void check(Certificate certificate,Runnable work) {
        require(certificate!=null&&certificate.limits!=null&&certificate.initial!=null&&certificate.initial.closed&&certificate.initial.compounds!=null&&!certificate.history.isEmpty()&&certificate.history.size()<=certificate.limits.passes,"Actual bounded local stability certificate required");Limits l=certificate.limits;new Limits(l.passes,l.kernelRounds,l.candidates,l.clauses,l.alternatives,l.depth,l.attempts,l.work);ContextUniversalFrozenConstraints.checkSaturation(certificate.initial,64,work);Result before=certificate.initial;
        for(int i=0;i<certificate.history.size();++i){work.run();Pass pass=certificate.history.get(i);require(pass!=null&&pass.candidates!=null&&pass.after!=null&&pass.number==i+1&&pass.before==before,"Different actual local pass chain/order");ContextUniversalGroupedCandidates.check(pass.candidates,64,work);bound(before,pass.candidates,pass.after,l,work);ContextUniversalFrozenConstraints.checkSaturation(pass.after,l.depth,work);nonWorsening(before,pass.after,work);boolean changed=!sameContents(before,pass.after,work);require(pass.changed==changed&&(i==certificate.history.size()-1?!changed:changed),"Invalid local stability flag or ignored earlier fixed point");before=pass.after;}
        require(certificate.output==before&&certificate.terminal!=null&&certificate.terminal.base==before,"Final check must consume ACTUAL stable output");policy(certificate.terminal,l);ContextUniversalGroupedCandidates.check(certificate.terminal,64,work);require(sameCandidates(certificate.history.get(certificate.history.size()-1).candidates,certificate.terminal,work),"Final fresh minimum inventory differs");covered(before,certificate.terminal,work);
    }
}
