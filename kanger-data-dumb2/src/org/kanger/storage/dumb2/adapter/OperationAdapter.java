package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.LibMode;
import org.kanger.enums.UnitType;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Operation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit semantic projection for one persistent KANGER library operation. */
public final class OperationAdapter implements KangerUnitAdapter<Operation> {

    public static final String TYPE_NAME = "SYSOP";
    public static final String LAYOUT_NAME = "Operation-v1";
    public static final String MODE_NAME = "OperationMode";
    public static final OperationAdapter INSTANCE = new OperationAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("id", Descriptor.INT64),
            Descriptor.field("mindId", Descriptor.INT64),
            Descriptor.field("deleted", Descriptor.BOOL),
            Descriptor.field("mode", Descriptor.enumeration(MODE_NAME,
                    Arrays.asList("UNKNOWN", "PREDICATE", "FUNCTION"))),
            Descriptor.field("name", Descriptor.UTF8),
            Descriptor.field("scripts", Descriptor.list(Descriptor.UTF8)),
            Descriptor.field("range", Descriptor.INT32),
            Descriptor.field("params", Descriptor.list(Descriptor.UTF8))));

    private OperationAdapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.SYSOP; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(Operation value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        int range = value.getRange();
        if (range < 0) throw new IOException("SYSOP range must not be negative");
        if (value.getParams().size() < range)
            throw new IOException("SYSOP parameter list shorter than range=" + range);

        List<StructuralValue> scripts = new ArrayList<StructuralValue>();
        for (String script : value.getScripts())
            scripts.add(StructuralValue.utf8(script));

        List<StructuralValue> params = new ArrayList<StructuralValue>(range);
        for (int i = 0; i < range; ++i)
            params.add(StructuralValue.utf8(value.getParams().get(i)));

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("mode", StructuralValue.enumeration(
                MODE_NAME, value.getMode().name()));
        fields.put("name", StructuralValue.utf8(value.getName()));
        fields.put("scripts", StructuralValue.list(scripts));
        fields.put("range", StructuralValue.int32(range));
        fields.put("params", StructuralValue.list(params));
        return StructuralValue.struct(fields);
    }

    @Override
    public Operation materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);

        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();

        StructuralValue modeValue = require(fields, "mode", StructuralValue.Kind.ENUM);
        if (!MODE_NAME.equals(modeValue.getEnumName()))
            throw new IOException("Invalid SYSOP mode enum " + modeValue.getEnumName());
        final LibMode mode;
        try {
            mode = LibMode.valueOf(modeValue.getEnumSymbol());
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unknown SYSOP mode " + modeValue.getEnumSymbol(), failure);
        }

        String name = require(fields, "name", StructuralValue.Kind.UTF8).asUtf8();
        int range = require(fields, "range", StructuralValue.Kind.INT32).asInt32();
        if (range < 0) throw new IOException("SYSOP range must not be negative");

        StructuralValue scriptsValue =
                require(fields, "scripts", StructuralValue.Kind.LIST);
        List<String> scripts = strings(scriptsValue, "scripts");

        StructuralValue paramsValue =
                require(fields, "params", StructuralValue.Kind.LIST);
        List<String> params = strings(paramsValue, "params");
        if (params.size() != range)
            throw new IOException("SYSOP parameter count " + params.size()
                    + " does not match range=" + range);

        Operation result = new Operation(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setMode(mode);
        result.setName(name);
        result.setRange(range);
        result.getScripts().addAll(scripts);
        result.getParams().addAll(params);
        result.getParams().add(name); // runtime/result slot, not persistent payload
        if (deleted) mind.setUnitDeleted(result, true);
        return result;
    }

    private static List<String> strings(StructuralValue value, String field)
            throws IOException {
        List<String> result = new ArrayList<String>();
        for (StructuralValue one : value.asList()) {
            if (one.getKind() != StructuralValue.Kind.UTF8)
                throw new IOException("SYSOP " + field + " element must be UTF8");
            result.add(one.asUtf8());
        }
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("SYSOP structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing SYSOP field " + name);
        return value;
    }
}
