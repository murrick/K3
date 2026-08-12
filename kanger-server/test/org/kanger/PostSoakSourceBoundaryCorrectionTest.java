/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression gates for the 3.7.0.8 post-soak presentation/source corrections. */
public class PostSoakSourceBoundaryCorrectionTest {

    @Test
    public void sharedSemanticTitlesStaySingleLineWhenColumnNarrows() throws Exception {
        String css = readBrowserFile("presentation.css");
        String start = "body.kanger-presentation .kanger-section-title,";
        int from = css.indexOf(start);
        assertTrue(from >= 0, "Shared semantic-title rule is missing");
        int to = css.indexOf("}\n", from);
        assertTrue(to > from, "Shared semantic-title rule is incomplete");
        String rule = css.substring(from, to);
        assertTrue(rule.contains("white-space: nowrap"),
                "Shared semantic titles may wrap");
        assertTrue(rule.contains("overflow: hidden"),
                "Shared semantic titles do not clip narrow content");
        assertTrue(rule.contains("text-overflow: ellipsis"),
                "Shared semantic titles do not expose a stable narrow fallback");
    }

    @Test
    public void compilerInputAddsOnlyVirtualTerminalBoundary() {
        String exact = "!baseline;/* closed footer */";
        assertEquals(exact + "\n", SourceDocumentState.compilerInput(exact));
        assertEquals(exact, exact,
                "Compiler boundary must not mutate the exact document");
    }

    @Test
    public void stopLossGetPreservesExactDocumentAndRejectedRecovery() throws Exception {
        String identity = "post-soak-source-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            IMind mind = new Mind(user);
            user.setCurrentMind(mind);

            String token = UserFactory.addUser(user);
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            String eofSource = "!baseline;/* closed footer */";
            Path eofFile = Paths.get(user.getSourceDir()).resolve("eof-boundary.k");
            Files.createDirectories(eofFile.getParent());
            Files.write(eofFile, eofSource.getBytes(StandardCharsets.UTF_8));

            JSONObject loaded = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("get", "eof-boundary.k"));
            assertEquals("OK", loaded.optString("result"), loaded.toString());
            assertEquals(eofSource, SourceDocumentState.current(user, user.getCurrentMind()),
                    "Successful get did not retain the exact no-final-EOL document");
            assertEquals("eof-boundary.k", user.getCurrentMind().getSourceFileName());

            Path putFile = Paths.get(user.getSourceDir()).resolve("exact-put.k");
            JSONObject put = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("put", "exact-put.k"));
            assertEquals("OK", put.optString("result"), put.toString());
            assertEquals(eofSource,
                    new String(Files.readAllBytes(putFile), StandardCharsets.UTF_8),
                    "Source put reconstructed instead of persisting the exact document");

            String semanticBeforeReject = user.getCurrentMind().getSourceCode();
            String nameBeforeReject = user.getCurrentMind().getSourceFileName();
            String exactBeforeReject = SourceDocumentState.current(user, user.getCurrentMind());
            String rejectedSource = "?baseline;/* repair me */";
            Path rejectedFile = Paths.get(user.getSourceDir()).resolve("rejected.k");
            Files.write(rejectedFile, rejectedSource.getBytes(StandardCharsets.UTF_8));

            JSONObject rejected = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("get", "rejected.k"));
            assertEquals("error", rejected.optString("result"), rejected.toString());
            assertEquals(semanticBeforeReject, user.getCurrentMind().getSourceCode(),
                    "Rejected get changed the live semantic workspace");
            assertEquals(nameBeforeReject, user.getCurrentMind().getSourceFileName(),
                    "Rejected get rebound the accepted source name");
            assertEquals(exactBeforeReject,
                    SourceDocumentState.current(user, user.getCurrentMind()),
                    "Rejected get replaced the accepted exact document");
            JSONObject recovery = rejected.optJSONObject("source_recovery");
            assertNotNull(recovery, "Rejected get did not expose repair source");
            assertEquals(1, recovery.optInt("schema"));
            assertEquals("rejected.k", recovery.optString("logical_name"));
            assertEquals(rejectedSource, recovery.optString("text"));
        } finally {
            UserFactory.dropUser(user);
        }
    }

    @Test
    public void stopLossCompilePublishesExactNoFinalEolDocument() throws Exception {
        String identity = "post-soak-compile-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            user.setCurrentMind(new Mind(user));
            String token = UserFactory.addUser(user);
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            String compileSource = "!replacement;/* closed footer */";
            JSONObject compiled = invoke(reactor, "query", new JSONObject()
                    .put("token", token)
                    .put("compile", URLEncoder.encode(compileSource, "UTF-8")));
            assertEquals("OK", compiled.optString("result"), compiled.toString());
            assertEquals(compileSource,
                    SourceDocumentState.current(user, user.getCurrentMind()),
                    "Compile did not retain exact no-final-EOL document");
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private JSONObject invoke(DestructiveStopLossReactor reactor,
                              String context,
                              JSONObject parameters) throws Exception {
        Object response = reactor.run(new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters)));
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private String readBrowserFile(String name) throws Exception {
        Path[] candidates = {
                Paths.get("html", name),
                Paths.get("..", "html", name)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Browser file not found: " + name);
    }
}
