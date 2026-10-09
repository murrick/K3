/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * First DMZ candidate characterization, not an automatic DMZ implementation.
 * Uses an explicitly known pair of conflicting ground supports. Alternatives
 * stay in real Mind overlays; no source is copied to create the alternative.
 * The alternate branch uses existing deletion/re-saturation to retract one
 * ground support, not a general rule-application exclusion mechanism.
 */
public final class DmzTransactionBranchProbeRunner {
    private static final String RULES =
            "!@x a(x) -> male(x); "
            + "!@x b(x) -> ~male(x); "
            + "!@x a(x) -> shared(x); "
            + "!@x c(x) -> shared(x); "
            + "!@x male(x) -> positive(x); "
            + "!@x ~male(x) -> negative(x);";
    private static int checks;

    private DmzTransactionBranchProbeRunner() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("dmz-probe-").toString());
        int rounds = args.length == 0 ? 3 : Integer.parseInt(args[0]);
        require(rounds > 0, "positive rounds");
        for (int i = 0; i < rounds; ++i) {
            symmetricBranch(false);
            symmetricBranch(true);
            siblingBranches();
            qHasPriority();
        }
        System.out.println("DMZ_TRANSACTION_BRANCH_PROBE_PASS rounds=" + rounds
                + " checks=" + checks);
    }

    private static Mind root() throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind q = new Mind(user);
        user.setCurrentMind(q);
        require(q.compile(RULES, null, false), "compile Q rules");
        return q;
    }

    private static void symmetricBranch(boolean negativeFirst) throws Exception {
        Mind q = root();
        List<String> originalQ = visible(q);
        int terms = q.getTerms().size();
        int predicates = q.getPredicates().size();
        Mind current = Mind.ephemeralChild(q);
        Mind alternate = null;
        try {
            String first = negativeFirst ? "b" : "a";
            String second = negativeFirst ? "a" : "b";
            String selected = "!" + first + "(John);";
            String incoming = "!" + second + "(John);";
            require(current.compile(selected + " !a(Mary); !c(John); !stable(Tom);",
                    null, false), "first branch inputs");
            truth(current, "?male(John);", !negativeFirst);
            truth(current, "?positive(John);", negativeFirst ? null : Boolean.TRUE);
            truth(current, "?negative(John);", negativeFirst ? Boolean.TRUE : null);
            truth(current, "?positive(Mary);", true);
            truth(current, "?shared(John);", true);

            // Domain's canonical opposite-side rendering uses '?' rather than '!~'.
            String production = negativeFirst ? "?male(John);" : "!male(John);";
            require(hasAncestor(current, production, selected),
                    "generated conflict side exposes ground-support ancestry");
            List<String> originalCurrent = visible(current);
            require(!current.compile(incoming, null, false), "opposite chain collides");
            ContextQualification conflict = current.getLastCompileQualification();
            require(conflict != null && !conflict.isValid()
                    && !conflict.getCollisions().isEmpty(), "collision witnesses retained");
            require(originalCurrent.equals(visible(current)), "rejected input leaves branch unchanged");

            // Symmetric block for this known ground pair: current keeps first,
            // alternative masks first and takes second. There is no commit to Q.
            alternate = Mind.ephemeralChild(current);
            require(Boolean.TRUE.equals(alternate.query("-" + first + "(John);", null, false)),
                    "mask ground support only in alternative");
            truth(alternate, "?positive(John);", null);
            truth(alternate, "?negative(John);", null);
            truth(alternate, "?shared(John);", true); // independent c(John) survives
            truth(alternate, "?positive(Mary);", true); // same general a -> male rule survives
            require(alternate.compile(incoming, null, false), "alternative accepts opposite support");
            truth(alternate, "?male(John);", negativeFirst);
            truth(alternate, "?positive(John);", negativeFirst ? Boolean.TRUE : null);
            truth(alternate, "?negative(John);", negativeFirst ? null : Boolean.TRUE);
            truth(alternate, "?shared(John);", true);
            truth(alternate, "?positive(Mary);", true);
            truth(alternate, "?stable(Tom);", true);
            require(originalCurrent.equals(visible(current)), "alternative does not mutate current");

            compareFresh(q, current, selected);
            compareFresh(q, alternate, incoming);
            require(originalCurrent.equals(visible(current)), "oracle queries do not mutate current");
            require(originalQ.equals(visible(q)), "Q knowledge unchanged while branches live");
            truth(q, "?male(John);", null);
            truth(q, "?stable(Tom);", null);
            require(q.getUser().getCurrentMind() == q, "user current slot remains Q");
            require(counter(q) == 1 && counter(current) == 1,
                    "only live branch reservations remain");
            System.out.println("SYMMETRIC_BRANCH_PASS first=" + first
                    + " witnesses=" + conflict.getCollisions().size());
        } finally {
            if (alternate != null) current.discardEphemeral(alternate);
            q.discardEphemeral(current);
        }
        require(counter(q) == 0, "all Q reservations settled");
        require(originalQ.equals(visible(q)), "Q unchanged after branch disposal");
        require(q.getTerms().size() == terms && q.getPredicates().size() == predicates,
                "branch-only dictionaries never reach Q");
    }

    private static void qHasPriority() throws Exception {
        Mind q = root();
        require(q.compile("!male(John);", null, false), "authoritative Q input");
        List<String> originalQ = visible(q);
        Mind branch = Mind.ephemeralChild(q);
        try {
            require(branch.compile("!stable(Tom);", null, false), "independent input");
            List<String> before = visible(branch);
            require(!branch.compile("!b(John);", null, false), "derived external conflict rejected by Q");
            require(before.equals(visible(branch)), "Q rejection is atomic");
            truth(branch, "?male(John);", true);
            truth(branch, "?positive(John);", true);
            truth(branch, "?negative(John);", null);
            // Another substitution of the same b -> ~male production is allowed.
            require(branch.compile("!b(Mary);", null, false), "other substitution remains allowed");
            truth(branch, "?male(Mary);", false);
            truth(branch, "?negative(Mary);", true);
            truth(branch, "?stable(Tom);", true);
            require(originalQ.equals(visible(q)), "external rejection never weakens Q");
            System.out.println("Q_PRIORITY_PASS");
        } finally {
            q.discardEphemeral(branch);
        }
        require(counter(q) == 0 && originalQ.equals(visible(q)), "Q priority cleanup");
    }

    private static void siblingBranches() throws Exception {
        Mind q = root();
        List<String> before = visible(q);
        Mind a = Mind.ephemeralChild(q);
        Mind b = Mind.ephemeralChild(q);
        try {
            require(counter(q) == 2, "two sibling transaction reservations");
            require(a.compile("!a(John);", null, false), "positive sibling");
            require(b.compile("!b(John);", null, false), "negative sibling");
            truth(a, "?male(John);", true);
            truth(b, "?male(John);", false);
            truth(a, "?negative(John);", null);
            truth(b, "?positive(John);", null);
            require(before.equals(visible(q)), "opposite siblings never enter Q");
            System.out.println("SIBLING_BRANCHES_PASS");
        } finally {
            q.discardEphemeral(b);
            q.discardEphemeral(a);
        }
        require(counter(q) == 0, "sibling reservations settled");
    }

    private static void compareFresh(Mind q, Mind branch, String input) throws Exception {
        Mind oracle = Mind.ephemeralChild(q);
        try {
            require(oracle.compile(input + " !a(Mary); !c(John); !stable(Tom);",
                    null, false), "fresh branch oracle");
            String[] queries = { "?male(John);", "?positive(John);", "?negative(John);",
                    "?shared(John);", "?positive(Mary);", "?stable(Tom);" };
            for (String query : queries) {
                require(Objects.equals(branch.query(query, null, false),
                        oracle.query(query, null, false)), "fresh oracle differs: " + query);
            }
            for (String query : new String[] { "?$x positive(x);", "?$x negative(x);",
                    "?$x shared(x);" }) {
                require(Objects.equals(branch.query(query, null, false),
                        oracle.query(query, null, false)), "enumeration truth differs: " + query);
                require(rows(branch).equals(rows(oracle)), "enumeration values differ: " + query);
            }
        } finally {
            q.discardEphemeral(oracle);
        }
    }

    private static Set<String> rows(Mind mind) throws Exception {
        Set<String> result = new HashSet<String>();
        for (Map<String, ITerm> row : mind.getValues()) {
            ITerm value = row.get("x");
            require(value != null, "enumeration binds x");
            result.add(value.toString());
        }
        return result;
    }

    private static boolean hasAncestor(Mind mind, String generated, String input) throws Exception {
        for (IRule candidate : mind.getRules()) {
            if (!candidate.isDeleted(mind) && candidate.isGenerated()
                    && ((Rule) candidate).getDomain().toString(mind).equals(generated)) {
                if (hasAncestor(mind, candidate, input, new HashSet<Long>())) return true;
            }
        }
        return false;
    }

    private static boolean hasAncestor(Mind mind, IRule rule, String input, Set<Long> seen)
            throws Exception {
        if (rule == null || !seen.add(rule.getId())) return false;
        if (input.equals(rule.getOrigin())) return true;
        for (ICause cause : rule.getCauses()) {
            if (hasAncestor(mind, cause.getRule(mind), input, seen)
                    || hasAncestor(mind, cause.getDonor(mind), input, seen)) return true;
        }
        return false;
    }

    private static List<String> visible(Mind mind) throws Exception {
        List<String> result = new ArrayList<String>();
        for (IRule rule : mind.getRules()) {
            if (!rule.isDeleted(mind)) result.add(rule.isGenerated() + ":" + ((Rule) rule).toString(mind));
        }
        Collections.sort(result);
        return result;
    }

    private static void truth(Mind mind, String query, Boolean expected) throws Exception {
        Boolean actual = mind.query(query, null, false);
        require(Objects.equals(expected, actual), query + " expected=" + expected + " actual=" + actual);
    }

    private static int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static void require(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }
}
