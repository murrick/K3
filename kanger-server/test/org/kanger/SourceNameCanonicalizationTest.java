/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.udf.UDF;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes the canonical .k source-name transport contract. */
class SourceNameCanonicalizationTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void canonicalCommandsAddressOneLowercaseKSuffixedSource() throws Exception {
        assertEquals("foo.k", source("get foo"));
        assertEquals("foo.k", source("get foo.k"));
        assertEquals("foo.k", source("get foo.K"));
        assertEquals("foo.txt.k", source("put foo.txt"));
        assertEquals("foo.k", source("delete foo"));
    }

    @Test
    void rawSourceTransportCanonicalizesBeforeRuntime() throws Exception {
        final AtomicReference<JSONObject> seen = new AtomicReference<JSONObject>();
        SourceTransportBoundaryReactor reactor = new SourceTransportBoundaryReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        seen.set(new JSONObject(packet.toString()));
                        return new JSONObject().put("result", "OK");
                    }
                });

        JSONObject response = (JSONObject) reactor.run(packet("command",
                new JSONObject().put("put", "foo")));

        assertEquals("OK", response.getString("result"));
        assertEquals("foo.k", parameters(seen.get()).getString("put"));
    }

    @Test
    void physicalGetPutDeleteUseTheSameCanonicalFile() throws Exception {
        String identity = "source-k-suffix-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        String token = null;
        String logical = "roundtrip-" + UUID.randomUUID().toString();
        Path canonical = Paths.get(user.getSourceDir()).resolve(logical + ".k");
        Path unsuffixed = Paths.get(user.getSourceDir()).resolve(logical);
        try {
            new UDF().init(user);
            user.setCurrentMind(new Mind(user));
            token = UserFactory.addUser(user);

            SourceTransportBoundaryReactor reactor = new SourceTransportBoundaryReactor(
                    new DestructiveStopLossReactor(new IReactor<JSONObject>() {
                        @Override
                        public Object run(JSONObject packet) {
                            throw new AssertionError("Source operation escaped stop-loss boundary");
                        }
                    }));

            JSONObject put = (JSONObject) reactor.run(packet("command",
                    new JSONObject().put("token", token).put("put", logical)));
            assertEquals("OK", put.getString("result"), put.toString());
            assertTrue(Files.isRegularFile(canonical));
            assertFalse(Files.exists(unsuffixed));

            JSONObject get = (JSONObject) reactor.run(packet("command",
                    new JSONObject().put("token", token).put("get", logical)));
            assertEquals("source_empty", get.getString("code"), get.toString());

            JSONObject delete = (JSONObject) reactor.run(packet("command",
                    new JSONObject().put("token", token).put("delete", logical)));
            assertEquals("OK", delete.getString("result"), delete.toString());
            assertFalse(Files.exists(canonical));
        } finally {
            Files.deleteIfExists(unsuffixed);
            Files.deleteIfExists(canonical);
            if (token != null) {
                UserFactory.dropUser(user);
            }
        }
    }

    @Test
    void sourceDiscoveryPublishesOnlyCanonicalKFiles() throws Exception {
        SourceTransportBoundaryReactor reactor = new SourceTransportBoundaryReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        return new JSONObject()
                                .put("result", "OK")
                                .put("size", 4)
                                .put("list", new JSONArray()
                                        .put("one.k")
                                        .put("two.k.bak")
                                        .put("three.K")
                                        .put("four"));
                    }
                });

        JSONObject response = (JSONObject) reactor.run(packet("command",
                new JSONObject().put("get", "")));

        assertEquals(1, response.getInt("size"));
        assertEquals(1, response.getJSONArray("list").length());
        assertEquals("one.k", response.getJSONArray("list").getString(0));
    }

    @Test
    void unsafeRawSourceIsRejectedBeforeRuntime() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        SourceTransportBoundaryReactor reactor = new SourceTransportBoundaryReactor(
                new IReactor<JSONObject>() {
                    @Override
                    public Object run(JSONObject packet) {
                        calls.incrementAndGet();
                        return new JSONObject().put("result", "OK");
                    }
                });

        JSONObject response = (JSONObject) reactor.run(packet("command",
                new JSONObject().put("get", "../secret")));

        assertEquals("error", response.getString("result"));
        assertEquals("source_name_invalid", response.getString("code"));
        assertEquals(0, calls.get());
        assertTrue(response.getString("description")
                .contains("Invalid filesystem identifier"));
    }

    private String source(String line) throws Exception {
        CommandInvocation invocation = parser.parse(line);
        return String.valueOf(invocation.getArgument("source"));
    }

    private JSONObject packet(String context, JSONObject parameters) {
        return new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
    }

    private JSONObject parameters(JSONObject packet) {
        return packet.getJSONObject("body").getJSONObject("parameters");
    }
}
