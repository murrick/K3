/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.ClientVocabularyCorpus;
import org.kanger.command.CommandFormatter;
import org.kanger.command.CommandInvocation;
import org.kanger.interfaces.IReactor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the shared client vocabulary through the real Browser ingress. */
class CanonicalClientVocabularyEquivalenceTest {

    @Test
    void browserIngressMatchesSharedConsoleBrowserVocabularyCorpus()
            throws Exception {
        CommandFormatter formatter = new CommandFormatter();
        for (ClientVocabularyCorpus.Case one : ClientVocabularyCorpus.load()) {
            Capture capture = new Capture();
            CanonicalCommandIngressReactor reactor =
                    new CanonicalCommandIngressReactor(capture);
            JSONObject packet = dialogue(one.getLine());

            Object raw = reactor.run(packet);
            assertTrue(raw instanceof JSONObject, one.toString());
            JSONObject response = (JSONObject) raw;
            assertEquals(one.getBrowserResult(), response.optString("result"),
                    one + " response " + response);

            if (!one.isAccepted()) {
                assertEquals("command_parse_error", response.optString("code"),
                        one + " response " + response);
                assertEquals(one.getResult(), response.optString("reason"),
                        one + " response " + response);
                assertEquals(0, capture.calls.get(),
                        one + " escaped into Browser runtime");
                continue;
            }

            assertEquals(one.getResult(), response.optString(
                            CanonicalCommandIngressReactor.CANONICAL_INTENT_FIELD),
                    one + " response " + response);
            CommandInvocation invocation =
                    CanonicalCommandIngressReactor.invocation(packet);
            assertNotNull(invocation, one + " invocation marker");
            assertEquals(one.getResult(), invocation.getIntent().name(),
                    one + " invocation intent");
            assertEquals(one.getCanonical(), formatter.format(invocation),
                    one + " canonical echo");
            if (one.getArgumentName() != null) {
                assertEquals(one.getArgumentValue(), String.valueOf(
                                invocation.getArgument(one.getArgumentName())),
                        one + " argument " + one.getArgumentName());
            }
            assertEquals("confirmation_required".equals(one.getBrowserResult())
                            ? 0 : 1,
                    capture.calls.get(), one + " runtime call count");
        }
    }

    private static JSONObject dialogue(String line) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", "dialogue")
                .put("parameters", new JSONObject()
                        .put("token", "client-vocabulary-token")
                        .put("line", line)));
    }

    private static final class Capture implements IReactor<JSONObject> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Object run(JSONObject packet) {
            calls.incrementAndGet();
            return new JSONObject()
                    .put("result", "OK")
                    .put("description", "accepted");
        }
    }
}
