package org.kanger.storage.dumb2.adapter;

import org.kanger.primitives.Argument;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Neutral embedded structural projection for one predicate solve shape. */
final class SolveStructuralAdapter {

    static final String LAYOUT_NAME = "Solve-v1";

    static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("predicate", Descriptor.ref("predicates")),
            Descriptor.field("range", Descriptor.INT32),
            Descriptor.field("antc", Descriptor.BOOL),
            Descriptor.field("arguments", Descriptor.list(ArgumentStructuralAdapter.DESCRIPTOR))));

    private SolveStructuralAdapter() {}

    static StructuralValue project(Solve value) throws IOException {
        if (value == null) throw new NullPointerException("value");
        int range = value.getRange();
        if (range < 0) throw new IOException("Solve range must not be negative");
        if (value.getArguments().size() != range)
            throw new IOException("Solve argument count " + value.getArguments().size()
                    + " does not match range=" + range);

        List<StructuralValue> arguments = new ArrayList<StructuralValue>(range);
        for (int i = 0; i < range; ++i)
            arguments.add(ArgumentStructuralAdapter.project(
                    (Argument) value.getArguments().get(i)));

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("predicate",
                StructuralValue.ref("predicates", value.getPredicateId()));
        fields.put("range", StructuralValue.int32(range));
        fields.put("antc", StructuralValue.bool(value.isAntc()));
        fields.put("arguments", StructuralValue.list(arguments));
        return StructuralValue.struct(fields);
    }

    static Solve materialize(StructuralValue value) throws IOException {
        Solve result = new Solve();
        apply(value, result);
        return result;
    }

    static void apply(StructuralValue value, Solve result) throws IOException {
        if (value == null || result == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);

        StructuralValue predicate =
                require(fields, "predicate", StructuralValue.Kind.REF);
        if (!"predicates".equals(predicate.getReferenceSchema()))
            throw new IOException("Invalid Solve predicate reference namespace "
                    + predicate.getReferenceSchema());

        int range = require(fields, "range", StructuralValue.Kind.INT32).asInt32();
        if (range < 0) throw new IOException("Solve range must not be negative");
        boolean antc = require(fields, "antc", StructuralValue.Kind.BOOL).asBool();

        StructuralValue arguments =
                require(fields, "arguments", StructuralValue.Kind.LIST);
        if (arguments.asList().size() != range)
            throw new IOException("Solve argument count " + arguments.asList().size()
                    + " does not match range=" + range);

        result.setPersistentPredicateId(predicate.getReferenceId());
        result.setRange(range);
        result.setAntc(antc);
        result.getArguments().clear();
        for (StructuralValue argument : arguments.asList())
            result.getArguments().add(ArgumentStructuralAdapter.materialize(argument));
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("Solve structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing Solve field " + name);
        return value;
    }
}
