package org.kanger.storage.dumb2.adapter;

import org.kanger.enums.ArgumentType;
import org.kanger.primitives.Argument;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Neutral embedded projection for one KANGER Argument slot. */
public final class ArgumentStructuralAdapter {

    public static final String LAYOUT_NAME = "Argument-v1";
    public static final String KIND_NAME = "ArgumentKind";

    private static final Descriptor KIND = Descriptor.enumeration(KIND_NAME,
            Arrays.asList("EMPTY", "TERM", "FUNCTION", "TVARIABLE", "FVALUE", "TVALUE"));

    public static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("kind", KIND),
            Descriptor.field("value", Descriptor.variant("kind", Arrays.asList(
                    Descriptor.variantCase("EMPTY", Descriptor.NULL),
                    Descriptor.variantCase("TERM", Descriptor.ref("dictionary")),
                    Descriptor.variantCase("FUNCTION", Descriptor.ref("functions")),
                    Descriptor.variantCase("TVARIABLE", Descriptor.ref("tvariables")),
                    Descriptor.variantCase("FVALUE", Descriptor.ref("fvalues")),
                    Descriptor.variantCase("TVALUE", Descriptor.ref("tvalues"))))),
            Descriptor.field("varOrder", Descriptor.INT32)));

    private ArgumentStructuralAdapter() {
    }

    public static StructuralValue project(Argument argument) throws IOException {
        if (argument == null) throw new NullPointerException("argument");
        String kind = argument.getType().name();
        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("kind", StructuralValue.enumeration(KIND_NAME, kind));
        fields.put("value", reference(argument.getType(), argument.getId()));
        fields.put("varOrder", StructuralValue.int32(argument.getVarOrder()));
        return StructuralValue.struct(fields);
    }

    public static Argument materialize(StructuralValue value) throws IOException {
        if (value == null) throw new NullPointerException("value");
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("Argument structural value must be STRUCT");
        Map<String, StructuralValue> fields = value.asStruct();
        StructuralValue kindValue = require(fields, "kind", StructuralValue.Kind.ENUM);
        if (!KIND_NAME.equals(kindValue.getEnumName()))
            throw new IOException("Invalid Argument enum namespace " + kindValue.getEnumName());

        final ArgumentType type;
        try {
            type = ArgumentType.valueOf(kindValue.getEnumSymbol());
        } catch (IllegalArgumentException failure) {
            throw new IOException("Unknown Argument kind " + kindValue.getEnumSymbol(), failure);
        }

        StructuralValue stored = fields.get("value");
        if (stored == null) throw new IOException("Missing Argument field value");

        long id = -1L;
        if (type == ArgumentType.EMPTY) {
            if (stored.getKind() != StructuralValue.Kind.NULL)
                throw new IOException("EMPTY Argument value must be NULL");
        } else {
            if (stored.getKind() != StructuralValue.Kind.REF)
                throw new IOException(type + " Argument value must be REF");
            String expected = namespace(type);
            if (!expected.equals(stored.getReferenceSchema()))
                throw new IOException("Invalid Argument reference namespace: expected "
                        + expected + " actual " + stored.getReferenceSchema());
            id = stored.getReferenceId();
        }

        Argument result = new Argument();
        result.setPersistentReference(id, type);
        result.setVarOrder(require(fields, "varOrder", StructuralValue.Kind.INT32).asInt32());
        return result;
    }

    private static StructuralValue reference(ArgumentType type, long id) throws IOException {
        switch (type) {
            case EMPTY: return StructuralValue.nullValue();
            case TERM: return StructuralValue.ref("dictionary", id);
            case FUNCTION: return StructuralValue.ref("functions", id);
            case TVARIABLE: return StructuralValue.ref("tvariables", id);
            case FVALUE: return StructuralValue.ref("fvalues", id);
            case TVALUE: return StructuralValue.ref("tvalues", id);
            default: throw new IOException("Unsupported Argument kind " + type);
        }
    }

    private static String namespace(ArgumentType type) throws IOException {
        switch (type) {
            case TERM: return "dictionary";
            case FUNCTION: return "functions";
            case TVARIABLE: return "tvariables";
            case FVALUE: return "fvalues";
            case TVALUE: return "tvalues";
            default: throw new IOException("No reference namespace for " + type);
        }
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name, StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing Argument field " + name);
        return value;
    }
}
