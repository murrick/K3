package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.CachedDomain;
import org.kanger.units.Domain;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit semantic projection for one persistent KANGER Domain. */
public final class DomainAdapter implements KangerUnitAdapter<Domain> {

    public static final String TYPE_NAME = "DOMAIN";
    public static final String LAYOUT_NAME = "Domain-v1";
    public static final DomainAdapter INSTANCE = new DomainAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("id", Descriptor.INT64),
            Descriptor.field("mindId", Descriptor.INT64),
            Descriptor.field("deleted", Descriptor.BOOL),
            Descriptor.field("rule", Descriptor.ref("rules")),
            Descriptor.field("substitutable", Descriptor.BOOL),
            Descriptor.field("abstractive", Descriptor.BOOL),
            Descriptor.field("solve", SolveStructuralAdapter.DESCRIPTOR)));

    private DomainAdapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.DOMAIN; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(Domain value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("rule", StructuralValue.ref("rules", value.getRuleId()));
        fields.put("substitutable", StructuralValue.bool(value.isSubstitutable()));
        fields.put("abstractive", StructuralValue.bool(value.isAbstractive()));
        fields.put("solve", SolveStructuralAdapter.project(value));
        return StructuralValue.struct(fields);
    }

    @Override
    public Domain materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);

        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();

        StructuralValue rule = require(fields, "rule", StructuralValue.Kind.REF);
        if (!"rules".equals(rule.getReferenceSchema()))
            throw new IOException("Invalid DOMAIN rule reference namespace "
                    + rule.getReferenceSchema());

        Domain result = new CachedDomain(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setPersistentRuleId(rule.getReferenceId());
        result.setSubstitutable(
                require(fields, "substitutable", StructuralValue.Kind.BOOL).asBool());
        result.setAbstractive(
                require(fields, "abstractive", StructuralValue.Kind.BOOL).asBool());
        SolveStructuralAdapter.apply(
                require(fields, "solve", StructuralValue.Kind.STRUCT), result);
        if (deleted) mind.setUnitDeleted(result, true);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("DOMAIN structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing DOMAIN field " + name);
        return value;
    }
}
