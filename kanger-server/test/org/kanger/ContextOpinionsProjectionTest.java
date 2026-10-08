package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandIntent;
import org.kanger.interfaces.internal.IContextFederation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextOpinionsProjectionTest {
    @Test void hypotheticalDonorOriginSurvivesTransport() {
        Revision source = new Revision(UUID.randomUUID(), 1);
        ProofCause cause = new ProofCause(0, "!@x q(x) -> r(x);", null, "!q(John);", false,
                Collections.emptyList(), true, source, "?r(John);");
        RuleRow solution = new RuleRow(8, "!r(John);", true, "", Collections.emptyList(), Collections.singletonList(cause));
        QueryResult unknown = new QueryResult(false, FrontierTruth.UNKNOWN, 0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.singletonList(new ProvisionalHypothesis(source, "!q(John);")));
        Opinion opinion = new Opinion("B", source, false, unknown, Collections.singletonList(solution), true);
        JSONObject view = CanonicalCommandRuntimeReactor.contextOpinions(Collections.singletonMap("B", opinion),
                CommandIntent.CTX_SOLVES).getJSONObject("B");
        assertTrue(view.getBoolean("configured_by_x"));
        JSONObject donor = view.getJSONArray("solutions").getJSONObject(0).getJSONArray("causes").getJSONObject(0);
        assertTrue(donor.getBoolean("hypothesis"));
        assertEquals(source.getContextId().toString(), donor.getString("hypothesis_context_id"));
        assertEquals(1, donor.getLong("hypothesis_revision"));
        assertEquals("?r(John);", donor.getString("required_for"));
    }
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
