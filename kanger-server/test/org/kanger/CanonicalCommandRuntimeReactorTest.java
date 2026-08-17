/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.stores.SolutionsStore;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalCommandRuntimeReactorTest {

    @Test
    void canonicalHelpComesFromRegistryAndKeepsWorkspaceProjection() throws Exception {
        Fixture fixture = fixture("help");
        try {
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalChain(escaped);

            JSONObject response = invoke(reactor, fixture.token, "help");

            assertEquals("OK", response.optString("result"), response.toString());
            assertTrue(response.optString("description").contains("rule <id>"));
            assertTrue(response.optString("description").contains("values order <field>"));
            JSONObject structured = response.getJSONObject("dialogue_help");
            assertEquals(1, structured.getInt("schema"));
            JSONArray sections = structured.getJSONArray("sections");
            assertTrue(sections.length() > 0);
            boolean foundDeleteListForm = false;
            boolean foundPredicateFamilySpellings = false;
            boolean foundSquashAlias = false;
            boolean foundStorageStatusAlias = false;
            for (int i = 0; i < sections.length(); i++) {
                JSONArray commands = sections.getJSONObject(i).getJSONArray("commands");
                for (int j = 0; j < commands.length(); j++) {
                    JSONObject command = commands.getJSONObject(j);
                    String syntax = command.optString("syntax");
                    if ("delete [<source>]".equals(syntax)) {
                        foundDeleteListForm = true;
                    }
                    if ("base predicates".equals(syntax)) {
                        JSONArray spellings = command.getJSONArray(
                                "family_spellings");
                        foundPredicateFamilySpellings = spellings.length() == 2
                                && "predicate".equals(spellings.getString(0))
                                && "predicates".equals(spellings.getString(1));
                    }
                    if ("transaction squash".equals(syntax)) {
                        JSONArray aliases = command.getJSONArray("aliases");
                        foundSquashAlias = aliases.length() == 1
                                && "squash".equals(aliases.getString(0));
                    }
                    if ("storage".equals(syntax)) {
                        JSONArray aliases = command.getJSONArray("aliases");
                        foundStorageStatusAlias = aliases.length() == 1
                                && "use".equals(aliases.getString(0));
                    }
                }
            }
            assertTrue(foundDeleteListForm, response.toString());
            assertTrue(foundPredicateFamilySpellings, response.toString());
            assertTrue(foundSquashAlias, response.toString());
            assertTrue(foundStorageStatusAlias, response.toString());
            assertTrue(response.has("workspace"), response.toString());
            assertEquals("HELP", response.optString(
                    CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD));
            assertEquals(0, escaped.get(), "HELP escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    @Test
    void ruleCommentRoundTripsThroughCanonicalBinding() throws Exception {
        Fixture fixture = fixture("comment");
        try {
            Rule rule = addDirectRule(fixture.root, "comment target");
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalChain(escaped);

            JSONObject set = invoke(reactor, fixture.token,
                    "rule comment " + rule.getId() + " hello world");
            assertEquals("OK", set.optString("result"), set.toString());
            assertEquals("hello world", set.optString("comment"));

            JSONObject get = invoke(reactor, fixture.token,
                    "rule comment " + rule.getId());
            assertEquals("OK", get.optString("result"), get.toString());
            assertEquals("hello world", get.optString("comment"));
            assertEquals(0, escaped.get(), "Rule comment escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    @Test
    void solutionAddressUsesRuntimeIdRatherThanRowOrdinal() throws Exception {
        Fixture fixture = fixture("solution-id");
        try {
            Rule solution = new Rule(fixture.root);
            solution.setId(4201L);
            solution.setOrigin(fixture.root.getTerms().add("solution runtime id"));
            ((SolutionsStore) fixture.root.getSolutions()).add(solution);

            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalChain(escaped);

            JSONObject found = invoke(reactor, fixture.token, "solution 4201");
            assertEquals("OK", found.optString("result"), found.toString());
            JSONArray list = found.getJSONArray("list");
            assertEquals(1, list.length());
            assertEquals(4201L, list.getJSONObject(0).getLong("id"));

            JSONObject ordinal = invoke(reactor, fixture.token, "solution 0");
            assertEquals("error", ordinal.optString("result"), ordinal.toString());
            assertEquals("solution_not_found", ordinal.optString("code"));
            assertEquals(0, escaped.get(), "Solution lookup escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    @Test
    void whenAcceptUsesZeroBasedBoundsOfCurrentHypothesisRowset() throws Exception {
        Fixture fixture = fixture("hypothesis-index");
        try {
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalChain(escaped);

            JSONObject response = invoke(reactor, fixture.token, "when accept 0");

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("hypothesis_index_out_of_range", response.optString("code"));
            assertEquals(0, escaped.get(), "Hypothesis accept escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    @Test
    void invocationLocalValuesOrderDoesNotMutateConfiguredDefault() throws Exception {
        Fixture fixture = fixture("values-order");
        try {
            fixture.root.setOrder("defaultField");
            fixture.root.setAscending(false);
            AtomicInteger escaped = new AtomicInteger();
            IReactor<JSONObject> reactor = canonicalChain(escaped);

            JSONObject response = invoke(reactor, fixture.token,
                    "values order x desc, y");

            assertEquals("OK", response.optString("result"), response.toString());
            assertEquals("defaultField", fixture.root.getOrder());
            assertEquals(false, fixture.root.isAscending());
            assertEquals(2, response.getJSONArray("order").length());
            assertEquals("x desc", response.getJSONArray("order").getString(0));
            assertEquals("y asc", response.getJSONArray("order").getString(1));
            assertEquals(0, escaped.get(), "Values ordering escaped into legacy runtime");
        } finally {
            fixture.close();
        }
    }

    private IReactor<JSONObject> canonicalChain(final AtomicInteger escaped) {
        IReactor<JSONObject> legacy = new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                escaped.incrementAndGet();
                throw new AssertionError("Canonical-only intent escaped into legacy runtime");
            }
        };
        return new CanonicalCommandIngressReactor(
                new WorkspaceStateReactor(
                        new CanonicalCommandRuntimeReactor(legacy)));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String token,
                              String line) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("line", line)));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject, "Response is not JSON: " + response);
        return (JSONObject) response;
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "canonical-runtime-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, root, token);
    }

    private Rule addDirectRule(Mind mind, String origin) throws Exception {
        Rule rule = new Rule(mind);
        mind.getRules().register(rule);
        rule.setOrigin(mind.getTerms().add(origin));
        mind.getRules().add(rule);
        return rule;
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;
        private final String token;

        private Fixture(IUser user, Mind root, String token) {
            this.user = user;
            this.root = root;
            this.token = token;
        }

        private void close() throws Exception {
            try {
                UserFactory.logout(token);
            } catch (AuthenticationErrorException alreadyClosed) {
                // Test owns this isolated token; an already-closed session is clean.
            }
        }
    }
}
