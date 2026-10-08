package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandIntent;
import org.kanger.interfaces.IContextResults.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextOpinionsProjectionTest {
    @Test void jointOpinionIdentifiesTheCommuneAndAllPinnedMembers() {
        Revision facts = new Revision(UUID.randomUUID(), 7);
        Revision rules = new Revision(UUID.randomUUID(), 2);
        Revision commune = new Revision(facts.getContextId(), 7, "own", Arrays.asList(facts, rules));
        QueryResult result = new QueryResult(true, FrontierTruth.FALSE, 0, 0,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        Opinion opinion = new Opinion("trust own", commune, false, result, Collections.emptyList());
        JSONObject view = CanonicalCommandRuntimeReactor.contextOpinions(Collections.singletonMap("trust own", opinion),
                CommandIntent.CTX_OPINIONS).getJSONObject("trust own");
        assertEquals("own", view.getString("commune"));
        assertEquals("FALSE", view.getString("truth"));
        assertEquals(2, view.getJSONArray("commune_members").length());
        assertEquals(facts.getContextId().toString(), view.getJSONArray("commune_members").getJSONObject(0).getString("context_id"));
        assertEquals(7, view.getJSONArray("commune_members").getJSONObject(0).getLong("revision"));
        assertEquals(rules.getContextId().toString(), view.getJSONArray("commune_members").getJSONObject(1).getString("context_id"));
        assertEquals(0, view.getJSONArray("hypotheses").length());
    }

    @Test void detachedProofPreservesNestedContextOwnership() {
        Revision source = new Revision(UUID.randomUUID(), 1);
        Revision donorSource = new Revision(UUID.randomUUID(), 2);
        ProofCause donor = new ProofCause(3, "!q(John);", null, "", false,
                Collections.emptyList(), false, null, null, donorSource, true);
        ProofCause proof = new ProofCause(8, "!r(John);", null, "", false,
                Collections.singletonList(donor), false, null, null, source);
        RuleRow solution = new RuleRow(12, "!r(John);", true, "", Collections.emptyList(), Collections.singletonList(proof));
        QueryResult resolved = new QueryResult(true, FrontierTruth.TRUE, 0, 0, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        Opinion opinion = new Opinion("B", source, false, resolved, Collections.singletonList(solution), true);
        JSONObject view = CanonicalCommandRuntimeReactor.contextOpinions(Collections.singletonMap("B", opinion),
                CommandIntent.CTX_SOLVES).getJSONObject("B");
        assertTrue(view.getBoolean("configured_by_x"));
        JSONObject root = view.getJSONArray("solutions").getJSONObject(0).getJSONArray("causes").getJSONObject(0);
        assertEquals(source.getContextId().toString(), root.getString("context_id"));
        assertEquals(1, root.getLong("context_revision"));
        JSONObject nested = root.getJSONArray("causes").getJSONObject(0);
        assertEquals(donorSource.getContextId().toString(), nested.getString("context_id"));
        assertEquals(2, nested.getLong("context_revision"));
        assertTrue(nested.getBoolean("configured_by_x"));
        assertEquals("!q(John);", nested.getString("rule"));
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
