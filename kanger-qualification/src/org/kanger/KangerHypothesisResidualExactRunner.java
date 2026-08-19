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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Diagnostic-only EXACT audit of every unique Linker residual hypothesis. */
public final class KangerHypothesisResidualExactRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisResidualExactRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path home = Files.createTempDirectory("kanger-residual-exact-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser("residual-exact", "residual-exact");
            new UDF().init(user);
            new DB().init(user);

            inspect(user, BAD);
            inspect(user, GOOD);

            System.out.println("HYPOTHESIS_RESIDUAL_EXACT_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void inspect(IUser user, String query) throws Exception {
        Mind mind = prepared(user);

        QueryHypothesisFormationTrace.begin();
        Boolean known;
        QueryHypothesisFormationTrace.Snapshot formation;
        try {
            known = mind.query(query, null, false);
        } finally {
            formation = QueryHypothesisFormationTrace.end();
        }
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query + ", got " + known);
        }

        Set<String> residual = new LinkedHashSet<String>();
        for (QueryHypothesisFormationTrace.Event event : formation.getEvents()) {
            residual.add(event.getHypothesis());
        }

        Set<String> raw = new LinkedHashSet<String>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            raw.add(((Hypothesis) hypothesis).toString(mind));
        }

        Map<String, Boolean> relevant = new LinkedHashMap<String, Boolean>();
        for (String source : residual) {
            Boolean answer = exact(mind, query, source);
            if (answer != null) {
                relevant.put(source, answer);
            }
        }

        int relevantRaw = 0;
        for (String source : relevant.keySet()) {
            if (raw.contains(source)) {
                ++relevantRaw;
            }
        }

        System.out.printf("RESIDUAL_EXACT_SUMMARY query=%s events=%d unique=%d raw=%d exactRelevant=%d exactRelevantRaw=%d exactRelevantNotRaw=%d%n",
                query,
                formation.getEvents().size(),
                residual.size(),
                raw.size(),
                relevant.size(),
                relevantRaw,
                relevant.size() - relevantRaw);

        for (Map.Entry<String, Boolean> entry : relevant.entrySet()) {
            System.out.printf("RESIDUAL_EXACT_H query=%s rawFinal=%s answer=%s h=%s%n",
                    query,
                    Boolean.toString(raw.contains(entry.getKey())),
                    entry.getValue().toString(),
                    entry.getKey());
        }
    }

    private static Boolean exact(Mind base, String query, String source) throws Exception {
        Mind child = new Mind(base);
        try {
            Rule rule = (Rule) child.compileLine(source, false, null);
            if (rule == null) {
                return null;
            }
            child.link(rule, false);
            boolean collision = child.analyze(rule, false);
            if (collision) {
                return null;
            }
            return child.query(query, null, false);
        } finally {
            base.release(child);
        }
    }

    private static Mind prepared(IUser user) throws Exception {
        Mind mind = (Mind) new Mind(user).clearWorkspace();
        String source = new String(Files.readAllBytes(Paths.get("natives.k")),
                StandardCharsets.UTF_8);
        if (!mind.compile(source)) {
            throw new AssertionError("natives.k compilation rejected");
        }
        return mind;
    }
}
