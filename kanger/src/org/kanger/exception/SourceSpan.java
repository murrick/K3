/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.exception;

/**
 * Immutable source range used by KANGER failures.
 *
 * <p>Offsets and lengths are zero-based UTF-16 code-unit indexes. This matches
 * Java {@link String} indexing and JavaScript string offsets, so the same span
 * can cross the Core/Server/Browser boundary without coordinate conversion.
 * A zero length denotes a point/caret location.</p>
 */
public final class SourceSpan {

    private final int offset;
    private final int length;

    public SourceSpan(int offset, int length) {
        if (offset < 0) {
            throw new IllegalArgumentException("source offset must not be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("source length must not be negative");
        }
        this.offset = offset;
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public int getEndOffset() {
        return offset + length;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceSpan)) {
            return false;
        }
        SourceSpan that = (SourceSpan) other;
        return offset == that.offset && length == that.length;
    }

    @Override
    public int hashCode() {
        int result = offset;
        result = 31 * result + length;
        return result;
    }

    @Override
    public String toString() {
        return "SourceSpan{" + offset + "," + length + "}";
    }
}
