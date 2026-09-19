package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

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
public final class TValueAdapter {

    public static final String TYPE_NAME = "TVALUE";
    public static final String LAYOUT_NAME = "TValue-v1";

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
     * Materializes a TValue shell in the supplied Mind by resolving its two
     * typed references through the current KANGER factories.
     *
     * <p>The returned object is not registered in TValueFactory. Registration,
     * canonicalization and transaction publication remain factory/Mind
     * lifecycle responsibilities, exactly as for a unit hydrated by storage.</p>
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

        ITerm term = mind.getTerms().get(termRef.getReferenceId());
        TVariable variable = mind.getTVars().get(variableRef.getReferenceId());
        if (term == null) {
            throw new IOException("TVALUE references missing dictionary id="
                    + termRef.getReferenceId());
        }
        if (variable == null) {
            throw new IOException("TVALUE references missing tvariables id="
                    + variableRef.getReferenceId());
        }

        TValue result = new TValue(variable, term, mind);
        result.setId(id);
        result.setMindId(mindId);
        if (deleted) {
            result.setDeleted(true, mind);
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
