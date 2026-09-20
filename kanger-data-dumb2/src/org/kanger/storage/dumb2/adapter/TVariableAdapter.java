package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.TVariable;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit semantic projection for a persistent KANGER TVariable definition. */
public final class TVariableAdapter implements KangerUnitAdapter<TVariable> {

    public static final String TYPE_NAME = "TVARIABLE";
    public static final String LAYOUT_NAME = "TVariable-v1";
    public static final TVariableAdapter INSTANCE = new TVariableAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(
            LAYOUT_NAME, Arrays.asList(
                    Descriptor.field("id", Descriptor.INT64),
                    Descriptor.field("mindId", Descriptor.INT64),
                    Descriptor.field("deleted", Descriptor.BOOL),
                    Descriptor.field("name", Descriptor.ref("dictionary")),
                    Descriptor.field("index", Descriptor.INT32),
                    Descriptor.field("rule", Descriptor.ref("rules"))));

    private TVariableAdapter() {
    }

    @Override
    public UnitType getRuntimeType() {
        return UnitType.TVARIABLE;
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
    public StructuralValue project(TVariable value, Mind mind) {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("name", StructuralValue.ref("dictionary", value.getNameId()));
        fields.put("index", StructuralValue.int32(value.getIndex()));
        fields.put("rule", StructuralValue.ref("rules", value.getRuleId()));
        return StructuralValue.struct(fields);
    }

    @Override
    public TVariable materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);

        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId =
                require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted =
                require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();
        StructuralValue name =
                require(fields, "name", StructuralValue.Kind.REF);
        StructuralValue rule =
                require(fields, "rule", StructuralValue.Kind.REF);
        requireReference(name, "dictionary");
        requireReference(rule, "rules");

        ITerm nameTerm = mind.getTerms().get(name.getReferenceId());
        if (nameTerm == null) {
            throw new IOException("TVARIABLE references missing dictionary id="
                    + name.getReferenceId());
        }
        IRule ownerRule = mind.getRules().get(rule.getReferenceId());
        if (ownerRule == null) {
            throw new IOException("TVARIABLE references missing rules id="
                    + rule.getReferenceId());
        }

        TVariable result = new TVariable(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setName(nameTerm);
        result.setIndex(
                require(fields, "index", StructuralValue.Kind.INT32).asInt32());
        result.setRule(ownerRule);
        if (deleted) result.setDeleted(true, mind);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("TVARIABLE structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing TVARIABLE field " + name);
        return value;
    }

    private static void requireReference(StructuralValue value, String schema)
            throws IOException {
        if (!schema.equals(value.getReferenceSchema())) {
            throw new IOException("Invalid TVARIABLE reference namespace: expected "
                    + schema + " actual " + value.getReferenceSchema());
        }
    }
}
