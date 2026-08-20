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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
