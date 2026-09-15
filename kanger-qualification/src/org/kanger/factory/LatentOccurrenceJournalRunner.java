/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import java.nio.file.Files;
import java.util.Arrays;

/** Exercises ordered duplicates and nested rollback of immutable occurrence rows. */
public final class LatentOccurrenceJournalRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-journal-").toString());
        Mind mind = new Mind(new User());
        if (!mind.compile("!p(1);")) throw new AssertionError("fixture");
        IRule existing = (IRule) mind.getRules().iterator().next();
        Domain domain = ((Rule) existing).getDomain();
        Rule first = rule(mind, domain, 9000000000L);
        Rule second = rule(mind, domain, 9000000001L);
        RuleCandidateIndex parent = new RuleCandidateIndex();
        if (!parent.hasOccurrenceIndex()) throw new AssertionError("Run in factory mode");
        parent.indexRule(first);
        present(parent, first, domain);
        parent.mark();
        parent.unindexRule(first);
        absent(parent, first, domain);
        parent.mark();
        parent.indexRule(first);
        parent.commit();
        parent.release();
        present(parent, first, domain);
        parent.mark();
        parent.indexRule(second);
        parent.release();
        absent(parent, second, domain);

        RuleCandidateIndex child = new RuleCandidateIndex();
        child.indexRule(second);
        parent.mark();
        parent.mergeFrom(child);
        child.clear();
        present(parent, second, domain);
        parent.release();
        absent(parent, second, domain);
        present(parent, first, domain);
        parent.clear();
        absent(parent, first, domain);
        System.out.println("LATENT_OCCURRENCE_JOURNAL_PASS");
    }

    private static Rule rule(Mind mind, Domain domain, long id) throws Exception {
        Rule rule = new Rule(mind);
        rule.setId(id);
        rule.getTree().get(0).add(domain);
        rule.getTree().get(0).add(domain);
        rule.cloneTree(rule.getTree().get(0));
        return rule;
    }

    private static void present(RuleCandidateIndex index, Rule rule, Domain domain) throws Exception {
        long[] ids = index.findOccurrences(rule.getId(), domain.getPredicateId(), domain.isAntc());
        long d = domain.getId();
        if (!Arrays.equals(ids, new long[]{d, d, d, d})) throw new AssertionError("duplicate/order/journal");
        if (index.findOccurrences(rule.getId(), domain.getPredicateId(), !domain.isAntc()).length != 0)
            throw new AssertionError("opposite polarity");
    }

    private static void absent(RuleCandidateIndex index, Rule rule, Domain domain) throws Exception {
        if (index.findOccurrences(rule.getId(), domain.getPredicateId(), domain.isAntc()) != null)
            throw new AssertionError("discarded row retained");
    }
}
