package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.internal.IUnit;
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

    public static Argument materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
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
        IUnit object = resolve(type, stored, mind);

        Argument result = new Argument();
        if (object != null) result.setObject(object);
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

    private static IUnit resolve(ArgumentType type, StructuralValue value, Mind mind)
            throws Exception {
        if (type == ArgumentType.EMPTY) {
            if (value.getKind() != StructuralValue.Kind.NULL)
                throw new IOException("EMPTY Argument value must be NULL");
            return null;
        }
        if (value.getKind() != StructuralValue.Kind.REF)
            throw new IOException(type + " Argument value must be REF");

        String expected;
        IUnit object;
        switch (type) {
            case TERM:
                expected = "dictionary"; object = (IUnit) mind.getTerms().get(value.getReferenceId()); break;
            case FUNCTION:
                expected = "functions"; object = mind.getFunctions().get(value.getReferenceId()); break;
            case TVARIABLE:
                expected = "tvariables"; object = mind.getTVars().get(value.getReferenceId()); break;
            case FVALUE:
                expected = "fvalues"; object = mind.getFValues().get(value.getReferenceId()); break;
            case TVALUE:
                expected = "tvalues"; object = mind.getTValues().get(value.getReferenceId()); break;
            default:
                throw new IOException("Unsupported Argument kind " + type);
        }
        if (!expected.equals(value.getReferenceSchema()))
            throw new IOException("Invalid Argument reference namespace: expected "
                    + expected + " actual " + value.getReferenceSchema());
        if (object == null)
            throw new IOException("Argument references missing " + expected
                    + " id=" + value.getReferenceId());
        return object;
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
