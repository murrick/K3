/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.Objects;
import org.kanger.ContextProductionCapture.Source;

/** Immutable sourced primary declarations shared by production and qualification. */
public final class ContextProductionInputs {
    private ContextProductionInputs() { }
    public static final class Declaration {
        public final Source source;
        public final String statement;
        public Declaration(Source source,String statement) {
            this.source = Objects.requireNonNull(source); this.statement = Objects.requireNonNull(statement);
        }
    }
}
