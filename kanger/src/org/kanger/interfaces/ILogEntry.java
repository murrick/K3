/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.interfaces;

import org.kanger.enums.LogMode;

import java.util.Date;

/**
 * Immutable-by-contract diagnostic event recorded during KANGER execution.
 * A log entry describes an observation made by a Mind or one of its owned
 * components; it is not part of logical truth, transaction state or persisted
 * semantic provenance unless an embedding application stores it separately.
 *
 * <p>The textual record is intended for diagnostics and human inspection. Its
 * wording and formatting are not a stable serialization or machine protocol.</p>
 */
public interface ILogEntry {

    /**
     * Returns the category assigned when the diagnostic event was created.
     * The category allows callers to filter or render records without parsing
     * their human-readable text.
     *
     * @return non-null diagnostic category of this entry
     */
    LogMode getType();

    /**
     * Returns the wall-clock timestamp captured when the entry was created.
     * Callers must treat the returned value as descriptive ordering metadata,
     * not as a transaction identifier or proof of causal ordering.
     *
     * @return creation timestamp of the diagnostic entry
     */
    Date getTime();

    /**
     * Returns the human-readable diagnostic payload captured for this entry.
     * The value may contain object identifiers or context-specific wording and
     * must not be used as a canonical identity or persistence representation.
     *
     * @return diagnostic message associated with the entry
     */
    String getRecord();
}
