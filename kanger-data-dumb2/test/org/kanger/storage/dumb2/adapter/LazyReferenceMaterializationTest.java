package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.FunctionBinding;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.FValue;
import org.kanger.units.Function;
import org.kanger.units.Predicate;
import org.kanger.units.TVariable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification that descriptor hydration restores IDs before semantic objects. */
public class LazyReferenceMaterializationTest {

    @Test
    void fValueKeepsMissingFunctionAndTermAsPersistentIds() throws Exception {
        Mind mind = new Mind(new User());
        Map<String, StructuralValue> fields = base(mind, 101L);
        fields.put("function", StructuralValue.ref("functions", 9001L));
        fields.put("value", StructuralValue.ref("dictionary", 9002L));
        fields.put("stamp", StructuralValue.list(Arrays.asList(
                StructuralValue.int64(0L), StructuralValue.int64(9003L))));

        FValue restored = FValueAdapter.INSTANCE.materialize(
                StructuralValue.struct(fields), mind);

        assertEquals(9001L, restored.getFunctionId());
        assertEquals(9002L, restored.getValueId());
        assertEquals(Arrays.asList(0L, 9003L), restored.getStamp());
    }

    @Test
    void tVariableKeepsMissingNameAndRuleAsPersistentIds() throws Exception {
        Mind mind = new Mind(new User());
        Map<String, StructuralValue> fields = base(mind, 102L);
        fields.put("name", StructuralValue.ref("dictionary", 9101L));
        fields.put("index", StructuralValue.int32(7));
        fields.put("rule", StructuralValue.ref("rules", 9102L));

        TVariable restored = TVariableAdapter.INSTANCE.materialize(
                StructuralValue.struct(fields), mind);

        assertEquals(9101L, restored.getNameId());
        assertEquals(9102L, restored.getRuleId());
        assertEquals(7, restored.getIndex());
    }

    @Test
    void predicateKeepsMissingNameAsPersistentId() throws Exception {
        Mind mind = new Mind(new User());
        Map<String, StructuralValue> fields = base(mind, 103L);
        fields.put("name", StructuralValue.ref("dictionary", 9201L));
        fields.put("range", StructuralValue.int32(3));

        Predicate restored = PredicateAdapter.INSTANCE.materialize(
                StructuralValue.struct(fields), mind);

        assertEquals(9201L, restored.getNameId());
        assertEquals(3, restored.getRange());
    }

    @Test
    void deletedFunctionRestoresDeletionWithoutResolvingReferences() throws Exception {
        Mind mind = new Mind(new User());
        Map<String, StructuralValue> fields = base(mind, 104L);
        fields.put("deleted", StructuralValue.bool(true));
        fields.put("name", StructuralValue.ref("dictionary", 9301L));
        fields.put("range", StructuralValue.int32(1));
        fields.put("binding", StructuralValue.enumeration(
                FunctionAdapter.BINDING_NAME, FunctionBinding.INFRASTRUCTURE.name()));

        Map<String, StructuralValue> argument = new LinkedHashMap<String, StructuralValue>();
        argument.put("kind", StructuralValue.enumeration(
                ArgumentStructuralAdapter.KIND_NAME, "TVARIABLE"));
        argument.put("value", StructuralValue.ref("tvariables", 9302L));
        argument.put("varOrder", StructuralValue.int32(0));
        fields.put("arguments", StructuralValue.list(
                Arrays.asList(StructuralValue.struct(argument))));

        Function restored = FunctionAdapter.INSTANCE.materialize(
                StructuralValue.struct(fields), mind);

        assertEquals(9301L, restored.getNameId());
        assertEquals(9302L, restored.getArguments().get(0).getId());
        assertTrue(restored.isDeleted(mind));
    }

    private static Map<String, StructuralValue> base(Mind mind, long id) {
        Map<String, StructuralValue> fields = new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(id));
        fields.put("mindId", StructuralValue.int64(mind.getId()));
        fields.put("deleted", StructuralValue.bool(false));
        return fields;
    }
}
