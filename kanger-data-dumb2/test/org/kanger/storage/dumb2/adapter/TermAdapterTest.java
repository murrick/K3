package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.DataType;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification of recursive Term-v1 structural persistence. */
public class TermAdapterTest {

    @Test
    void recursiveSetAndCVariableMetadataRoundTripWithoutReferenceResolution()
            throws Exception {
        Mind mind = new Mind(new User());

        Term text = term(mind, 101L, DataType.STRING, "alpha", 11);
        Term number = term(mind, 102L, DataType.NUMERIC, 2.5d, 12);

        List<ITerm> items = new ArrayList<ITerm>();
        items.add(text);
        items.add(number);

        Term source = term(mind, 100L, DataType.SET, items, 13);
        source.setPersistentState(
                DataType.SET, items, 13, 4, 9001L, 9002L, true);

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                TermAdapter.TYPE_NAME, TermAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        assertEquals("SET_ITEMS",
                projected.asStruct().get("kind").getEnumSymbol());

        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restoredUnit = adapters.materialize(definition, decoded, mind);
        Term restored = (Term) restoredUnit;

        assertSame(TermAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(100L, restored.getId());
        assertEquals(DataType.SET, restored.getType());
        assertEquals(13, restored.getPersistentHash());
        assertEquals(4, restored.getIndex());
        assertEquals(9001L, restored.getNameId());
        assertEquals(9002L, restored.getRuleId());
        assertTrue(restored.isDomini());

        List<?> restoredItems = (List<?>) restored.getValue();
        assertEquals(2, restoredItems.size());
        Term restoredText = (Term) restoredItems.get(0);
        Term restoredNumber = (Term) restoredItems.get(1);
        assertEquals(DataType.STRING, restoredText.getType());
        assertEquals("alpha", restoredText.getValue());
        assertEquals(DataType.NUMERIC, restoredNumber.getType());
        assertEquals(2.5d, (Double) restoredNumber.getValue(), 0.0d);
    }

    @Test
    void scalarAndLegacyTextFormsUseStableKinds() throws Exception {
        Mind mind = new Mind(new User());

        assertScalar(mind, DataType.VOID, null, "VOID");
        assertScalar(mind, DataType.PERIOD, "2 days", "PERIOD");
        assertScalar(mind, DataType.STRING, "text", "STRING");
        assertScalar(mind, DataType.NUMERIC, 7.25d, "NUMERIC");
        assertScalar(mind, DataType.DATE, new Date(123456789L), "DATE");
        assertScalar(mind, DataType.INTERVAL, "1..2", "INTERVAL_TEXT");
        assertScalar(mind, DataType.SET, "legacy", "SET_TEXT");

        byte[] blob = new byte[] {1, 2, 3, 4};
        Term source = term(mind, 201L, DataType.BLOB, blob, 31);
        Term restored = roundTrip(source, mind);
        assertEquals(DataType.BLOB, restored.getType());
        assertArrayEquals(blob, (byte[]) restored.getValue());

        Term nested = term(mind, 202L, DataType.STRING, "nested", 32);
        Term wrapper = term(mind, 203L, DataType.TERM, nested, 33);
        Term restoredWrapper = roundTrip(wrapper, mind);
        assertEquals(DataType.TERM, restoredWrapper.getType());
        assertEquals("nested", ((Term) restoredWrapper.getValue()).getValue());
    }

    @Test
    void descriptorUsesSelfRecursionAndConditionalCVariableMetadata() {
        Descriptor descriptor = TermAdapter.INSTANCE.getDescriptor();
        assertEquals("Term-v1", descriptor.getName());

        Descriptor value = descriptor.getFields().get(5).getDescriptor();
        assertEquals(Descriptor.Kind.VARIANT, value.getKind());
        assertEquals(Descriptor.Kind.SELF,
                value.getCases().get(2).getDescriptor().getKind());
        assertEquals(Descriptor.Kind.LIST,
                value.getCases().get(9).getDescriptor().getKind());
        assertEquals(Descriptor.Kind.SELF,
                value.getCases().get(9).getDescriptor().getElement().getKind());

        assertEquals(Descriptor.Kind.CONDITIONAL,
                descriptor.getFields().get(7).getDescriptor().getKind());
        assertEquals("index",
                descriptor.getFields().get(7).getDescriptor()
                        .getCondition().getFieldName());
        assertEquals(Descriptor.ConditionOperator.GREATER_THAN_INT64,
                descriptor.getFields().get(7).getDescriptor()
                        .getCondition().getOperator());
    }

    private static void assertScalar(Mind mind,
                                     DataType type,
                                     Object value,
                                     String expectedKind) throws Exception {
        Term source = term(mind, 300L + type.ordinal(), type, value, 40 + type.ordinal());
        StructuralValue projected = TermAdapter.INSTANCE.project(source, mind);
        assertEquals(expectedKind,
                projected.asStruct().get("kind").getEnumSymbol());
        Term restored = roundTrip(source, mind);
        assertEquals(type, restored.getType());
        if (value instanceof Date)
            assertEquals(((Date) value).getTime(), ((Date) restored.getValue()).getTime());
        else
            assertEquals(value, restored.getValue());
    }

    private static Term roundTrip(Term source, Mind mind) throws Exception {
        StructuralValue projected = TermAdapter.INSTANCE.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(
                TermAdapter.INSTANCE.getDescriptor(), projected);
        return TermAdapter.INSTANCE.materialize(
                StructuralValueCodec.decode(
                        TermAdapter.INSTANCE.getDescriptor(), bytes),
                mind);
    }

    private static Term term(Mind mind,
                             long id,
                             DataType type,
                             Object value,
                             int hash) {
        Term term = new Term();
        term.setMind(mind);
        term.setId(id);
        term.setMindId(mind.getId());
        term.setPersistentState(type, value, hash, 0, -1L, -1L, false);
        return term;
    }
}
