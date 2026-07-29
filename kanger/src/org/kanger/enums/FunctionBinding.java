/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.enums;

/**
 * Resolution contract captured for one compiled Function occurrence.
 */
public enum FunctionBinding {
    /**
     * Backward-compatible mode for Function records written before explicit
     * binding metadata existed. Execution preserves the historical
     * infrastructure-first, UDF-second lookup order.
     */
    LEGACY_AUTO,

    /** Resolve only through the immutable infrastructure function registry. */
    INFRASTRUCTURE,

    /** Resolve the current user-defined operation by signature on each call. */
    UDF_DYNAMIC;

    public static FunctionBinding fromCode(int code) {
        FunctionBinding[] values = values();
        return code >= 0 && code < values.length ? values[code] : LEGACY_AUTO;
    }
}
