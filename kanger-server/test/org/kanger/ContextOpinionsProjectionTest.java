package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandIntent;
import org.kanger.interfaces.internal.IContextFederation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextOpinionsProjectionTest {
    @Test void sourceAttributionLocalHypothesesAndSavedViewSelectionSurviveTransport() {
        Revision source = new Revision(UUID.randomUUID(), 7);
        ProvisionalHypothesis hypothesis = new ProvisionalHypothesis(source, "!p(John);");
        QueryResult unknown = new QueryResult(false, FrontierTruth.UNKNOWN, 0, 0,
                Collections.emptyList(), Collections.emptyList(), Collections.singletonList(hypothesis));
        Opinion b = new Opinion("B", source, false, unknown, Collections.emptyList());
        QueryResult proved = new QueryResult(true, FrontierTruth.FALSE, 0, 0,
                Collections.emptyList(), Collections.singletonList(new ValueRow(Collections.singletonMap("x", "Mary"))),
                Collections.emptyList());
        RuleRow proof = new RuleRow(12, "!~p(Mary);", false, "", Collections.emptyList(), Collections.singletonList(new ProofCause(3, "!@x q(x) -> ~p(x);", 4L, "!q(Mary);", false, Collections.emptyList())));
        Opinion a = new Opinion("A", source, false, proved, Collections.singletonList(proof));
        Map<String,Opinion> opinions = new LinkedHashMap<>(); opinions.put("A", a); opinions.put("B", b);
        JSONObject all = CanonicalCommandRuntimeReactor.contextOpinions(opinions, CommandIntent.CTX_OPINIONS);
        assertEquals("FALSE", all.getJSONObject("A").getString("truth"));
        assertEquals("!~p(Mary);", all.getJSONObject("A").getJSONArray("solutions").getJSONObject(0).getString("statement"));
        assertEquals("!p(John);", all.getJSONObject("B").getJSONArray("hypotheses").getString(0));
        assertEquals(source.getContextId().toString(), all.getJSONObject("B").getString("context_id"));
        assertEquals(7, all.getJSONObject("B").getLong("revision")); assertTrue(all.getJSONObject("B").getBoolean("isolated"));
        JSONObject values = CanonicalCommandRuntimeReactor.contextOpinions(opinions, CommandIntent.CTX_VALUES).getJSONObject("A");
        assertEquals("Mary", values.getJSONArray("values").getJSONObject(0).getString("x"));
        assertFalse(values.has("hypotheses")); assertFalse(values.has("solutions"));
        JSONObject solves = CanonicalCommandRuntimeReactor.contextOpinions(opinions, CommandIntent.CTX_SOLVES).getJSONObject("A");
        assertEquals("!q(Mary);", solves.getJSONArray("solutions").getJSONObject(0).getJSONArray("causes").getJSONObject(0).getString("donor"));
        assertFalse(solves.has("values"));
        JSONObject when = CanonicalCommandRuntimeReactor.contextOpinions(opinions, CommandIntent.CTX_WHEN).getJSONObject("B");
        assertEquals(1, when.getJSONArray("hypotheses").length()); assertFalse(when.has("solutions"));
    }
}
