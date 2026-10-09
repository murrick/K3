/*
 * MIT License
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.CanonicalCommandProcessor;
import org.kanger.command.CommandParser;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Map;

/**
 * Standalone diagnostic for equivalent connection/rollback cycles.
 * Run with the module test classpath; the optional argument is the cycle count.
 * Reflection measures session ownership without changing runtime state.
 * Includes nested commit/rollback and checks reclamation at root quiescence.
 */
public final class ContextLongSessionProbe {
    private ContextLongSessionProbe() { }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static int retained(DB data) throws Exception {
        Field field = DB.class.getDeclaredField("retiredLayers");
        field.setAccessible(true);
        return ((Set<?>) field.get(data)).size();
    }

    static int communes(DB data) throws Exception {
        Field cache = DB.class.getDeclaredField("communeCache");
        cache.setAccessible(true);
        Field runtimes = CommuneRuntimeCache.class.getDeclaredField("runtimes");
        runtimes.setAccessible(true);
        return ((Map<?, ?>) runtimes.get(cache.get(data))).size();
    }

    public static void main(String[] args) throws Exception {
        int cycles = args.length == 0 ? 40 : Integer.parseInt(args[0]);
        require(cycles > 0, "positive cycle count required");
        Path directory = Files.createTempDirectory("kanger-long-session-");
        System.out.println("Test database: " + directory);
        User user = new User();
        user.setDatabaseDir(directory + File.separator);
        DB data = new DB();
        data.init(user);
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage("N");
        user.setCurrentMind(mind);
        try {
            require(Boolean.TRUE.equals(mind.query("!p(John);", null, false)), "fact");
            require(Boolean.TRUE.equals(mind.query("!@x p(x) -> q(x);", null, false)), "rule");
        } finally {
            user.setCurrentMind(mind.closeStorage());
        }
        mind = (Mind) user.getCurrentMind().useStorage("X");
        user.setCurrentMind(mind);
        CanonicalCommandProcessor processor = new CanonicalCommandProcessor();
        CommandParser parser = new CommandParser();
        int baseline = -1;
        try {
            require(Boolean.TRUE.equals(mind.query("!anchor(X);", null, false)), "anchor");
            for (int cycle = 1; cycle <= cycles; cycle++) {
                data.connectContext("N", cycle % 2 == 0 ? "own" : null);
                require(Boolean.TRUE.equals(mind.query("?$x q(x);", null, false)),
                        "connected query " + cycle);
                require(mind.getValues().size() == 1, "connected Values " + cycle);
                processor.execute(parser.parse("transaction start"), user);
                Mind child = (Mind) user.getCurrentMind();
                processor.execute(parser.parse("ctx ask N !p(Tom);"), user);
                require(Boolean.TRUE.equals(child.query("?q(Tom);", null, false)),
                        "child query " + cycle);
                processor.execute(parser.parse("transaction start"), user);
                Mind inner = (Mind) user.getCurrentMind();
                processor.execute(parser.parse("ctx ask N !p(Mary);"), user);
                require(Boolean.TRUE.equals(inner.query("?q(Mary);", null, false)),
                        "inner query " + cycle);
                boolean committed = cycle % 2 == 0;
                processor.execute(parser.parse(committed ? "commit" : "rollback"), user);
                child = (Mind) user.getCurrentMind();
                require(Boolean.TRUE.equals(child.query("?q(Tom);", null, false)),
                        "outer checkpoint lost Tom " + cycle);
                require(committed == Boolean.TRUE.equals(child.query("?q(Mary);", null, false)),
                        "inner settlement lost its semantics " + cycle);
                processor.execute(parser.parse("rollback"), user);
                mind = (Mind) user.getCurrentMind();
                require(mind.query("?q(Tom);", null, false) == null,
                        "Tom survived rollback " + cycle);
                require(mind.query("?q(Mary);", null, false) == null,
                        "Mary survived outer rollback " + cycle);
                require(Boolean.TRUE.equals(mind.query("?$x q(x);", null, false)),
                        "root query " + cycle);
                require(mind.getValues().size() == 1, "root Values " + cycle);
                if (baseline < 0) baseline = mind.getRules().size();
                require(mind.getRules().size() == baseline, "root rules grew " + cycle);
                data.disconnectContext("N");
                require(mind.query("?q(John);", null, false) == null,
                        "John survived disconnect " + cycle);
                require(retained(data) == 0, "retired layers survived root settlement " + cycle);
                require(communes(data) == 0, "communes survived disconnect " + cycle);
                if (cycle == 1 || cycle % 10 == 0 || cycle == cycles) {
                    System.out.println("CYCLE " + cycle + " rootRules=" + baseline
                            + " retiredLayers=" + retained(data));
                }
            }
        } finally {
            Mind current = (Mind) user.getCurrentMind();
            while (current.getNext() != null) {
                Mind parent = (Mind) current.getNext();
                parent.release(current);
                current = parent;
                user.setCurrentMind(current);
            }
            user.setCurrentMind(current.closeStorage());
        }
        require(retained(data) == 0, "layers retained after close");
        System.out.println("SEMANTIC_CYCLES_PASS " + cycles + "; CLOSED_LAYERS=0");
    }
}
