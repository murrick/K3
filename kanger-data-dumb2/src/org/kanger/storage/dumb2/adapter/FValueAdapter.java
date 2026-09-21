package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.FValue;
import org.kanger.units.Function;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit semantic projection for a persistent KANGER FValue result. */
public final class FValueAdapter implements KangerUnitAdapter<FValue> {

    public static final String TYPE_NAME = "FVALUE";
    public static final String LAYOUT_NAME = "FValue-v1";
    public static final FValueAdapter INSTANCE = new FValueAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(
            LAYOUT_NAME, Arrays.asList(
                    Descriptor.field("id", Descriptor.INT64),
                    Descriptor.field("mindId", Descriptor.INT64),
                    Descriptor.field("deleted", Descriptor.BOOL),
                    Descriptor.field("function", Descriptor.ref("functions")),
                    Descriptor.field("value", Descriptor.ref("dictionary")),
                    Descriptor.field("stamp", Descriptor.list(Descriptor.INT64))));

    private FValueAdapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.FVALUE; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(FValue value, Mind mind) {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("function", StructuralValue.ref("functions", value.getFunctionId()));
        fields.put("value", StructuralValue.ref("dictionary", value.getValueId()));
        List<StructuralValue> stamp = new ArrayList<StructuralValue>();
        for (Long id : value.getStamp()) stamp.add(StructuralValue.int64(id.longValue()));
        fields.put("stamp", StructuralValue.list(stamp));
        return StructuralValue.struct(fields);
    }

    @Override
    public FValue materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);
        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();
        StructuralValue functionRef = require(fields, "function", StructuralValue.Kind.REF);
        StructuralValue valueRef = require(fields, "value", StructuralValue.Kind.REF);
        requireReference(functionRef, "functions");
        requireReference(valueRef, "dictionary");

        Function function = mind.getFunctions().get(functionRef.getReferenceId());
        Term term = (Term) mind.getTerms().get(valueRef.getReferenceId());
        if (function == null) throw new IOException("FVALUE references missing functions id=" + functionRef.getReferenceId());
        if (term == null) throw new IOException("FVALUE references missing dictionary id=" + valueRef.getReferenceId());

        StructuralValue stampValue = require(fields, "stamp", StructuralValue.Kind.LIST);
        List<Long> stamp = new ArrayList<Long>();
        for (StructuralValue one : stampValue.asList()) {
            if (one.getKind() != StructuralValue.Kind.INT64)
                throw new IOException("FVALUE stamp element must be INT64");
            stamp.add(one.asInt64());
        }

        FValue result = new FValue();
        result.setMind(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setFunction(function);
        result.setValue(term);
        result.setStamp(stamp);
        if (deleted) result.setDeleted(true, mind);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value) throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("FVALUE structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields, String name,
                                           StructuralValue.Kind kind) throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing FVALUE field " + name);
        return value;
    }

    private static void requireReference(StructuralValue value, String schema) throws IOException {
        if (!schema.equals(value.getReferenceSchema()))
            throw new IOException("Invalid FVALUE reference namespace: expected " + schema
                    + " actual " + value.getReferenceSchema());
    }
}
