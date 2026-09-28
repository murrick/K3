package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.TValue;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KANGER-side adapter between the current TValue semantic object and the
 * neutral DUMB 2.0 structural representation.
 *
 * <p>This class deliberately owns the KANGER knowledge. Descriptor/value codecs
 * remain unaware of TValue, UnitType and Java implementation classes.</p>
 */
public final class TValueAdapter implements KangerUnitAdapter<TValue> {

    public static final String TYPE_NAME = "TVALUE";
    public static final String LAYOUT_NAME = "TValue-v1";
    public static final TValueAdapter INSTANCE = new TValueAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("id", Descriptor.INT64),
            Descriptor.field("mindId", Descriptor.INT64),
            Descriptor.field("deleted", Descriptor.BOOL),
            Descriptor.field("value", Descriptor.ref("dictionary")),
            Descriptor.field("variable", Descriptor.ref("tvariables"))));

    private TValueAdapter() {
    }

    public static Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public UnitType getRuntimeType() {
        return UnitType.TVALUE;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public StructuralValue project(TValue value, Mind mind) {
        return toStructural(value, mind);
    }

    @Override
    public TValue materialize(StructuralValue value, Mind mind) throws Exception {
        return fromStructural(value, mind);
    }

    public static StructuralValue toStructural(TValue value, Mind mind) {
        if (value == null || mind == null) {
            throw new NullPointerException();
        }
        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("value", StructuralValue.ref("dictionary", value.getValueId()));
        fields.put("variable", StructuralValue.ref("tvariables", value.getTVarId()));
        return StructuralValue.struct(fields);
    }

    /**
     * Materializes only persistent TValue state. Typed reference IDs are restored
     * immediately; referenced semantic objects remain unresolved until TValue
     * accessors actually request them.
     */
    public static TValue fromStructural(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) {
            throw new NullPointerException();
        }
        Map<String, StructuralValue> fields = requireStruct(value);
        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();

        StructuralValue termRef = require(fields, "value", StructuralValue.Kind.REF);
        StructuralValue variableRef = require(fields, "variable", StructuralValue.Kind.REF);
        requireReference(termRef, "dictionary");
        requireReference(variableRef, "tvariables");

        TValue result = new TValue(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setPersistentReferences(
                termRef.getReferenceId(), variableRef.getReferenceId());
        if (deleted) {
            mind.setUnitDeleted(result, true);
        }
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT) {
            throw new IOException("TVALUE structural value must be STRUCT");
        }
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind) throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null) {
            throw new IOException("Missing TVALUE field " + name);
        }
        if (value.getKind() != kind) {
            throw new IOException("Invalid TVALUE field " + name + ": expected "
                    + kind + " actual " + value.getKind());
        }
        return value;
    }

    private static void requireReference(StructuralValue value, String schema)
            throws IOException {
        if (!schema.equals(value.getReferenceSchema())) {
            throw new IOException("Invalid TVALUE reference namespace: expected "
                    + schema + " actual " + value.getReferenceSchema());
        }
    }
}
