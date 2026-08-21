/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.exception;

/**
 * Marker contract for a controlled KANGER failure.
 *
 * <p>The failure code is stable machine-readable semantics owned by Core. It
 * must not be reconstructed from {@link Throwable#getMessage()} or from a
 * rendered environment-specific error string.</p>
 */
public interface KangerFailure {

    /**
     * @return stable machine-readable KANGER failure code
     */
    String getFailureCode();
}
