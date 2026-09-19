package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Predicate;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit semantic projection for the persistent Predicate definition. */
public final class PredicateAdapter implements KangerUnitAdapter<Predicate> {

    public static final String TYPE_NAME = "PREDICATE";
    public static final String LAYOUT_NAME = "Predicate-v1";
    public static final PredicateAdapter INSTANCE = new PredicateAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(
            LAYOUT_NAME, Arrays.asList(
                    Descriptor.field("id", Descriptor.INT64),
                    Descriptor.field("mindId", Descriptor.INT64),
                    Descriptor.field("deleted", Descriptor.BOOL),
                    Descriptor.field("name", Descriptor.ref("dictionary")),
                    Descriptor.field("range", Descriptor.INT32)));

    private PredicateAdapter() {
    }

    @Override
    public UnitType getRuntimeType() {
        return UnitType.PREDICATE;
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
    public StructuralValue project(Predicate value, Mind mind) {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("name", StructuralValue.ref("dictionary", value.getNameId()));
        fields.put("range", StructuralValue.int32(value.getRange()));
        return StructuralValue.struct(fields);
    }

    @Override
    public Predicate materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);
        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId =
                require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted =
                require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();
        StructuralValue name = require(fields, "name", StructuralValue.Kind.REF);
        if (!"dictionary".equals(name.getReferenceSchema())) {
            throw new IOException("Invalid PREDICATE name reference namespace");
        }
        ITerm term = mind.getTerms().get(name.getReferenceId());
        if (term == null) {
            throw new IOException("PREDICATE references missing dictionary id="
                    + name.getReferenceId());
        }

        Predicate result = new Predicate(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setName(term);
        result.setRange(
                require(fields, "range", StructuralValue.Kind.INT32).asInt32());
        if (deleted) result.setDeleted(true, mind);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("PREDICATE structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing PREDICATE field " + name);
        return value;
    }
}
