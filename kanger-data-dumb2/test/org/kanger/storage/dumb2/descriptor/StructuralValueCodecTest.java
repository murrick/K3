package org.kanger.storage.dumb2.descriptor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proof that DUMB2 values can be encoded without type-specific serializers. */
public class StructuralValueCodecTest {

    @Test
    void tvalueShapeUsesOnlyGenericLittleEndianCodec() throws Exception {
        Descriptor descriptor = Descriptor.struct("TValue-v1", Arrays.asList(
                Descriptor.field("id", Descriptor.INT64),
                Descriptor.field("mindId", Descriptor.INT64),
                Descriptor.field("deleted", Descriptor.BOOL),
                Descriptor.field("value", Descriptor.ref("dictionary")),
                Descriptor.field("variable", Descriptor.ref("tvariables"))));
        StructuralValue value = struct(
                "id", StructuralValue.int64(7L),
                "mindId", StructuralValue.int64(2L),
                "deleted", StructuralValue.bool(true),
                "value", StructuralValue.ref("dictionary", 11L),
                "variable", StructuralValue.ref("tvariables", 13L));

        byte[] encoded = StructuralValueCodec.encode(descriptor, value);

        assertEquals(33, encoded.length);
        assertEquals(7, encoded[0]);
        assertEquals(1, encoded[16]);
        assertEquals(value, StructuralValueCodec.decode(descriptor, encoded));
    }

    @Test
    void argumentEnumSelectsVariantWithoutKangerSpecificSwitch() throws Exception {
        Descriptor argumentType = Descriptor.enumeration("ArgumentType",
                Arrays.asList("EMPTY", "TERM", "TVARIABLE"));
        Descriptor none = Descriptor.struct("None", Collections.<Descriptor.Field>emptyList());
        Descriptor descriptor = Descriptor.struct("Argument-v1", Arrays.asList(
                Descriptor.field("type", argumentType),
                Descriptor.field("value", Descriptor.variant("type", Arrays.asList(
                        Descriptor.variantCase("EMPTY", none),
                        Descriptor.variantCase("TERM", Descriptor.ref("dictionary")),
                        Descriptor.variantCase("TVARIABLE", Descriptor.ref("tvariables"))))),
                Descriptor.field("varOrder", Descriptor.INT32)));
        StructuralValue value = struct(
                "type", StructuralValue.enumeration("ArgumentType", "TERM"),
                "value", StructuralValue.ref("dictionary", 42L),
                "varOrder", StructuralValue.int32(3));

        byte[] encoded = StructuralValueCodec.encode(descriptor, value);
        StructuralValue decoded = StructuralValueCodec.decode(descriptor, encoded);

        assertEquals(value, decoded);
        assertArrayEquals(encoded, StructuralValueCodec.encode(descriptor, decoded));
    }

    @Test
    void recursiveTermShapeUsesSelfAndConditionalWithoutCustomCodec() throws Exception {
        Descriptor dataType = Descriptor.enumeration("DataType",
                Arrays.asList("VOID", "NUMERIC", "TERM", "SET"));
        Descriptor none = Descriptor.struct("None", Collections.<Descriptor.Field>emptyList());
        Descriptor variableData = Descriptor.struct("TermVariable-v1", Arrays.asList(
                Descriptor.field("name", Descriptor.ref("dictionary")),
                Descriptor.field("rule", Descriptor.ref("rules")),
                Descriptor.field("domini", Descriptor.BOOL)));
        Descriptor term = Descriptor.struct("Term-v1", Arrays.asList(
                Descriptor.field("id", Descriptor.INT64),
                Descriptor.field("type", dataType),
                Descriptor.field("value", Descriptor.variant("type", Arrays.asList(
                        Descriptor.variantCase("VOID", none),
                        Descriptor.variantCase("NUMERIC", Descriptor.FLOAT64),
                        Descriptor.variantCase("TERM", Descriptor.SELF),
                        Descriptor.variantCase("SET", Descriptor.list(Descriptor.SELF))))),
                Descriptor.field("index", Descriptor.INT32),
                Descriptor.field("variableData",
                        Descriptor.conditional(Descriptor.greaterThanInt64("index", 0L),
                                variableData))));
        StructuralValue nested = struct(
                "id", StructuralValue.int64(2L),
                "type", StructuralValue.enumeration("DataType", "NUMERIC"),
                "value", StructuralValue.float64(12.5),
                "index", StructuralValue.int32(0));
        StructuralValue value = struct(
                "id", StructuralValue.int64(1L),
                "type", StructuralValue.enumeration("DataType", "TERM"),
                "value", nested,
                "index", StructuralValue.int32(1),
                "variableData", struct(
                        "name", StructuralValue.ref("dictionary", 8L),
                        "rule", StructuralValue.ref("rules", 9L),
                        "domini", StructuralValue.bool(false)));

        byte[] encoded = StructuralValueCodec.encode(term, value);

        assertEquals(value, StructuralValueCodec.decode(term, encoded));
    }

    @Test
    void wrongReferenceNamespaceIsRejected() {
        Descriptor descriptor = Descriptor.ref("dictionary");
        StructuralValue value = StructuralValue.ref("rules", 1L);

        assertThrows(IOException.class, () -> StructuralValueCodec.encode(descriptor, value));
    }

    @Test
    void inactiveConditionalFieldMustBeAbsent() {
        Descriptor descriptor = Descriptor.struct("Conditional", Arrays.asList(
                Descriptor.field("index", Descriptor.INT32),
                Descriptor.field("extra", Descriptor.conditional(
                        Descriptor.greaterThanInt64("index", 0L), Descriptor.INT64))));
        StructuralValue invalid = struct(
                "index", StructuralValue.int32(0),
                "extra", StructuralValue.int64(1L));

        assertThrows(IOException.class, () -> StructuralValueCodec.encode(descriptor, invalid));
    }

    private static StructuralValue struct(Object... pairs) {
        Map<String, StructuralValue> values = new LinkedHashMap<String, StructuralValue>();
        for (int i = 0; i < pairs.length; i += 2)
            values.put((String) pairs[i], (StructuralValue) pairs[i + 1]);
        return StructuralValue.struct(values);
    }
}
