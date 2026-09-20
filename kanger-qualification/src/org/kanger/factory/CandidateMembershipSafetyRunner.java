package org.kanger.factory;

import java.nio.file.Files;
import java.util.*;
import org.kanger.*;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Isolates ordered positional selection; end-to-end runners cover batching. */
public final class CandidateMembershipSafetyRunner {
    private static int checks;
    private static ArgumentsList args(ITerm... terms) {
        ArgumentsList result = new ArgumentsList();
        for (ITerm term : terms) result.add(term == null ? new Argument() : new Argument(term));
        return result;
    }
    private static Rule rule(Mind mind, Predicate p, long id, boolean fallback, ITerm... terms) throws Exception {
        Rule rule = new Rule(mind); rule.setId(id); rule.setGenerated(fallback);
        rule.getTree().get(0).add(new Domain(p,true,args(terms),rule));
        return rule;
    }
    private static Domain source(Predicate p, ITerm... terms) {
        return new Domain(p,false,args(terms)) {
            @Override public boolean isSubstitutable() { return true; }
        };
    }
    private static void check(RuleCandidateIndex index, Mind mind, Domain source, Long... expected) throws Exception {
        for (String mode : Arrays.asList("off","on","verify")) {
            System.setProperty("kanger.experiment.candidateMembershipFilter",String.valueOf(!mode.equals("off")));
            System.setProperty("kanger.experiment.verifyCandidateMembershipFilter",String.valueOf(mode.equals("verify")));
            LinkedHashSet<Long> result = new LinkedHashSet<>();
            index.collectResolvedLocal(source,true,mind,result);
            if (!new ArrayList<>(result).equals(Arrays.asList(expected)))
                throw new AssertionError(mode + " expected " + Arrays.toString(expected) + " got " + result);
            // Caller mutation must not touch buckets or the next query.
            result.clear(); result.add(123456L);
            LinkedHashSet<Long> again = new LinkedHashSet<>();
            index.collectResolvedLocal(source,true,mind,again);
            if (!new ArrayList<>(again).equals(Arrays.asList(expected))) throw new AssertionError("snapshot alias");
            ++checks;
        }
    }
    public static void main(String[] ignored) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("candidate-membership-").toString());
        Mind mind = new Mind(new User());
        ITerm a=mind.getTerms().add("a"), b=mind.getTerms().add("b"), c=mind.getTerms().add("c");
        Predicate p=mind.getPredicates().add(mind.getTerms().add("p"),2);
        RuleCandidateIndex index = new RuleCandidateIndex();
        Domain ab=source(p,a,b), cc=source(p,c,c);
        check(index,mind,ab); // Empty signature.
        Rule exact=rule(mind,p,9,false,a,b), wildcard=rule(mind,p,2,false,null,b);
        Rule fallback=rule(mind,p,7,true,c,a), mismatch=rule(mind,p,4,false,a,c);
        index.indexRule(exact);
        check(index,mind,ab,9L);
        check(index,mind,cc); // Both exact and wildcard buckets absent.
        index.indexRule(wildcard);
        check(index,mind,source(p,c,b),2L); // Wildcard-only.
        index.indexRule(fallback); index.indexRule(mismatch);
        check(index,mind,ab,9L,2L,7L); // Ordered union, second-position pruning.
        check(index,mind,cc,7L); // Fallback-only, despite incompatible terms.
        check(index,mind,source(p,null,null),9L,2L,7L,4L); // Unresolved positions.
        check(index,mind,source(p,a,c),7L,4L);
        index.mark();
        Rule added=rule(mind,p,0,false,a,b); index.indexRule(added);
        check(index,mind,ab,9L,2L,7L,0L);
        index.mark(); index.unindexRule(added); index.commit(); index.release();
        check(index,mind,ab,9L,2L,7L);
        RuleCandidateIndex child = new RuleCandidateIndex();
        child.indexRule(rule(mind,p,Long.MAX_VALUE,false,a,b)); index.mergeFrom(child);
        child.clear();
        check(index,mind,ab,9L,2L,7L,Long.MAX_VALUE);
        index.unindexRule(fallback);
        check(index,mind,cc);
        index.clear(); check(index,mind,ab);
        System.out.println("CANDIDATE_MEMBERSHIP_BOUNDARIES_PASS checks="+checks);
    }
}
