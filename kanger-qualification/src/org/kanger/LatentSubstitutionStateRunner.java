/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.*;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Rule;
import org.kanger.udf.UDF;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Emit sorted semantic projections for comparison in independent JVMs. */
public final class LatentSubstitutionStateRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-state-").toString());
        run("empty", "", "?p(1);");
        run("single-collision", "!p(1);", "?p(1);", "!~p(1);", "?p(1);", "?p(2);");
        run("chain-symmetry", "!edge(A,B); !@x @y edge(x,y) -> edge(y,x); !@x @y edge(x,y) -> path(x,y);",
                "?$x $y path(x,y);", "?path(B,A);", "?path(A,C);");
        run("functions", "!p(1); !p(2); !@x p(x) -> q(x+1);", "?$x q(x);", "?q(3);");
        run("many", "!p(1); !p(2); !p(3); !@x p(x) -> q(x);", "?$x q(x);", "?q(4);");
        run("natives", new String(Files.readAllBytes(Paths.get("natives.k")), StandardCharsets.UTF_8),
                "?$x male(x);", "?male(Tom);", "?father(John,Tom);", "!~father(John,Tom);");
    }

    private static void run(String name, String source, String... queries) throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind mind = new Mind(user);
        mind.setDebugLevel(0);
        if (!source.isEmpty() && !mind.compile(source)) throw new AssertionError("Rejected fixture " + name);
        for (String query : queries) {
            long started = System.nanoTime();
            SemanticEffectTelemetry.begin();
            Boolean result;
            SemanticEffectTelemetry.Snapshot effects;
            try { result = mind.query(query, null, false); }
            finally { effects = SemanticEffectTelemetry.end(); }
            System.out.println(name + " " + query + " => " + result);
            List<String> rows = new ArrayList<>();
            for (Map<String, ITerm> row : mind.getValues()) {
                Map<String, String> values = new TreeMap<>();
                for (Map.Entry<String, ITerm> e : row.entrySet()) values.put(e.getKey(), e.getValue().toString());
                rows.add(values.toString());
            }
            print("values", rows);
            rows.clear();
            for (IRule r : mind.getSolutions()) rows.add(((Rule) r).toString(mind));
            print("solutions", rows);
            rows.clear();
            for (IHypothesis h : mind.getHypothesis()) rows.add(((Hypothesis) h).toString(mind));
            print("hypotheses", rows);
            rows.clear();
            for (IRule r : mind.getRules()) if (!r.isDeleted(mind))
                rows.add(r.isGenerated() + ":" + ((Rule) r).toString(mind));
            print("rules", rows);
            LinkerStatistics s = mind.getLinkerStatistics();
            long[] passActions = s.getPassActionMasks();
            long completedPasses = 0;
            for (long count : passActions) completedPasses += count;
            if (completedPasses > s.getPasses()) throw new AssertionError("More completed than started passes");
            System.out.println("pass-actions=" + Arrays.toString(passActions)
                    + "; started=" + s.getPasses());
            System.err.println(name + "\t" + query + "\t" + s.getDomainPairs()
                    + "\t" + s.getUnificationAttempts() + "\t" + (System.nanoTime() - started));
            System.out.println("effects=" + s.getUnificationAttempts() + "," + s.getNewTValues()
                    + "," + effects.getNewCauses() + "," + effects.getNewTSolves()
                    + "," + effects.getNewGeneratedRules() + "," + effects.getSolveCandidates());
        }
    }

    private static void print(String name, List<String> rows) {
        Collections.sort(rows);
        System.out.println(name + "=" + rows);
    }
}
