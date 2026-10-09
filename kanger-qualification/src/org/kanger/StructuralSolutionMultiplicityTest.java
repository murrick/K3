package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IRule;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Different primary rule structures can prove the same semantic ground row. */
class StructuralSolutionMultiplicityTest {
    @Test void literalAndBoundParameterProofsShareOneSemanticAnswer() throws Exception {
        User user = new User();
        new org.kanger.storage.DB().init(user);
        Mind root = new Mind(user); user.setCurrentMind(root);
        Mind bound = new Mind(root);
        Mind literal = new Mind(root);
        assertTrue(bound.query("!value(1,?,?);", new Object[] { 3, 1003 }, false));
        assertTrue(literal.query("!value(1,3,1003);", null, false));
        assertTrue(root.commit(bound)); assertTrue(root.commit(literal));
        assertTrue(root.query("?$x $y value(1,x,y);", null, false));
        assertFalse(root.getSolutions().isEmpty(), "The ground fact retains proof support");
        assertEquals(1, root.getValues().size(), "Values contains one semantic row");
        Set<List<String>> proven = new HashSet<>();
        for (IRule solution : root.getSolutions())
            proven.add(Arrays.asList(solution.getArguments().get(1).getValue(root).toString(),
                    solution.getArguments().get(2).getValue(root).toString()));
        assertEquals(1, proven.size());
        assertTrue(proven.contains(Arrays.asList("3.0", "1003.0")));
    }
}
