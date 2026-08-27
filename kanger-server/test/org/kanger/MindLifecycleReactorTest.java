/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.ParseErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the 3.5.2.3 server Mind lifecycle boundary. */
class MindLifecycleReactorTest {

    @Test
    void requestFailureReleasesHiddenChildReservationAndEscapes() throws Exception {
        Fixture fixture = fixture("hidden-failure");
        try {
            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate(),
                    new MindLifecycleReactor.ChildFactory() {
                        @Override
                        public Mind create(IMind parent) throws Exception {
                            return new FailingQueryMind(parent);
                        }
                    });

            assertThrows(InjectedQueryFailure.class,
                    () -> invoke(reactor, "query", new JSONObject()
                            .put("token", fixture.token)
                            .put("request", encode("?hidden_failure;"))));

            assertSame(fixture.root, fixture.user.getCurrentMind(),
                    "Failed request displaced the authoritative root");
            assertEquals(0, counter(fixture.root),
                    "Failed request leaked an unreachable child reservation");
        } finally {
            fixture.close();
        }
    }

    @Test
    void requestErrorAlsoReleasesHiddenChildReservation() throws Exception {
        Fixture fixture = fixture("hidden-error");
        try {
            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate(),
                    new MindLifecycleReactor.ChildFactory() {
                        @Override
                        public Mind create(IMind parent) throws Exception {
                            return new FailingQueryErrorMind(parent);
                        }
                    });

            assertThrows(InjectedQueryError.class,
                    () -> invoke(reactor, "query", new JSONObject()
                            .put("token", fixture.token)
                            .put("request", encode("?hidden_error;"))));
            assertSame(fixture.root, fixture.user.getCurrentMind(),
                    "Error path displaced the authoritative root");
            assertEquals(0, counter(fixture.root),
                    "Error path leaked an unreachable child reservation");
        } finally {
            fixture.close();
        }
    }

    @Test
    void parseFailureReachesCanonicalBoundaryWithSourceSpan() throws Exception {
        Fixture fixture = fixture("parse-propagation");
        try {
            MindLifecycleReactor lifecycle = new MindLifecycleReactor(
                    rejectingDelegate(),
                    new MindLifecycleReactor.ChildFactory() {
                        @Override
                        public Mind create(IMind parent) throws Exception {
                            return new FailingParseQueryMind(parent);
                        }
                    });
            IReactor<JSONObject> reactor = new CanonicalErrorBoundaryReactor(lifecycle);

            JSONObject response = invoke(reactor, "query", new JSONObject()
                    .put("token", fixture.token)
                    .put("request", encode("?parse_failure;")));

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals("parse_error", response.optString("code"));
            assertFalse(response.has("source"),
                    "Source location must remain inside the canonical error envelope");
            JSONObject diagnostic = response.getJSONObject("error");
            JSONObject source = diagnostic.getJSONObject("source");
            assertEquals(7, source.getInt("offset"));
            assertEquals(2, source.getInt("length"));
            assertSame(fixture.root, fixture.user.getCurrentMind(),
                    "Parse failure displaced the authoritative root");
            assertEquals(0, counter(fixture.root),
                    "Parse failure leaked an unreachable child reservation");
        } finally {
            fixture.close();
        }
    }

    @Test
    void successfulRequestAlsoBalancesHiddenChild() throws Exception {
        Fixture fixture = fixture("hidden-success");
        try {
            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate());

            JSONObject response = invoke(reactor, "query", new JSONObject()
                    .put("token", fixture.token)
                    .put("request", encode("?unknown_lifecycle_fact;")));

            assertEquals("OK", response.optString("result"), response.toString());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals(0, counter(fixture.root),
                    "Successful request leaked its request-local child");
            assertEquals(0, response.optInt("transaction", -1));
        } finally {
            fixture.close();
        }
    }

    @Test
    void explicitNestedTransactionsPublishOnlyLiveMind() throws Exception {
        Fixture fixture = fixture("explicit-chain");
        try {
            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate());

            JSONObject firstCreate = transaction(reactor, fixture.token, "create");
            assertEquals("OK", firstCreate.optString("result"));
            Mind child = (Mind) fixture.user.getCurrentMind();
            assertSame(fixture.root, child.getNext());
            assertEquals(1, counter(fixture.root));

            JSONObject secondCreate = transaction(reactor, fixture.token, "create");
            assertEquals("OK", secondCreate.optString("result"));
            Mind grandchild = (Mind) fixture.user.getCurrentMind();
            assertSame(child, grandchild.getNext());
            assertEquals(1, counter(child));

            JSONObject rollback = transaction(reactor, fixture.token, "rollback");
            assertEquals("OK", rollback.optString("result"), rollback.toString());
            assertSame(child, fixture.user.getCurrentMind());
            assertEquals(0, counter(child));
            assertEquals(1, counter(fixture.root));

            JSONObject commit = transaction(reactor, fixture.token, "commit");
            assertEquals("OK", commit.optString("result"), commit.toString());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals(0, counter(fixture.root));
            assertEquals(0, commit.optInt("transaction", -1));
        } finally {
            fixture.close();
        }
    }

    @Test
    void failedCommitCannotLeaveSlotOnFinishedChildAndEscapes() throws Exception {
        Fixture fixture = fixture("commit-failure-slot");
        try {
            Mind child = new Mind(fixture.root);
            addDirectRule(child, "mind_lifecycle_commit_failure");

            Mind sibling = new Mind(fixture.root);
            addDirectRule(sibling, "mind_lifecycle_commit_sibling");
            assertTrue(fixture.root.commit(sibling),
                    "Fixture sibling commit unexpectedly failed");
            assertEquals(1, counter(fixture.root),
                    "Sibling commit did not preserve the original child reservation");

            fixture.user.setCurrentMind(child);
            replaceAnalyzer(fixture.root, new FailingAnalyzer(fixture.root));

            MindLifecycleReactor reactor = new MindLifecycleReactor(
                    rejectingDelegate());
            assertThrows(InjectedCommitFailure.class,
                    () -> transaction(reactor, fixture.token, "commit"));

            assertSame(fixture.root, fixture.user.getCurrentMind(),
                    "Failed commit left currentMind on a finished child");
            assertEquals(0, counter(fixture.root),
                    "Failed commit leaked its parent reservation");
        } finally {
            fixture.close();
        }
    }

    @Test
    void logoutRollsBackPublishedChainBeforeStorageClose() throws Exception {
        Fixture fixture = fixture("logout-chain");
        Mind child = new Mind(fixture.root);
        Mind grandchild = new Mind(child);
        fixture.user.setCurrentMind(grandchild);

        MindLifecycleReactor reactor = new MindLifecycleReactor(
                rejectingDelegate());
        JSONObject response = invoke(reactor, "command", new JSONObject()
                .put("token", fixture.token)
                .put("quit", ""));

        assertEquals("OK", response.optString("result"), response.toString());
        assertEquals(0, counter(child),
                "Logout leaked the grandchild reservation");
        assertEquals(0, counter(fixture.root),
                "Logout leaked the child reservation");
        assertNull(fixture.user.getCurrentMind());
        assertThrows(AuthenticationErrorException.class,
                () -> UserFactory.getUser(fixture.token));
    }

    @Test
    void unrelatedRequestsRemainOnLegacyDelegate() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        JSONObject expected = new JSONObject().put("result", "delegate");
        MindLifecycleReactor reactor = new MindLifecycleReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        calls.incrementAndGet();
                        return expected;
                    }
                });

        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "version")
                .put("parameters", new JSONObject()));
        Object response = reactor.run(packet);

        assertSame(expected, response);
        assertEquals(1, calls.get());
    }

    private JSONObject transaction(MindLifecycleReactor reactor,
                                   String token,
                                   String action) throws Exception {
        return invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("transaction", action));
    }

    private JSONObject invoke(IReactor<JSONObject> reactor,
                              String context,
                              JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "Lifecycle response is not JSON: " + response);
        return (JSONObject) response;
    }

    private IReactor<JSONObject> rejectingDelegate() {
        return new IReactor<JSONObject>() {
            @Override
            public Object run(JSONObject packet) {
                throw new AssertionError("Lifecycle request escaped to legacy delegate");
            }
        };
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "mind-lifecycle-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        String token = UserFactory.addUser(user);
        return new Fixture(user, root, token);
    }

    private String encode(String source) throws Exception {
        return URLEncoder.encode(source, "UTF-8");
    }

    private int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private void addDirectRule(Mind mind, String origin) throws Exception {
        Rule rule = new Rule(mind);
        mind.getRules().register(rule);
        rule.setOrigin(mind.getTerms().add(origin));
        assertSame(rule, mind.getRules().add(rule));
    }

    private void replaceAnalyzer(Mind mind, Analyzer analyzer) throws Exception {
        Field field = Mind.class.getDeclaredField("analyzer");
        field.setAccessible(true);
        field.set(mind, analyzer);
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
                // Logout qualification owns this token in its test.
            }
        }
    }

    private static final class FailingQueryMind extends Mind {
        private FailingQueryMind(IMind parent) throws Exception {
            super(parent);
        }

        @Override
        public Boolean query(String query) {
            throw new InjectedQueryFailure();
        }
    }

    private static final class FailingQueryErrorMind extends Mind {
        private FailingQueryErrorMind(IMind parent) throws Exception {
            super(parent);
        }

        @Override
        public Boolean query(String query) {
            throw new InjectedQueryError();
        }
    }

    private static final class FailingParseQueryMind extends Mind {
        private FailingParseQueryMind(IMind parent) throws Exception {
            super(parent);
        }

        @Override
        public Boolean query(String query) throws Exception {
            throw new ParseErrorException(7, 2, "Unexpected term");
        }
    }

    private static final class FailingAnalyzer extends Analyzer {
        private FailingAnalyzer(Mind mind) {
            super(mind);
        }

        @Override
        public boolean checkDatabase(Set<Long> list, boolean logging) {
            throw new InjectedCommitFailure();
        }
    }

    private static final class InjectedQueryFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class InjectedQueryError extends AssertionError {
        private static final long serialVersionUID = 1L;
    }

    private static final class InjectedCommitFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
