/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.Objects;
/** Linear resource charging for complete finite argument/variable inventories; no arity ceiling. */
final class ContextArgumentBudget {
 private ContextArgumentBudget(){}
 static void positions(Iterable<?> positions,Runnable work){Objects.requireNonNull(positions);Objects.requireNonNull(work);for(Object position:positions){work.run();Objects.requireNonNull(position,"Complete argument/variable position required");}}
}
