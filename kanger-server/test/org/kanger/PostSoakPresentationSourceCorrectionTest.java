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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the 3.7.0.8 post-soak small corrections. */
public class PostSoakPresentationSourceCorrectionTest {

    @Test
    public void sharedSemanticTitleDoesNotWrapWhenColumnNarrows() throws Exception {
        String css = readBrowserFile("presentation.css");
        String marker = "body.kanger-presentation .kanger-section-title,";
        int start = css.indexOf(marker);
        assertTrue(start >= 0, "Shared semantic-title rule is missing");
        int end = css.indexOf("}\n", start);
        assertTrue(end > start, "Shared semantic-title rule is incomplete");
        String rule = css.substring(start, end);
        assertTrue(rule.contains("white-space: nowrap"),
                "Shared semantic title may wrap");
        assertTrue(rule.contains("overflow: hidden"),
                "Narrow shared semantic title is not clipped");
        assertTrue(rule.contains("text-overflow: ellipsis"),
                "Narrow shared semantic title lacks ellipsis fallback");
    }

    @Test
    public void explicitGetUsesVirtualEolAndReturnsRejectedExactSourceForRepair()
            throws Exception {
        String identity = "post-soak-get-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            IMind mind = new Mind(user);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!baseline;"));
            mind.setSourceFileName("baseline.k");

            String token = UserFactory.addUser(user);
            GetSourceBoundaryReactor reactor = new GetSourceBoundaryReactor(
                    new QueryProcessor());

            String acceptedSource = "!loaded;/* closed footer */";
            Path acceptedFile = Paths.get(user.getSourceDir()).resolve("accepted-eof.k");
            Files.createDirectories(acceptedFile.getParent());
            Files.write(acceptedFile, acceptedSource.getBytes(StandardCharsets.UTF_8));

            JSONObject accepted = invoke(reactor, token, "accepted-eof.k");
            assertEquals("OK", accepted.optString("result"), accepted.toString());
            assertEquals(acceptedSource,
                    SourceDocumentState.current(user, user.getCurrentMind()),
                    "Accepted no-final-EOL document was not retained exactly");
            assertEquals("accepted-eof.k", user.getCurrentMind().getSourceFileName());

            String semanticBeforeReject = user.getCurrentMind().getSourceCode();
            String exactBeforeReject = SourceDocumentState.current(
                    user, user.getCurrentMind());
            String nameBeforeReject = user.getCurrentMind().getSourceFileName();

            String rejectedSource = "?baseline;/* repair me */";
            Path rejectedFile = Paths.get(user.getSourceDir()).resolve("rejected.k");
            Files.write(rejectedFile, rejectedSource.getBytes(StandardCharsets.UTF_8));

            JSONObject rejected = invoke(reactor, token, "rejected.k");
            assertEquals("error", rejected.optString("result"), rejected.toString());
            assertEquals("source_compile_rejected", rejected.optString("code"));
            assertEquals(semanticBeforeReject, user.getCurrentMind().getSourceCode(),
                    "Rejected get changed the live semantic context");
            assertEquals(exactBeforeReject,
                    SourceDocumentState.current(user, user.getCurrentMind()),
                    "Rejected get replaced the accepted exact document");
            assertEquals(nameBeforeReject, user.getCurrentMind().getSourceFileName(),
                    "Rejected get rebound the accepted source name");

            JSONObject recovery = rejected.optJSONObject("source_recovery");
            assertNotNull(recovery, "Rejected get did not expose repair source");
            assertEquals(1, recovery.optInt("schema"));
            assertEquals("rejected.k", recovery.optString("logical_name"));
            assertEquals(rejectedSource, recovery.optString("text"));
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private JSONObject invoke(GetSourceBoundaryReactor reactor,
                              String token,
                              String fileName) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", "command")
                .put("parameters", new JSONObject()
                        .put("token", token)
                        .put("get", fileName)));
        Object response = reactor.run(packet);
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
