package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;

/**
 * Explicit KANGER semantic boundary for one persistent Unit layout.
 *
 * <p>Runtime UnitType is used only to select a KANGER adapter. It is never
 * serialized as a persistent ordinal. The persistent identity is the
 * Context-local typeCode resolved to typeName plus descriptor.</p>
 */
public interface KangerUnitAdapter<T extends IUnit> {

    UnitType getRuntimeType();

    String getTypeName();

    Descriptor getDescriptor();

    StructuralValue project(T value, Mind mind) throws Exception;

    T materialize(StructuralValue value, Mind mind) throws Exception;
}
