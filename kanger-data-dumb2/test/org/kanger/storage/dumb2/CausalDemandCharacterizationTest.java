package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3.12a observations of the existing local proof machine, before changing
 * federation orchestration. Ground demand already preserves reordered fixed
 * arguments. Unbound demand does not yet expose a production premise: the
 * tests named "currently" document that missing native surface, not a weaker
 * replacement for the causal-demand contract in SMART M3 sections 12/13/19.
 * No root Value is inferred from an unproved child substitution.
 */
public class CausalDemandCharacterizationTest {

    private static final String REORDERED_RULE =
            "!@a @b parent(a,b) -> ancestor(b,a);";

    @Test
    void groundDemandReordersArgumentsAndExcludesUnrelatedProduction()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE,
                "!@c @d unrelated(c,d) -> other(d,c);");
        Mind work = Mind.ephemeralChild(root);
        try {
            Rule query = compileQuery(work, "?ancestor(Mary,John);");
            assertFalse(prove(work, query));

            FrontierDomain child = onlyFrontier(work);
            assertEquals("parent", child.getPredicateName());
            assertEquals("?parent(John,Mary);", child.getDiagnosticSource());
            assertEquals("?parent(?,?);", child.getQuerySource());
            assertTrue(child.isGround());
            assertTrue(child.getVariables().isEmpty());
            assertTrue(work.getValues().isEmpty());
        } finally {
            root.discardEphemeral(work);
        }
    }

    @Test
    void canonicalTargetLifecycleRetainsGroundChildDemand()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        try {
            assertNull(work.queryCanonical(
                    "?ancestor(Mary,John);", null, false));
            FrontierDomain child = onlyFrontier(work);
            assertEquals("parent", child.getPredicateName());
            assertEquals("?parent(John,Mary);", child.getDiagnosticSource());
            assertTrue(work.getValues().isEmpty());
        } finally {
            root.discardEphemeral(work);
        }
    }

    @Test
    void detachedGroundDemandSurvivesTargetChildDiscard()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        FrontierDomain child;
        try {
            assertNull(work.queryCanonical(
                    "?ancestor(Mary,John);", null, false));
            child = onlyFrontier(work);
        } finally {
            root.discardEphemeral(work);
        }

        // A separate local id-space answers the detached child query. Text is
        // only the carrier; fixed semantic Terms cross the boundary separately.
        Mind donor = offlineMind("!padding(Noise);", "!parent(John,Mary);");
        assertTrue(Boolean.TRUE.equals(donor.queryCanonical(
                child.getQuerySource(), child.projectFixedArguments(donor), false)));
        Queue<ITerm> arguments = child.evidenceArguments(donor,
                Collections.<String>emptyList(), Collections.<ITerm>emptyList());
        assertEquals("John", arguments.remove().toString());
        assertEquals("Mary", arguments.remove().toString());
        assertTrue(arguments.isEmpty());
    }

    @Test
    void groundChildEvidenceContinuesSameCompiledParentQuery()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        try {
            Rule query = compileQuery(work, "?ancestor(Mary,John);");
            long queryId = query.getId();
            assertFalse(prove(work, query));
            FrontierDomain child = onlyFrontier(work);

            // Use the established operation-local donor form, not independent
            // assertion qualification against the generated opposite demand.
            work.setQueryPass(QueryPass.ACCEPT);
            Rule evidence = (Rule) work.compileLine(child.getEvidenceSource(true),
                    false, child.evidenceArguments(work,
                            Collections.<String>emptyList(),
                            Collections.<ITerm>emptyList()));
            assertFalse(evidence.isSecond());
            work.setQueryPass(QueryPass.CHECKTRUE);
            work.link(null, false);
            assertTrue(work.analyze(query, false));
            assertEquals(queryId, query.getId());
        } finally {
            root.discardEphemeral(work);
        }

        assertNull(root.queryCanonical("?ancestor(Mary,John);", null, false),
                "supplied evidence must not escape the operation child");
    }

    @Test
    void unboundReorderedGoalCurrentlyExposesOnlyParentFrontier()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        try {
            assertNull(work.queryCanonical(
                    "?$right $left ancestor(right,left);", null, false));
            FrontierDomain frontier = onlyFrontier(work);
            assertEquals("ancestor", frontier.getPredicateName(),
                    "characterization gap: native proof has not exposed parent(a,b)");
            assertEquals("?$right $left ancestor(right,left);",
                    frontier.getQuerySource());
            assertFalse(frontier.isGround());
            assertTrue(work.getValues().isEmpty());
        } finally {
            root.discardEphemeral(work);
        }
    }

    @Test
    void partiallyBoundGoalCurrentlyDoesNotExposeUnboundChildPremise()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        try {
            assertNull(work.queryCanonical(
                    "?$person ancestor(Mary,person);", null, false));
            FrontierDomain frontier = onlyFrontier(work);
            assertEquals("ancestor", frontier.getPredicateName(),
                    "a fixed second production argument does not expose its free first argument");
            assertEquals("?$person ancestor(?,person);", frontier.getQuerySource());
            assertTrue(work.getValues().isEmpty());
        } finally {
            root.discardEphemeral(work);
        }
    }

    @Test
    void nativeProofReturnsIndependentReorderedValuesAfterSuppliedFacts()
            throws Exception {
        Mind root = offlineMind(REORDERED_RULE);
        Mind work = Mind.ephemeralChild(root);
        try {
            assertNull(work.queryCanonical(
                    "?$right $left ancestor(right,left);", null, false));
            assertTrue(work.getValues().isEmpty());

            assertTrue(Boolean.TRUE.equals(work.query(
                    "!parent(John,Mary);", null, false)));
            assertTrue(Boolean.TRUE.equals(work.query(
                    "!parent(Mary,Tom);", null, false)));
            assertTrue(Boolean.TRUE.equals(work.queryCanonical(
                    "?$right $left ancestor(right,left);", null, false)));
            assertEquals(new LinkedHashSet<String>(Arrays.asList(
                    "Mary/John", "Tom/Mary")), rows(work, "right", "left"));
        } finally {
            root.discardEphemeral(work);
        }

        assertNull(root.queryCanonical(
                "?$right $left ancestor(right,left);", null, false));
        assertTrue(root.getValues().isEmpty());
    }

    @Test
    void johnMaryChainHasForwardProofButCurrentlyNoUnboundChildDemand()
            throws Exception {
        Mind root = offlineMind("!@x p(x) -> q(x);", "!@x q(x) -> r(x);");
        Mind work = Mind.ephemeralChild(root);
        try {
            assertNull(work.queryCanonical("?$result r(result);", null, false));
            assertEquals("r", onlyFrontier(work).getPredicateName(),
                    "M3.12 must still add native q/p demand exposure for the split chain");
            assertTrue(work.getValues().isEmpty());

            assertTrue(Boolean.TRUE.equals(work.query("!p(John);", null, false)));
            assertTrue(Boolean.TRUE.equals(work.query("!p(Mary);", null, false)));
            assertTrue(Boolean.TRUE.equals(work.queryCanonical(
                    "?$result r(result);", null, false)));
            assertEquals(new LinkedHashSet<String>(Arrays.asList("John", "Mary")),
                    rows(work, "result"));
        } finally {
            root.discardEphemeral(work);
        }
    }

    private Mind offlineMind(String... statements) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        for (String statement : statements) {
            assertTrue(mind.compile(statement), "fixture rejected: " + statement);
        }
        return mind;
    }

    private Rule compileQuery(Mind work, String source) throws Exception {
        work.setQueryPass(QueryPass.CHECKTRUE);
        return (Rule) work.compileLine(source, true, new LinkedList<ITerm>());
    }

    private boolean prove(Mind work, Rule query) throws Exception {
        work.analyze(query, false);
        work.link(query, false);
        return work.analyze(query, false);
    }

    private FrontierDomain onlyFrontier(Mind work) {
        List<FrontierDomain> frontier = work.getFrontierDomains();
        assertEquals(1, frontier.size());
        return frontier.get(0);
    }

    private Set<String> rows(Mind mind, String... variables) {
        Set<String> rows = new LinkedHashSet<String>();
        for (Map<String, ITerm> row : mind.getValues()) {
            StringBuilder rendered = new StringBuilder();
            for (String variable : variables) {
                if (rendered.length() > 0) {
                    rendered.append('/');
                }
                rendered.append(row.get(variable));
            }
            assertTrue(rows.add(rendered.toString()), "duplicate tuple");
        }
        return rows;
    }
}
