/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

/** Parse/canonicalization rejection at the command-language boundary. */
public final class CommandParseException extends Exception {

    public enum Reason {
        UNKNOWN_KEYWORD,
        AMBIGUOUS_PREFIX,
        INVALID_GRAMMAR,
        MISSING_ARGUMENT,
        EXTRA_ARGUMENT,
        INVALID_ARGUMENT_SHAPE,
        UNTERMINATED_QUOTE
    }

    private final Reason reason;

    public CommandParseException(Reason reason, String message) {
        super(message);
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
