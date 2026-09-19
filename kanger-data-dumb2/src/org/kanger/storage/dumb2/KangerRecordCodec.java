package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.adapter.KangerAdapterRegistry;
import org.kanger.storage.dumb2.adapter.KangerUnitAdapter;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;

/**
 * Thin KANGER semantic bridge around the generic persistent record codec.
 *
 * <p>Registration is deliberately performed before record bytes are returned:
 * a caller cannot obtain a record containing a typeCode that has not already
 * been published in the Context manifest.</p>
 */
final class KangerRecordCodec {

    private final KangerAdapterRegistry adapters;

    KangerRecordCodec(KangerAdapterRegistry adapters) {
        if (adapters == null) {
            throw new NullPointerException("adapters");
        }
        this.adapters = adapters;
    }

    byte[] encode(ContextStore context,
                  long id,
                  int hash,
                  long nextId,
                  IUnit unit,
                  Mind mind) throws Exception {
        if (context == null || unit == null) {
            throw new NullPointerException();
        }

        KangerUnitAdapter<?> adapter = adapters.forRuntime(unit);
        TypeDefinition definition = context.registerType(
                adapter.getTypeName(), adapter.getDescriptor());
        StructuralValue structural = adapters.project(unit, mind);
        byte[] payload = StructuralValueCodec.encode(
                definition.getDescriptor(), structural);
        return PersistentRecordCodec.encode(new PersistentRecord(
                id, hash, nextId, definition.getTypeCode(), payload));
    }

    DecodedRecord decode(ContextStore context,
                         byte[] bytes,
                         Mind mind) throws Exception {
        if (context == null) {
            throw new NullPointerException("context");
        }

        PersistentRecord record = PersistentRecordCodec.decode(bytes);
        TypeDefinition definition = context.resolveType(record.getTypeCode());
        StructuralValue structural = StructuralValueCodec.decode(
                definition.getDescriptor(), record.getPayload());
        IUnit unit = adapters.materialize(definition, structural, mind);
        return new DecodedRecord(record, definition, unit);
    }

    static final class DecodedRecord {
        private final PersistentRecord record;
        private final TypeDefinition definition;
        private final IUnit unit;

        private DecodedRecord(PersistentRecord record,
                              TypeDefinition definition,
                              IUnit unit) {
            this.record = record;
            this.definition = definition;
            this.unit = unit;
        }

        long getId() { return record.getId(); }
        int getHash() { return record.getHash(); }
        long getNextId() { return record.getNextId(); }
        int getTypeCode() { return record.getTypeCode(); }
        TypeDefinition getDefinition() { return definition; }
        IUnit getUnit() { return unit; }
    }
}
