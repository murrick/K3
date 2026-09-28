package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.LibMode;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Operation;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification of persistent Operation definition state. */
public class OperationAdapterTest {

    @Test
    void operationRoundTripsWithoutRuntimeReactorOrResultParamPersistence()
            throws Exception {
        Mind mind = new Mind(new User());

        Operation source = new Operation(mind);
        source.setId(61L);
        source.setMindId(mind.getId());
        source.setMode(LibMode.FUNCTION);
        source.setName("sum");
        source.setRange(2);
        source.getScripts().add("return a + b;");
        source.getScripts().add("return fallback;");
        source.getParams().add("a");
        source.getParams().add("b");
        source.getParams().add("sum");

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                OperationAdapter.TYPE_NAME, OperationAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        assertEquals(2, projected.asStruct().get("params").asList().size());

        byte[] bytes = StructuralValueCodec.encode(definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(definition.getDescriptor(), bytes);
        IUnit restoredUnit = adapters.materialize(definition, decoded, mind);
        Operation restored = (Operation) restoredUnit;

        assertSame(OperationAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(LibMode.FUNCTION, restored.getMode());
        assertEquals("sum", restored.getName());
        assertEquals(2, restored.getRange());
        assertEquals(source.getScripts(), restored.getScripts());
        assertEquals(Arrays.asList("a", "b", "sum"), restored.getParams());
        assertNull(restored.getProc());
    }

    @Test
    void descriptorUsesStableModeSymbolsAndUtf8Lists() {
        assertEquals("Operation-v1", OperationAdapter.INSTANCE.getDescriptor().getName());
        assertEquals(Arrays.asList("UNKNOWN", "PREDICATE", "FUNCTION"),
                OperationAdapter.INSTANCE.getDescriptor()
                        .getFields().get(3).getDescriptor().getSymbols());
        assertEquals(org.kanger.storage.dumb2.descriptor.Descriptor.Kind.UTF8,
                OperationAdapter.INSTANCE.getDescriptor()
                        .getFields().get(5).getDescriptor().getElement().getKind());
        assertEquals(org.kanger.storage.dumb2.descriptor.Descriptor.Kind.UTF8,
                OperationAdapter.INSTANCE.getDescriptor()
                        .getFields().get(7).getDescriptor().getElement().getKind());
    }
}
