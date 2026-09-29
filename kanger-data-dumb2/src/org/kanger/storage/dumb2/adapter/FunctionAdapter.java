package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.FunctionBinding;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IArgument;
import org.kanger.primitives.Argument;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit semantic projection for a persistent KANGER Function definition. */
public final class FunctionAdapter implements KangerUnitAdapter<Function> {

    public static final String TYPE_NAME = "FUNCTION";
    public static final String LAYOUT_NAME = "Function-v1";
    public static final String BINDING_NAME = "FunctionBinding";
    public static final FunctionAdapter INSTANCE = new FunctionAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("id", Descriptor.INT64),
            Descriptor.field("mindId", Descriptor.INT64),
            Descriptor.field("deleted", Descriptor.BOOL),
            Descriptor.field("name", Descriptor.ref("dictionary")),
            Descriptor.field("range", Descriptor.INT32),
            Descriptor.field("binding", Descriptor.enumeration(BINDING_NAME,
                    Arrays.asList("LEGACY_AUTO", "INFRASTRUCTURE", "UDF_DYNAMIC"))),
            Descriptor.field("arguments", Descriptor.list(ArgumentStructuralAdapter.DESCRIPTOR))));

    private FunctionAdapter() {}

    @Override public UnitType getRuntimeType() { return UnitType.FUNCTION; }
    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public Descriptor getDescriptor() { return DESCRIPTOR; }

    @Override
    public StructuralValue project(Function value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        int range = value.getRange();
        if (range < 0 || value.getArguments().size() < range)
            throw new IOException("FUNCTION argument graph shorter than range=" + range);

        List<StructuralValue> arguments = new ArrayList<StructuralValue>(range);
        for (int i = 0; i < range; ++i)
            arguments.add(ArgumentStructuralAdapter.project((Argument) value.getArguments().get(i)));

        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("name", StructuralValue.ref("dictionary", value.getNameId()));
        fields.put("range", StructuralValue.int32(range));
        fields.put("binding", StructuralValue.enumeration(BINDING_NAME, value.getBinding().name()));
        fields.put("arguments", StructuralValue.list(arguments));
        return StructuralValue.struct(fields);
    }

    @Override
    public Function materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);
        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId = require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted = require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();
        StructuralValue name = require(fields, "name", StructuralValue.Kind.REF);
        if (!"dictionary".equals(name.getReferenceSchema()))
            throw new IOException("Invalid FUNCTION name reference namespace "
                    + name.getReferenceSchema());
        int range = require(fields, "range", StructuralValue.Kind.INT32).asInt32();
        if (range < 0) throw new IOException("FUNCTION range must not be negative");

        StructuralValue bindingValue = require(fields, "binding", StructuralValue.Kind.ENUM);
        if (!BINDING_NAME.equals(bindingValue.getEnumName()))
            throw new IOException("Invalid FUNCTION binding enum " + bindingValue.getEnumName());
        final FunctionBinding binding;
        try {
            binding = FunctionBinding.valueOf(bindingValue.getEnumSymbol());
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unknown FUNCTION binding " + bindingValue.getEnumSymbol(), failure);
        }

        StructuralValue list = require(fields, "arguments", StructuralValue.Kind.LIST);
        if (list.asList().size() != range)
            throw new IOException("FUNCTION arguments count " + list.asList().size()
                    + " does not match range=" + range);

        Function result = new Function(mind);
        result.setId(id);
        result.setMindId(mindId);
        result.setPersistentNameId(name.getReferenceId());
        result.setRange(range);
        result.setBinding(binding);
        for (StructuralValue argument : list.asList())
            result.getArguments().add(ArgumentStructuralAdapter.materialize(argument));
        result.getArguments().add(new Argument()); // transient result slot
        if (deleted) mind.setUnitDeleted(result, true);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("FUNCTION structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields, String name,
                                           StructuralValue.Kind kind) throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing FUNCTION field " + name);
        return value;
    }
}
