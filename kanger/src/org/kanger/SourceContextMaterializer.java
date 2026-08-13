/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IMind;

/**
 * Compatibility facade for semantic source materialization.
 *
 * <p>There is no authoritative current source document. New code materializes
 * the declarative source projection of the current explicit Mind level through
 * {@link CurrentLevelSourceMaterializer}.</p>
 */
public final class SourceContextMaterializer {

    private SourceContextMaterializer() {
    }

    public static String materializeCurrentLevel(IMind mind) throws Exception {
        return CurrentLevelSourceMaterializer.materialize(mind);
    }

    /** Compatibility entry point retained for existing level-zero callers. */
    @Deprecated
    public static String materializeLevelZero(IMind mind) throws Exception {
        if (mind == null) {
            throw new IllegalArgumentException("Mind is required");
        }
        if (mind.getTransactionLevel() != 0) {
            throw new IllegalStateException(
                    "Level-zero source materialization requires transaction level 0");
        }
        return CurrentLevelSourceMaterializer.materialize(mind);
    }
}
