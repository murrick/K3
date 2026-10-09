package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.kanger.CausalFrontierCapture;
import org.kanger.FrontierDemand;
import org.kanger.Mind;
import org.kanger.SemanticTermSnapshot;
import org.kanger.User;
import org.kanger.interfaces.ITerm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Native-match observations and detached positional candidates, never proof. */
public class CausalFrontierCaptureTest {

    @Test
    void freeReorderedArgumentsBecomeDetachedChildDemand() throws Exception {
        Mind root = mind("!@a @b parent(a,b) -> ancestor(b,a);");
        Mind work = Mind.ephemeralChild(root);
        FrontierDemand demand;
        try {
            CausalFrontierCapture.Result result = capture(work,
                    "?$right $left ancestor(right,left);");
            assertNull(result.getTruth());
            demand = onlyDemand(result);
            assertEquals("ancestor", demand.getParent().getPredicateName());
            assertEquals("parent", demand.getQuery().getPredicateName());
            assertFalse(demand.getQuery().isGround());
            assertEquals(2, demand.getQuery().getVariables().size());
            assertEquals(1, demand.getParentProjection().getChildPosition(0));
            assertEquals(0, demand.getParentProjection().getChildPosition(1));
            assertTrue(work.getValues().isEmpty());
        } finally {
            root.discardEphemeral(work);
        }

        // The target runtime is gone. Only semantic values and argument
        // positions remain; the child tuple is a candidate, not a root Value.
        Mind donor = mind("!padding(Noise);");
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values(donor, "John", "Mary"));
        assertEquals("Mary", candidate.get(0).materialize().toString());
        assertEquals("John", candidate.get(1).materialize().toString());
        assertTrue(root.getValues().isEmpty());
    }

    @Test
    void fixedParentArgumentProjectsIntoReorderedChildQuery() throws Exception {
        Mind root = mind("!@a @b parent(a,b) -> ancestor(b,a);");
        FrontierDemand demand = onlyDemand(capture(root,
                "?$person ancestor(Mary,person);"));
        assertEquals("parent", demand.getQuery().getPredicateName());
        assertEquals(1, demand.getQuery().getVariables().size());
        assertEquals("Mary", demand.getQuery().projectFixedArguments(root)
                .remove().toString());
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values(root, "John", "Mary"));
        assertEquals("Mary", candidate.get(0).materialize().toString());
        assertEquals("John", candidate.get(1).materialize().toString());
        assertNull(demand.getParentProjection().project(values(root, "John", "Tom")),
                "the fixed parent binding constrains supplied child evidence");
    }

    @Test
    void repeatedParentVariableConstrainsTwoChildArguments() throws Exception {
        Mind root = mind("!@a @b parent(a,b) -> ancestor(b,a);");
        FrontierDemand demand = onlyDemand(capture(root,
                "?$person ancestor(person,person);"));
        assertEquals(1, demand.getQuery().getVariables().size());
        assertNull(demand.getParentProjection().project(values(root, "John", "Mary")));
        assertNotNull(demand.getParentProjection().project(values(root, "Tom", "Tom")));
    }

    @Test
    void repeatedProductionVariableProjectsToBothParentPositions() throws Exception {
        Mind root = mind("!@person p(person) -> q(person,person);");
        FrontierDemand demand = onlyDemand(capture(root, "?$x $y q(x,y);"));
        assertEquals(0, demand.getParentProjection().getChildPosition(0));
        assertEquals(0, demand.getParentProjection().getChildPosition(1));
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values(root, "John"));
        assertEquals("John", candidate.get(0).materialize().toString());
        assertEquals("John", candidate.get(1).materialize().toString());
    }

    @Test
    void conflictingRepeatedFixedBindingProducesNoDemand() throws Exception {
        Mind root = mind("!@person p(person) -> q(person,person);");
        CausalFrontierCapture.Result result = capture(root, "?q(John,Mary);");
        assertNull(result.getTruth());
        assertTrue(result.getDemands().isEmpty());
    }

    @Test
    void groundProjectionAndAmbiguousStringKeepSemanticTypes() throws Exception {
        Mind root = mind("!@x p(x) -> q(x);");
        FrontierDemand demand = onlyDemand(capture(root, "?q('42');"));
        assertTrue(demand.getQuery().isGround());
        assertNotNull(demand.getParentProjection().project(values(root, "'42'")));
        assertNull(demand.getParentProjection().project(values(root, 42)),
                "string '42' and numeric 42 are different semantic bindings");
    }

    @Test
    void onlyDirectNativeMatchIsCapturedWithoutRecursiveRuleExpansion() throws Exception {
        Mind root = mind("!@x p(x) -> q(x);", "!@x q(x) -> r(x);",
                "!@x noise(x) -> unrelated(x);");
        FrontierDemand first = onlyDemand(capture(root, "?$value r(value);"));
        assertEquals("q", first.getQuery().getPredicateName());
        FrontierDemand second = onlyDemand(CausalFrontierCapture.query(root,
                first.getQuery().getQuerySource(),
                first.getQuery().projectFixedArguments(root), false));
        assertEquals("p", second.getQuery().getPredicateName());
        assertTrue(root.getValues().isEmpty());
    }

    @Test
    void negativePassDemandRetainsParentAndChildPolarity() throws Exception {
        Mind root = mind("!@x ~p(x) -> ~q(x);");
        FrontierDemand demand = onlyDemand(capture(root, "?q(John);"));
        assertTrue(demand.getParent().isNegated());
        assertTrue(demand.getQuery().isNegated());
        assertEquals("p", demand.getQuery().getPredicateName());
    }

    @Test
    void explicitlyNegativeFreeQueryRetainsPolarity() throws Exception {
        Mind root = mind("!@x ~p(x) -> ~q(x);");
        FrontierDemand demand = onlyDemand(capture(root, "?$value ~q(value);"));
        assertTrue(demand.getParent().isNegated());
        assertTrue(demand.getQuery().isNegated());
        assertEquals(1, demand.getQuery().getVariables().size());
    }

    @Test
    void falsePassCVariableIsNeverExportedAsConcreteEvidence() throws Exception {
        Mind root = mind("!@x ~p(x) -> ~q(x);");
        assertTrue(capture(root, "?$value q(value);").getDemands().isEmpty());
    }

    @Test
    void compoundResidualBranchStaysLocal() throws Exception {
        Mind root = mind("!@x p(x) && s(x) -> q(x);");
        assertTrue(capture(root, "?$value q(value);").getDemands().isEmpty());
    }

    @Test
    void unmappedParentArgumentRemainsUnboundCandidate() throws Exception {
        Mind root = mind("!@x @y p(x) -> q(x,y);");
        FrontierDemand demand = onlyDemand(capture(root, "?$a $b q(a,b);"));
        assertEquals(-1, demand.getParentProjection().getChildPosition(1));
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values(root, "John"));
        assertEquals("John", candidate.get(0).materialize().toString());
        assertNull(candidate.get(1));
        assertTrue(root.getValues().isEmpty());
    }

    @Test
    void failedCaptureReleasesScopeAndResultIsImmutable() throws Exception {
        Mind root = mind("!@x p(x) -> q(x);");
        assertThrows(IllegalArgumentException.class, () -> capture(root, "!q(John);"));
        CausalFrontierCapture.Result result = capture(root, "?$value q(value);");
        FrontierDemand demand = onlyDemand(result);
        assertThrows(UnsupportedOperationException.class, () -> result.getDemands().clear());
        List<SemanticTermSnapshot> candidate = demand.getParentProjection()
                .project(values(root, "John"));
        assertThrows(UnsupportedOperationException.class, () -> candidate.clear());
        assertThrows(IllegalArgumentException.class, () -> demand.getParentProjection()
                .project(java.util.Collections.<SemanticTermSnapshot>emptyList()));
    }

    @Test
    void projectedChildQueryRunsWithIndependentCanonicalTerms() throws Exception {
        Mind target = mind("!@a @b parent(a,b) -> ancestor(b,a);");
        FrontierDemand demand = onlyDemand(capture(target, "?$person ancestor(Mary,person);"));
        Mind donor = mind("!padding(Noise);", "!parent(John,Mary);", "!parent(Tom,Other);");
        assertEquals(Boolean.TRUE, donor.queryCanonical(demand.getQuery().getQuerySource(),
                demand.getQuery().projectFixedArguments(donor), false));
        assertEquals(1, donor.getValues().size());
        assertEquals("John", donor.getValues().iterator().next().values().iterator().next().toString());
        assertTrue(target.getValues().isEmpty());
    }

    @Test
    void calculatedPremiseIsNeverExternalized() throws Exception {
        Mind root = mind("!@x x > 10 -> q(x);");
        CausalFrontierCapture.Result result = capture(root, "?$value q(value);");
        assertTrue(result.getDemands().isEmpty());
    }

    @Test
    void captureDoesNotChangeLocalTruthValuesOrHypotheses() throws Exception {
        Mind root = mind("!@a @b parent(a,b) -> ancestor(b,a);");
        Boolean normal = root.queryCanonical("?$x $y ancestor(x,y);", null, false);
        int hypotheses = root.getHypothesis().size();
        long nativeAttempts = root.getLinkerStatistics().getUnificationAttempts();
        long nativeTValues = root.getLinkerStatistics().getNewTValues();
        CausalFrontierCapture.Result captured = capture(root, "?$x $y ancestor(x,y);");
        assertEquals(normal, captured.getTruth());
        assertEquals(hypotheses, root.getHypothesis().size());
        assertEquals(nativeAttempts, root.getLinkerStatistics().getUnificationAttempts());
        assertEquals(nativeTValues, root.getLinkerStatistics().getNewTValues());
        assertTrue(root.getValues().isEmpty());

        Mind work = Mind.ephemeralChild(root);
        try {
            assertEquals(Boolean.TRUE, work.query("!parent(John,Mary);", null, false));
            CausalFrontierCapture.Result proven = capture(work, "?$x $y ancestor(x,y);");
            assertEquals(Boolean.TRUE, proven.getTruth());
            assertTrue(proven.getDemands().isEmpty());
            Map<String, ITerm> row = work.getValues().iterator().next();
            assertEquals("Mary", row.get("x").toString());
            assertEquals("John", row.get("y").toString());
        } finally {
            root.discardEphemeral(work);
        }
        assertNull(root.queryCanonical("?$x $y ancestor(x,y);", null, false));
        assertEquals("ancestor", root.getFrontierDomains().get(0).getPredicateName(),
                "normal Analyzer frontier surface remains unchanged");
    }

    private CausalFrontierCapture.Result capture(Mind work, String query) throws Exception {
        return CausalFrontierCapture.query(work, query, null, false);
    }

    private FrontierDemand onlyDemand(CausalFrontierCapture.Result result) {
        assertEquals(1, result.getDemands().size());
        return result.getDemands().get(0);
    }

    private Mind mind(String... statements) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        for (String statement : statements) {
            assertTrue(mind.compile(statement), statement);
        }
        return mind;
    }

    private List<SemanticTermSnapshot> values(Mind context, Object... values) throws Exception {
        SemanticTermSnapshot[] detached = new SemanticTermSnapshot[values.length];
        for (int i = 0; i < values.length; ++i) {
            detached[i] = SemanticTermSnapshot.capture(context.getTerms().add(values[i]));
        }
        return Arrays.asList(detached);
    }
}
