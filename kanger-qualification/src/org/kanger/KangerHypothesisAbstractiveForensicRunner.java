/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostic-only audit of the dormant abstractive-hypothesis policy. */
public final class KangerHypothesisAbstractiveForensicRunner {

    private static final String BAD = "?$x son(John,x);";
    private static final String GOOD = "?male(Tom);";

    private KangerHypothesisAbstractiveForensicRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            run();
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    public static void run() throws Exception {
        Path home = Files.createTempDirectory("kanger-abstractive-forensic-");
        System.setProperty("user.home", home.toAbsolutePath().toString());

        User user = (User) UserFactory.createUser("abstractive-forensic", "abstractive-forensic");
        new UDF().init(user);
        new DB().init(user);

        inspect(user, BAD);
        inspect(user, GOOD);
        System.out.println("HYPOTHESIS_ABSTRACTIVE_FORENSIC_OK");
    }

    private static void inspect(IUser user, String query) throws Exception {
        Mind mind = prepared(user);
        enableAbstractiveHypothesis(mind);

        Boolean known = mind.query(query, null, false);
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query + ", got " + known);
        }

        List<Hypothesis> raw = new ArrayList<Hypothesis>();
        Map<String, Hypothesis> byText = new LinkedHashMap<String, Hypothesis>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            Hypothesis h = (Hypothesis) hypothesis;
            raw.add(h);
            byText.put(h.toString(mind), h);
        }

        Map<String, Boolean> relevant = new LinkedHashMap<String, Boolean>();
        for (Hypothesis hypothesis : raw) {
            String source = hypothesis.toString(mind);
            Boolean answer = exact(mind, query, source);
            if (answer != null) {
                relevant.put(source, answer);
            }
        }

        int abstractCount = 0;
        int abstractRelevant = 0;
        int lineageAllowed = 0;
        int lineageRelevant = 0;
        int lineageFalsePositive = 0;
        int lineageFalseNegative = 0;

        for (Hypothesis hypothesis : raw) {
            String source = hypothesis.toString(mind);
            Lineage lineage = lineage(hypothesis, mind);
            boolean exact = relevant.containsKey(source);
            boolean allowed = lineage.cvars == 0 || lineage.allQueryRoot;

            if (lineage.cvars > 0) {
                ++abstractCount;
                if (exact) {
                    ++abstractRelevant;
                }
            }
            if (allowed) {
                ++lineageAllowed;
                if (exact) {
                    ++lineageRelevant;
                } else {
                    ++lineageFalsePositive;
                }
            } else if (exact) {
                ++lineageFalseNegative;
            }

            System.out.printf("ABSTRACTIVE_LINEAGE_H query=%s abstract=%s cvars=%d queryRoots=%d allQueryRoot=%s allowed=%s exact=%s answer=%s h=%s%n",
                    query,
                    Boolean.toString(lineage.cvars > 0),
                    lineage.cvars,
                    lineage.queryRoots,
                    Boolean.toString(lineage.allQueryRoot),
                    Boolean.toString(allowed),
                    Boolean.toString(exact),
                    exact ? relevant.get(source).toString() : "WHO_KNOWS_OR_REJECTED",
                    source);
        }

        System.out.printf("ABSTRACTIVE_SUMMARY query=%s raw=%d abstract=%d exactRelevant=%d abstractRelevant=%d lineageAllowed=%d lineageRelevant=%d lineageFalsePositive=%d lineageFalseNegative=%d%n",
                query, raw.size(), abstractCount, relevant.size(), abstractRelevant,
                lineageAllowed, lineageRelevant, lineageFalsePositive, lineageFalseNegative);

        for (Map.Entry<String, Boolean> entry : relevant.entrySet()) {
            System.out.printf("ABSTRACTIVE_EXACT_H query=%s answer=%s h=%s%n",
                    query, entry.getValue().toString(), entry.getKey());
        }
    }

    private static Lineage lineage(Hypothesis hypothesis, Mind mind) throws Exception {
        int cvars = 0;
        int queryRoots = 0;
        boolean allQueryRoot = true;

        for (IArgument argument : hypothesis.getArguments()) {
            if (argument.isEmpty(mind)) {
                continue;
            }
            ITerm value = argument.getValue(mind);
            if (value == null || !value.isCVariable()) {
                continue;
            }

            ++cvars;
            Term root = (Term) value;
            ITerm parent;
            while ((parent = root.getParent(mind)) != null && parent.isCVariable()) {
                root = (Term) parent;
            }

            IRule owner = root.getRule(mind);
            boolean queryRoot = owner != null && owner.isQuery();
            if (queryRoot) {
                ++queryRoots;
            } else {
                allQueryRoot = false;
            }
        }

        return new Lineage(cvars, queryRoots, cvars > 0 && allQueryRoot);
    }

    private static final class Lineage {
        private final int cvars;
        private final int queryRoots;
        private final boolean allQueryRoot;

        private Lineage(int cvars, int queryRoots, boolean allQueryRoot) {
            this.cvars = cvars;
            this.queryRoots = queryRoots;
            this.allQueryRoot = allQueryRoot;
        }
    }

    private static void enableAbstractiveHypothesis(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("includeAbstractiveHypothesis");
        field.setAccessible(true);
        field.setBoolean(mind, true);
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
