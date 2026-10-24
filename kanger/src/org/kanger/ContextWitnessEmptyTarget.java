/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextWitnessResultTransport.Cut;

/** Explicit absence of target producers in one whole native-bound inventory. */
final class ContextWitnessEmptyTarget {
    final Snapshot snapshot;
    final Cut cut;
    private ContextWitnessEmptyTarget(Snapshot snapshot) {
        this.snapshot = snapshot; cut = Cut.of(snapshot);
    }
    static ContextWitnessEmptyTarget of(Snapshot snapshot,Runnable work) {
        if(snapshot.trustedGroup!=null){
            ContextProductionFamilyAssembly.identity(snapshot,false,work);
            if(!snapshot.primary.isEmpty())throw new IllegalStateException("Foreign shared target contains primary declarations");
            return new ContextWitnessEmptyTarget(snapshot);
        }
        ContextProductionInventory inventory = snapshot.inventory;
        Set<Long> ids = new HashSet<>();
        for (ContextProductionInventory.Row row : inventory.primary) { work.run(); ids.add(row.nativeRule); }
        if (snapshot.primary.size() != ids.size()) throw new IllegalStateException("Empty target inventory is incomplete");
        for (Entry entry : snapshot.primary) {
            work.run();
            if (!entry.source.context.equals(inventory.context) || entry.source.revision != inventory.revision
                    || entry.source.localX != inventory.localX || !entry.source.view.equals("inventory:"+inventory.fingerprint)
                    || !ids.remove(entry.source.rule)) throw new IllegalStateException("Empty target binding mismatch");
            if (!inventory.localX) throw new IllegalStateException("Foreign target contains primary declarations");
            // Local X facts are already accounted as seeds; none may hide a producer.
            if (entry.bundle == null) for (Clause clause : entry.clauses) {
                work.run(); if (!clause.variables.isEmpty() || clause.literals.size() != 1)
                    throw new IllegalStateException("Self target contains a producer clause");
            }
        }
        if (!ids.isEmpty()) throw new IllegalStateException("Empty target has unaccounted rows");
        return new ContextWitnessEmptyTarget(snapshot);
    }
    void validate(Cut expected,Runnable work) {
        work.run(); if (!cut.equals(expected)) throw new IllegalStateException("Empty target has a different inventory cut");
    }
}
