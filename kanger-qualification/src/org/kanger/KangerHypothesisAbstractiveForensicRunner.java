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
            Path home = Files.createTempDirectory("kanger-abstractive-forensic-");
            System.setProperty("user.home", home.toAbsolutePath().toString());

            User user = (User) UserFactory.createUser("abstractive-forensic", "abstractive-forensic");
            new UDF().init(user);
            new DB().init(user);

            inspect(user, BAD);
            inspect(user, GOOD);

            System.out.println("HYPOTHESIS_ABSTRACTIVE_FORENSIC_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void inspect(IUser user, String query) throws Exception {
        Mind mind = prepared(user);
        enableAbstractiveHypothesis(mind);

        Boolean known = mind.query(query, null, false);
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query + ", got " + known);
        }

        List<String> raw = new ArrayList<String>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            raw.add(((Hypothesis) hypothesis).toString(mind));
        }

        Map<String, Boolean> relevant = new LinkedHashMap<String, Boolean>();
        for (String source : raw) {
            Boolean answer = exact(mind, query, source);
            if (answer != null) {
                relevant.put(source, answer);
            }
        }

        int abstractCount = 0;
        int abstractRelevant = 0;
        for (String source : raw) {
            if (source.indexOf('$') >= 0 || source.indexOf('?') > 0) {
                ++abstractCount;
                if (relevant.containsKey(source)) {
                    ++abstractRelevant;
                }
            }
        }

        System.out.printf("ABSTRACTIVE_SUMMARY query=%s raw=%d abstract=%d exactRelevant=%d abstractRelevant=%d%n",
                query, raw.size(), abstractCount, relevant.size(), abstractRelevant);

        for (Map.Entry<String, Boolean> entry : relevant.entrySet()) {
            System.out.printf("ABSTRACTIVE_EXACT_H query=%s answer=%s h=%s%n",
                    query, entry.getValue().toString(), entry.getKey());
        }

        for (String source : raw) {
            if (source.indexOf('$') >= 0 || source.indexOf('?') > 0) {
                System.out.printf("ABSTRACTIVE_RAW_H query=%s exact=%s h=%s%n",
                        query,
                        relevant.containsKey(source) ? relevant.get(source).toString() : "WHO_KNOWS_OR_REJECTED",
                        source);
            }
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
