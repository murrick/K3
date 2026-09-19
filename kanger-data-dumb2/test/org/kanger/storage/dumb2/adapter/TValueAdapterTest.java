package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** First live KANGER Unit proof for the neutral DUMB2 persistent contract. */
public class TValueAdapterTest {

    @Test
    void liveTValueRoundTripsThroughNeutralDescriptorCodec() throws Exception {
        Mind mind = new Mind(new User());
        ITerm variableName = mind.getTerms().add("x");
        ITerm donor = mind.getTerms().add("value");
        TVariable variable = mind.getTVars().createTVar(new Rule(mind), variableName);
        TValue source = mind.getTValues().add(variable, donor);

        StructuralValue structural = TValueAdapter.toStructural(source, mind);
        byte[] bytes = StructuralValueCodec.encode(TValueAdapter.descriptor(), structural);
        StructuralValue decoded = StructuralValueCodec.decode(TValueAdapter.descriptor(), bytes);
        TValue restored = TValueAdapter.fromStructural(decoded, mind);

        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(source.getValueId(), restored.getValueId());
        assertEquals(source.getTVarId(), restored.getTVarId());
        assertEquals(source.getHash(), restored.getHash());
        assertEquals(source, restored);
        assertFalse(restored.isDeleted(mind));
    }

    @Test
    void adapterDescriptorContainsOnlyNeutralTypedReferences() {
        assertEquals("TValue-v1", TValueAdapter.descriptor().getName());
        assertEquals("dictionary",
                TValueAdapter.descriptor().getFields().get(3).getDescriptor().getTarget());
        assertEquals("tvariables",
                TValueAdapter.descriptor().getFields().get(4).getDescriptor().getTarget());
    }

    @Test
    void missingReferencedUnitFailsExplicitly() throws Exception {
        Mind mind = new Mind(new User());
        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(7L));
        fields.put("mindId", StructuralValue.int64(mind.getId()));
        fields.put("deleted", StructuralValue.bool(false));
        fields.put("value", StructuralValue.ref("dictionary", Long.MAX_VALUE));
        fields.put("variable", StructuralValue.ref("tvariables", Long.MAX_VALUE));

        assertThrows(IOException.class,
                () -> TValueAdapter.fromStructural(StructuralValue.struct(fields), mind));
    }
}
