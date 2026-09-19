package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qualification of the explicit KANGER semantic adapter boundary. */
public class KangerAdapterRegistryTest {

    @Test
    void runtimeAndPersistentLookupMeetAtSameTValueAdapter() throws Exception {
        Mind mind = new Mind(new User());
        ITerm name = mind.getTerms().add("x");
        ITerm donor = mind.getTerms().add("value");
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), name);
        TValue source = mind.getTValues().add(variable, donor);

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeRegistry types = new TypeRegistry();
        org.kanger.storage.dumb2.descriptor.TypeDefinition definition =
                types.register(TValueAdapter.TYPE_NAME, TValueAdapter.descriptor());

        assertSame(TValueAdapter.INSTANCE, adapters.forRuntime(source));
        assertSame(TValueAdapter.INSTANCE, adapters.forPersistent(definition));

        StructuralValue projected = adapters.project(source, mind);
        IUnit restored = adapters.materialize(definition, projected, mind);

        assertEquals(source, restored);
    }

    @Test
    void persistentLookupRequiresExactDescriptorNotOnlyTypeName() {
        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeRegistry types = new TypeRegistry();
        org.kanger.storage.dumb2.descriptor.TypeDefinition unknownLayout =
                types.register("TVALUE",
                        org.kanger.storage.dumb2.descriptor.Descriptor.struct(
                                "TValue-v2",
                                java.util.Collections.singletonList(
                                        org.kanger.storage.dumb2.descriptor.Descriptor.field(
                                                "value",
                                                org.kanger.storage.dumb2.descriptor.Descriptor.INT64))));

        assertThrows(IllegalArgumentException.class,
                () -> adapters.forPersistent(unknownLayout));
    }
}
