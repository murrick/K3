package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.internal.IContextFederation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Browser explain keeps causal candidates separate from final root Values. */
class CausalExplainProjectionTest {
    @Test
    void browserProjectionPreservesSemanticEdgesPinsAndEvidenceWithoutRuntimeInternals() throws Exception {
        UUID x = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        IContextFederation.Snapshot snapshot = new IContextFederation.Snapshot("X", x, 3,
                Arrays.asList(connection("A", a, 7), connection("C", c, 11)));
        IContextFederation.Revision donor = new IContextFederation.Revision(c, 11);
        IContextFederation.CausalDemand demand = new IContextFederation.CausalDemand(
                "?$right $left ancestor(right,left);", "?$a $b parent(a,b);", Arrays.asList(1,0));
        IContextFederation.CausalStep step = new IContextFederation.CausalStep(3,
                "?$right $left ancestor(right,left);", "?$right $left ancestor(right,left);",
                new IContextFederation.Revision(a,7), IContextFederation.FrontierTruth.TRUE,
                Collections.singletonList(new IContextFederation.ValueRow(Collections.singletonMap("right", "Mary"))),
                Collections.singletonList(new IContextFederation.EvidenceInjection("!parent('John','Mary');",
                        Collections.<String,String>emptyMap(), Collections.singletonList(donor))),
                Collections.singletonList(demand));
        IContextFederation.QueryResult query = new IContextFederation.QueryResult(true,
                IContextFederation.FrontierTruth.TRUE,1,1, Collections.<IContextFederation.FrontierObservation>emptyList(),
                Collections.<IContextFederation.ValueRow>emptyList(), Collections.<IContextFederation.EvidenceInjection>emptyList(),
                Collections.<IContextFederation.ProvisionalHypothesis>emptyList(), Collections.singletonList(step));
        IContextFederation.ExplainResult explain = new IContextFederation.ExplainResult(snapshot,
                IContextFederation.FrontierTruth.UNKNOWN,IContextFederation.FrontierTruth.TRUE,
                Collections.singletonList(new IContextFederation.ExplainPass(IContextFederation.ExplainPolarity.TRUE_PASS,query)),
                Collections.singletonList(new IContextFederation.ValueRow(Collections.singletonMap("answer", "John"))),
                Collections.<String>emptyList());
        CanonicalCommandRuntimeReactor reactor = new CanonicalCommandRuntimeReactor(value -> null);
        Method method = CanonicalCommandRuntimeReactor.class.getDeclaredMethod("contextExplain", IContextFederation.ExplainResult.class);
        method.setAccessible(true);
        JSONObject json = (JSONObject) method.invoke(reactor, explain);
        JSONObject projected = json.getJSONArray("passes").getJSONObject(0).getJSONArray("causal_steps").getJSONObject(0);
        assertEquals("A", projected.getJSONObject("target").getString("locator"));
        assertEquals(7, projected.getJSONObject("target").getLong("revision"));
        assertEquals("Mary", projected.getJSONArray("values").getJSONObject(0).getString("right"));
        assertEquals("John", json.getJSONArray("values").getJSONObject(0).getString("answer"));
        JSONArray mapping = projected.getJSONArray("demands").getJSONObject(0).getJSONArray("parent_to_child");
        assertEquals(1, mapping.getInt(0)); assertEquals(0, mapping.getInt(1));
        JSONObject evidence = projected.getJSONArray("supplied_evidence").getJSONObject(0);
        assertEquals("!parent('John','Mary');", evidence.getString("statement"));
        assertEquals("C", evidence.getJSONArray("supports").getJSONObject(0).getString("locator"));
        assertEquals(11, evidence.getJSONArray("supports").getJSONObject(0).getLong("revision"));
        String text = json.toString();
        for (String internal : Arrays.asList("term_id", "invocation_id", "fingerprint", "cache", "factory", "bucket")) assertFalse(text.contains(internal));
    }

    private IContextFederation.Connection connection(String locator, UUID id, long revision) {
        return new IContextFederation.Connection(locator,id,revision,revision,IContextFederation.PinPolicy.EXACT_REVISION,IContextFederation.CompatibilityStatus.QUALIFIED,"3.8.0");
    }
}
