package org.kanger.storage.dumb2.descriptor;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification of Context-local immutable typeCode semantics. */
public class TypeRegistryTest {

    @Test
    void sameTypeAndLayoutRegistrationIsIdempotent() {
        TypeRegistry registry = new TypeRegistry();
        Descriptor descriptor = oneField("TValue-v1", Descriptor.INT64);

        TypeDefinition first = registry.register("TVALUE", descriptor);
        TypeDefinition second = registry.register("TVALUE", descriptor);

        assertSame(first, second);
        assertEquals(1, registry.size());
    }

    @Test
    void newLayoutForSameTypeGetsNewCodeAndBothRemainReadable() {
        TypeRegistry registry = new TypeRegistry();
        TypeDefinition v1 = registry.register("TVALUE",
                oneField("TValue-v1", Descriptor.INT64));
        TypeDefinition v2 = registry.register("TVALUE",
                oneField("TValue-v2", Descriptor.ref("dictionary")));

        assertNotEquals(v1.getTypeCode(), v2.getTypeCode());
        assertEquals(v1, registry.resolve(v1.getTypeCode()));
        assertEquals(v2, registry.resolve(v2.getTypeCode()));
    }

    @Test
    void publishedCodeCannotBeRedefined() {
        TypeRegistry registry = new TypeRegistry();
        registry.install(17, "TVALUE", oneField("TValue-v1", Descriptor.INT64));

        assertThrows(IllegalStateException.class, () ->
                registry.install(17, "TVALUE",
                        oneField("TValue-v2", Descriptor.INT64)));
        assertThrows(IllegalStateException.class, () ->
                registry.install(17, "RULE",
                        oneField("Rule-v1", Descriptor.INT64)));
    }

    @Test
    void manifestInstalledHighCodeAdvancesFutureAllocation() {
        TypeRegistry registry = new TypeRegistry();
        registry.install(24, "RULE", oneField("Rule-v1", Descriptor.INT64));

        assertEquals(25, registry.register("PREDICATE",
                oneField("Predicate-v1", Descriptor.INT64)).getTypeCode());
    }

    @Test
    void typeCodesHaveNoMeaningAcrossContexts() {
        TypeRegistry first = new TypeRegistry();
        TypeRegistry second = new TypeRegistry();

        TypeDefinition firstCode = first.register("TVALUE",
                oneField("TValue-v1", Descriptor.INT64));
        TypeDefinition secondCode = second.register("RULE",
                oneField("Rule-v1", Descriptor.INT64));

        assertEquals(firstCode.getTypeCode(), secondCode.getTypeCode());
        assertNotEquals(firstCode.getTypeName(), secondCode.getTypeName());
    }

    @Test
    void unknownCodeFailsExplicitly() {
        TypeRegistry registry = new TypeRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.resolve(99));
    }

    private static Descriptor oneField(String name, Descriptor field) {
        return Descriptor.struct(name, Collections.singletonList(
                Descriptor.field("value", field)));
    }
}
