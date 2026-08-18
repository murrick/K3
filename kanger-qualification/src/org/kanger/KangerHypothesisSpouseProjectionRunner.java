/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporary diagnostic for the spouse-variable projection counterexample. */
public final class KangerHypothesisSpouseProjectionRunner {

    private static final String QUERY = "?$x spouse(John,x);";

    private static final String SOURCE =
            "!@x $y parent(y,x);" +
            "!@x ~parent(x,x);" +
            "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
            "!@x @y daughter(x,y) -> female(x), child(x,y);" +
            "!@x @y son(x,y) -> male(x), child(x,y);" +
            "!@x @y father(x,y) -> male(x), parent(x,y);" +
            "!@x @y mother(x,y) -> female(x), parent(x,y);" +
            "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
            "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
            "!@x @y ~(parent(x,y), parent(y,x));" +
            "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y);" +
            "!@x @y ~(sibling(x,y), parent(x,y));" +
            "!@x @y sibling(x,y) -> sibling(y,x);" +
            "!@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);" +
            "!father(John, Tom);" +
            "!daughter(Sarah, John);" +
            "!mother(Mary,Sarah);" +
            "!child(Tom,Mary);" +
            "!age(John, 37);" +
            "!age(Tom, 12);" +
            "!age(Sarah, 4);";

    private KangerHypothesisSpouseProjectionRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-hypothesis-spouse-");
            System.setProperty("user.home", home.toAbsolutePath().toString());
            User user = (User) UserFactory.createUser("hypothesis-spouse", "hypothesis-spouse");
            new UDF().init(user);
            new DB().init(user);

            Mind mind = (Mind) new Mind(user).clearWorkspace();
            if (!mind.compile(SOURCE)) {
                throw new AssertionError("Qualification source compilation rejected");
            }

            QueryTaintSolve.begin();
            Boolean result;
            QueryTaintSolve.Snapshot solve;
            try {
                result = mind.query(QUERY, null, false);
            } finally {
                solve = QueryTaintSolve.end();
            }
            if (result != null) {
                throw new AssertionError("Expected WHO KNOWS, got " + result);
            }

            mind.optimizeHypothesis();
            List<IHypothesis> legacy = copy(mind.getHypothesis());
            List<IHypothesis> solved = solve.selectCandidates(mind, legacy);
            List<IHypothesis> solvedAlt = QueryTaintSolveAlternatives.expand(
                    mind, legacy, solved);
            Map<String, Boolean> exactAll = exact(mind, QUERY, legacy);

            print("LEGACY", mind, legacy);
            print("SOLVE", mind, solved);
            print("SOLVE_ALT", mind, solvedAlt);
            System.out.println("SPOUSE_PROJECTION_EXACT " + exactAll);
            System.out.printf("SPOUSE_PROJECTION_METRICS legacy=%d solve=%d solveAlt=%d exact=%d roots=%d operations=%d tuples=%d observed=%d marked=%d errors=%d%n",
                    legacy.size(), solved.size(), solvedAlt.size(), exactAll.size(),
                    solve.getQueryRootCount(), solve.getRelevantOperationCount(),
                    solve.getRelevantTupleCount(), solve.getObservedHypothesisCount(),
                    solve.getTaintedHypothesisCount(), solve.getInstrumentationErrorCount());
            System.out.println("SPOUSE_PROJECTION_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void print(String label, Mind mind,
                              Collection<IHypothesis> hypotheses) {
        List<String> values = new ArrayList<>();
        for (IHypothesis hypothesis : hypotheses) {
            values.add(((Hypothesis) hypothesis).toString(mind));
        }
        System.out.println("SPOUSE_PROJECTION_" + label + " " + values);
    }

    private static List<IHypothesis> copy(Iterable<IHypothesis> source) {
        List<IHypothesis> copy = new ArrayList<>();
        for (IHypothesis hypothesis : source) {
            copy.add(hypothesis);
        }
        return copy;
    }

    private static Map<String, Boolean> exact(Mind base, String query,
                                               Collection<IHypothesis> candidates)
            throws Exception {
        Map<String, Boolean> relevant = new LinkedHashMap<>();
        for (IHypothesis candidate : candidates) {
            String source = ((Hypothesis) candidate).toString(base);
            Mind child = new Mind(base);
            try {
                Rule rule = (Rule) child.compileLine(source, false, null);
                child.link(rule, false);
                boolean collision = child.analyze(rule, false);
                if (!collision) {
                    Boolean result = child.query(query, null, false);
                    if (result != null) {
                        relevant.put(source, result);
                    }
                }
            } finally {
                base.release(child);
            }
        }
        return relevant;
    }
}
