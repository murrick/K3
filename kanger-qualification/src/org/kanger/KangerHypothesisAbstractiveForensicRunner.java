/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.ITerm;
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

        User user = (User) UserFactory.createUser(
                "abstractive-forensic", "abstractive-forensic");
        new UDF().init(user);
        new DB().init(user);

        inspect(user, BAD);
        inspect(user, GOOD);
        System.out.println("HYPOTHESIS_ABSTRACTIVE_FORENSIC_OK");
    }

    private static void inspect(IUser user, String query) throws Exception {
        Mind mind = prepared(user);
        enableAbstractiveHypothesis(mind);

        QueryHypothesisFormationTrace.begin();
        QueryHypothesisFormationTrace.Snapshot formation;
        Boolean known;
        try {
            known = mind.query(query, null, false);
        } finally {
            formation = QueryHypothesisFormationTrace.end();
        }
        if (known != null) {
            throw new AssertionError("Expected WHO KNOWS for " + query
                    + ", got " + known);
        }

        List<Hypothesis> raw = new ArrayList<Hypothesis>();
        for (IHypothesis hypothesis : mind.getHypothesis()) {
            raw.add((Hypothesis) hypothesis);
        }

        Map<String, List<QueryHypothesisFormationTrace.Event>> eventsByHypothesis =
                new LinkedHashMap<String, List<QueryHypothesisFormationTrace.Event>>();
        for (QueryHypothesisFormationTrace.Event event : formation.getEvents()) {
            List<QueryHypothesisFormationTrace.Event> events =
                    eventsByHypothesis.get(event.getHypothesis());
            if (events == null) {
                events = new ArrayList<QueryHypothesisFormationTrace.Event>();
                eventsByHypothesis.put(event.getHypothesis(), events);
            }
            events.add(event);
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
        int policyAllowed = 0;
        int policyRelevant = 0;
        int policyFalsePositive = 0;
        int policyFalseNegative = 0;
        int abstractAllowed = 0;
        int abstractAllowedRelevant = 0;
        int abstractNoSource = 0;

        for (Hypothesis hypothesis : raw) {
            String source = hypothesis.toString(mind);
            boolean abstractive = isAbstractive(hypothesis, mind);
            boolean exact = relevant.containsKey(source);
            List<QueryHypothesisFormationTrace.Event> events =
                    eventsByHypothesis.get(source);
            boolean sourceQueryReachable = hasQueryReachableSourceLineage(events);
            boolean allowed = !abstractive || sourceQueryReachable;

            if (abstractive) {
                ++abstractCount;
                if (exact) {
                    ++abstractRelevant;
                }
                if (events == null || events.isEmpty()) {
                    ++abstractNoSource;
                }
                if (allowed) {
                    ++abstractAllowed;
                    if (exact) {
                        ++abstractAllowedRelevant;
                    }
                }
            }

            if (allowed) {
                ++policyAllowed;
                if (exact) {
                    ++policyRelevant;
                } else {
                    ++policyFalsePositive;
                }
            } else if (exact) {
                ++policyFalseNegative;
            }

            int eventCount = events == null ? 0 : events.size();
            int maxCVars = 0;
            int maxOwnedRoots = 0;
            int maxLinkedRoots = 0;
            int maxReachableRoots = 0;
            boolean directQueryOwned = false;
            boolean directQueryLinked = false;
            if (events != null) {
                for (QueryHypothesisFormationTrace.Event event : events) {
                    maxCVars = Math.max(maxCVars, event.getCandidateCVars());
                    maxOwnedRoots = Math.max(maxOwnedRoots,
                            event.getCandidateQueryRoots());
                    maxLinkedRoots = Math.max(maxLinkedRoots,
                            event.getCandidateQueryLinkedRoots());
                    maxReachableRoots = Math.max(maxReachableRoots,
                            event.getCandidateQueryReachableRoots());
                    directQueryOwned |= event.hasCandidateAllQueryRoots();
                    directQueryLinked |= event.hasCandidateAllQueryLinkedRoots();
                }
            }

            System.out.printf("ABSTRACTIVE_SOURCE_H query=%s abstract=%s sourceEvents=%d directQueryOwned=%s directQueryLinked=%s sourceQueryReachable=%s maxSourceCVars=%d maxSourceOwnedRoots=%d maxSourceLinkedRoots=%d maxSourceReachableRoots=%d allowed=%s exact=%s answer=%s h=%s%n",
                    query,
                    Boolean.toString(abstractive),
                    eventCount,
                    Boolean.toString(directQueryOwned),
                    Boolean.toString(directQueryLinked),
                    Boolean.toString(sourceQueryReachable),
                    maxCVars,
                    maxOwnedRoots,
                    maxLinkedRoots,
                    maxReachableRoots,
                    Boolean.toString(allowed),
                    Boolean.toString(exact),
                    exact ? relevant.get(source).toString()
                            : "WHO_KNOWS_OR_REJECTED",
                    source);
        }

        System.out.printf("ABSTRACTIVE_SOURCE_SUMMARY query=%s raw=%d abstract=%d exactRelevant=%d abstractRelevant=%d policyAllowed=%d policyRelevant=%d policyFalsePositive=%d policyFalseNegative=%d abstractAllowed=%d abstractAllowedRelevant=%d abstractNoSource=%d residualEvents=%d%n",
                query,
                raw.size(),
                abstractCount,
                relevant.size(),
                abstractRelevant,
                policyAllowed,
                policyRelevant,
                policyFalsePositive,
                policyFalseNegative,
                abstractAllowed,
                abstractAllowedRelevant,
                abstractNoSource,
                formation.getEvents().size());

        for (QueryHypothesisFormationTrace.Event event : formation.getEvents()) {
            if (event.getCandidateCVars() == 0) {
                continue;
            }
            boolean rawFinal = containsText(raw, mind, event.getHypothesis());
            boolean exact = relevant.containsKey(event.getHypothesis());
            System.out.printf("ABSTRACTIVE_SOURCE_EVENT query=%s pass=%s rule=%d branch=%d sourceCVars=%d sourceOwnedRoots=%d sourceLinkedRoots=%d sourceReachableRoots=%d allSourceOwnedRoots=%s allSourceLinkedRoots=%s allSourceReachableRoots=%s rawFinal=%s exact=%s candidate=%s h=%s%n",
                    query,
                    event.getPass(),
                    event.getRuleId(),
                    event.getBranchIndex(),
                    event.getCandidateCVars(),
                    event.getCandidateQueryRoots(),
                    event.getCandidateQueryLinkedRoots(),
                    event.getCandidateQueryReachableRoots(),
                    Boolean.toString(event.hasCandidateAllQueryRoots()),
                    Boolean.toString(event.hasCandidateAllQueryLinkedRoots()),
                    Boolean.toString(event.hasCandidateAllQueryReachableRoots()),
                    Boolean.toString(rawFinal),
                    Boolean.toString(exact),
                    event.getCandidate(),
                    event.getHypothesis());
        }

        for (Map.Entry<String, Boolean> entry : relevant.entrySet()) {
            System.out.printf("ABSTRACTIVE_EXACT_H query=%s answer=%s h=%s%n",
                    query, entry.getValue().toString(), entry.getKey());
        }
    }

    private static boolean hasQueryReachableSourceLineage(
            List<QueryHypothesisFormationTrace.Event> events) {
        if (events == null) {
            return false;
        }
        for (QueryHypothesisFormationTrace.Event event : events) {
            if (event.getCandidateCVars() > 0
                    && event.hasCandidateAllQueryReachableRoots()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAbstractive(Hypothesis hypothesis, Mind mind)
            throws Exception {
        for (IArgument argument : hypothesis.getArguments()) {
            if (argument.isEmpty(mind)) {
                continue;
            }
            ITerm value = argument.getValue(mind);
            if (value != null && value.isCVariable()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(List<Hypothesis> hypotheses,
                                        Mind mind,
                                        String text) {
        for (Hypothesis hypothesis : hypotheses) {
            if (text.equals(hypothesis.toString(mind))) {
                return true;
            }
        }
        return false;
    }

    private static void enableAbstractiveHypothesis(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("includeAbstractiveHypothesis");
        field.setAccessible(true);
        field.setBoolean(mind, true);
    }

    private static Boolean exact(Mind base, String query, String source)
            throws Exception {
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
        String source = new String(
                Files.readAllBytes(Paths.get("natives.k")),
                StandardCharsets.UTF_8);
        if (!mind.compile(source)) {
            throw new AssertionError("natives.k compilation rejected");
        }
        return mind;
    }
}
