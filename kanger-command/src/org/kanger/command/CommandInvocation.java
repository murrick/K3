/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable result of the shared command-language boundary.
 *
 * <p>A line is either ordinary canonical command intent plus normalized
 * arguments, or a Core-language expression which must bypass command dispatch.
 * No execution behavior is attached to this object.</p>
 */
public final class CommandInvocation {

    public enum Kind {
        COMMAND,
        CORE_LANGUAGE
    }

    private final Kind kind;
    private final CommandIntent intent;
    private final Map<String, Object> arguments;
    private final String raw;

    private CommandInvocation(Kind kind,
                              CommandIntent intent,
                              Map<String, Object> arguments,
                              String raw) {
        this.kind = kind;
        this.intent = intent;
        this.arguments = Collections.unmodifiableMap(
                new LinkedHashMap<String, Object>(arguments));
        this.raw = raw;
    }

    public static CommandInvocation command(CommandIntent intent,
                                            Map<String, Object> arguments,
                                            String raw) {
        if (intent == null) {
            throw new IllegalArgumentException("intent must not be null");
        }
        Map<String, Object> args = arguments == null
                ? Collections.<String, Object>emptyMap() : arguments;
        return new CommandInvocation(Kind.COMMAND, intent, args, raw);
    }

    public static CommandInvocation command(CommandIntent intent, String raw) {
        return command(intent, Collections.<String, Object>emptyMap(), raw);
    }

    public static CommandInvocation coreLanguage(String raw) {
        return new CommandInvocation(
                Kind.CORE_LANGUAGE,
                null,
                Collections.<String, Object>emptyMap(),
                raw);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isCoreLanguage() {
        return kind == Kind.CORE_LANGUAGE;
    }

    public CommandIntent getIntent() {
        return intent;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Object getArgument(String name) {
        return arguments.get(name);
    }

    public String getRaw() {
        return raw;
    }
}
