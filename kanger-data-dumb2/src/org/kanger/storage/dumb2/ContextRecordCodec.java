package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.adapter.KangerAdapterRegistry;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * DUMB2 record payload boundary.
 *
 * <p>KANGER semantic units use explicit semantic adapters. Historical physical
 * Long / Collection&lt;Long&gt; payloads are represented by neutral structural
 * types and never masquerade as semantic UnitTypes.</p>
 */
final class ContextRecordCodec {

    static final String LONG_TYPE = "DUMB2_LONG";
    static final String LONGS_TYPE = "DUMB2_LONGS";

    private static final Descriptor LONG_DESCRIPTOR = Descriptor.INT64;
    private static final Descriptor LONGS_DESCRIPTOR =
            Descriptor.list(Descriptor.INT64);

    private final KangerRecordCodec kanger =
            new KangerRecordCodec(new KangerAdapterRegistry());

    byte[] encode(ContextStore context,
                  long id,
                  int hash,
                  long nextId,
                  Object data) throws Exception {
        if (context == null || data == null) {
            throw new NullPointerException();
        }

        if (data instanceof IUnit) {
            IUnit unit = (IUnit) data;
            Mind mind = unit.getMind();
            if (mind == null) {
                throw new IllegalStateException(
                        "DUMB2 cannot project an unbound KANGER unit "
                                + unit.getUnitType() + " id=" + unit.getId());
            }
            return kanger.encode(context, id, hash, nextId, unit, mind);
        }

        if (data instanceof Long) {
            TypeDefinition definition =
                    context.registerType(LONG_TYPE, LONG_DESCRIPTOR);
            byte[] payload = StructuralValueCodec.encode(
                    definition.getDescriptor(),
                    StructuralValue.int64(((Long) data).longValue()));
            return PersistentRecordCodec.encode(new PersistentRecord(
                    id, hash, nextId, definition.getTypeCode(), payload));
        }

        if (data instanceof Collection) {
            List<StructuralValue> values = new ArrayList<StructuralValue>();
            for (Object one : (Collection<?>) data) {
                if (!(one instanceof Long)) {
                    throw new IllegalArgumentException(
                            "DUMB2 physical LONGS payload contains "
                                    + (one == null ? "null" : one.getClass().getName()));
                }
                values.add(StructuralValue.int64(((Long) one).longValue()));
            }
            TypeDefinition definition =
                    context.registerType(LONGS_TYPE, LONGS_DESCRIPTOR);
            byte[] payload = StructuralValueCodec.encode(
                    definition.getDescriptor(), StructuralValue.list(values));
            return PersistentRecordCodec.encode(new PersistentRecord(
                    id, hash, nextId, definition.getTypeCode(), payload));
        }

        throw new IllegalArgumentException(
                "Unsupported DUMB2 persistent payload " + data.getClass().getName());
    }

    Object decode(PersistentTypeResolver context,
                  PersistentRecord record,
                  Mind mind) throws Exception {
        if (context == null || record == null) {
            throw new NullPointerException();
        }

        TypeDefinition definition = context.resolveType(record.getTypeCode());
        if (LONG_TYPE.equals(definition.getTypeName())) {
            requireDescriptor(definition, LONG_DESCRIPTOR);
            StructuralValue value = StructuralValueCodec.decode(
                    definition.getDescriptor(), record.getPayload());
            if (value.getKind() != StructuralValue.Kind.INT64) {
                throw new IOException("Invalid DUMB2_LONG payload");
            }
            return Long.valueOf(value.asInt64());
        }

        if (LONGS_TYPE.equals(definition.getTypeName())) {
            requireDescriptor(definition, LONGS_DESCRIPTOR);
            StructuralValue value = StructuralValueCodec.decode(
                    definition.getDescriptor(), record.getPayload());
            if (value.getKind() != StructuralValue.Kind.LIST) {
                throw new IOException("Invalid DUMB2_LONGS payload");
            }
            List<Long> result = new ArrayList<Long>();
            for (StructuralValue one : value.asList()) {
                if (one.getKind() != StructuralValue.Kind.INT64) {
                    throw new IOException("Invalid DUMB2_LONGS element");
                }
                result.add(Long.valueOf(one.asInt64()));
            }
            return result;
        }

        if (mind == null) {
            throw new IllegalStateException(
                    "KANGER semantic hydration requires Mind for "
                            + definition.getTypeName());
        }
        return kanger.decode(context, record, mind).getUnit();
    }

    boolean isPhysicalScalar(PersistentTypeResolver context, PersistentRecord record) {
        TypeDefinition definition = context.resolveType(record.getTypeCode());
        return LONG_TYPE.equals(definition.getTypeName())
                || LONGS_TYPE.equals(definition.getTypeName());
    }

    private static void requireDescriptor(TypeDefinition definition,
                                          Descriptor expected)
            throws IOException {
        if (!expected.equals(definition.getDescriptor())) {
            throw new IOException(
                    "DUMB2 physical type descriptor mismatch for "
                            + definition.getTypeName());
        }
    }
}
