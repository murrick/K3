/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.exception;

import org.kanger.enums.ParseError;

/**
 * Controlled KANGER source parsing/compilation failure.
 *
 * <p>The exception owns structured machine-readable semantics. Human-readable
 * text is the ordinary {@link #getMessage()} value; source location is carried
 * separately as {@link SourceSpan}. Consumers must never parse either
 * {@code toString()} or the message to recover source coordinates.</p>
 */
public class ParseErrorException extends Exception implements SourceLocatedFailure {

    private static final long serialVersionUID = 1L;
    private static final String FAILURE_CODE = "parse_error";

    private final SourceSpan sourceSpan;
    private final ParseError reason;

    public ParseErrorException() {
        this(null, null, "Parse error");
    }

    public ParseErrorException(int offset, String message) {
        this(new SourceSpan(offset, 0), null, message);
    }

    public ParseErrorException(int offset, int length, String message) {
        this(new SourceSpan(offset, length), null, message);
    }

    public ParseErrorException(SourceSpan sourceSpan, String message) {
        this(sourceSpan, null, message);
    }

    public ParseErrorException(int position, ParseError reason) {
        this(position < 0 ? null : new SourceSpan(position, 0),
                requireReason(reason), message(reason));
    }

    private ParseErrorException(SourceSpan sourceSpan,
                                ParseError reason,
                                String message) {
        super(normalizeMessage(message));
        this.sourceSpan = sourceSpan;
        this.reason = reason;
    }

    @Override
    public String getFailureCode() {
        return FAILURE_CODE;
    }

    @Override
    public SourceSpan getSourceSpan() {
        return sourceSpan;
    }

    /** Optional historical parser reason when one was supplied explicitly. */
    public ParseError getCode() {
        return reason;
    }

    /**
     * Transitional Console facade. Presentation code should consume
     * {@link #getSourceSpan()} directly.
     */
    @Deprecated
    public int getExceptionPosition() {
        return sourceSpan == null ? -1 : sourceSpan.getOffset();
    }

    /** Transitional Console facade; use {@link #getMessage()}. */
    @Deprecated
    public String getExceptionMessage() {
        return getMessage();
    }

    /** Transitional facade retained only while legacy callers are removed. */
    @Deprecated
    public String getPureMessage() {
        if (sourceSpan == null) {
            return getMessage();
        }
        return sourceSpan.getOffset() + "@" + getMessage();
    }

    private static ParseError requireReason(ParseError reason) {
        if (reason == null) {
            throw new IllegalArgumentException("parse error reason is required");
        }
        return reason;
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isEmpty() ? "Parse error" : message;
    }

    private static String message(ParseError error) {
        switch (error) {
            case SUCCESS:
                return "Success";
            case BRACKET:
                return "Right brackets mismatch";
            case SUCC:
                return "Must be ! or ? symbol";
            case QUOTESL:
                return "Left quotes mismatch";
            case QUOTESR:
                return "Right quotes mismatch";
            case RBRACES:
                return "Right braces mismatch";
            case LBRACES:
                return "Left braces mismatch";
            case EOLN:
                return "Semicolon required";
            case ANOT:
                return "Misplaced ~ symbol";
            case LBRACK:
                return "Misplaced left bracket";
            case QUANTOR:
                return "Misplaced quantor symbol";
            case INFIX:
                return "Misplaced infix symbol";
            case EMPTY:
                return "Empty term";
            case AVAR:
                return "Quantor variable mismatch";
            case INPRO:
                return "Symbol inside predicate";
            case COMMA:
                return "Misplaced comma";
            case ATERM:
                return "Misplaced term";
            case IPNAME:
                return "Invalid predicate name";
            case FUNC:
                return "Unexpected use of function";
            case RANGE:
                return "Unexpected parameter count";
            case COMMENT:
                return "Unclosed comments";
            case EPARAM:
                return "External parameter expected";
            case ENEG:
                return "Misplaced unary minus";
            default:
                return "Unknown parse error";
        }
    }
}
