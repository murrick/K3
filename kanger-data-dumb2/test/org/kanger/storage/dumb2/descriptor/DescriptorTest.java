package org.kanger.storage.dumb2.descriptor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification for the minimal declarative DUMB 2.0 descriptor language. */
public class DescriptorTest {

    @Test
    void typedReferencesAndNestedStructuresDescribeTValueWithoutRuntimeTypes() {
        Descriptor tvalue = Descriptor.struct("TValue-v1", Arrays.asList(
                Descriptor.field("id", Descriptor.INT64),
                Descriptor.field("mindId", Descriptor.INT64),
                Descriptor.field("deleted", Descriptor.BOOL),
                Descriptor.field("value", Descriptor.ref("dictionary")),
                Descriptor.field("variable", Descriptor.ref("tvariables"))));

        assertEquals(Descriptor.Kind.STRUCT, tvalue.getKind());
        assertEquals("dictionary", tvalue.getFields().get(3).getDescriptor().getTarget());
        assertEquals("tvariables", tvalue.getFields().get(4).getDescriptor().getTarget());
    }

    @Test
    void argumentShapeUsesNamedEnumVariantAndTypedReferences() {
        Descriptor argumentType = Descriptor.enumeration("ArgumentType", Arrays.asList(
                "EMPTY", "TERM", "FUNCTION", "TVARIABLE", "FVALUE", "TVALUE"));
        Descriptor none = Descriptor.struct("None", Collections.<Descriptor.Field>emptyList());
        Descriptor value = Descriptor.variant("type", Arrays.asList(
                Descriptor.variantCase("EMPTY", none),
                Descriptor.variantCase("TERM", Descriptor.ref("dictionary")),
                Descriptor.variantCase("FUNCTION", Descriptor.ref("functions")),
                Descriptor.variantCase("TVARIABLE", Descriptor.ref("tvariables")),
                Descriptor.variantCase("FVALUE", Descriptor.ref("fvalues")),
                Descriptor.variantCase("TVALUE", Descriptor.ref("tvalues"))));

        Descriptor argument = Descriptor.struct("Argument-v1", Arrays.asList(
                Descriptor.field("type", argumentType),
                Descriptor.field("value", value),
                Descriptor.field("varOrder", Descriptor.INT32)));

        assertEquals("type", argument.getFields().get(1).getDescriptor().getDiscriminatorField());
        assertEquals(6, argument.getFields().get(1).getDescriptor().getCases().size());
    }

    @Test
    void termShapeProvesVariantConditionalListAndRecursiveReferenceVocabulary() {
        Descriptor dataType = Descriptor.enumeration("DataType", Arrays.asList(
                "VOID", "DATE", "NUMERIC", "PERIOD", "STRING", "BLOB", "TERM", "SET"));
        Descriptor none = Descriptor.struct("None", Collections.<Descriptor.Field>emptyList());
        Descriptor value = Descriptor.variant("type", Arrays.asList(
                Descriptor.variantCase("VOID", none),
                Descriptor.variantCase("DATE", Descriptor.INT64),
                Descriptor.variantCase("NUMERIC", Descriptor.FLOAT64),
                Descriptor.variantCase("PERIOD", Descriptor.UTF8),
                Descriptor.variantCase("STRING", Descriptor.UTF8),
                Descriptor.variantCase("BLOB", Descriptor.BYTES),
                Descriptor.variantCase("TERM", Descriptor.ref("dictionary")),
                Descriptor.variantCase("SET", Descriptor.list(Descriptor.ref("dictionary")))));
        Descriptor variableData = Descriptor.struct("TermVariable-v1", Arrays.asList(
                Descriptor.field("name", Descriptor.ref("dictionary")),
                Descriptor.field("rule", Descriptor.ref("rules")),
                Descriptor.field("domini", Descriptor.BOOL)));

        Descriptor term = Descriptor.struct("Term-v1", Arrays.asList(
                Descriptor.field("id", Descriptor.INT64),
                Descriptor.field("mindId", Descriptor.INT64),
                Descriptor.field("deleted", Descriptor.BOOL),
                Descriptor.field("type", dataType),
                Descriptor.field("value", value),
                Descriptor.field("index", Descriptor.INT32),
                Descriptor.field("variableData",
                        Descriptor.conditional(Descriptor.greaterThanInt64("index", 0L), variableData))));

        assertEquals(Descriptor.Kind.CONDITIONAL,
                term.getFields().get(6).getDescriptor().getKind());
        assertEquals("index",
                term.getFields().get(6).getDescriptor().getCondition().getFieldName());
    }

    @Test
    void streamingDependenciesMustReferToPreviousFields() {
        Descriptor variant = Descriptor.variant("type", Collections.singletonList(
                Descriptor.variantCase("TERM", Descriptor.ref("dictionary"))));

        assertThrows(IllegalArgumentException.class, () -> Descriptor.struct(
                "Broken", Arrays.asList(
                        Descriptor.field("value", variant),
                        Descriptor.field("type", Descriptor.INT32))));
    }

    @Test
    void descriptorCollectionsAreImmutable() {
        Descriptor descriptor = Descriptor.struct("One", Collections.singletonList(
                Descriptor.field("value", Descriptor.INT64)));

        assertThrows(UnsupportedOperationException.class,
                () -> descriptor.getFields().clear());
    }
}
