package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, non-reflective mapping between current KANGER Units and persistent
 * symbolic layouts.
 *
 * <p>The registry is deliberately outside the generic descriptor codec. A
 * future storage reader can decode StructuralValue without this class; KANGER
 * uses it only when semantic Unit materialization is requested.</p>
 */
public final class KangerAdapterRegistry {

    private final Map<UnitType, KangerUnitAdapter<?>> byRuntimeType =
            new EnumMap<UnitType, KangerUnitAdapter<?>>(UnitType.class);
    private final Map<String, List<KangerUnitAdapter<?>>> byPersistentName =
            new HashMap<String, List<KangerUnitAdapter<?>>>();

    public KangerAdapterRegistry() {
        register(TValueAdapter.INSTANCE);
    }

    public synchronized void register(KangerUnitAdapter<?> adapter) {
        if (adapter == null) {
            throw new NullPointerException("adapter");
        }
        KangerUnitAdapter<?> existing = byRuntimeType.get(adapter.getRuntimeType());
        if (existing != null && existing != adapter) {
            throw new IllegalStateException(
                    "KANGER adapter already registered for " + adapter.getRuntimeType());
        }
        byRuntimeType.put(adapter.getRuntimeType(), adapter);

        List<KangerUnitAdapter<?>> layouts =
                byPersistentName.get(adapter.getTypeName());
        if (layouts == null) {
            layouts = new ArrayList<KangerUnitAdapter<?>>();
            byPersistentName.put(adapter.getTypeName(), layouts);
        }
        for (KangerUnitAdapter<?> one : layouts) {
            if (one.getDescriptor().equals(adapter.getDescriptor())) {
                if (one != adapter) {
                    throw new IllegalStateException(
                            "KANGER adapter already registered for persistent layout "
                                    + adapter.getTypeName());
                }
                return;
            }
        }
        layouts.add(adapter);
    }

    public synchronized KangerUnitAdapter<?> forRuntime(IUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        KangerUnitAdapter<?> adapter = byRuntimeType.get(unit.getUnitType());
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "No DUMB2 KANGER adapter for runtime type " + unit.getUnitType());
        }
        return adapter;
    }

    public synchronized KangerUnitAdapter<?> forPersistent(TypeDefinition definition) {
        if (definition == null) {
            throw new NullPointerException("definition");
        }
        List<KangerUnitAdapter<?>> layouts =
                byPersistentName.get(definition.getTypeName());
        if (layouts != null) {
            for (KangerUnitAdapter<?> adapter : layouts) {
                if (adapter.getDescriptor().equals(definition.getDescriptor())) {
                    return adapter;
                }
            }
        }
        throw new IllegalArgumentException(
                "No KANGER semantic adapter for persistent layout "
                        + definition.getTypeName() + "/"
                        + definition.getDescriptor().getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public StructuralValue project(IUnit unit, Mind mind) throws Exception {
        KangerUnitAdapter adapter = forRuntime(unit);
        return adapter.project(unit, mind);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public IUnit materialize(TypeDefinition definition,
                             StructuralValue value,
                             Mind mind) throws Exception {
        KangerUnitAdapter adapter = forPersistent(definition);
        return (IUnit) adapter.materialize(value, mind);
    }
}
