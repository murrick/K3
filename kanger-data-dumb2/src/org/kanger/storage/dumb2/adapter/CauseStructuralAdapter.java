package org.kanger.storage.dumb2.adapter;

import org.kanger.primitives.Cause;
import org.kanger.primitives.Solve;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Neutral embedded structural projection for one Rule cause. */
final class CauseStructuralAdapter {

    static final String LAYOUT_NAME = "Cause-v1";

    static final Descriptor DESCRIPTOR = Descriptor.struct(LAYOUT_NAME, Arrays.asList(
            Descriptor.field("rule", Descriptor.ref("rules")),
            Descriptor.field("donor", SolveStructuralAdapter.DESCRIPTOR)));

    private CauseStructuralAdapter() {}

    static StructuralValue project(Cause value) throws IOException {
        if (value == null) throw new NullPointerException("value");
        Solve donor = value.getDonor();
        if (donor == null) throw new IOException("CAUSE donor must not be null");

        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("rule", StructuralValue.ref("rules", value.getRuleId()));
        fields.put("donor", SolveStructuralAdapter.project(donor));
        return StructuralValue.struct(fields);
    }

    static Cause materialize(StructuralValue value) throws IOException {
        if (value == null || value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("CAUSE structural value must be STRUCT");
        Map<String, StructuralValue> fields = value.asStruct();

        StructuralValue rule = require(fields, "rule", StructuralValue.Kind.REF);
        if (!"rules".equals(rule.getReferenceSchema()))
            throw new IOException("Invalid CAUSE rule reference namespace "
                    + rule.getReferenceSchema());

        StructuralValue donor =
                require(fields, "donor", StructuralValue.Kind.STRUCT);
        Cause result = new Cause();
        result.setPersistentState(
                rule.getReferenceId(), SolveStructuralAdapter.materialize(donor));
        return result;
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing CAUSE field " + name);
        return value;
    }
}
