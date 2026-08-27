/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.exception;

/** Controlled KANGER failure that identifies a source range. */
public interface SourceLocatedFailure extends KangerFailure {

    /**
     * @return source range, or {@code null} when the failure is not localized
     */
    SourceSpan getSourceSpan();
}
