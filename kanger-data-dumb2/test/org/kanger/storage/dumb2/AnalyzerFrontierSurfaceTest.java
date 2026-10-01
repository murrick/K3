package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3.3 characterization for the Analyzer unresolved-domain frontier surface.
 */
public class AnalyzerFrontierSurfaceTest {

    @Test
    void unknownGroundOrdinaryPredicateBecomesFrontier()
            throws Exception {
        Mind mind = offlineMind();

        assertNull(mind.query("?male(Tom);", null, false));

        List<FrontierDomain> frontier =
                mind.getFrontierDomains();
        assertEquals(1, frontier.size());
        assertEquals("male",
                frontier.get(0).getPredicateName());
        assertTrue(frontier.get(0).isGround());
        assertTrue(frontier.get(0)
                .getVariables().isEmpty());
    }

    @Test
    void unknownVariablePredicatePreservesQueryVariable()
            throws Exception {
        Mind mind = offlineMind();
        Mind child = new Mind(mind);
        try {
            Rule rule = (Rule) child.compileLine(
                    "?$y age(Tom,y);",
                    true,
                    new LinkedList<ITerm>());

            boolean beforeLink = child.analyze(rule, false);
            List<FrontierDomain> before =
                    child.getFrontierDomains();

            child.link(rule, false);
            boolean usedAfterLink =
                    rule.getDomain().isUsed(child);
            boolean afterLink = child.analyze(rule, false);
            List<FrontierDomain> after =
                    child.getFrontierDomains();

            String state = "stored=" + rule.isStored()
                    + " query=" + rule.isQuery()
                    + " domainQuery="
                    + rule.getDomain().isQuery(child)
                    + " beforeResult=" + beforeLink
                    + " beforeFrontier=" + before.size()
                    + " usedAfterLink=" + usedAfterLink
                    + " afterResult=" + afterLink
                    + " afterFrontier=" + after.size();

            assertEquals(1, after.size(), state);
            FrontierDomain domain = after.get(0);
            assertEquals("age", domain.getPredicateName());
            assertFalse(domain.isGround());
            assertEquals(1, domain.getVariables().size());
            assertEquals("y",
                    domain.getVariables().get(0).getName());
            assertFalse(
                    domain.getVariables().get(0).isBound());
        } finally {
            mind.release(child);
        }
    }

    @Test
    void systemPredicateIsNotExternalized()
            throws Exception {
        Mind mind = offlineMind();

        mind.query("?$y y > 10;", null, false);

        assertTrue(mind.getFrontierDomains().isEmpty());
    }

    private Mind offlineMind() throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        return mind;
    }
}
